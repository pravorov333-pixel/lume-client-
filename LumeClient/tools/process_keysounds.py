"""Trims the key-release tail off each recorded switch sample (keep only the press
transient) and synthesizes a new "pleasant" style-4 click that isn't a mechanical
recording at all. Run from anywhere; writes into the live game's keysounds folder AND
mirrors into LumeClient's own repo copy (tools/keysounds_src/) for safekeeping.
"""
import os
import numpy as np
import soundfile as sf

APPDATA = os.environ["APPDATA"]
LIVE_DIR = os.path.join(APPDATA, ".lumeclient", "profiles", "1.21.4", "lume", "custom_sounds", "keysounds")
BACKUP_DIR = os.path.join(os.path.dirname(__file__), "keysounds_src")
DOWNLOADS = os.path.join(os.path.expanduser("~"), "Downloads")
SOURCE_MP3 = {
    "1_click.ogg": "1 click.mp3", "1_space.ogg": "1 space.mp3",
    "2_click.ogg": "2 click.mp3", "2_space.ogg": "2 space.mp3",
    "3_click.ogg": "3 click.mp3", "3_space.ogg": "3 space.mp3",
}
os.makedirs(BACKUP_DIR, exist_ok=True)


def envelope_segments(mono, sr, win_s=0.002, rel_thresh=0.06, gap_ms=35):
    win = max(1, int(sr * win_s))
    n = len(mono) // win
    if n == 0:
        return []
    env = np.array([np.sqrt(np.mean(mono[i * win:(i + 1) * win] ** 2)) for i in range(n)])
    peak = env.max()
    if peak <= 0:
        return []
    above = np.where(env > peak * rel_thresh)[0]
    if len(above) == 0:
        return []
    gap_windows = max(1, int(gap_ms / 1000 * sr / win))
    segs = []
    start = above[0]
    prev = above[0]
    for idx in above[1:]:
        if idx - prev > gap_windows:
            segs.append((start, prev))
            start = idx
        prev = idx
    segs.append((start, prev))
    return [(s * win, e * win) for s, e in segs]  # sample indices


def to_stereo(data):
    if data.ndim == 1:
        return np.stack([data, data], axis=1)
    return data


def trim_release(path, out_path, tail_ms=110, preroll_ms=4, fade_ms=12):
    data, sr = sf.read(path, always_2d=False)
    mono = data.mean(axis=1) if data.ndim > 1 else data

    segs = envelope_segments(mono, sr)
    if not segs:
        print(f"  {os.path.basename(path)}: no signal found, leaving untouched")
        sf.write(out_path, to_stereo(data), sr, format="OGG", subtype="VORBIS")
        return

    press_start, press_end = segs[0]
    next_onset = segs[1][0] if len(segs) > 1 else len(mono)

    preroll = int(sr * preroll_ms / 1000)
    tail = int(sr * tail_ms / 1000)
    cut_start = max(0, press_start - preroll)
    cut_end = min(len(mono), press_end + tail, next_onset - int(sr * 0.015))
    cut_end = max(cut_end, press_end + int(sr * 0.02))  # always keep at least a short natural decay

    clip = data[cut_start:cut_end]
    if clip.ndim == 1:
        clip_mono = clip
    else:
        clip_mono = clip.mean(axis=1)

    # de-click fade-out over the last `fade_ms`
    fade_n = min(len(clip), int(sr * fade_ms / 1000))
    if fade_n > 0:
        fade = np.linspace(1.0, 0.0, fade_n)
        if clip.ndim == 1:
            clip[-fade_n:] *= fade
        else:
            clip[-fade_n:] *= fade[:, None]

    # tame the two clipping style-3 recordings (peak > 1.0 → digital clipping distortion)
    peak = np.abs(clip).max()
    if peak > 0.98:
        clip = clip * (0.92 / peak)

    clip = to_stereo(clip)
    sf.write(out_path, clip, sr, format="OGG", subtype="VORBIS")
    old_dur = len(mono) / sr
    new_dur = len(clip) / sr
    print(f"  {os.path.basename(path)}: {old_dur:.3f}s -> {new_dur:.3f}s (press {press_start/sr:.3f}-{press_end/sr:.3f}s, cut at {cut_end/sr:.3f}s)")


def synth_pleasant(freq, dur_s, sr=44100):
    """Soft UI 'pop' — quick pitch-down sine + a touch of filtered noise for texture,
    fast attack / short exponential decay. Not a keyboard-switch recording."""
    n = int(sr * dur_s)
    t = np.arange(n) / sr
    decay = np.exp(-t / (dur_s * 0.28))
    pitch = freq * np.exp(-t / (dur_s * 0.55)) + freq * 0.6
    tone = np.sin(2 * np.pi * np.cumsum(pitch) / sr)
    # short high-freq tick right at onset for a bit of "attack" definition
    tick_n = int(sr * 0.004)
    tick = np.zeros(n)
    if tick_n > 0:
        tick_env = np.exp(-np.arange(tick_n) / (tick_n * 0.3))
        tick[:tick_n] = (np.random.rand(tick_n) * 2 - 1) * tick_env * 0.25
    sig = tone * decay * 0.5 + tick
    attack_n = min(n, int(sr * 0.0015))
    if attack_n > 0:
        sig[:attack_n] *= np.linspace(0, 1, attack_n)
    sig = sig / max(1e-6, np.abs(sig).max()) * 0.5
    return sig.astype(np.float64)


def main():
    print("Trimming release tail from styles 1-3 (re-processed from the original MP3s):")
    for name, mp3_name in SOURCE_MP3.items():
        src = os.path.join(DOWNLOADS, mp3_name)
        out = os.path.join(LIVE_DIR, name)
        if not os.path.isfile(src):
            print(f"  MISSING SOURCE: {mp3_name}")
            continue
        trim_release(src, out)

    print("Synthesizing style 4 (pleasant, non-mechanical click):")
    sr = 44100
    click = synth_pleasant(freq=1400, dur_s=0.07, sr=sr)
    space = synth_pleasant(freq=950, dur_s=0.10, sr=sr)
    sf.write(os.path.join(LIVE_DIR, "4_click.ogg"), to_stereo(click), sr, format="OGG", subtype="VORBIS")
    sf.write(os.path.join(LIVE_DIR, "4_space.ogg"), to_stereo(space), sr, format="OGG", subtype="VORBIS")
    print("  wrote 4_click.ogg / 4_space.ogg")

    import shutil
    for name in os.listdir(LIVE_DIR):
        if name.endswith(".ogg"):
            shutil.copy(os.path.join(LIVE_DIR, name), os.path.join(BACKUP_DIR, name))
    print(f"Mirrored into {BACKUP_DIR}")


if __name__ == "__main__":
    main()
