package com.lume.client.mixin;

import com.lume.client.util.ItemPhysicsCarrier;
import net.minecraft.client.render.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Carries the tumble rotation + landed flag computed by {@code ItemEntityMixin} from update to render (see {@code ItemEntityRendererMixin}). */
@Mixin(ItemEntityRenderState.class)
public class ItemEntityRenderStateMixin implements ItemPhysicsCarrier {

    @Unique private float lume$yaw, lume$pitch, lume$roll;
    @Unique private boolean lume$grounded;

    @Override public float lume$getYaw() { return lume$yaw; }
    @Override public float lume$getPitch() { return lume$pitch; }
    @Override public float lume$getRoll() { return lume$roll; }
    @Override public boolean lume$getGrounded() { return lume$grounded; }

    @Override
    public void lume$set(float yaw, float pitch, float roll, boolean grounded) {
        lume$yaw = yaw; lume$pitch = pitch; lume$roll = roll; lume$grounded = grounded;
    }
}
