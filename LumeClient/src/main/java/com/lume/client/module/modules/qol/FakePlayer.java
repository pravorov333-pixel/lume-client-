package com.lume.client.module.modules.qol;

import com.lume.client.LumeClient;
import com.lume.client.entity.LumeFakePlayerEntity;
import com.lume.client.module.Category;
import com.lume.client.module.Module;
import com.lume.client.module.setting.ModeSetting;
import com.lume.client.module.setting.StringSetting;
import com.lume.client.util.SkinFetcher;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * Spawns a client-only "clone" of yourself standing where you are — a real tracked entity
 * in {@code mc.world} so it renders, hits, and gets ESP'd exactly like another player, but is
 * never sent to the server and never seen by anyone else.
 */
public class FakePlayer extends Module {

    public final StringSetting displayName = add(new StringSetting("Name", "Player"));
    public final ModeSetting skin = add(new ModeSetting("Skin", 0, "Steve", "Alex", "Nickname"));
    public final StringSetting skinNickname = add(new StringSetting("Skin Nickname", ""));
    public final ModeSetting pose = add(new ModeSetting("Pose", 0, "Standing", "Sitting", "T-pose"));

    private LumeFakePlayerEntity entity;
    private String appliedName, appliedNickname;
    private int appliedSkinIndex = -1;
    private double spawnX, spawnY, spawnZ;
    private float spawnYaw;

    public FakePlayer() {
        super("Fake Player", "Spawns a client-only clone of yourself where you stand", Category.CHAT, -1);
    }

    @Override
    public void onEnable() { spawn(); }

    @Override
    public void onDisable() { despawn(); }

    @Override
    public void onTick() {
        // Also caught by LumeClient's JOIN hook (the normal path when reconnecting), but this
        // covers going back to the title screen and staying there without a fresh JOIN firing.
        if (mc.player == null || mc.world == null) { setEnabled(false); return; }
        if (entity == null || entity.isRemoved()) { spawn(); return; }
        boolean nameChanged = !displayName.value.equals(appliedName);
        boolean skinChanged = skin.index != appliedSkinIndex
                || (skin.index == 2 && !skinNickname.value.equals(appliedNickname));
        if (nameChanged) { spawn(); return; }        // GameProfile name is immutable → respawn to rename
        if (skinChanged) applyNewSkin();

        // Nothing ever confirms this entity's position from a server, so ordinary local physics
        // (gravity, block collision push-out) would otherwise slowly walk/sink it away from where
        // it was placed — pin it down every tick instead of relying on setNoGravity() alone.
        entity.setVelocity(0, 0, 0);
        entity.setPosition(spawnX, spawnY, spawnZ);
        entity.setYaw(spawnYaw);
        entity.setHeadYaw(spawnYaw);
        entity.setBodyYaw(spawnYaw);
    }

    private void spawn() {
        despawn();
        if (mc.player == null || mc.world == null) return;
        String name = displayName.value.isBlank() ? "Player" : displayName.value;
        GameProfile profile = new GameProfile(UUID.randomUUID(), name);
        entity = new LumeFakePlayerEntity(mc.world, profile, defaultSkin(skin.index == 1));
        spawnX = mc.player.getX(); spawnY = mc.player.getY(); spawnZ = mc.player.getZ();
        spawnYaw = mc.player.getYaw();
        entity.setPosition(spawnX, spawnY, spawnZ);
        entity.setYaw(spawnYaw);
        entity.setHeadYaw(spawnYaw);
        entity.setBodyYaw(spawnYaw);
        // Never touched by a server: health/dimensions otherwise stay whatever the bare DataTracker
        // defaults are, which isAlive() (gate for Target ESP's findTarget) and hit-particle sizing
        // (derived from getBoundingBox()) both depend on being correct.
        entity.setNoGravity(true);
        entity.setHealth(entity.getMaxHealth());
        entity.calculateDimensions();
        mc.world.addEntity(entity);
        appliedName = displayName.value;
        appliedSkinIndex = skin.index;
        appliedNickname = skinNickname.value;
        if (skin.index == 2 && !skinNickname.value.isBlank()) fetchNicknameSkin();
    }

    private void applyNewSkin() {
        appliedSkinIndex = skin.index;
        appliedNickname = skinNickname.value;
        if (skin.index == 2) {
            if (!skinNickname.value.isBlank()) fetchNicknameSkin();
        } else {
            entity.setSkin(defaultSkin(skin.index == 1));
        }
    }

    private void fetchNicknameSkin() {
        LumeFakePlayerEntity target = entity;
        SkinFetcher.fetch(skinNickname.value, (Identifier id, Boolean slim) -> {
            if (target == entity && entity != null) {
                entity.setSkin(new SkinTextures(id, "", null, null,
                        slim ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE, true));
            }
        });
    }

    private static SkinTextures defaultSkin(boolean alex) {
        Identifier tex = alex ? Identifier.ofVanilla("textures/entity/player/slim/alex.png")
                : Identifier.ofVanilla("textures/entity/player/wide/steve.png");
        return new SkinTextures(tex, "", null, null, alex ? SkinTextures.Model.SLIM : SkinTextures.Model.WIDE, true);
    }

    private void despawn() {
        if (entity != null) { entity.discard(); entity = null; }
        appliedSkinIndex = -1;
        appliedName = null;
    }

    /** 0 = no pose override, 1 = Sitting, 2 = T-pose — read by {@code BipedEntityModelMixin} for our tracked entity's render id only. */
    public static int poseFor(int entityId) {
        Module m = LumeClient.MODULES.getByName("Fake Player");
        if (!(m instanceof FakePlayer fp) || !fp.isEnabled() || fp.entity == null) return 0;
        if (fp.entity.getId() != entityId) return 0;
        return fp.pose.index;
    }
}
