package com.ootruffle.dynamicskies.mixins;

import com.ootruffle.dynamicskies.sky.SkyRenderer;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    /**
     * After the opaque terrain (solid, cutout mipped, cutout) and before entities and
     * water. With Skip Hidden Sky the sky is drawn here, depth-tested, so the shader only
     * runs where no terrain covers it. Optional: if another mod changes this call, the
     * renderer notices the hook never runs and goes back to drawing with the sky.
     */
    @Inject(
            method = "renderWorldPass(IFJ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;renderBlockLayer(Lnet/minecraft/util/EnumWorldBlockLayer;DILnet/minecraft/entity/Entity;)I",
                    ordinal = 2,
                    shift = At.Shift.AFTER
            ),
            require = 0
    )
    private void dynamicskies$afterTerrain(int pass, float partialTicks, long finishTimeNano, CallbackInfo ci) {
        SkyRenderer.INSTANCE.renderAfterTerrain(partialTicks);
    }

    @Shadow
    @Final
    private int[] lightmapColors;

    /**
     * Sky Lighting: once the lightmap's colours are worked out and just before they're
     * uploaded, tint the sky-lit ones toward the sky. Optional, like the terrain hook.
     */
    @Inject(
            method = "updateLightmap(F)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;updateDynamicTexture()V"
            ),
            require = 0
    )
    private void dynamicskies$tintLightmap(float partialTicks, CallbackInfo ci) {
        SkyRenderer.INSTANCE.tintLightmap(lightmapColors, partialTicks);
    }
}
