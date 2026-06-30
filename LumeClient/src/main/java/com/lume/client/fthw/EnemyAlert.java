package com.lume.client.fthw;

import com.lume.client.LumeClient;
import com.lume.client.gui.Notifications;
import com.lume.client.module.modules.fthw.ServerHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.HashSet;
import java.util.Set;

/**
 * "Enemy used an item on you" alert — legit: it only reads YOUR OWN status
 * effects. When a new harmful effect appears (the kind FunTime items inflict —
 * disorientation = nausea/blindness, chronosphere = slowness, …) it fires a HUD
 * toast naming the likely item and how long it lasts. No enemy ESP / inventory.
 */
public final class EnemyAlert {

    private static final Set<String> active = new HashSet<>();

    private EnemyAlert() {}

    /** Call every client tick. */
    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerHelper sh = (ServerHelper) LumeClient.MODULES.getByName("Server Helper");
        if (sh == null || !sh.isEnabled() || !sh.enemyAlert.value
                || ServerType.current() == ServerType.UNKNOWN || mc.player == null) {
            active.clear();
            return;
        }
        Set<String> now = new HashSet<>();
        for (StatusEffectInstance e : mc.player.getStatusEffects()) {
            StatusEffect type = e.getEffectType().value();
            if (type.getCategory() != StatusEffectCategory.HARMFUL) continue;
            String name = type.getName().getString();
            now.add(name);
            if (!active.contains(name)) {
                String item = mapItem(type.getTranslationKey());
                int sec = e.getDuration() / 20;
                String label = item != null ? item : name;
                Notifications.push("⚠ Тебя задело: " + label + " — " + sec + "с", 0xFFE06666, 3500);
            }
        }
        active.clear();
        active.addAll(now);
    }

    /** Map a vanilla effect to the FunTime item that usually inflicts it. */
    private static String mapItem(String translationKey) {
        String k = translationKey.toLowerCase();
        if (k.contains("nausea") || k.contains("confusion")) return "Дезориентация";
        if (k.contains("blindness")) return "Дезориентация (слепота)";
        if (k.contains("slowness")) return "Хроносфера (замедление)";
        if (k.contains("weakness")) return "ослабление";
        if (k.contains("wither")) return "иссушение";
        if (k.contains("poison")) return "яд";
        return null;
    }
}
