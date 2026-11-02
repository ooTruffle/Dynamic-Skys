package com.ootruffle.dynamicskies;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.ootruffle.dynamicskies.config.DynamicSkiesConfig;
import com.ootruffle.dynamicskies.gui.SkyBrowserScreen;
import net.ornithemc.osl.entrypoints.api.ModInitializer;
import org.polyfrost.oneconfig.api.commands.v1.CommandManager;
import org.polyfrost.oneconfig.utils.v1.dsl.ScreensKt;

public class DynamicSkiesEntrypoint implements ModInitializer {

    @Override
    public void init() {
        DynamicSkiesConfig.INSTANCE.preload();

        // "/dynamicskies" opens the settings, "/dynamicskies browse" the sky browser.
        CommandManager.INSTANCE.register(
                withBrowse(ScreensKt.addDefaultCommand(DynamicSkiesConfig.INSTANCE, "dynamicskies"))
        );

        Log.info("Initialized. Run /dynamicskies to open settings, /dynamicskies browse for the sky browser.");
    }

    private static <S> LiteralArgumentBuilder<S> withBrowse(LiteralArgumentBuilder<S> root) {
        return root.then(LiteralArgumentBuilder.<S>literal("browse").executes(ctx -> {
            SkyBrowserScreen.open();
            return 1;
        }));
    }
}
