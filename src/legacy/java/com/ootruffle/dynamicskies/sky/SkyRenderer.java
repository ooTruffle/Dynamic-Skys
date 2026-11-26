package com.ootruffle.dynamicskies.sky;

import com.ootruffle.dynamicskies.Log;
import com.ootruffle.dynamicskies.audio.AudioManager;
import com.ootruffle.dynamicskies.config.DynamicSkiesConfig;
import com.ootruffle.dynamicskies.sky.SkyShader.U;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Map;

/**
 * Draws the selected shader sky in place of (or blended over) the vanilla sky.
 * Called from RenderGlobalMixin at the start and end of RenderGlobal.renderSky.
 */
public final class SkyRenderer {

    public static final SkyRenderer INSTANCE = new SkyRenderer();

    private static final ResourceLocation SUN = new ResourceLocation("textures/environment/sun.png");
    private static final ResourceLocation MOON = new ResourceLocation("textures/environment/moon_phases.png");

    private final Map<SkyType, SkyShader> shaders = new EnumMap<>(SkyType.class);
    private final Map<SkyType, Boolean> broken = new EnumMap<>(SkyType.class);

    private SkyType current;
    private SkyType previous;
    private long fadeStartNanos;
    private long lastCycleNanos = System.nanoTime();

    private long lastFrameNanos;
    /** Animation clock in seconds. It wraps at TIME_WRAP so it stays precise as a GLSL float. */
    private double time;
    /** When the clock wraps, the sky at the old time fades out over the restarted one. */
    private double wrapOldTime;
    private float wrapFade = -1f;
    private static final double TIME_WRAP = 3600.0;
    private static final float WRAP_FADE_SECONDS = 4f;
    private float frameAlpha;
    private boolean frameStarted;
    private boolean overlayThisFrame;
    private boolean replacedThisFrame;
    private long lastBeginNanos;
    private boolean previewing;
    private long compileDeadlineNanos;
    private int compiledThisFrame;

    private static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;
    private Framebuffer skyBuffer;
    private int scaledPrevFbo;
    private final IntBuffer viewportBuffer = BufferUtils.createIntBuffer(16);

    private int spectrumTexture = -1;
    private final ByteBuffer spectrumData = BufferUtils.createByteBuffer(AudioManager.BANDS);
    private final byte[] uploadedSpectrum = new byte[AudioManager.BANDS];
    private boolean spectrumUploaded;
    private final FloatBuffer fogBuffer = BufferUtils.createFloatBuffer(16);
    private float fogR, fogG, fogB;
    private float skyR = 0.47f, skyG = 0.65f, skyB = 1f;
    private float sunAngle = (float) Math.PI;
    private int cubeList = -1;

    /*
     * Skip Hidden Sky: instead of drawing the sky first and letting the terrain paint over
     * most of it, the draw waits until the opaque terrain is done and is depth-tested at the
     * far plane, so the (often expensive) sky shader only runs on pixels that stay sky.
     */
    private boolean deferThisFrame;
    private boolean pendingDraw;
    private boolean drawingDeferred;
    private boolean deferBroken;

    /** OptiFine shaders keep their own program bound around the sky; everyone else leaves 0 bound. */
    private static final boolean SHADER_MOD = classExists("shadersmod.client.Shaders")
            || classExists("net.optifine.shaders.Shaders");

    private static final String[] METEOR_NAMES = names("uMeteors", Meteors.MAX);
    private static final String[] METEOR_DIR_NAMES = names("uMeteorDirs", Meteors.MAX);
    private static final String[] SHELL_NAMES = names("uShells", Fireworks.MAX);
    private static final String[] SHELL_INFO_NAMES = names("uShellInfo", Fireworks.MAX);

