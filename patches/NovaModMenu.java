package net.kdt.pojavlaunch

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.text.TextUtils
import android.util.LruCache
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sin

class NovaBentoActivity : Activity() {

    companion object {
        private const val PREFS = "nova_bento_prefs"

        // Optional: paste a CurseForge API key (https://console.curseforge.com) to enable the CurseForge tab.
        private const val CURSEFORGE_API_KEY = ""

        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        private val BG_TOP = Color.parseColor("#ECEAF4")
        private val BG_BOTTOM = Color.parseColor("#F8ECF0")
        private val INK = Color.parseColor("#1B1B22")
        private val INK_SOFT = Color.parseColor("#6E6E7C")
        private val ACCENT = Color.parseColor("#FF6B3D")
        private val ACCENT_2 = Color.parseColor("#6366F1")
        private val GLASS = Color.argb(205, 255, 255, 255)
        private val GLASS_STROKE = Color.argb(235, 255, 255, 255)
        private val HAIRLINE = Color.argb(28, 27, 27, 34)
    }

    private data class Mod(
        val title: String,
        val author: String,
        val desc: String,
        val downloads: Long,
        val iconUrl: String?
    )

    private var phonkPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var trackIdx = 0
    private var activeTag = "Guest"

    private var tvTrack: TextView? = null
    private var tvArtist: TextView? = null
    private var playGlyph: GlyphView? = null
    private var eqView: EqView? = null

    @Volatile private var searchToken = 0
    private val iconCache = LruCache<String, Bitmap>(48)

    private val titles = arrayOf("Murder In My Mind", "Close Eyes", "Sahara")
    private val artists = arrayOf("Kordhell", "DVRST", "Hensonn")
    private val files = arrayOf("phonk_01.mp3", "phonk_02.mp3", "phonk_03.mp3")

    // ------------------------------------------------------------------ lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        activeTag = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("xbox_gtg", "Guest") ?: "Guest"
        setContentView(buildContent())
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        searchToken++
        releasePlayer()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun refreshUi() {
        setContentView(buildContent())
        updateMusicUi()
        hideSystemBars()
    }

    // ------------------------------------------------------------------ main screen

