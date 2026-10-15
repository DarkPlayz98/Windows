import os
import re
import math
import wave
import json
import zlib
import struct
import shutil
import urllib.request

# 1. Patch Gradle SDK 36, Disable CheckAarMetadata, and Strip Amethyst/Pojav
for root, _, files in os.walk("."):
    for f in files:
        if f in ("build.gradle", "build.gradle.kts"):
            p = os.path.join(root, f)
            with open(p, "r", encoding="utf-8", errors="ignore") as fp:
                txt = fp.read()
            txt = re.sub(r"compileSdk\s*=\s*37", "compileSdk = 36", txt)
            txt = re.sub(r"compileSdkVersion\s+37", "compileSdkVersion 36", txt)
            txt = re.sub(r"targetSdk\s*=\s*37", "targetSdk = 36", txt)
            txt = re.sub(r"targetSdkVersion\s+37", "targetSdkVersion 36", txt)
            txt = re.sub(
                r'resValue\s*\(\s*"string"\s*,\s*"app_name"\s*,\s*"[^"]*"\s*\)',
                'resValue("string", "app_name", "NovaLauncher")',
                txt,
            )
            txt = re.sub(r"Amethyst\s*\(Debug\)", "NovaLauncher", txt, flags=re.IGNORECASE)
            txt = re.sub(r"Amethyst\s+Debug", "NovaLauncher", txt, flags=re.IGNORECASE)
            txt = re.sub(r'"Amethyst"', '"NovaLauncher"', txt)
            txt = re.sub(r'"PojavLauncher"', '"NovaLauncher"', txt)
            if "app_pojavlauncher" in p and "AarMetadata" not in txt:
                txt += (
                    "\n\ntasks.configureEach { task ->\n"
                    '    if (task.name.contains("AarMetadata")) {\n'
                    "        task.enabled = false\n"
                    "    }\n"
                    "}\n"
                )
            with open(p, "w", encoding="utf-8") as fp:
                fp.write(txt)


# 2. Generate Custom NovaLauncher Logo PNG & Replace All Icons
def make_nova_icon_png(size):
    raw_rows = []
    cx = (size - 1) / 2.0
    cy = (size - 1) / 2.0
    r_max = size / 2.0
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            dx = (x - cx) / r_max
            dy = (y - cy) / r_max
            sdist = (abs(dx) ** 4 + abs(dy) ** 4) ** 0.25
            if sdist > 0.95:
                row.extend((0, 0, 0, 0))
                continue
            dist = math.hypot(dx, dy)
            angle = math.atan2(dy, dx)
            star = (abs(math.cos(angle * 2.0)) ** 6) * max(0.0, 1.0 - dist * 1.15)
            orb = math.exp(-dist * dist * 5.5)
            ring = math.exp(-((dist - 0.58) ** 2) * 90.0)
            border = 1.0 if sdist > 0.90 else 0.0
            r = 11.0 + 95.0 * orb + 130.0 * star + 99.0 * ring + 60.0 * border
            g = 12.0 + 70.0 * orb + 190.0 * star + 102.0 * ring + 65.0 * border
            b = 18.0 + 210.0 * orb + 255.0 * star + 241.0 * ring + 90.0 * border
            if dist < 0.22:
                core = (0.22 - dist) / 0.22
                r += core * 180.0
                g += core * 180.0
                b += core * 180.0
            row.extend(
                (
                    min(255, max(0, int(r))),
                    min(255, max(0, int(g))),
                    min(255, max(0, int(b))),
                    255,
                )
            )
        raw_rows.append(bytes(row))
    raw_data = b"".join(raw_rows)

    def chunk(ctype, data):
        return (
            struct.pack(">I", len(data))
            + ctype
            + data
            + struct.pack(">I", zlib.crc32(ctype + data) & 0xFFFFFFFF)
        )

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw_data, 9))
        + chunk(b"IEND", b"")
    )


icon_192 = make_nova_icon_png(192)
icon_512 = make_nova_icon_png(512)

for root, dirs, files in os.walk("app_pojavlauncher/src"):
    if os.path.basename(root).startswith("mipmap-anydpi"):
        shutil.rmtree(root, ignore_errors=True)
        continue
    for f in files:
        fl = f.lower()
        p = os.path.join(root, f)
        if (fl.endswith(".png") or fl.endswith(".webp")) and (
            "ic_launcher" in fl or "pojav" in fl or "amethyst" in fl or "logo" in fl
        ):
            with open(p, "wb") as out:
                out.write(icon_192)

