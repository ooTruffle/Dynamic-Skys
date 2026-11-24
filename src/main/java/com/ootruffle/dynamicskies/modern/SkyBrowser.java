package com.ootruffle.dynamicskies.modern;

import com.ootruffle.dynamicskies.sky.Favorites;
import com.ootruffle.dynamicskies.sky.ModernSkyRenderer;
import com.ootruffle.dynamicskies.sky.SkyType;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.polyfrost.oneconfig.utils.v1.dsl.ScreensKt;
//? if >= 26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else
import net.minecraft.client.gui.GuiGraphics;
//? if >= 1.21.10 {
/*import net.minecraft.client.input.MouseButtonEvent;
*///?}

import java.util.ArrayList;
import java.util.List;

/**
 * Live previews of every sky, like the 1.8.9 browser. The grid on the left
 * shows them all; the big pane on the right shows the hovered one. Click a
 * tile to use it, click its star to favorite it, drag to look around, scroll
 * for more.
 *
 * The previews are drawn straight to the screen with OpenGL. Up to 1.21.5 the GUI
 * draws as it goes, so they're drawn after the background and buttons. From 1.21.6
 * the GUI is collected and drawn at the end of the frame, over anything drawn here,
 * so the backdrop is drawn directly too and the labels and stars land on top.
 */
final class SkyBrowser extends Screen {

    private static final int GAP = 6;
    private static final int TOP = 34;
    private static final int BOTTOM = 34;
    private static final int LABEL_H = 13;
    private static final int STAR = 11;
    /** Space under the big preview for name, description (2 lines) and status. */
    private static final int PANE_TEXT_H = 58;

    private static boolean favoritesOnly;

    private final List<SkyType> entries = new ArrayList<>();
    private SkyType focus;

    private float yaw = 0f;
    private float pitch = -18f;
    private boolean dragging;
    private double pressX, pressY;
    private boolean moved;
    private float scroll;
    private long lastFrameNanos;

    private int cols, tileW, tileH, gridRight;
    private int paneX, paneW, paneImgH;
    private Button filterButton, useButton, favoriteButton;

    SkyBrowser() {
        super(Component.literal("Dynamic Skies"));
    }

