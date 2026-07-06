package com.lume.client.gui;

import com.lume.client.nanovg.NanoVgRenderer;
import com.lume.client.util.ConfigProfiles;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Full-screen Config tab — profile save/load/new/delete + import by code. */
public class ConfigScreen extends LumeSubScreen {

    private static final int WIN_W = 520, WIN_H = 356;

    private float scroll = 0, scrollTarget = 0;
    private long lastFrame = System.currentTimeMillis();

    // Import code text field
    private String importCode = "";
    private boolean importFocused = false;
    private int[] importBox = { 0, 0, 0, 0 };

    // Texture pack picker (GUI-only pending selection until Confirm is pressed)
    private String pendingPack = null;

    // Hit testing (filled per frame)
    private final List<Object[]> hits = new ArrayList<>();

    public ConfigScreen(Screen parent) {
        super(Text.literal("Config – Lume"), parent);
    }

    private static net.minecraft.resource.ResourcePackManager packManager() {
        return net.minecraft.client.MinecraftClient.getInstance().getResourcePackManager();
    }

    /** Currently applied non-vanilla pack id, or null if none. */
    private static String activePackId() {
        for (String id : packManager().getEnabledIds()) if (!"vanilla".equals(id)) return id;
        return null;
    }

    /** Opens (and creates if missing) a subfolder of the game's run directory in the OS file explorer. */
    private static void openFolder(String name) {
        java.io.File dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve(name).toFile();
        dir.mkdirs();
        net.minecraft.util.Util.getOperatingSystem().open(dir);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);   // same background blur as the main menu
        ctx.fill(0, 0, width, height, Theme.backdrop());
        drawHudEditor(ctx, mouseX, mouseY);          // draggable HUD frames stay visible here
        NanoVgRenderer.ensureInit();
        if (!NanoVgRenderer.ready()) return;

        int S = (int) Math.max(1, client.getWindow().getScaleFactor());
        long now = System.currentTimeMillis();
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        int sw = width * S, sh = height * S;
        int W = WIN_W * S, H = WIN_H * S;
        int x = (sw - W) / 2, y = (sh - H) / 2;
        computeTotal(WIN_W, WIN_H);
        int mx = localMx(mouseX, S, sw), my = localMy(mouseY, S, sh);

        hits.clear();

        List<String> profiles = ConfigProfiles.list();
        String active = ConfigProfiles.activeProfile;
        int margin = 24 * S;
        int gy = y + 42 * S;
        int clipTop = gy - 2 * S;
        int clipBot = y + H - 74 * S; // leave room for import field
        int visH = clipBot - gy;
        int sx = x + margin, ew = W - margin * 2;

        List<net.minecraft.resource.ResourcePackProfile> packs = new ArrayList<>();
        for (net.minecraft.resource.ResourcePackProfile p : packManager().getProfiles())
            if (!"vanilla".equals(p.getId())) packs.add(p);
        int packRowH = 18 * S;

        int rowH = 30 * S, gap = 8 * S, btnH = 28 * S;
        int contentH = 22 * S + btnH + gap + btnH + gap + profiles.size() * (rowH + gap)
                + 20 * S + btnH + gap + packs.size() * (packRowH + 2 * S) + gap + btnH;
        int maxScroll = Math.max(0, contentH - visH);
        scrollTarget = (float) Math.max(0, Math.min(scrollTarget, maxScroll));
        scroll = approach(scroll, scrollTarget, 16f, dt);
        if (Math.abs(scroll - scrollTarget) < 0.5f) scroll = scrollTarget;
        final int scrollI = Math.round(scroll);
        final int fMaxScroll = maxScroll, fContentH = contentH;
        final int fRowH = rowH, fGap = gap, fBtnH = btnH;
        final List<String> fProfiles = new ArrayList<>(profiles);
        final String fActive = active;

