package com.ootruffle.dynamicskies.modern;

import com.ootruffle.dynamicskies.sky.Favorites;
import com.ootruffle.dynamicskies.sky.ModernSkyRenderer;
import com.ootruffle.dynamicskies.sky.SkyType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.List;

final class SkyBrowser extends Screen {
    private final Screen parent;
    private final ModernSkyRenderer preview = new ModernSkyRenderer();
    private final long opened = System.nanoTime();
    private final int[] viewport = new int[4];
    private SkyType focus = SkyType.byIndex(ModernClient.SETTINGS.sky);
    private boolean favoritesOnly;
    private int page;
    private int rows;
    private int listWidth;

    SkyBrowser(Screen parent) {
        super(Component.literal("Browse skies"));
        this.parent = parent;
    }

    private List<SkyType> entries() {
        return Arrays.stream(SkyType.values()).filter(s -> !favoritesOnly || Favorites.isFavorite(s)).toList();
    }

    private void button(int x, int y, int w, String label, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(x, y, w, 20).build());
    }

    @Override
    protected void init() {
        clearWidgets();
        rows = Math.max(1, (height - 90) / 21);
        listWidth = Math.min(155, width / 2 - 12);
        var skies = entries();
        page = Math.clamp(page, 0, Math.max(0, (skies.size() - 1) / rows));
        for (int row = 0; row < rows && page * rows + row < skies.size(); row++) {
            var sky = skies.get(page * rows + row);
            button(8, 30 + row * 21, listWidth, sky.displayName, () -> { focus = sky; init(); });
        }
        button(8, height - 52, listWidth / 2, "<", () -> { page--; init(); });
        button(9 + listWidth / 2, height - 52, listWidth / 2, ">", () -> { page++; init(); });
        button(8, height - 27, listWidth, favoritesOnly ? "Show all skies" : "Show favorites", () -> {
            favoritesOnly = !favoritesOnly;
            page = 0;
            init();
        });
        int x = listWidth + 16, half = Math.max(1, (width - x - 10) / 2);
        button(x, height - 52, half, Favorites.isFavorite(focus) ? "Unfavorite" : "Favorite", () -> {
            Favorites.toggle(focus);
            init();
        });
        button(x + half + 2, height - 52, half, "Use sky", () -> {
            ModernClient.SETTINGS.selectSky(focus);
            onClose();
        });
        button(x, height - 27, half * 2 + 2, "Done", this::onClose);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawString(font, title, 8, 11, 0xffffff);
        int x = listWidth + 16, w = width - x - 8, h = Math.max(1, height - 136);
        graphics.drawString(font, focus.displayName, x, 11, 0xffffff);
        graphics.drawWordWrap(font, Component.literal(focus.description), x, 35 + h, w, 0xffffff);
        graphics.flush();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        double sx = viewport[2] / (double) width, sy = viewport[3] / (double) height;
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean drawn;
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glViewport(viewport[0] + (int) (x * sx), viewport[1] + (int) ((height - 29 - h) * sy),
                    Math.max(1, (int) (w * sx)), Math.max(1, (int) (h * sy)));
            drawn = preview.drawPreview(focus, (float) w / h, (System.nanoTime() - opened) * 1e-9);
        } finally {
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
        if (!drawn) graphics.drawString(font, "Preview unavailable", x, 32, 0xffffff);
    }

    @Override
    public void removed() {
        preview.close();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
