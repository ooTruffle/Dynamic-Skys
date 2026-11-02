package com.ootruffle.dynamicskies.gui;

import com.ootruffle.dynamicskies.config.DynamicSkiesConfig;
import com.ootruffle.dynamicskies.sky.Favorites;
import com.ootruffle.dynamicskies.sky.SkyRenderer;
import com.ootruffle.dynamicskies.sky.SkyType;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.polyfrost.oneconfig.utils.v1.dsl.ScreensKt;

import java.util.ArrayList;
import java.util.List;

/**
 * Live previews of every sky. The grid on the left shows them all; the big
 * pane on the right shows the hovered one. Click a tile to use it, click its
 * star to favorite it, drag to look around, scroll for more.
 */
public class SkyBrowserScreen extends GuiScreen {

    private static final int GAP = 6;
    private static final int TOP = 34;
    private static final int BOTTOM = 34;
    private static final int LABEL_H = 13;
    private static final int STAR = 11;
    /** Space under the big preview for name, description (2 lines) and status. */
    private static final int PANE_TEXT_H = 58;

    private static final int BTN_SETTINGS = 0;
    private static final int BTN_DONE = 1;
    private static final int BTN_FILTER = 2;
    private static final int BTN_USE = 3;
    private static final int BTN_FAVORITE = 4;

    private static boolean favoritesOnly;

    private final List<SkyType> entries = new ArrayList<>();
    private SkyType focus;

    private float yaw = 0f;
    private float pitch = -18f;
    private boolean dragging;
    private int pressX, pressY, lastX, lastY;
    private boolean moved;
    private float scroll;
    private long lastFrameNanos;

    private int cols, tileW, tileH, gridRight;
    private int paneX, paneW, paneImgH;
    private GuiButton filterButton, useButton, favoriteButton;

    /** Opens the browser a moment later, so whatever screen is open now (OneConfig, chat) closes first. */
    public static void open() {
        ScreensKt.openScreen(new SkyBrowserScreen(), 2);
    }

    @Override
    public void initGui() {
        if (focus == null) {
            focus = selected();
        }
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

        buttonList.clear();
        int bw = 100;
        filterButton = new GuiButton(BTN_FILTER, width / 2 - bw * 3 / 2 - 6, height - 27, bw, 20, "");
        buttonList.add(filterButton);
        buttonList.add(new GuiButton(BTN_SETTINGS, width / 2 - bw / 2, height - 27, bw, 20, "Settings"));
        buttonList.add(new GuiButton(BTN_DONE, width / 2 + bw / 2 + 6, height - 27, bw, 20, "Done"));
        if (wide) {
            int by = TOP + paneImgH + PANE_TEXT_H;
            int half = (paneW - 4) / 2;
            useButton = new GuiButton(BTN_USE, paneX, by, half, 20, "Use This Sky");
            favoriteButton = new GuiButton(BTN_FAVORITE, paneX + half + 4, by, paneW - half - 4, 20, "");
            buttonList.add(useButton);
            buttonList.add(favoriteButton);
        } else {
            useButton = favoriteButton = null;
        }
        updateButtons();
        clampScroll();
    }

    private static SkyType selected() {
        return SkyType.byIndex(DynamicSkiesConfig.INSTANCE.sky);
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
        filterButton.displayString = favoritesOnly ? "Show: Favorites" : "Show: All";
        if (useButton != null) {
            boolean inUse = focus == selected();
            useButton.enabled = !inUse;
            useButton.displayString = inUse ? "In Use" : "Use This Sky";
            favoriteButton.displayString = Favorites.isFavorite(focus) ? "★ Favorited" : "☆ Favorite";
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BTN_SETTINGS:
                ScreensKt.openUI(DynamicSkiesConfig.INSTANCE);
                break;
            case BTN_DONE:
                mc.displayGuiScreen(null);
                break;
            case BTN_FILTER:
                favoritesOnly = !favoritesOnly;
                rebuildEntries();
                scroll = 0;
                clampScroll();
                break;
            case BTN_USE:
                use(focus);
                break;
            case BTN_FAVORITE:
                toggleFavorite(focus);
                break;
        }
        updateButtons();
    }

