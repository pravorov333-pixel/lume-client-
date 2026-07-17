package com.lume.client.module.modules.qol;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/**
 * Better Chat — Ctrl+Click a chat line (while the chat window is open) to
 * copy its full text to the clipboard. The actual click handling lives in
 * {@code mixin.ChatHudMixin}; this is just the on/off switch it checks.
 */
public class BetterChat extends Module {

    public BetterChat() {
        super("Better Chat", "Ctrl+Click a chat line to copy it", Category.CHAT, -1);
    }
}
