package com.lume.client.module.modules.qol;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chat Filters — hides incoming chat lines that contain a blocked keyword
 * (case-insensitive substring match). Keywords are edited via the
 * {@code .filter} chat command (add/del/list), mirroring {@code .macro}/{@code .wp}.
 *
 * <p>The chat hook lives in {@link com.lume.client.LumeClient}; it calls
 * {@link #shouldBlock(Text)} for every incoming message when this module is on.
 */
public class ChatFilters extends Module {

    public static final List<String> keywords = new ArrayList<>();

    public ChatFilters() {
        super("Chat Filters", "Hide chat lines containing blocked keywords", Category.CHAT, -1);
    }

    public static void add(String word) {
        String w = word.trim().toLowerCase(Locale.ROOT);
        if (!w.isEmpty() && !keywords.contains(w)) keywords.add(w);
    }

    public static boolean remove(String word) {
        return keywords.remove(word.trim().toLowerCase(Locale.ROOT));
    }

    public boolean shouldBlock(Text message) {
        if (keywords.isEmpty()) return false;
        String s = message.getString().toLowerCase(Locale.ROOT);
        for (String w : keywords) if (s.contains(w)) return true;
        return false;
    }
}
