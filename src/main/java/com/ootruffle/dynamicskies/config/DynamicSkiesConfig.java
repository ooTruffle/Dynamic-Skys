package com.ootruffle.dynamicskies.config;

import com.ootruffle.dynamicskies.Log;
import com.ootruffle.dynamicskies.audio.AudioManager;
import com.ootruffle.dynamicskies.gui.SkyBrowserScreen;
import com.ootruffle.dynamicskies.sky.SkyRenderer;
import com.ootruffle.dynamicskies.sky.SkyType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import org.polyfrost.compose.render.PolyColor;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.Property;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.annotations.Button;
import org.polyfrost.oneconfig.api.config.v1.annotations.Color;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;

import java.io.File;

/**
 * All settings, opened with /dynamicskies. preload() must be called once at init.
 *
 * The sky dropdown's options must stay in the same order as {@link SkyType}.
 */
public class DynamicSkiesConfig extends Config {


    // ---- Sky ---------------------------------------------------------------

    @Switch(title = "Enable Dynamic Skies", category = "Sky", subcategory = "Sky")
    public boolean enabled = true;

    @Dropdown(
            title = "Sky",
            description = "Which animated sky to show.",
            // Filled in from SkyType when the config loads; see makeTree().
            options = {},
            category = "Sky", subcategory = "Sky"
    )
    public int sky = 0;

    @Button(title = "Browse Skies", text = "Open", description = "See live previews of every sky and click one to use it. Also: /dynamicskies browse",
            category = "Sky", subcategory = "Sky")
    public void browseSkies() {
        SkyBrowserScreen.open();
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

    @Switch(title = "Show Sun & Moon", description = "Draw the normal sun and moon on top of the dynamic sky.", category = "Sky", subcategory = "World")
    public boolean showSunMoon = false;

    @Switch(title = "Fade Out In Rain", description = "Let the normal grey sky show through while it's raining.", category = "Sky", subcategory = "World")
    public boolean fadeInRain = true;

    @Switch(title = "Replace End Sky", category = "Sky", subcategory = "World")
    public boolean replaceEnd = false;

    @Dropdown(
            title = "Render Quality",
            description = "Draws the sky at a lower resolution and smoothly scales it up. The soft skies look almost the same "
                    + "at Medium and cost about a quarter as much; sharp ones like Code Rain get a little blurry.",
            options = {"Full", "High (75%)", "Medium (50%)", "Low (33%)"},
            category = "Sky", subcategory = "Performance"
    )
    public int renderQuality = 0;

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

    // ---- Layers (drawn on top of any sky) -----------------------------------

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
        for (String line : AudioManager.describeDevices()) {
            chat(line);
        }
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
        SkyRenderer.INSTANCE.requestShaderReload();
    }

    @Button(title = "Open Shader Folder", text = "Open", category = "Custom", subcategory = "Custom Shader")
    public void openShaderFolder() {
        openFolder(shaderFolder());
    }

    // ---- Custom image ------------------------------------------------------

    @Text(title = "Image File", description = "An equirectangular (2:1) PNG/JPG in .minecraft/config/dynamicskies/.",
            placeholder = "sky.png", category = "Custom", subcategory = "Custom Image")
    public String imageFile = "sky.png";

    @Slider(title = "Image Rotation Speed", min = -1f, max = 1f, step = 0.01f, category = "Custom", subcategory = "Custom Image")
    public float imageRotation = 0.02f;

    @Button(title = "Reload Image", text = "Reload", category = "Custom", subcategory = "Custom Image")
    public void reloadImage() {
        SkyRenderer.INSTANCE.requestImageReload();
    }

    @Button(title = "Open Image Folder", text = "Open", category = "Custom", subcategory = "Custom Image")
    public void openImageFolder() {
        openFolder(imageFolder());
    }

    private static void openFolder(File dir) {
        // The game runs headless as far as AWT is concerned, so java.awt.Desktop can't be used.
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String opener = os.contains("win") ? "explorer" : os.contains("mac") ? "open" : "xdg-open";
        try {
            new ProcessBuilder(opener, dir.getAbsolutePath()).start();
        } catch (Throwable t) {
            chat("Image folder: " + dir.getAbsolutePath());
        }
    }

    public static final DynamicSkiesConfig INSTANCE = new DynamicSkiesConfig();

    private DynamicSkiesConfig() {
        super("dynamicskies.json", "Dynamic Skies", Category.VISUALS);

        hideIf("cycleMinutes", () -> !cycleSkies);
        hideIf("overlayBlend", () -> skyMode == 0);
        hideIf("overlayCutoff", () -> skyMode == 0);
        hideIf("fogStrength", () -> !matchFog);
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
    }

    public static File imageFolder() {
        File dir = new File(Minecraft.getMinecraft().mcDataDir, "config/dynamicskies");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    /**
     * Fills the Sky dropdown from SkyType, so the list of skies only lives in one
     * place. This runs right after the settings are read and before saved values
     * are loaded, so the saved sky is never checked against an empty list.
     */
    @Override
    protected Tree makeTree() {
        Tree tree = super.makeTree();
        if (tree != null) {
            Property<?> sky = tree.getProp("sky");
            if (sky != null) {
                sky.addMetadata("options", SkyType.displayNames());
            } else {
                Log.error("Couldn't find the Sky setting to fill its options");
            }
        }
        return tree;
    }

    public static File shaderFolder() {
        File dir = new File(imageFolder(), "skies");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return dir;
    }

    /** Changes the selected sky and saves it, keeping the settings menu in sync. */
    public void setSky(int index) {
        getProperty("sky").setAs(index);
        save();
    }

    public static void chat(String msg) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer != null) {
            mc.thePlayer.addChatMessage(new ChatComponentText("§b[Dynamic Skies]§r " + msg));
        } else {
            Log.info(msg);
        }
    }
}
