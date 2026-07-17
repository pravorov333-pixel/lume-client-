package com.lume.client.gui;

import com.lume.client.nanovg.NanoVgRenderer;

/**
 * Shared "Menu / Events / Config / Friends" nav bar — drawn by BOTH ClickGuiScreen's own
 * header (the "Menu" tab) and LumeSubScreen's (Events/Config/Friends). The sliding accent
 * pill is STATIC so it's the same pill gliding across screen-class boundaries: ClickGuiScreen
 * and LumeSubScreen are different classes (Events/Config/Friends even re-instantiate on every
 * tab switch), so per-instance animation state would just snap instead of animating whenever
 * the transition crossed one of those boundaries — e.g. Menu → Events never animated before,
 * only Events ↔ Config ↔ Friends did, since only LumeSubScreen owned any pill state at all.
 */
final class NavBar {
    private NavBar() {}

    private static float pillX = -1f, pillW = -1f;

    private static float approach(float cur, float target, float rate, float dt) {
        return cur + (target - cur) * Math.min(1f, rate * dt);
    }

    /** Draws the bar centred over the window [x, x+W), fills outNavX/outNavW (length 4) with
     *  each tab's hit rect, and returns {barY, barH} for the caller's own hit-testing. */
    static int[] draw(long vg, int x, int y, int W, int S, int activeTab, float dt, int[] outNavX, int[] outNavW) {
        int navH = 28 * S, navGap = 6 * S;
        int nY = y - navGap - navH;
        String[] labels = { com.lume.client.Lang.tUI("Menu"), com.lume.client.Lang.tUI("Events"),
                com.lume.client.Lang.tUI("Config"), com.lume.client.Lang.tUI("Friends") };
        float font = 10 * S;
        int pad = 12 * S;
        int total = 0;
        int[] ww = new int[4];
        for (int i = 0; i < 4; i++) {
            ww[i] = (int) NanoVgRenderer.textWidth(vg, font, labels[i]) + pad * 2;
            total += ww[i];
        }
        int barX = x + (W - total) / 2;
        NanoVgRenderer.shadow(vg, barX - 4 * S, nY - 3 * S, total + 8 * S, navH + 6 * S, 13 * S, 16 * S, 0x44000000);
        NanoVgRenderer.roundedRect(vg, barX - 4 * S, nY - 3 * S, total + 8 * S, navH + 6 * S, 13 * S, Theme.glassRow());
        NanoVgRenderer.strokeRoundedRect(vg, barX - 4 * S + 0.5f * S, nY - 3 * S + 0.5f * S,
                total + 8 * S - S, navH + 6 * S - S, 13 * S, S, Theme.rim());
        int cx = barX;
        for (int i = 0; i < 4; i++) { outNavX[i] = cx; outNavW[i] = ww[i]; cx += ww[i]; }

        // activeTab < 0 means "nothing selected" (ClickGuiScreen clears this while a search
        // is active) — leave the pill's last position frozen (not drawn) so it resumes
        // animating smoothly from wherever it was once the search clears, instead of
        // snapping or animating toward a nonexistent tab index.
        if (activeTab >= 0) {
            float tx = outNavX[activeTab], tw = outNavW[activeTab];
            if (pillX < 0) { pillX = tx; pillW = tw; }   // first frame ever: snap into place
            pillX = approach(pillX, tx, 18f, dt);
            pillW = approach(pillW, tw, 18f, dt);
            NanoVgRenderer.bloom(vg, pillX, nY, pillW, navH, 12 * S, 10 * S, Theme.accentRgb(), 0x88);
            NanoVgRenderer.gradientRoundedRect(vg, pillX, nY, pillW, navH, 12 * S, Theme.accent(), Theme.accent2());
        }

        for (int i = 0; i < 4; i++) {
            int lblCol = i == activeTab ? Theme.activeText() : (Theme.isDark() ? Theme.txtDim() : 0xFFFFFFFF);
            NanoVgRenderer.text(vg, outNavX[i] + outNavW[i] / 2f, nY + navH / 2f, font,
                    lblCol, NanoVgRenderer.ALIGN_CENTER_MIDDLE, labels[i]);
        }
        return new int[]{ nY, navH };
    }
}
