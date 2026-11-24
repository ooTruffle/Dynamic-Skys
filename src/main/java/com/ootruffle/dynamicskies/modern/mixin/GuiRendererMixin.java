package com.ootruffle.dynamicskies.modern.mixin;

import org.spongepowered.asm.mixin.Mixin;
//? if >= 1.21.6 {
/*import com.ootruffle.dynamicskies.sky.ModernSkyRenderer;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * From 1.21.6 the GUI is collected while screens render and drawn here at the end of the
 * frame (from 26.1 even before the world is drawn). The sky browser's previews are queued
 * while it renders and drawn at the start of this, after the world and under the GUI.
 * Before 1.21.6 the previews are drawn as the screen renders, so this mixin does nothing.
 */
//? if >= 1.21.6 {
/*@Mixin(net.minecraft.client.gui.render.GuiRenderer.class)
*///?} else
@Mixin(net.minecraft.client.Minecraft.class)
abstract class GuiRendererMixin {

    //? if >= 1.21.6 {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void dynamicskies$drawPreviews(CallbackInfo ci) {
        ModernSkyRenderer.INSTANCE.drawQueuedPreviews();
    }
    *///?}
}
