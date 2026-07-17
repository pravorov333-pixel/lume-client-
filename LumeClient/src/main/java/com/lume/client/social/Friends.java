package com.lume.client.social;

import com.google.gson.JsonObject;
import com.lume.client.LumeClient;
import com.lume.client.gui.Notifications;
import com.lume.client.module.Module;
import com.lume.client.module.modules.qol.FastWaypoint;
import com.lume.client.module.modules.qol.Waypoints;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Friends list + cross-server presence/points state, backed by {@link FriendsNet}.
 * Identity is just the Minecraft username (no account system exists yet — see
 * {@code Config}); {@link #deviceId} is a random per-install token sent along
 * with writes so a shared-point delete can at least check "did I create this".
 * This is a convenience trust model between actual friends, not real auth.
 *
 * <p>Call {@link #tick()} once a client tick; every network call is throttled
 * internally so this is cheap to call unconditionally (mirrors fthw.EventManager).
 */
public final class Friends {
    private Friends() {}

    public static volatile String deviceId = null;

    public static final class Status {
        public boolean online;
        public String server, dimension;
        public double x, y, z;
        public long lastSeen;
    }

    public static final class Point {
        public long id;
        public String from, name, server, dimension;
        public double x, y, z;
    }

    public static volatile List<String> friendList = new ArrayList<>();
    public static volatile List<String> incoming = new ArrayList<>();
    public static volatile List<String> outgoing = new ArrayList<>();
    public static volatile Map<String, Status> status = new HashMap<>();
    public static volatile List<Point> points = new ArrayList<>();

    /** Marker name for a Fast Waypoint ping (see {@link #pingHere()}) — distinguishes it from a
     *  normal persistent shared point so it can be rendered as a transient 5s pin with a sound. */
    public static final String PING_NAME = "⚡ Ping";
    /** Point id -> local expiry (ms since epoch). Populated the first time a ping point is seen;
     *  {@code HudRenderer} only draws a ping pin while its id is present here and not expired. */
    public static final Map<Long, Long> activePings = new HashMap<>();
    private static final Set<Long> seenPointIds = new HashSet<>();
    private static long lastPingSentAt = 0;

    private static long lastHeartbeat = 0, lastFriendPoll = 0, lastStatusPoll = 0, lastPointPoll = 0;

    public static String ensureDeviceId() {
        if (deviceId == null) deviceId = UUID.randomUUID().toString();
        return deviceId;
    }

    public static String myName() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player != null ? mc.player.getGameProfile().getName() : null;
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String me = myName();
        if (me == null) return;
        long now = System.currentTimeMillis();

        if (now - lastHeartbeat > 15000) {
            lastHeartbeat = now;
            String server = Waypoints.currentServerKey();
            String dim = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
            FriendsNet.heartbeat(me, ensureDeviceId(), server, dim, mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }

        if (now - lastFriendPoll > 12000) {
            lastFriendPoll = now;
            FriendsNet.refreshFriendList(me, obj -> {
                List<String> f = new ArrayList<>(), in = new ArrayList<>(), out = new ArrayList<>();
                obj.getAsJsonArray("friends").forEach(e -> f.add(e.getAsString()));
                obj.getAsJsonArray("incoming").forEach(e -> in.add(e.getAsJsonObject().get("from_user").getAsString()));
                obj.getAsJsonArray("outgoing").forEach(e -> out.add(e.getAsJsonObject().get("to_user").getAsString()));
                friendList = f; incoming = in; outgoing = out;
            });
        }

        if (now - lastStatusPoll > 8000 && !friendList.isEmpty()) {
            lastStatusPoll = now;
            FriendsNet.refreshStatus(friendList, obj -> {
                Map<String, Status> m = new HashMap<>();
                for (String k : obj.keySet()) {
                    JsonObject o = obj.getAsJsonObject(k);
                    Status s = new Status();
                    s.online = o.has("online") && o.get("online").getAsBoolean();
                    if (s.online) {
                        s.server = str(o, "server"); s.dimension = str(o, "dimension");
                        s.x = num(o, "x"); s.y = num(o, "y"); s.z = num(o, "z");
                        s.lastSeen = o.has("lastSeen") && !o.get("lastSeen").isJsonNull() ? o.get("lastSeen").getAsLong() : 0;
                    }
                    m.put(k, s);
                }
                status = m;
            });
        }

        // Short interval (vs. the other polls) so a Fast Waypoint ping propagates to friends in
        // a few seconds rather than up to the old 15s — see pingHere().
        if (now - lastPointPoll > 3000) {
            lastPointPoll = now;
            FriendsNet.refreshPoints(me, arr -> {
                List<Point> pts = new ArrayList<>();
                for (var el : arr) {
                    JsonObject o = el.getAsJsonObject();
                    Point p = new Point();
                    p.id = o.get("id").getAsLong();
                    p.from = o.get("from_user").getAsString();
                    p.name = o.get("name").getAsString();
                    p.x = o.get("x").getAsDouble(); p.y = o.get("y").getAsDouble(); p.z = o.get("z").getAsDouble();
                    p.server = str(o, "server"); p.dimension = str(o, "dimension");
                    pts.add(p);
                }
                handlePings(pts);
                points = pts;
            });
        }
    }

    /** Detects newly-arrived ping points (plays the pling + toasts once per id) and, once our own
     *  ping has had a few poll cycles to propagate to friends, deletes it so it doesn't linger. */
    private static void handlePings(List<Point> pts) {
        String me = myName();
        Set<Long> currentIds = new HashSet<>();
        for (Point p : pts) {
            currentIds.add(p.id);
            if (!PING_NAME.equals(p.name) || seenPointIds.contains(p.id)) continue;
            activePings.put(p.id, System.currentTimeMillis() + 5000);
            if (me != null && !p.from.equalsIgnoreCase(me)) {
                Module m = LumeClient.MODULES.getByName("Fast Waypoint");
                boolean soundOn = !(m instanceof FastWaypoint fw) || fw.pingSound.value;
                MinecraftClient mc = MinecraftClient.getInstance();
                if (soundOn && mc.player != null) {
                    mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 1.0f));
                }
                Notifications.push(p.from + " pinged a location!", 0xFFE8C15A, 3000);
            }
        }
        seenPointIds.retainAll(currentIds);
        seenPointIds.addAll(currentIds);
        activePings.keySet().retainAll(currentIds);

        if (lastPingSentAt != 0 && System.currentTimeMillis() - lastPingSentAt > 8000) {
            lastPingSentAt = 0;
            for (Point p : pts) {
                if (PING_NAME.equals(p.name) && me != null && p.from.equalsIgnoreCase(me)) {
                    deletePoint(p);
                }
            }
        }
    }

    /** Fast Waypoint: pings where you're LOOKING (not your feet) to every friend for ~5s (with a
     *  pling sound on their end), then cleans itself up. Bound via the {@code Fast Waypoint}
     *  module's Binds entry. Aim point: a real block hit within range, else a point far out
     *  along the look vector (so pointing at a distant mountain still marks roughly there,
     *  not nothing) — same {@code World.raycast} idiom Target ESP uses for its own line-of-sight
     *  check, just with a much longer 300-block range since this isn't attack-reach-limited. */
    public static void pingHere() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String me = myName();
        if (mc.player == null || me == null || mc.world == null) return;
        net.minecraft.util.math.Vec3d start = mc.player.getCameraPosVec(1f);
        net.minecraft.util.math.Vec3d look = mc.player.getRotationVec(1f);
        net.minecraft.util.math.Vec3d end = start.add(look.multiply(300));
        net.minecraft.util.hit.BlockHitResult hit = mc.world.raycast(new net.minecraft.world.RaycastContext(
                start, end, net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player));
        net.minecraft.util.math.Vec3d aim = hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? end : hit.getPos();

        String server = Waypoints.currentServerKey();
        String dim = mc.world.getRegistryKey().getValue().toString();
        lastPingSentAt = System.currentTimeMillis();
        FriendsNet.sharePoint(me, ensureDeviceId(), "all", PING_NAME, aim.x, aim.y, aim.z, server, dim);
        lastPointPoll = 0;   // pull our own ping in on the very next tick, seeding activePings sooner
    }

    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null; }
    private static double num(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : 0; }

    public static Status statusOf(String name) { return status.get(name.toLowerCase(Locale.ROOT)); }

    public static boolean isOnline(String name) {
        Status s = statusOf(name);
        return s != null && s.online;
    }

    public static boolean onSameServer(String name) {
        Status s = statusOf(name);
        return s != null && s.online && s.server != null && s.server.equalsIgnoreCase(Waypoints.currentServerKey());
    }

    // ---- actions -------------------------------------------------------

    public static void addFriend(String name) {
        String me = myName();
        if (me != null && !name.isBlank()) FriendsNet.sendRequest(me, name.trim());
    }

    public static void acceptFriend(String name) {
        String me = myName();
        if (me != null) { FriendsNet.accept(me, name); lastFriendPoll = 0; } // force a re-poll next tick
    }

    public static void declineFriend(String name) {
        String me = myName();
        if (me != null) { FriendsNet.decline(me, name); lastFriendPoll = 0; }
    }

    public static void removeFriend(String name) {
        String me = myName();
        if (me != null) { FriendsNet.remove(me, name); lastFriendPoll = 0; }
    }

    /** Sends "/tpa <name>" as if the player typed it — works on servers with a tpa plugin (FunTime/HolyWorld). */
    public static void tpaTo(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatCommand("tpa " + name);
    }

    /** Connects straight to the address a friend last heartbeat-ed from. */
    public static void connectTo(String address) {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            ServerInfo info = new ServerInfo(address, address, ServerInfo.ServerType.OTHER);
            ConnectScreen.connect(new TitleScreen(), mc, ServerAddress.parse(address), info, false, null);
        } catch (Throwable ignored) {}
    }

    /** Shares your current position with one friend ("name") or everyone ("all"). */
    public static void sharePointHere(String toFriendOrAll) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String me = myName();
        if (mc.player == null || me == null) return;
        String server = Waypoints.currentServerKey();
        String dim = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
        FriendsNet.sharePoint(me, ensureDeviceId(), toFriendOrAll, "Marker",
                mc.player.getX(), mc.player.getY(), mc.player.getZ(), server, dim);
        lastPointPoll = 0;
    }

    public static void deletePoint(Point p) {
        String me = myName();
        if (me == null) return;
        FriendsNet.deletePoint(p.id, me, deviceId);
        List<Point> copy = new ArrayList<>(points);
        copy.remove(p);
        points = copy;
    }

    /** Shared points visible right now: same server key as us, same dimension. */
    public static List<Point> pointsHere() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String server = Waypoints.currentServerKey();
        String dim = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
        List<Point> out = new ArrayList<>();
        for (Point p : points) {
            if (p.server != null && p.server.equalsIgnoreCase(server) && java.util.Objects.equals(p.dimension, dim)) out.add(p);
        }
        return out;
    }
}