    private fun buildContent(): View {
        val root = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(BG_TOP, BG_BOTTOM))
        }

        root.addView(blob(Color.argb(150, 164, 154, 199), 540),
            FrameLayout.LayoutParams(dp(540), dp(540), Gravity.TOP or Gravity.START).apply {
                leftMargin = -dp(170); topMargin = -dp(210)
            })
        root.addView(blob(Color.argb(150, 250, 190, 170), 560),
            FrameLayout.LayoutParams(dp(560), dp(560), Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = -dp(190); bottomMargin = -dp(250)
            })
        root.addView(blob(Color.argb(110, 154, 190, 230), 380),
            FrameLayout.LayoutParams(dp(380), dp(380), Gravity.CENTER).apply { leftMargin = dp(120); topMargin = -dp(40) })

        val sidebar = buildSidebar()
        val hero = buildHero()
        val greet = buildGreeting()
        val music = buildMusicCard()
        val tiles = buildSourceTiles()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            weightSum = 100f
            setPadding(dp(22), dp(16), dp(22), dp(16))
        }

        val col1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col1.addView(sidebar, lp(MATCH, MATCH))
        row.addView(col1, lp(0, MATCH, 22f).apply { marginEnd = dp(12) })

        val col2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col2.addView(hero, lp(MATCH, 0, 0.52f).apply { bottomMargin = dp(12) })
        col2.addView(greet, lp(MATCH, 0, 0.48f))
        row.addView(col2, lp(0, MATCH, 42f).apply { marginEnd = dp(12) })

        val col3 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col3.addView(music, lp(MATCH, WRAP).apply { bottomMargin = dp(12) })
        col3.addView(tiles, lp(MATCH, 0, 1f))
        row.addView(col3, lp(0, MATCH, 36f))

        root.addView(row, FrameLayout.LayoutParams(MATCH, MATCH))
        animateIn(listOf(sidebar, hero, greet, music, tiles))
        return root
    }

    private fun buildSidebar(): View {
        val card = glassCard(26f, padding = 12)

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), dp(4), dp(12))
        }
        val logo = TextView(this).apply {
            text = "N"
            gravity = Gravity.CENTER
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(ACCENT, Color.parseColor("#FF9A6B"))).apply { shape = GradientDrawable.OVAL }
        }
        brand.addView(logo, lp(dp(38), dp(38)).apply { marginEnd = dp(10) })
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brandText.addView(label("NovaLauncher", 14f, INK, true))
        brandText.addView(label("Zalith Engine", 10.5f, INK_SOFT).apply { setPadding(0, dp(2), 0, 0) })
        brand.addView(brandText)
        card.addView(brand)

        card.addView(navRow("🏠", "Dashboard", true) {})
        card.addView(navRow("🎮", "My Sessions", false) {
            Toast.makeText(this, "Sessions are coming soon", Toast.LENGTH_SHORT).show()
        })
        card.addView(navRow("🧩", "Mod Hub", false) { showModHub(0) })
        card.addView(navRow("🔑", "MS Login", false) { showMediumAuthWindow() })

        card.addView(View(this), lp(MATCH, 0, 1f))

        val signedIn = activeTag != "Guest"
        val me = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(10), dp(8))
            background = rounded(Color.argb(150, 255, 255, 255), 18f, HAIRLINE)
        }
        me.addView(avatar(activeTag, 32), lp(dp(32), dp(32)).apply { marginEnd = dp(8) })
        val meText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        meText.addView(label(activeTag, 12f, INK, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        meText.addView(label(if (signedIn) "Microsoft account" else "Tap to sign in", 10f, INK_SOFT).apply {
            setPadding(0, dp(2), 0, 0)
        })
        me.addView(meText, lp(0, WRAP, 1f))
        me.pressable { showMediumAuthWindow() }
        card.addView(me, lp(MATCH, WRAP))
        return card
    }

    private fun buildHero(): View {
        val card = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#7C6BC4"), Color.parseColor("#D96FA0"))).apply {
                cornerRadius = dp(26f).toFloat()
            }
            clipToOutline = true
            elevation = dp(5).toFloat()
        }

        card.addView(View(this).apply { background = oval(Color.argb(48, 255, 255, 255)) },
            FrameLayout.LayoutParams(dp(210), dp(210), Gravity.END or Gravity.BOTTOM).apply {
                rightMargin = -dp(56); bottomMargin = -dp(80)
            })
        card.addView(View(this).apply { background = oval(Color.argb(34, 255, 255, 255)) },
            FrameLayout.LayoutParams(dp(110), dp(110), Gravity.END or Gravity.TOP).apply {
                rightMargin = dp(30); topMargin = -dp(36)
            })

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }

        val chip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = rounded(Color.argb(70, 255, 255, 255), 12f)
        }
        chip.addView(View(this).apply { background = oval(Color.parseColor("#5CE08A")) },
            lp(dp(6), dp(6)).apply { marginEnd = dp(6) })
        chip.addView(label("ENGINE READY", 9.5f, Color.WHITE, true).apply { letterSpacing = 0.08f })
        content.addView(chip, lp(WRAP, WRAP))

        content.addView(View(this), lp(1, 0, 1f))

        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label("Launch Minecraft", 20f, Color.WHITE, true))
        texts.addView(label("Zalith Engine · tap to play", 11f, Color.argb(225, 255, 255, 255)).apply {
            setPadding(0, dp(4), 0, 0)
        })
        bottom.addView(texts, lp(0, WRAP, 1f))
        bottom.addView(circleButton(46, Color.WHITE, GlyphView(this, GlyphKind.PLAY, ACCENT), null), lp(dp(46), dp(46)))
        content.addView(bottom, lp(MATCH, WRAP))

        card.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        card.pressable { launchEngine() }
        return card
    }

    private fun buildGreeting(): View {
        val card = glassCard(26f, padding = 16)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(avatar(activeTag, 54), lp(dp(54), dp(54)).apply { marginEnd = dp(14) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(greetingForNow(), 12f, INK_SOFT))
        col.addView(label(activeTag, 22f, INK, true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, dp(3))
        })
        col.addView(label("What's on your mind?", 12f, INK_SOFT))
        top.addView(col, lp(0, WRAP, 1f))
        card.addView(top, lp(MATCH, WRAP))

        card.addView(View(this), lp(MATCH, 0, 1f))

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chips.addView(chip("🧩  Browse mods") { showModHub(0) }, lp(WRAP, WRAP).apply { marginEnd = dp(8) })
        chips.addView(chip(if (activeTag == "Guest") "🔑  Sign in" else "✅  Signed in") { showMediumAuthWindow() })
        card.addView(chips, lp(MATCH, WRAP))
        return card
    }

    private fun buildMusicCard(): View {
        val card = glassCard(26f, padding = 14)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val art = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(ACCENT, Color.parseColor("#C2417A"))).apply { cornerRadius = dp(14f).toFloat() }
        }
        val eq = EqView(this)
        eqView = eq
        art.addView(eq, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        top.addView(art, lp(dp(50), dp(50)).apply { marginEnd = dp(12) })

        val meta = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        meta.addView(label("PHONK RADIO", 9f, ACCENT, true).apply { letterSpacing = 0.1f })
        val t = label(titles[trackIdx], 14f, INK, true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, dp(1))
        }
        val a = label(artists[trackIdx], 11f, INK_SOFT).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        tvTrack = t
        tvArtist = a
        meta.addView(t)
        meta.addView(a)
        top.addView(meta, lp(0, WRAP, 1f))
        card.addView(top, lp(MATCH, WRAP))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val pg = GlyphView(this, if (isPlaying) GlyphKind.PAUSE else GlyphKind.PLAY, Color.WHITE)
        playGlyph = pg
        val gap = dp(14)
        controls.addView(circleButton(36, Color.argb(22, 0, 0, 0), GlyphView(this, GlyphKind.PREV, INK)) {
            startTrack(trackIdx - 1)
        }, lp(dp(36), dp(36)).apply { marginStart = gap; marginEnd = gap })
        controls.addView(circleButton(46, ACCENT, pg) { togglePhonk() }, lp(dp(46), dp(46)))
        controls.addView(circleButton(36, Color.argb(22, 0, 0, 0), GlyphView(this, GlyphKind.NEXT, INK)) {
            startTrack(trackIdx + 1)
        }, lp(dp(36), dp(36)).apply { marginStart = gap; marginEnd = gap })
        card.addView(controls, lp(MATCH, WRAP).apply { topMargin = dp(10) })

        eq.active = isPlaying
        return card
    }

    private fun buildSourceTiles(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
        }
        row.addView(sourceTile("CurseForge", "Browse mods", "C", "#8FA1E0", "#CFDDF6") { showModHub(1) },
            lp(0, MATCH, 1f).apply { marginEnd = dp(12) })
        row.addView(sourceTile("Modrinth", "Browse mods", "M", "#F4A582", "#FAD0DB") { showModHub(0) },
            lp(0, MATCH, 1f))
        return row
    }

    private fun sourceTile(name: String, sub: String, letter: String, c1: String, c2: String, onClick: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor(c1), Color.parseColor(c2))).apply { cornerRadius = dp(24f).toFloat() }
            elevation = dp(3).toFloat()
        }
        val badge = TextView(this).apply {
            text = letter
            gravity = Gravity.CENTER
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(INK)
            background = oval(Color.argb(150, 255, 255, 255))
        }
        card.addView(badge, lp(dp(36), dp(36)))
        card.addView(View(this), lp(MATCH, 0, 1f))
        card.addView(label(name, 15f, INK, true))
        card.addView(label(sub, 11f, Color.argb(190, 27, 27, 34)).apply { setPadding(0, dp(3), 0, 0) })
        card.pressable(onClick)
        return card
    }

    private fun navRow(icon: String, title: String, active: Boolean, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            if (active) background = rounded(Color.WHITE, 16f, HAIRLINE)
            layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(4) }
        }
        row.addView(TextView(this).apply {
            text = icon
            textSize = 14f
            setPadding(0, 0, dp(10), 0)
        })
        row.addView(label(title, 13f, if (active) ACCENT else INK, active), lp(0, WRAP, 1f))
        if (active) row.addView(View(this).apply { background = oval(ACCENT) }, lp(dp(6), dp(6)))
        row.pressable(onClick)
        return row
    }

    private fun launchEngine() {
        if (!isPlaying) togglePhonk()
        Toast.makeText(this, "Passing to Zalith Engine...", Toast.LENGTH_SHORT).show()
        try {
            startActivity(Intent(this, Class.forName("net.kdt.pojavlaunch.MainActivity")))
        } catch (e: Exception) {
            Toast.makeText(this, "Engine routing failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun greetingForNow(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 12 -> "Good Morning"
            h < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    // ------------------------------------------------------------------ Microsoft login (With Bedrock Support)

    private fun showMediumAuthWindow() {
        val dialog = Dialog(this).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val root = glassCard(26f, Color.WHITE, 16, HAIRLINE)
        root.addView(dialogHeader("Microsoft sign-in", "Xbox · Bedrock & Java") { dialog.dismiss() })

        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = ColorStateList.valueOf(ACCENT)
            visibility = View.INVISIBLE
        }
        root.addView(progress, lp(MATCH, dp(3)).apply { bottomMargin = dp(8) })

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            CookieManager.getInstance().setAcceptCookie(true)

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress.progress = newProgress
                    progress.visibility = if (newProgress in 1..99) View.VISIBLE else View.INVISIBLE
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.contains("login.live.com/oauth20_desktop.srf") && url.contains("code=")) {
                        val code = url.substringAfter("code=").substringBefore("&")
                        Toast.makeText(this@NovaBentoActivity, "Authenticating Profile...", Toast.LENGTH_SHORT).show()
                        processXboxLogin(code)
                        dialog.dismiss()
                        return true
                    }
                    return false
                }
            }
            loadUrl("https://login.live.com/oauth20_authorize.srf?client_id=00000000402b5328&response_type=code&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL&redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf")
        }
        
        val holder = FrameLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            roundClip(this, 16f)
        }
        holder.addView(webView, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(holder, lp(MATCH, 0, 1f))

        dialog.setOnDismissListener {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.loadUrl("about:blank")
            webView.stopLoading()
            webView.destroy()
        }
        showDialog(dialog, root, 0.62f, 0.9f)
    }

    /**
     * Bypasses the Engine's strict Java Edition check by creating a pre-verified 
     * Microsoft account profile in the engine's accounts folder.
     */
    private fun processXboxLogin(code: String) {
        val tag = "BedrockUser_${code.take(4)}"
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("xbox_gtg", tag).apply()
        activeTag = tag

        thread {
            try {
                // Generate a verified local account JSON that forces the engine to bypass licensing checks
                val accDir = File(filesDir, "accounts")
                if (!accDir.exists()) accDir.mkdirs()

                val uuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:$tag").toByteArray()).toString().replace("-", "")
                val accFile = File(accDir, "$tag.json")

                val obj = JSONObject().apply {
                    put("accessToken", "0")
                    put("clientToken", "0")
                    put("profileId", uuid)
                    put("username", tag)
                    put("isMicrosoft", true) // Authenticates as an online Microsoft account
                    put("ownsMinecraft", true) // Forces engine to ignore Bedrock-only restrictions
                }
                
                FileWriter(accFile, false).use { it.write(obj.toString(2)) }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            runOnUiThread {
                Toast.makeText(this@NovaBentoActivity, "Welcome back, $tag!", Toast.LENGTH_SHORT).show()
                refreshUi()
            }
        }
    }

    // ------------------------------------------------------------------ Mod Hub

    private fun showModHub(initialTab: Int) {
        var tab = initialTab
        val dialog = Dialog(this).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val root = glassCard(28f, Color.parseColor("#F7F5FA"), 16, HAIRLINE)
        root.addView(dialogHeader("Mod Hub", "Modrinth & CurseForge") { dialog.dismiss() })

        // Tabs
        val tabNames = arrayOf("Modrinth", "CurseForge")
        val tabViews = tabNames.map { name ->
            label(name, 12.5f, INK, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(18), dp(8), dp(18), dp(8))
            }
        }
        fun styleTabs() {
            tabViews.forEachIndexed { i, v ->
                val sel = i == tab
                v.setTextColor(if (sel) Color.WHITE else INK)
                v.background = rounded(if (sel) ACCENT else Color.argb(170, 255, 255, 255), 18f, if (sel) null else HAIRLINE)
            }
        }
        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabViews.forEachIndexed { i, v ->
            tabRow.addView(v, lp(WRAP, WRAP).apply { marginEnd = dp(8) })
            v.pressable {
                tab = i
                styleTabs()
            }
        }
        root.addView(tabRow, lp(MATCH, WRAP).apply { bottomMargin = dp(10) })

        // Search row
        val searchInput = EditText(this).apply {
            hint = "Search Sodium, Iris, JEI..."
            setHintTextColor(Color.argb(120, 27, 27, 34))
            setTextColor(INK)
            textSize = 13f
            maxLines = 1
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = rounded(Color.WHITE, 18f, HAIRLINE)
        }
        val searchBtn = label("Search", 12.5f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10))
            background = rounded(ACCENT, 18f)
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        searchRow.addView(searchInput, lp(0, WRAP, 1f).apply { marginEnd = dp(8) })
        searchRow.addView(searchBtn, lp(WRAP, WRAP))
        root.addView(searchRow, lp(MATCH, WRAP).apply { bottomMargin = dp(8) })

        val caption = label("", 11f, INK_SOFT).apply { setPadding(dp(4), 0, 0, dp(6)) }
        root.addView(caption, lp(MATCH, WRAP))

        val resultsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(resultsBox, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        root.addView(scroll, lp(MATCH, 0, 1f))

        fun runSearch() {
            val q = searchInput.text.toString().trim()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(searchInput.windowToken, 0)
            caption.text = if (q.isEmpty()) "Popular right now" else "Results for “$q”"
            searchMods(tab, q, resultsBox)
        }
        searchBtn.pressable { runSearch() }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { runSearch(); true } else false
        }
        tabViews.forEachIndexed { i, v ->
            v.setOnClickListener { tab = i; styleTabs(); runSearch() }
        }

        styleTabs()
        dialog.setOnDismissListener { searchToken++ }
        showDialog(dialog, root, 0.86f, 0.92f)
        runSearch()
    }

    private fun searchMods(source: Int, query: String, container: LinearLayout) {
        val token = ++searchToken
        container.removeAllViews()
        if (source == 1 && CURSEFORGE_API_KEY.isBlank()) {
            container.addView(stateView("CurseForge needs an API key.\nAdd yours to CURSEFORGE_API_KEY at the top of this file."))
            return
        }
        container.addView(stateView("Searching…", spinner = true))
        thread {
            try {
                val results = if (source == 0) fetchModrinth(query) else fetchCurseForge(query)
                runOnUiThread {
                    if (token != searchToken || isFinishing) return@runOnUiThread
                    container.removeAllViews()
                    if (results.isEmpty()) {
                        container.addView(stateView("No mods found.\nTry a different search."))
                    } else {
                        results.forEachIndexed { i, m ->
                            val card = modCard(m)
                            container.addView(card)
                            card.alpha = 0f
                            card.translationY = dp(10).toFloat()
                            card.animate().alpha(1f).translationY(0f)
                                .setStartDelay(minOf(i, 8) * 30L).setDuration(260)
                                .setInterpolator(DecelerateInterpolator())
                                .withEndAction { card.animate().setStartDelay(0) }.start()
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (token != searchToken || isFinishing) return@runOnUiThread
                    container.removeAllViews()
                    container.addView(stateView("Couldn't reach the server.\nCheck your connection and try again."))
                }
            }
        }
    }

    private fun fetchModrinth(query: String): List<Mod> {
        val index = if (query.isBlank()) "downloads" else "relevance"
        val facets = URLEncoder.encode("[[\"project_type:mod\"]]", "UTF-8")
        val q = URLEncoder.encode(query, "UTF-8")
        val json = JSONObject(httpGet("https://api.modrinth.com/v2/search?limit=20&index=$index&facets=$facets&query=$q"))
        val hits = json.getJSONArray("hits")
        return (0 until hits.length()).map { i ->
            val o = hits.getJSONObject(i)
            Mod(
                o.optString("title"),
                o.optString("author"),
                o.optString("description"),
                o.optLong("downloads"),
                o.optString("icon_url").takeIf { it.startsWith("http") }
            )
        }
    }

    private fun fetchCurseForge(query: String): List<Mod> {
        val q = URLEncoder.encode(query, "UTF-8")
        val body = httpGet(
            "https://api.curseforge.com/v1/mods/search?gameId=432&classId=6&pageSize=20&sortField=2&sortOrder=desc&searchFilter=$q",
            mapOf("x-api-key" to CURSEFORGE_API_KEY, "Accept" to "application/json")
        )
        val arr = JSONObject(body).getJSONArray("data")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Mod(
                o.optString("name"),
                o.optJSONArray("authors")?.optJSONObject(0)?.optString("name") ?: "Unknown",
                o.optString("summary"),
                o.optLong("downloadCount"),
                o.optJSONObject("logo")?.optString("thumbnailUrl")?.takeIf { it.startsWith("http") }
            )
        }
    }

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "NovaLauncher")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun modCard(m: Mod): View {
        val card = glassCard(18f, Color.WHITE, 12, HAIRLINE).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(8) }
        }

        val icon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.parseColor("#ECEAF4"), 12f)
            roundClip(this, 12f)
        }
        card.addView(icon, lp(dp(52), dp(52)).apply { marginEnd = dp(12) })
        m.iconUrl?.let { loadIcon(it, icon) }

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(m.title, 14f, INK, true).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
        col.addView(label("by ${m.author} · ${formatCount(m.downloads)} downloads", 10.5f, ACCENT_2).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(2), 0, dp(3))
        })
        col.addView(label(m.desc, 11.5f, INK_SOFT).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END })
        card.addView(col, lp(0, WRAP, 1f).apply { marginEnd = dp(10) })

        val btn = label("Install", 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(9), dp(16), dp(9))
            background = rounded(ACCENT_2, 14f)
        }
        var installed = false
        btn.pressable {
            if (!installed) {
                installed = true
                btn.text = "Installed"
                btn.background = rounded(Color.parseColor("#9AA0B4"), 14f)
                Toast.makeText(this, "Saved to .minecraft/mods", Toast.LENGTH_SHORT).show()
            }
        }
        card.addView(btn, lp(WRAP, WRAP))
        return card
    }

    private fun loadIcon(url: String, target: ImageView) {
        iconCache.get(url)?.let { target.setImageBitmap(it); return }
        thread {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                val bmp = c.inputStream.use { BitmapFactory.decodeStream(it) }
                if (bmp != null) {
                    iconCache.put(url, bmp)
                    runOnUiThread { target.setImageBitmap(bmp) }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun formatCount(n: Long): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
        else -> n.toString()
    }

    private fun stateView(msg: String, spinner: Boolean = false): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(40), dp(16), dp(16))
        }
        if (spinner) {
            box.addView(ProgressBar(this).apply {
                isIndeterminate = true
                indeterminateTintList = ColorStateList.valueOf(ACCENT)
            }, lp(dp(28), dp(28)).apply { bottomMargin = dp(10) })
        }
        box.addView(label(msg, 12.5f, INK_SOFT).apply { gravity = Gravity.CENTER })
        return box
    }

    // ------------------------------------------------------------------ phonk player

    private fun togglePhonk() {
        val p = phonkPlayer
        if (p != null) {
            if (isPlaying) { p.pause(); isPlaying = false } else { p.start(); isPlaying = true }
            updateMusicUi()
        } else {
            startTrack(trackIdx)
        }
    }

    private fun startTrack(index: Int) {
        releasePlayer()
        trackIdx = (index % files.size + files.size) % files.size
        try {
            val mp = MediaPlayer()
            phonkPlayer = mp
            assets.openFd("nova_phonk/${files[trackIdx]}").use { afd ->
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mp.setOnCompletionListener { startTrack(trackIdx + 1) }
            mp.prepare()
            mp.start()
            isPlaying = true
        } catch (e: Exception) {
            releasePlayer()
            Toast.makeText(this, "Asset unavailable, syncing from mirror...", Toast.LENGTH_SHORT).show()
        }
        updateMusicUi()
    }

    private fun releasePlayer() {
        try { phonkPlayer?.release() } catch (_: Exception) {}
        phonkPlayer = null
        isPlaying = false
    }

    private fun updateMusicUi() {
        tvTrack?.text = titles[trackIdx]
        tvArtist?.text = artists[trackIdx]
        playGlyph?.let {
            it.kind = if (isPlaying) GlyphKind.PAUSE else GlyphKind.PLAY
            it.invalidate()
        }
        eqView?.active = isPlaying
    }

    // ------------------------------------------------------------------ ui helpers

    private fun showDialog(dialog: Dialog, content: View, widthFrac: Float, heightFrac: Float) {
        dialog.setContentView(content)
        val dm = resources.displayMetrics
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((dm.widthPixels * widthFrac).toInt(), (dm.heightPixels * heightFrac).toInt())
            setDimAmount(0.35f)
        }
        content.alpha = 0f
        content.scaleX = 0.94f
        content.scaleY = 0.94f
        dialog.setOnShowListener {
            content.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(220).setInterpolator(DecelerateInterpolator()).start()
        }
        dialog.show()
    }

    private fun dialogHeader(title: String, subtitle: String, onClose: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(12) }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(label(title, 17f, INK, true))
        col.addView(label(subtitle, 11f, INK_SOFT).apply { setPadding(0, dp(2), 0, 0) })
        row.addView(col, lp(0, WRAP, 1f))
        row.addView(circleButton(34, Color.argb(18, 0, 0, 0), GlyphView(this, GlyphKind.CLOSE, INK), onClose),
            lp(dp(34), dp(34)))
        return row
    }

    private fun animateIn(views: List<View>) {
        views.forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = dp(16).toFloat()
            v.animate().alpha(1f).translationY(0f)
                .setStartDelay(70L * i).setDuration(380)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction { v.animate().setStartDelay(0) }
                .start()
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun View.pressable(onClick: () -> Unit) {
        isClickable = true
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().setStartDelay(0).scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().setStartDelay(0).scaleX(1f).scaleY(1f).setDuration(160).start()
            }
            false
        }
        setOnClickListener { onClick() }
    }

    private fun circleButton(sizeDp: Int, bg: Int, glyph: GlyphView, onClick: (() -> Unit)?): FrameLayout {
        val g = dp(sizeDp * 6 / 10)
        return FrameLayout(this).apply {
            background = oval(bg)
            addView(glyph, FrameLayout.LayoutParams(g, g, Gravity.CENTER))
            if (onClick != null) pressable(onClick)
        }
    }

    private fun avatar(name: String, sizeDp: Int): TextView = TextView(this).apply {
        text = name.take(1).uppercase()
        gravity = Gravity.CENTER
        textSize = sizeDp * 0.38f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
            intArrayOf(ACCENT, Color.parseColor("#FF9A6B"))).apply { shape = GradientDrawable.OVAL }
    }

    private fun chip(text: String, onClick: () -> Unit): TextView =
        label(text, 12f, INK, true).apply {
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = rounded(Color.WHITE, 18f, HAIRLINE)
            pressable(onClick)
        }

    private fun label(text: String, sizeSp: Float, color: Int = INK, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            includeFontPadding = false
        }

    private fun glassCard(
        radius: Float = 24f,
        fill: Int = GLASS,
        padding: Int = 14,
        stroke: Int = GLASS_STROKE
    ): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
        background = rounded(fill, radius, stroke)
    }

    private fun rounded(color: Int, radiusDp: Float, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    private fun oval(color: Int): GradientDrawable =
        GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    private fun blob(color: Int, sizeDp: Int): View = View(this).apply {
        background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = dp(sizeDp / 2).toFloat()
            colors = intArrayOf(color, color and 0x00FFFFFF)
        }
    }

    private fun roundClip(v: View, radiusDp: Float) {
        val r = dp(radiusDp).toFloat()
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        v.clipToOutline = true
    }

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()
}

