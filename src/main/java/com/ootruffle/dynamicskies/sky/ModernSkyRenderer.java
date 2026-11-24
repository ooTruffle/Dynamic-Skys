package com.ootruffle.dynamicskies.sky;

import com.ootruffle.dynamicskies.audio.AudioManager;
import com.ootruffle.dynamicskies.modern.ModernClient;
import com.ootruffle.dynamicskies.sky.SkyShader.U;
import com.mojang.logging.LogUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
//? if >= 26.3 {
/*import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.renderpearl.backend.opengl.GlTexture;
*///?} elif >= 1.21.5 {
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
//?}
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumMap;
import java.util.Map;

/**
 * The modern (core profile) counterpart of the 1.8.9 SkyRenderer: one instance
 * shared by the world sky and the sky browser, so time, audio, shooting stars
 * and compiled shaders carry over between them.
 */
public final class ModernSkyRenderer {

    public static final ModernSkyRenderer INSTANCE = new ModernSkyRenderer();

    public static final int PREVIEW_OK = 0;
    public static final int PREVIEW_LOADING = 1;
    public static final int PREVIEW_FAILED = 2;

    private static final Logger LOG = LogUtils.getLogger();
    private static final double TIME_WRAP = 3600.0;
    private static final float WRAP_FADE_SECONDS = 4f;

    private final Map<SkyType, SkyShader> shaders = new EnumMap<>(SkyType.class);
    private final Map<SkyType, Boolean> broken = new EnumMap<>(SkyType.class);

    private SkyType current;
    private SkyType previous;
    private long fadeStartNanos;
    private float fadeSeconds;
    private boolean jumpOnDrop;
    private long lastCycleNanos = System.nanoTime();

    private long lastFrameNanos;
    private long lastWorldFrameNanos;
    private long lastSkyDrawnNanos;
    private int mainFbo;
    private boolean vulkanReported;
    /** Animation clock in seconds. It wraps at TIME_WRAP so it stays precise as a GLSL float. */
    private double time;
    private double wrapOldTime;
    private float wrapFade = -1f;

    private final Meteors meteors = new Meteors();
    private final Fireworks fireworks = new Fireworks();
    private long lastBeatMeteorNanos;
    private float dropHue, dropHueTarget;

    private boolean previewing;
    private long compileDeadlineNanos;
    private int compiledThisFrame;

    // Values for the frame being drawn.
    private float sunAngle = (float) Math.PI;
    private float skyR = 0.47f, skyG = 0.65f, skyB = 1f;
    private float fogR, fogG, fogB;
    private final Matrix4f inverse = new Matrix4f();
    private final Matrix4f scratchView = new Matrix4f();
    private final Matrix4f scratchProjection = new Matrix4f();

    private int vao;
    private int spectrumTexture;

    private int imageTexture;
    private String imageName;
    private long imageModified;
    private boolean imageReloadRequested;
    private boolean imageMissingReported;

    private SkyShader custom;
    private String customName;
    private long customModified;
    private boolean shaderReloadRequested;
    private boolean exampleWritten;

    // Render Quality: the sky is drawn into this smaller buffer, then stretched over the screen.
    private int skyFbo, skyFboTexture, skyFboW, skyFboH;
    private int blitProgram, blitAlphaLoc, blitUvLoc, blitDepthLoc;
    private int skyFboDepth;
    private int prepassProgram, prepassUseDepthLoc, prepassMapLoc, prepassSizeLoc, prepassValuesLoc;
    private boolean skyFboComplete;
    private int scaledW, scaledH;
    private int fillProgram, fillColorLoc;
    private int scaledDrawFbo, scaledReadFbo;
    private final int[] viewport = new int[4];

    private ModernSkyRenderer() {
    }

    public void requestShaderReload() {
        shaderReloadRequested = true;
    }

    public void requestImageReload() {
        imageReloadRequested = true;
    }

    // ---- World sky -----------------------------------------------------------

    // The world sky is drawn right after the solid terrain with a depth test, so the sky
    // shader only runs where the sky can actually be seen (not behind hills, walls or
    // leaves). The sky pass records what to draw and afterOpaqueTerrain() draws it. If that
    // hook stops running (another mod replaced terrain rendering), the sky goes back to
    // being drawn in the sky pass, over everything, the way it was before.
    private static final int LATE_MISS_LIMIT = 3;
    private boolean pending;
    private int lateMisses;
    private final Matrix4f pendingView = new Matrix4f();
    private final Matrix4f pendingProjection = new Matrix4f();
    private float pendingSun, pendingR, pendingG, pendingB, pendingFogR, pendingFogG, pendingFogB, pendingRain;
    private boolean pendingEnd;

    /** Called from the sky pass every frame: moves time on and queues the sky for after the terrain. */
    public void draw(Matrix4fc view, Matrix4fc projection, float sunAngle,
                     float skyR, float skyG, float skyB, float fogR, float fogG, float fogB,
                     float rain, boolean end) {
        lastWorldFrameNanos = System.nanoTime();
        float dt = tickTime();
        AudioManager.update(dt);
        updateEvents(dt);

        // Still pending: last frame's terrain hook never ran.
        if (pending && ++lateMisses == LATE_MISS_LIMIT) {
            LOG.warn("Dynamic Skies: the after-terrain hook isn't running (another mod may replace terrain rendering); "
                    + "drawing the sky without skipping hidden pixels.");
        }
        pending = false;
        if (lateMisses >= LATE_MISS_LIMIT) {
            drawSky(view, projection, sunAngle, skyR, skyG, skyB, fogR, fogG, fogB, rain, end, false);
            return;
        }
        pendingView.set(view);
        pendingProjection.set(projection);
        pendingSun = sunAngle;
        pendingR = skyR;
        pendingG = skyG;
        pendingB = skyB;
        pendingFogR = fogR;
        pendingFogG = fogG;
        pendingFogB = fogB;
        pendingRain = rain;
        pendingEnd = end;
        pending = true;
    }

