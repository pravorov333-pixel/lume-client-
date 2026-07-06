package com.lume.client;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Custom Lume sound events (registered from assets/lume/sounds.json). */
public final class Sounds {

    public static final SoundEvent BASS_CRIT_1 = register("bass_crit_1");
    public static final SoundEvent BASS_CRIT_2 = register("bass_crit_2");

    private Sounds() {}

    private static SoundEvent register(String path) {
        Identifier id = Identifier.of("lume", path);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    /** Touches the class to force static init (call once from mod init). */
    public static void init() {}
}
