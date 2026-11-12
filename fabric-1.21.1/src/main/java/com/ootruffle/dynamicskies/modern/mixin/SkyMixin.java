package com.ootruffle.dynamicskies.modern.mixin;

import com.ootruffle.dynamicskies.modern.ModernClient;

import com.ootruffle.dynamicskies.sky.SkyShader;
import com.ootruffle.dynamicskies.sky.SkyType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class SkyMixin {
    @Unique private SkyShader dynamicskies$program;
    @Unique private int dynamicskies$vao;
    @Unique private SkyType dynamicskies$loaded;

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void dynamicskies$sky(Matrix4f view, Matrix4f projection, float tick, Camera camera,
                                  boolean foggy, Runnable setupFog, CallbackInfo ci) {
        var world = Minecraft.getInstance().level;
        var settings = ModernClient.SETTINGS;
        if (!settings.enabled || world == null || world.dimension() != Level.OVERWORLD || foggy
                || camera.getFluidInCamera() != FogType.NONE) return;
        SkyType selected = SkyType.byId(settings.skyId);
        if (selected == null || selected == SkyType.CUSTOM_SHADER || selected == SkyType.CUSTOM_IMAGE) return;
        if (selected != dynamicskies$loaded) {
            dynamicskies$loaded = selected;
            if (dynamicskies$program != null) dynamicskies$program.delete();
            dynamicskies$program = null;
            try {
                dynamicskies$program = SkyShader.compileCore(selected);
                if (dynamicskies$vao == 0) dynamicskies$vao = GL30.glGenVertexArrays();
            } catch (RuntimeException error) {
                com.mojang.logging.LogUtils.getLogger().error("Could not initialize Dynamic Skies", error);
                return;
            }
        }
        if (dynamicskies$program == null) return;
        setupFog.run();
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            GL11.glDepthMask(false);
            GL30.glBindVertexArray(dynamicskies$vao);
            dynamicskies$program.bind();
            var inverse = new Matrix4f(projection).mul(new Matrix4f(view).setTranslation(0, 0, 0)).invert();
            GL20.glUniformMatrix4fv(dynamicskies$program.loc("uInverseViewProjection"), false, inverse.get(stack.mallocFloat(16)));
            dynamicskies$program.set("uTime", (world.getGameTime() % 1728000L + tick) / 20f);
            dynamicskies$program.set("uTint", 1, 1, 1);
            dynamicskies$program.set("uBrightness", settings.brightness);
            dynamicskies$program.set("uSaturation", settings.saturation);
            dynamicskies$program.set("uAlpha", 1);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            GL30.glBindVertexArray(previousVao);
            GL20.glUseProgram(previousProgram);
            GL11.glDepthMask(depthWrite);
        }
        ci.cancel();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void dynamicskies$close(CallbackInfo ci) {
        if (dynamicskies$program != null) {
            dynamicskies$program.delete();
            dynamicskies$program = null;
        }
        GL30.glDeleteVertexArrays(dynamicskies$vao);
        dynamicskies$vao = 0;
        dynamicskies$loaded = null;
    }
}
