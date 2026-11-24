package com.ootruffle.dynamicskies.modern.mixin;

import com.ootruffle.dynamicskies.sky.ModernSkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide Clouds: skips vanilla's clouds while the dynamic sky is showing. */
//? if >= 1.21.4 {
@Mixin(net.minecraft.client.renderer.CloudRenderer.class)
//?} else
/*@Mixin(net.minecraft.client.renderer.LevelRenderer.class)*/
abstract class CloudsMixin {

    //? if >= 26.3 {
    /*@Inject(method = {"render", "renderOit"}, at = @At("HEAD"), cancellable = true, require = 1)
    *///?} elif >= 1.21.4 {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    //?} else
    /*@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)*/
    private void dynamicskies$hideClouds(CallbackInfo ci) {
        if (ModernSkyRenderer.INSTANCE.shouldHideClouds()) ci.cancel();
    }
}
