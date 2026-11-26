package com.ootruffle.dynamicskies.modern.mixin;

import com.ootruffle.dynamicskies.sky.ModernSkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >= 26.1 {
/*import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?} elif >= 1.21.8 {
/*import org.spongepowered.asm.mixin.injection.ModifyArg;
*///?} else {
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//?}

/**
 * Sky Lighting: tints vanilla's sky light colour (and only it, so torch-lit areas keep their
 * own colour) with the dynamic sky's. Every version builds that colour once per lightmap
 * update; the hook sits where it's handed on:
 * <ul>
 *   <li>up to 1.21.5: the first Vector3f updateLightTexture stores, used for the CPU
 *   lightmap (1.21.1) or passed to the lightmap shader as SkyLightColor;</li>
 *   <li>1.21.8 to 1.21.11: the first vec3 written into the lightmap's uniform buffer;</li>
 *   <li>from 26.1: the render state's skyLightColor, just before Lightmap.render uses it.</li>
 * </ul>
 * Optional: if another mod changes this code, the lightmap is simply left alone.
 */
//? if >= 26.1 {
/*@Mixin(net.minecraft.client.renderer.Lightmap.class)
*///?} else
@Mixin(net.minecraft.client.renderer.LightTexture.class)
abstract class LightmapMixin {

    //? if >= 26.1 {
    /*@Inject(method = "render", at = @At("HEAD"), require = 0)
    private void dynamicskies$tintSkyLight(net.minecraft.client.renderer.state.LightmapRenderState state, CallbackInfo ci) {
        if (state.needsUpdate) state.skyLightColor = ModernSkyRenderer.INSTANCE.tintSkyLight(state.skyLightColor);
    }
    *///?} elif >= 1.21.8 {
    /*@ModifyArg(method = "updateLightTexture",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/buffers/Std140Builder;putVec3(Lorg/joml/Vector3fc;)Lcom/mojang/blaze3d/buffers/Std140Builder;",
                    ordinal = 0),
            require = 0)
    private org.joml.Vector3fc dynamicskies$tintSkyLight(org.joml.Vector3fc color) {
        return ModernSkyRenderer.INSTANCE.tintSkyLight(color);
    }
    *///?} else {
    @ModifyVariable(method = "updateLightTexture", at = @At(value = "STORE", ordinal = 0), ordinal = 0, require = 0)
    private org.joml.Vector3f dynamicskies$tintSkyLight(org.joml.Vector3f color) {
        org.joml.Vector3fc tinted = ModernSkyRenderer.INSTANCE.tintSkyLight(color);
        return tinted == color ? color : new org.joml.Vector3f(tinted);
    }
    //?}
}
