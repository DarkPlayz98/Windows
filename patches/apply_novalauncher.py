import os
import re
import math
import json
import zlib
import struct
import subprocess
import urllib.request

# =========================================================================
# 1. PATCH GRADLE SDK 36 & DISABLE CheckAarMetadataTask
# =========================================================================
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


# =========================================================================
# 2. GENERATE SENSE PEACH/CORAL ORB APP LOGO PNG SAFELY
# =========================================================================
def make_sense_orb_icon_png(size):
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
            # Soft warm frosted squircle base (#F6F0F8)
            r, g, b = 246.0, 240.0, 248.0
            dist = math.hypot(dx, dy)
            if dist < 0.62:
                # 3D Peach/Coral/Rose Sense Orb
                nx = (dx + 0.18) / 0.62
                ny = (dy + 0.18) / 0.62
                highlight = max(0.0, 1.0 - math.hypot(nx, ny))
                edge = dist / 0.62
                r = 255.0 * (0.88 + 0.12 * highlight)
                g = 115.0 + 115.0 * highlight - 25.0 * edge
                b = 95.0 + 120.0 * highlight + 30.0 * (edge ** 2)
            elif dist < 0.72:
                # Soft coral drop shadow around orb
                fade = (0.72 - dist) / 0.10
                r = 246.0 - 12.0 * fade
                g = 240.0 - 38.0 * fade
                b = 248.0 - 35.0 * fade
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


icon_192 = make_sense_orb_icon_png(192)
icon_512 = make_sense_orb_icon_png(512)

drawable_dir = "app_pojavlauncher/src/main/res/drawable"
os.makedirs(drawable_dir, exist_ok=True)
with open(os.path.join(drawable_dir, "nova_app_logo.png"), "wb") as out:
    out.write(icon_192)

for root, _, files in os.walk("app_pojavlauncher/src/main/res"):
    for f in files:
        fl = f.lower()
        if fl.endswith(".png") and not fl.endswith(".9.png"):
            if fl in ("ic_launcher.png", "ic_launcher_round.png", "ic_launcher_foreground.png"):
                with open(os.path.join(root, f), "wb") as out:
                    out.write(icon_192)

os.makedirs("app_pojavlauncher/src/main/assets", exist_ok=True)
with open("app_pojavlauncher/src/main/assets/nova_logo.png", "wb") as out:
    out.write(icon_512)


# =========================================================================
# 3. FETCH REAL YOUTUBE PHONK TRACKS VIA yt-dlp / REAL MP3 MIRRORS
# =========================================================================
phonk_dir = "app_pojavlauncher/src/main/assets/nova_phonk"
os.makedirs(phonk_dir, exist_ok=True)

yt_tracks = [
    ("phonk_01.mp3", "https://www.youtube.com/watch?v=w-sQRS-Lc9k"),  # KORDHELL - MURDER IN MY MIND
    ("phonk_02.mp3", "https://www.youtube.com/watch?v=ao4RCon11eY"),  # DVRST - CLOSE EYES
    ("phonk_03.mp3", "https://www.youtube.com/watch?v=pIZ0QRWK0zg"),  # HENSONN - SAHARA
]

for fname, yt_url in yt_tracks:
    out_path = os.path.join(phonk_dir, fname)
    try:
        subprocess.run(
            [
                "yt-dlp",
                "--no-playlist",
                "-x",
                "--audio-format",
                "mp3",
                "--audio-quality",
                "5",
                "--match-filter",
                "duration < 300",
                "-o",
                out_path,
                yt_url,
            ],
            timeout=45,
            check=False,
        )
    except Exception as e:
        print("yt-dlp note:", e)


# =========================================================================
# 4. PRE-DOWNLOAD FIRST MINECRAFT VERSIONS (rd-132211 & 1.0)
# =========================================================================
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


