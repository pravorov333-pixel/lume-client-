package com.lume.client1165.gui;

import com.lume.client1165.LumeClient1165;
import com.lume.client1165.module.Category;
import com.lume.client1165.module.Module;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;

import java.util.ArrayList;
import java.util.List;

/**
 * Lume ClickGUI for the 1.16.5 build — centered glass window, logo header with a
 * Light/Dark toggle, a row of category tabs, and a list of module rows where each
 * row is a toggle (lavender pill = on). Rendered via {@link RenderUtil} on
 * {@link MatrixStack}. Simpler than the 1.21 card-grid (no per-card settings yet)
 * but shares the cream/lavender glass identity.
 */
public class ClickGuiScreen extends Screen {

    private static Category selectedCat = Category.VISUALS;

    private static final int W = 320, H = 244;
    private static final int ROW_H = 22, ROW_GAP = 4;

    // hit regions rebuilt every frame
    private final List<int[]> tabHits = new ArrayList<int[]>();    // x,y,w,h,ordinal
    private final List<int[]> modHits = new ArrayList<int[]>();    // x,y,w,h,index
    private int themeX, themeY, themeW, themeH;

    public ClickGuiScreen() {
        super(new LiteralText("Lume"));
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static List<Category> activeCats() {
        List<Category> out = new ArrayList<Category>();
        for (Category c : Category.values()) {
            if (!LumeClient1165.MODULES.getModules(c).isEmpty()) out.add(c);
        }
        if (out.isEmpty()) out.add(Category.VISUALS);
        return out;
    }

    @Override
    public void render(MatrixStack ms, int mouseX, int mouseY, float delta) {
        // dim backdrop
        RenderUtil.rect(ms, 0, 0, this.width, this.height, Theme.backdrop());

        int wx = (this.width - W) / 2;
        int wy = (this.height - H) / 2;

        // window: shadow + body + rim
        RenderUtil.roundedRect(ms, wx - 2, wy + 4, W + 4, H, 16, Theme.shadow());
        RenderUtil.gradientRoundedRect(ms, wx, wy, W, H, 16, Theme.winTop(), Theme.winBot());
        RenderUtil.roundedRect(ms, wx, wy, W, 1, 0, Theme.rim());

        // header: logo + wordmark
        RenderUtil.drawLogo(ms, wx + 16, wy + 14, 24);
        RenderUtil.textScaled(ms, this.textRenderer, "Lume", wx + 48, wy + 17, Theme.txt(), 1.6f, false);
        RenderUtil.text(ms, this.textRenderer, "1.16.5", wx + 49, wy + 33, Theme.txtDim(), false);

        // theme toggle pill (top-right)
        themeW = 54; themeH = 20; themeX = wx + W - themeW - 16; themeY = wy + 16;
        RenderUtil.roundedRect(ms, themeX, themeY, themeW, themeH, themeH / 2, Theme.glassRow());
        String tlabel = Theme.isDark() ? "Dark" : "Light";
        RenderUtil.textCentered(ms, this.textRenderer, tlabel, themeX, themeY, themeW, themeH, Theme.txt());

        // category tabs
        tabHits.clear();
        List<Category> cats = activeCats();
        int tabsY = wy + 50;
        int tabX = wx + 16;
        int tabH = 22;
        for (Category c : cats) {
            int tw = this.textRenderer.getWidth(c.title) + 20;
            boolean sel = c == selectedCat;
            if (sel) RenderUtil.gradientRoundedRect(ms, tabX, tabsY, tw, tabH, tabH / 2, Theme.accent(), Theme.accent2());
            else RenderUtil.roundedRect(ms, tabX, tabsY, tw, tabH, tabH / 2, Theme.glassRow());
            int tcol = sel ? 0xFFFFFFFF : Theme.txtDim();
            RenderUtil.textCentered(ms, this.textRenderer, c.title, tabX, tabsY, tw, tabH, tcol);
            tabHits.add(new int[]{tabX, tabsY, tw, tabH, c.ordinal()});
            tabX += tw + 6;
            if (tabX + 60 > wx + W - 16) { tabX = wx + 16; tabsY += tabH + 6; } // wrap if needed
        }

        // module rows
        modHits.clear();
        List<Module> mods = LumeClient1165.MODULES.getModules(selectedCat);
        int listX = wx + 16;
        int listW = W - 32;
        int ry = tabsY + tabH + 12;
        for (int i = 0; i < mods.size(); i++) {
            Module m = mods.get(i);
            boolean on = m.isEnabled();
            boolean hov = mouseX >= listX && mouseX <= listX + listW && mouseY >= ry && mouseY <= ry + ROW_H;
            int rowBg = on ? Theme.colorLerp(Theme.glassRow(), Theme.accent(), 0.22f)
                           : (hov ? Theme.glassHov() : Theme.glassRow());
            RenderUtil.roundedRect(ms, listX, ry, listW, ROW_H, 7, rowBg);
            RenderUtil.textVCentered(ms, this.textRenderer, m.getName(), listX + 12, ry, ROW_H, Theme.txt());

            // toggle pill on the right
            int pw = 34, ph = 14;
            int px = listX + listW - pw - 8, py = ry + (ROW_H - ph) / 2;
            if (on) RenderUtil.gradientRoundedRect(ms, px, py, pw, ph, ph / 2, Theme.accent(), Theme.accent2());
            else RenderUtil.roundedRect(ms, px, py, pw, ph, ph / 2, Theme.pillOff());
            int knob = ph - 4;
            int kx = on ? px + pw - knob - 2 : px + 2;
            RenderUtil.roundedRect(ms, kx, py + 2, knob, knob, knob / 2, 0xFFFFFFFF);

            modHits.add(new int[]{listX, ry, listW, ROW_H, i});
            ry += ROW_H + ROW_GAP;
        }

        super.render(ms, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // theme toggle
            if (mouseX >= themeX && mouseX <= themeX + themeW && mouseY >= themeY && mouseY <= themeY + themeH) {
                Theme.toggle();
                return true;
            }
            // tabs
            for (int[] t : tabHits) {
                if (mouseX >= t[0] && mouseX <= t[0] + t[2] && mouseY >= t[1] && mouseY <= t[1] + t[3]) {
                    selectedCat = Category.values()[t[4]];
                    return true;
                }
            }
            // module rows
            List<Module> mods = LumeClient1165.MODULES.getModules(selectedCat);
            for (int[] r : modHits) {
                if (mouseX >= r[0] && mouseX <= r[0] + r[2] && mouseY >= r[1] && mouseY <= r[1] + r[3]) {
                    Module m = mods.get(r[4]);
                    if (m.isToggleable()) m.toggle();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
