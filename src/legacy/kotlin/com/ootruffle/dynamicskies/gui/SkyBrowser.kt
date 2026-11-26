@file:OptIn(ExperimentalOneConfigUiApi::class)

package com.ootruffle.dynamicskies.gui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ootruffle.dynamicskies.config.DynamicSkiesConfig
import com.ootruffle.dynamicskies.sky.Favorites
import com.ootruffle.dynamicskies.sky.SkyRenderer
import com.ootruffle.dynamicskies.sky.SkyType
import net.minecraft.client.renderer.GlStateManager
import org.lwjgl.opengl.GL11
import org.polyfrost.compose.minecraft.canvas.PolyOffscreen
import org.polyfrost.compose.minecraft.preview.McContent
import org.polyfrost.compose.minecraft.screen.ComposeSupport
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.ui.v1.OneConfigUI
import org.polyfrost.oneconfig.api.ui.v1.components.Badge
import org.polyfrost.oneconfig.api.ui.v1.components.BadgeTone
import org.polyfrost.oneconfig.api.ui.v1.components.Card
import org.polyfrost.oneconfig.api.ui.v1.components.CardGrid
import org.polyfrost.oneconfig.api.ui.v1.components.Chip
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.Icon
import org.polyfrost.oneconfig.api.ui.v1.components.IconButton
import org.polyfrost.oneconfig.api.ui.v1.components.IconButtonStyle
import org.polyfrost.oneconfig.api.ui.v1.components.MediaTile
import org.polyfrost.oneconfig.api.ui.v1.components.PageScaffold
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigDestination
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPage
import org.polyfrost.oneconfig.api.ui.v1.page.OneConfigPages
import org.polyfrost.oneconfig.api.ui.v1.page.PageGroup
import org.polyfrost.oneconfig.api.ui.v1.page.PageText
import org.polyfrost.oneconfig.api.ui.v1.page.currentPageSearchQueryOrNull
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import kotlin.math.abs

/**
 * The sky browser, as a page inside OneConfig's own window: OneConfig's header, search box,
 * theme and UI scale, with OneConfig's card grid, tiles, chips and buttons. Each tile's live
 * preview is an [McContent]: PolyCompose hands it an offscreen framebuffer the size of its
 * spot in the layout and [SkyRenderer.renderPreview] draws the sky into it.
 *
 * The page isn't listed in OneConfig's sidebar; `/dynamicskies browse` and the "Browse Skies"
 * button open it. When PolyCompose's UI is switched off, [open] falls back to
 * [ClassicSkyBrowserScreen].
 */
object SkyBrowser : OneConfigPage("dynamicskies:skies", PageText.literal("Skies"), "sun", PageGroup.MODS_AND_OPTIONS) {

    override val showInSidebar: Boolean get() = false

    override val searchPlaceholder: PageText get() = PageText.literal("Search skies")

    @JvmStatic
    fun open() {
        if (!ComposeSupport.isAvailable) {
            ClassicSkyBrowserScreen.open()
            return
        }
        OneConfigPages.registerIfAbsent(this)
        OneConfigUI.open(this)
    }

    /**
     * Our settings, in the same OneConfig window. OneConfig finds a mod's config by its config
     * tree's id (the config file name, "dynamicskies.json"), not by the mod id.
     */
    private fun openSettings() {
        val config = DynamicSkiesConfig.INSTANCE
        val id = config.tree?.id ?: run {
            config.preload()
            config.tree?.id
        } ?: "dynamicskies.json"
        OneConfigUI.open(OneConfigDestination.ModConfig(OneConfigPages.modConfig(id)))
    }

    private val SPECIAL = setOf(SkyType.CUSTOM_IMAGE, SkyType.CUSTOM_SHADER, SkyType.NONE)

    private var favoritesOnly by mutableStateOf(false)
    private var focus by mutableStateOf(selected())
    private var inUse by mutableStateOf(selected())
    /** Bumped when favorites change, since [Favorites] isn't Compose state. */
    private var favoritesVersion by mutableIntStateOf(0)
    /** The last [SkyRenderer] status of each sky that couldn't be drawn, for the placeholder text. */
    private val status = mutableStateMapOf<SkyType, Int>()

