package com.lume.client.menu;

import com.lume.client.Config;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Ultra Performance's Account Manager screen — plain vanilla widgets, same data/actions
 * (saved offline nicknames, pick the "preferred" one, add/remove) as LumeTitleMenu's account panel.
 */
public class FlatAccountManagerScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget nameField;

    public FlatAccountManagerScreen(Screen parent) {
        super(Text.literal("Account Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = 30;
        for (String name : new java.util.ArrayList<>(Config.savedAccounts)) {
            boolean preferred = name.equals(Config.preferredAccount);
            addDrawableChild(ButtonWidget.builder(Text.literal((preferred ? "✓ " : "") + name), b -> {
                Config.preferredAccount = name;
                Config.save();
                client.setScreen(new FlatAccountManagerScreen(parent));
            }).dimensions(width / 2 - 150, y, 220, 20).build()).active = !preferred;
            addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
                Config.savedAccounts.remove(name);
                if (name.equals(Config.preferredAccount)) Config.preferredAccount = null;
                Config.save();
                client.setScreen(new FlatAccountManagerScreen(parent));
            }).dimensions(width / 2 + 74, y, 20, 20).build());
            y += 24;
        }
        if (Config.savedAccounts.isEmpty()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("No saved accounts yet"), b -> {}).dimensions(width / 2 - 100, y, 200, 20).build()).active = false;
            y += 24;
        }

        y += 8;
        nameField = new TextFieldWidget(textRenderer, width / 2 - 100, y, 140, 20, Text.literal(""));
        nameField.setMaxLength(16);
        nameField.setSuggestion("Nickname");
        addDrawableChild(nameField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Add"), b -> {
            String n = nameField.getText().trim();
            if (!n.isEmpty() && !Config.savedAccounts.contains(n)) {
                Config.savedAccounts.add(n);
                Config.preferredAccount = n;
                Config.save();
                client.setScreen(new FlatAccountManagerScreen(parent));
            }
        }).dimensions(width / 2 + 44, y, 106, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(parent)).dimensions(width / 2 - 40, height - 30, 80, 20).build());
    }

    @Override
    public boolean shouldPause() { return false; }
}
