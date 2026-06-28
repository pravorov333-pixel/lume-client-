package com.lume.client.module.modules.qol;

import com.lume.client.module.Category;
import com.lume.client.module.Module;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Chat Timestamps — prefixes every chat line with a grey [HH:mm] so you can tell
 * when a message arrived. Pure cosmetic, doesn't touch what's sent.
 *
 * <p>The chat hook lives in {@link com.lume.client.LumeClient}; it calls
 * {@link #stamp(Text)} for every incoming (non-overlay) message when on.
 */
public class ChatTimestamps extends Module {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    public ChatTimestamps() {
        super("Chat Timestamps", "Prefix chat with [HH:mm]", Category.CHAT, -1);
    }

    /** Returns the message with a grey time prefix prepended. */
    public Text stamp(Text message) {
        MutableText prefix = Text.literal("[" + LocalTime.now().format(FMT) + "] ")
                .formatted(Formatting.GRAY);
        return prefix.append(message);
    }
}