    /** Called right after the solid terrain is drawn: draws the queued sky where nothing covers it. */
    public void afterOpaqueTerrain() {
        if (!pending) return;
        pending = false;
        lateMisses = 0;
        drawSky(pendingView, pendingProjection, pendingSun, pendingR, pendingG, pendingB,
                pendingFogR, pendingFogG, pendingFogB, pendingRain, pendingEnd, true);
    }

    private void drawSky(Matrix4fc view, Matrix4fc projection, float sunAngle,
                         float skyR, float skyG, float skyB, float fogR, float fogG, float fogB,
                         float rain, boolean end, boolean depthTest) {
        var settings = ModernClient.SETTINGS;
        if (!settings.enabled || (end && !settings.replaceEnd) || !openGl()) return;
        float alpha = settings.opacity;
        if (settings.nightOnly && !end) alpha *= smooth(0.15f, -0.12f, (float) Math.cos(sunAngle));
        if (settings.fadeInRain && !end) alpha *= 1f - rain * 0.75f;
        if (alpha <= 0.001f) return;

        this.sunAngle = sunAngle;
        this.skyR = skyR;
        this.skyG = skyG;
        this.skyB = skyB;
        this.fogR = fogR;
        this.fogG = fogG;
        this.fogB = fogB;
        inverse.set(projection).mul(scratchView.set(view).setTranslation(0, 0, 0)).invert();

        SkyType sky = selectSky();
        SkyShader shader = shader(sky);
        if (shader == null || (sky.usesImage() && image() == 0)) return;

        float fade = 1f;
        SkyShader prevShader = null;
        if (previous != null) {
            fade = fadeSeconds <= 0f ? 1f : (System.nanoTime() - fadeStartNanos) / 1e9f / fadeSeconds;
            if (fade >= 1f) {
                previous = null;
                fade = 1f;
            } else {
                prevShader = shader(previous);
                if (prevShader != null && previous.usesImage() && image() == 0) prevShader = null;
            }
        }

        boolean overlay = settings.skyMode == 1 || (settings.skyMode == 2 && sky.overlayFriendly());
        GlState state = glState.save(false);
        try {
            int drawFbo = state.drawFbo, readFbo = state.readFbo;
            int vx = state.viewport[0], vy = state.viewport[1], vw = state.viewport[2], vh = state.viewport[3];
            //? if >= 1.21.5 {
            if (!bindMainTarget(true, depthTest)) return;
            var target = com.ootruffle.dynamicskies.modern.Compat.mainRenderTarget();
            drawFbo = readFbo = mainFbo;
            vx = vy = 0;
            vw = target.width;
            vh = target.height;
            //?}
            int depth = depthTest ? mainDepthTexture() : 0;
            prepareState();
            boolean scaled = beginScaled(drawFbo, readFbo, vx, vy, vw, vh, depth);
            if (!scaled && depth != 0) {
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthFunc(skyDepthFunc());
            }
            float strength = scaled ? 1f : alpha;
            if (scaled && overlay) {
                // Overlay passes add up in the buffer; the buffer is then blended onto the screen.
                GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
                strength = alpha;
            } else {
                applyBlend(overlay);
            }

            float f = smooth(0f, 1f, fade);
            if (prevShader != null) {
                // Overlays add up, so the old one fades out while the new one fades in.
                drawPass(prevShader, previous, time, strength * (overlay ? 1f - f : 1f), overlay);
                drawPass(shader, sky, time, strength * f, overlay);
            } else if (wrapFade >= 0f) {
                float w = smooth(0f, 1f, wrapFade);
                drawPass(shader, sky, wrapOldTime, strength * (overlay ? 1f - w : 1f), overlay);
                drawPass(shader, sky, time, strength * w, overlay);
            } else {
                drawPass(shader, sky, time, strength, overlay);
            }
            if (scaled) endScaled(overlay, alpha, depth != 0);
        } finally {
            state.restore();
        }
        lastSkyDrawnNanos = System.nanoTime();
    }

    // ---- Depth ----------------------------------------------------------------

    //? if >= 26.3 {
    /*private static final boolean REVERSED_DEPTH = true;
    *///?} else
    private static final boolean REVERSED_DEPTH = false;
    private int clipZeroToOne = -1;

    /** The cleared (far) depth value: 1 normally, 0 with 26.3's reversed depth. */
    private static float farDepth() {
        return REVERSED_DEPTH ? 0f : 1f;
    }

    private static int skyDepthFunc() {
        return REVERSED_DEPTH ? GL11.GL_GEQUAL : GL11.GL_LEQUAL;
    }

    /** Clip-space z of the far plane, where the sky sits so the depth test keeps it behind everything. */
    private float skyClipZ() {
        if (!REVERSED_DEPTH) return 1f;
        if (clipZeroToOne < 0) {
            var caps = org.lwjgl.opengl.GL.getCapabilities();
            boolean clipControl = caps.OpenGL45 || caps.GL_ARB_clip_control;
            // GL_CLIP_DEPTH_MODE == GL_ZERO_TO_ONE: what 26.3 sets when the driver supports it.
            clipZeroToOne = clipControl && GL11.glGetInteger(0x935D) == 0x935F ? 1 : 0;
        }
        return clipZeroToOne == 1 ? 0f : -1f;
    }

    /** The main render target's depth texture, or 0 when it has none. */
    private static int mainDepthTexture() {
        var target = com.ootruffle.dynamicskies.modern.Compat.mainRenderTarget();
        //? if >= 1.21.5 {
        return depthOf(target) instanceof GlTexture depth ? depth.glId() : 0;
        //?} else {
        /*return target.useDepth ? target.getDepthTextureId() : 0;
        *///?}
    }

    //? if >= 26.3 {
    /*private static Object depthOf(com.mojang.blaze3d.pipeline.RenderTarget target) {
        return target.hasDepth() ? target.getDepthTexture() : null;
    }
    *///?} elif >= 1.21.5 {
    private static Object depthOf(com.mojang.blaze3d.pipeline.RenderTarget target) {
        return target.useDepth ? target.getDepthTexture() : null;
    }
    //?}

    /** True while the dynamic sky is showing and Hide Clouds is on (clouds draw right after the sky). */
    public boolean shouldHideClouds() {
        return ModernClient.SETTINGS.hideClouds && System.nanoTime() - lastSkyDrawnNanos < 250_000_000L;
    }

