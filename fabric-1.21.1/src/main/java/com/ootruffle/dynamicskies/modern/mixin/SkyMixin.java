package com.ootruffle.dynamicskies.modern.mixin;

import com.ootruffle.dynamicskies.modern.ModernClient;

import com.ootruffle.dynamicskies.sky.ModernSkyRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class SkyMixin {
    @Unique private final ModernSkyRenderer dynamicskies$renderer = new ModernSkyRenderer();

    @Inject(method = "renderSky", at = @At("RETURN"))
    private void dynamicskies$sky(Matrix4f view, Matrix4f projection, float tick, Camera camera,
                                  boolean foggy, Runnable setupFog, CallbackInfo ci) {
        var world = Minecraft.getInstance().level;
        var settings = ModernClient.SETTINGS;
        if (!settings.enabled || world == null || (world.dimension() != Level.OVERWORLD && world.dimension() != Level.END) || foggy
                || camera.getFluidInCamera() != FogType.NONE) return;
        var color = world.getSkyColor(camera.getPosition(), tick);
        float[] fog = com.mojang.blaze3d.systems.RenderSystem.getShaderFogColor();
        dynamicskies$drewSky = dynamicskies$renderer.draw(view, projection, world.getGameTime() + (double) tick,
                world.getSunAngle(tick), (float) color.x, (float) color.y, (float) color.z,
                fog[0], fog[1], fog[2], world.getRainLevel(tick), world.dimension() == Level.END);
    }

    @Unique private boolean dynamicskies$drewSky;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void dynamicskies$beginFrame(CallbackInfo ci) {
        dynamicskies$drewSky = false;
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void dynamicskies$clouds(CallbackInfo ci) {
        if (dynamicskies$drewSky && ModernClient.SETTINGS.hideClouds) ci.cancel();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void dynamicskies$close(CallbackInfo ci) {
        dynamicskies$renderer.close();
    }
}