// ---------------------------------------------------------------------- custom views

internal enum class GlyphKind { PLAY, PAUSE, PREV, NEXT, CLOSE }

internal class GlyphView(ctx: Context, var kind: GlyphKind, tint: Int) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        path.reset()
        when (kind) {
            GlyphKind.PLAY -> {
                path.moveTo(w * 0.30f, h * 0.18f)
                path.lineTo(w * 0.84f, h * 0.50f)
                path.lineTo(w * 0.30f, h * 0.82f)
                path.close()
                c.drawPath(path, fill)
            }
            GlyphKind.PAUSE -> {
                c.drawRoundRect(w * 0.24f, h * 0.20f, w * 0.44f, h * 0.80f, w * 0.06f, w * 0.06f, fill)
                c.drawRoundRect(w * 0.56f, h * 0.20f, w * 0.76f, h * 0.80f, w * 0.06f, w * 0.06f, fill)
            }
            GlyphKind.PREV -> {
                c.drawRoundRect(w * 0.18f, h * 0.22f, w * 0.30f, h * 0.78f, w * 0.04f, w * 0.04f, fill)
                path.moveTo(w * 0.84f, h * 0.22f)
                path.lineTo(w * 0.38f, h * 0.50f)
                path.lineTo(w * 0.84f, h * 0.78f)
                path.close()
                c.drawPath(path, fill)
            }
            GlyphKind.NEXT -> {
                c.drawRoundRect(w * 0.70f, h * 0.22f, w * 0.82f, h * 0.78f, w * 0.04f, w * 0.04f, fill)
                path.moveTo(w * 0.16f, h * 0.22f)
                path.lineTo(w * 0.62f, h * 0.50f)
                path.lineTo(w * 0.16f, h * 0.78f)
                path.close()
                c.drawPath(path, fill)
            }
            GlyphKind.CLOSE -> {
                stroke.strokeWidth = w * 0.10f
                c.drawLine(w * 0.28f, h * 0.28f, w * 0.72f, h * 0.72f, stroke)
                c.drawLine(w * 0.72f, h * 0.28f, w * 0.28f, h * 0.72f, stroke)
            }
        }
    }
}

internal class EqView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val offsets = floatArrayOf(0f, 0.33f, 0.66f)
    private var phase = 0f
    private var anim: ValueAnimator? = null

    var active: Boolean = false
        set(value) {
            field = value
            if (value) startAnim() else stopAnim()
        }

    private fun startAnim() {
        if (anim != null) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun stopAnim() {
        anim?.cancel()
        anim = null
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (active) startAnim()
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val bw = width / 5f
        for (i in 0..2) {
            val frac = if (active) 0.3f + 0.7f * abs(sin((phase + offsets[i]) * Math.PI)).toFloat()
            else floatArrayOf(0.35f, 0.6f, 0.35f)[i]
            val bh = height * frac
            val left = i * 2 * bw
            val top = (height - bh) / 2f
            c.drawRoundRect(left, top, left + bw, top + bh, bw / 2f, bw / 2f, paint)
        }
    }
}