    @Override
    protected void init() {
        if (focus == null) focus = selected();
        rebuildEntries();

        boolean wide = width >= 420;
        paneW = wide ? Math.max(160, Math.min(440, Math.round(width * 0.36f))) : 0;
        paneX = width - paneW - 10;
        gridRight = wide ? paneX - 10 : width - 10;
        int usable = gridRight - 10;
        cols = Math.max(1, Math.min(5, (usable + GAP) / (120 + GAP)));
        tileW = (usable - GAP * (cols - 1)) / cols;
        tileH = tileW * 9 / 16 + LABEL_H;
        paneImgH = Math.max(60, Math.min(paneW * 3 / 4, listBottom() - TOP - PANE_TEXT_H - 24));

        int bw = 100;
        filterButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            favoritesOnly = !favoritesOnly;
            rebuildEntries();
            scroll = 0;
            clampScroll();
            updateButtons();
        }).bounds(width / 2 - bw * 3 / 2 - 6, height - 27, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> ScreensKt.openUI(ModernClient.SETTINGS))
                .bounds(width / 2 - bw / 2, height - 27, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 + bw / 2 + 6, height - 27, bw, 20).build());
        if (wide) {
            int by = TOP + paneImgH + PANE_TEXT_H;
            int half = (paneW - 4) / 2;
            useButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
                use(focus);
                updateButtons();
            }).bounds(paneX, by, half, 20).build());
            favoriteButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
                toggleFavorite(focus);
                updateButtons();
            }).bounds(paneX + half + 4, by, paneW - half - 4, 20).build());
        } else {
            useButton = favoriteButton = null;
        }
        updateButtons();
        clampScroll();
    }

    private static SkyType selected() {
        return SkyType.byIndex(ModernClient.SETTINGS.sky);
    }

    /** Built-in skies first, the "bring your own" ones last; only favorites when filtered. */
    private void rebuildEntries() {
        entries.clear();
        List<SkyType> special = new ArrayList<>();
        for (SkyType t : SkyType.values()) {
            if (favoritesOnly && !Favorites.isFavorite(t)) continue;
            if (t == SkyType.CUSTOM_IMAGE || t == SkyType.CUSTOM_SHADER || t == SkyType.NONE) special.add(t);
            else entries.add(t);
        }
        entries.addAll(special);
    }

    private void updateButtons() {
        filterButton.setMessage(Component.literal(favoritesOnly ? "Show: Favorites" : "Show: All"));
        if (useButton != null) {
            boolean inUse = focus == selected();
            useButton.active = !inUse;
            useButton.setMessage(Component.literal(inUse ? "In Use" : "Use This Sky"));
            favoriteButton.setMessage(Component.literal(Favorites.isFavorite(focus) ? "★ Favorited" : "☆ Favorite"));
        }
    }

    private void use(SkyType type) {
        ModernClient.SETTINGS.selectSky(type);
        click();
    }

    private void toggleFavorite(SkyType type) {
        Favorites.toggle(type);
        if (favoritesOnly) {
            rebuildEntries();
            clampScroll();
        }
        click();
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int listBottom() {
        return height - BOTTOM;
    }

    private int contentHeight() {
        int rows = (entries.size() + cols - 1) / cols;
        return Math.max(0, rows * tileH + (rows - 1) * GAP);
    }

    private void clampScroll() {
        float max = Math.max(0, contentHeight() - (listBottom() - TOP));
        scroll = Math.max(0, Math.min(max, scroll));
    }

    private int tileX(int i) {
        return 10 + (i % cols) * (tileW + GAP);
    }

    private int tileY(int i) {
        return TOP + (i / cols) * (tileH + GAP) - Math.round(scroll);
    }

    private int tileAt(double mx, double my) {
        if (my < TOP || my >= listBottom() || mx > gridRight) return -1;
        for (int i = 0; i < entries.size(); i++) {
            int x = tileX(i), y = tileY(i);
            if (mx >= x && mx < x + tileW && my >= y && my < y + tileH) return i;
        }
        return -1;
    }

    private boolean onStar(int i, double mx, double my) {
        int sx = tileX(i) + tileW - STAR - 2, sy = tileY(i) + 2;
        return mx >= sx && mx < sx + STAR && my >= sy && my < sy + STAR;
    }

    private boolean inPaneImage(double mx, double my) {
        return paneW > 0 && mx >= paneX && mx < paneX + paneW && my >= TOP && my < TOP + paneImgH;
    }

    // ---- Rendering -------------------------------------------------------------

    //? if >= 26.1 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        frame(new Gfx(graphics), mouseX, mouseY, () -> super.extractRenderState(graphics, mouseX, mouseY, partialTick));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Drawn straight to the screen in frame(), under the previews.
    }
    *///?} else {
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        frame(new Gfx(graphics), mouseX, mouseY, () -> super.render(graphics, mouseX, mouseY, partialTick));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // A plain dim backdrop: the menu blur would also blur the previews' edges.
        //? if < 1.21.6 {
        graphics.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        //?}
    }
    //?}

    private void frame(Gfx g, int mouseX, int mouseY, Runnable vanilla) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        if (!dragging) yaw += dt * 6f;

        var renderer = ModernSkyRenderer.INSTANCE;
        renderer.tickForPreview();
        renderer.beginPreviewFrame();
        //? if >= 1.21.6 {
        /*renderer.requestDim(0.063f, 0.063f, 0.063f, 0.78f);
        *///?}
        // Background and buttons; up to 1.21.5 they're drawn now, so the skies go after them.
        vanilla.run();
        g.flush();

        double scale = minecraft.getWindow().getGuiScale();
        int windowH = minecraft.getWindow().getHeight();
        SkyType inUse = selected();
        int hovered = dragging ? -1 : tileAt(mouseX, mouseY);
        if (hovered >= 0 && focus != entries.get(hovered)) {
            focus = entries.get(hovered);
            updateButtons();
        }
        int clipTop = TOP, clipBottom = listBottom();

        // The big preview goes first so it gets compiled before the tiles.
        drawPane(g, inUse, scale, windowH);

        for (int i = 0; i < entries.size(); i++) {
            SkyType type = entries.get(i);
            int x = tileX(i), y = tileY(i);
            int imgH = tileH - LABEL_H;
            if (y + tileH < clipTop || y > clipBottom) continue;

            int border = type == inUse ? 0xFF4FD1E8 : i == hovered ? 0xFFFFFFFF : type == focus && paneW > 0 ? 0xA0FFFFFF : 0x50FFFFFF;
            drawFrame(g, x - 1, y - 1, x + tileW + 1, y + tileH + 1, border, clipTop, clipBottom);
            drawSky(g, type, x, y, tileW, imgH, clipTop, clipBottom, scale, windowH, 75f);

            // Star: always shown on favorites, otherwise only on the hovered tile.
            boolean fav = Favorites.isFavorite(type);
            if (fav || i == hovered) {
                int sx = x + tileW - STAR - 2, sy = y + 2;
                boolean overStar = i == hovered && onStar(i, mouseX, mouseY);
                fillClipped(g, sx, sy, sx + STAR, sy + STAR, overStar ? 0xC0303030 : 0x90000000, clipTop, clipBottom);
                drawCenteredClipped(g, fav ? "★" : "☆", sx + STAR / 2 + 1, sy + 2,
                        fav ? 0xFFFFD54A : overStar ? 0xFFFFFFFF : 0xFFB0B0B0, clipTop, clipBottom);
            }

            int ly = y + imgH;
            fillClipped(g, x, ly, x + tileW, ly + LABEL_H, type == inUse ? 0xE0124A55 : 0xE0101014, clipTop, clipBottom);
            String name = font.plainSubstrByWidth(type.displayName + (type == inUse ? "  ✔" : ""), tileW - 4);
            drawCenteredClipped(g, name, x + tileW / 2, ly + 3, type == inUse ? 0xFF8FEFFF : 0xFFE0E0E0, clipTop, clipBottom);
        }

        if (entries.isEmpty()) {
            int cx = (10 + gridRight) / 2;
            g.centered("No favorites yet", cx, TOP + 30, 0xFFFFFFFF);
            g.centered("Show all skies and click the ☆ on a tile", cx, TOP + 44, 0xFFA0A0A0);
        }

        // Scrollbar.
        int content = contentHeight(), view = listBottom() - TOP;
        if (content > view) {
            int barX = gridRight + 3;
            int barH = Math.max(20, view * view / content);
            int barY = TOP + Math.round((view - barH) * (scroll / (content - view)));
            g.fill(barX, TOP, barX + 3, listBottom(), 0x40FFFFFF);
            g.fill(barX, barY, barX + 3, barY + barH, 0xC0FFFFFF);
        }

        g.centered("Dynamic Skies", width / 2, 8, 0xFFFFFFFF);
        g.centered("Click a sky to use it  ·  ☆ to favorite  ·  Drag to look around  ·  Scroll for more",
                width / 2, 20, 0xFFA0A0A0);

        // No room for the big preview: show the name and description as a tooltip instead.
        if (paneW <= 0 && hovered >= 0) {
            SkyType t = entries.get(hovered);
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(t.displayName));
            tip.add(Component.literal("§7" + t.description));
            g.tooltip(tip, mouseX, mouseY);
        }
    }

    /** The big preview of the hovered (or last hovered) sky. */
    private void drawPane(Gfx g, SkyType inUse, double scale, int windowH) {
        if (paneW <= 0 || focus == null) return;
        int x = paneX, y = TOP;
        drawFrame(g, x - 1, y - 1, x + paneW + 1, y + paneImgH + 1, focus == inUse ? 0xFF4FD1E8 : 0xA0FFFFFF, 0, height);
        drawSky(g, focus, x, y, paneW, paneImgH, y, y + paneImgH, scale, windowH, 80f);

        int ty = y + paneImgH + 6;
        g.pushScaled(x, ty, 1.5f);
        g.text(focus.displayName, 0, 0, 0xFFFFFFFF);
        g.pop();

        List<FormattedCharSequence> lines = font.split(Component.literal(focus.description), paneW);
        int dy = ty + 15;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(lines.get(i), x, dy, 0xFFD0D0D0);
            dy += 10;
        }

        String status = focus == inUse ? "✔ In use" : "Click its tile or \"Use This Sky\"";
        if (focus.overlayFriendly()) status += "  ·  works well as an overlay";
        g.text(font.plainSubstrByWidth(status, paneW), x, ty + 37, focus == inUse ? 0xFF8FEFFF : 0xFF909090);
    }

    private void drawSky(Gfx g, SkyType type, int x, int y, int w, int h, int clipTop, int clipBottom,
                         double scale, int windowH, float fov) {
        int top = Math.max(y, clipTop), bottom = Math.min(y + h, clipBottom);
        int px = (int) Math.round(x * scale), pw = (int) Math.round(w * scale), ph = (int) Math.round(h * scale);
        int status = ModernSkyRenderer.INSTANCE.requestPreview(type,
                px, windowH - (int) Math.round((y + h) * scale), pw, ph,
                px, windowH - (int) Math.round(bottom * scale), pw, (int) Math.round(Math.max(0, bottom - top) * scale),
                yaw, pitch, fov);
        if (status != ModernSkyRenderer.PREVIEW_OK) {
            fillClipped(g, x, y, x + w, y + h, 0xFF101014, clipTop, clipBottom);
            String msg = status == ModernSkyRenderer.PREVIEW_LOADING ? "Loading…"
                    : type == SkyType.CUSTOM_IMAGE ? "No image yet" : "Shader error";
            drawCenteredClipped(g, msg, x + w / 2, y + h / 2 - 4, 0xFF888888, clipTop, clipBottom);
        }
    }

    private static void drawFrame(Gfx g, int l, int t, int r, int b, int color, int clipTop, int clipBottom) {
        fillClipped(g, l, t, r, t + 1, color, clipTop, clipBottom);
        fillClipped(g, l, b - 1, r, b, color, clipTop, clipBottom);
        fillClipped(g, l, t, l + 1, b, color, clipTop, clipBottom);
        fillClipped(g, r - 1, t, r, b, color, clipTop, clipBottom);
    }

    private static void fillClipped(Gfx g, int l, int t, int r, int b, int color, int clipTop, int clipBottom) {
        t = Math.max(t, clipTop);
        b = Math.min(b, clipBottom);
        if (b > t) g.fill(l, t, r, b, color);
    }

    private static void drawCenteredClipped(Gfx g, String text, int cx, int y, int color, int clipTop, int clipBottom) {
        if (y >= clipTop && y + 8 <= clipBottom) g.centered(text, cx, y, color);
    }

    /** The few GUI drawing calls this screen uses, under their name in each version. */
    private final class Gfx {
        //? if >= 26.1 {
        /*private final GuiGraphicsExtractor g;

        Gfx(GuiGraphicsExtractor g) {
            this.g = g;
        }
        *///?} else {
        private final GuiGraphics g;

        Gfx(GuiGraphics g) {
            this.g = g;
        }
        //?}

        void fill(int l, int t, int r, int b, int color) {
            g.fill(l, t, r, b, color);
        }

        void text(String text, int x, int y, int color) {
            //? if >= 26.1 {
            /*g.text(font, text, x, y, color);
            *///?} else
            g.drawString(font, text, x, y, color);
        }

        void text(FormattedCharSequence text, int x, int y, int color) {
            //? if >= 26.1 {
            /*g.text(font, text, x, y, color);
            *///?} else
            g.drawString(font, text, x, y, color);
        }

        void centered(String text, int cx, int y, int color) {
            //? if >= 26.1 {
            /*g.centeredText(font, text, cx, y, color);
            *///?} else
            g.drawCenteredString(font, text, cx, y, color);
        }

        void pushScaled(float x, float y, float scale) {
            //? if >= 1.21.6 {
            /*g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(scale, scale);
            *///?} else {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(scale, scale, 1f);
            //?}
        }

        void pop() {
            //? if >= 1.21.6 {
            /*g.pose().popMatrix();
            *///?} else
            g.pose().popPose();
        }

        void tooltip(List<Component> lines, int x, int y) {
            //? if >= 1.21.6 {
            /*g.setTooltipForNextFrame(font, lines, java.util.Optional.empty(), x, y);
            *///?} else
            g.renderComponentTooltip(font, lines, x, y);
        }

        /** Draws what's been queued so far; from 1.21.6 everything is drawn at the end of the frame instead. */
        void flush() {
            //? if < 1.21.6 {
            g.flush();
            //?}
        }
    }

    // ---- Input -----------------------------------------------------------------

    private boolean press(double mouseX, double mouseY, int button) {
        boolean inGrid = mouseY >= TOP && mouseY < listBottom() && mouseX <= gridRight;
        if (button == 0 && (inGrid || inPaneImage(mouseX, mouseY))) {
            dragging = true;
            moved = false;
            pressX = mouseX;
            pressY = mouseY;
            return true;
        }
        return false;
    }

    private void drag(double mouseX, double mouseY, double dragX, double dragY) {
        if (Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 3) moved = true;
        yaw += (float) dragX * 0.6f;
        pitch = Math.max(-89f, Math.min(60f, pitch + (float) dragY * 0.6f));
    }

    /** Returns whether a drag was going on. */
    private boolean release(double mouseX, double mouseY) {
        if (dragging && !moved) {
            int i = tileAt(mouseX, mouseY);
            if (i >= 0) {
                SkyType type = entries.get(i);
                if (onStar(i, mouseX, mouseY)) toggleFavorite(type);
                else use(type);
                focus = type;
                updateButtons();
            } else if (inPaneImage(mouseX, mouseY)) {
                use(focus);
                updateButtons();
            }
        }
        boolean wasDragging = dragging;
        dragging = false;
        return wasDragging;
    }

    //? if >= 1.21.10 {
    /*@Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick) || press(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!dragging) return super.mouseDragged(event, dragX, dragY);
        drag(event.x(), event.y(), dragX, dragY);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean wasDragging = release(event.x(), event.y());
        return super.mouseReleased(event) || wasDragging;
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button) || press(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!dragging) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        drag(mouseX, mouseY, dragX, dragY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean wasDragging = release(mouseX, mouseY);
        return super.mouseReleased(mouseX, mouseY, button) || wasDragging;
    }
    //?}

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scroll -= (float) Math.signum(scrollY) * (tileH + GAP) * 0.6f;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
