package com.ootruffle.dynamicskies.modern;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.logging.LogUtils;
import com.ootruffle.dynamicskies.audio.AudioManager;
import com.ootruffle.dynamicskies.sky.Favorites;
import com.ootruffle.dynamicskies.sky.ModernSkyRenderer;
import com.ootruffle.dynamicskies.sky.SkyType;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.commands.v1.CommandManager;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.annotations.Button;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.Include;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;
import org.polyfrost.oneconfig.utils.v1.dsl.ScreensKt;

import java.nio.file.Path;

public final class ModernClient implements ClientModInitializer {
    public static final Settings SETTINGS = new Settings();
    public static final String AUDIO_GUIDE_URL = "https://github.com/ooTruffle/Dynamic-Skys/blob/master/docs/AUDIO_SETUP.md";

    @Override
    public void onInitializeClient() {
        SETTINGS.preload();
        CommandManager.INSTANCE.register(withBrowse(ScreensKt.addDefaultCommand(SETTINGS, "dynamicskies")));
        AudioManager.configure(() -> new AudioManager.Options(SETTINGS.enabled ? SETTINGS.audioSource : 0,
                SETTINGS.audioDevice, SETTINGS.sensitivity, SETTINGS.smoothing, SETTINGS.fakeBpm,
                SETTINGS.dropDetection, SETTINGS.dropSensitivity), ModernClient::chat);
    }

    private static <S> LiteralArgumentBuilder<S> withBrowse(LiteralArgumentBuilder<S> root) {
        return root.then(LiteralArgumentBuilder.<S>literal("browse").executes(ctx -> {
            SETTINGS.browseSkies();
            return 1;
        }));
    }

