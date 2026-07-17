package com.lume.client.module.modules.render;

import com.lume.client.module.Category;
import com.lume.client.module.Module;

/**
 * Dropped items tumble on all 3 axes while falling and freeze at whatever
 * angle they land at, resting flush on the ground instead of vanilla's
 * perpetual float+spin — fixed, natural-feeling physics, no tunable knobs
 * (see {@code ItemEntityMixin} for the fall/landing physics, {@code
 * ItemEntityRendererMixin} for replacing vanilla's own draw with it).
 */
public class ItemPhysics extends Module {

    public ItemPhysics() {
        super("Item Physics", "Предметы падают и катятся как в реальном мире", Category.RENDER, -1);
    }
}
