package com.ootruffle.dynamicskies.modern.mixin;

import com.ootruffle.dynamicskies.modern.ModernClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >= 1.21.4 {
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.injection.Slice;
*///?}

/** Hide Sun and Moon: skips the vanilla sun and moon. */
//? if >= 1.21.4 {
@Mixin(SkyRenderer.class)
//?} else
/*@Mixin(LevelRenderer.class)*/
abstract class SunMoonMixin {

    //? if >= 1.21.4 {
    @Inject(method = {"renderSun", "renderMoon"}, at = @At("HEAD"), cancellable = true, require = 2)
    private void dynamicskies$hideCelestialBody(CallbackInfo ci) {
        if (ModernClient.SETTINGS.shouldHideSunMoon()) ci.cancel();
    }
    //?} else {
    /*@WrapOperation(method = "renderSky",
            slice = @Slice(
                    from = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;SUN_LOCATION:Lnet/minecraft/resources/ResourceLocation;"),
                    to = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getStarBrightness(F)F")),
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"),
            require = 2, allow = 2)
    private void dynamicskies$drawCelestialBody(MeshData mesh, Operation<Void> original) {
        if (ModernClient.SETTINGS.shouldHideSunMoon()) mesh.close();
        else original.call(mesh);
    }
    *///?}
}
