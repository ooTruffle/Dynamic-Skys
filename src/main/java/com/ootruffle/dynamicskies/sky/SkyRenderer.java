package com.ootruffle.dynamicskies.sky;

import com.ootruffle.dynamicskies.Log;
import com.ootruffle.dynamicskies.audio.AudioManager;
import com.ootruffle.dynamicskies.config.DynamicSkiesConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;

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
    private double time;
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
    private final FloatBuffer fogBuffer = BufferUtils.createFloatBuffer(16);

    private DynamicTexture image;
    private String loadedImage;
    private boolean imageReloadRequested;
    private boolean imageMissingReported;

    private final Meteors meteors = new Meteors();
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
        Minecraft mc = Minecraft.getMinecraft();
        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

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
        previewing = true;
        try {
            drawPass(shader, type, 1f, false, mc.theWorld, 1f, 0f, 0f, 0f);
        } finally {
            previewing = false;
        }

        GL20.glUseProgram(prevProgram);
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();
        GlStateManager.enableTexture2D();
        return PREVIEW_OK;
    }

    private void beginFrame(float partialTicks) {
        frameStarted = true;
        lastBeginNanos = System.nanoTime();
        float dt = tickTime();
        AudioManager.update(dt);
        updateEvents(dt);
        frameAlpha = computeAlpha(partialTicks);
        overlayThisFrame = isOverlay();
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
        time = (time + dt * speed) % 3600.0;
        return dt;
    }

    /** Shooting stars, and what happens on beats and drops. */
    private void updateEvents(float dt) {
        DynamicSkiesConfig cfg = DynamicSkiesConfig.INSTANCE;
        meteors.update(dt);
        boolean audio = cfg.audioSource != 0;

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
                cfg.setSky(nextSkyIndex(cfg.sky));
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
            cfg.setSky(nextSkyIndex(cfg.sky));
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

        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean cullWasOn = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        fogBuffer.clear();
        GL11.glGetFloat(GL11.GL_FOG_COLOR, fogBuffer);
        float fogR = fogBuffer.get(0), fogG = fogBuffer.get(1), fogB = fogBuffer.get(2);

        GlStateManager.disableFog();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);
        uploadSpectrum();

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
            drawPass(prevShader, previous, strength * (overlay ? 1f - f : 1f), overlay, world, partialTicks, fogR, fogG, fogB);
            drawPass(shader, sky, strength * f, overlay, world, partialTicks, fogR, fogG, fogB);
        } else {
            drawPass(shader, sky, strength, overlay, world, partialTicks, fogR, fogG, fogB);
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
        if (cullWasOn) {
            GlStateManager.enableCull();
        }
        GlStateManager.enableFog();
        GlStateManager.enableTexture2D();
        GlStateManager.depthMask(true);
        return true;
    }

    private void drawPass(SkyShader shader, SkyType type, float alpha, boolean overlay, WorldClient world, float partialTicks,
                          float fogR, float fogG, float fogB) {
        DynamicSkiesConfig cfg = DynamicSkiesConfig.INSTANCE;
        float react = cfg.audioSource == 0 ? 0f : cfg.reactivity;

        if (type.usesImage()) {
            DynamicTexture tex = image();
            if (tex == null) {
                return;
            }
            GlStateManager.bindTexture(tex.getGlTextureId());
        } else {
            GlStateManager.bindTexture(spectrumTexture);
        }

        shader.bind();
        shader.setInt("uTex", 0);
        shader.set("uTime", (float) time);
        shader.set("uBass", AudioManager.bass() * react);
        shader.set("uMid", AudioManager.mid() * react);
        shader.set("uHigh", AudioManager.high() * react);
        shader.set("uLevel", AudioManager.level() * react);
        shader.set("uBeat", AudioManager.beat() * react);

        float angle = world != null ? world.getCelestialAngle(partialTicks) * (float) Math.PI * 2f : (float) Math.PI;
        shader.set("uSunDir", -(float) Math.sin(angle), (float) Math.cos(angle), 0f);
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        if (world != null && view != null) {
            Vec3 c = world.getSkyColor(view, partialTicks);
            shader.set("uSkyColor", (float) c.xCoord, (float) c.yCoord, (float) c.zCoord);
        } else {
            shader.set("uSkyColor", 0.47f, 0.65f, 1f);
        }
        shader.set("uFogColor", fogR, fogG, fogB);
        shader.set("uFog", cfg.matchFog && !previewing ? cfg.fogStrength : 0f);

        int tint = cfg.tint.getArgb();
        shader.set("uTint", ((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f);
        shader.set("uBrightness", cfg.brightness);
        shader.set("uSaturation", cfg.saturation);
        shader.set("uHue", cfg.hueShift / 360f + dropHue);
        shader.set("uAudioHue", cfg.audioHue * react);
        shader.set("uBeatFlash", cfg.beatFlash * react);
        shader.set("uImageRot", (float) (time * cfg.imageRotation * 0.05));
        shader.set("uAlpha", alpha);
        shader.set("uOverlay", overlay ? 1f : 0f);
        shader.set("uOverlayCutoff", cfg.overlayCutoff);

        boolean drops = react > 0f && cfg.dropDetection;
        shader.set("uBuild", drops ? AudioManager.build() * cfg.buildUpStrength : 0f);
        shader.set("uDropAge", drops ? AudioManager.dropAge() : 100f);
        shader.set("uDropFlash", cfg.dropFlash ? 1f : 0f);
        shader.set("uDropWave", cfg.dropShockwave ? 1f : 0f);

        shader.set("uLayerStars", cfg.layerStars);
        shader.set("uLayerAurora", cfg.layerAurora);
        shader.set("uLayerWisps", cfg.layerWisps);
        for (int i = 0; i < Meteors.MAX; i++) {
            float[] s = meteors.start[i];
            float[] m = meteors.dir[i];
            shader.set("uMeteors[" + i + "]", s[0], s[1], s[2], cfg.shootingStars || cfg.dropMeteors ? s[3] : -1f);
            shader.set("uMeteorDirs[" + i + "]", m[0], m[1], m[2], m[3]);
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
        GlStateManager.disableDepth();
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

    /** Inward-facing cube around the camera; the shader turns each pixel into a view direction. */
    private static void drawCube() {
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
        spectrumData.clear();
        for (int i = 0; i < AudioManager.BANDS; i++) {
            spectrumData.put((byte) Math.round(Math.max(0f, Math.min(1f, spec[i])) * 255f));
        }
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
