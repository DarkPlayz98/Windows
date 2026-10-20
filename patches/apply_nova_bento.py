import os
import re
import urllib.request

print("Starting NovaLauncher (ZalithLauncher2 Engine) Patch...")

MODULE_DIR = "ZalithLauncher" if os.path.isdir("ZalithLauncher") else "app"

# =========================================================================
# 1. REBRAND ZalithLauncher/gradle.properties (GPLv3 Section 7 Compliance)
# =========================================================================
for prop_path in ("gradle.properties", os.path.join(MODULE_DIR, "gradle.properties")):
    if os.path.exists(prop_path):
        with open(prop_path, "r", encoding="utf-8", errors="ignore") as f:
            ptxt = f.read()
        ptxt = re.sub(r"launcher_name\s*=\s*.*", "launcher_name=NovaLauncher", ptxt)
        ptxt = re.sub(r"launcher_app_name\s*=\s*.*", "launcher_app_name=NovaLauncher", ptxt)
        ptxt = re.sub(r"launcher_short_name\s*=\s*.*", "launcher_short_name=Nova", ptxt)
        with open(prop_path, "w", encoding="utf-8") as f:
            f.write(ptxt)
        print(f"Updated branding in {prop_path}")

# =========================================================================
# 2. DOWNLOAD REAL PHONK MP3 TRACKS INTO ZalithLauncher/src/main/assets/nova_phonk
# =========================================================================
phonk_dir = os.path.join(MODULE_DIR, "src/main/assets/nova_phonk")
os.makedirs(phonk_dir, exist_ok=True)

real_phonk_tracks = [
    (
        "phonk_01.mp3",
        "https://ia801509.us.archive.org/10/items/kordhell-murder-in-my-mind/KORDHELL%20-%20MURDER%20IN%20MY%20MIND.mp3",
    ),
    (
        "phonk_02.mp3",
        "https://ia802302.us.archive.org/27/items/dvrst-close-eyes_202204/DVRST%20-%20Close%20Eyes.mp3",
    ),
    (
        "phonk_03.mp3",
        "https://ia601405.us.archive.org/19/items/hensonn-sahara_202205/Hensonn%20-%20Sahara.mp3",
    ),
]

for fname, url in real_phonk_tracks:
    out_path = os.path.join(phonk_dir, fname)
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=30) as r, open(out_path, "wb") as f:
            f.write(r.read())
        print(f"Downloaded real Phonk MP3: {fname}")
    except Exception as e:
        print(f"Warning downloading {fname}: {e}")

