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

    /**
     * Throttled, cached "which drop-in texture is selected" resolver for a module (WorldParticles,
     * HitParticles, ...). A real crash was traced to resolving + decoding the PNG straight from the
     * hot path — {@code HitEffects.onAttack} called this on every hit, {@code WorldParticles.onTick}
     * every tick — a disk read + native texture decode at the worst possible moment (mid-combat).
     * {@link #get()} is cheap (no I/O, just returns whatever's cached) and is what the hot path
     * should call; {@link #refresh()} does the actual (throttled to ~1/sec) file-list scan + decode
     * and belongs in {@code onEnable()}/{@code onTick()} instead, so loading happens shortly after a
     * settings change, not in the middle of an attack.
     */
    public static final class Slot {
        private final String folder;
        private final java.util.function.BooleanSupplier enabled;
        private final java.util.function.Supplier<String> selectedFile;
        private Identifier cached;
        private boolean lastEnabled = false;
        private String lastFile = null;
        private long lastCheck = 0;

        public Slot(String folder, java.util.function.BooleanSupplier enabled, java.util.function.Supplier<String> selectedFile) {
            this.folder = folder;
            this.enabled = enabled;
            this.selectedFile = selectedFile;
        }

        /** Cheap — call from the hot path (per-tick, per-hit). No I/O. */
        public Identifier get() {
            return enabled.getAsBoolean() ? cached : null;
        }

        /** Re-checks (throttled) whether the toggle/selection changed and (re)loads if so.
         *  Call from onEnable()/onTick() — never from the hot per-hit/per-particle path. */
        public void refresh() {
            long now = System.currentTimeMillis();
            if (now - lastCheck < 1000) return;
            lastCheck = now;
            boolean on = enabled.getAsBoolean();
            String file = selectedFile.get();
            if (!on) { cached = null; lastEnabled = false; lastFile = null; return; }
            if (cached != null && lastEnabled && java.util.Objects.equals(lastFile, file)) return;
            File[] files = list(folder);
            if (files.length == 0) return;
            File chosen = files[0];
            if (file != null) {
                for (File f : files) if (f.getName().equals(file)) { chosen = f; break; }
            }
            cached = ParticleTexture.get(chosen);
            lastEnabled = true;
            lastFile = file;
        }
    }
}
