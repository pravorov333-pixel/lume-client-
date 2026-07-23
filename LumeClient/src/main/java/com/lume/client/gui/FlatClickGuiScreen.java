package com.lume.client.gui;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Ultra Performance's ClickGUI — same module list/categories/settings as {@link ClickGuiScreen},
 * but built entirely from plain vanilla widgets (no NanoVG, no blur/glow, no hover/open
 * animations). Every module still toggles and every setting is still editable — this is a
 * different renderer for the same data, not a reduced feature set.
 */
public class FlatClickGuiScreen extends Screen {

    private Category selected = Category.VISUALS;
    private int scroll = 0;
    private int maxScroll = 0;

    public FlatClickGuiScreen() {
        super(Text.literal("Lume — Ultra Performance"));
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearChildren();

        int tabX = 6, tabY = 24, tabW = 96, tabH = 18;
        for (Category c : Category.values()) {
            Category cat = c;
            addDrawableChild(ButtonWidget.builder(Text.literal(c.title), b -> { selected = cat; scroll = 0; rebuild(); })
                    .dimensions(tabX, tabY, tabW, tabH).build()).active = selected != c;
            tabY += tabH + 3;
        }

        int listX = tabX + tabW + 12, listY = 24 - scroll, rowH = 22, rowW = Math.max(220, width - listX - 12);
        int top = 24, bottom = height - 8;
        java.util.List<Module> mods = LumeClient.MODULES.getModules(selected);
        for (Module m : mods) {
            int y = listY;
            listY += rowH;
            if (y + rowH < top || y > bottom) continue;   // clipped by scroll — skip building offscreen rows

            String label = m.getName() + "  [" + (m.isEnabled() ? "On" : "Off") + "]";
            ButtonWidget toggle = ButtonWidget.builder(Text.literal(label), b -> { if (m.isToggleable()) { m.toggle(); rebuild(); } })
                    .dimensions(listX, y, m.hasSettings() ? rowW - 26 : rowW, rowH - 2).build();
            toggle.active = m.isToggleable();
            addDrawableChild(toggle);
            if (m.hasSettings()) {
                addDrawableChild(ButtonWidget.builder(Text.literal("⚙"), b -> client.setScreen(new FlatModuleSettingsScreen(this, m)))
                        .dimensions(listX + rowW - 22, y, 22, rowH - 2).build());
            }
        }
        maxScroll = Math.max(0, listY - (24 - scroll) - (bottom - top));

        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close()).dimensions(width - 70, 6, 64, 18).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int before = scroll;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (verticalAmount * 18)));
        if (scroll != before) rebuild();
        return true;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, "Lume — Ultra Performance", width / 2, 6, 0xFFFFFFFF);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return true; }
}
