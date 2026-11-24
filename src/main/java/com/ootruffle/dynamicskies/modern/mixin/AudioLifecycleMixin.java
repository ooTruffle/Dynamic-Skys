package com.ootruffle.dynamicskies.modern.mixin;

import com.ootruffle.dynamicskies.audio.AudioManager;
import com.ootruffle.dynamicskies.modern.ModernClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class AudioLifecycleMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void dynamicskies$stopInactiveCapture(CallbackInfo ci) {
        var settings = ModernClient.SETTINGS;
        if (Minecraft.getInstance().level == null || !settings.enabled || settings.audioSource == 0) {
            AudioManager.stop();
        }
    }
}
