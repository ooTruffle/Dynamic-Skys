package com.ootruffle.dynamicskies.mixins;

import com.ootruffle.dynamicskies.sky.SkyRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs right after the whole sky has been drawn, whoever drew it (vanilla, a
 * resource pack sky mod, or our own replacement). Overlay mode and partly
 * transparent skies are drawn here, on top of it.
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(
            method = "renderWorldPass(IFJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderSky(FI)V",
                    shift = At.Shift.AFTER
            )
    )
    private void dynamicskies$afterSky(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        SkyRenderer.INSTANCE.renderAfterSky(partialTicks);
    }
}