    // Camera shared by every preview. Read on the render thread while previews draw, so plain fields.
    private var yaw = 0f
    private var pitch = -18f
    private var dragging = false
    private var lastFrameNanos = 0L

    private fun selected(): SkyType = SkyType.byIndex(DynamicSkiesConfig.INSTANCE.sky)

    private fun use(type: SkyType) {
        DynamicSkiesConfig.INSTANCE.setSky(type.ordinal)
        inUse = type
        focus = type
    }

    private fun toggleFavorite(type: SkyType) {
        Favorites.toggle(type)
        favoritesVersion++
    }

    private fun isFavorite(type: SkyType): Boolean = favoritesVersion >= 0 && Favorites.isFavorite(type)

    /** Once a frame, before the previews draw: spin the camera and keep the sky's clock running. */
    private fun beforePreviews() {
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0f else ((now - lastFrameNanos) / 1e9f).coerceAtMost(0.1f)
        lastFrameNanos = now
        if (!dragging) yaw += dt * 6f
        SkyRenderer.INSTANCE.tickForPreview()
        SkyRenderer.INSTANCE.beginPreviewFrame()
    }

    /** Draws [type] into the framebuffer PolyCompose has bound for one preview. */
    private fun drawSky(type: SkyType, w: Int, h: Int, fov: Float): Boolean {
        val result = SkyRenderer.INSTANCE.renderPreview(type, 0, 0, w, h, 0, 0, w, h, yaw, pitch, fov)
        if (result != SkyRenderer.PREVIEW_OK) {
            if (status[type] != result) status[type] = result
            return false
        }
        if (status.containsKey(type)) status.remove(type)
        // The sky shaders don't all write alpha; make the preview opaque so it doesn't fade into the page.
        GlStateManager.colorMask(false, false, false, true)
        GlStateManager.clearColor(0f, 0f, 0f, 1f)
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT)
        GlStateManager.colorMask(true, true, true, true)
        GlStateManager.clearColor(0f, 0f, 0f, 0f)
        return true
    }

    /** Drag anywhere over the previews to look around. Once a press moves, it no longer counts as a click. */
    private fun Modifier.dragToLook(): Modifier = pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = 3.dp.toPx()
            var last = down.position
            var moved = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes
                    .firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                val total = change.position - down.position
                if (!moved && abs(total.x) + abs(total.y) > slop) {
                    moved = true
                    dragging = true
                }
                if (moved) {
                    val delta = (change.position - last) / density
                    yaw += delta.x * 0.4f
                    pitch = (pitch + delta.y * 0.4f).coerceIn(-89f, 60f)
                    change.consume()
                }
                last = change.position
            }
            dragging = false
        }
    }

    @Composable
    override fun Content() {
        DisposableEffect(Unit) {
            // Registered before any preview's own hook, so it runs first each frame.
            val hook = PolyOffscreen.onBeforeUi(::beforePreviews)
            inUse = selected()
            onDispose { hook.close() }
        }

        val query = currentPageSearchQueryOrNull.orEmpty().trim()
        favoritesVersion // Recompose when favorites change.
        val entries = SkyType.values()
            .filter { !favoritesOnly || Favorites.isFavorite(it) }
            .filter { query.isEmpty() || it.displayName.contains(query, true) || it.description.contains(query, true) }
            .sortedBy { it in SPECIAL }

        PageScaffold(
            modifier = Modifier.dragToLook(),
            toolbar = {
                Chip("All skies", !favoritesOnly, { favoritesOnly = false })
                Chip("Favorites", favoritesOnly, { favoritesOnly = true }, icon = "star")
                Spacer(Modifier.weight(1f))
                TextButton(
                    "Settings",
                    { openSettings() },
                    leading = { Icon("settings", null) },
                )
            },
            sidePanel = { Details() },
            sidePanelWidth = 300.dp,
        ) {
            when {
                entries.isEmpty() && favoritesOnly && query.isEmpty() -> Centered {
                    EmptyState("No favorite skies", icon = "star", text = "Star a sky on its tile to keep it here.")
                }
                entries.isEmpty() -> Centered {
                    EmptyState("No skies match \"$query\"", icon = "sun")
                }
                else -> CardGrid(
                    entries,
                    key = { it.ordinal },
                    modifier = Modifier.fillMaxSize().padding(start = OneConfigTheme.current.spacing.pageGutter),
                    minCellWidth = 170.dp,
                    group = { if (it in SPECIAL) "Bring your own" else "Built-in" },
                ) { Tile(it) }
            }
        }
    }

    @Composable
    private fun Centered(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }

    @Composable
    private fun Tile(type: SkyType) {
        val source = remember { MutableInteractionSource() }
        val hovered by source.collectIsHoveredAsState()
        LaunchedEffect(hovered) {
            if (hovered && !dragging) focus = type
        }
        val favorite = isFavorite(type)
        MediaTile(
            onClick = { use(type) },
            label = type.displayName,
            selected = type == inUse,
            title = type.displayName,
            subtitle = if (type == inUse) "In use" else "Click to use",
            aspectRatio = 16f / 9f,
            interactionSource = source,
            actions = {
                IconButton(
                    if (favorite) "star-filled" else "star",
                    if (favorite) "Remove from favorites" else "Add to favorites",
                    { toggleFavorite(type) },
                    style = IconButtonStyle.Overlay,
                )
            },
            badge = if (type == inUse) ({ Badge("In use", tone = BadgeTone.Success, overImage = true) }) else null,
            tags = if (favorite) ({ Icon("star-filled", null) }) else null,
        ) {
            SkyPreview(type, Modifier.fillMaxSize(), fov = 75f)
        }
    }

    @Composable
    private fun SkyPreview(type: SkyType, modifier: Modifier, fov: Float) {
        McContent(
            modifier = modifier,
            live = true,
            key = type,
            fallback = {
                val message = when {
                    status[type] == SkyRenderer.PREVIEW_FAILED && type == SkyType.CUSTOM_IMAGE -> "No image yet"
                    status[type] == SkyRenderer.PREVIEW_FAILED -> "Shader error"
                    else -> "Loading…"
                }
                Text(
                    message,
                    Modifier.align(Alignment.Center),
                    color = OneConfigTheme.current.mutedText,
                    style = OneConfigTheme.current.typography.caption,
                )
            },
        ) { _, w, h -> drawSky(type, w, h, fov) }
    }

    /** The side panel: a big preview of the hovered (or last hovered) sky. */
    @Composable
    private fun Details() {
        val theme = OneConfigTheme.current
        val spacing = theme.spacing
        val type = focus
        val isInUse = type == inUse
        val favorite = isFavorite(type)
        Column(
            Modifier.fillMaxSize().padding(spacing.pageGutter),
            verticalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Card(
                Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                onClick = { use(type) },
                label = type.displayName,
                selected = isInUse,
            ) {
                SkyPreview(type, Modifier.fillMaxSize(), fov = 80f)
            }
            Text(type.displayName, style = theme.typography.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(type.description, color = theme.textColorSecondary, style = theme.typography.body)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                if (isInUse) Badge("In use", tone = BadgeTone.Success)
                if (type.overlayFriendly()) Badge("Works as an overlay")
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    if (isInUse) "In use" else "Use this sky",
                    { use(type) },
                    Modifier.weight(1f),
                    style = ControlStyle.Primary,
                    enabled = !isInUse,
                    fillWidth = true,
                )
                IconButton(
                    if (favorite) "star-filled" else "star",
                    if (favorite) "Remove from favorites" else "Add to favorites",
                    { toggleFavorite(type) },
                    style = IconButtonStyle.Framed,
                    selected = favorite,
                )
            }
            Text("Drag a preview to look around", color = theme.mutedText, style = theme.typography.caption)
        }
    }
}
