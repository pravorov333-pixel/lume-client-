package com.lume.client.gui;

import com.lume.client.command.MacroManager;
import com.lume.client.module.modules.qol.ChatFilters;
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

    // Chat Filters "add keyword" text field
    private String filterText = "";
    private boolean filterFocused = false;
    private int[] filterBox = { 0, 0, 0, 0 };

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
        int macroCount = Math.max(1, MacroManager.macros.size());
        int filterCount = Math.max(1, ChatFilters.keywords.size());
        int contentH = 22 * S + btnH + gap + btnH + gap + profiles.size() * (rowH + gap)
                + 20 * S + btnH + gap + packs.size() * (packRowH + 2 * S) + gap + btnH
                + gap + 20 * S + macroCount * (rowH + gap)
                + gap + 20 * S + filterCount * (rowH + gap) + btnH + gap;
        int maxScroll = Math.max(0, contentH - visH);
        scrollTarget = (float) Math.max(0, Math.min(scrollTarget, maxScroll));
        scroll = approach(scroll, scrollTarget, 16f, dt);
        if (Math.abs(scroll - scrollTarget) < 0.5f) scroll = scrollTarget;
        final int scrollI = Math.round(scroll);
        final int fMaxScroll = maxScroll, fContentH = contentH;
        final int fRowH = rowH, fGap = gap, fBtnH = btnH;
        final List<String> fProfiles = new ArrayList<>(profiles);
        final String fActive = active;

        if (!NanoVgRenderer.ready()) {
            renderLegacyContent(ctx, S, sw, sh, x, y, W, H, mx, my, dt, gy, clipTop, clipBot, visH,
                    sx, ew, scrollI, fMaxScroll, fContentH, fRowH, fGap, fBtnH, fProfiles, fActive, packs, packRowH);
            drawOpenTransition(S, sw, sh, x, y, W, H);
            return;
        }

        drawGlassBackdrop(S, sw, sh, x, y, W, H, 18 * S);
        ctx.draw();   // flush DrawContext's own queued geometry before raw-GL NanoVG draws
        NvgTextQueue.begin(ClickGuiScreen.getWinOffX() * S, ClickGuiScreen.getWinOffY() * S, sw / 2.0, sh / 2.0, total);
        NanoVgRenderer.frame(vg -> {
            applyTransform(vg, S, sw, sh);
            drawWindowFrame(vg, x, y, W, H, S, mx, my, 2, dt);

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
                NanoVgRenderer.text(vg, sx + btnW / 2f, bry + fBtnH / 2f, 11 * S, Theme.activeText(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Save"));
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
                NanoVgRenderer.text(vg, sx + ew / 2f, cby + fBtnH / 2f, 11 * S, Theme.activeText(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Confirm"));
                hits.add(new Object[]{ "confirmPack", "", sx, cby, ew, fBtnH });
            }
            cur += fBtnH;

            // Chat Macros — key -> command/message binds (added via ".macro add <key> <text>" in chat)
            cur += fGap;
            NanoVgRenderer.text(vg, sx, gy + cur - scrollI + 8 * S, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE,
                    com.lume.client.Lang.tUI("Chat Macros") + "  ·  .macro add <key> <text>");
            cur += 20 * S;
            if (MacroManager.macros.isEmpty()) {
                int ry = gy + cur - scrollI;
                if (ry + fRowH >= clipTop && ry <= clipBot) {
                    NanoVgRenderer.text(vg, sx, ry + fRowH / 2f, 9.5f * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE,
                            com.lume.client.Lang.tUI("No macros yet"));
                }
                cur += fRowH + fGap;
            } else {
                for (MacroManager.Macro mac : new ArrayList<>(MacroManager.macros)) {
                    int ry = gy + cur - scrollI;
                    if (ry + fRowH >= clipTop && ry <= clipBot) {
                        NanoVgRenderer.roundedRect(vg, sx, ry, ew, fRowH, 9 * S, Theme.glassRow());
                        NanoVgRenderer.text(vg, sx + 12 * S, ry + fRowH / 2f, 10.5f * S, Theme.accent(), NanoVgRenderer.ALIGN_MIDDLE,
                                com.lume.client.command.CommandManager.keyName(mac.key));
                        NanoVgRenderer.text(vg, sx + 46 * S, ry + fRowH / 2f, 10 * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, mac.text);
                        NanoVgRenderer.text(vg, sx + ew - 16 * S, ry + fRowH / 2f, 10 * S, 0xFFE05656, NanoVgRenderer.ALIGN_MIDDLE, "✕");
                        hits.add(new Object[]{ "delMacro", String.valueOf(mac.key), sx + ew - 22 * S, ry, 22 * S, fRowH });
                    }
                    cur += fRowH + fGap;
                }
            }

            // Chat Filters — keywords that hide matching chat lines (Chat Filters module must be on)
            cur += fGap;
            NanoVgRenderer.text(vg, sx, gy + cur - scrollI + 8 * S, 10 * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE,
                    com.lume.client.Lang.tUI("Chat Filters"));
            cur += 20 * S;
            if (ChatFilters.keywords.isEmpty()) {
                int ry = gy + cur - scrollI;
                if (ry + fRowH >= clipTop && ry <= clipBot) {
                    NanoVgRenderer.text(vg, sx, ry + fRowH / 2f, 9.5f * S, Theme.txtDim(), NanoVgRenderer.ALIGN_MIDDLE,
                            com.lume.client.Lang.tUI("No filters yet"));
                }
                cur += fRowH + fGap;
            } else {
                for (String word : new ArrayList<>(ChatFilters.keywords)) {
                    int ry = gy + cur - scrollI;
                    if (ry + fRowH >= clipTop && ry <= clipBot) {
                        NanoVgRenderer.roundedRect(vg, sx, ry, ew, fRowH, 9 * S, Theme.glassRow());
                        NanoVgRenderer.text(vg, sx + 12 * S, ry + fRowH / 2f, 10.5f * S, Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, word);
                        NanoVgRenderer.text(vg, sx + ew - 16 * S, ry + fRowH / 2f, 10 * S, 0xFFE05656, NanoVgRenderer.ALIGN_MIDDLE, "✕");
                        hits.add(new Object[]{ "delFilter", word, sx + ew - 22 * S, ry, 22 * S, fRowH });
                    }
                    cur += fRowH + fGap;
                }
            }

            int filterFieldW = ew - 80 * S, filterFieldH = fBtnH;
            int flX = sx, flY = gy + cur - scrollI;
            if (flY + filterFieldH >= clipTop && flY <= clipBot) {
                NanoVgRenderer.roundedRect(vg, flX, flY, filterFieldW, filterFieldH, 8 * S, filterFocused ? Theme.glassHov() : Theme.glassRow());
                NanoVgRenderer.strokeRoundedRect(vg, flX + 0.5f * S, flY + 0.5f * S, filterFieldW - S, filterFieldH - S, 8 * S, S,
                        filterFocused ? Theme.accent() : Theme.rim());
                String placeholder = com.lume.client.Lang.tUI("keyword…");
                String shown = filterText.isEmpty() && !filterFocused ? placeholder : filterText + (filterFocused ? "|" : "");
                NanoVgRenderer.text(vg, flX + 10 * S, flY + filterFieldH / 2f, 10 * S,
                        filterText.isEmpty() && !filterFocused ? Theme.txtDim() : Theme.txt(), NanoVgRenderer.ALIGN_MIDDLE, shown);

                int faX = flX + filterFieldW + 8 * S, faW = ew - filterFieldW - 8 * S;
                NanoVgRenderer.gradientRoundedRect(vg, faX, flY, faW, filterFieldH, 8 * S, Theme.accent(), Theme.accent2());
                NanoVgRenderer.text(vg, faX + faW / 2f, flY + filterFieldH / 2f, 10 * S, Theme.activeText(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Add"));
                hits.add(new Object[]{ "addFilter", "", faX, flY, faW, filterFieldH });
            }
            filterBox = new int[]{ flX, flY, filterFieldW, filterFieldH };
            cur += filterFieldH;

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
            NanoVgRenderer.text(vg, applyX + applyW / 2f, ibY + ibH / 2f, 10 * S, Theme.activeText(), NanoVgRenderer.ALIGN_CENTER_MIDDLE, com.lume.client.Lang.tUI("Apply"));
            hits.add(new Object[]{ "import", "", applyX, ibY, applyW, ibH });
        });
        NvgTextQueue.flush(ctx);
        drawOpenTransition(S, sw, sh, x, y, W, H);
    }

    /** DrawContext port of the NanoVG frame body above — same layout math, 1:1 primitive swap
     *  per the established NanoVG-removal conversion pattern. */
    private void renderLegacyContent(DrawContext ctx, int S, int sw, int sh, int x, int y, int W, int H,
                                      int mx, int my, float dt, int gy, int clipTop, int clipBot,
                                      int visH, int sx, int ew, int scrollI, int fMaxScroll, int fContentH,
                                      int fRowH, int fGap, int fBtnH, List<String> fProfiles, String fActive,
                                      List<net.minecraft.resource.ResourcePackProfile> packs, int packRowH) {
        applyTransformLegacy(ctx, S, sw, sh);
        drawWindowFrameLegacy(ctx, x, y, W, H, S, mx, my, 2, dt);
        var tr = this.textRenderer;

        ctx.enableScissor(x, clipTop, x + W, clipTop + visH);
        int cur = 0;

        RenderUtil.textVCentered(ctx, tr, com.lume.client.Lang.tUI("Config profiles — click to load"),
                sx, gy + cur - scrollI, 16 * S, Theme.txtDim(), 10f / 18f);
        cur += 22 * S;

        // Save + New buttons
        int btnW = (ew - 8 * S) / 2;
        int bry = gy + cur - scrollI;
        if (bry + fBtnH >= clipTop && bry <= clipBot) {
            sdfFill(ctx, S, sx, bry, btnW, fBtnH, 9 * S, Theme.accent());
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Save"), sx, bry, btnW, fBtnH, Theme.activeText(), 11f / 18f);
            hits.add(new Object[]{ "save", fActive, sx, bry, btnW, fBtnH });

            int bx2 = sx + btnW + 8 * S;
            sdfFillOutline(ctx, S, bx2, bry, btnW, fBtnH, 9 * S, Theme.glassHov(), Theme.rim(), S);
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("New Profile"), bx2, bry, btnW, fBtnH, Theme.txt(), 11f / 18f);
            hits.add(new Object[]{ "new", "", bx2, bry, btnW, fBtnH });
        }
        cur += fBtnH + fGap;

        // Reset the ClickGUI menu itself (window position/scale/theme) to factory defaults
        int rmy = gy + cur - scrollI;
        if (rmy + fBtnH >= clipTop && rmy <= clipBot) {
            sdfFillOutline(ctx, S, sx, rmy, ew, fBtnH, 9 * S, withAlpha(0xFFE05656, 0x33), withAlpha(0xFFE05656, 0x66), S);
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Reset Menu to Default"), sx, rmy, ew, fBtnH, 0xFFE05656, 10.5f / 18f);
            hits.add(new Object[]{ "resetMenu", "", sx, rmy, ew, fBtnH });
        }
        cur += fBtnH + fGap;

        // Profile list
        for (String name : fProfiles) {
            int ry = gy + cur - scrollI;
            if (ry + fRowH >= clipTop && ry <= clipBot) {
                boolean isActive = name.equals(fActive);
                boolean hov = mx >= sx && mx <= sx + ew && my >= ry && my <= ry + fRowH;
                sdfFill(ctx, S, sx, ry, ew, fRowH, 9 * S,
                        isActive ? withAlpha(Theme.accentRgb(), 0x44) : (hov ? Theme.glassHov() : Theme.glassRow()));
                if (isActive) sdfFill(ctx, S, sx, ry, 3 * S, fRowH, 2 * S, Theme.accent());
                RenderUtil.textVCentered(ctx, tr, name, sx + 14 * S, ry, fRowH, isActive ? Theme.accent() : Theme.txt(), 11f / 18f);
                if (isActive) {
                    String activeLbl = com.lume.client.Lang.tUI("active");
                    int aw = RenderUtil.width(tr, activeLbl, 9f / 18f);
                    RenderUtil.textVCentered(ctx, tr, activeLbl, sx + ew - aw - 10 * S, ry, fRowH, Theme.accent(), 9f / 18f);
                } else {
                    RenderUtil.textVCentered(ctx, tr, "✕", sx + ew - 16 * S, ry, fRowH, 0xFFE05656, 10f / 18f);
                    hits.add(new Object[]{ "delete", name, sx + ew - 22 * S, ry, 22 * S, fRowH });
                }
                hits.add(new Object[]{ "load", name, sx, ry, ew - 24 * S, fRowH });
            }
            cur += fRowH + fGap;
        }

        // Texture Packs & Mods
        cur += fGap;
        RenderUtil.textVCentered(ctx, tr, com.lume.client.Lang.tUI("Texture Packs & Mods"), sx, gy + cur - scrollI, 16 * S, Theme.txtDim(), 10f / 18f);
        cur += 20 * S;

        int folderBtnW = (ew - 8 * S) / 2;
        int fby = gy + cur - scrollI;
        if (fby + fBtnH >= clipTop && fby <= clipBot) {
            sdfFillOutline(ctx, S, sx, fby, folderBtnW, fBtnH, 9 * S, Theme.glassHov(), Theme.rim(), S);
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Open Texture Packs Folder"), sx, fby, folderBtnW, fBtnH, Theme.txt(), 9.5f / 18f);
            hits.add(new Object[]{ "openTexFolder", "", sx, fby, folderBtnW, fBtnH });

            int fbx2 = sx + folderBtnW + 8 * S;
            sdfFillOutline(ctx, S, fbx2, fby, folderBtnW, fBtnH, 9 * S, Theme.glassHov(), Theme.rim(), S);
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Open Mods Folder"), fbx2, fby, folderBtnW, fBtnH, Theme.txt(), 9.5f / 18f);
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
                sdfFill(ctx, S, sx, pry, ew, packRowH, 6 * S,
                        isSel ? withAlpha(Theme.accentRgb(), 0x44) : (hovP ? Theme.glassHov() : Theme.glassRow()));
                RenderUtil.textVCentered(ctx, tr, p.getDisplayName().getString(), sx + 10 * S, pry, packRowH,
                        isSel ? Theme.accent() : Theme.txt(), 9f / 18f);
                hits.add(new Object[]{ "selectPack", id, sx, pry, ew, packRowH });
            }
            cur += packRowH + 2 * S;
        }
        cur += fGap;

        boolean packChanged = pendingPack != null && !pendingPack.equals(activePack);
        int cby = gy + cur - scrollI;
        if (packChanged && cby + fBtnH >= clipTop && cby <= clipBot) {
            sdfFill(ctx, S, sx, cby, ew, fBtnH, 9 * S, Theme.accent());
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Confirm"), sx, cby, ew, fBtnH, Theme.activeText(), 11f / 18f);
            hits.add(new Object[]{ "confirmPack", "", sx, cby, ew, fBtnH });
        }
        cur += fBtnH;

        // Chat Macros
        cur += fGap;
        RenderUtil.textVCentered(ctx, tr, com.lume.client.Lang.tUI("Chat Macros") + "  ·  .macro add <key> <text>",
                sx, gy + cur - scrollI, 16 * S, Theme.txtDim(), 10f / 18f);
        cur += 20 * S;
        if (MacroManager.macros.isEmpty()) {
            int ry = gy + cur - scrollI;
            if (ry + fRowH >= clipTop && ry <= clipBot) {
                RenderUtil.textVCentered(ctx, tr, com.lume.client.Lang.tUI("No macros yet"), sx, ry, fRowH, Theme.txtDim(), 9.5f / 18f);
            }
            cur += fRowH + fGap;
        } else {
            for (MacroManager.Macro mac : new ArrayList<>(MacroManager.macros)) {
                int ry = gy + cur - scrollI;
                if (ry + fRowH >= clipTop && ry <= clipBot) {
                    sdfFill(ctx, S, sx, ry, ew, fRowH, 9 * S, Theme.glassRow());
                    RenderUtil.textVCentered(ctx, tr, com.lume.client.command.CommandManager.keyName(mac.key),
                            sx + 12 * S, ry, fRowH, Theme.accent(), 10.5f / 18f);
                    RenderUtil.textVCentered(ctx, tr, mac.text, sx + 46 * S, ry, fRowH, Theme.txt(), 10f / 18f);
                    RenderUtil.textVCentered(ctx, tr, "✕", sx + ew - 16 * S, ry, fRowH, 0xFFE05656, 10f / 18f);
                    hits.add(new Object[]{ "delMacro", String.valueOf(mac.key), sx + ew - 22 * S, ry, 22 * S, fRowH });
                }
                cur += fRowH + fGap;
            }
        }

        // Chat Filters
        cur += fGap;
        RenderUtil.textVCentered(ctx, tr, com.lume.client.Lang.tUI("Chat Filters"), sx, gy + cur - scrollI, 16 * S, Theme.txtDim(), 10f / 18f);
        cur += 20 * S;
        if (ChatFilters.keywords.isEmpty()) {
            int ry = gy + cur - scrollI;
            if (ry + fRowH >= clipTop && ry <= clipBot) {
                RenderUtil.textVCentered(ctx, tr, com.lume.client.Lang.tUI("No filters yet"), sx, ry, fRowH, Theme.txtDim(), 9.5f / 18f);
            }
            cur += fRowH + fGap;
        } else {
            for (String word : new ArrayList<>(ChatFilters.keywords)) {
                int ry = gy + cur - scrollI;
                if (ry + fRowH >= clipTop && ry <= clipBot) {
                    sdfFill(ctx, S, sx, ry, ew, fRowH, 9 * S, Theme.glassRow());
                    RenderUtil.textVCentered(ctx, tr, word, sx + 12 * S, ry, fRowH, Theme.txt(), 10.5f / 18f);
                    RenderUtil.textVCentered(ctx, tr, "✕", sx + ew - 16 * S, ry, fRowH, 0xFFE05656, 10f / 18f);
                    hits.add(new Object[]{ "delFilter", word, sx + ew - 22 * S, ry, 22 * S, fRowH });
                }
                cur += fRowH + fGap;
            }
        }

        int filterFieldW = ew - 80 * S, filterFieldH = fBtnH;
        int flX = sx, flY = gy + cur - scrollI;
        if (flY + filterFieldH >= clipTop && flY <= clipBot) {
            sdfFillOutline(ctx, S, flX, flY, filterFieldW, filterFieldH, 8 * S,
                    filterFocused ? Theme.glassHov() : Theme.glassRow(), filterFocused ? Theme.accent() : Theme.rim(), S);
            String placeholder = com.lume.client.Lang.tUI("keyword…");
            String shown = filterText.isEmpty() && !filterFocused ? placeholder : filterText + (filterFocused ? "|" : "");
            RenderUtil.textVCentered(ctx, tr, shown, flX + 10 * S, flY, filterFieldH,
                    filterText.isEmpty() && !filterFocused ? Theme.txtDim() : Theme.txt(), 10f / 18f);

            int faX = flX + filterFieldW + 8 * S, faW = ew - filterFieldW - 8 * S;
            sdfFill(ctx, S, faX, flY, faW, filterFieldH, 8 * S, Theme.accent());
            RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Add"), faX, flY, faW, filterFieldH, Theme.activeText(), 10f / 18f);
            hits.add(new Object[]{ "addFilter", "", faX, flY, faW, filterFieldH });
        }
        filterBox = new int[]{ flX, flY, filterFieldW, filterFieldH };
        cur += filterFieldH;

        ctx.disableScissor();

        if (fMaxScroll > 0) {
            int sbW = 3 * S, sbX = x + W - 14 * S;
            sdfFill(ctx, S, sbX, gy, sbW, visH, sbW / 2, Theme.glassRow());
            int thumbH = Math.max(14 * S, Math.round(visH * (visH / (float) fContentH)));
            int thumbY = gy + Math.round((visH - thumbH) * (scroll / fMaxScroll));
            sdfFill(ctx, S, sbX, thumbY, sbW, thumbH, sbW / 2, Theme.accent());
        }

        // Import by code section (always visible at bottom, unclipped)
        int ibAreaY = y + H - 70 * S;
        RenderUtil.roundedRect(ctx, x + 10 * S, ibAreaY - 2 * S, W - 20 * S, Math.max(1, S), Math.max(1, S) / 2, Theme.border());
        RenderUtil.textVCentered(ctx, tr, com.lume.client.Lang.tUI("Import config by code:"), sx, ibAreaY, 16 * S, Theme.txtDim(), 9f / 18f);

        int ibW = ew - 80 * S, ibH = 26 * S;
        int ibX = sx, ibY = ibAreaY + 22 * S;
        sdfFillOutline(ctx, S, ibX, ibY, ibW, ibH, 8 * S,
                importFocused ? Theme.glassHov() : Theme.glassRow(), importFocused ? Theme.accent() : Theme.rim(), S);
        String placeholder = com.lume.client.Lang.tUI("profile code…");
        String shown = importCode.isEmpty() && !importFocused ? placeholder : importCode + (importFocused ? "|" : "");
        RenderUtil.textVCentered(ctx, tr, shown, ibX + 10 * S, ibY, ibH,
                importCode.isEmpty() && !importFocused ? Theme.txtDim() : Theme.txt(), 10f / 18f);
        importBox = new int[]{ ibX, ibY, ibW, ibH };

        int applyX = ibX + ibW + 8 * S, applyW = ew - ibW - 8 * S;
        sdfFill(ctx, S, applyX, ibY, applyW, ibH, 8 * S, Theme.accent());
        RenderUtil.textCentered(ctx, tr, com.lume.client.Lang.tUI("Apply"), applyX, ibY, applyW, ibH, Theme.activeText(), 10f / 18f);
        hits.add(new Object[]{ "import", "", applyX, ibY, applyW, ibH });

        ctx.getMatrices().pop();
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

        // Chat Filters "add keyword" field
        if (mx >= filterBox[0] && mx <= filterBox[0] + filterBox[2]
                && my >= filterBox[1] && my <= filterBox[1] + filterBox[3]) {
            filterFocused = true;
            return true;
        }
        filterFocused = false;

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
                case "delMacro" -> { MacroManager.remove(Integer.parseInt(name)); com.lume.client.Config.save(); }
                case "delFilter" -> { ChatFilters.remove(name); com.lume.client.Config.save(); }
                case "addFilter" -> {
                    if (!filterText.isBlank()) { ChatFilters.add(filterText); com.lume.client.Config.save(); filterText = ""; }
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
        if (filterFocused && chr >= 32 && chr != 127) { filterText += chr; return true; }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (importFocused) {
            if (key == 256) { importFocused = false; return true; }
            if (key == 259 && !importCode.isEmpty()) { importCode = importCode.substring(0, importCode.length() - 1); return true; }
            return true;
        }
        if (filterFocused) {
            if (key == 256) { filterFocused = false; return true; }
            if (key == 257 || key == 335) { // Enter
                if (!filterText.isBlank()) { ChatFilters.add(filterText); com.lume.client.Config.save(); filterText = ""; }
                return true;
            }
            if (key == 259 && !filterText.isEmpty()) { filterText = filterText.substring(0, filterText.length() - 1); return true; }
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
