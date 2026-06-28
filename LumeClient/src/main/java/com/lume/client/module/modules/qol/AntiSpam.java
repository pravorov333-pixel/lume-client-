package com.lume.client.module.modules.qol;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Anti-Spam — hides chat messages that are exact duplicates of one seen in the
 * last second. Kills the "player spammed the same line 10x" wall on busy servers.
 *
 * <p>The chat hook lives in {@link com.lume.client.LumeClient}; it calls
 * {@link #shouldBlock(Text)} for every incoming message when this module is on.
 */
public class AntiSpam extends Module {

    /** A message identical to one within this window (ms) is treated as spam. */
    private static final long WINDOW_MS = 1000L;
    private static final int HISTORY = 6;

    private static final Deque<Entry> recent = new ArrayDeque<>();

    private record Entry(String text, long time) {}

    public AntiSpam() {
        super("Anti-Spam", "Hide duplicate chat messages", Category.CHAT, -1);
    }

    @Override
    public void onDisable() {
        recent.clear();
    }

    /** True if this message duplicates a recent one and should be dropped. */
    public boolean shouldBlock(Text message) {
        String s = message.getString();
        long now = System.currentTimeMillis();

        // drop expired entries
        recent.removeIf(e -> now - e.time() > WINDOW_MS);

        for (Entry e : recent) {
            if (e.text().equals(s)) return true;
        }

        recent.addLast(new Entry(s, now));
        while (recent.size() > HISTORY) recent.removeFirst();
        return false;
    }
}