    private static String[] names(String array, int count) {
        String[] out = new String[count];
        for (int i = 0; i < count; i++) out[i] = array + "[" + i + "]";
        return out;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, SkyRenderer.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private DynamicTexture image;
    private String loadedImage;
    private boolean imageReloadRequested;
    private boolean imageMissingReported;

    private final Meteors meteors = new Meteors();
    private final Fireworks fireworks = new Fireworks();
    private long lastBeatMeteorNanos;
    private float dropHue, dropHueTarget;
    private float fadeSeconds;
    private boolean jumpOnDrop;

    private SkyShader custom;
    private String customName;
    private long customModified;
    private long lastCustomCheckNanos;
    private boolean shaderReloadRequested;
    private boolean exampleWritten;

    private SkyRenderer() {
    }

    public void requestImageReload() {
        imageReloadRequested = true;
    }

    public void requestShaderReload() {
        shaderReloadRequested = true;
    }

    /** True when clouds should be skipped this frame (the dynamic sky is showing and Hide Clouds is on). */
    public boolean shouldHideClouds() {
        return DynamicSkiesConfig.INSTANCE.hideClouds && frameAlpha > 0.001f;
    }

    /**
     * Start of renderSky. Returns true when the dynamic sky fully covers the
     * screen, so vanilla's sky should be skipped.
     */
    public boolean renderReplacing(float partialTicks) {
        beginFrame(partialTicks);
        if (overlayThisFrame || frameAlpha < 0.999f) {
            return false;
        }
        if (deferThisFrame) {
            // Skip vanilla's sky now; ours is drawn after the terrain (renderAfterTerrain).
            replacedThisFrame = shader(SkyType.byIndex(DynamicSkiesConfig.INSTANCE.sky)) != null;
            return replacedThisFrame;
        }
        replacedThisFrame = draw(partialTicks, true, false);
        return replacedThisFrame;
    }

    /**
     * After the whole sky is drawn (by vanilla, a resource pack sky mod, or us).
     * Draws overlay mode and partly transparent skies on top of it.
     */
    public void renderAfterSky(float partialTicks) {
        if (!frameStarted) {
            // renderSky was cancelled by another mod before our hook ran.
            beginFrame(partialTicks);
        }
        frameStarted = false;
        boolean replaced = replacedThisFrame;
        replacedThisFrame = false;

        if (frameAlpha <= 0.001f) {
            return;
        }
        if (deferThisFrame) {
            pendingDraw = true;
            return;
        }
        if (overlayThisFrame) {
            draw(partialTicks, false, true);
        } else if (frameAlpha < 0.999f) {
            draw(partialTicks, false, false);
        } else if (!replaced) {
            // Replace mode, but we never got to draw first: cover what was drawn.
            draw(partialTicks, true, false);
        }
    }

    /**
     * After the opaque terrain. With Skip Hidden Sky the sky is drawn here at the far
     * plane with the depth test on, so only pixels no terrain covered get shaded; water,
     * entities and clouds that come later still draw on top of it as before.
     */
    public void renderAfterTerrain(float partialTicks) {
        if (!pendingDraw) {
            return;
        }
        pendingDraw = false;
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        // Everything lands exactly on the far plane (the cleared depth), wherever it is drawn.
        GL11.glDepthRange(1.0, 1.0);
        drawingDeferred = true;
        try {
            if (overlayThisFrame) {
                draw(partialTicks, false, true);
            } else if (frameAlpha < 0.999f) {
                draw(partialTicks, false, false);
            } else {
                draw(partialTicks, true, false);
            }
        } finally {
            drawingDeferred = false;
            GL11.glDepthRange(0.0, 1.0);
        }
        // Vanilla adjusts the block atlas' mipmap filtering next without binding it first.
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
    }

    /**
     * Keeps time, audio and shooting stars moving in the sky browser when the
     * world's sky isn't being drawn (no world, sky disabled, low render distance).
     */
    public void tickForPreview() {
        if (System.nanoTime() - lastBeginNanos > 100_000_000L) {
            float dt = tickTime();
            AudioManager.update(dt);
            updateEvents(dt);
        }
    }

    /**
     * Draws one sky into a screen rectangle, looking toward yaw/pitch (degrees).
     * x/y/w/h are real pixels with y measured from the bottom of the window;
     * only the part inside the clip rectangle is drawn (for scrolled lists).
     * Returns false if that sky can't be drawn (shader error, missing image).
     */
    public static final int PREVIEW_OK = 0;
    public static final int PREVIEW_LOADING = 1;
    public static final int PREVIEW_FAILED = 2;

    /**
     * Call once per frame before drawing previews. Shaders that aren't compiled
     * yet get compiled a few per frame (about 6 ms' worth), so opening the sky
     * browser doesn't freeze the game while all of them compile at once.
     */
    private final IntBuffer viewportBuf = BufferUtils.createIntBuffer(16);

    public void beginPreviewFrame() {
        compileDeadlineNanos = System.nanoTime() + 6_000_000L;
        compiledThisFrame = 0;
    }

    public int renderPreview(SkyType type, int x, int y, int w, int h,
                             int clipX, int clipY, int clipW, int clipH,
                             float yaw, float pitch, float fov) {
        if (clipW <= 0 || clipH <= 0 || w <= 0 || h <= 0) {
            return PREVIEW_OK;
        }
        boolean ready = type == SkyType.CUSTOM_SHADER || shaders.containsKey(type) || broken.containsKey(type);
        if (!ready) {
            if (compiledThisFrame > 0 && System.nanoTime() > compileDeadlineNanos) {
                return PREVIEW_LOADING;
            }
            compiledThisFrame++;
        }
        SkyShader shader = shader(type);
        if (shader == null || (type.usesImage() && image() == null)) {
            return PREVIEW_FAILED;
        }
        int prevProgram = currentProgram();
        // Put back whatever viewport was set: the window, or an offscreen preview target.
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuf);
        int vx = viewportBuf.get(0), vy = viewportBuf.get(1), vw = viewportBuf.get(2), vh = viewportBuf.get(3);

        GL11.glViewport(x, y, w, h);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(clipX, clipY, clipW, clipH);

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        double top = 0.05 * Math.tan(Math.toRadians(fov) / 2);
        double right = top * w / h;
        GL11.glFrustum(-right, right, -top, top, 0.05, 100.0);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.rotate(pitch, 1f, 0f, 0f);
        GlStateManager.rotate(yaw + 180f, 0f, 1f, 0f);

        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableAlpha();
        GlStateManager.disableBlend();
        GlStateManager.disableCull();
        GlStateManager.disableFog();
        GlStateManager.color(1f, 1f, 1f, 1f);

        uploadSpectrum();
        updateWorldColors(Minecraft.getMinecraft().theWorld, 1f);
        fogR = fogG = fogB = 0f;
        previewing = true;
        try {
            drawPass(shader, type, time, 1f, false);
        } finally {
            previewing = false;
        }

        GL20.glUseProgram(prevProgram);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glViewport(vx, vy, vw, vh);

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        return PREVIEW_OK;
    }

