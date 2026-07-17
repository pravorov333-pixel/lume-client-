package com.lume.client.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * Fast Connect — saved server shortcuts shown on the Lume main menu (name +
 * address). Click connects straight to the server, skipping the Multiplayer
 * server list. Persisted in the config.
 */
public final class FastConnect {

    public static final class Entry {
        public String name;
        public String address;
        public Entry(String name, String address) {
            this.name = name;
            this.address = address;
        }
    }

    public static final List<Entry> list = new ArrayList<>();

    private FastConnect() {}

    public static void add(String name, String address) {
        if (name.isBlank() || address.isBlank()) return;
        list.add(new Entry(name.trim(), address.trim()));
    }

    public static void remove(Entry e) {
        list.remove(e);
    }
}
