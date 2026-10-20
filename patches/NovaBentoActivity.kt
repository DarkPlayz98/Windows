import os
import re
import urllib.request

print("Starting NovaLauncher Engine Patch & Asset Download...")

# 1. Download REAL YouTube Phonk Tracks
phonk_dir = "app/src/main/assets/nova_phonk"
os.makedirs(phonk_dir, exist_ok=True)

real_phonk_tracks = [
    ("phonk_01.mp3", "https://ia801509.us.archive.org/10/items/kordhell-murder-in-my-mind/KORDHELL%20-%20MURDER%20IN%20MY%20MIND.mp3"),
    ("phonk_02.mp3", "https://ia802302.us.archive.org/27/items/dvrst-close-eyes_202204/DVRST%20-%20Close%20Eyes.mp3"),
    ("phonk_03.mp3", "https://ia601405.us.archive.org/19/items/hensonn-sahara_202205/Hensonn%20-%20Sahara.mp3")
]

for fname, url in real_phonk_tracks:
    out_path = os.path.join(phonk_dir, fname)
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=30) as r, open(out_path, "wb") as f:
            f.write(r.read())
        print(f"Downloaded real Phonk MP3: {fname}")
    except Exception as e:
        print(f"Failed to download {fname}: {e}")

# 2. Patch Kotlin Source (Rebranding & Auth Unlock)
for root, _, files in os.walk("app/src/main/java"):
    for file in files:
        if file.endswith(".kt") or file.endswith(".java"):
            filepath = os.path.join(root, file)
            with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
                content = f.read()
            
            orig = content
            content = content.replace("Zalith Launcher 2", "NovaLauncher")
            content = content.replace("Zalith Launcher", "NovaLauncher")
            content = content.replace("ZalithLauncher2", "NovaLauncher")
            
            # Unlock Offline / Bedrock Login in Zalith Engine
            content = re.sub(r'(fun\s+[A-Za-z0-9_]*[Oo]ffline[A-Za-z0-9_]*\s*\([^)]*\)\s*:\s*Boolean\s*)\{[^}]*\}', r'\1{ return true }', content)
            content = re.sub(r'(fun\s+[A-Za-z0-9_]*[Oo]wn[A-Za-z0-9_]*\s*\([^)]*\)\s*:\s*Boolean\s*)\{[^}]*\}', r'\1{ return true }', content)
            content = re.sub(r'(fun\s+[A-Za-z0-9_]*[Cc]heck[A-Za-z0-9_]*\s*\([^)]*\)\s*:\s*Boolean\s*)\{[^}]*\}', r'\1{ return true }', content)
            
            if content != orig:
                with open(filepath, "w", encoding="utf-8") as f:
                    f.write(content)

# 3. Reroute AndroidManifest.xml to boot NovaBentoActivity
manifest_path = "app/src/main/AndroidManifest.xml"
if os.path.exists(manifest_path):
    with open(manifest_path, "r", encoding="utf-8") as f:
        m_txt = f.read()
    
    # Rebrand App Name
    m_txt = re.sub(r'android:label="[^"]*"', 'android:label="NovaLauncher"', m_txt)
    
    # Strip LAUNCHER category from Zalith's MainActivity
    m_txt = m_txt.replace('<category android:name="android.intent.category.LAUNCHER" />', '')
    
    # Register NovaBentoActivity as the true Launcher
    bento_activity = """
        <activity
            android:name="net.kdt.pojavlaunch.NovaBentoActivity"
            android:screenOrientation="sensorLandscape"
            android:theme="@android:style/Theme.NoTitleBar.Fullscreen"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    """
    m_txt = m_txt.replace('</application>', bento_activity + '\n</application>')
    
    with open(manifest_path, "w", encoding="utf-8") as f:
        f.write(m_txt)
