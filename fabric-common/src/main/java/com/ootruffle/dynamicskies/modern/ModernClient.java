package com.ootruffle.dynamicskies.modern;

import com.ootruffle.dynamicskies.sky.SkyType;
import org.polyfrost.oneconfig.api.config.v1.Tree;
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown;
import org.polyfrost.oneconfig.api.config.v1.annotations.Include;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;
import net.fabricmc.api.ClientModInitializer;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;

public final class ModernClient implements ClientModInitializer {
    public static final Settings SETTINGS = new Settings();

    @Override
    public void onInitializeClient() {
        SETTINGS.preload();
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

        private Settings() {
            super("dynamicskies-modern.json", "Dynamic Skies", Category.VISUALS);
            this.<Object>addCallback("sky", value -> {
                if (value instanceof Number index) skyId = SkyType.byIndex(index.intValue()).id;
                return false;
            });
        }
    }
}
