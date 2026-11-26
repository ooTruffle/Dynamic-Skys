package com.ootruffle.dynamicskies;

import com.ootruffle.dynamicskies.audio.AudioManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.ootruffle.dynamicskies.config.DynamicSkiesConfig;
import com.ootruffle.dynamicskies.gui.SkyBrowser;
import net.ornithemc.osl.entrypoints.api.ModInitializer;
import org.polyfrost.oneconfig.api.commands.v1.CommandManager;
import org.polyfrost.oneconfig.utils.v1.dsl.ScreensKt;

public class DynamicSkiesEntrypoint implements ModInitializer {

    @Override
    public void init() {
        var settings = DynamicSkiesConfig.INSTANCE;
        settings.preload();
        AudioManager.configure(() -> new AudioManager.Options(settings.enabled ? settings.audioSource : 0,
                settings.audioDevice, settings.sensitivity, settings.smoothing, settings.fakeBpm,
                settings.dropDetection, settings.dropSensitivity), message -> {
            DynamicSkiesConfig.chat("\u00a7cAudio: " + message);
            DynamicSkiesConfig.guideLink();
        });

        // "/dynamicskies" opens the settings, "/dynamicskies browse" the sky browser.
        CommandManager.INSTANCE.register(
                withBrowse(ScreensKt.addDefaultCommand(DynamicSkiesConfig.INSTANCE, "dynamicskies"))
        );

        Log.info("Initialized. Run /dynamicskies to open settings, /dynamicskies browse for the sky browser.");
    }

    private static <S> LiteralArgumentBuilder<S> withBrowse(LiteralArgumentBuilder<S> root) {
        return root.then(LiteralArgumentBuilder.<S>literal("browse").executes(ctx -> {
            SkyBrowser.open();
            return 1;
        }));
    }
}
