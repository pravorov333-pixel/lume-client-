package com.lume.client.audio;

import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Plays arbitrary external .ogg files straight through OpenAL, bypassing Minecraft's
 * resource-pack-backed SoundManager entirely. This is what lets a user drop their own
 * sound file on disk (no resourcepack/jar rebuild) and have a module play it immediately.
 * Uses the SAME OpenAL context Minecraft already has open (LWJGL's AL10 calls are
 * process-global, not tied to a specific "handle" object) so no extra init is needed.
 */
public final class CustomAudioPlayer {

    private CustomAudioPlayer() {}

    private record Voice(int source, int buffer) {}
    private static final List<Voice> active = new ArrayList<>();

    /** Root folder for all drop-in custom sounds, one subfolder per module. Created eagerly. */
    public static Path folder(String moduleFolder) {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("lume/custom_sounds/" + moduleFolder);
        try { java.nio.file.Files.createDirectories(dir); } catch (Exception ignored) {}
        return dir;
    }

    /** Creates the folder (if missing) and drops a short instructions file for the user. */
    public static void ensureReadme(String moduleFolder) {
        Path dir = folder(moduleFolder);
        Path readme = dir.resolve("Положи сюда свой .ogg файл.txt");
        if (java.nio.file.Files.exists(readme)) return;
        try {
            java.nio.file.Files.writeString(readme,
                    "Положи сюда .ogg файл(ы) — они появятся в разделе \"My Sounds\" настроек модуля HitSound.\n" +
                    "Включи там \"Use My Sound\" и выбери нужный файл из списка (если он один — выбирать не надо).\n" +
                    "Формат: OGG Vorbis.\n");
        } catch (Exception ignored) {}
    }

    /** First .ogg file found in the given module's custom-sound folder, or null. */
    public static File find(String moduleFolder) {
        File[] files = list(moduleFolder);
        return files.length > 0 ? files[0] : null;
    }

    /** All .ogg files in the given module's custom-sound folder ("My Sounds" picker), name-sorted. */
    public static File[] list(String moduleFolder) {
        File dir = folder(moduleFolder).toFile();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".ogg"));
        if (files == null) return new File[0];
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        return files;
    }

    public static void play(File file, float volume, float pitch) {
        if (file == null || !file.isFile()) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channelsBuf = stack.mallocInt(1);
            IntBuffer sampleRateBuf = stack.mallocInt(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_filename(file.getAbsolutePath(), channelsBuf, sampleRateBuf);
            if (pcm == null) { System.out.println("[Lume] custom sound decode failed: " + file); return; }
            int channels = channelsBuf.get(0);
            int sampleRate = sampleRateBuf.get(0);
            int format = channels > 1 ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16;

            int buffer = AL10.alGenBuffers();
            AL10.alBufferData(buffer, format, pcm, sampleRate);
            MemoryUtil.memFree(pcm);

            int source = AL10.alGenSources();
            AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
            AL10.alSourcef(source, AL10.AL_GAIN, Math.max(0f, volume));
            AL10.alSourcef(source, AL10.AL_PITCH, Math.max(0.1f, pitch));
            AL10.alSourcePlay(source);
            active.add(new Voice(source, buffer));
        } catch (Throwable t) {
            System.out.println("[Lume] custom sound play failed: " + t);
        }
    }

    /** Frees finished voices — call once per client tick. */
    public static void tick() {
        active.removeIf(v -> {
            int state = AL10.alGetSourcei(v.source(), AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING) {
                AL10.alDeleteSources(v.source());
                AL10.alDeleteBuffers(v.buffer());
                return true;
            }
            return false;
        });
    }
}
