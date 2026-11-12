package com.ootruffle.dynamicskies.sky;

import com.ootruffle.dynamicskies.modern.ModernClient;
import com.ootruffle.dynamicskies.audio.AudioManager;
import org.joml.Matrix4f;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

public final class ModernSkyRenderer implements AutoCloseable {
    private SkyShader program;
    private int vao;
    private SkyType loaded;
    private final Meteors meteors = new Meteors();
    private double lastTime;
    private long lastCycle = System.nanoTime();
    private boolean preview;
    private float opacity = 1f;
    private boolean overlay;
    private float fogRed, fogGreen, fogBlue;
    private double animationTime;
    private int spectrumTexture;
    private float dropHue;
    private float dropHueTarget;
    private long lastBeatMeteor;

    private void bindSpectrum() {
        if (spectrumTexture == 0) {
            spectrumTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, spectrumTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F, AudioManager.BANDS, 1, 0,
                    GL11.GL_RED, GL11.GL_FLOAT, 0L);
        } else {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, spectrumTexture);
        }
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, AudioManager.BANDS, 1,
                GL11.GL_RED, GL11.GL_FLOAT, AudioManager.spectrum());
    }
    private int imageTexture;
    private String assetName;
    private long assetModified;
    private long nextAssetCheck;

    private boolean prepare(SkyType selected, ModernClient.Settings settings) {
        boolean image = selected == SkyType.CUSTOM_IMAGE;
        boolean custom = image || selected == SkyType.CUSTOM_SHADER;
        String name = custom ? (image ? settings.customImage : settings.customShader) : null;
        boolean changed = selected != loaded || !Objects.equals(name, assetName);
        long now = System.nanoTime();
        if (!changed && (!custom || now < nextAssetCheck)) return program != null && (!image || imageTexture != 0);
        nextAssetCheck = now + 500_000_000L;
        if (changed) {
            releaseProgram();
            loaded = selected;
            assetName = name;
            assetModified = Long.MIN_VALUE;
        }
        try {
            Path file = null;
            if (custom) {
                Path folder = FabricLoader.getInstance().getConfigDir().resolve("dynamicskies");
                if (!image) folder = folder.resolve("skies");
                Files.createDirectories(folder);
                if (!image && !Files.exists(folder.resolve("example.fsh"))) {
                    Files.writeString(folder.resolve("example.fsh"), SkyShader.read("example_custom.fsh"),
                            java.nio.file.StandardOpenOption.CREATE_NEW);
                }
                file = folder.resolve(name).normalize();
                if (!file.startsWith(folder) || !Files.isRegularFile(file)) {
                    if (assetModified != -1L) com.mojang.logging.LogUtils.getLogger().warn("Missing custom sky file: {}", file);
                    releaseProgram();
                    assetModified = -1L;
                    return false;
                }
                long modified = Files.getLastModifiedTime(file).toMillis();
                if (!changed && modified == assetModified) return program != null && (!image || imageTexture != 0);
                assetModified = modified;
            }
            if (image) {
                if (program == null) program = SkyShader.compileCore(selected);
                loadImage(file);
            } else {
                SkyShader next = custom ? SkyShader.compileCore(name, Files.readString(file)) : SkyShader.compileCore(selected);
                if (program != null) program.delete();
                program = next;
            }
            if (vao == 0) vao = GL30.glGenVertexArrays();
        } catch (IOException | RuntimeException error) {
            com.mojang.logging.LogUtils.getLogger().error("Could not load sky " + selected.id, error);
        }
        return program != null && (!image || imageTexture != 0);
    }

    private void loadImage(Path file) throws IOException {
        var image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException("Unreadable image: " + file);
        int w = image.getWidth(), h = image.getHeight();
        int limit = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
        if (w > limit || h > limit) throw new IOException("Image exceeds GPU texture size: " + limit);
        var pixels = MemoryUtil.memAllocInt(Math.multiplyExact(w, h));
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            pixels.put(image.getRGB(0, 0, w, h, null, 0, w)).flip();
            if (imageTexture == 0) imageTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, imageTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                    GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, pixels);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            MemoryUtil.memFree(pixels);
        }
    }

    private void releaseProgram() {
        if (program != null) program.delete();
        if (imageTexture != 0) GL11.glDeleteTextures(imageTexture);
        program = null;
        imageTexture = 0;
    }

    public boolean draw(Matrix4f view, Matrix4f projection, double gameTime, float sunAngle,
                        float skyR, float skyG, float skyB, float fogR, float fogG, float fogB,
                        float rain, boolean end) {
        var settings = ModernClient.SETTINGS;
        if (!settings.enabled || (end && !settings.replaceEnd)) return false;
        opacity = settings.opacity;
        if (settings.nightOnly && !end) {
            float night = Math.clamp((0.15f - (float) Math.cos(sunAngle)) / 0.27f, 0f, 1f);
            opacity *= night * night * (3f - 2f * night);
        }
        if (settings.fadeInRain && !end) opacity *= 1f - rain * 0.75f;
        if (opacity <= 0.001f) return false;
        fogRed = fogR;
        fogGreen = fogG;
        fogBlue = fogB;
        long now = System.nanoTime();
        if (!settings.cycleSkies) lastCycle = now;
        else if (now - lastCycle >= settings.cycleMinutes * 60e9) {
            lastCycle = now;
            settings.nextSky();
        }
        SkyType selected = SkyType.byId(settings.skyId);
        overlay = settings.skyMode == 1 || (settings.skyMode == 2 && selected != null && selected.overlayFriendly());
        return drawSky(selected, view, projection, gameTime, sunAngle, skyR, skyG, skyB);
    }

    public boolean drawPreview(SkyType sky, float aspect, double seconds) {
        preview = true;
        var projection = new Matrix4f().perspective((float) Math.toRadians(72), aspect, 0.1f, 100f);
        var view = new Matrix4f().rotateX(0.24f).rotateY((float) (seconds * 0.06));
        return drawSky(sky, view, projection, seconds * 20, 1.7f, 0.47f, 0.65f, 1f);
    }

    private boolean drawSky(SkyType selected, Matrix4f view, Matrix4f projection, double gameTime,
                            float sunAngle, float skyR, float skyG, float skyB) {
        var settings = ModernClient.SETTINGS;
        if (selected == null || !prepare(selected, settings)) return false;
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        int srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            if (overlay) {
                int source = settings.overlayBlend == 0 ? GL11.GL_ONE : GL11.GL_ONE_MINUS_DST_COLOR;
                GL14.glBlendFuncSeparate(source, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE);
            } else GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GL30.glBindVertexArray(vao);
            program.bind();
            program.setInt("uTex", 0);
            var inverse = new Matrix4f(projection).mul(new Matrix4f(view).setTranslation(0, 0, 0)).invert();
            GL20.glUniformMatrix4fv(program.loc("uInverseViewProjection"), false, inverse.get(stack.mallocFloat(16)));
            float dt = lastTime == 0 ? 0f : Math.clamp((float) ((gameTime - lastTime) / 20.0), 0f, 0.25f);
            lastTime = gameTime;
            if (!preview) AudioManager.update(dt);
            float react = settings.audioSource == 0 ? 0f : settings.reactivity;
            if (selected.usesImage()) GL11.glBindTexture(GL11.GL_TEXTURE_2D, imageTexture);
            else bindSpectrum();
            float build = settings.dropDetection ? AudioManager.build() * settings.buildUpStrength : 0f;
            animationTime += dt * settings.speed * (1f + AudioManager.bass() * react * settings.bassSpeed)
                    * (1f + build * 1.5f);
            program.set("uBass", AudioManager.bass() * react);
            program.set("uMid", AudioManager.mid() * react);
            program.set("uHigh", AudioManager.high() * react);
            program.set("uLevel", AudioManager.level() * react);
            program.set("uBeat", AudioManager.beat() * react);
            program.set("uAudioHue", settings.audioHue * react);
            program.set("uBeatFlash", settings.beatFlash * react);
            program.set("uBuild", build);
            program.set("uDropAge", settings.dropDetection && react > 0 ? AudioManager.dropAge() : 100f);
            program.set("uDropFlash", settings.dropFlash ? 1f : 0f);
            program.set("uDropWave", settings.dropShockwave ? 1f : 0f);
            if (AudioManager.dropThisFrame()) {
                if (!preview && settings.dropChangeSky) settings.nextSky();
                if (settings.dropColorShift) dropHueTarget += 0.18f + (float) Math.random() * 0.25f;
                if (settings.dropMeteors) for (int i = 0; i < 3; i++) meteors.spawn(1.5f);
            }
            dropHue += (dropHueTarget - dropHue) * (1f - (float) Math.exp(-dt * 2.5f));
            long now = System.nanoTime();
            if (settings.shootingStars && settings.shootingOnBeat && AudioManager.beatThisFrame()
                    && AudioManager.bass() > 0.6f && now - lastBeatMeteor > 1_500_000_000L) {
                lastBeatMeteor = now;
                meteors.spawn(1.2f);
            }
            program.set("uImageRot", (float) (animationTime * settings.imageRotation * 0.05));
            program.set("uTime", (float) animationTime);
            program.set("uSunDir", -(float) Math.sin(sunAngle), (float) Math.cos(sunAngle), 0f);
            program.set("uSkyColor", skyR, skyG, skyB);
            program.set("uHue", settings.hueShift / 360f + dropHue);
            program.set("uLayerStars", settings.layerStars);
            program.set("uLayerAurora", settings.layerAurora);
            program.set("uLayerWisps", settings.layerWisps);
            meteors.update(dt);
            if (settings.shootingStars && meteors.chance(settings.shootingRate, dt)) meteors.spawn(1f);
            for (int i = 0; i < Meteors.MAX; i++) {
                float[] start = meteors.start[i], dir = meteors.dir[i];
                program.set("uMeteors[" + i + "]", start[0], start[1], start[2], (settings.shootingStars || settings.dropMeteors) ? start[3] : -1f);
                program.set("uMeteorDirs[" + i + "]", dir[0], dir[1], dir[2], dir[3]);
            }
            int tint = settings.tint.getArgb();
            program.set("uTint", ((tint >>> 16) & 255) / 255f, ((tint >>> 8) & 255) / 255f, (tint & 255) / 255f);
            program.set("uBrightness", settings.brightness);
            program.set("uSaturation", settings.saturation);
            program.set("uAlpha", opacity);
            program.set("uOverlay", overlay ? 1f : 0f);
            program.set("uOverlayCutoff", settings.overlayCutoff);
            program.set("uFogColor", fogRed, fogGreen, fogBlue);
            program.set("uFog", !preview && settings.matchFog ? settings.fogStrength : 0f);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(activeTexture);
            GL30.glBindVertexArray(previousVao);
            GL20.glUseProgram(previousProgram);
            GL11.glDepthMask(depthWrite);
            if (depthTest) GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (!blend) GL11.glDisable(GL11.GL_BLEND);
        }
        return true;
    }

    @Override
    public void close() {
        releaseProgram();
        if (!preview) AudioManager.stop();
        GL11.glDeleteTextures(spectrumTexture);
        spectrumTexture = 0;
        GL30.glDeleteVertexArrays(vao);
        vao = 0;
        loaded = null;
    }
}
