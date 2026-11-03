package com.ootruffle.dynamicskies.sky;

/**
 * The one list of skies. The "Sky" dropdown in the settings is filled from it
 * (see DynamicSkiesConfig.makeTree), and the sky browser shows it.
 *
 * The chosen sky is saved by its id (DynamicSkiesConfig.skyId), so the list can
 * be reordered. Never change an id: it's the saved setting, the favorites entry
 * and the shader file name in assets/dynamicskies/shaders/skies/. Older configs
 * only have the position and are converted on first load, so a reorder in the
 * same release as that conversion would move those users to another sky.
 */
public enum SkyType {
    AURORA_NIGHT("aurora_night", "Aurora Night",
            "Green and violet aurora curtains rippling over a starry night."),
    NEBULA("nebula", "Nebula",
            "Swirling pink and blue gas clouds drifting through deep space."),
    SYNTHWAVE("synthwave", "Synthwave",
            "A striped retro sun over neon mountains and a scrolling grid."),
    BLACK_HOLE("black_hole", "Black Hole",
            "A black hole bending starlight, ringed by a glowing disk."),
    CODE_RAIN("code_rain", "Code Rain",
            "Columns of green glyphs pouring down all around you."),
    PLASMA_VEINS("plasma_veins", "Plasma Veins",
            "Electric violet veins crackling through a dark haze."),
    KALEIDOSCOPE("kaleidoscope", "Kaleidoscope",
            "Mirrored neon patterns turning slowly overhead."),
    GOLDEN_HOUR("golden_hour", "Golden Hour",
            "A warm low sun and soft clouds at the end of the day."),
    BLOOD_MOON("blood_moon", "Blood Moon",
            "A huge cratered red moon hanging in a crimson night."),
    ECLIPSE("eclipse", "Eclipse",
            "A black disk wreathed in a streaming white-gold corona."),
    STORM_FRONT("storm_front", "Storm Front",
            "Heavy racing clouds and lightning that strikes on the beat."),
    VOID_RIFT("void_rift", "Void Rift",
            "A glowing tear across the sky, churning with energy."),
    MOLTEN_MARBLE("molten_marble", "Molten Marble",
            "Slow liquid stone threaded with glowing gold veins."),
    SILK("silk", "Silk",
            "Soft shimmering folds of pastel fabric."),
    CHROMA_SKY("chroma_sky", "Chroma Sky",
            "Rolling rainbow waves of shifting color."),
    FROZEN_TIDE("frozen_tide", "Frozen Tide",
            "Icy crystal cells with cold light washing across them."),
    LIQUID_VORTEX("liquid_vortex", "Liquid Vortex",
            "A colorful whirlpool twisting overhead."),
    CIRCUIT_MAZE("circuit_maze", "Circuit Maze",
            "Glowing circuit traces with pulses racing along them."),
    SEA_OF_CLOUDS("sea_of_clouds", "Sea of Clouds",
            "Floating above an endless ocean of clouds at dusk."),
    CHERRY_BLOSSOM("cherry_blossom", "Cherry Blossom",
            "A pastel spring sky with pink petals drifting down."),
    SWIRLING_NIGHT("swirling_night", "Swirling Night",
            "Painterly swirling strokes and big glowing stars."),
    DEEP_SPACE("deep_space", "Deep Space",
            "A dense starfield with a dusty galaxy arching overhead."),
    VOID("void", "Void",
            "Almost pure black, with a faint glow that breathes with the bass."),
    SPECTRUM("spectrum", "Spectrum",
            "A music visualizer: bars around the horizon, a ring above."),
    CUSTOM_IMAGE("custom_image", "Custom Image",
            "Your own panorama image wrapped around the sky."),
    CUSTOM_SHADER("custom_shader", "Custom Shader",
            "Your own GLSL sky from config/dynamicskies/skies."),
    NONE("none", "None (Layers Only)",
            "No sky of its own; just the layers and drop effects."),
    MEGASTRUCTURE("megastructure", "Megastructure",
            "A colossal ring structure arching past a gas giant."),
    THE_WATCHER("the_watcher", "The Watcher",
            "A giant eye that blinks, glances around and watches."),
    NEON_LOTUS("neon_lotus", "Neon Lotus",
            "A neon lotus blooming overhead in glowing petals."),
    AMETHYST_CELLS("amethyst_cells", "Amethyst Cells",
            "A dome of violet crystal cells that pulse and sparkle."),
    FIREFALL("firefall", "Firefall",
            "Streaks of fire raining down while embers rise."),
    ACRYLIC_POUR("acrylic_pour", "Acrylic Pour",
            "Slow fluid-art swirls of paint with white lacing."),
    ANIME_SKY("anime_sky", "Anime Sky",
            "A vivid summer sky with big crisp cumulus clouds.");

    public final String id;
    public final String displayName;
    /** One line shown under the big preview in the sky browser. */
    public final String description;

    SkyType(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public boolean usesImage() {
        return this == CUSTOM_IMAGE;
    }

    /** Skies left out of "Cycle Skies". */
    public boolean skipInCycle() {
        return this == CUSTOM_IMAGE || this == CUSTOM_SHADER || this == VOID || this == NONE;
    }

    /** Skies that are mostly dark, so they look right drawn on top of another sky ("Auto" mode). */
    public boolean overlayFriendly() {
        switch (this) {
            case AURORA_NIGHT:
            case NEBULA:
            case BLACK_HOLE:
            case CODE_RAIN:
            case PLASMA_VEINS:
            case KALEIDOSCOPE:
            case ECLIPSE:
            case VOID_RIFT:
            case CIRCUIT_MAZE:
            case DEEP_SPACE:
            case SPECTRUM:
            case NONE:
            case MEGASTRUCTURE:
            case NEON_LOTUS:
                return true;
            default:
                return false;
        }
    }

    public static String[] displayNames() {
        SkyType[] all = values();
        String[] names = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            names[i] = all[i].displayName;
        }
        return names;
    }

    /** The sky with this id, or null if there isn't one. */
    public static SkyType byId(String id) {
        if (id == null) return null;
        for (SkyType t : values()) {
            if (t.id.equals(id.trim())) return t;
        }
        return null;
    }

    public static SkyType byIndex(int index) {
        SkyType[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, index))];
    }
}
