package com.ootruffle.dynamicskies.mixins;

import com.ootruffle.dynamicskies.sky.SkyRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the vanilla sky for the dynamic one in Replace mode when it's fully
 * opaque. Everything drawn on top of another sky (Overlay mode, or a partly
 * transparent sky from Night Only / rain / Opacity) happens in
 * EntityRendererMixin, after the sky is done.
 */
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalMixin {

    @Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true)
    private void dynamicskies$replaceSky(float partialTicks, int pass, CallbackInfo ci) {
        if (SkyRenderer.INSTANCE.renderReplacing(partialTicks)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderClouds(FI)V", at = @At("HEAD"), cancellable = true)
    private void dynamicskies$hideClouds(float partialTicks, int pass, CallbackInfo ci) {
        if (SkyRenderer.INSTANCE.shouldHideClouds()) {
            ci.cancel();
        }
    }
}
