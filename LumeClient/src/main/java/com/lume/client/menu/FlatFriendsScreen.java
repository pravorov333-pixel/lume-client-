package com.lume.client.menu;

import com.lume.client.social.Friends;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Ultra Performance's Friends screen — plain vanilla widgets (no NanoVG/blur), same data and
 * actions as {@link com.lume.client.gui.FriendsScreen} (online status, add/accept/decline,
 * connect/tpa) just without the glass rendering. No scrolling — lists are simply stacked and may
 * run off-screen for a very long friend list, an accepted simplification for this lightweight mode.
 */
public class FlatFriendsScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget addField;

    public FlatFriendsScreen(Screen parent) {
        super(Text.literal("Friends"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = 30;
        addField = new TextFieldWidget(textRenderer, width / 2 - 100, y, 160, 20, Text.literal(""));
        addField.setMaxLength(24);
        addDrawableChild(addField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Add friend"), b -> {
            String n = addField.getText().trim();
            if (!n.isEmpty()) { Friends.addFriend(n); addField.setText(""); }
        }).dimensions(width / 2 + 64, y, 80, 20).build());
        y += 28;

        for (String name : Friends.incoming) {
            addDrawableChild(ButtonWidget.builder(Text.literal(name + " — incoming request"), b -> {}).dimensions(width / 2 - 150, y, 180, 20).build()).active = false;
            addDrawableChild(ButtonWidget.builder(Text.literal("Accept"), b -> Friends.acceptFriend(name)).dimensions(width / 2 + 34, y, 60, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Decline"), b -> Friends.declineFriend(name)).dimensions(width / 2 + 98, y, 60, 20).build());
            y += 24;
        }
        for (String name : Friends.outgoing) {
            addDrawableChild(ButtonWidget.builder(Text.literal(name + " — request sent"), b -> {}).dimensions(width / 2 - 150, y, 300, 20).build()).active = false;
            y += 24;
        }
        if (!Friends.incoming.isEmpty() || !Friends.outgoing.isEmpty()) y += 8;

        for (String name : Friends.friendList) {
            Friends.Status st = Friends.statusOf(name);
            boolean online = st != null && st.online;
            String label = name + (online ? "  (online" + (st.server != null && !st.server.isEmpty() ? " — " + st.server : "") + ")" : "  (offline)");
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {}).dimensions(width / 2 - 150, y, 180, 20).build()).active = false;
            if (online && st.server != null && !st.server.isEmpty()) {
                addDrawableChild(ButtonWidget.builder(Text.literal("Connect"), b -> Friends.connectTo(st.server)).dimensions(width / 2 + 34, y, 60, 20).build());
            }
            addDrawableChild(ButtonWidget.builder(Text.literal("TPA"), b -> Friends.tpaTo(name)).dimensions(width / 2 + 98, y, 40, 20).build()).active = online;
            addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> { Friends.removeFriend(name); client.setScreen(new FlatFriendsScreen(parent)); }).dimensions(width / 2 + 142, y, 20, 20).build());
            y += 24;
        }
        if (Friends.friendList.isEmpty() && Friends.incoming.isEmpty() && Friends.outgoing.isEmpty()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("No friends yet — add one above"), b -> {}).dimensions(width / 2 - 100, y, 200, 20).build()).active = false;
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(parent)).dimensions(width / 2 - 40, height - 30, 80, 20).build());
    }

    @Override
    public boolean shouldPause() { return false; }
}
