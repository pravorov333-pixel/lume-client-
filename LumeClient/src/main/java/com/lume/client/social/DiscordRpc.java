package com.lume.client.social;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Minimal Discord Rich Presence client — talks directly to the local Discord
 * client over its IPC named pipe ({@code \\.\pipe\discord-ipc-N}). No external
 * dependency: uses Gson (already bundled with Minecraft) for the JSON frames
 * and a plain {@link RandomAccessFile} to open the pipe, which Windows exposes
 * as a regular file path — no native library needed. Windows-only for now.
 * Every call fails silently (no Discord running, wrong client id, etc.) —
 * this is cosmetic, never worth crashing or spamming errors over.
 */
public final class DiscordRpc {

    private static RandomAccessFile pipe;
    private static long startTimestamp = 0;

    private DiscordRpc() {}

    public static boolean connected() { return pipe != null; }

    /** Blocking — call off-thread. Tries pipes 0..9 (Discord picks the first free one it can bind). */
    public static synchronized boolean connect(String clientId) {
        if (pipe != null) return true;
        for (int i = 0; i < 10; i++) {
            try {
                RandomAccessFile f = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                JsonObject hs = new JsonObject();
                hs.addProperty("v", 1);
                hs.addProperty("client_id", clientId);
                writeFrame(f, 0, hs.toString());
                readFrame(f);   // READY event — just drain it, nothing to act on
                pipe = f;
                startTimestamp = System.currentTimeMillis() / 1000L;
                return true;
            } catch (Exception ignored) {
                // this pipe index doesn't exist, or isn't Discord — try the next one
            }
        }
        return false;
    }

    /** Blocking — call off-thread. */
    public static synchronized void setActivity(String state, String details) {
        if (pipe == null) return;
        try {
            JsonObject timestamps = new JsonObject();
            timestamps.addProperty("start", startTimestamp);

            JsonObject assets = new JsonObject();
            assets.addProperty("large_image", "lume_logo");
            assets.addProperty("large_text", "Lume Client");

            JsonObject activity = new JsonObject();
            activity.addProperty("state", state);
            activity.addProperty("details", details);
            activity.add("timestamps", timestamps);
            activity.add("assets", assets);

            JsonObject args = new JsonObject();
            args.addProperty("pid", ProcessHandle.current().pid());
            args.add("activity", activity);

            JsonObject frame = new JsonObject();
            frame.addProperty("cmd", "SET_ACTIVITY");
            frame.add("args", args);
            frame.addProperty("nonce", UUID.randomUUID().toString());

            writeFrame(pipe, 1, frame.toString());
        } catch (Exception e) {
            disconnect();   // pipe almost certainly closed (Discord quit) — reset so a later tick reconnects
        }
    }

    public static synchronized void disconnect() {
        if (pipe == null) return;
        try { pipe.close(); } catch (Exception ignored) {}
        pipe = null;
    }

    private static void writeFrame(RandomAccessFile f, int opcode, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        byte[] header = new byte[8];
        putIntLE(header, 0, opcode);
        putIntLE(header, 4, body.length);
        f.write(header);
        f.write(body);
    }

    private static void readFrame(RandomAccessFile f) throws IOException {
        byte[] header = new byte[8];
        f.readFully(header);
        int len = getIntLE(header, 4);
        byte[] body = new byte[len];
        f.readFully(body);
    }

    private static void putIntLE(byte[] b, int off, int v) {
        b[off] = (byte) v; b[off + 1] = (byte) (v >> 8); b[off + 2] = (byte) (v >> 16); b[off + 3] = (byte) (v >> 24);
    }

    private static int getIntLE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8) | ((b[off + 2] & 0xFF) << 16) | ((b[off + 3] & 0xFF) << 24);
    }
}