        NanoVgRenderer.frame(vg -> {
            applyTransform(vg, S, sw, sh);
            drawWindowFrame(vg, x, y, W, H, S, mx, my, 2);

            NanoVgRenderer.save(vg);
            NanoVgRenderer.scissor(vg, x, clipTop, W, visH);
            int cur = 0;

            NanoVgRenderer.text(vg, sx, gy + cur - scrollI + 8 * S, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE,
                    com.lume.client.Lang.tUI("Config profiles — click to load"));
            cur += 22 * S;

            // Save + New buttons
            int btnW = (ew - 8 * S) / 2;
            int bry = gy + cur - scrollI;
            if (bry + fBtnH >= clipTop && bry <= clipBot) {
                NanoVgRenderer.shadow(vg, sx, bry, btnW, fBtnH, 9 * S, 8 * S, withAlpha(Theme.accentRgb(), 0x44));
                NanoVgRenderer.gradientRoundedRect(vg, sx, bry, btnW, fBtnH, 9 * S, Theme.accent(), Theme.accent2());
                NanoVgRenderer.text(vg, sx + btnW / 2f, bry + fBtnH / 2f, 11 * S, 0xFFFFFFFF, NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Save"));
                hits.add(new Object[]{ "save", fActive, sx, bry, btnW, fBtnH });

                int bx2 = sx + btnW + 8 * S;
                NanoVgRenderer.roundedRect(vg, bx2, bry, btnW, fBtnH, 9 * S, Theme.glassHov());
                NanoVgRenderer.strokeRoundedRect(vg, bx2 + 0.5f * S, bry + 0.5f * S, btnW - S, fBtnH - S, 9 * S, S, Theme.rim());
                NanoVgRenderer.text(vg, bx2 + btnW / 2f, bry + fBtnH / 2f, 11 * S, Theme.txt(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("New Profile"));
                hits.add(new Object[]{ "new", "", bx2, bry, btnW, fBtnH });
            }
            cur += fBtnH + fGap;

            // Reset the ClickGUI menu itself (window position/scale/theme) to factory defaults
            int rmy = gy + cur - scrollI;
            if (rmy + fBtnH >= clipTop && rmy <= clipBot) {
                NanoVgRenderer.roundedRect(vg, sx, rmy, ew, fBtnH, 9 * S, withAlpha(0xFFE05656, 0x33));
                NanoVgRenderer.strokeRoundedRect(vg, sx + 0.5f * S, rmy + 0.5f * S, ew - S, fBtnH - S, 9 * S, S, withAlpha(0xFFE05656, 0x66));
                NanoVgRenderer.text(vg, sx + ew / 2f, rmy + fBtnH / 2f, 10.5f * S, 0xFFE05656, NanoVgRenderer.ALIGN_CENTER_MIDDLE,
                        com.lume.client.Lang.tUI("Reset Menu to Default"));
                hits.add(new Object[]{ "resetMenu", "", sx, rmy, ew, fBtnH });
            }
            cur += fBtnH + fGap;

            // Profile list
            for (String name : fProfiles) {
                int ry = gy + cur - scrollI;
                if (ry + fRowH >= clipTop && ry <= clipBot) {
                    boolean isActive = name.equals(fActive);
                    boolean hov = mx >= sx && mx <= sx + ew && my >= ry && my <= ry + fRowH;
                    NanoVgRenderer.roundedRect(vg, sx, ry, ew, fRowH, 9 * S,
                            isActive ? withAlpha(Theme.accentRgb(), 0x44) : (hov ? Theme.glassHov() : Theme.glassRow()));
                    if (isActive) NanoVgRenderer.roundedRect(vg, sx, ry, 3 * S, fRowH, 2 * S, Theme.accent());
                    NanoVgRenderer.text(vg, sx + 14 * S, ry + fRowH / 2f, 11 * S,
                            isActive ? Theme.accent() : Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, name);
                    if (isActive) {
                        float aw = NanoVgRenderer.textWidth(vg, 9 * S, com.lume.client.Lang.tUI("active"));
                        NanoVgRenderer.text(vg, sx + ew - (int) aw - 10 * S, ry + fRowH / 2f, 9 * S, Theme.accent(), NanoVgRenderer.ALIGN_MIDDLE, com.lume.client.Lang.tUI("active"));
                    } else {
                        NanoVgRenderer.text(vg, sx + ew - 16 * S, ry + fRowH / 2f, 10 * S, 0xFFE05656, NanoVgRenderer.ALIGN_MIDDLE, "✕");
                        hits.add(new Object[]{ "delete", name, sx + ew - 22 * S, ry, 22 * S, fRowH });
                    }
                    hits.add(new Object[]{ "load", name, sx, ry, ew - 24 * S, fRowH });
                }
                cur += fRowH + fGap;
            }

            // Texture Packs & Mods
            cur += fGap;
            NanoVgRenderer.text(vg, sx, gy + cur - scrollI + 8 * S, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE,
                    com.lume.client.Lang.tUI("Texture Packs & Mods"));
            cur += 20 * S;

            int folderBtnW = (ew - 8 * S) / 2;
            int fby = gy + cur - scrollI;
            if (fby + fBtnH >= clipTop && fby <= clipBot) {
                NanoVgRenderer.roundedRect(vg, sx, fby, folderBtnW, fBtnH, 9 * S, Theme.glassHov());
                NanoVgRenderer.strokeRoundedRect(vg, sx + 0.5f * S, fby + 0.5f * S, folderBtnW - S, fBtnH - S, 9 * S, S, Theme.rim());
                NanoVgRenderer.text(vg, sx + folderBtnW / 2f, fby + fBtnH / 2f, 9.5f * S, Theme.txt(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Open Texture Packs Folder"));
                hits.add(new Object[]{ "openTexFolder", "", sx, fby, folderBtnW, fBtnH });

                int fbx2 = sx + folderBtnW + 8 * S;
                NanoVgRenderer.roundedRect(vg, fbx2, fby, folderBtnW, fBtnH, 9 * S, Theme.glassHov());
                NanoVgRenderer.strokeRoundedRect(vg, fbx2 + 0.5f * S, fby + 0.5f * S, folderBtnW - S, fBtnH - S, 9 * S, S, Theme.rim());
                NanoVgRenderer.text(vg, fbx2 + folderBtnW / 2f, fby + fBtnH / 2f, 9.5f * S, Theme.txt(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Open Mods Folder"));
                hits.add(new Object[]{ "openModsFolder", "", fbx2, fby, folderBtnW, fBtnH });
            }
            cur += fBtnH + fGap;

            String activePack = activePackId();
            String pendingSel = pendingPack != null ? pendingPack : activePack;
            for (net.minecraft.resource.ResourcePackProfile p : packs) {
                String id = p.getId();
                int pry = gy + cur - scrollI;
                if (pry + packRowH >= clipTop && pry <= clipBot) {
                    boolean isSel = id.equals(pendingSel);
                    boolean hovP = mx >= sx && mx <= sx + ew && my >= pry && my <= pry + packRowH;
                    NanoVgRenderer.roundedRect(vg, sx, pry, ew, packRowH, 6 * S,
                            isSel ? withAlpha(Theme.accentRgb(), 0x44) : (hovP ? Theme.glassHov() : Theme.glassRow()));
                    NanoVgRenderer.text(vg, sx + 10 * S, pry + packRowH / 2f, 9 * S,
                            isSel ? Theme.accent() : Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, p.getDisplayName().getString());
                    hits.add(new Object[]{ "selectPack", id, sx, pry, ew, packRowH });
                }
                cur += packRowH + 2 * S;
            }
            cur += fGap;

            boolean packChanged = pendingPack != null && !pendingPack.equals(activePack);
            int cby = gy + cur - scrollI;
            if (packChanged && cby + fBtnH >= clipTop && cby <= clipBot) {
                NanoVgRenderer.gradientRoundedRect(vg, sx, cby, ew, fBtnH, 9 * S, Theme.accent(), Theme.accent2());
                NanoVgRenderer.text(vg, sx + ew / 2f, cby + fBtnH / 2f, 11 * S, 0xFFFFFFFF, NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Confirm"));
                hits.add(new Object[]{ "confirmPack", "", sx, cby, ew, fBtnH });
            }
            cur += fBtnH;

            NanoVgRenderer.restore(vg);

            if (fMaxScroll > 0) {
                int sbW = 3 * S, sbX = x + W - 14 * S;
                NanoVgRenderer.roundedRect(vg, sbX, gy, sbW, visH, sbW / 2f, Theme.glassRow());
                int thumbH = Math.max(14 * S, Math.round(visH * (visH / (float) fContentH)));
                int thumbY = gy + Math.round((visH - thumbH) * (scroll / fMaxScroll));
                NanoVgRenderer.roundedRect(vg, sbX, thumbY, sbW, thumbH, sbW / 2f, Theme.accent());
            }

            // Import by code section (always visible at bottom)
            int ibAreaY = y + H - 70 * S;
            NanoVgRenderer.roundedRect(vg, x + 10 * S, ibAreaY - 2 * S, W - 20 * S, Math.max(1, S), 0.5f, Theme.border());
            NanoVgRenderer.text(vg, sx, ibAreaY + 8 * S, 9 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE,
                    com.lume.client.Lang.tUI("Import config by code:"));

            int ibW = ew - 80 * S, ibH = 26 * S;
            int ibX = sx, ibY = ibAreaY + 22 * S;
            NanoVgRenderer.roundedRect(vg, ibX, ibY, ibW, ibH, 8 * S, importFocused ? Theme.glassHov() : Theme.glassRow());
            NanoVgRenderer.strokeRoundedRect(vg, ibX + 0.5f * S, ibY + 0.5f * S, ibW - S, ibH - S, 8 * S, S,
                    importFocused ? Theme.accent() : Theme.rim());
            String placeholder = com.lume.client.Lang.tUI("profile code…");
            String shown = importCode.isEmpty() && !importFocused ? placeholder : importCode + (importFocused ? "|" : "");
            NanoVgRenderer.text(vg, ibX + 10 * S, ibY + ibH / 2f, 10 * S,
                    importCode.isEmpty() && !importFocused ? Theme.txtDim() : Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, shown);
            importBox = new int[]{ ibX, ibY, ibW, ibH };

            int applyX = ibX + ibW + 8 * S, applyW = ew - ibW - 8 * S;
            NanoVgRenderer.gradientRoundedRect(vg, applyX, ibY, applyW, ibH, 8 * S, Theme.accent(), Theme.accent2());
            NanoVgRenderer.text(vg, applyX + applyW / 2f, ibY + ibH / 2f, 10 * S, 0xFFFFFFFF, NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Apply"));
            hits.add(new Object[]{ "import", "", applyX, ibY, applyW, ibH });
        });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        if (tryNavClick(mouseX, mouseY)) return true;

        int S = (int) Math.max(1, client.getWindow().getScaleFactor());
        int sw = width * S, sh = height * S;
        double mx = localMx(mouseX, S, sw), my = localMy(mouseY, S, sh);

        // Import code field
        if (mx >= importBox[0] && mx <= importBox[0] + importBox[2]
                && my >= importBox[1] && my <= importBox[1] + importBox[3]) {
            importFocused = true;
            return true;
        }
        importFocused = false;

        for (Object[] h : hits) {
            String kind = (String) h[0];
            String name = (String) h[1];
            int hx = (int) h[2], hy = (int) h[3], hw = (int) h[4], hh = (int) h[5];
            if (mx < hx || mx > hx + hw || my < hy || my > hy + hh) continue;
            switch (kind) {
                case "save" -> { ConfigProfiles.saveActive(); com.lume.client.Config.save(); }
                case "new" -> {
                    ConfigProfiles.activeProfile = "profile_" + System.currentTimeMillis();
                    ConfigProfiles.saveActive();
                }
                case "load" -> {
                    ConfigProfiles.activeProfile = name;
                    if (!ConfigProfiles.loadActive()) ConfigProfiles.saveActive();
                }
                case "delete" -> { ConfigProfiles.delete(name); }
                case "import" -> {
                    if (!importCode.isBlank()) {
                        try { com.lume.client.Config.fromJson(importCode); }
                        catch (Exception e) { System.out.println("[Lume] import failed: " + e); }
                        importCode = "";
                    }
                }
                case "resetMenu" -> ClickGuiScreen.resetToDefaults();
                case "openTexFolder" -> openFolder("resourcepacks");
                case "openModsFolder" -> openFolder("mods");
                case "selectPack" -> pendingPack = name;
                case "confirmPack" -> {
                    if (pendingPack != null) {
                        var manager = packManager();
                        manager.setEnabledProfiles(java.util.List.of("vanilla", pendingPack));
                        client.reloadResourcesConcurrently();
                        pendingPack = null;
                    }
                }
            }
            return true;
        }
        if (tryHudDrag(mouseX, mouseY)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (importFocused && chr >= 32 && chr != 127) { importCode += chr; return true; }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (importFocused) {
            if (key == 256) { importFocused = false; return true; }
            if (key == 259 && !importCode.isEmpty()) { importCode = importCode.substring(0, importCode.length() - 1); return true; }
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        scrollTarget -= (float) v * 30 * (int) Math.max(1, client.getWindow().getScaleFactor());
        return true;
    }
}
