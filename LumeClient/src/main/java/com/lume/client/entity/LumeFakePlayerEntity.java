package com.lume.client.entity;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.world.ClientWorld;

/** Client-only stand-in "player" for the Fake Player module — never sent to or known by the server. */
public class LumeFakePlayerEntity extends OtherClientPlayerEntity {

    private volatile SkinTextures skin;

    public LumeFakePlayerEntity(ClientWorld world, GameProfile profile, SkinTextures skin) {
        super(world, profile);
        this.skin = skin;
    }

    public void setSkin(SkinTextures skin) { this.skin = skin; }

    @Override
    public SkinTextures getSkinTextures() { return skin; }
}