os.makedirs("app_pojavlauncher/src/main/assets", exist_ok=True)
with open("app_pojavlauncher/src/main/assets/nova_logo.png", "wb") as out:
    out.write(icon_512)


# 3. Synthesize 3 Drift Phonk Tracks into assets/nova_phonk/
phonk_dir = "app_pojavlauncher/src/main/assets/nova_phonk"
os.makedirs(phonk_dir, exist_ok=True)


def generate_phonk_wav(filepath, bpm, melody_freqs, bass_freq):
    sr = 22050
    step_sec = (60.0 / bpm) / 4.0
    total_steps = 64
    total_samples = int(sr * step_sec * total_steps)
    buf = bytearray()
    for i in range(total_samples):
        t = i / float(sr)
        step = int(t / step_sec) % total_steps
        step_pos = (t % step_sec) / step_sec
        b_freq = bass_freq if (step // 16) % 2 == 0 else bass_freq * 1.189
        bass_env = max(0.0, 1.0 - step_pos * 0.45)
        bass_raw = math.sin(2.0 * math.pi * b_freq * t) * 2.4
        bass = max(-0.85, min(0.85, bass_raw)) * bass_env * 0.50
        cb_freq = melody_freqs[step % len(melody_freqs)]
        cb = 0.0
        if cb_freq > 0:
            cb_env = math.exp(-step_pos * 6.5)
            w1 = 1.0 if math.sin(2.0 * math.pi * cb_freq * t) >= 0 else -1.0
            w2 = 1.0 if math.sin(2.0 * math.pi * (cb_freq * 1.481) * t) >= 0 else -1.0
            cb = (w1 + w2) * 0.5 * cb_env * 0.32
        kick = 0.0
        if (step % 16) in (0, 6, 10):
            k_env = math.exp(-step_pos * 14.0)
            k_freq = 130.0 * math.exp(-step_pos * 22.0) + 42.0
            kick = math.sin(2.0 * math.pi * k_freq * (t % step_sec)) * k_env * 0.55
        if (step % 8) == 4:
            s_env = math.exp(-step_pos * 16.0)
            noise = ((i * 1103515245 + 12345) & 0x7FFFFFFF) / 2147483647.0 * 2.0 - 1.0
            snare = noise * s_env * 0.35
        else:
            h_env = math.exp(-step_pos * 38.0)
            noise = ((i * 214013 + 2531011) & 0x7FFFFFFF) / 2147483647.0 * 2.0 - 1.0
            snare = noise * h_env * 0.12
        sample = max(-0.98, min(0.98, bass + cb + kick + snare))
        buf.extend(struct.pack("<h", int(sample * 32000)))
    with wave.open(filepath, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes(bytes(buf))


generate_phonk_wav(
    os.path.join(phonk_dir, "01_nova_drift_160bpm.wav"),
    160,
    [523.25, 0, 523.25, 622.25, 523.25, 0, 698.46, 622.25, 523.25, 0, 523.25, 622.25, 466.16, 0, 493.88, 0],
    55.0,
)
generate_phonk_wav(
    os.path.join(phonk_dir, "02_midnight_cowbell_152bpm.wav"),
    152,
    [440.0, 440.0, 0, 523.25, 440.0, 0, 587.33, 523.25, 440.0, 0, 392.0, 440.0, 349.23, 0, 392.0, 0],
    49.0,
)
generate_phonk_wav(
    os.path.join(phonk_dir, "03_shadow_808_168bpm.wav"),
    168,
    [587.33, 0, 698.46, 587.33, 783.99, 0, 698.46, 587.33, 523.25, 0, 587.33, 523.25, 440.0, 0, 523.25, 0],
    61.7,
)


# 4. Pre-download First Minecraft Versions (rd-132211 & 1.0)
assets_ver_dir = "app_pojavlauncher/src/main/assets/novacraft_versions"
os.makedirs(assets_ver_dir, exist_ok=True)
try:
    manifest_url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    with urllib.request.urlopen(manifest_url, timeout=30) as resp:
        manifest = json.loads(resp.read().decode("utf-8"))
    for v in manifest.get("versions", []):
        vid = v.get("id")
        if vid in ("rd-132211", "1.0"):
            vdir = os.path.join(assets_ver_dir, vid)
            os.makedirs(vdir, exist_ok=True)
            vjson_path = os.path.join(vdir, f"{vid}.json")
            with urllib.request.urlopen(v["url"], timeout=30) as vresp:
                vdata_raw = vresp.read()
            with open(vjson_path, "wb") as out:
                out.write(vdata_raw)
            vdata = json.loads(vdata_raw.decode("utf-8"))
            client_url = vdata.get("downloads", {}).get("client", {}).get("url")
            if client_url:
                urllib.request.urlretrieve(client_url, os.path.join(vdir, f"{vid}.jar"))
except Exception as e:
    print("Warning: Could not pre-download version:", e)


# 5. Strip Amethyst / Pojav from XMLs & Force Horizontal (sensorLandscape) in Manifest
for root, _, files in os.walk("app_pojavlauncher/src"):
    for f in files:
        if f.endswith(".xml"):
            p = os.path.join(root, f)
            with open(p, "r", encoding="utf-8", errors="ignore") as fp:
                txt = fp.read()
            orig = txt
            txt = re.sub(
                r'<string name="app_name"[^>]*>.*?</string>',
                '<string name="app_name">NovaLauncher</string>',
                txt,
                flags=re.DOTALL,
            )
            txt = re.sub(
                r'<string name="app_short_name"[^>]*>.*?</string>',
                '<string name="app_short_name">NovaLauncher</string>',
                txt,
                flags=re.DOTALL,
            )
            txt = re.sub(r"Amethyst\s*\(Debug\)", "NovaLauncher", txt, flags=re.IGNORECASE)
            txt = re.sub(r"Amethyst\s+Debug", "NovaLauncher", txt, flags=re.IGNORECASE)
            txt = re.sub(r"Angel\s*Aura\s*Amethyst", "NovaLauncher", txt, flags=re.IGNORECASE)
            txt = re.sub(r"PojavLauncher", "NovaLauncher", txt, flags=re.IGNORECASE)
            txt = re.sub(r"\bAmethyst\b", "NovaLauncher", txt)
            if f == "AndroidManifest.xml":
                txt = re.sub(r'android:label="[^"]*"', 'android:label="NovaLauncher"', txt)
                txt = re.sub(
                    r'android:screenOrientation="portrait"',
                    'android:screenOrientation="sensorLandscape"',
                    txt,
                )
                txt = re.sub(
                    r'android:screenOrientation="unspecified"',
                    'android:screenOrientation="sensorLandscape"',
                    txt,
                )
            if txt != orig:
                with open(p, "w", encoding="utf-8") as fp:
                    fp.write(txt)


# 6. Patch Tools.java, MicrosoftBackgroundLogin.java, mcAccountSpinner.java, MinecraftAccount.java
def wrap_method_any(src, method_name, fallback_code):
    pattern = rf"(?:public|private|protected)?\s*(?:static\s+)?[a-zA-Z0-9_<>\[\]]+\s+{method_name}\s*\([^)]*\)\s*(?:throws\s+[^{{]+)?\{{"
    m = re.search(pattern, src)
    if not m:
        return src
    brace_start = src.find("{", m.end() - 1)
    if brace_start == -1:
        return src
    depth = 0
    end = -1
    for i in range(brace_start, len(src)):
        if src[i] == "{":
            depth += 1
        elif src[i] == "}":
            depth -= 1
            if depth == 0:
                end = i
                break
    if end == -1:
        return src
    body = src[brace_start + 1 : end]
    patched = (
        "\n        try {\n"
        + body
        + "\n        } catch (Throwable _novaIgnore) {\n            "
        + fallback_code
        + "\n        }\n    "
    )
    return src[: brace_start + 1] + patched + src[end:]


java_root = "app_pojavlauncher/src/main/java"
for root, _, files in os.walk(java_root):
    for f in files:
        p = os.path.join(root, f)

        if f == "Tools.java":
            with open(p, "r", encoding="utf-8", errors="ignore") as fp:
                ttxt = fp.read()
            ttxt = re.sub(
                r"\b([a-zA-Z0-9_]+)\.show\s*\(\s*\)\s*;",
                r"try { \1.show(); } catch (Throwable _badTokenIgnored) {}",
                ttxt,
            )
            ttxt = ttxt.replace("PojavLauncher", "NovaLauncher").replace("Amethyst", "NovaLauncher")
            with open(p, "w", encoding="utf-8") as fp:
                fp.write(ttxt)

        elif f == "MicrosoftBackgroundLogin.java":
            with open(p, "r", encoding="utf-8", errors="ignore") as fp:
                jtxt = fp.read()

            jtxt = jtxt.replace("public boolean doesOwnGame;", "public boolean doesOwnGame = true;")
            jtxt = jtxt.replace("doesOwnGame = false;", "doesOwnGame = true;")
            jtxt = jtxt.replace("doesOwnGame = false", "doesOwnGame = true")
            jtxt = re.sub(
                r'mcName\s*=\s*"Player"\s*;',
                "mcName = net.kdt.pojavlaunch.NovaModMenu.getResolvedGamertag();",
                jtxt,
            )
            jtxt = re.sub(
                r"(acquireXsts\s*\(\s*String\s+([a-zA-Z0-9_]+)\s*\)\s*(?:throws\s+[^{]+)?\{)",
                r"\1\n        net.kdt.pojavlaunch.NovaModMenu.captureXboxGamertag(\2);",
                jtxt,
                count=1,
            )
            jtxt = wrap_method_any(
                jtxt,
                "acquireMinecraftToken",
                'this.doesOwnGame = true;\n            if (this.mcToken == null) this.mcToken = "0";',
            )
            jtxt = wrap_method_any(jtxt, "fetchOwnedItems", "this.doesOwnGame = true;")
            jtxt = wrap_method_any(jtxt, "checkMcStore", "this.doesOwnGame = true;")
            jtxt = wrap_method_any(
                jtxt,
                "checkMcProfile",
                'this.doesOwnGame = true;\n            if (this.mcName == null || this.mcName.isEmpty() || this.mcName.equalsIgnoreCase("Player")) this.mcName = net.kdt.pojavlaunch.NovaModMenu.getResolvedGamertag();\n            if (this.mcUuid == null || this.mcUuid.isEmpty()) this.mcUuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + this.mcName).getBytes()).toString().replace("-", "");',
            )
            jtxt = re.sub(
                r"([a-zA-Z0-9_]+)\.onLoginError\s*\(\s*([a-zA-Z0-9_]+)\s*\)\s*;",
                r"net.kdt.pojavlaunch.NovaModMenu.completeBedrockLoginFallback(this, doneListener, \1, \2);",
                jtxt,
            )
            with open(p, "w", encoding="utf-8") as fp:
                fp.write(jtxt)

        elif f == "mcAccountSpinner.java":
            with open(p, "r", encoding="utf-8", errors="ignore") as fp:
                stxt = fp.read()
            stxt = re.sub(
                r"Tools\.dialog\s*\([^;]+;",
                "try { \\g<0> } catch (Throwable _ignored) {}",
                stxt,
            )
            with open(p, "w", encoding="utf-8") as fp:
                fp.write(stxt)

        elif f == "MinecraftAccount.java":
            with open(p, "r", encoding="utf-8", errors="ignore") as fp:
                atxt = fp.read()
            atxt = re.sub(
                r"public\s+boolean\s+isDemo\s*\(\s*\)\s*\{[^}]*\}",
                "public boolean isDemo() { return false; }",
                atxt,
            )
            with open(p, "w", encoding="utf-8") as fp:
                fp.write(atxt)

# 7. Hook NovaModMenu into Activities
for act_name in ("LauncherActivity.java", "MainActivity.java", "TestStorageActivity.java"):
    for root, _, files in os.walk(java_root):
        if act_name in files:
            p = os.path.join(root, act_name)
            with open(p, "r", encoding="utf-8", errors="ignore") as fp:
                act_txt = fp.read()
            if "NovaModMenu.attach(this);" not in act_txt:
                act_txt = re.sub(
                    r"(super\.onCreate\s*\([^)]*\)\s*;)",
                    r"\1\n        net.kdt.pojavlaunch.NovaModMenu.attach(this);",
                    act_txt,
                    count=1,
                )
                with open(p, "w", encoding="utf-8") as fp:
                    fp.write(act_txt)
                print(f"Hooked NovaModMenu into {act_name}")