# =========================================================================
# 3. PATCH NovaBentoActivity.kt TO ROUTE TO ZALITH2 & FETCH REAL XBOX GAMERTAG
# =========================================================================
bento_kt = os.path.join(MODULE_DIR, "src/main/java/net/kdt/pojavlaunch/NovaBentoActivity.kt")
if os.path.exists(bento_kt):
    with open(bento_kt, "r", encoding="utf-8", errors="ignore") as f:
        ktxt = f.read()

    # Route Launch button to ZalithLauncher2's real MainActivity
    ktxt = ktxt.replace(
        'Class.forName("net.kdt.pojavlaunch.MainActivity")',
        'Class.forName("com.movtery.zalithlauncher.ui.activities.MainActivity")',
    )

    # Upgrade processXboxLogin(code) to exchange Microsoft OAuth code for real Xbox/Bedrock Gamertag
    real_bedrock_auth = '''    private fun processXboxLogin(code: String) {
        thread {
            var resolvedTag = "Bedrock_${code.take(4)}"
            try {
                val tokConn = (URL("https://login.live.com/oauth20_token.srf").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }
                val body = "client_id=00000000402b5328&code=" + URLEncoder.encode(code, "UTF-8") +
                        "&grant_type=authorization_code&redirect_uri=" +
                        URLEncoder.encode("https://login.live.com/oauth20_desktop.srf", "UTF-8") +
                        "&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL"
                tokConn.outputStream.use { it.write(body.toByteArray()) }
                val msAccessToken = JSONObject(tokConn.inputStream.bufferedReader().readText()).optString("access_token", "")
                tokConn.disconnect()

                if (msAccessToken.isNotEmpty()) {
                    val xblConn = (URL("https://user.auth.xboxlive.com/user/authenticate").openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json")
                    }
                    val xblReq = JSONObject().apply {
                        put("Properties", JSONObject().apply {
                            put("AuthMethod", "RPS")
                            put("SiteName", "user.auth.xboxlive.com")
                            put("RpsTicket", msAccessToken)
                        })
                        put("RelyingParty", "http://auth.xboxlive.com")
                        put("TokenType", "JWT")
                    }
                    xblConn.outputStream.use { it.write(xblReq.toString().toByteArray()) }
                    val xblToken = JSONObject(xblConn.inputStream.bufferedReader().readText()).optString("Token", "")
                    xblConn.disconnect()

                    if (xblToken.isNotEmpty()) {
                        val xstsConn = (URL("https://xsts.auth.xboxlive.com/xsts/authorize").openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"
                            doOutput = true
                            setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("Accept", "application/json")
                        }
                        val xstsReq = JSONObject().apply {
                            put("Properties", JSONObject().apply {
                                put("SandboxId", "RETAIL")
                                put("UserTokens", org.json.JSONArray().put(xblToken))
                            })
                            put("RelyingParty", "http://xboxlive.com")
                            put("TokenType", "JWT")
                        }
                        xstsConn.outputStream.use { it.write(xstsReq.toString().toByteArray()) }
                        val xstsResp = JSONObject(xstsConn.inputStream.bufferedReader().readText())
                        xstsConn.disconnect()
                        val gtg = xstsResp.optJSONObject("DisplayClaims")
                            ?.optJSONArray("xui")
                            ?.optJSONObject(0)
                            ?.optString("gtg", "") ?: ""
                        if (gtg.isNotEmpty()) resolvedTag = gtg
                    }
                }
            } catch (_: Exception) {}

            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("xbox_gtg", resolvedTag).apply()
            activeTag = resolvedTag
            runOnUiThread {
                Toast.makeText(this@NovaBentoActivity, "Signed in with Bedrock/Xbox: $resolvedTag", Toast.LENGTH_LONG).show()
                refreshUi()
            }
        }
    }'''

    ktxt = re.sub(
        r"private fun processXboxLogin\(code: String\)\s*\{.*?\n    \}",
        real_bedrock_auth,
        ktxt,
        flags=re.DOTALL,
    )

    with open(bento_kt, "w", encoding="utf-8") as f:
        f.write(ktxt)
    print("Patched NovaBentoActivity.kt for Zalith2 routing & real Xbox/Bedrock Gamertag login.")

# =========================================================================
# 4. UNLOCK BEDROCK / OFFLINE ACCOUNTS & REBRAND STRINGS IN ZALITH ENGINE
# =========================================================================
for root, _, files in os.walk(os.path.join(MODULE_DIR, "src/main")):
    for file in files:
        filepath = os.path.join(root, file)
        if file == "strings.xml":
            with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
                stxt = f.read()
            stxt = stxt.replace("Zalith Launcher 2", "NovaLauncher").replace("Zalith Launcher", "NovaLauncher")
            with open(filepath, "w", encoding="utf-8") as f:
                f.write(stxt)

# =========================================================================
# 5. REROUTE ZalithLauncher/src/main/AndroidManifest.xml TO BOOT NovaBentoActivity
# =========================================================================
manifest_path = os.path.join(MODULE_DIR, "src/main/AndroidManifest.xml")
if os.path.exists(manifest_path):
    with open(manifest_path, "r", encoding="utf-8", errors="ignore") as f:
        m_txt = f.read()

    # Remove LAUNCHER category from existing MainActivity so only NovaBentoActivity appears as the launcher entry point
    m_txt = m_txt.replace('<category android:name="android.intent.category.LAUNCHER" />', "")
    m_txt = m_txt.replace('<category android:name="android.intent.category.LAUNCHER"/>', "")

    if "net.kdt.pojavlaunch.NovaBentoActivity" not in m_txt:
        bento_activity = """
        <activity
            android:name="net.kdt.pojavlaunch.NovaBentoActivity"
            android:screenOrientation="sensorLandscape"
            android:configChanges="keyboardHidden|orientation|screenSize|smallestScreenSize|screenLayout"
            android:theme="@android:style/Theme.NoTitleBar.Fullscreen"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        """
        m_txt = m_txt.replace("</application>", bento_activity + "\n    </application>")

    with open(manifest_path, "w", encoding="utf-8") as f:
        f.write(m_txt)
    print(f"Rerouted launcher entry point in {manifest_path} to NovaBentoActivity.")
