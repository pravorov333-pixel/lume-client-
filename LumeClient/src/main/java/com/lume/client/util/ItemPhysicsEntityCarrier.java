package com.lume.client.util;

/** Implemented by {@code ItemEntityMixin} — the live tumble simulation, ticked every server/client tick. */
public interface ItemPhysicsEntityCarrier {
    float lume$tumbleYaw();
    float lume$tumblePitch();
    float lume$tumbleRoll();
    boolean lume$isLanded();
}
