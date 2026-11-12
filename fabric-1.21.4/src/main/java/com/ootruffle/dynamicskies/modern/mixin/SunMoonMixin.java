package com.ootruffle.dynamicskies.modern.mixin;

import com.ootruffle.dynamicskies.modern.ModernClient;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRenderer.class)
abstract class SunMoonMixin {
    @Inject(method = {"renderSun", "renderMoon"}, at = @At("HEAD"), cancellable = true, require = 2)
    private void dynamicskies$hideCelestialBody(CallbackInfo ci) {
        if (ModernClient.SETTINGS.shouldHideSunMoon()) ci.cancel();
    }
}
