package com.lume.client.fx;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Drop-in custom PNG particle textures — same "put a file in a folder" idea as
 * {@link com.lume.client.audio.CustomAudioPlayer}, but for a billboard sprite instead
 * of a sound. One folder per module; a bundled example.png is copied in on first use
 * so the folder is never empty and the format is obvious.
 */
public final class ParticleTexture {

    private ParticleTexture() {}

    private static final Map<String, Identifier> CACHE = new HashMap<>();

    /** Root folder for one module's drop-in particle textures. Created eagerly. */
    public static Path folder(String moduleFolder) {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("lume/custom_particles/" + moduleFolder);
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir;
    }

    /** Creates the folder (if missing), drops a short instructions file and an example.png
     *  copied from the mod's own bundled resource, so users have a working sample to replace. */
    public static void ensureReadme(String moduleFolder) {
        Path dir = folder(moduleFolder);
        Path readme = dir.resolve("Положи сюда свою .png текстуру.txt");
        if (!Files.exists(readme)) {
            try {
                Files.writeString(readme,
                        "Положи сюда .png текстуру(ы) частицы — они появятся в разделе \"My Particles\".\n" +
                        "example.png уже лежит рядом как образец (мягкое круглое пятно) — замени его или добавь свои файлы.\n" +
                        "Формат: PNG, лучше маленький и квадратный (32x32 - 128x128), с прозрачным фоном.\n");
            } catch (Exception ignored) {}
        }
        Path example = dir.resolve("example.png");
        if (!Files.exists(example)) {
            try (InputStream in = ParticleTexture.class.getResourceAsStream("/assets/lume/textures/particle/example.png")) {
                if (in != null) Files.copy(in, example);
            } catch (Exception ignored) {}
        }
    }

    /** All .png files in the given module's custom-particle folder ("My Particles" picker), name-sorted. */
    public static File[] list(String moduleFolder) {
        File dir = folder(moduleFolder).toFile();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (files == null) return new File[0];
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        return files;
    }

    /** Loads (and caches) the given file as a runtime texture, returns its Identifier, or null on failure. */
    public static Identifier get(File file) {
        if (file == null || !file.isFile()) return null;
        String key = file.getAbsolutePath();
        Identifier cached = CACHE.get(key);
        if (cached != null) return cached;
        try (InputStream in = Files.newInputStream(file.toPath())) {
            NativeImage img = NativeImage.read(in);
            NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
            Identifier id = Identifier.of("lume", "custom_particle/" + Integer.toHexString(key.hashCode()));
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
            CACHE.put(key, id);
            return id;
        } catch (Exception e) {
            System.out.println("[Lume] custom particle texture load failed: " + e);
            return null;
        }
    }
}
