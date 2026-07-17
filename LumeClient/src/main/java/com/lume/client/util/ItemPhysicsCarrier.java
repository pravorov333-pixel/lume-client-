package com.lume.client.util;

/** Implemented by {@code ItemEntityRenderStateMixin} — carries the tumble rotation + landed flag from update to render. */
public interface ItemPhysicsCarrier {
    float lume$getYaw();
    float lume$getPitch();
    float lume$getRoll();
    boolean lume$getGrounded();
    void lume$set(float yaw, float pitch, float roll, boolean grounded);
}