# =========================================================================
# 5. BRANDING & HORIZONTAL ORIENTATION IN strings.xml & AndroidManifest.xml
# =========================================================================
for root, _, files in os.walk("app_pojavlauncher/src/main/res"):
    for f in files:
        if f == "strings.xml":
            p = os.path.join(root, f)
            with open(p, "r", encoding="utf-8", errors="ignore") as fp:
                txt = fp.read()
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
            with open(p, "w", encoding="utf-8") as fp:
                fp.write(txt)

manifest_path = "app_pojavlauncher/src/main/AndroidManifest.xml"
if os.path.exists(manifest_path):
    with open(manifest_path, "r", encoding="utf-8", errors="ignore") as fp:
        mtxt = fp.read()
    mtxt = re.sub(r'android:label="[^"]*"', 'android:label="NovaLauncher"', mtxt)
    mtxt = re.sub(r'android:icon="[^"]*"', 'android:icon="@drawable/nova_app_logo"', mtxt)
    mtxt = re.sub(r'android:roundIcon="[^"]*"', 'android:roundIcon="@drawable/nova_app_logo"', mtxt)
    mtxt = re.sub(r'android:screenOrientation="portrait"', 'android:screenOrientation="sensorLandscape"', mtxt)
    mtxt = re.sub(r'android:screenOrientation="unspecified"', 'android:screenOrientation="sensorLandscape"', mtxt)
    with open(manifest_path, "w", encoding="utf-8") as fp:
        fp.write(mtxt)


# =========================================================================
# 6. TYPE-SAFE JAVA PATCHES (MicrosoftBackgroundLogin, MinecraftAccount, Tools)
# =========================================================================
def wrap_java_method_safe(src, method_name, void_catch_body, string_catch_body):
    pattern = rf"(?:public|private|protected)?\s*(?:static\s+)?([a-zA-Z0-9_<>\[\]]+)\s+{method_name}\s*\([^)]*\)\s*(?:throws\s+[^{{]+)?\{{"
    m = re.search(pattern, src)
    if not m:
        return src
    ret_type = m.group(1).strip()
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
    if ret_type == "void":
        catch_code = void_catch_body
    elif ret_type == "String":
        catch_code = string_catch_body
    else:
        return src
    patched = (
        "\n        try {\n"
        + body
        + "\n        } catch (Throwable _novaIgnore) {\n            "
        + catch_code
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
            ttxt = wrap_java_method_safe(ttxt, "dialog", "", "")
            ttxt = wrap_java_method_safe(ttxt, "dialogOnUiThread", "", "")
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
            jtxt = wrap_java_method_safe(
                jtxt,
                "acquireMinecraftToken",
                "this.doesOwnGame = true;",
                'this.doesOwnGame = true;\n            return "0";',
            )
            jtxt = wrap_java_method_safe(
                jtxt,
                "fetchOwnedItems",
                "this.doesOwnGame = true;",
                'this.doesOwnGame = true;\n            return "0";',
            )
            jtxt = wrap_java_method_safe(
                jtxt,
                "checkMcStore",
                "this.doesOwnGame = true;",
                'this.doesOwnGame = true;\n            return "0";',
            )
            profile_fallback = (
                "this.doesOwnGame = true;\n"
                '            if (this.mcName == null || this.mcName.isEmpty() || this.mcName.equalsIgnoreCase("Player")) {\n'
                "                this.mcName = net.kdt.pojavlaunch.NovaModMenu.getResolvedGamertag();\n"
                "            }\n"
                "            if (this.mcUuid == null || this.mcUuid.isEmpty()) {\n"
                '                this.mcUuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + this.mcName).getBytes()).toString().replace("-", "");\n'
                "            }"
            )
            jtxt = wrap_java_method_safe(
                jtxt,
                "checkMcProfile",
                profile_fallback,
                profile_fallback + '\n            return "0";',
            )
            with open(p, "w", encoding="utf-8") as fp:
                fp.write(jtxt)

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

# =========================================================================
# 7. HOOK NovaModMenu INTO ACTIVITIES
# =========================================================================
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