    private void use(SkyType type) {
        DynamicSkiesConfig.INSTANCE.setSky(type.ordinal());
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
        mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1f));
    }

    @Override
    public boolean doesGuiPauseGame() {
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

    private int tileAt(int mx, int my) {
        if (my < TOP || my >= listBottom() || mx > gridRight) return -1;
        for (int i = 0; i < entries.size(); i++) {
            int x = tileX(i), y = tileY(i);
            if (mx >= x && mx < x + tileW && my >= y && my < y + tileH) return i;
        }
        return -1;
    }

    private boolean onStar(int i, int mx, int my) {
        int sx = tileX(i) + tileW - STAR - 2, sy = tileY(i) + 2;
        return mx >= sx && mx < sx + STAR && my >= sy && my < sy + STAR;
    }

    private boolean inPaneImage(int mx, int my) {
        return paneW > 0 && mx >= paneX && mx < paneX + paneW && my >= TOP && my < TOP + paneImgH;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1e9f);
        lastFrameNanos = now;
        if (!dragging) {
            yaw += dt * 6f;
        }

        drawDefaultBackground();
        SkyRenderer.INSTANCE.tickForPreview();
        SkyRenderer.INSTANCE.beginPreviewFrame();

        int scale = new ScaledResolution(mc).getScaleFactor();
        SkyType inUse = selected();
        int hovered = dragging ? -1 : tileAt(mouseX, mouseY);
        if (hovered >= 0 && focus != entries.get(hovered)) {
            focus = entries.get(hovered);
            updateButtons();
        }
        int clipTop = TOP, clipBottom = listBottom();

        // The big preview goes first so it gets compiled before the tiles.
        drawPane(inUse, scale);

        for (int i = 0; i < entries.size(); i++) {
            SkyType type = entries.get(i);
            int x = tileX(i), y = tileY(i);
            int imgH = tileH - LABEL_H;
            if (y + tileH < clipTop || y > clipBottom) continue;

            int border = type == inUse ? 0xFF4FD1E8 : i == hovered ? 0xFFFFFFFF : type == focus && paneW > 0 ? 0xA0FFFFFF : 0x50FFFFFF;
            drawFrame(x - 1, y - 1, x + tileW + 1, y + tileH + 1, border, clipTop, clipBottom);
            drawSky(type, x, y, tileW, imgH, clipTop, clipBottom, scale, 75f);

            // Star: always shown on favorites, otherwise only on the hovered tile.
            boolean fav = Favorites.isFavorite(type);
            if (fav || i == hovered) {
                int sx = x + tileW - STAR - 2, sy = y + 2;
                boolean overStar = i == hovered && onStar(i, mouseX, mouseY);
                fillClipped(sx, sy, sx + STAR, sy + STAR, overStar ? 0xC0303030 : 0x90000000, clipTop, clipBottom);
                drawCenteredClipped(fav ? "★" : "☆", sx + STAR / 2 + 1, sy + 2,
                        fav ? 0xFFFFD54A : overStar ? 0xFFFFFFFF : 0xFFB0B0B0, clipTop, clipBottom);
            }

            int ly = y + imgH;
            fillClipped(x, ly, x + tileW, ly + LABEL_H, type == inUse ? 0xE0124A55 : 0xE0101014, clipTop, clipBottom);
            String name = fontRendererObj.trimStringToWidth(type.displayName + (type == inUse ? "  ✔" : ""), tileW - 4);
            drawCenteredClipped(name, x + tileW / 2, ly + 3, type == inUse ? 0xFF8FEFFF : 0xFFE0E0E0, clipTop, clipBottom);
        }

        if (entries.isEmpty()) {
            int cx = (10 + gridRight) / 2;
            drawCenteredString(fontRendererObj, "No favorites yet", cx, TOP + 30, 0xFFFFFF);
            drawCenteredString(fontRendererObj, "Show all skies and click the ☆ on a tile", cx, TOP + 44, 0xA0A0A0);
        }

        // Scrollbar.
        int content = contentHeight(), view = listBottom() - TOP;
        if (content > view) {
            int barX = gridRight + 3;
            int barH = Math.max(20, view * view / content);
            int barY = TOP + Math.round((view - barH) * (scroll / (content - view)));
            Gui.drawRect(barX, TOP, barX + 3, listBottom(), 0x40FFFFFF);
            Gui.drawRect(barX, barY, barX + 3, barY + barH, 0xC0FFFFFF);
        }

        drawCenteredString(fontRendererObj, "Dynamic Skies", width / 2, 8, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "Click a sky to use it  ·  ☆ to favorite  ·  Drag to look around  ·  Scroll for more",
                width / 2, 20, 0xA0A0A0);
        super.drawScreen(mouseX, mouseY, partialTicks);

        // No room for the big preview: show the name and description as a tooltip instead.
        if (paneW <= 0 && hovered >= 0) {
            SkyType t = entries.get(hovered);
            List<String> tip = new ArrayList<>();
            tip.add(t.displayName);
            for (String line : fontRendererObj.listFormattedStringToWidth(t.description, 180)) {
                tip.add("§7" + line);
            }
            drawHoveringText(tip, mouseX, mouseY);
        }
    }

    /** The big preview of the hovered (or last hovered) sky. */
    private void drawPane(SkyType inUse, int scale) {
        if (paneW <= 0 || focus == null) return;
        int x = paneX, y = TOP;
        drawFrame(x - 1, y - 1, x + paneW + 1, y + paneImgH + 1, focus == inUse ? 0xFF4FD1E8 : 0xA0FFFFFF, 0, height);
        drawSky(focus, x, y, paneW, paneImgH, y, y + paneImgH, scale, 80f);

        int ty = y + paneImgH + 6;
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, ty, 0);
        GlStateManager.scale(1.5f, 1.5f, 1f);
        fontRendererObj.drawStringWithShadow(focus.displayName, 0, 0, 0xFFFFFF);
        GlStateManager.popMatrix();

        List<String> lines = fontRendererObj.listFormattedStringToWidth(focus.description, paneW);
        int dy = ty + 15;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            fontRendererObj.drawStringWithShadow(lines.get(i), x, dy, 0xD0D0D0);
            dy += 10;
        }

        String status = focus == inUse ? "✔ In use" : "Click its tile or \"Use This Sky\"";
        if (focus.overlayFriendly()) {
            status += "  ·  works well as an overlay";
        }
        fontRendererObj.drawStringWithShadow(fontRendererObj.trimStringToWidth(status, paneW), x, ty + 37,
                focus == inUse ? 0x8FEFFF : 0x909090);
    }

    private void drawSky(SkyType type, int x, int y, int w, int h, int clipTop, int clipBottom, int scale, float fov) {
        int top = Math.max(y, clipTop), bottom = Math.min(y + h, clipBottom);
        int status = SkyRenderer.INSTANCE.renderPreview(type,
                x * scale, mc.displayHeight - (y + h) * scale, w * scale, h * scale,
                x * scale, mc.displayHeight - bottom * scale, w * scale, Math.max(0, bottom - top) * scale,
                yaw, pitch, fov);
        if (status != SkyRenderer.PREVIEW_OK) {
            fillClipped(x, y, x + w, y + h, 0xFF101014, clipTop, clipBottom);
            String msg = status == SkyRenderer.PREVIEW_LOADING ? "Loading…"
                    : type == SkyType.CUSTOM_IMAGE ? "No image yet" : "Shader error";
            drawCenteredClipped(msg, x + w / 2, y + h / 2 - 4, 0xFF888888, clipTop, clipBottom);
        }
    }

    private void drawFrame(int l, int t, int r, int b, int color, int clipTop, int clipBottom) {
        fillClipped(l, t, r, t + 1, color, clipTop, clipBottom);
        fillClipped(l, b - 1, r, b, color, clipTop, clipBottom);
        fillClipped(l, t, l + 1, b, color, clipTop, clipBottom);
        fillClipped(r - 1, t, r, b, color, clipTop, clipBottom);
    }

    private static void fillClipped(int l, int t, int r, int b, int color, int clipTop, int clipBottom) {
        t = Math.max(t, clipTop);
        b = Math.min(b, clipBottom);
        if (b > t) Gui.drawRect(l, t, r, b, color);
    }

    private void drawCenteredClipped(String text, int cx, int y, int color, int clipTop, int clipBottom) {
        if (y >= clipTop && y + 8 <= clipBottom) {
            drawCenteredString(fontRendererObj, text, cx, y, color);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        boolean inGrid = mouseY >= TOP && mouseY < listBottom() && mouseX <= gridRight;
        if (mouseButton == 0 && (inGrid || inPaneImage(mouseX, mouseY))) {
            dragging = true;
            moved = false;
            pressX = lastX = mouseX;
            pressY = lastY = mouseY;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!dragging) return;
        if (Math.abs(mouseX - pressX) + Math.abs(mouseY - pressY) > 3) {
            moved = true;
        }
        yaw += (mouseX - lastX) * 0.6f;
        pitch = Math.max(-89f, Math.min(60f, pitch + (mouseY - lastY) * 0.6f));
        lastX = mouseX;
        lastY = mouseY;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (dragging && !moved) {
            int i = tileAt(mouseX, mouseY);
            if (i >= 0) {
                SkyType type = entries.get(i);
                if (onStar(i, mouseX, mouseY)) {
                    toggleFavorite(type);
                } else {
                    use(type);
                }
                focus = type;
                updateButtons();
            } else if (inPaneImage(mouseX, mouseY)) {
                use(focus);
                updateButtons();
            }
        }
        dragging = false;
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scroll -= Math.signum(wheel) * (tileH + GAP) * 0.6f;
            clampScroll();
        }
    }
}
