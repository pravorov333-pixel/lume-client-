package com.lume.client.menu;

import com.lume.client.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;

/**
 * Ultra Performance's Fast Connect screen — plain vanilla widgets, same data/action (saved
 * server shortcuts, one click connects) as the glass version inside LumeTitleMenu.
 */
public class FlatFastConnectScreen extends Screen {

    private final Screen parent;
    private TextFieldWidget nameField, addrField;

    public FlatFastConnectScreen(Screen parent) {
        super(Text.literal("Fast Connect"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = 30;
        for (FastConnect.Entry e : new java.util.ArrayList<>(FastConnect.list)) {
            addDrawableChild(ButtonWidget.builder(Text.literal(e.name + "  (" + e.address + ")"), b -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                ServerInfo info = new ServerInfo(e.name, e.address, ServerInfo.ServerType.OTHER);
                ConnectScreen.connect(this, mc, ServerAddress.parse(e.address), info, false, null);
            }).dimensions(width / 2 - 150, y, 220, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
                FastConnect.remove(e);
                Config.save();
                client.setScreen(new FlatFastConnectScreen(parent));
            }).dimensions(width / 2 + 74, y, 20, 20).build());
            y += 24;
        }
        if (FastConnect.list.isEmpty()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("No saved servers yet"), b -> {}).dimensions(width / 2 - 100, y, 200, 20).build()).active = false;
            y += 24;
        }

        y += 8;
        nameField = new TextFieldWidget(textRenderer, width / 2 - 150, y, 100, 20, Text.literal(""));
        nameField.setMaxLength(24);
        nameField.setSuggestion("Name");
        addDrawableChild(nameField);
        addrField = new TextFieldWidget(textRenderer, width / 2 - 44, y, 118, 20, Text.literal(""));
        addrField.setMaxLength(64);
        addrField.setSuggestion("host:port");
        addDrawableChild(addrField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> {
            FastConnect.add(nameField.getText(), addrField.getText());
            Config.save();
            client.setScreen(new FlatFastConnectScreen(parent));
        }).dimensions(width / 2 + 78, y, 72, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(parent)).dimensions(width / 2 - 40, height - 30, 80, 20).build());
    }

    @Override
    public boolean shouldPause() { return false; }
}
