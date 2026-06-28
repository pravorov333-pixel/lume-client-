package com.lume.client.fthw;

import java.util.regex.Pattern;

/**
 * One configurable server event: a display name, a chat regex that marks its
 * start, and how long it runs (seconds). FunTime/HolyWorld-specific rules will
 * be filled in once the exact chat texts are gathered.
 */
public class EventRule {

    public final ServerType server;
    public final String name;
    public final Pattern startPattern;
    public final int durationSec;

    // learned schedule: when it was last seen + estimated interval between occurrences
    public long lastSeen = 0;
    public long intervalSec = 0;

    public EventRule(ServerType server, String name, String startRegex, int durationSec) {
        this.server = server;
        this.name = name;
        this.startPattern = Pattern.compile(startRegex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        this.durationSec = durationSec;
    }

    /** Seconds since last seen, or -1 if never. */
    public long agoSec() {
        return lastSeen == 0 ? -1 : (System.currentTimeMillis() - lastSeen) / 1000;
    }

    /** Estimated seconds until next occurrence, or -1 if unknown. */
    public long etaSec() {
        if (lastSeen == 0 || intervalSec <= 0) return -1;
        return (lastSeen / 1000 + intervalSec) - System.currentTimeMillis() / 1000;
    }
}
