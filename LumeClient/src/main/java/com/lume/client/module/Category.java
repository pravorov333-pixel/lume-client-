package com.lume.client.module;

/**
 * Module categories shown as columns in the ClickGUI.
 * Matches the approved glass mockup: Visuals, Render, Performance, Chat & QoL, Settings.
 */
public enum Category {
    VISUALS("Visuals"),
    RENDER("Render"),
    PERFORMANCE("Performance"),
    CHAT("Chat & QoL"),
    COSMETIC("Cosmetics"),
    SETTINGS("Settings");

    public final String title;

    Category(String title) {
        this.title = title;
    }
}
