package com.lume.client.gui;

import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;
import com.lume.client.module.setting.ColorSetting;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.Setting;
import com.lume.client.module.setting.SliderSetting;
import com.lume.client.module.setting.StringSetting;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Ultra Performance's per-module settings editor — one plain vanilla widget per {@link Setting},
 *  same values/behaviour as the NanoVG ClickGUI's setting rows, just flat. */
public class FlatModuleSettingsScreen extends Screen {

    private final Screen parent;
    private final Module module;

    public FlatModuleSettingsScreen(Screen parent, Module module) {
        super(Text.literal(module.getName() + " — Settings"));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        int x = width / 2 - 150, y = 30, w = 300, h = 20, gap = 26;
        for (Setting s : module.getSettings()) {
            if (s.hidden) continue;
            if (s instanceof BoolSetting b) {
                addDrawableChild(ButtonWidget.builder(Text.literal(s.name + ": " + (b.value ? "On" : "Off")), btn -> {
                    b.value = !b.value;
                    client.setScreen(new FlatModuleSettingsScreen(parent, module));
                }).dimensions(x, y, w, h).build());
            } else if (s instanceof SliderSetting sl) {
                addDrawableChild(new SliderWidget(x, y, w, h, Text.literal(s.name + ": " + sl.display()), sl.fraction()) {
                    @Override protected void updateMessage() { setMessage(Text.literal(s.name + ": " + sl.display())); }
                    @Override protected void applyValue() { sl.setFraction(this.value); }
                });
            } else if (s instanceof ModeSetting md) {
                addDrawableChild(ButtonWidget.builder(Text.literal(s.name + ": " + md.get()), btn -> {
                    md.cycle(1);
                    client.setScreen(new FlatModuleSettingsScreen(parent, module));
                }).dimensions(x, y, w, h).build());
            } else if (s instanceof StringSetting ts) {
                TextFieldWidget field = new TextFieldWidget(textRenderer, x + 110, y, w - 110, h, Text.literal(s.name));
                field.setMaxLength(64);
                field.setText(ts.value);
                field.setChangedListener(v -> ts.value = v);
                addDrawableChild(field);
                y += h;
                continue;
            } else if (s instanceof ColorSetting c) {
                addDrawableChild(ButtonWidget.builder(Text.literal(s.name + " — Accent: " + (c.accent ? "On" : "Off")), btn -> {
                    c.accent = !c.accent;
                    client.setScreen(new FlatModuleSettingsScreen(parent, module));
                }).dimensions(x, y, 150, h).build());
                TextFieldWidget hex = new TextFieldWidget(textRenderer, x + 156, y, w - 156, h, Text.literal("hex"));
                hex.setMaxLength(6);
                hex.setText(String.format("%02X%02X%02X", c.r, c.g, c.b));
                hex.setChangedListener(v -> {
                    if (v.length() == 6 && v.matches("[0-9a-fA-F]{6}")) {
                        c.r = Integer.parseInt(v.substring(0, 2), 16);
                        c.g = Integer.parseInt(v.substring(2, 4), 16);
                        c.b = Integer.parseInt(v.substring(4, 6), 16);
                        c.accent = false;
                    }
                });
                addDrawableChild(hex);
            }
            y += gap;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> client.setScreen(parent)).dimensions(width / 2 - 40, height - 30, 80, 20).build());
    }

    @Override
    public boolean shouldPause() { return false; }
}
