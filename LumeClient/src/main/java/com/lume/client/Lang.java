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
        // HUD labels
        RU.put("Day", "День");
        RU.put("Ping", "Пинг");
        RU.put("Speed", "Скорость");
        RU.put("b/s", "б/с");
        RU.put("ms", "мс");
        // Setting names (the buttons that open inside a module)
        RU.put("Color", "Цвет");
        RU.put("Style", "Стиль");
        RU.put("Opacity", "Прозрачность");
        RU.put("Filter", "Фильтр");
        RU.put("Thickness", "Толщина");
        RU.put("Size", "Размер");
        RU.put("Sound", "Звук");
        RU.put("Volume", "Громкость");
        RU.put("Pitch", "Тон");
        RU.put("Only on Crit", "Только крит");
        RU.put("Fill", "Заливка");
        RU.put("Fill Opacity", "Прозрачность заливки");
        RU.put("Rate", "Частота");
        RU.put("Gravity", "Гравитация");
        RU.put("Lifetime", "Время жизни");
        RU.put("Turbulence", "Турбулентность");
        RU.put("Radius", "Радиус");
        RU.put("Type", "Тип");
        RU.put("Count", "Количество");
        RU.put("Intensity", "Интенсивность");
        RU.put("Angle", "Угол");
        RU.put("Preset", "Пресет");
        RU.put("Ratio", "Соотношение");
        RU.put("Time", "Время");
        RU.put("No Rain", "Без дождя");
        RU.put("No Storm", "Без грозы");
        RU.put("Scale", "Масштаб");
        RU.put("Mode", "Режим");
        RU.put("Range", "Дальность");
        RU.put("Outline", "Обводка");
        RU.put("Center dot", "Точка");
        RU.put("Gap", "Отступ");
        RU.put("Duration ms", "Длительность мс");
        RU.put("RAM Bar HUD", "RAM в HUD");
        RU.put("Simple", "Простой");
        RU.put("Accent", "Акцент");
        RU.put("Pos X", "Позиция X");
        RU.put("Pos Y", "Позиция Y");
        RU.put("Pos Z", "Позиция Z");
        RU.put("Rot X", "Поворот X");
        RU.put("Rot Y", "Поворот Y");
        RU.put("Rot Z", "Поворот Z");
        // ClickGUI strings (keys are English, values are Russian)
        RU.put("Search modules…", "Поиск модулей…");
        RU.put("No results", "Ничего не найдено");
        RU.put("press a key…", "нажми клавишу…");
        RU.put("Enabled", "Включено");
        RU.put("Disabled", "Выключено");
        RU.put("Menu", "Меню");
        RU.put("Events", "Ивенты");
        RU.put("Config", "Конфиг");
        RU.put("Friends", "Друзья");
        // Sub-screen strings
        RU.put("Configs", "Конфиги");
        RU.put("Config profiles — click to load", "Профили конфигурации — нажми для загрузки");
        RU.put("Save", "Сохранить");
        RU.put("New Profile", "Новый профиль");
        RU.put("Import config by code:", "Импорт конфига по коду:");
        RU.put("profile code…", "код профиля…");
        RU.put("Apply", "Применить");
        RU.put("active", "✓ активен");
        RU.put("Friends", "Друзья");
        RU.put("Online status coming soon — add names for now:", "Онлайн-статус появится позже — пока можно добавить имена:");
        RU.put("offline", "не в сети");
        RU.put("Add to list:", "Добавить в список:");
        RU.put("player name…", "ник игрока…");
        RU.put("Add", "Добавить");
        RU.put("List is empty — add a player name below", "Список пуст — добавь ник игрока ниже");

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

    /**
     * Module/function name — always returns English. tName kept for compatibility
     * but callers in the GUI now use m.getName() directly.
     */
    public static String tName(String en) { return en; }

    /**
     * UI string — returns Russian translation in RU mode.
     * Key is always the English string.
     */
    public static String tUI(String en) {
        return ru() ? RU.getOrDefault(en, en) : en;
    }
}
