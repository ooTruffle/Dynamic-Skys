package com.ootruffle.dynamicskies.modern;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.nio.file.Path;

/** The small Minecraft APIs that moved between versions, so the rest of the code doesn't care. */
public final class Compat {

    private Compat() {
    }

    public static void openUri(String uri) {
        //? if >= 26.3 {
        /*com.mojang.blaze3d.Blaze3D.openUri(URI.create(uri));
        *///?} elif >= 1.21.11 {
        /*net.minecraft.util.Util.getPlatform().openUri(URI.create(uri));
        *///?} else
        net.minecraft.Util.getPlatform().openUri(URI.create(uri));
    }

    public static void openFolder(Path folder) {
        //? if >= 26.3 {
        /*com.mojang.blaze3d.Blaze3D.openPath(folder);
        *///?} elif >= 1.21.11 {
        /*net.minecraft.util.Util.getPlatform().openPath(folder);
        *///?} else
        net.minecraft.Util.getPlatform().openFile(folder.toFile());
    }

    /** Adds a line to the chat; call on the render thread. */
    public static void chat(Component message) {
        //? if >= 26.2 {
        /*Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(message);
        *///?} elif >= 26.1 {
        /*Minecraft.getInstance().gui.getChat().addClientSystemMessage(message);
        *///?} else
        Minecraft.getInstance().gui.getChat().addMessage(message);
    }

    public static void setScreen(Screen screen) {
        //? if >= 26.2 {
        /*Minecraft.getInstance().gui.setScreen(screen);
        *///?} else
        Minecraft.getInstance().setScreen(screen);
    }

    public static RenderTarget mainRenderTarget() {
        //? if >= 26.2 {
        /*return Minecraft.getInstance().gameRenderer.mainRenderTarget();
        *///?} else
        return Minecraft.getInstance().getMainRenderTarget();
    }
}
