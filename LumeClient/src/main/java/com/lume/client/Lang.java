package com.lume.client;

import com.lume.client.module.Module;
import com.lume.client.module.modules.misc.Language;

import java.util.HashMap;
import java.util.Map;

/**
 * Tiny EN→RU dictionary for Lume's own HUD labels (potion / mob / block names
 * come localized from Minecraft, so they aren't here). Module names are never
 * translated. Controlled by the "Language" module.
 */
public final class Lang {

    private static final Map<String, String> RU = new HashMap<>();
    private static final Map<String, String> CAT = new HashMap<>();
    private static final Map<String, String> NAME = new HashMap<>();

    static {
        RU.put("Day", "День");
        RU.put("Ping", "Пинг");
        RU.put("Speed", "Скорость");
        RU.put("b/s", "б/с");
        RU.put("ms", "мс");

        CAT.put("Visuals", "Визуалы");
        CAT.put("Render", "Рендер");
        CAT.put("Performance", "Произв.");
        CAT.put("Chat & QoL", "Чат и QoL");
        CAT.put("Cosmetics", "Косметика");
        CAT.put("Settings", "Настройки");
        CAT.put("Binds", "Бинды");
        CAT.put("Server", "Сервер");

        // Only functions with a clean Russian translation. Inherently-English ones
        // (HUD, Target HUD, Block Info, CPS) are intentionally omitted → stay English.
        NAME.put("Coords", "Координаты");
        NAME.put("Keystrokes", "Клавиши");
        NAME.put("Potion HUD", "Зелья");
        NAME.put("Armor HUD", "Броня");
        NAME.put("Inventory HUD", "Инвентарь");
        NAME.put("Totem Counter", "Тотемы");
        NAME.put("Ping", "Пинг");
        NAME.put("Day Counter", "День");
        NAME.put("Speed", "Скорость");
        NAME.put("Clock", "Часы");
        NAME.put("Module List", "Список модулей");
        NAME.put("FullBright", "Яркость");
        NAME.put("Zoom", "Зум");
        NAME.put("Reduced Particles", "Меньше частиц");
        NAME.put("AutoSprint", "Авто-спринт");
        NAME.put("Auto Reconnect", "Авто-реконнект");
        NAME.put("Anti-Spam", "Анти-спам");
        NAME.put("Chat Timestamps", "Время в чате");
        NAME.put("Waypoints", "Метки");
        NAME.put("Server Helper", "Сервер-хелпер");
        NAME.put("Custom Crosshair", "Прицел");
        NAME.put("Menu Logo", "Лого меню");
        NAME.put("Game Font", "Шрифт игры");
        NAME.put("Custom Menu", "Своё меню");
        NAME.put("Block Outline", "Обводка блока");
        NAME.put("Clean View", "Чистый вид");
        NAME.put("HUD Scale", "Размер HUD");
        NAME.put("Language", "Язык");
    }

    private Lang() {}

    public static boolean ru() {
        Module m = LumeClient.MODULES.getByName("Language");
        return m instanceof Language l && l.isRu();
    }

    /** Translate one of Lume's own labels (returns the input unchanged in EN). */
    public static String t(String en) {
        return ru() ? RU.getOrDefault(en, en) : en;
    }

    /** Category/tab title (translated only if a clean RU exists). */
    public static String tCat(String en) {
        return ru() ? CAT.getOrDefault(en, en) : en;
    }

    /** Module/function name — translated only where a clean RU exists, else kept English. */
    public static String tName(String en) {
        return ru() ? NAME.getOrDefault(en, en) : en;
    }
}
