package com.ootruffle.dynamicskies.modern.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ootruffle.dynamicskies.sky.ModernSkyRenderer;
import net.minecraft.client.Minecraft;
import com.ootruffle.dynamicskies.modern.ModernClient;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class SkyMixin {
    @Unique private final ModernSkyRenderer dynamicskies$renderer = new ModernSkyRenderer();
    // This is the frame-graph sky pass body on 1.21.4. Compose over the completed vanilla pass.
    @Inject(method = "method_62215", at = @At("RETURN"))
    private void dynamicskies$sky(FogParameters fog, DimensionSpecialEffects.SkyType type, float tick,
                                  DimensionSpecialEffects effects, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        var world = client.level;
        if (world == null) return;
        int sky = world.getSkyColor(client.gameRenderer.getMainCamera().getPosition(), tick);
        dynamicskies$drewSky = dynamicskies$renderer.draw(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix(),
                world.getGameTime() + (double) tick, world.getSunAngle(tick),
                ARGB.redFloat(sky), ARGB.greenFloat(sky), ARGB.blueFloat(sky),
                fog.red(), fog.green(), fog.blue(), world.getRainLevel(tick), type == DimensionSpecialEffects.SkyType.END);
    }

    @Unique private boolean dynamicskies$drewSky;

    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void dynamicskies$beginFrame(CallbackInfo ci) {
        dynamicskies$drewSky = false;
    }

    @Inject(method = "method_62205", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/CloudRenderer;render(ILnet/minecraft/client/CloudStatus;FLorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;F)V"), cancellable = true)
    private void dynamicskies$clouds(CallbackInfo ci) {
        if (dynamicskies$drewSky && ModernClient.SETTINGS.hideClouds) ci.cancel();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void dynamicskies$close(CallbackInfo ci) {
        dynamicskies$renderer.close();
    }
}