    /*
     * Sky Lighting: the world picks up the sky's colour. A few times a second the current sky
     * is drawn into a tiny framebuffer (two views up into the sky, facing opposite ways) and its
     * average colour is read back through a pixel buffer one sample later, so the read never
     * waits on the GPU. That colour, smoothed over time, tints the sky-light half of the
     * lightmap; torch-lit areas keep their own colour.
     */
    private static final int AMBIENT_W = 16, AMBIENT_H = 8;
    private static final long AMBIENT_INTERVAL_NANOS = 100_000_000L;
    private Framebuffer ambientBuffer;
    private final ByteBuffer ambientPixels = BufferUtils.createByteBuffer(AMBIENT_W * AMBIENT_H * 4);
    private int ambientPbo;
    private boolean ambientPboFilled;
    private boolean ambientUsePbo = true;
    private boolean ambientBroken;
    private long lastAmbientNanos;
    private long lastLightNanos;
    /** The sky's measured average colour, before smoothing. */
    private float sampleR = 1f, sampleG = 1f, sampleB = 1f;
    private boolean sampled;
    /** What the lightmap is tinted with now: eases toward the sample, and back to white when off. */
    private float lightR = 1f, lightG = 1f, lightB = 1f, lightAmount;

    /**
     * Called from the lightmap update, just before the lightmap is uploaded. Tints the
     * sky-lit entries of colors (index = sky level * 16 + block level) toward the sky.
     */
    public void tintLightmap(int[] colors, float partialTicks) {
        DynamicSkiesConfig cfg = DynamicSkiesConfig.INSTANCE;
        long now = System.nanoTime();
        float dt = lastLightNanos == 0 ? 0f : Math.min(0.5f, (now - lastLightNanos) / 1e9f);
        lastLightNanos = now;

        float alpha = cfg.skyLighting && !ambientBroken ? computeAlpha(partialTicks) : 0f;
        if (alpha > 0.001f) {
            sampleAmbient(now);
        }
        // The colour the world sees: our sky over (or added to) the normal one.
        float r = skyR, g = skyG, b = skyB;
        float amount = 0f;
        if (alpha > 0.001f && sampled) {
            if (isOverlay()) {
                r = Math.min(1f, r + sampleR * alpha);
                g = Math.min(1f, g + sampleG * alpha);
                b = Math.min(1f, b + sampleB * alpha);
            } else {
                r += (sampleR - r) * alpha;
                g += (sampleG - g) * alpha;
                b += (sampleB - b) * alpha;
            }
            amount = cfg.skyLightingStrength;
        }
        float ease = 1f - (float) Math.exp(-dt * 3f);
        lightR += (r - lightR) * ease;
        lightG += (g - lightG) * ease;
        lightB += (b - lightB) * ease;
        lightAmount += (amount - lightAmount) * ease;
        if (lightAmount < 0.002f) {
            return;
        }

        // Hue from the sky's colour (normalised to its brightness), plus a gentle brightness
        // shift: dark skies dim the world a little, bright ones lift it, within limits.
        float lum = 0.2126f * lightR + 0.7152f * lightG + 0.0722f * lightB;
        float inv = 1f / Math.max(lum, 0.04f);
        float bright = Math.max(0.65f, Math.min(1.35f, 0.65f + lum * 1.1f));
        float tr = Math.min(2.5f, lightR * inv) * bright;
        float tg = Math.min(2.5f, lightG * inv) * bright;
        float tb = Math.min(2.5f, lightB * inv) * bright;

        for (int i = 0; i < colors.length; i++) {
            int sky = i >> 4, block = i & 15;
            float w = lightAmount * (sky / 15f) * (1f - 0.8f * (block / 15f));
            if (w <= 0f) continue;
            int c = colors[i];
            colors[i] = 0xFF000000
                    | channel((c >> 16) & 0xFF, tr, w) << 16
                    | channel((c >> 8) & 0xFF, tg, w) << 8
                    | channel(c & 0xFF, tb, w);
        }
    }

    private static int channel(int value, float tint, float weight) {
        float factor = Math.max(0.35f, 1f + (tint - 1f) * weight);
        return Math.min(255, Math.round(value * factor));
    }

