package com.lume.client.module.modules.cosmetic;

import com.lume.client.LumeClient;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.BoolSetting;

/**
 * Clean View — removes jarring camera motion and distracting vanilla visuals:
 * the red tilt when you take damage, walking view-bob, the on-fire overlay/model
 * flame, boss bars, the scoreboard sidebar, the totem-of-undying pop animation,
 * and (optionally) the persistent chat log. Cosmetic / comfort. Applied across
 * {@code GameRendererMixin}, {@code InGameOverlayRendererMixin}, {@code EntityRendererMixin},
 * {@code InGameHudMixin}, {@code BossBarHudMixin}, {@code GameRendererTotemMixin} and {@code ChatHudMixin}.
 */
public class CleanView extends Module {

    public final BoolSetting hurtCam = add(new BoolSetting("No hurt cam", true));
    public final BoolSetting viewBob = add(new BoolSetting("No view bob", false));
    public final BoolSetting noFireSelf = add(new BoolSetting("No Fire", false));
    public final BoolSetting noFire = add(new BoolSetting("No Fire overlay", false));
    public final BoolSetting noUnderwater = add(new BoolSetting("No Underwater tint", false));
    public final BoolSetting noScoreboard = add(new BoolSetting("No Scoreboard", false));
    public final BoolSetting noBossBar = add(new BoolSetting("No Boss Bar", false));
    public final BoolSetting noTotemAnim = add(new BoolSetting("No Totem Animation", false));
    public final BoolSetting hideChat = add(new BoolSetting("Hide Chat", false));

    public CleanView() {
        super("Clean View", "No hurt tilt / steadier view", Category.COSMETIC, -1);
    }

    public static boolean noHurtCam() {
        Module m = LumeClient.MODULES.getByName("Clean View");
        return m instanceof CleanView c && c.isEnabled() && c.hurtCam.value;
    }

    public static boolean noBob() {
        Module m = LumeClient.MODULES.getByName("Clean View");
        return m instanceof CleanView c && c.isEnabled() && c.viewBob.value;
    }

    /** True if the burning-model overlay on your own player (third person / hand) should be hidden. */
    public static boolean noFireSelf() {
        Module m = LumeClient.MODULES.getByName("Clean View");
        return m instanceof CleanView c && c.isEnabled() && c.noFireSelf.value;
    }

    /** True if the full-screen fire overlay should be removed entirely. */
    public static boolean noFire() {
        Module m = LumeClient.MODULES.getByName("Clean View");
        return m instanceof CleanView c && c.isEnabled() && c.noFire.value;
    }

    /** True if the blue underwater screen tint/texture should be removed. */
    public static boolean noUnderwater() {
        Module m = LumeClient.MODULES.getByName("Clean View");
        return m instanceof CleanView c && c.isEnabled() && c.noUnderwater.value;
    }

    /** True if the scoreboard sidebar should be hidden. */
    public static boolean noScoreboard() {
        Module m = LumeClient.MODULES.getByName("Clean View");
        return m instanceof CleanView c && c.isEnabled() && c.noScoreboard.value;
    }

    /** True if boss bars (Wither/Ender Dragon/server-sent) should be hidden. */
    public static boolean noBossBar() {
        Module m = LumeClient.MODULES.getByName("Clean View");
        return m instanceof CleanView c && c.isEnabled() && c.noBossBar.value;
    }

    /** True if the totem-of-undying floating-item pop animation should be suppressed. */
    public static boolean noTotemAnim() {
        Module m = LumeClient.MODULES.getByName("Clean View");
        return m instanceof CleanView c && c.isEnabled() && c.noTotemAnim.value;
    }

    /** True if the persistent chat log should stay hidden until the chat screen is open. */
    public static boolean hideChat() {
        Module m = LumeClient.MODULES.getByName("Clean View");
        return m instanceof CleanView c && c.isEnabled() && c.hideChat.value;
    }
}
