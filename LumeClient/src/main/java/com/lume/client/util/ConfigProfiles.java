package com.lume.client.util;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** Manages named configuration profiles saved as JSON files. */
public class ConfigProfiles {

    private static final Path DIR = Paths.get(
            System.getProperty("user.home"), ".lumeclient", "profiles");

    public static String activeProfile = "default";

    public static List<String> list() {
        List<String> names = new ArrayList<>();
        try {
            if (!Files.exists(DIR)) Files.createDirectories(DIR);
            try (var s = Files.list(DIR)) {
                s.filter(p -> p.toString().endsWith(".json"))
                 .map(p -> p.getFileName().toString().replace(".json", ""))
                 .sorted()
                 .forEach(names::add);
            }
        } catch (Exception ignored) {}
        if (names.isEmpty()) names.add("default");
        return names;
    }

    public static void save(String name, String json) {
        try {
            if (!Files.exists(DIR)) Files.createDirectories(DIR);
            Files.writeString(DIR.resolve(name + ".json"), json);
        } catch (Exception e) {
            System.out.println("[Lume] ConfigProfiles save error: " + e.getMessage());
        }
    }

    public static String load(String name) {
        try {
            Path p = DIR.resolve(name + ".json");
            if (Files.exists(p)) return Files.readString(p);
        } catch (Exception e) {
            System.out.println("[Lume] ConfigProfiles load error: " + e.getMessage());
        }
        return null;
    }

    public static void delete(String name) {
        try {
            Files.deleteIfExists(DIR.resolve(name + ".json"));
        } catch (Exception ignored) {}
    }

    public static void saveActive() {
        save(activeProfile, com.lume.client.Config.toJson());
        System.out.println("[Lume] Saved profile: " + activeProfile);
    }

    public static boolean loadActive() {
        String json = load(activeProfile);
        if (json == null) return false;
        com.lume.client.Config.fromJson(json);
        System.out.println("[Lume] Loaded profile: " + activeProfile);
        return true;
    }
}
