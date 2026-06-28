package com.lume.client1165.module;

/** Module categories shown as tabs in the ClickGUI. */
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
