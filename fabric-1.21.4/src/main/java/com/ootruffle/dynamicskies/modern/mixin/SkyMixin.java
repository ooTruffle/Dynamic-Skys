package com.ootruffle.dynamicskies.modern.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ootruffle.dynamicskies.sky.ModernSkyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class SkyMixin {
    @Unique private final ModernSkyRenderer dynamicskies$renderer = new ModernSkyRenderer();
    @Unique private boolean dynamicskies$replaced;

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void dynamicskies$sky(float r, float g, float b, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var world = client.level;
        float tick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        dynamicskies$replaced = world != null && dynamicskies$renderer.draw(
                RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(),
                world.getGameTime() + (double) tick, world.getSunAngle(tick), r, g, b, () -> {});
        if (dynamicskies$replaced) ci.cancel();
    }

    @Inject(method = {"renderDarkDisc", "renderSunMoonAndStars", "renderSunriseAndSunset"},
            at = @At("HEAD"), cancellable = true)
    private void dynamicskies$vanillaSky(CallbackInfo ci) {
        if (dynamicskies$replaced) ci.cancel();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void dynamicskies$close(CallbackInfo ci) {
        dynamicskies$renderer.close();
    }
}
