package com.lume.client.module;

import com.lume.client.module.modules.cosmetic.BlockOutline;
import com.lume.client.module.modules.cosmetic.Capes;
import com.lume.client.module.modules.cosmetic.CleanView;
import com.lume.client.module.modules.cosmetic.CustomDeathScreen;
import com.lume.client.module.modules.cosmetic.CustomCrosshair;
import com.lume.client.module.modules.cosmetic.CustomHotbar;
import com.lume.client.module.modules.cosmetic.CustomInventory;
import com.lume.client.module.modules.cosmetic.CustomMenu;
import com.lume.client.module.modules.cosmetic.EnchantGlint;
import com.lume.client.module.modules.cosmetic.GuiAnimations;
import com.lume.client.module.modules.cosmetic.GameFont;
import com.lume.client.module.modules.cosmetic.JumpParticles;
import com.lume.client.module.modules.cosmetic.MenuLogo;
import com.lume.client.module.modules.cosmetic.ParticleTrail;
import com.lume.client.module.modules.cosmetic.Trail;
import com.lume.client.module.modules.fthw.ServerHelper;
import com.lume.client.module.modules.misc.HudScale;
import com.lume.client.module.modules.misc.Language;
import com.lume.client.module.modules.player.AutoSprint;
import com.lume.client.module.modules.qol.AntiSpam;
import com.lume.client.module.modules.qol.PvpHelper;
import com.lume.client.module.modules.qol.AutoCommand;
import com.lume.client.module.modules.qol.Autoclicker;
import com.lume.client.module.modules.qol.AutoReconnect;
import com.lume.client.module.modules.qol.AutoRespawn;
import com.lume.client.module.modules.qol.ChatTimestamps;
import com.lume.client.module.modules.qol.FakePlayer;
import com.lume.client.module.modules.qol.FastXp;
import com.lume.client.module.modules.qol.LockSlot;
import com.lume.client.module.modules.qol.Waypoints;
import com.lume.client.module.modules.performance.EntityDistance;
import com.lume.client.module.modules.performance.FpsLimit;
import com.lume.client.module.modules.performance.GraphicsQuality;
import com.lume.client.module.modules.performance.JvmOptimizer;
import com.lume.client.module.modules.performance.RenderDistance;
import com.lume.client.module.modules.render.Aspect;
import com.lume.client.module.modules.render.CustomHand;
import com.lume.client.module.modules.render.CustomHitbox;
import com.lume.client.module.modules.render.FreeLook;
import com.lume.client.module.modules.render.FullBright;
import com.lume.client.module.modules.render.HitColor;
import com.lume.client.module.modules.render.HitParticles;
import com.lume.client.module.modules.render.NoHitParticles;
import com.lume.client.module.modules.render.HitSound;
import com.lume.client.module.modules.render.DeathAnimations;
import com.lume.client.module.modules.render.ItemPhysics;
import com.lume.client.module.modules.render.ReducedParticles;
import com.lume.client.module.modules.render.TimeChanger;
import com.lume.client.module.modules.render.WorldCustomizer;
import com.lume.client.module.modules.render.WorldParticles;
import com.lume.client.module.modules.render.Zoom;
import com.lume.client.module.modules.qol.BetterChat;
import com.lume.client.module.modules.qol.ArmorManager;
import com.lume.client.module.modules.qol.AutoEat;
import com.lume.client.module.modules.qol.ChatFilters;
import com.lume.client.module.modules.qol.DiscordPresence;
import com.lume.client.module.modules.qol.FastWaypoint;
import com.lume.client.module.modules.visual.Alerts;
import com.lume.client.module.modules.visual.ArmorHud;
import com.lume.client.module.modules.visual.BlockInfo;
import com.lume.client.module.modules.visual.CustomNametags;
import com.lume.client.module.modules.visual.Hud;
import com.lume.client.module.modules.visual.InventoryHud;
import com.lume.client.module.modules.visual.Keystrokes;
import com.lume.client.module.modules.visual.ModuleList;
import com.lume.client.module.modules.visual.PotionHud;
import com.lume.client.module.modules.visual.SelfName;
import com.lume.client.module.modules.visual.ShiftIndicator;
import com.lume.client.module.modules.visual.TargetEsp;
import com.lume.client.module.modules.visual.TotemCounter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registers and stores all modules, and routes ticks / keybinds to them.
 */
public class ModuleManager {

