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

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void dynamicskies$sky(Matrix4f view, Matrix4f projection, float tick, Camera camera,
                                  boolean foggy, Runnable setupFog, CallbackInfo ci) {
        var world = Minecraft.getInstance().level;
        var settings = ModernClient.SETTINGS;
        if (!settings.enabled || world == null || world.dimension() != Level.OVERWORLD || foggy
                || camera.getFluidInCamera() != FogType.NONE) return;
        var color = world.getSkyColor(camera.getPosition(), tick);
        if (dynamicskies$renderer.draw(view, projection, world.getGameTime() + (double) tick,
                world.getSunAngle(tick), (float) color.x, (float) color.y, (float) color.z, setupFog)) ci.cancel();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void dynamicskies$close(CallbackInfo ci) {
        dynamicskies$renderer.close();
    }
}
