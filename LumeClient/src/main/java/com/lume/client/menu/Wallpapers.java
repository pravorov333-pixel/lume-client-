package com.lume.client.menu;

import com.lume.client.nanovg.NanoVgRenderer;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom main-menu wallpapers: the user drops images into {@code .lumeclient/wallpapers}
 * and picks one from the title screen's Background setting.
 *
 * <p>The folder is the SHARED {@code .lumeclient} root (next to {@code theme.json}), not the
 * per-version profile dir — resolved the same way {@link com.lume.client.gui.ThemeSync} finds
 * theme.json, so 1.21.4 and 1.16.5 see the same wallpapers and the launcher can reach them too.
 *
 * <p>Decoding goes through NanoVG (stb_image) rather than MC's NativeImage: we're already
 * drawing the menu in a NanoVG frame, and stb accepts jpg/bmp/gif, which NativeImage does not.
 */
public final class Wallpapers {

    private Wallpapers() {}

    private static final List<String> EXTS = List.of(".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif");
    private static final String README_NAME = "КАК-ДОБАВИТЬ-ОБОИ.txt";

    /** Kept byte-identical to the launcher's copy in {@code LumeLauncher/src/main.js} — whichever
     *  side runs first creates it, so both must describe the same rules. */
    private static final String README = String.join("\r\n",
            "КАК ДОБАВИТЬ СВОИ ОБОИ В LUME",
            "===================================",
            "",
            "Просто положи картинку в ЭТУ папку, затем в игре открой главное меню →",
            "иконка шестерёнки (справа сверху) → строка «Background» → разверни её",
            "и выбери свою картинку. Кнопка «Открыть папку» там же.",
            "",
            "ЧТОБЫ ОБОИ НЕ БАГАЛИСЬ, СОБЛЮДАЙ ЭТО:",
            "",
            "1. ФОРМАТ: только .png, .jpg, .jpeg, .bmp или .gif.",
            "   Другие форматы (.psd, .tiff, .heic, .mp4 и т.п.) НЕ увидятся.",
            "   .webp может не открыться — если картинка не появилась, пересохрани в .png.",
            "",
            "2. РАЗРЕШЕНИЕ: бери под свой монитор — например 1920x1080 или 2560x1440.",
            "   Картинка вписывается по режиму «cover»: она НЕ растянется, но лишнее",
            "   обрежется по краям. Меньше 1280x720 — будет мыло.",
            "",
            "3. ВЕС ФАЙЛА: держи до ~10 МБ. Очень тяжёлые картинки (30+ МБ) грузятся",
            "   долго и могут подвесить игру в момент выбора.",
            "",
            "4. ИМЯ ФАЙЛА: лучше латиницей без спецсимволов (например my-wallpaper.png).",
            "   Эмодзи в имени лучше не использовать.",
            "",
            "5. АНИМАЦИЯ: .gif показывается ТОЛЬКО первым кадром — он не анимируется.",
            "",
            "6. ЧИТАЕМОСТЬ: если поверх ярких обоев плохо видно текст — там же в",
            "   настройках Background крути ползунок «Dim» (затемнение).",
            "",
            "Совет: чтобы вернуть анимированный фон — выбери стиль «Sparkles».",
            "");

    /** {@code .lumeclient/wallpapers}. Falls back to the game dir if the layout is unexpected. */
    public static Path dir() {
        Path gameDir = FabricLoader.getInstance().getGameDir();              // .../.lumeclient/profiles/<version>
        Path root = gameDir.getParent() != null ? gameDir.getParent().getParent() : null;   // .../.lumeclient
        return (root != null ? root : gameDir).resolve("wallpapers");
    }

    /** Creates the folder and seeds the how-to README once. Safe to call repeatedly. */
    public static Path ensureDir() {
        Path d = dir();
        try {
            Files.createDirectories(d);
            Path readme = d.resolve(README_NAME);
            if (!Files.exists(readme)) Files.writeString(readme, README, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println("[Lume] wallpapers dir failed: " + e);
        }
        return d;
    }

    public static void openFolder() {
        net.minecraft.util.Util.getOperatingSystem().open(ensureDir().toFile());
    }

    /** Image filenames in the folder, sorted. Never null. */
    public static List<String> list() {
        List<String> out = new ArrayList<>();
        try (var s = Files.list(ensureDir())) {
            s.filter(Files::isRegularFile)
             .map(p -> p.getFileName().toString())
             .filter(Wallpapers::isImage)
             .sorted(String.CASE_INSENSITIVE_ORDER)
             .forEach(out::add);
        } catch (IOException e) {
            // folder unreadable — behave as "no wallpapers", the menu falls back to Sparkles
        }
        return out;
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return EXTS.stream().anyMatch(lower::endsWith);
    }

    // ---- cached GPU image ------------------------------------------------------------
    // One decoded image at a time; swapping selection frees the old handle so flipping
    // through a folder of 4K wallpapers can't leak GL textures.

    private static int handle = -1;
    private static String loadedName = null;
    private static String failedName = null;   // don't retry a broken file every single frame

    /** Forces the next draw to re-read from disk — call when the selection changes. */
    public static void invalidate() {
        loadedName = null;
        failedName = null;
    }

    /**
     * Paints the selected wallpaper over the whole screen.
     *
     * @return false when there's nothing to paint (no selection, missing or undecodable file),
     *         so the caller can fall back to an animated style instead of a blank screen.
     */
    public static boolean draw(long vg, float w, float h, float dim) {
        String name = com.lume.client.module.modules.cosmetic.CustomMenu.wallpaper();
        if (name == null || name.isBlank()) return false;
        if (name.equals(failedName)) return false;

        if (!name.equals(loadedName)) {
            NanoVgRenderer.deleteImage(vg, handle);
            handle = -1;
            // basename only: the setting is round-tripped through lume.json, so treat it as
            // untrusted input rather than letting a stray path escape the folder.
            Path f = ensureDir().resolve(Path.of(name).getFileName().toString());
            try {
                if (Files.isRegularFile(f)) handle = NanoVgRenderer.createImage(vg, Files.readAllBytes(f));
            } catch (IOException | RuntimeException e) {
                System.out.println("[Lume] wallpaper read failed (" + name + "): " + e);
            }
            if (handle == -1) { failedName = name; return false; }
            loadedName = name;
        }

        NanoVgRenderer.imageCover(vg, 0, 0, w, h, handle, 1f);
        if (dim > 0.01f) NanoVgRenderer.roundedRect(vg, 0, 0, w, h, 0, ((int) (dim * 255) << 24));
        return true;
    }
}
