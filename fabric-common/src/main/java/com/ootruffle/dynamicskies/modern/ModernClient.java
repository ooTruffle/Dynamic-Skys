package com.ootruffle.dynamicskies.modern;

import com.ootruffle.dynamicskies.sky.SkyType;
import com.ootruffle.dynamicskies.sky.Favorites;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import org.polyfrost.oneconfig.api.commands.v1.CommandManager;
import org.polyfrost.oneconfig.utils.v1.dsl.ScreensKt;
import com.ootruffle.dynamicskies.audio.AudioManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.polyfrost.oneconfig.api.config.v1.annotations.Button;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.Include;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;
import net.fabricmc.api.ClientModInitializer;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public final class ModernClient implements ClientModInitializer {
    public static final Settings SETTINGS = new Settings();

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

    private static void chat(String message) {
        Minecraft.getInstance().gui.getChat().addMessage(Component.literal("[Dynamic Skies] " + message));
    }

    public static final class Settings extends Config {
        @Switch(title = "Enable Dynamic Skies")
        public boolean enabled = true;

        @Include
        public String skyId = "aurora_night";

        @Slider(title = "Brightness", min = 0, max = 2)
        public float brightness = 1f;

        @Slider(title = "Saturation", min = 0, max = 2)
        public float saturation = 1f;

        @Dropdown(title = "Sky", options = {})
        public int sky;

        @Override
        protected Tree makeTree() {
            Tree tree = super.makeTree();
            if (tree != null) tree.getProp("sky").addMetadata("options", SkyType.displayNames());
            return tree;
        }

        @Override
        protected void initialize(boolean byConfigManager) {
            super.initialize(byConfigManager);
            var saved = SkyType.byId(skyId);
            if (getTree() != null && saved != null) getProperty("sky").setAs(saved.ordinal());
        }

        @Slider(title = "Animation Speed", min = 0f, max = 3f, step = 0.05f, category = "Look", subcategory = "Motion")
        public float speed = 1f;

        @Slider(title = "Hue Shift", min = 0f, max = 360f, step = 1f, category = "Look", subcategory = "Colors")
        public float hueShift = 0f;

        @Slider(title = "Extra Stars", description = "Twinkling stars on top of any sky. 0 = off.", min = 0f, max = 1f, step = 0.05f, category = "Layers", subcategory = "Stars")
        public float layerStars = 0f;

        @Slider(title = "Aurora", description = "Aurora curtains on top of any sky. 0 = off.", min = 0f, max = 1f, step = 0.05f, category = "Layers", subcategory = "Atmosphere")
        public float layerAurora = 0f;

        @Slider(title = "Cloud Wisps", description = "Thin drifting cloud streaks. 0 = off.", min = 0f, max = 1f, step = 0.05f, category = "Layers", subcategory = "Atmosphere")
        public float layerWisps = 0f;

        @Switch(title = "Shooting Stars", category = "Layers", subcategory = "Stars")
        public boolean shootingStars = false;

        @Slider(title = "Shooting Stars Per Minute", min = 0f, max = 30f, step = 1f, category = "Layers", subcategory = "Stars")
        public float shootingRate = 4f;

        @Text(title = "Shader File", description = "A .fsh in config/dynamicskies/skies/", category = "Custom")
        public String customShader = "example.fsh";

        @Text(title = "Image File", description = "A panorama PNG or JPG in config/dynamicskies/", category = "Custom")
        public String customImage = "sky.png";

        @Slider(title = "Image Rotation", min = -2, max = 2, category = "Custom")
        public float imageRotation;

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
            for (String line : AudioManager.describeDevices()) {
                chat(line);
            }

        }

        @Button(title = "Audio Setup Guide", text = "Open", description = "Opens the guide for setting up Voicemeeter or VB-CABLE in your browser.", category = "Audio", subcategory = "Source")
        public void openAudioGuide() {
            net.minecraft.Util.getPlatform().openUri("https://github.com/ooTruffle/Dynamic-Skys/blob/master/docs/AUDIO_SETUP.md");
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

        @Switch(title = "Shooting Stars On Beats", category = "Layers", subcategory = "Stars")
        public boolean shootingOnBeat = true;

        @Button(title = "Browse Skies", text = "Browse", category = "Sky")
        public void browseSkies() {
            var mc = Minecraft.getInstance();
            mc.execute(() -> mc.setScreen(new SkyBrowser(mc.screen)));
        }

        @Switch(title = "Cycle Skies", category = "Sky")
        public boolean cycleSkies;

        @Slider(title = "Cycle Every (minutes)", min = 0.5f, max = 30f, step = 0.5f, category = "Sky")
        public float cycleMinutes = 5f;

        @Switch(title = "Only Cycle Favorites", category = "Sky")
        public boolean cycleFavorites;

        @Switch(title = "Change Sky On Drop", category = "Drops")
        public boolean dropChangeSky;

        public void selectSky(SkyType type) {
            getProperty("sky").setAs(type.ordinal());
            save();
        }

        public void nextSky() {
            SkyType[] skies = SkyType.values();
            boolean favoritesOnly = cycleFavorites && !Favorites.isEmpty();
            for (int step = 1; step <= skies.length; step++) {
                SkyType next = skies[(SkyType.byIndex(sky).ordinal() + step) % skies.length];
                if (favoritesOnly ? Favorites.isFavorite(next) : !next.skipInCycle()) {
                    getProperty("sky").setAs(next.ordinal());
                    return;
                }
            }
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

        @Switch(title = "Night Only", description = "Only show the dynamic sky at night. The normal sky fades back in during the day.", category = "Sky", subcategory = "World")
        public boolean nightOnly = false;

        @Switch(title = "Match Fog", description = "Blend the horizon into the world's fog color so distant terrain doesn't cut off sharply.", category = "Sky", subcategory = "World")
        public boolean matchFog = true;

        @Slider(title = "Fog Strength", min = 0f, max = 1f, step = 0.05f, category = "Sky", subcategory = "World")
        public float fogStrength = 0.7f;

        @Slider(title = "Opacity", description = "Below 100% the normal sky shows through.", min = 0.05f, max = 1f, step = 0.05f, category = "Sky", subcategory = "World")
        public float opacity = 1f;

        @Switch(title = "Fade Out In Rain", description = "Let the normal grey sky show through while it's raining.", category = "Sky", subcategory = "World")
        public boolean fadeInRain = true;

        @Switch(title = "Replace End Sky", category = "Sky", subcategory = "World")
        public boolean replaceEnd = false;

        @Switch(title = "Hide Clouds", category = "Sky", subcategory = "World")
        public boolean hideClouds = true;

        @Color(title = "Tint", alpha = false, category = "Look", subcategory = "Colors")
        public PolyColor tint = new PolyColor(0xFFFFFFFF);

        private Settings() {
            super("dynamicskies-modern.json", "Dynamic Skies", Category.VISUALS);
            this.<Object>addCallback("sky", value -> {
                if (value instanceof Number index) skyId = SkyType.byIndex(index.intValue()).id;
                return false;
            });
        }
    }
}
