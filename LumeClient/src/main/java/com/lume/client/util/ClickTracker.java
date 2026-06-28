package com.lume.client.util;

import java.util.ArrayDeque;
import java.util.Deque;

/** Tracks left/right mouse clicks for the CPS counter (clicks in the last second). */
public final class ClickTracker {

    private static final Deque<Long> left = new ArrayDeque<>();
    private static final Deque<Long> right = new ArrayDeque<>();

    private ClickTracker() {}

    public static void leftClick() { left.addLast(System.currentTimeMillis()); }
    public static void rightClick() { right.addLast(System.currentTimeMillis()); }

    public static int left() { return count(left); }
    public static int right() { return count(right); }

    private static int count(Deque<Long> q) {
        long now = System.currentTimeMillis();
        while (!q.isEmpty() && now - q.peekFirst() > 1000) q.removeFirst();
        return q.size();
    }
}
