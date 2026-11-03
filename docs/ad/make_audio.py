"""Trim each voice line, lay them on a timeline, add a soft music bed, write timings.json + mix.wav."""
import json, wave, pathlib
import numpy as np

AD = pathlib.Path(__file__).parent
REPO = AD  # script.json, vo/, mix.wav and timings.json all live in this folder
SR = 44100
lines = json.loads((REPO / "script.json").read_text(encoding="utf-8"))

def load(i):
    """Read vo/lNN.<any audio type> (wav, m4a, mp3...) as mono 44.1 kHz, trim silence, even out loudness."""
    import subprocess, imageio_ffmpeg
    src = sorted((AD / "vo").glob(f"l{i:02d}.*"))
    assert src, f"missing vo/l{i:02d}.wav (or .m4a/.mp3)"
    raw = subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), "-loglevel", "error", "-i", str(src[0]),
                          "-ac", "1", "-ar", str(SR), "-f", "s16le", "-"], capture_output=True, check=True).stdout
    a = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768
    a = a - a.mean()
    thr = 0.04 * np.abs(a).max()          # relative, so phone noise floor doesn't count as speech
    idx = np.where(np.abs(a) > thr)[0]
    a = a[max(idx[0] - 2200, 0): idx[-1] + 4400]
    rms = np.sqrt(np.mean(a[np.abs(a) > thr] ** 2))
    return np.clip(a * (0.12 / rms), -1, 1) if rms > 0 else a

INTRO = 0.5          # silence before the first line
GAP = 0.42           # pause between lines in the same scene (calm pacing)
SCENE_GAP = 0.75     # pause when the scene changes
OUTRO = 2.6          # hold on the end card

t = INTRO
clips, timings = [], []
for i, l in enumerate(lines):
    a = load(i)
    if i and lines[i - 1]["scene"] != l["scene"]:
        t += SCENE_GAP - GAP
    d = len(a) / SR
    clips.append((t, a))
    timings.append({**l, "start": round(t, 3), "end": round(t + d, 3)})
    t += d + GAP
total = t - GAP + OUTRO

vo = np.zeros(int(total * SR) + SR, dtype=np.float32)
for s, a in clips:
    i = int(s * SR); vo[i:i + len(a)] += a

# ---- soft music bed: warm pad chords + gentle plucked arpeggio, 96 bpm
n = len(vo); tt = np.arange(n) / SR
music = np.zeros(n, dtype=np.float32)
beat = 60 / 96
chords = [[57, 60, 64], [53, 57, 60], [48, 52, 55], [55, 59, 62]]  # Am F C G (midi)
f = lambda m: 440 * 2 ** ((m - 69) / 12)
bar = beat * 4
for b in range(int(total / bar) + 2):
    ch = chords[b % 4]
    s0 = int(b * bar * SR); s1 = min(int((b + 1) * bar * SR), n)
    if s0 >= n: break
    seg = tt[s0:s1] - b * bar
    env = np.minimum(1, seg / 0.6) * np.minimum(1, (bar - seg) / 0.6)
    for m in ch:
        music[s0:s1] += 0.035 * env * (np.sin(2 * np.pi * f(m - 12) * seg) + 0.3 * np.sin(2 * np.pi * f(m) * seg))
    for k in range(8):  # eighth-note pluck arpeggio
        p0 = int((b * bar + k * beat / 2) * SR)
        if p0 >= n: break
        L = min(int(0.5 * SR), n - p0)
        st = np.arange(L) / SR
        m = ch[[0, 1, 2, 1][k % 4]] + 12
        music[p0:p0 + L] += 0.045 * np.exp(-st * 7) * np.sin(2 * np.pi * f(m) * st)

# duck the music under the voice
voice_env = np.convolve(np.abs(vo), np.ones(4410) / 4410, mode="same")
duck = 1 - 0.55 * np.clip(voice_env / 0.03, 0, 1)
fade = np.minimum(1, tt / 1.0) * np.clip((total - tt) / 1.5, 0, 1)
mix = vo + music * duck * fade
mix = mix / max(1.0, np.abs(mix).max() / 0.95)

with wave.open(str(AD / "mix.wav"), "w") as w:
    w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((mix * 32767).astype(np.int16).tobytes())
(AD / "timings.json").write_text(json.dumps({"total": round(total, 3), "lines": timings}, indent=1), encoding="utf-8")
print("total", round(total, 2), "s")
for x in timings: print(x["start"], x["end"], x["text"])
