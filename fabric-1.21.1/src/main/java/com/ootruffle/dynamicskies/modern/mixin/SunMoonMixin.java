package com.ootruffle.dynamicskies.modern.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.MeshData;
import com.ootruffle.dynamicskies.modern.ModernClient;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Slice;

@Mixin(LevelRenderer.class)
abstract class SunMoonMixin {
    @WrapOperation(method = "renderSky",
            slice = @Slice(
                    from = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/LevelRenderer;SUN_LOCATION:Lnet/minecraft/resources/ResourceLocation;"),
                    to = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getStarBrightness(F)F")),
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"),
            require = 2, allow = 2)
    private void dynamicskies$drawCelestialBody(MeshData mesh, Operation<Void> original) {
        if (ModernClient.SETTINGS.shouldHideSunMoon()) mesh.close();
        else original.call(mesh);
    }
}
