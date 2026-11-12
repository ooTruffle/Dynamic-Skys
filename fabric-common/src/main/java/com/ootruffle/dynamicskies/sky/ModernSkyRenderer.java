package com.ootruffle.dynamicskies.sky;

import com.ootruffle.dynamicskies.modern.ModernClient;
import org.joml.Matrix4f;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
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
    private double animationTime;
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
                        float skyR, float skyG, float skyB, Runnable setupFog) {
        var settings = ModernClient.SETTINGS;
        if (!settings.enabled) return false;
        SkyType selected = SkyType.byId(settings.skyId);
        if (selected == null || !prepare(selected, settings)) return false;
        setupFog.run();
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL11.glDepthMask(false);
            GL30.glBindVertexArray(vao);
            program.bind();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, imageTexture);
            program.setInt("uTex", 0);
            var inverse = new Matrix4f(projection).mul(new Matrix4f(view).setTranslation(0, 0, 0)).invert();
            GL20.glUniformMatrix4fv(program.loc("uInverseViewProjection"), false, inverse.get(stack.mallocFloat(16)));
            float dt = lastTime == 0 ? 0f : Math.clamp((float) ((gameTime - lastTime) / 20.0), 0f, 0.25f);
            lastTime = gameTime;
            animationTime += dt * settings.speed;
            program.set("uImageRot", (float) (animationTime * settings.imageRotation * 0.05));
            program.set("uTime", (float) animationTime);
            program.set("uSunDir", -(float) Math.sin(sunAngle), (float) Math.cos(sunAngle), 0f);
            program.set("uSkyColor", skyR, skyG, skyB);
            program.set("uHue", settings.hueShift / 360f);
            program.set("uLayerStars", settings.layerStars);
            program.set("uLayerAurora", settings.layerAurora);
            program.set("uLayerWisps", settings.layerWisps);
            meteors.update(dt);
            if (settings.shootingStars && meteors.chance(settings.shootingRate, dt)) meteors.spawn(1f);
            for (int i = 0; i < Meteors.MAX; i++) {
                float[] start = meteors.start[i], dir = meteors.dir[i];
                program.set("uMeteors[" + i + "]", start[0], start[1], start[2], settings.shootingStars ? start[3] : -1f);
                program.set("uMeteorDirs[" + i + "]", dir[0], dir[1], dir[2], dir[3]);
            }
            program.set("uTint", 1, 1, 1);
            program.set("uBrightness", settings.brightness);
            program.set("uSaturation", settings.saturation);
            program.set("uAlpha", 1);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(activeTexture);
            GL30.glBindVertexArray(previousVao);
            GL20.glUseProgram(previousProgram);
            GL11.glDepthMask(depthWrite);
        }
        return true;
    }

    @Override
    public void close() {
        releaseProgram();
        GL30.glDeleteVertexArrays(vao);
        vao = 0;
        loaded = null;
    }
}