    /**
     * Whether the game renders through OpenGL. From 26.2 the player can pick Vulkan, and
     * then there's no GL context: every GL call would crash, so the dynamic sky stays off.
     */
    private boolean openGl() {
        //? if >= 1.21.5 {
        var target = com.ootruffle.dynamicskies.modern.Compat.mainRenderTarget();
        boolean gl = target != null && target.getColorTexture() instanceof GlTexture;
        if (!gl && !vulkanReported) {
            vulkanReported = true;
            ModernClient.chat("§eDynamic Skies needs the OpenGL graphics API. Switch Video Settings → Graphics API to OpenGL to see it.");
        }
        return gl;
        //?} else {
        /*return true;
        *///?}
    }

    /**
     * Keeps time, audio and shooting stars moving in the sky browser when the
     * world's sky isn't being drawn (no world, sky disabled, paused).
     */
    public void tickForPreview() {
        if (System.nanoTime() - lastWorldFrameNanos > 100_000_000L) {
            float dt = tickTime();
            AudioManager.update(dt);
            updateEvents(dt);
        }
    }

    // ---- Sky browser previews -------------------------------------------------

    /**
     * Call once per frame before drawing previews. Skies that aren't compiled yet
     * get compiled a few per frame (about 6 ms' worth), so opening the sky
     * browser doesn't freeze the game while all of them compile at once.
     */
    public void beginPreviewFrame() {
        compileDeadlineNanos = System.nanoTime() + 6_000_000L;
        compiledThisFrame = 0;
        queuedPreviews.clear();
        queuedDim = null;
    }

    private record QueuedPreview(SkyType type, int x, int y, int w, int h, int clipX, int clipY, int clipW, int clipH,
                                 float yaw, float pitch, float fov) {
    }

    private final java.util.List<QueuedPreview> queuedPreviews = new java.util.ArrayList<>();
    private final Map<SkyType, Integer> lastPreviewStatus = new EnumMap<>(SkyType.class);
    private float[] queuedDim;

    /**
     * A preview for the sky browser. Up to 1.21.5 it's drawn right away. From 1.21.6 the GUI
     * is drawn at the end of the frame (over anything drawn now), so it's queued and drawn by
     * {@link #drawQueuedPreviews()} just before the GUI; the status is then last frame's.
     */
    public int requestPreview(SkyType type, int x, int y, int w, int h,
                              int clipX, int clipY, int clipW, int clipH,
                              float yaw, float pitch, float fov) {
        //? if >= 1.21.6 {
        /*queuedPreviews.add(new QueuedPreview(type, x, y, w, h, clipX, clipY, clipW, clipH, yaw, pitch, fov));
        return lastPreviewStatus.getOrDefault(type, PREVIEW_LOADING);
        *///?} else
        return renderPreview(type, x, y, w, h, clipX, clipY, clipW, clipH, yaw, pitch, fov);
    }

    /** The sky browser's backdrop: drawn right away up to 1.21.5, queued like the previews after. */
    public void requestDim(float r, float g, float b, float a) {
        //? if >= 1.21.6 {
        /*queuedDim = new float[]{r, g, b, a};
        *///?} else
        dimScreen(r, g, b, a);
    }

    /** Draws the queued backdrop and previews; called just before the GUI is drawn. */
    public void drawQueuedPreviews() {
        if (queuedDim == null && queuedPreviews.isEmpty()) return;
        if (queuedDim != null) dimScreen(queuedDim[0], queuedDim[1], queuedDim[2], queuedDim[3]);
        compileDeadlineNanos = System.nanoTime() + 6_000_000L;
        compiledThisFrame = 0;
        for (QueuedPreview p : queuedPreviews) {
            lastPreviewStatus.put(p.type(), renderPreview(p.type(), p.x(), p.y(), p.w(), p.h(),
                    p.clipX(), p.clipY(), p.clipW(), p.clipH(), p.yaw(), p.pitch(), p.fov()));
        }
        queuedPreviews.clear();
        queuedDim = null;
    }

