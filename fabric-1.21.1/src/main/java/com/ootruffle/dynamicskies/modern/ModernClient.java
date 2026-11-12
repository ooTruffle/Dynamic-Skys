package com.ootruffle.dynamicskies.modern;

import net.fabricmc.api.ClientModInitializer;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.config.v1.annotations.Text;

public final class ModernClient implements ClientModInitializer {
    public static final Settings SETTINGS = new Settings();

    @Override
    public void onInitializeClient() {
        SETTINGS.preload();
    }

    public static final class Settings extends Config {
        @Switch(title = "Enable Dynamic Skies")
        public boolean enabled = true;

        @Text(title = "Sky ID", description = "Built-in shader ID, for example aurora_night or nebula.")
        public String skyId = "aurora_night";

        @Slider(title = "Brightness", min = 0, max = 2)
        public float brightness = 1f;

        @Slider(title = "Saturation", min = 0, max = 2)
        public float saturation = 1f;

        private Settings() {
            super("dynamicskies-modern.json", "Dynamic Skies", Category.VISUALS);
        }
    }
}