    private void sampleAmbient(long now) {
        if (now - lastAmbientNanos < AMBIENT_INTERVAL_NANOS) {
            return;
        }
        lastAmbientNanos = now;
        if (!OpenGlHelper.isFramebufferEnabled()) {
            ambientBroken = true;
            Log.warn("Sky Lighting needs framebuffers, which are turned off; it stays off.");
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        // Last sample's pixels: the GPU finished them long ago, so this doesn't wait.
        if (ambientPboFilled) {
            readAmbient();
        }

        SkyType sky = current != null ? current : SkyType.byIndex(DynamicSkiesConfig.INSTANCE.sky);
        if (ambientBuffer == null) {
            ambientBuffer = new Framebuffer(AMBIENT_W, AMBIENT_H, false);
            ambientBuffer.setFramebufferFilter(GL11.GL_NEAREST);
        }
        int prevFbo = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING);
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        ambientBuffer.bindFramebuffer(true);
        GlStateManager.clearColor(0f, 0f, 0f, 1f);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT);
        Entity view = mc.getRenderViewEntity();
        float yaw = view != null ? view.rotationYaw : 0f;
        int half = AMBIENT_W / 2;
        int a = renderPreview(sky, 0, 0, half, AMBIENT_H, 0, 0, half, AMBIENT_H, yaw, -55f, 110f);
        int b = renderPreview(sky, half, 0, half, AMBIENT_H, half, 0, half, AMBIENT_H, yaw + 180f, -55f, 110f);
        if (a == PREVIEW_OK && b == PREVIEW_OK) {
            requestAmbientRead();
        }
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, prevFbo);
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
    }

    private void requestAmbientRead() {
        if (ambientUsePbo) {
            try {
                if (ambientPbo == 0) {
                    ambientPbo = GL15.glGenBuffers();
                    GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, ambientPbo);
                    GL15.glBufferData(GL21.GL_PIXEL_PACK_BUFFER, (long) ambientPixels.capacity(), GL15.GL_STREAM_READ);
                } else {
                    GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, ambientPbo);
                }
                GL11.glReadPixels(0, 0, AMBIENT_W, AMBIENT_H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L);
                ambientPboFilled = true;
                return;
            } catch (Throwable t) {
                ambientUsePbo = false;
                Log.warn("Pixel buffers aren't available; Sky Lighting reads the sky directly instead.");
            } finally {
                GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
            }
        }
        // Fallback: read straight away. It waits on the GPU, but the image is tiny.
        ambientPixels.clear();
        GL11.glReadPixels(0, 0, AMBIENT_W, AMBIENT_H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, ambientPixels);
        averageAmbient();
    }

    private void readAmbient() {
        ambientPboFilled = false;
        ambientPixels.clear();
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, ambientPbo);
        GL15.glGetBufferSubData(GL21.GL_PIXEL_PACK_BUFFER, 0L, ambientPixels);
        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        averageAmbient();
    }

    private void averageAmbient() {
        long r = 0, g = 0, b = 0;
        int count = AMBIENT_W * AMBIENT_H;
        for (int i = 0; i < count; i++) {
            r += ambientPixels.get(i * 4) & 0xFF;
            g += ambientPixels.get(i * 4 + 1) & 0xFF;
            b += ambientPixels.get(i * 4 + 2) & 0xFF;
        }
        sampleR = r / (count * 255f);
        sampleG = g / (count * 255f);
        sampleB = b / (count * 255f);
        sampled = true;
    }

    private void beginFrame(float partialTicks) {
        if (pendingDraw) {
            // The after-terrain hook never ran (another mod changed that code), so the sky went
            // missing last frame. Draw it the normal way from now on.
            pendingDraw = false;
            if (!deferBroken) {
                deferBroken = true;
                Log.warn("Skip Hidden Sky isn't compatible with another installed mod; drawing the sky normally.");
            }
        }
        frameStarted = true;
        lastBeginNanos = System.nanoTime();
        float dt = tickTime();
        AudioManager.update(dt);
        updateEvents(dt);
        frameAlpha = computeAlpha(partialTicks);
        overlayThisFrame = isOverlay();
        deferThisFrame = shouldDefer();
    }

    private boolean shouldDefer() {
        DynamicSkiesConfig cfg = DynamicSkiesConfig.INSTANCE;
        if (!cfg.skipHiddenSky || deferBroken || SHADER_MOD) {
            return false;
        }
        if (!cfg.hideClouds) {
            // Below y=128 vanilla draws clouds between the sky and the terrain. Drawing the
            // sky after the terrain would paint over them, so keep the old order there.
            Entity view = Minecraft.getMinecraft().getRenderViewEntity();
            if (view == null || view.posY + view.getEyeHeight() < 128.0) {
                return false;
            }
        }
        return true;
    }

    private static int currentProgram() {
        return SHADER_MOD ? GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) : 0;
    }

    /** Sky color and sun angle, worked out once per frame instead of once per pass. */
    private void updateWorldColors(WorldClient world, float partialTicks) {
        sunAngle = world != null ? world.getCelestialAngle(partialTicks) * (float) Math.PI * 2f : (float) Math.PI;
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (world != null && view != null) {
            Vec3 c = world.getSkyColor(view, partialTicks);
            skyR = (float) c.xCoord;
            skyG = (float) c.yCoord;
            skyB = (float) c.zCoord;
        } else {
            skyR = 0.47f;
            skyG = 0.65f;
            skyB = 1f;
        }
    }

    private static boolean usesSpectrum(SkyShader shader, SkyType type) {
        return !type.usesImage() && shader.loc(U.TEX) >= 0;
    }

    private static boolean isOverlay() {
        DynamicSkiesConfig cfg = DynamicSkiesConfig.INSTANCE;
        if (cfg.skyMode == 1) return true;
        return cfg.skyMode == 2 && SkyType.byIndex(cfg.sky).overlayFriendly();
    }

    private float tickTime() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0f : (now - lastFrameNanos) / 1e9f;
        lastFrameNanos = now;
        dt = Math.max(0f, Math.min(0.1f, dt));

        DynamicSkiesConfig cfg = DynamicSkiesConfig.INSTANCE;
        float speed = cfg.speed * (1f + cfg.bassSpeed * cfg.reactivity * AudioManager.bass());
        speed *= 1f + AudioManager.build() * cfg.buildUpStrength * 1.5f;
        double step = dt * speed;
        time += step;
        if (wrapFade >= 0f) {
            wrapOldTime += step;
            wrapFade += dt / WRAP_FADE_SECONDS;
            if (wrapFade >= 1f) wrapFade = -1f;
        }
        if (time >= TIME_WRAP) {
            // Restarting the clock would make every sky jump, so fade between the two.
            wrapOldTime = time;
            time %= TIME_WRAP;
            wrapFade = 0f;
        }
        return dt;
    }

    /** Shooting stars, and what happens on beats and drops. */
    private void updateEvents(float dt) {
        DynamicSkiesConfig cfg = DynamicSkiesConfig.INSTANCE;
        meteors.update(dt);
        boolean audio = cfg.audioSource != 0;
        fireworks.update(dt, audio && AudioManager.beatThisFrame(), AudioManager.bass() * cfg.reactivity);

        if (cfg.shootingStars) {
            if (meteors.chance(cfg.shootingRate, dt)) {
                meteors.spawn(1f);
            }
            long now = System.nanoTime();
            if (audio && cfg.shootingOnBeat && AudioManager.beatThisFrame() && AudioManager.bass() > 0.6f
                    && now - lastBeatMeteorNanos > 1_500_000_000L) {
                lastBeatMeteorNanos = now;
                meteors.spawn(1.2f);
            }
        }

        if (audio && AudioManager.dropThisFrame()) {
            if (cfg.dropMeteors) {
                for (int i = 0; i < 3; i++) meteors.spawn(1.5f);
            }
            if (cfg.dropColorShift) {
                dropHueTarget += 0.18f + (float) Math.random() * 0.25f;
            }
            if (cfg.dropChangeSky) {
                jumpOnDrop = true;
                cfg.setSkyWithoutSaving(nextSkyIndex(cfg.sky));
            }
        }
        dropHue += (dropHueTarget - dropHue) * (1f - (float) Math.exp(-dt * 2.5));
        if (dropHueTarget > 10f) {
            dropHueTarget -= 10f;
            dropHue -= 10f;
        }
    }

    private static int nextSkyIndex(int from) {
        SkyType[] all = SkyType.values();
        boolean favoritesOnly = DynamicSkiesConfig.INSTANCE.cycleFavorites && !Favorites.isEmpty();
        int start = SkyType.byIndex(from).ordinal();
        for (int step = 1; step <= all.length; step++) {
            SkyType t = all[(start + step) % all.length];
            if (favoritesOnly ? Favorites.isFavorite(t) : !t.skipInCycle()) {
                return t.ordinal();
            }
        }
        return start;
    }

    private float computeAlpha(float partialTicks) {
        DynamicSkiesConfig cfg = DynamicSkiesConfig.INSTANCE;
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.theWorld;
        if (!cfg.enabled || world == null) {
            return 0f;
        }
        boolean end = world.provider.getDimensionId() == 1;
        if (end ? !cfg.replaceEnd : !world.provider.isSurfaceWorld()) {
            return 0f;
        }
        float alpha = cfg.opacity;
        if (cfg.nightOnly && !end) {
            float sunY = sunDirY(world, partialTicks);
            alpha *= smooth(0.15f, -0.12f, sunY);
        }
        if (cfg.fadeInRain && !end) {
            alpha *= 1f - world.getRainStrength(partialTicks) * 0.75f;
        }
        return alpha;
    }

    private static float sunDirY(WorldClient world, float partialTicks) {
        return (float) Math.cos(world.getCelestialAngle(partialTicks) * Math.PI * 2.0);
    }

    private static float smooth(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    private SkyType selectSky() {
        DynamicSkiesConfig cfg = DynamicSkiesConfig.INSTANCE;
        long now = System.nanoTime();

        if (cfg.cycleSkies && now - lastCycleNanos > (long) (cfg.cycleMinutes * 60e9)) {
            lastCycleNanos = now;
            cfg.setSkyWithoutSaving(nextSkyIndex(cfg.sky));
        } else if (!cfg.cycleSkies) {
            lastCycleNanos = now;
        }

        SkyType wanted = SkyType.byIndex(cfg.sky);
        if (current == null) {
            current = wanted;
        } else if (wanted != current) {
            previous = current;
            current = wanted;
            fadeStartNanos = now;
            // A sky change on a drop should hit with the drop, not fade in slowly.
            fadeSeconds = jumpOnDrop ? Math.min(0.3f, cfg.crossfadeSeconds) : cfg.crossfadeSeconds;
        }
        jumpOnDrop = false;
        return current;
    }

    /**
     * @param replacing the sky covers everything (also draws sun and moon if enabled)
     * @param overlay   added on top of the existing sky, dark parts see-through
     */
    private boolean draw(float partialTicks, boolean replacing, boolean overlay) {
        DynamicSkiesConfig cfg = DynamicSkiesConfig.INSTANCE;
        Minecraft mc = Minecraft.getMinecraft();
        WorldClient world = mc.theWorld;

        SkyType sky = selectSky();
        SkyShader shader = shader(sky);
        if (shader == null) {
            return false;
        }

        float fade = 1f;
        SkyShader prevShader = null;
        if (previous != null) {
            fade = fadeSeconds <= 0f ? 1f : (System.nanoTime() - fadeStartNanos) / 1e9f / fadeSeconds;
            if (fade >= 1f) {
                previous = null;
                fade = 1f;
            } else {
                prevShader = shader(previous);
            }
        }

        int prevProgram = currentProgram();
        if (cfg.matchFog) {
            // Only read back from the driver when the fog color is actually used.
            fogBuffer.clear();
            GL11.glGetFloat(GL11.GL_FOG_COLOR, fogBuffer);
            fogR = fogBuffer.get(0);
            fogG = fogBuffer.get(1);
            fogB = fogBuffer.get(2);
        }
        updateWorldColors(world, partialTicks);

        // Culling stays as it is: the cube faces inward and the sun and moon use vanilla's winding.
        GlStateManager.disableFog();
        GlStateManager.disableAlpha();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        if (usesSpectrum(shader, sky) || (prevShader != null && usesSpectrum(prevShader, previous))) {
            uploadSpectrum();
        }

        // Below Full quality the sky is drawn into a smaller buffer first and
        // then stretched over the screen with the same blending as below.
        boolean scaled = beginScaled(mc);
        float strength = scaled ? 1f : frameAlpha;
        if (scaled && overlay) {
            // Overlay passes add up in the buffer; the buffer is then blended onto the screen.
            GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            strength = frameAlpha;
        } else {
            applyBlend(overlay);
        }

        float f = smooth(0f, 1f, fade);
        if (prevShader != null) {
            // Overlays add up, so the old one fades out while the new one fades in.
            drawPass(prevShader, previous, time, strength * (overlay ? 1f - f : 1f), overlay);
            drawPass(shader, sky, time, strength * f, overlay);
        } else if (wrapFade >= 0f) {
            // Same crossfade, from the sky at the old clock to the restarted one.
            float w = smooth(0f, 1f, wrapFade);
            drawPass(shader, sky, wrapOldTime, strength * (overlay ? 1f - w : 1f), overlay);
            drawPass(shader, sky, time, strength * w, overlay);
        } else {
            drawPass(shader, sky, time, strength, overlay);
        }

        GL20.glUseProgram(prevProgram);
        if (scaled) {
            endScaled(overlay);
        }

        if (replacing && cfg.showSunMoon && world.provider.getDimensionId() != 1) {
            drawSunAndMoon(mc, world, partialTicks);
        }

        // Leave GL state the way vanilla's renderSky does.
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableFog();
        GlStateManager.enableTexture2D();
        GlStateManager.depthMask(true);
        return true;
    }

    private void drawPass(SkyShader shader, SkyType type, double t, float alpha, boolean overlay) {
        DynamicSkiesConfig cfg = DynamicSkiesConfig.INSTANCE;
        float react = cfg.audioSource == 0 ? 0f : cfg.reactivity;

        if (type.usesImage()) {
            DynamicTexture tex = image();
            if (tex == null) {
                return;
            }
            GlStateManager.bindTexture(tex.getGlTextureId());
        } else if (shader.loc(U.TEX) >= 0) {
            GlStateManager.bindTexture(spectrumTexture);
        }

        // Uniform locations are looked up once per shader (SkyShader.U), not by name every frame.
        shader.bind();
        shader.setInt(U.TEX, 0);
        shader.set(U.TIME, (float) t);
        shader.set(U.BASS, AudioManager.bass() * react);
        shader.set(U.MID, AudioManager.mid() * react);
        shader.set(U.HIGH, AudioManager.high() * react);
        shader.set(U.LEVEL, AudioManager.level() * react);
        shader.set(U.BEAT, AudioManager.beat() * react);

        shader.set(U.SUN_DIR, -(float) Math.sin(sunAngle), (float) Math.cos(sunAngle), 0f);
        shader.set(U.SKY_COLOR, skyR, skyG, skyB);
        shader.set(U.FOG_COLOR, fogR, fogG, fogB);
        shader.set(U.FOG, cfg.matchFog && !previewing ? cfg.fogStrength : 0f);

        int tint = cfg.tint.getArgb();
        shader.set(U.TINT, ((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f);
        shader.set(U.BRIGHTNESS, cfg.brightness);
        shader.set(U.SATURATION, cfg.saturation);
        shader.set(U.HUE, cfg.hueShift / 360f + dropHue);
        shader.set(U.AUDIO_HUE, cfg.audioHue * react);
        shader.set(U.BEAT_FLASH, cfg.beatFlash * react);
        shader.set(U.IMAGE_ROT, (float) (t * cfg.imageRotation * 0.05));
        shader.set(U.ALPHA, alpha);
        shader.set(U.OVERLAY, overlay ? 1f : 0f);
        shader.set(U.OVERLAY_CUTOFF, cfg.overlayCutoff);

        boolean drops = react > 0f && cfg.dropDetection;
        shader.set(U.BUILD, drops ? AudioManager.build() * cfg.buildUpStrength : 0f);
        shader.set(U.DROP_AGE, drops ? AudioManager.dropAge() : 100f);
        shader.set(U.DROP_FLASH, cfg.dropFlash ? 1f : 0f);
        shader.set(U.DROP_WAVE, cfg.dropShockwave ? 1f : 0f);

        shader.set(U.LAYER_STARS, cfg.layerStars);
        shader.set(U.LAYER_AURORA, cfg.layerAurora);
        shader.set(U.LAYER_WISPS, cfg.layerWisps);
        boolean meteorsOn = cfg.shootingStars || cfg.dropMeteors;
        for (int i = 0; i < Meteors.MAX; i++) {
            float[] s = meteors.start[i];
            float[] m = meteors.dir[i];
            shader.set(METEOR_NAMES[i], s[0], s[1], s[2], meteorsOn ? s[3] : -1f);
            shader.set(METEOR_DIR_NAMES[i], m[0], m[1], m[2], m[3]);
        }
        if (type == SkyType.FIREWORKS) {
            for (int i = 0; i < Fireworks.MAX; i++) {
                float[] s = fireworks.shell[i];
                float[] f = fireworks.info[i];
                shader.set(SHELL_NAMES[i], s[0], s[1], s[2], s[3]);
                shader.set(SHELL_INFO_NAMES[i], f[0], f[1], f[2], f[3]);
            }
        }

        drawCube();
    }

    private static void applyBlend(boolean overlay) {
        DynamicSkiesConfig cfg = DynamicSkiesConfig.INSTANCE;
        if (!overlay) {
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        } else if (cfg.overlayBlend == 0) {
            // Add: the shader outputs color already multiplied by its strength.
            GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        } else {
            // Screen: 1 - (1 - src)(1 - dst), never brighter than white.
            GlStateManager.tryBlendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        }
    }

    private static float qualityScale() {
        switch (DynamicSkiesConfig.INSTANCE.renderQuality) {
            case 1: return 0.75f;
            case 2: return 0.5f;
            case 3: return 1f / 3f;
            default: return 1f;
        }
    }

    /**
     * Switches drawing to the low-resolution sky buffer. Returns false (draw
     * straight to the screen) at Full quality or if framebuffers are off.
     */
    private boolean beginScaled(Minecraft mc) {
        float scale = qualityScale();
        if (scale >= 0.999f || !OpenGlHelper.isFramebufferEnabled()) {
            return false;
        }
        scaledPrevFbo = GL11.glGetInteger(GL_FRAMEBUFFER_BINDING);
        viewportBuffer.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewportBuffer);

        int w = Math.max(1, Math.round(viewportBuffer.get(2) * scale));
        int h = Math.max(1, Math.round(viewportBuffer.get(3) * scale));
        if (skyBuffer == null) {
            skyBuffer = new Framebuffer(w, h, false);
            skyBuffer.setFramebufferColor(0f, 0f, 0f, 1f);
            skyBuffer.setFramebufferFilter(GL11.GL_LINEAR);
        } else if (skyBuffer.framebufferWidth != w || skyBuffer.framebufferHeight != h) {
            skyBuffer.createBindFramebuffer(w, h);
            skyBuffer.setFramebufferFilter(GL11.GL_LINEAR);
        }
        skyBuffer.framebufferClear();
        skyBuffer.bindFramebuffer(true);
        // The buffer's alpha stays 1; how see-through the sky is gets applied when it's drawn to the screen.
        GlStateManager.colorMask(true, true, true, false);
        return true;
    }

    /** Stretches the low-resolution sky over the screen and switches back. */
    private void endScaled(boolean overlay) {
        GlStateManager.colorMask(true, true, true, true);
        OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER, scaledPrevFbo);
        GL11.glViewport(viewportBuffer.get(0), viewportBuffer.get(1), viewportBuffer.get(2), viewportBuffer.get(3));

        applyBlend(overlay);
        if (!drawingDeferred) {
            GlStateManager.disableDepth();
        }
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(skyBuffer.framebufferTexture);
        GlStateManager.color(1f, 1f, 1f, overlay ? 1f : frameAlpha);

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();

        float u = skyBuffer.framebufferWidth / (float) skyBuffer.framebufferTextureWidth;
        float v = skyBuffer.framebufferHeight / (float) skyBuffer.framebufferTextureHeight;
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(-1, -1, 0).tex(0, 0).endVertex();
        wr.pos(1, -1, 0).tex(u, 0).endVertex();
        wr.pos(1, 1, 0).tex(u, v).endVertex();
        wr.pos(-1, 1, 0).tex(0, v).endVertex();
        tess.draw();

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        GlStateManager.enableDepth();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    /**
     * Inward-facing cube around the camera; the shader turns each pixel into a view direction.
     * Built once into a display list (as vanilla does with its sky) rather than every pass.
     */
    private void drawCube() {
        if (cubeList < 0) {
            cubeList = GLAllocation.generateDisplayLists(1);
            GL11.glNewList(cubeList, GL11.GL_COMPILE);
            buildCube();
            GL11.glEndList();
        }
        GL11.glCallList(cubeList);
    }

    private static void buildCube() {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        float s = 10f;
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        // +Y / -Y
        wr.pos(-s, s, -s).endVertex(); wr.pos(s, s, -s).endVertex(); wr.pos(s, s, s).endVertex(); wr.pos(-s, s, s).endVertex();
        wr.pos(-s, -s, -s).endVertex(); wr.pos(-s, -s, s).endVertex(); wr.pos(s, -s, s).endVertex(); wr.pos(s, -s, -s).endVertex();
        // +X / -X
        wr.pos(s, -s, -s).endVertex(); wr.pos(s, -s, s).endVertex(); wr.pos(s, s, s).endVertex(); wr.pos(s, s, -s).endVertex();
        wr.pos(-s, -s, -s).endVertex(); wr.pos(-s, s, -s).endVertex(); wr.pos(-s, s, s).endVertex(); wr.pos(-s, -s, s).endVertex();
        // +Z / -Z
        wr.pos(-s, -s, s).endVertex(); wr.pos(-s, s, s).endVertex(); wr.pos(s, s, s).endVertex(); wr.pos(s, -s, s).endVertex();
        wr.pos(-s, -s, -s).endVertex(); wr.pos(s, -s, -s).endVertex(); wr.pos(s, s, -s).endVertex(); wr.pos(-s, s, -s).endVertex();
        tess.draw();
    }

    private static void drawSunAndMoon(Minecraft mc, WorldClient world, float partialTicks) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.pushMatrix();
        GlStateManager.color(1f, 1f, 1f, 1f - world.getRainStrength(partialTicks));
        GlStateManager.rotate(-90f, 0f, 1f, 0f);
        GlStateManager.rotate(world.getCelestialAngle(partialTicks) * 360f, 1f, 0f, 0f);
        // A tenth of vanilla's distance and size: same look, but inside the terrain's far
        // plane so it isn't clipped when drawn after the terrain at low render distances.
        GlStateManager.scale(0.1f, 0.1f, 0.1f);

        float size = 30f;
        mc.getTextureManager().bindTexture(SUN);
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(-size, 100, -size).tex(0, 0).endVertex();
        wr.pos(size, 100, -size).tex(1, 0).endVertex();
        wr.pos(size, 100, size).tex(1, 1).endVertex();
        wr.pos(-size, 100, size).tex(0, 1).endVertex();
        tess.draw();

        size = 20f;
        mc.getTextureManager().bindTexture(MOON);
        int phase = world.getMoonPhase();
        int col = phase % 4, row = phase / 4 % 2;
        float u0 = col / 4f, v0 = row / 2f, u1 = (col + 1) / 4f, v1 = (row + 1) / 2f;
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(-size, -100, size).tex(u1, v1).endVertex();
        wr.pos(size, -100, size).tex(u0, v1).endVertex();
        wr.pos(size, -100, -size).tex(u0, v0).endVertex();
        wr.pos(-size, -100, -size).tex(u1, v0).endVertex();
        tess.draw();

        GlStateManager.popMatrix();
    }

    private void uploadSpectrum() {
        if (spectrumTexture < 0) {
            spectrumTexture = GL11.glGenTextures();
            GlStateManager.bindTexture(spectrumTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            spectrumData.clear();
            for (int i = 0; i < AudioManager.BANDS; i++) spectrumData.put((byte) 0);
            spectrumData.flip();
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_LUMINANCE, AudioManager.BANDS, 1, 0,
                    GL11.GL_LUMINANCE, GL11.GL_UNSIGNED_BYTE, spectrumData);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
        }
        float[] spec = AudioManager.spectrum();
        boolean changed = !spectrumUploaded;
        for (int i = 0; i < AudioManager.BANDS; i++) {
            byte b = (byte) Math.round(Math.max(0f, Math.min(1f, spec[i])) * 255f);
            if (b != uploadedSpectrum[i]) {
                uploadedSpectrum[i] = b;
                changed = true;
            }
        }
        if (!changed) {
            // Silence or audio off: the texture already holds these values.
            return;
        }
        spectrumUploaded = true;
        spectrumData.clear();
        spectrumData.put(uploadedSpectrum);
        spectrumData.flip();
        GlStateManager.bindTexture(spectrumTexture);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, AudioManager.BANDS, 1,
                GL11.GL_LUMINANCE, GL11.GL_UNSIGNED_BYTE, spectrumData);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
    }

    private DynamicTexture image() {
        String name = DynamicSkiesConfig.INSTANCE.imageFile;
        if (name == null || name.trim().isEmpty()) {
            name = "sky.png";
        }
        if (!imageReloadRequested && name.equals(loadedImage)) {
            return image;
        }
        imageReloadRequested = false;
        loadedImage = name;
        if (image != null) {
            image.deleteGlTexture();
            image = null;
        }
        File file = new File(DynamicSkiesConfig.imageFolder(), name.trim());
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                throw new IllegalStateException("not a readable image");
            }
            image = new DynamicTexture(img);
            GlStateManager.bindTexture(image.getGlTextureId());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            imageMissingReported = false;
        } catch (Throwable t) {
            if (!imageMissingReported) {
                imageMissingReported = true;
                Log.warn("Couldn't load custom sky image " + file.getAbsolutePath() + ": " + t.getMessage());
            }
        }
        return image;
    }

    /**
     * The user's shader from config/dynamicskies/skies/. Reloads when the file
     * changes (Live Reload) or on the Reload button; a broken edit keeps the
     * last working version and reports the error in chat.
     */
    private SkyShader customShader() {
        DynamicSkiesConfig cfg = DynamicSkiesConfig.INSTANCE;
        File folder = DynamicSkiesConfig.shaderFolder();
        if (!exampleWritten) {
            exampleWritten = true;
            writeExample(folder);
        }

        String name = cfg.customShader == null || cfg.customShader.trim().isEmpty() ? "example.fsh" : cfg.customShader.trim();
        long now = System.nanoTime();
        boolean nameChanged = !name.equals(customName);
        boolean manual = shaderReloadRequested;
        if (!nameChanged && !manual && !(cfg.liveReload && now - lastCustomCheckNanos > 500_000_000L)) {
            return custom;
        }
        lastCustomCheckNanos = now;

        File file = new File(folder, name);
        long modified = file.isFile() ? file.lastModified() : -1L;
        if (!nameChanged && !manual && modified == customModified) {
            return custom;
        }
        shaderReloadRequested = false;
        customName = name;
        customModified = modified;

        if (modified < 0) {
            // Only on the button: the file name updates on every keystroke while typing it.
            if (manual) {
                DynamicSkiesConfig.chat("§cShader file not found: " + file.getAbsolutePath());
            }
            deleteCustom();
            return null;
        }
        try {
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            SkyShader compiled = SkyShader.compile(name, source);
            deleteCustom();
            custom = compiled;
            DynamicSkiesConfig.chat("§aLoaded shader " + name);
        } catch (Throwable t) {
            DynamicSkiesConfig.chat("§cShader " + name + " has an error" + (custom != null && !nameChanged ? " (keeping the last working version)" : "") + ":");
            String msg = String.valueOf(t.getMessage());
            String[] lines = msg.split("\n");
            int shown = 0;
            for (int i = 1; i < lines.length && shown < 4; i++) {
                if (!lines[i].trim().isEmpty()) {
                    DynamicSkiesConfig.chat("§7" + lines[i].trim());
                    shown++;
                }
            }
            Log.error("Custom shader failed to compile: " + msg);
            if (nameChanged) {
                deleteCustom();
            }
        }
        return custom;
    }

    private void deleteCustom() {
        if (custom != null) {
            custom.delete();
            custom = null;
        }
    }

    private static void writeExample(File folder) {
        File example = new File(folder, "example.fsh");
        if (example.exists()) {
            return;
        }
        try {
            Files.write(example.toPath(), SkyShader.read("example_custom.fsh").getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            Log.warn("Couldn't write example shader: " + t.getMessage());
        }
    }

    private SkyShader shader(SkyType type) {
        if (type == SkyType.CUSTOM_SHADER) {
            return customShader();
        }
        SkyShader s = shaders.get(type);
        if (s != null || broken.containsKey(type)) {
            return s;
        }
        try {
            s = SkyShader.compile(type);
            shaders.put(type, s);
        } catch (Throwable t) {
            broken.put(type, Boolean.TRUE);
            Log.error("Sky '" + type.id + "' failed to compile: " + t.getMessage());
        }
        return s;
    }
}
