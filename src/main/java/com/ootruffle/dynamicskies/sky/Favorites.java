package com.ootruffle.dynamicskies.sky;

import com.ootruffle.dynamicskies.Log;
import com.ootruffle.dynamicskies.config.DynamicSkiesConfig;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Starred skies, saved one id per line in config/dynamicskies/favorites.txt. */
public final class Favorites {

    private static Set<SkyType> favorites;

    private Favorites() {
    }

    private static File file() {
        return new File(DynamicSkiesConfig.imageFolder(), "favorites.txt");
    }

    private static Set<SkyType> load() {
        if (favorites != null) return favorites;
        favorites = EnumSet.noneOf(SkyType.class);
        File f = file();
        if (f.isFile()) {
            try {
                for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                    for (SkyType t : SkyType.values()) {
                        if (t.id.equals(line.trim())) favorites.add(t);
                    }
                }
            } catch (Exception e) {
                Log.warn("Couldn't read favorites: " + e.getMessage());
            }
        }
        return favorites;
    }

    public static boolean isFavorite(SkyType type) {
        return load().contains(type);
    }

    public static void toggle(SkyType type) {
        Set<SkyType> set = load();
        if (!set.remove(type)) set.add(type);
        List<String> lines = new ArrayList<>();
        for (SkyType t : set) lines.add(t.id);
        try {
            Files.write(file().toPath(), lines, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.warn("Couldn't save favorites: " + e.getMessage());
        }
    }

    public static boolean isEmpty() {
        return load().isEmpty();
    }
}
