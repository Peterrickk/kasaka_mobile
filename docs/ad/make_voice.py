"""Generate the voiceover lines in vo/ with Kokoro, an offline neural voice (Apache-2.0, OK for commercial use).

Usage:  python make_voice.py [voice] [speed]       e.g.  python make_voice.py af_heart 0.92
        python make_voice.py --samples              one test sentence in several calm voices -> vo_samples/
Needs:  pip install kokoro-onnx soundfile, plus kokoro-v1.0.onnx and voices-v1.0.bin
        (github.com/thewh1teagle/kokoro-onnx, release "model-files-v1.0") in the folder named by KOKORO_DIR.
"""
import json, os, pathlib, sys
import soundfile as sf
from kokoro_onnx import Kokoro

HERE = pathlib.Path(__file__).parent
MODELS = pathlib.Path(os.environ.get("KOKORO_DIR", HERE / "kokoro"))
kokoro = Kokoro(str(MODELS / "kokoro-v1.0.onnx"), str(MODELS / "voices-v1.0.bin"))
lines = json.loads((HERE / "script.json").read_text(encoding="utf-8"))

# a few words are easier to say right when spelled the way they sound
SAY = {"Kasaka": "Kah-sah-kah", "SakaSense": "Sah-kah Sense", "pechay": "peh-chai", "Leyte": "Lay-tay"}
def spoken(text):
    for k, v in SAY.items(): text = text.replace(k, v)
    return text

if len(sys.argv) > 1 and sys.argv[1] == "--samples":
    out = HERE / "vo_samples"; out.mkdir(exist_ok=True)
    test = spoken("Your pechay leaves are turning pale. So don't guess. Test first. SakaSense and the Kasaka app.")
    for v in ["af_heart", "af_sarah", "bf_emma", "am_michael", "am_adam", "bm_george"]:
        a, sr = kokoro.create(test, voice=v, speed=0.92, lang="en-us" if v[0] == "a" else "en-gb")
        sf.write(out / f"{v}.wav", a, sr); print("sample", v)
    sys.exit()

voice = sys.argv[1] if len(sys.argv) > 1 else "af_heart"
speed = float(sys.argv[2]) if len(sys.argv) > 2 else 0.92
vo = HERE / "vo"; vo.mkdir(exist_ok=True)
for f in vo.glob("l*.*"): f.unlink()
for i, l in enumerate(lines):
    a, sr = kokoro.create(spoken(l["text"]), voice=voice, speed=speed, lang="en-us" if voice[0] == "a" else "en-gb")
    sf.write(vo / f"l{i:02d}.wav", a, sr)
    print(f"l{i:02d}", round(len(a) / sr, 2), "s", l["text"])