    public static void chat(String message) {
        var mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player != null) Compat.chat(Component.literal("§b[Dynamic Skies]§r " + message));
            else LogUtils.getLogger().info("[Dynamic Skies] {}", message);
        });
    }

    /** A chat line that opens {@code url} when clicked. */
    static void chatLink(String text, String url) {
        var mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.player == null) return;
            var link = Component.literal(text).withStyle(style -> style.withColor(ChatFormatting.AQUA).withUnderlined(true)
                    //? if >= 1.21.5 {
                    .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(url))));
                    //?} else
                    /*.withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));*/
            Compat.chat(Component.literal("§b[Dynamic Skies]§r ").append(link));
        });
    }

    private static void openFolder(Path folder) {
        Compat.openFolder(folder);
    }

    /**
     * All settings, opened with /dynamicskies. Laid out like the 1.8.9 config so
     * both versions read the same; the sky dropdown follows {@link SkyType}'s order.
     */
    public static final class Settings extends Config {

        // ---- Sky ---------------------------------------------------------------

        @Switch(title = "Enable Dynamic Skies", category = "Sky", subcategory = "Sky")
        public boolean enabled = true;

        @Dropdown(title = "Sky", description = "Which animated sky to show.", options = {}, category = "Sky", subcategory = "Sky")
        public int sky = 0;

        /** The selected sky's id; trusted over {@link #sky} on load so reordering SkyType never moves anyone. */
        @Include
        public String skyId = "";

        @Button(title = "Browse Skies", text = "Open", description = "See live previews of every sky and click one to use it. Also: /dynamicskies browse",
                category = "Sky", subcategory = "Sky")
        public void browseSkies() {
            var mc = Minecraft.getInstance();
            mc.execute(() -> Compat.setScreen(new SkyBrowser()));
        }

        @Dropdown(
                title = "Sky Mode",
                description = "Replace: the dynamic sky covers the normal one. Overlay: it's drawn on top of the vanilla or "
                        + "resource pack sky, with its dark parts see-through. Auto: overlay for the dark skies (Aurora Night, "
                        + "Nebula, Deep Space, ...), replace for the rest.",
                options = {"Replace", "Overlay", "Auto"},
                category = "Sky", subcategory = "Sky"
        )
        public int skyMode = 0;

        @Dropdown(title = "Overlay Blend", description = "Add is bolder; Screen is softer and never blows out to white.",
                options = {"Add", "Screen"}, category = "Sky", subcategory = "Sky")
        public int overlayBlend = 1;

        @Slider(title = "Overlay Black Cutoff", description = "How dark a part of the sky must be to become see-through in overlay mode.",
                min = 0f, max = 0.2f, step = 0.005f, category = "Sky", subcategory = "Sky")
        public float overlayCutoff = 0.04f;

        @Slider(title = "Crossfade Time (seconds)", description = "How long switching between skies takes. 0 = instant.",
                min = 0f, max = 10f, step = 0.5f, category = "Sky", subcategory = "Switching")
        public float crossfadeSeconds = 2f;

        @Switch(title = "Cycle Skies", description = "Automatically move to the next sky every few minutes.", category = "Sky", subcategory = "Switching")
        public boolean cycleSkies = false;

        @Slider(title = "Cycle Every (minutes)", min = 0.5f, max = 30f, step = 0.5f, category = "Sky", subcategory = "Switching")
        public float cycleMinutes = 5f;

        @Switch(title = "Only Cycle Favorites", description = "Cycle Skies and Change Sky On Drop only pick skies you've starred in the sky browser.",
                category = "Sky", subcategory = "Switching")
        public boolean cycleFavorites = false;

        @Switch(title = "Night Only", description = "Only show the dynamic sky at night. The normal sky fades back in during the day.", category = "Sky", subcategory = "World")
        public boolean nightOnly = false;

        @Switch(title = "Match Fog", description = "Blend the horizon into the world's fog color so distant terrain doesn't cut off sharply.", category = "Sky", subcategory = "World")
        public boolean matchFog = true;

        @Slider(title = "Fog Strength", min = 0f, max = 1f, step = 0.05f, category = "Sky", subcategory = "World")
        public float fogStrength = 0.7f;

        @Slider(title = "Opacity", description = "Below 100% the normal sky shows through.", min = 0.05f, max = 1f, step = 0.05f, category = "Sky", subcategory = "World")
        public float opacity = 1f;

        @Switch(title = "Hide Clouds", description = "Hide vanilla clouds while the dynamic sky is showing.", category = "Sky", subcategory = "World")
        public boolean hideClouds = true;

        @Switch(title = "Hide Sun and Moon", description = "Hide the vanilla sun and moon while Dynamic Skies is enabled.",
                category = "Sky", subcategory = "World")
        public boolean hideSunMoon = false;

        @Switch(title = "Fade Out In Rain", description = "Let the normal grey sky show through while it's raining.", category = "Sky", subcategory = "World")
        public boolean fadeInRain = true;

        @Switch(title = "Replace End Sky", category = "Sky", subcategory = "World")
        public boolean replaceEnd = false;

        @Switch(title = "Sky Lights the World",
                description = "Tint outdoor lighting with the sky's colour, so a purple sky gives a purple glow. "
                        + "Torch-lit areas keep their own colour. Doesn't work with shader packs.",
                category = "Sky", subcategory = "Lighting")
        public boolean skyLighting = false;

        @Slider(title = "Lighting Strength", min = 0.05f, max = 1f, step = 0.05f, category = "Sky", subcategory = "Lighting")
        public float skyLightingStrength = 0.6f;

        @Dropdown(
                title = "Render Quality",
                description = "Draws the sky at a lower resolution and smoothly scales it up. The soft skies look almost the same "
                        + "at Medium and cost about a quarter as much; sharp ones like Code Rain get a little blurry.",
                options = {"Full", "High (75%)", "Medium (50%)", "Low (33%)"},
                category = "Sky", subcategory = "Performance"
        )
        public int renderQuality = 1;

        // ---- Look --------------------------------------------------------------

        @Slider(title = "Animation Speed", min = 0f, max = 3f, step = 0.05f, category = "Look", subcategory = "Motion")
        public float speed = 1f;

        @Slider(title = "Brightness", min = 0.1f, max = 2f, step = 0.05f, category = "Look", subcategory = "Colors")
        public float brightness = 1f;

        @Slider(title = "Saturation", min = 0f, max = 2f, step = 0.05f, category = "Look", subcategory = "Colors")
        public float saturation = 1f;

        @Slider(title = "Hue Shift", min = 0f, max = 360f, step = 1f, category = "Look", subcategory = "Colors")
        public float hueShift = 0f;

        @Color(title = "Tint", alpha = false, category = "Look", subcategory = "Colors")
        public PolyColor tint = new PolyColor(0xFFFFFFFF);

        // ---- Layers ------------------------------------------------------------

        @Slider(title = "Extra Stars", description = "Twinkling stars on top of any sky. 0 = off.", min = 0f, max = 1f, step = 0.05f, category = "Layers", subcategory = "Stars")
        public float layerStars = 0f;

        @Switch(title = "Shooting Stars", category = "Layers", subcategory = "Stars")
        public boolean shootingStars = false;

        @Slider(title = "Shooting Stars Per Minute", min = 0f, max = 30f, step = 1f, category = "Layers", subcategory = "Stars")
        public float shootingRate = 4f;

        @Switch(title = "Shooting Stars On Beats", description = "Also fire a shooting star on strong beats.", category = "Layers", subcategory = "Stars")
        public boolean shootingOnBeat = true;

        @Slider(title = "Aurora", description = "Aurora curtains on top of any sky. 0 = off.", min = 0f, max = 1f, step = 0.05f, category = "Layers", subcategory = "Atmosphere")
        public float layerAurora = 0f;

        @Slider(title = "Cloud Wisps", description = "Thin drifting cloud streaks. 0 = off.", min = 0f, max = 1f, step = 0.05f, category = "Layers", subcategory = "Atmosphere")
        public float layerWisps = 0f;

        // ---- Audio -------------------------------------------------------------

        @Dropdown(
                title = "Audio Source",
                description = "System Audio reacts to whatever Windows is playing (Spotify, YouTube, Discord...) with no setup. "
                        + "Input Device listens to a recording device such as Stereo Mix, Voicemeeter or a microphone. "
                        + "Fake Beat pulses at a set BPM without any audio.",
                options = {"Off", "Input Device", "Fake Beat", "System Audio"},
                category = "Audio", subcategory = "Source"
        )
        public int audioSource = 0;

        @Text(title = "Audio Device", description = "Part of the device name to listen to. Leave empty to pick a loopback device automatically "
                + "(Stereo Mix, CABLE Output, etc). Use \"List Audio Devices\" to see names.",
                placeholder = "auto", category = "Audio", subcategory = "Source")
        public String audioDevice = "";

        @Slider(title = "Fake Beat BPM", min = 60f, max = 200f, step = 1f, category = "Audio", subcategory = "Source")
        public float fakeBpm = 120f;

        @Slider(title = "Reactivity", description = "How strongly the sky reacts to sound.", min = 0f, max = 2f, step = 0.05f, category = "Audio", subcategory = "Reaction")
        public float reactivity = 1f;

        @Slider(title = "Sensitivity", description = "Raise this if the sky barely reacts to quiet music.", min = 0.25f, max = 4f, step = 0.05f, category = "Audio", subcategory = "Source")
        public float sensitivity = 1f;

        @Slider(title = "Smoothing", description = "Higher = calmer, slower falloff.", min = 0f, max = 1f, step = 0.05f, category = "Audio", subcategory = "Reaction")
        public float smoothing = 0.5f;

        @Slider(title = "Beat Flash", description = "Brightness pulse on each beat.", min = 0f, max = 1f, step = 0.05f, category = "Audio", subcategory = "Reaction")
        public float beatFlash = 0.35f;

        @Slider(title = "Bass Speeds Up Animation", min = 0f, max = 2f, step = 0.05f, category = "Audio", subcategory = "Reaction")
        public float bassSpeed = 0.6f;

        @Slider(title = "Audio Hue Shift", description = "Shift colors with the mids.", min = 0f, max = 1f, step = 0.05f, category = "Audio", subcategory = "Reaction")
        public float audioHue = 0f;

        @Button(title = "List Audio Devices", text = "List", description = "Print the available recording devices in chat.", category = "Audio", subcategory = "Source")
        public void listDevices() {
            for (String line : AudioManager.describeDevices()) chat(line);
            chatLink("Need help? Click to open the audio setup guide.", AUDIO_GUIDE_URL);
        }

        @Button(title = "Audio Setup Guide", text = "Open", description = "Opens the guide for setting up Voicemeeter or VB-CABLE in your browser.", category = "Audio", subcategory = "Source")
        public void openAudioGuide() {
            Compat.openUri(AUDIO_GUIDE_URL);
        }

        @Button(title = "Restart Audio", text = "Restart", category = "Audio", subcategory = "Source")
        public void restartAudio() {
            AudioManager.restart();
        }

        // ---- Build-ups & drops -------------------------------------------------

        @Switch(title = "Detect Build-Ups & Drops", description = "Spot when the bass cuts out for a build-up and slams back in for the drop.", category = "Drops", subcategory = "Detection")
        public boolean dropDetection = true;

        @Slider(title = "Drop Sensitivity", description = "Higher catches more drops, but may fire on smaller changes.", min = 0.5f, max = 2f, step = 0.05f, category = "Drops", subcategory = "Detection")
        public float dropSensitivity = 1f;

        @Slider(title = "Build-Up Effect", description = "During a build-up the sky speeds up, brightens and glows at the horizon.", min = 0f, max = 1f, step = 0.05f, category = "Drops", subcategory = "Detection")
        public float buildUpStrength = 0.7f;

        @Switch(title = "Flash On Drop", category = "Drops", subcategory = "On Drop")
        public boolean dropFlash = true;

        @Switch(title = "Shockwave On Drop", description = "A ring of light sweeps up from the horizon.", category = "Drops", subcategory = "On Drop")
        public boolean dropShockwave = true;

        @Switch(title = "Color Shift On Drop", description = "Shift the sky's colors to a new hue on every drop.", category = "Drops", subcategory = "On Drop")
        public boolean dropColorShift = false;

        @Switch(title = "Shooting Stars On Drop", category = "Drops", subcategory = "On Drop")
        public boolean dropMeteors = true;

        @Switch(title = "Change Sky On Drop", description = "Jump to the next sky on every drop.", category = "Drops", subcategory = "On Drop")
        public boolean dropChangeSky = false;

        // ---- Custom shader -----------------------------------------------------

        @Text(title = "Shader File", description = "A .fsh file in .minecraft/config/dynamicskies/skies/. Pick \"Custom Shader\" as the sky to use it. "
                + "See example.fsh there for what you can use.", placeholder = "example.fsh", category = "Custom", subcategory = "Custom Shader")
        public String customShader = "example.fsh";

        @Switch(title = "Live Reload", description = "Recompile the shader as soon as you save the file.", category = "Custom", subcategory = "Custom Shader")
        public boolean liveReload = true;

        @Button(title = "Reload Shader", text = "Reload", category = "Custom", subcategory = "Custom Shader")
        public void reloadShader() {
            ModernSkyRenderer.INSTANCE.requestShaderReload();
        }

        @Button(title = "Open Shader Folder", text = "Open", category = "Custom", subcategory = "Custom Shader")
        public void openShaderFolder() {
            openFolder(ModernSkyRenderer.shaderFolder());
        }

        // ---- Custom image ------------------------------------------------------

        @Text(title = "Image File", description = "An equirectangular (2:1) PNG/JPG in .minecraft/config/dynamicskies/.",
                placeholder = "sky.png", category = "Custom", subcategory = "Custom Image")
        public String customImage = "sky.png";

        @Slider(title = "Image Rotation Speed", min = -1f, max = 1f, step = 0.01f, category = "Custom", subcategory = "Custom Image")
        public float imageRotation = 0.02f;

        @Button(title = "Reload Image", text = "Reload", category = "Custom", subcategory = "Custom Image")
        public void reloadImage() {
            ModernSkyRenderer.INSTANCE.requestImageReload();
        }

        @Button(title = "Open Image Folder", text = "Open", category = "Custom", subcategory = "Custom Image")
        public void openImageFolder() {
            openFolder(ModernSkyRenderer.imageFolder());
        }

        private Settings() {
            super("dynamicskies-modern.json", "Dynamic Skies", Category.VISUALS);

            hideIf("cycleMinutes", () -> !cycleSkies);
            hideIf("overlayBlend", () -> skyMode == 0);
            hideIf("overlayCutoff", () -> skyMode == 0);
            hideIf("fogStrength", () -> !matchFog);
            hideIf("skyLightingStrength", () -> !skyLighting);
            hideIf("audioDevice", () -> audioSource != 1);
            hideIf("fakeBpm", () -> audioSource != 2);
            for (String opt : new String[]{"reactivity", "smoothing", "beatFlash", "bassSpeed", "audioHue"}) {
                hideIf(opt, () -> audioSource == 0);
            }
            hideIf("shootingRate", () -> !shootingStars);
            hideIf("shootingOnBeat", () -> !shootingStars || audioSource == 0);
            hideIf("dropDetection", () -> audioSource == 0);
            for (String opt : new String[]{"dropSensitivity", "buildUpStrength", "dropFlash", "dropShockwave",
                    "dropColorShift", "dropMeteors", "dropChangeSky"}) {
                hideIf(opt, () -> audioSource == 0 || !dropDetection);
            }
            hideIf("sensitivity", () -> audioSource != 1 && audioSource != 3);
            hideIf("listDevices", () -> audioSource != 1 && audioSource != 3);
            hideIf("restartAudio", () -> audioSource != 1 && audioSource != 3);

            // Keep skyId following the dropdown, however the sky gets changed.
            this.<Object>addCallback("sky", value -> {
                if (value instanceof Number index) skyId = SkyType.byIndex(index.intValue()).id;
                return false;
            });
        }

        @Override
        protected Tree makeTree() {
            Tree tree = super.makeTree();
            if (tree != null) tree.getProp("sky").addMetadata("options", SkyType.displayNames());
            return tree;
        }

        @Override
        protected void initialize(boolean byConfigManager) {
            super.initialize(byConfigManager);
            if (getTree() == null) return;
            // Point the dropdown at the saved id; configs without one only have the position.
            SkyType saved = SkyType.byId(skyId);
            if (saved != null) {
                if (saved.ordinal() != sky) getProperty("sky").setAs(saved.ordinal());
            } else {
                skyId = SkyType.byIndex(sky).id;
            }
        }

        public boolean shouldHideSunMoon() {
            return enabled && hideSunMoon;
        }

        /** Changes the selected sky and saves it, keeping the settings menu in sync. */
        public void selectSky(SkyType type) {
            getProperty("sky").setAs(type.ordinal());
            save();
        }

        /** Moves to the next sky without saving, for Cycle Skies and Change Sky On Drop. */
        public void nextSky() {
            SkyType[] skies = SkyType.values();
            boolean favoritesOnly = cycleFavorites && !Favorites.isEmpty();
            int start = SkyType.byIndex(sky).ordinal();
            for (int step = 1; step <= skies.length; step++) {
                SkyType next = skies[(start + step) % skies.length];
                if (favoritesOnly ? Favorites.isFavorite(next) : !next.skipInCycle()) {
                    getProperty("sky").setAs(next.ordinal());
                    return;
                }
            }
        }
    }
}