    private final List<Module> modules = new ArrayList<>();
    // getByName() is called from ~120 call sites across mixins/HUD/GUI code, many of them
    // every frame (e.g. HeldItemRendererMixin alone calls it 4x/frame) — a linear scan with
    // equalsIgnoreCase over ~50 modules on every one of those calls was real, repeated CPU
    // cost. `byName` is keyed by the module's exact registered name — every current call site
    // passes that exact string (verified), so the common case is a zero-allocation exact-match
    // hit; `byNameLower` is a lower-cased fallback map kept only so a differently-cased caller
    // still resolves correctly, matching the old case-insensitive contract.
    private final Map<String, Module> byName = new HashMap<>();
    private final Map<String, Module> byNameLower = new HashMap<>();

    /**
     * Register every module here, grouped by sub-theme within each category so related
     * modules always sit next to each other in the ClickGUI (not just same-category, but
     * clustered) — e.g. every HUD panel is together, every combat-feedback effect is
     * together. Keep this grouping when adding new modules: drop them into the matching
     * cluster below rather than at the end of the category.
     */
    public void init() {
        // --- Visuals: HUD panels/overlays first, then world markers (ESP/vanity) ---
        register(new Hud());
        register(new Keystrokes());
        register(new PotionHud());
        register(new ArmorHud());
        register(new InventoryHud());
        register(new TotemCounter());
        register(new BlockInfo());
        register(new ModuleList());
        register(new ShiftIndicator());
        register(new TargetEsp());
        register(new SelfName());
        register(new CustomNametags());
        register(new Alerts());
        // --- Performance ---
        register(new RenderDistance());
        register(new FpsLimit());
        register(new GraphicsQuality());
        register(new EntityDistance());
        register(new ReducedParticles());
        register(new JvmOptimizer());
        // --- Render: combat feedback, then viewmodel/camera, then world visuals ---
        register(new HitSound());
        register(new HitColor());
        register(new HitParticles());
        register(new NoHitParticles());
        register(new CustomHitbox());
        register(new DeathAnimations());
        register(new ItemPhysics());
        register(new FullBright());
        register(new Zoom());
        register(new FreeLook());
        register(new CustomHand());
        register(new Aspect());
        register(new TimeChanger());
        register(new WorldParticles());
        register(new WorldCustomizer());
        // --- Chat & QoL ---
        register(new AutoSprint());
        register(new AutoReconnect());
        register(new AutoRespawn());
        register(new PvpHelper());
        register(new AntiSpam());
        register(new ChatFilters());
        register(new BetterChat());
        register(new ChatTimestamps());
        register(new Waypoints());
        register(new FastWaypoint());
        register(new ServerHelper());
        register(new Autoclicker());
        register(new FastXp());
        register(new LockSlot());
        register(new AutoCommand());
        register(new FakePlayer());
        register(new AutoEat());
        register(new ArmorManager());
        register(new DiscordPresence());
        // --- Cosmetics: menu/GUI skin, then in-world cosmetics ---
        register(new MenuLogo());
        register(new GameFont());
        register(new CustomMenu());
        register(new CustomHotbar());
        register(new CustomInventory());
        register(new GuiAnimations());
        register(new com.lume.client.module.modules.cosmetic.NoBgBlur());
        register(new CustomCrosshair());
        register(new BlockOutline());
        register(new Capes());
        register(new ParticleTrail());
        register(new JumpParticles());
        register(new Trail());
        register(new EnchantGlint());
        register(new CustomDeathScreen());
        register(new CleanView());
        // --- Settings ---
        register(new HudScale());
        register(new Language());

        // Sensible defaults so the client looks alive on first launch.
        Module hud = getByName("HUD");
        if (hud != null) hud.setEnabled(true);
        Module menuLogo = getByName("Menu Logo");
        if (menuLogo != null) menuLogo.setEnabled(true);
        Module discord = getByName("Discord Rich Presence");
        if (discord != null) discord.setEnabled(true);
    }

    private void register(Module module) {
        modules.add(module);
        byName.put(module.getName(), module);
        byNameLower.put(module.getName().toLowerCase(java.util.Locale.ROOT), module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public List<Module> getModules(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module m : modules) {
            if (m.getCategory() == category) result.add(m);
        }
        return result;
    }

    public Module getByName(String name) {
        if (name == null) return null;
        Module m = byName.get(name);
        return m != null ? m : byNameLower.get(name.toLowerCase(java.util.Locale.ROOT));
    }

    /** Drive modules bound to the given GLFW key. HOLD mode follows {@code pressed}; TOGGLE flips on press. */
    public void onKey(int key, boolean pressed) {
        for (Module m : modules) {
            if (m.getKey() == -1 || m.getKey() != key) continue;
            if (m.getBindMode() == Module.BindMode.HOLD) m.setEnabled(pressed);
            else if (pressed) m.toggle();
        }
    }

    public void onTick() {
        for (Module m : modules) {
            if (m.isEnabled()) m.onTick();
        }
    }
}
