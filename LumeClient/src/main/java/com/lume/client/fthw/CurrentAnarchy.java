package com.lume.client.fthw;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which FunTime anarchy number we're currently on, read straight off the
 * vanilla sidebar scoreboard (no server support needed) — user-confirmed
 * that's where the anarchy number is actually shown in-game. Regex is a
 * first guess ("Анарх..." followed by a number) — tune once we see real
 * sidebar text if it doesn't match.
 */
public final class CurrentAnarchy {
    private CurrentAnarchy() {}

    private static final Pattern PATTERN = Pattern.compile("Анарх\\w*\\D*(\\d+)", Pattern.CASE_INSENSITIVE);

    private static String cached = null;
    private static long lastCheck = 0;

    public static String get() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 1500) return cached;
        lastCheck = now;
        String fresh = detect();
        if (!java.util.Objects.equals(fresh, cached)) {
            System.out.println("[Lume][CurrentAnarchy] " + cached + " -> " + fresh);
        }
        cached = fresh;
        return cached;
    }

    private static String detect() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return null;
        Scoreboard sb = mc.player.getScoreboard();
        ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        if (obj == null) return null;
        for (ScoreboardEntry e : sb.getScoreboardEntries(obj)) {
            String line = e.name().getString();
            Matcher m = PATTERN.matcher(line);
            if (m.find()) return m.group(1);
        }
        return null;
    }
}