    /**
     * Draws one sky into a window rectangle (real pixels, y from the bottom),
     * looking toward yaw/pitch in degrees. Only the clip rectangle is touched.
     */
    public int renderPreview(SkyType type, int x, int y, int w, int h,
                             int clipX, int clipY, int clipW, int clipH,
                             float yaw, float pitch, float fov) {
        if (clipW <= 0 || clipH <= 0 || w <= 0 || h <= 0) return PREVIEW_OK;
        if (!openGl()) return PREVIEW_FAILED;
        boolean ready = type == SkyType.CUSTOM_SHADER || shaders.containsKey(type) || broken.containsKey(type);
        if (!ready) {
            if (compiledThisFrame > 0 && System.nanoTime() > compileDeadlineNanos) return PREVIEW_LOADING;
            compiledThisFrame++;
        }
        SkyShader shader = shader(type);
        if (shader == null || (type.usesImage() && image() == 0)) return PREVIEW_FAILED;

        // The sun stays where the world sky last had it (straight down before any world).
        scratchProjection.setPerspective((float) Math.toRadians(fov), w / (float) h, 0.05f, 100f);
        scratchView.rotationX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw + 180f));
        inverse.set(scratchProjection).mul(scratchView).invert();

        GlState state = glState.save(true);
        previewing = true;
        try {
            int drawFbo = state.drawFbo, readFbo = state.readFbo;
            //? if >= 1.21.5 {
            if (!bindMainTarget(false, false)) return PREVIEW_FAILED;
            drawFbo = readFbo = mainFbo;
            //?}
            prepareState();
            GL11.glViewport(x, y, w, h);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(clipX, clipY, clipW, clipH);
            GL11.glDisable(GL11.GL_BLEND);
            boolean scaled = beginScaled(drawFbo, readFbo, x, y, w, h, 0);
            drawPass(shader, type, time, 1f, false);
            if (scaled) endScaled(false, 1f, false);
        } finally {
            previewing = false;
            state.restore();
        }
        return PREVIEW_OK;
    }

    // ---- Timing and events ----------------------------------------------------

    private float tickTime() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0f : (now - lastFrameNanos) / 1e9f;
        lastFrameNanos = now;
        dt = Math.max(0f, Math.min(0.1f, dt));

        var settings = ModernClient.SETTINGS;
        float react = settings.audioSource == 0 ? 0f : settings.reactivity;
        float build = settings.dropDetection ? AudioManager.build() * settings.buildUpStrength : 0f;
        double step = dt * settings.speed * (1f + settings.bassSpeed * react * AudioManager.bass()) * (1f + build * 1.5f);
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

    /** Shooting stars, fireworks, and what happens on beats and drops. */
    private void updateEvents(float dt) {
        var settings = ModernClient.SETTINGS;
        meteors.update(dt);
        boolean audio = settings.audioSource != 0;
        fireworks.update(dt, audio && AudioManager.beatThisFrame(), AudioManager.bass() * settings.reactivity);

        if (settings.shootingStars) {
            if (meteors.chance(settings.shootingRate, dt)) meteors.spawn(1f);
            long now = System.nanoTime();
            if (audio && settings.shootingOnBeat && AudioManager.beatThisFrame() && AudioManager.bass() > 0.6f
                    && now - lastBeatMeteorNanos > 1_500_000_000L) {
                lastBeatMeteorNanos = now;
                meteors.spawn(1.2f);
            }
        }

        if (audio && AudioManager.dropThisFrame()) {
            if (settings.dropMeteors) for (int i = 0; i < 3; i++) meteors.spawn(1.5f);
            if (settings.dropColorShift) dropHueTarget += 0.18f + (float) Math.random() * 0.25f;
            if (settings.dropChangeSky) {
                jumpOnDrop = true;
                settings.nextSky();
            }
        }
        dropHue += (dropHueTarget - dropHue) * (1f - (float) Math.exp(-dt * 2.5));
        if (dropHueTarget > 10f) {
            dropHueTarget -= 10f;
            dropHue -= 10f;
        }
    }

    private SkyType selectSky() {
        var settings = ModernClient.SETTINGS;
        long now = System.nanoTime();
        if (!settings.cycleSkies) {
            lastCycleNanos = now;
        } else if (now - lastCycleNanos > (long) (settings.cycleMinutes * 60e9)) {
            lastCycleNanos = now;
            settings.nextSky();
        }

        SkyType wanted = SkyType.byIndex(settings.sky);
        if (current == null) {
            current = wanted;
        } else if (wanted != current) {
            previous = current;
            current = wanted;
            fadeStartNanos = now;
            // A sky change on a drop should hit with the drop, not fade in slowly.
            fadeSeconds = jumpOnDrop ? Math.min(0.3f, settings.crossfadeSeconds) : settings.crossfadeSeconds;
        }
        jumpOnDrop = false;
        return current;
    }

    // ---- Drawing ---------------------------------------------------------------

    private void prepareState() {
        if (vao == 0) vao = GL30.glGenVertexArrays();
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        //? if >= 1.21.11 {
        /*// From 1.21.11 Minecraft binds sampler objects (the terrain's one is still on unit 0
        // after the solid terrain), which would override our textures' filtering.
        org.lwjgl.opengl.GL33.glBindSampler(0, 0);
        *///?}
        GL30.glBindVertexArray(vao);
        uploadSpectrum();
    }

    private static void applyBlend(boolean overlay) {
        if (!overlay) {
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        } else if (ModernClient.SETTINGS.overlayBlend == 0) {
            // Add: the shader outputs color already multiplied by its strength.
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        } else {
            // Screen: 1 - (1 - src)(1 - dst), never brighter than white.
            GL14.glBlendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
        }
    }

    private void drawPass(SkyShader shader, SkyType type, double t, float alpha, boolean overlay) {
        var settings = ModernClient.SETTINGS;
        float react = settings.audioSource == 0 ? 0f : settings.reactivity;

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, type.usesImage() ? imageTexture : spectrumTexture);
        shader.bind();
        int l = shader.loc(U.INVERSE_VIEW_PROJECTION);
        if (l >= 0) GL20.glUniformMatrix4fv(l, false, inverse.get(matrixArray));
        shader.setInt(U.TEX, 0);
        shader.set(U.SKY_DEPTH, skyClipZ());
        shader.set(U.TIME, (float) t);
        shader.set(U.BASS, AudioManager.bass() * react);
        shader.set(U.MID, AudioManager.mid() * react);
        shader.set(U.HIGH, AudioManager.high() * react);
        shader.set(U.LEVEL, AudioManager.level() * react);
        shader.set(U.BEAT, AudioManager.beat() * react);

        shader.set(U.SUN_DIR, -(float) Math.sin(sunAngle), (float) Math.cos(sunAngle), 0f);
        shader.set(U.SKY_COLOR, skyR, skyG, skyB);
        shader.set(U.FOG_COLOR, fogR, fogG, fogB);
        shader.set(U.FOG, settings.matchFog && !previewing ? settings.fogStrength : 0f);

        int tint = settings.tint.getArgb();
        shader.set(U.TINT, ((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f);
        shader.set(U.BRIGHTNESS, settings.brightness);
        shader.set(U.SATURATION, settings.saturation);
        shader.set(U.HUE, settings.hueShift / 360f + dropHue);
        shader.set(U.AUDIO_HUE, settings.audioHue * react);
        shader.set(U.BEAT_FLASH, settings.beatFlash * react);
        shader.set(U.IMAGE_ROT, (float) (t * settings.imageRotation * 0.05));
        shader.set(U.ALPHA, alpha);
        shader.set(U.OVERLAY, overlay ? 1f : 0f);
        shader.set(U.OVERLAY_CUTOFF, settings.overlayCutoff);

        boolean drops = react > 0f && settings.dropDetection;
        shader.set(U.BUILD, drops ? AudioManager.build() * settings.buildUpStrength : 0f);
        shader.set(U.DROP_AGE, drops ? AudioManager.dropAge() : 100f);
        shader.set(U.DROP_FLASH, settings.dropFlash ? 1f : 0f);
        shader.set(U.DROP_WAVE, settings.dropShockwave ? 1f : 0f);

        shader.set(U.LAYER_STARS, settings.layerStars);
        shader.set(U.LAYER_AURORA, settings.layerAurora);
        shader.set(U.LAYER_WISPS, settings.layerWisps);
        boolean meteorsOn = settings.shootingStars || settings.dropMeteors;
        uploadArray(shader.loc(U.METEORS), meteors.start, meteorArray, meteorsOn ? -1 : 3);
        uploadArray(shader.loc(U.METEOR_DIRS), meteors.dir, meteorDirArray, -1);
        if (type == SkyType.FIREWORKS) {
            uploadArray(shader.loc(U.SHELLS), fireworks.shell, shellArray, -1);
            uploadArray(shader.loc(U.SHELL_INFO), fireworks.info, shellInfoArray, -1);
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private final float[] matrixArray = new float[16];
    private final float[] meteorArray = new float[Meteors.MAX * 4];
    private final float[] meteorDirArray = new float[Meteors.MAX * 4];
    private final float[] shellArray = new float[Fireworks.MAX * 4];
    private final float[] shellInfoArray = new float[Fireworks.MAX * 4];

    /** Packs vec4s into one array and uploads it in one call; `unused` = a component to force to -1. */
    private static void uploadArray(int loc, float[][] src, float[] packed, int unused) {
        if (loc < 0) return;
        for (int i = 0; i < src.length; i++) {
            float[] v = src[i];
            int o = i * 4;
            packed[o] = v[0];
            packed[o + 1] = v[1];
            packed[o + 2] = v[2];
            packed[o + 3] = v[3];
            if (unused >= 0) packed[o + unused] = -1f;
        }
        GL20.glUniform4fv(loc, packed);
    }

    private final float[] uploadedSpectrum = new float[AudioManager.BANDS];

    /** Uploads the spectrum only when it changed: with audio off it stays the same, so nothing is sent. */
    private void uploadSpectrum() {
        float[] now = AudioManager.spectrum();
        if (spectrumTexture != 0 && java.util.Arrays.equals(now, uploadedSpectrum)) return;
        System.arraycopy(now, 0, uploadedSpectrum, 0, uploadedSpectrum.length);
        withPlainUnpack(this::uploadSpectrum0);
    }

    /**
     * Runs texture uploads with default unpack settings. Minecraft leaves row length /
     * skip rows / skip pixels set from its own uploads, and with those the driver reads
     * past the end of our small arrays and crashes (AMD). Minecraft sets all of them again
     * before each of its own uploads, so they're left at the defaults; only the unpack
     * buffer binding is put back (26.3 binds one itself and may expect it to stay).
     */
    private static void withPlainUnpack(Runnable upload) {
        int buffer = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try {
            if (buffer != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
            upload.run();
        } finally {
            if (buffer != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, buffer);
        }
    }

    private void uploadSpectrum0() {
        if (spectrumTexture == 0) {
            spectrumTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, spectrumTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, AudioManager.BANDS, 1, 0,
                    GL11.GL_RED, GL11.GL_FLOAT, 0L);
        } else {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, spectrumTexture);
        }
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, AudioManager.BANDS, 1,
                GL11.GL_RED, GL11.GL_FLOAT, uploadedSpectrum);
    }

    // ---- Render Quality --------------------------------------------------------

    private static float qualityScale() {
        return switch (ModernClient.SETTINGS.renderQuality) {
            case 1 -> 0.75f;
            case 2 -> 0.5f;
            case 3 -> 1f / 3f;
            default -> 1f;
        };
    }

    /**
     * Switches drawing to the low-resolution sky buffer; false = draw straight to the screen.
     * The buffer only grows, and each draw uses its bottom-left w x h corner, so the world
     * sky and the (smaller) sky browser previews share it without reallocating every frame.
     * The framebuffers and viewport are where to go back to. With a depth texture, the
     * buffer's depth marks which pixels can show sky, and the rest are skipped.
     */
    private boolean beginScaled(int drawFbo, int readFbo, int vx, int vy, int vw, int vh, int depthTexture) {
        float scale = qualityScale();
        if (scale >= 0.999f || !ensureBlitProgram() || !ensurePrepassProgram()) return false;
        int w = Math.max(1, Math.round(vw * scale));
        int h = Math.max(1, Math.round(vh * scale));
        if (skyFbo == 0) {
            skyFbo = GL30.glGenFramebuffers();
            skyFboTexture = GL11.glGenTextures();
            skyFboDepth = GL30.glGenRenderbuffers();
        }
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, skyFbo);
        if (w > skyFboW || h > skyFboH) {
            int tw = Math.max(w, skyFboW), th = Math.max(h, skyFboH);
            skyFboW = tw;
            skyFboH = th;
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, skyFboTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            withPlainUnpack(() -> GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, tw, th, 0,
                    GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, 0L));
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, skyFboTexture, 0);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, skyFboDepth);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL14.GL_DEPTH_COMPONENT24, tw, th);
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, 0);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL30.GL_RENDERBUFFER, skyFboDepth);
            // Only worth checking after the attachments changed.
            skyFboComplete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        }
        if (!skyFboComplete) {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            return false;
        }
        scaledDrawFbo = drawFbo;
        scaledReadFbo = readFbo;
        viewport[0] = vx;
        viewport[1] = vy;
        viewport[2] = vw;
        viewport[3] = vh;
        scaledW = w;
        scaledH = h;
        // A preview's scissor box is in screen pixels, which mean nothing inside the buffer.
        if (previewing) GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glViewport(0, 0, w, h);

        // One pass clears the buffer to black and, given the depth, marks where sky can show.
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glColorMask(true, true, true, true);
        if (depthTexture != 0) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_ALWAYS);
            GL11.glDepthMask(true);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
        }
        GL20.glUseProgram(prepassProgram);
        GL20.glUniform1i(prepassUseDepthLoc, depthTexture != 0 ? 1 : 0);
        GL20.glUniform4f(prepassMapLoc, vw / (float) w, vh / (float) h, vx, vy);
        GL20.glUniform2i(prepassSizeLoc, vw, vh);
        GL20.glUniform2f(prepassValuesLoc, farDepth(), 1f - farDepth());
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        if (depthTexture != 0) {
            GL11.glDepthMask(false);
            GL11.glDepthFunc(skyDepthFunc());
        }
        if (!previewing) GL11.glEnable(GL11.GL_BLEND);
        // The buffer's alpha stays 1; how see-through the sky is gets applied when it's drawn to the screen.
        GL11.glColorMask(true, true, true, false);
        return true;
    }

    /**
     * Stretches the low-resolution sky over the screen (or preview) and switches back. With
     * depth, the stretched sky is depth tested again at full size, so it only lands on sky.
     */
    private void endScaled(boolean overlay, float alpha, boolean depth) {
        GL11.glColorMask(true, true, true, true);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, scaledDrawFbo);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, scaledReadFbo);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        // The preview's clip box was never changed, only switched off.
        if (previewing) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        applyBlend(overlay);
        GL20.glUseProgram(blitProgram);
        GL20.glUniform1f(blitDepthLoc, depth ? skyClipZ() : 0f);
        GL20.glUniform1f(blitAlphaLoc, overlay ? 1f : alpha);
        // Sample only the used corner, stopping half a texel short so filtering can't pick up stale pixels.
        GL20.glUniform4f(blitUvLoc, scaledW / (float) skyFboW, scaledH / (float) skyFboH,
                (scaledW - 0.5f) / skyFboW, (scaledH - 0.5f) / skyFboH);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, skyFboTexture);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    /**
     * Clears the low-resolution buffer and writes its depth: far where the sky can show,
     * near (so the sky shader skips it) where terrain covers everything. A buffer pixel
     * counts as sky if any screen pixel within one buffer pixel of it shows sky, because
     * stretching it back up blends each screen pixel from its four nearest buffer pixels;
     * so a skipped (black) pixel never bleeds into the edges of the visible sky.
     */
    private boolean ensurePrepassProgram() {
        if (prepassProgram != 0) return prepassProgram > 0;
        int program = linkProgram("Sky depth prepass", FULLSCREEN_VERTEX, """
                #version 150
                uniform sampler2D uSceneDepth;
                uniform int uUseDepth;
                uniform vec4 uMap;     // xy = screen pixels per buffer pixel, zw = the screen area's corner
                uniform ivec2 uSize;   // the screen area's size
                uniform vec2 uValues;  // x = far (cleared) depth, y = a depth that hides the sky
                in vec2 vUv;
                out vec4 fragColor;
                void main() {
                    fragColor = vec4(0.0, 0.0, 0.0, 1.0);
                    if (uUseDepth == 0) {
                        gl_FragDepth = uValues.x;
                        return;
                    }
                    vec2 t = floor(gl_FragCoord.xy);
                    ivec2 lo = max(ivec2(floor((t - 1.0) * uMap.xy)), ivec2(0));
                    ivec2 hi = min(ivec2(ceil((t + 2.0) * uMap.xy)), uSize - 1);
                    ivec2 origin = ivec2(uMap.zw);
                    float d = uValues.y;
                    for (int y = lo.y; y <= hi.y && d != uValues.x; y++) {
                        for (int x = lo.x; x <= hi.x; x++) {
                            if (texelFetch(uSceneDepth, origin + ivec2(x, y), 0).r == uValues.x) {
                                d = uValues.x;
                                break;
                            }
                        }
                    }
                    gl_FragDepth = d;
                }
                """);
        prepassProgram = program == 0 ? -1 : program;
        if (program == 0) return false;
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uSceneDepth"), 0);
        prepassUseDepthLoc = GL20.glGetUniformLocation(program, "uUseDepth");
        prepassMapLoc = GL20.glGetUniformLocation(program, "uMap");
        prepassSizeLoc = GL20.glGetUniformLocation(program, "uSize");
        prepassValuesLoc = GL20.glGetUniformLocation(program, "uValues");
        return true;
    }

    private static final String FULLSCREEN_VERTEX = """
            #version 150
            uniform float uDepth;
            out vec2 vUv;
            void main() {
                vUv = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(vUv * 2.0 - 1.0, uDepth, 1.0);
            }
            """;

    private boolean ensureBlitProgram() {
        if (blitProgram != 0) return blitProgram > 0;
        int program = linkProgram("Sky upscale", FULLSCREEN_VERTEX, """
                #version 150
                uniform sampler2D uTex;
                uniform float uAlpha;
                uniform vec4 uUv; // xy = used part of the buffer, zw = furthest texel centre
                in vec2 vUv;
                out vec4 fragColor;
                void main() {
                    fragColor = vec4(texture(uTex, min(vUv * uUv.xy, uUv.zw)).rgb, uAlpha);
                }
                """);
        blitProgram = program == 0 ? -1 : program;
        if (program == 0) return false;
        GL20.glUseProgram(program);
        GL20.glUniform1i(GL20.glGetUniformLocation(program, "uTex"), 0);
        blitAlphaLoc = GL20.glGetUniformLocation(program, "uAlpha");
        blitUvLoc = GL20.glGetUniformLocation(program, "uUv");
        blitDepthLoc = GL20.glGetUniformLocation(program, "uDepth");
        return true;
    }

    private boolean ensureFillProgram() {
        if (fillProgram != 0) return fillProgram > 0;
        int program = linkProgram("Screen dim", FULLSCREEN_VERTEX, """
                #version 150
                uniform vec4 uColor;
                in vec2 vUv;
                out vec4 fragColor;
                void main() {
                    fragColor = uColor;
                }
                """);
        fillProgram = program == 0 ? -1 : program;
        if (program == 0) return false;
        fillColorLoc = GL20.glGetUniformLocation(program, "uColor");
        return true;
    }

    /**
     * A translucent wash over the whole screen, drawn straight into the main target.
     * The sky browser's backdrop from 1.21.6, where GUI fills are drawn after (so over)
     * anything drawn while the screen renders - including the previews.
     */
    public void dimScreen(float r, float g, float b, float a) {
        if (!openGl() || !ensureFillProgram()) return;
        GlState state = glState.save(true);
        try {
            //? if >= 1.21.5 {
            if (!bindMainTarget(true, false)) return;
            //?}
            prepareState();
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
            GL20.glUseProgram(fillProgram);
            GL20.glUniform4f(fillColorLoc, r, g, b, a);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            state.restore();
        }
    }

    /** Compiles and links a program, or returns 0 and logs why. */
    private static int linkProgram(String name, String vertex, String fragment) {
        int vs = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        int fs = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(vs, vertex);
        GL20.glShaderSource(fs, fragment);
        GL20.glCompileShader(vs);
        GL20.glCompileShader(fs);
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL20.glLinkProgram(program);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOG.error("{} shader failed: {}", name, GL20.glGetProgramInfoLog(program, 4096));
            GL20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    // ---- Shaders and images ----------------------------------------------------

    private SkyShader shader(SkyType type) {
        if (type == SkyType.CUSTOM_SHADER) return customShader();
        SkyShader s = shaders.get(type);
        if (s != null || broken.containsKey(type)) return s;
        try {
            s = SkyShader.compileCore(type);
            shaders.put(type, s);
        } catch (Throwable t) {
            broken.put(type, Boolean.TRUE);
            LOG.error("Sky '{}' failed to compile: {}", type.id, t.getMessage());
        }
        return s;
    }

    private static Path configFolder() throws IOException {
        Path folder = FabricLoader.getInstance().getConfigDir().resolve("dynamicskies");
        Files.createDirectories(folder);
        return folder;
    }

    public static Path imageFolder() {
        try {
            return configFolder();
        } catch (IOException e) {
            return FabricLoader.getInstance().getConfigDir().resolve("dynamicskies");
        }
    }

    public static Path shaderFolder() {
        Path folder = imageFolder().resolve("skies");
        try {
            Files.createDirectories(folder);
        } catch (IOException ignored) {
        }
        return folder;
    }

    // The folders as the render thread uses them: made once, then never touched on the disk
    // again from there (the Open Folder buttons still create them if they've been deleted).
    private Path imageFolderCached, shaderFolderCached;
    // The file the current name points at, worked out once per name; null = outside the folder.
    private Path customFile, imageFile;

    /**
     * The user's shader from config/dynamicskies/skies/. Reloads when the file
     * changes (Live Reload) or on the Reload button; a broken edit keeps the
     * last working version and reports the error in chat.
     */
    private SkyShader customShader() {
        var settings = ModernClient.SETTINGS;
        if (shaderFolderCached == null) shaderFolderCached = shaderFolder();
        Path folder = shaderFolderCached;
        if (!exampleWritten) {
            exampleWritten = true;
            try {
                if (!Files.exists(folder.resolve("example.fsh"))) {
                    Files.writeString(folder.resolve("example.fsh"), SkyShader.read("example_custom.fsh"), StandardOpenOption.CREATE_NEW);
                }
            } catch (IOException | RuntimeException e) {
                LOG.warn("Couldn't write example shader: {}", e.getMessage());
            }
        }

        String name = settings.customShader == null || settings.customShader.isBlank() ? "example.fsh" : settings.customShader.trim();
        boolean nameChanged = !name.equals(customName);
        boolean manual = shaderReloadRequested;
        if (!nameChanged && !manual && !settings.liveReload) return custom;

        if (nameChanged || customFile == null) {
            Path resolved = folder.resolve(name).normalize();
            customFile = resolved.startsWith(folder) ? resolved : null;
        }
        Path file = customFile == null ? folder.resolve(name).normalize() : customFile;
        // The modified time comes from the background file watcher, except on a fresh look.
        long modified = customFile == null ? -1L
                : nameChanged || manual ? FileWatcher.refresh(customFile) : FileWatcher.modified(customFile);
        if (!nameChanged && !manual && modified == customModified) return custom;
        shaderReloadRequested = false;
        customName = name;
        customModified = modified;

        if (modified < 0) {
            // Only on the button: the file name updates on every keystroke while typing it.
            if (manual) ModernClient.chat("§cShader file not found: " + file);
            deleteCustom();
            return null;
        }
        try {
            SkyShader compiled = SkyShader.compileCore(name, Files.readString(file));
            deleteCustom();
            custom = compiled;
            ModernClient.chat("§aLoaded shader " + name);
        } catch (Throwable t) {
            ModernClient.chat("§cShader " + name + " has an error"
                    + (custom != null && !nameChanged ? " (keeping the last working version)" : "") + ":");
            String[] lines = String.valueOf(t.getMessage()).split("\n");
            int shown = 0;
            for (int i = 1; i < lines.length && shown < 4; i++) {
                if (!lines[i].isBlank()) {
                    ModernClient.chat("§7" + lines[i].trim());
                    shown++;
                }
            }
            LOG.error("Custom shader failed to compile: {}", t.getMessage());
            if (nameChanged) deleteCustom();
        }
        return custom;
    }

    private void deleteCustom() {
        if (custom != null) {
            custom.delete();
            custom = null;
        }
    }

    /** The custom image's texture, or 0 if it can't be loaded. Picks up file changes. */
    private int image() {
        var settings = ModernClient.SETTINGS;
        String name = settings.customImage == null || settings.customImage.isBlank() ? "sky.png" : settings.customImage.trim();
        boolean changed = !name.equals(imageName) || imageReloadRequested;
        if (imageFolderCached == null) imageFolderCached = imageFolder();
        Path folder = imageFolderCached;
        if (changed || imageFile == null) {
            Path resolved = folder.resolve(name).normalize();
            imageFile = resolved.startsWith(folder) ? resolved : null;
        }
        Path file = imageFile == null ? folder.resolve(name).normalize() : imageFile;
        // The modified time comes from the background file watcher, except on a fresh look.
        long modified = imageFile == null ? -1L : changed ? FileWatcher.refresh(imageFile) : FileWatcher.modified(imageFile);
        if (!changed && modified == imageModified) return imageTexture;
        imageReloadRequested = false;
        imageName = name;
        imageModified = modified;
        try {
            if (modified < 0) throw new IOException("file not found");
            loadImage(file);
            imageMissingReported = false;
        } catch (IOException | RuntimeException e) {
            if (imageTexture != 0) GL11.glDeleteTextures(imageTexture);
            imageTexture = 0;
            if (!imageMissingReported) {
                imageMissingReported = true;
                LOG.warn("Couldn't load custom sky image {}: {}", file, e.getMessage());
            }
        }
        return imageTexture;
    }

    private void loadImage(Path file) throws IOException {
        var image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException("not a readable image");
        int w = image.getWidth(), h = image.getHeight();
        int limit = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        if (w > limit || h > limit) throw new IOException("image is bigger than the GPU's limit of " + limit);
        var pixels = MemoryUtil.memAllocInt(Math.multiplyExact(w, h));
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            pixels.put(image.getRGB(0, 0, w, h, null, 0, w)).flip();
            if (imageTexture == 0) imageTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, imageTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            withPlainUnpack(() -> GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                    GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels));
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            MemoryUtil.memFree(pixels);
        }
    }

    public void close() {
        shaders.values().forEach(SkyShader::delete);
        shaders.clear();
        broken.clear();
        deleteCustom();
        customName = null;
        if (imageTexture != 0) GL11.glDeleteTextures(imageTexture);
        imageTexture = 0;
        imageName = null;
        if (spectrumTexture != 0) GL11.glDeleteTextures(spectrumTexture);
        spectrumTexture = 0;
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        vao = 0;
        if (skyFbo != 0) {
            GL30.glDeleteFramebuffers(skyFbo);
            GL11.glDeleteTextures(skyFboTexture);
            GL30.glDeleteRenderbuffers(skyFboDepth);
        }
        skyFbo = skyFboTexture = skyFboDepth = skyFboW = skyFboH = 0;
        if (prepassProgram > 0) GL20.glDeleteProgram(prepassProgram);
        prepassProgram = 0;
        pending = false;
        skyFboComplete = false;
        if (mainFbo != 0) GL30.glDeleteFramebuffers(mainFbo);
        mainFbo = 0;
        //? if >= 1.21.5 {
        attachedColor = null;
        attachedDepth = null;
        //?}
        if (blitProgram > 0) GL20.glDeleteProgram(blitProgram);
        blitProgram = 0;
        if (fillProgram > 0) GL20.glDeleteProgram(fillProgram);
        fillProgram = 0;
        current = previous = null;
        AudioManager.stop();
    }

    private static float smooth(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3f - 2f * t);
    }

    //? if >= 1.21.5 {
    private Object attachedColor;
    private int attachedColorId;
    private Object attachedDepth;

    /**
     * From 1.21.5 every render pass ends by binding the window's framebuffer, so by the
     * time our hooks run nothing useful is bound. Draw into the main render target, which
     * the sky pass and the GUI both render to before it's copied to the window.
     */
    private boolean bindMainTarget(boolean fullViewport, boolean depth) {
        var target = com.ootruffle.dynamicskies.modern.Compat.mainRenderTarget();
        if (!(target.getColorTexture() instanceof GlTexture color)) return false;
        // Our own framebuffer with the main target's color texture attached: only needs the
        // texture's GL id, which every version exposes, unlike vanilla's framebuffer cache.
        if (mainFbo == 0) mainFbo = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
        // The texture is recreated on resize (and a new one may reuse the old id), so
        // reattach whenever it's a different texture object, not just a different id.
        if (color != attachedColor || color.glId() != attachedColorId) {
            attachedColor = color;
            attachedColorId = color.glId();
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, attachedColorId, 0);
        }
        // The depth is attached for the world sky, which is depth tested against the terrain.
        // Otherwise it's left on (nothing is depth tested) unless it's gone stale after a resize.
        Object current = depthOf(target);
        if (depth ? current != attachedDepth : attachedDepth != null && attachedDepth != current) {
            attachedDepth = depth ? current : null;
            int id = attachedDepth instanceof GlTexture texture ? texture.glId() : 0;
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, id, 0);
        }
        if (fullViewport) GL11.glViewport(0, 0, target.width, target.height);
        return true;
    }
    //?}

    private final GlState glState = new GlState();

    /**
     * The GL state we touch, so vanilla's state tracking never sees a change. One reused
     * instance (draws never nest). The scissor is only saved for the sky browser, which uses
     * it; the world sky never touches it.
     */
    private static final class GlState {
        boolean depthTest, blend, cull, scissor, depthWrite, withScissor;
        int depthFunc, srcRgb, dstRgb, srcAlpha, dstAlpha, program, vao, activeTexture, texture, sampler, drawFbo, readFbo;
        final int[] viewport = new int[4];
        final int[] scissorBox = new int[4];

        GlState save(boolean withScissor) {
            this.withScissor = withScissor;
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            if (activeTexture != GL13.GL_TEXTURE0) GL13.glActiveTexture(GL13.GL_TEXTURE0);
            texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            //? if >= 1.21.11 {
            /*sampler = GL11.glGetInteger(org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING);
            *///?}
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            if (withScissor) {
                scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
                GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
            }
            depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            blend = GL11.glIsEnabled(GL11.GL_BLEND);
            cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            return this;
        }

        void restore() {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            //? if >= 1.21.11 {
            /*org.lwjgl.opengl.GL33.glBindSampler(0, sampler);
            *///?}
            GL13.glActiveTexture(activeTexture);
            GL30.glBindVertexArray(vao);
            GL20.glUseProgram(program);
            GL11.glDepthMask(depthWrite);
            GL11.glDepthFunc(depthFunc);
            set(GL11.GL_DEPTH_TEST, depthTest);
            set(GL11.GL_BLEND, blend);
            set(GL11.GL_CULL_FACE, cull);
            if (withScissor) {
                set(GL11.GL_SCISSOR_TEST, scissor);
                GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            }
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glColorMask(true, true, true, true);
            //? if >= 1.21.5 {
            // GlStateManager caches the bound framebuffers; go through it so the cache stays right.
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            //?} else {
            /*GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            *///?}
        }

        private static void set(int cap, boolean on) {
            if (on) GL11.glEnable(cap);
            else GL11.glDisable(cap);
        }
    }
}
