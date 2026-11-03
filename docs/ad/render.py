"""Render kasaka-ad.html to an MP4.

Usage:  python render.py [--preview t1,t2,...]
Needs:  pip install playwright imageio-ffmpeg   (uses the installed Microsoft Edge; no browser download)
Inputs: timings.json + mix.wav from make_audio.py, in this folder.
"""
import json, pathlib, subprocess, sys, shutil
import imageio_ffmpeg
from playwright.sync_api import sync_playwright

HERE = pathlib.Path(__file__).parent
FPS = 30
FRAMES = HERE / "_frames"

tim = json.loads((HERE / "timings.json").read_text(encoding="utf-8"))
(HERE / "timings.js").write_text("window.TIMINGS = " + json.dumps(tim) + ";\n", encoding="utf-8")

preview = None
if len(sys.argv) > 2 and sys.argv[1] == "--preview":
    preview = [float(x) for x in sys.argv[2].split(",")]

with sync_playwright() as p:
    b = p.chromium.launch(channel="msedge", args=["--allow-file-access-from-files"])
    pg = b.new_page(viewport={"width": 720, "height": 1280})
    pg.goto((HERE / "kasaka-ad.html").as_uri())
    pg.evaluate("document.fonts.ready")
    pg.wait_for_timeout(500)
    if preview:
        out = HERE / "_preview"; out.mkdir(exist_ok=True)
        for t in preview:
            pg.evaluate(f"render({t})")
            pg.screenshot(path=str(out / f"t{t:05.1f}.png"))
        b.close(); print("preview done"); sys.exit()
    if FRAMES.exists(): shutil.rmtree(FRAMES)
    FRAMES.mkdir()
    n = int(tim["total"] * FPS)
    for i in range(n):
        pg.evaluate(f"render({i / FPS})")
        pg.screenshot(path=str(FRAMES / f"f{i:05d}.jpg"), type="jpeg", quality=92)
        if i % 300 == 0: print(f"{i}/{n}", flush=True)
    b.close()

ff = imageio_ffmpeg.get_ffmpeg_exe()
out = HERE / "Kasaka-Ad-NPK.mp4"
subprocess.run([ff, "-y", "-loglevel", "error", "-framerate", str(FPS), "-i", str(FRAMES / "f%05d.jpg"),
                "-i", str(HERE / "mix.wav"), "-c:v", "libx264", "-vf", "scale=in_range=pc:out_range=tv,format=yuv420p", "-crf", "20", "-preset", "medium",
                "-c:a", "aac", "-b:a", "160k", "-shortest", "-movflags", "+faststart", str(out)], check=True)
shutil.rmtree(FRAMES)
print("wrote", out)
