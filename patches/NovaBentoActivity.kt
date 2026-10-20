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
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class NovaBentoActivity : Activity() {

    companion object {
        private const val PREFS = "nova_bento_prefs"
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

    private data class Song(
        val title: String,
        val artist: String,
        val assetFile: String? = null,
        val streamUrl: String? = null,
        val artUrl: String? = null,
        val accentHex: String = "#FF6B3D"
    )

    private var currentPage = 0 // 0 = Dashboard, 1 = Full YT Music / Spotify Page
    private var phonkPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var isLoopingTrack = false
    private var trackIdx = 0
    private var activeTag = "Guest"

    private val defaultPlaylist = mutableListOf(
        Song("Murder In My Mind", "Kordhell", assetFile = "phonk_01.mp3",
            streamUrl = "https://ia801509.us.archive.org/10/items/kordhell-murder-in-my-mind/KORDHELL%20-%20MURDER%20IN%20MY%20MIND.mp3", accentHex = "#FF6B3D"),
        Song("Close Eyes", "DVRST", assetFile = "phonk_02.mp3",
            streamUrl = "https://ia802302.us.archive.org/27/items/dvrst-close-eyes_202204/DVRST%20-%20Close%20Eyes.mp3", accentHex = "#6366F1"),
        Song("Sahara", "Hensonn", assetFile = "phonk_03.mp3",
            streamUrl = "https://ia601405.us.archive.org/19/items/hensonn-sahara_202205/Hensonn%20-%20Sahara.mp3", accentHex = "#EC4899"),
        Song("Metamorphosis", "Interworld", accentHex = "#10B981"),
        Song("Phonky Town", "PlayaPhonk", accentHex = "#F59E0B"),
        Song("Disaster", "KSLV Noh", accentHex = "#8B5CF6"),
        Song("COWBELL WARRIOR!", "SXMPRA", accentHex = "#EF4444"),
        Song("NEON BLADE", "MoonDeity", accentHex = "#3B82F6"),
        Song("Override", "KSLV Noh", accentHex = "#14B8A6"),
        Song("SLAY!", "Eternxlkz", accentHex = "#F43F5E")
    )

    private val playlist = mutableListOf<Song>().apply { addAll(defaultPlaylist) }

    // Live UI references for music updates
    private var tvTrack: TextView? = null
    private var tvArtist: TextView? = null
    private var playGlyph: GlyphView? = null
    private var eqView: EqView? = null
    private var vinylView: VinylDiscView? = null
    private var waveView: SpectrumWaveView? = null
    private var seekBar: SeekBar? = null
    private var tvElapsed: TextView? = null
    private var tvTotal: TextView? = null
    private var trackListContainer: LinearLayout? = null

    private val uiHandler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            val mp = phonkPlayer
            if (mp != null && isPlaying) {
                try {
                    val pos = mp.currentPosition
                    val dur = mp.duration.coerceAtLeast(1)
                    seekBar?.max = dur
                    seekBar?.progress = pos
                    tvElapsed?.text = formatMs(pos)
                    tvTotal?.text = formatMs(dur)
                } catch (_: Exception) {}
            }
            uiHandler.postDelayed(this, 500)
        }
    }

    @Volatile private var searchToken = 0
    @Volatile private var musicSearchToken = 0
    private val iconCache = LruCache<String, Bitmap>(64)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        activeTag = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("xbox_gtg", "Guest") ?: "Guest"
        setContentView(buildContent())
        hideSystemBars()
        uiHandler.post(progressTicker)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        searchToken++
        musicSearchToken++
        uiHandler.removeCallbacksAndMessages(null)
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

    // ------------------------------------------------------------------ Root & Navigation

    private fun buildContent(): View {
        val darkMusic = currentPage == 1
        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                if (darkMusic) intArrayOf(Color.parseColor("#0D0E15"), Color.parseColor("#171524"))
                else intArrayOf(BG_TOP, BG_BOTTOM)
            )
        }

        root.addView(blob(if (darkMusic) Color.argb(110, 255, 107, 61) else Color.argb(150, 164, 154, 199), 540),
            FrameLayout.LayoutParams(dp(540), dp(540), Gravity.TOP or Gravity.START).apply {
                leftMargin = -dp(170); topMargin = -dp(210)
            })
        root.addView(blob(if (darkMusic) Color.argb(110, 99, 102, 241) else Color.argb(150, 250, 190, 170), 560),
            FrameLayout.LayoutParams(dp(560), dp(560), Gravity.BOTTOM or Gravity.END).apply {
                rightMargin = -dp(190); bottomMargin = -dp(250)
            })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            weightSum = 100f
            setPadding(dp(20), dp(14), dp(20), dp(14))
        }

        val sidebar = buildSidebar()
        val col1 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col1.addView(sidebar, lp(MATCH, MATCH))
        row.addView(col1, lp(0, MATCH, 22f).apply { marginEnd = dp(12) })

        if (currentPage == 1) {
            val musicPage = buildFullMusicPage()
            row.addView(musicPage, lp(0, MATCH, 78f))
            animateIn(listOf(musicPage))
        } else {
            val hero = buildHero()
            val greet = buildGreeting()
            val music = buildMiniMusicCard()
            val tiles = buildSourceTiles()

            val col2 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col2.addView(hero, lp(MATCH, 0, 0.52f).apply { bottomMargin = dp(12) })
            col2.addView(greet, lp(MATCH, 0, 0.48f))
            row.addView(col2, lp(0, MATCH, 42f).apply { marginEnd = dp(12) })

            val col3 = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col3.addView(music, lp(MATCH, WRAP).apply { bottomMargin = dp(12) })
            col3.addView(tiles, lp(MATCH, 0, 1f))
            row.addView(col3, lp(0, MATCH, 36f))
            animateIn(listOf(hero, greet, music, tiles))
        }

        root.addView(row, FrameLayout.LayoutParams(MATCH, MATCH))
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

        card.addView(navRow("🏠", "Dashboard", currentPage == 0) {
            if (currentPage != 0) { currentPage = 0; refreshUi() }
        })
        card.addView(navRow("🎧", "Music Studio", currentPage == 1) {
            if (currentPage != 1) { currentPage = 1; refreshUi() }
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
        meText.addView(label(if (signedIn) "Bedrock / MS Ready" else "Guest · Tap to sign in", 10f, INK_SOFT).apply {
            setPadding(0, dp(2), 0, 0)
        })
        me.addView(meText, lp(0, WRAP, 1f))
        me.pressable { showMediumAuthWindow() }
        card.addView(me, lp(MATCH, WRAP))
        return card
    }

    // ------------------------------------------------------------------ Full YT Music / Spotify Page

    private fun buildFullMusicPage(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 100f
        }

        // LEFT STAGE (45%): Spinning Vinyl Art + Live Spectrum Waveform + Seekbar + Controls
        val leftStage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            background = rounded(Color.parseColor("#181924"), 28f, Color.argb(45, 255, 255, 255))
        }

        val topBadgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topBadgeRow.addView(label("●  NOVA MUSIC  ·  SPOTIFY & YT STREAM", 9.5f, ACCENT, true).apply { letterSpacing = 0.08f }, lp(0, WRAP, 1f))
        val backChip = label("Dashboard ↗", 10.5f, Color.WHITE, true).apply {
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = rounded(Color.argb(40, 255, 255, 255), 12f)
            pressable { currentPage = 0; refreshUi() }
        }
        topBadgeRow.addView(backChip)
        leftStage.addView(topBadgeRow, lp(MATCH, WRAP))

        // Spinning Vinyl Record View
        val vinyl = VinylDiscView(this).apply { spinning = isPlaying }
        vinylView = vinyl
        leftStage.addView(vinyl, lp(dp(132), dp(132)).apply { topMargin = dp(6); bottomMargin = dp(6) })
        playlist.getOrNull(trackIdx)?.artUrl?.let { loadVinylArt(it, vinyl) }

        // Track Title & Artist
        val curSong = playlist.getOrElse(trackIdx) { defaultPlaylist[0] }
        val tTitle = label(curSong.title, 16f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val tArtist = label(curSong.artist, 11.5f, Color.parseColor("#9CA3AF")).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            setPadding(0, dp(2), 0, dp(6))
        }
        tvTrack = tTitle
        tvArtist = tArtist
        leftStage.addView(tTitle, lp(MATCH, WRAP))
        leftStage.addView(tArtist, lp(MATCH, WRAP))

        // 28-Bar Live Audio Waveform Visualizer
        val wave = SpectrumWaveView(this).apply { active = isPlaying }
        waveView = wave
        leftStage.addView(wave, lp(MATCH, dp(32)).apply { bottomMargin = dp(4) })

        // SeekBar + Timestamps
        val seekRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val el = label("0:00", 9.5f, Color.parseColor("#9CA3AF"))
        val tot = label("2:30", 9.5f, Color.parseColor("#9CA3AF"))
        tvElapsed = el
        tvTotal = tot
        val sb = SeekBar(this).apply {
            max = 1000
            progressTintList = ColorStateList.valueOf(ACCENT)
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) phonkPlayer?.seekTo(p)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        seekBar = sb
        seekRow.addView(el)
        seekRow.addView(sb, lp(0, WRAP, 1f))
        seekRow.addView(tot)
        leftStage.addView(seekRow, lp(MATCH, WRAP).apply { bottomMargin = dp(6) })

        // Transport Controls Row
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val pg = GlyphView(this, if (isPlaying) GlyphKind.PAUSE else GlyphKind.PLAY, Color.WHITE)
        playGlyph = pg
        val gap = dp(12)
        controls.addView(circleButton(36, Color.argb(35, 255, 255, 255), GlyphView(this, GlyphKind.PREV, Color.WHITE)) {
            startTrack(trackIdx - 1)
        }, lp(dp(36), dp(36)).apply { marginEnd = gap })
        controls.addView(circleButton(48, ACCENT, pg) { togglePhonk() }, lp(dp(48), dp(48)))
        controls.addView(circleButton(36, Color.argb(35, 255, 255, 255), GlyphView(this, GlyphKind.NEXT, Color.WHITE)) {
            startTrack(trackIdx + 1)
        }, lp(dp(36), dp(36)).apply { marginStart = gap })
        leftStage.addView(controls, lp(MATCH, WRAP))

        page.addView(leftStage, lp(0, MATCH, 44f).apply { marginEnd = dp(12) })

        // RIGHT STAGE (56%): Search Any Song on YT Music / Spotify + Curated Phonk Queue
        val rightStage = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.parseColor("#181924"), 28f, Color.argb(45, 255, 255, 255))
        }

        // Search Bar
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val searchInput = EditText(this).apply {
            hint = "Search any song or artist (Kordhell, DVRST, Travis Scott, etc.)..."
            setHintTextColor(Color.parseColor("#6B7280"))
            setTextColor(Color.WHITE)
            textSize = 12f
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = rounded(Color.parseColor("#252736"), 18f)
        }
        val searchBtn = label("Search", 11.5f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = rounded(ACCENT, 18f)
        }
        searchRow.addView(searchInput, lp(0, WRAP, 1f).apply { marginEnd = dp(8) })
        searchRow.addView(searchBtn, lp(WRAP, WRAP))
        rightStage.addView(searchRow, lp(MATCH, WRAP).apply { bottomMargin = dp(8) })

        // Quick Genre Chips
        val genreRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("🔥 Phonk Hits" to "", "🚗 Drift Phonk" to "drift phonk", "⚡ Brazilian Phonk" to "brazilian phonk", "🎹 Gaming Beats" to "synthwave phonk").forEach { (title, q) ->
            val pill = label(title, 10.5f, Color.WHITE, true).apply {
                setPadding(dp(10), dp(5), dp(10), dp(5))
                background = rounded(Color.parseColor("#252736"), 14f)
                pressable {
                    if (q.isEmpty()) {
                        playlist.clear()
                        playlist.addAll(defaultPlaylist)
                        renderTrackList()
                    } else {
                        searchInput.setText(q)
                        searchGlobalMusicCatalog(q)
                    }
                }
            }
            genreRow.addView(pill, lp(WRAP, WRAP).apply { marginEnd = dp(6) })
        }
        rightStage.addView(genreRow, lp(MATCH, WRAP).apply { bottomMargin = dp(8) })

        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        trackListContainer = listBox
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(listBox, FrameLayout.LayoutParams(MATCH, WRAP))
        }
        rightStage.addView(scroll, lp(MATCH, 0, 1f))

        val triggerMusicSearch = {
            val q = searchInput.text.toString().trim()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(searchInput.windowToken, 0)
            if (q.isEmpty()) {
                playlist.clear()
                playlist.addAll(defaultPlaylist)
                renderTrackList()
            } else {
                searchGlobalMusicCatalog(q)
            }
        }
        searchBtn.pressable { triggerMusicSearch() }
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { triggerMusicSearch(); true } else false
        }

        renderTrackList()
        page.addView(rightStage, lp(0, MATCH, 56f))
        return page
    }

    private fun renderTrackList() {
        val box = trackListContainer ?: return
        box.removeAllViews()
        playlist.forEachIndexed { idx, song ->
            val active = idx == trackIdx
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(12), dp(8))
                background = rounded(
                    if (active) Color.parseColor("#2D2A42") else Color.parseColor("#202230"),
                    16f,
                    if (active) ACCENT else null
                )
                layoutParams = lp(MATCH, WRAP).apply { bottomMargin = dp(6) }
            }

            val artImg = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(Color.parseColor(song.accentHex), 10f)
                roundClip(this, 10f)
            }
            row.addView(artImg, lp(dp(40), dp(40)).apply { marginEnd = dp(10) })
            song.artUrl?.let { loadIcon(it, artImg) }

            val meta = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            meta.addView(label(song.title, 12.5f, if (active) ACCENT else Color.WHITE, true).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            })
            meta.addView(label(song.artist, 10.5f, Color.parseColor("#9CA3AF")).apply {
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(2), 0, 0)
            })
            row.addView(meta, lp(0, WRAP, 1f))

            if (active && isPlaying) {
                val miniEq = EqView(this).apply { this.active = true }
                row.addView(miniEq, lp(dp(20), dp(20)).apply { marginEnd = dp(6) })
            } else {
                val playBadge = label("▶", 12f, Color.WHITE, true).apply {
                    gravity = Gravity.CENTER
                    background = oval(if (active) ACCENT else Color.argb(40, 255, 255, 255))
                }
                row.addView(playBadge, lp(dp(28), dp(28)))
            }

            row.pressable { startTrack(idx) }
            box.addView(row)
        }
    }

    private fun searchGlobalMusicCatalog(query: String) {
        val token = ++musicSearchToken
        val box = trackListContainer ?: return
        box.removeAllViews()
        box.addView(label("Searching Spotify / YT Music catalog for “$query”…", 12f, Color.parseColor("#9CA3AF")).apply {
            setPadding(dp(8), dp(20), dp(8), dp(8))
        })

        thread {
            try {
                val url = "https://itunes.apple.com/search?media=music&entity=song&limit=25&term=" + URLEncoder.encode(query, "UTF-8")
                val body = httpGet(url)
                val results = JSONObject(body).optJSONArray("results") ?: JSONArray()
                val found = mutableListOf<Song>()
                for (i in 0 until results.length()) {
                    val o = results.getJSONObject(i)
                    val preview = o.optString("previewUrl", "")
                    if (preview.isNotEmpty()) {
                        found.add(
                            Song(
                                title = o.optString("trackName", "Unknown Track"),
                                artist = o.optString("artistName", "Unknown Artist"),
                                streamUrl = preview,
                                artUrl = o.optString("artworkUrl100", "").replace("100x100bb", "300x300bb"),
                                accentHex = if (i % 2 == 0) "#FF6B3D" else "#6366F1"
                            )
                        )
                    }
                }
                runOnUiThread {
                    if (token != musicSearchToken || isFinishing) return@runOnUiThread
                    if (found.isNotEmpty()) {
                        playlist.clear()
                        playlist.addAll(found)
                        trackIdx = 0
                        renderTrackList()
                    } else {
                        box.removeAllViews()
                        box.addView(label("No songs found for “$query”.", 12f, Color.parseColor("#EF4444")))
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (token != musicSearchToken || isFinishing) return@runOnUiThread
                    box.removeAllViews()
                    box.addView(label("Couldn't reach music catalog. Showing offline Phonk library.", 12f, Color.parseColor("#9CA3AF")))
                }
            }
        }
    }

    // ------------------------------------------------------------------ Dashboard Cards

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
        chips.addView(chip("🎧  Music Studio") { currentPage = 1; refreshUi() }, lp(WRAP, WRAP).apply { marginEnd = dp(8) })
        chips.addView(chip(if (activeTag == "Guest") "🔑  Sign in" else "✅  Signed in") { showMediumAuthWindow() })
        card.addView(chips, lp(MATCH, WRAP))
        return card
    }

    private fun buildMiniMusicCard(): View {
        val card = glassCard(26f, padding = 14)
        val curSong = playlist.getOrElse(trackIdx) { defaultPlaylist[0] }

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
        meta.addView(label("PHONK STUDIO  ·  TAP FOR FULL PLAYER ↗", 8.5f, ACCENT, true).apply { letterSpacing = 0.08f })
        val t = label(curSong.title, 14f, INK, true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(0, dp(3), 0, dp(1))
        }
        val a = label(curSong.artist, 11f, INK_SOFT).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        tvTrack = t
        tvArtist = a
        meta.addView(t)
        meta.addView(a)
        top.addView(meta, lp(0, WRAP, 1f))
        top.pressable { currentPage = 1; refreshUi() }
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
        Toast.makeText(this, "Launching Zalith Engine...", Toast.LENGTH_SHORT).show()
        val targets = listOf(
            "com.movtery.zalithlauncher.ui.activities.MainActivity",
            "net.kdt.pojavlaunch.MainActivity"
        )
        for (cls in targets) {
            try {
                startActivity(Intent(this, Class.forName(cls)))
                return
            } catch (_: Exception) {}
        }
        Toast.makeText(this, "Engine activity not found", Toast.LENGTH_SHORT).show()
    }

    private fun greetingForNow(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            h < 12 -> "Good Morning"
            h < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    // ------------------------------------------------------------------ Microsoft & Bedrock Login

    private fun showMediumAuthWindow() {
        val dialog = Dialog(this).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
        val root = glassCard(26f, Color.WHITE, 16, HAIRLINE)
        root.addView(dialogHeader("Microsoft sign-in", "Xbox · Bedrock & Java Supported") { dialog.dismiss() })

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
                        Toast.makeText(this@NovaBentoActivity, "Verifying Xbox / Bedrock profile...", Toast.LENGTH_SHORT).show()
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

    private fun processXboxLogin(code: String) {
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
                                put("UserTokens", JSONArray().put(xblToken))
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

            try {
                val accDir = File(filesDir, "accounts")
                if (!accDir.exists()) accDir.mkdirs()
                val uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:$resolvedTag").toByteArray()).toString().replace("-", "")
                val obj = JSONObject().apply {
                    put("accessToken", "0")
                    put("clientToken", "0")
                    put("profileId", uuid)
                    put("username", resolvedTag)
                    put("isMicrosoft", true)
                    put("ownsMinecraft", true)
                }
                FileWriter(File(accDir, "$resolvedTag.json"), false).use { it.write(obj.toString(2)) }
            } catch (_: Exception) {}

            getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("xbox_gtg", resolvedTag).apply()
            activeTag = resolvedTag
            runOnUiThread {
                Toast.makeText(this@NovaBentoActivity, "Signed in with Bedrock/Xbox: $resolvedTag", Toast.LENGTH_LONG).show()
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
            v.pressable { tab = i; styleTabs() }
        }
        root.addView(tabRow, lp(MATCH, WRAP).apply { bottomMargin = dp(10) })

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
                                .setInterpolator(DecelerateInterpolator()).start()
                        }
                    }
                }
            } catch (_: Exception) {
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
        val body = httpGet("https://api.curse.tools/v1/cf/mods/search?gameId=432&classId=6&pageSize=20&searchFilter=$q")
        val arr = JSONObject(body).optJSONArray("data") ?: return fetchModrinth(query)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Mod(
                o.optString("name"),
                o.optJSONArray("authors")?.optJSONObject(0)?.optString("name") ?: "Creator",
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
            } catch (_: Exception) {}
        }
    }

    private fun loadVinylArt(url: String, target: VinylDiscView) {
        iconCache.get(url)?.let { target.artBitmap = it; target.invalidate(); return }
        thread {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                val bmp = c.inputStream.use { BitmapFactory.decodeStream(it) }
                if (bmp != null) {
                    iconCache.put(url, bmp)
                    runOnUiThread { target.artBitmap = bmp; target.invalidate() }
                }
            } catch (_: Exception) {}
        }
    }

    private fun formatCount(n: Long): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
        else -> n.toString()
    }

    private fun formatMs(ms: Int): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
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

    // ------------------------------------------------------------------ Music Player Engine

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
        if (playlist.isEmpty()) return
        releasePlayer()
        trackIdx = (index % playlist.size + playlist.size) % playlist.size
        val song = playlist[trackIdx]
        vinylView?.artBitmap = null
        song.artUrl?.let { url -> vinylView?.let { loadVinylArt(url, it) } }

        // 1. Try local bundled asset first if available
        if (song.assetFile != null) {
            try {
                val mp = MediaPlayer()
                phonkPlayer = mp
                assets.openFd("nova_phonk/${song.assetFile}").use { afd ->
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                mp.setOnCompletionListener { startTrack(trackIdx + 1) }
                mp.prepare()
                mp.start()
                isPlaying = true
                updateMusicUi()
                return
            } catch (_: Exception) {
                releasePlayer()
            }
        }

        // 2. Stream via URL or search iTunes stream dynamically
        isPlaying = true
        updateMusicUi()
        thread {
            try {
                var stream = song.streamUrl
                if (stream.isNullOrEmpty()) {
                    val q = URLEncoder.encode("${song.artist} ${song.title}", "UTF-8")
                    val body = httpGet("https://itunes.apple.com/search?media=music&entity=song&limit=1&term=$q")
                    val first = JSONObject(body).optJSONArray("results")?.optJSONObject(0)
                    stream = first?.optString("previewUrl", "")
                    val art = first?.optString("artworkUrl100", "")?.replace("100x100bb", "300x300bb")
                    if (!art.isNullOrEmpty()) {
                        playlist[trackIdx] = song.copy(streamUrl = stream, artUrl = art)
                        runOnUiThread { vinylView?.let { loadVinylArt(art, it) } }
                    }
                }
                if (!stream.isNullOrEmpty()) {
                    val mp = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(stream)
                        setOnCompletionListener { runOnUiThread { startTrack(trackIdx + 1) } }
                        prepare()
                        start()
                    }
                    phonkPlayer = mp
                    runOnUiThread { updateMusicUi() }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    isPlaying = false
                    updateMusicUi()
                }
            }
        }
    }

    private fun releasePlayer() {
        try { phonkPlayer?.release() } catch (_: Exception) {}
        phonkPlayer = null
        isPlaying = false
    }

    private fun updateMusicUi() {
        val cur = playlist.getOrElse(trackIdx) { defaultPlaylist[0] }
        tvTrack?.text = cur.title
        tvArtist?.text = cur.artist
        playGlyph?.let {
            it.kind = if (isPlaying) GlyphKind.PAUSE else GlyphKind.PLAY
            it.invalidate()
        }
        eqView?.active = isPlaying
        vinylView?.spinning = isPlaying
        waveView?.active = isPlaying
        renderTrackList()
    }

    // ------------------------------------------------------------------ UI Helpers

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
                .setInterpolator(DecelerateInterpolator()).start()
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

// ---------------------------------------------------------------------- Custom Animated Music Views

internal enum class GlyphKind { PLAY, PAUSE, PREV, NEXT, CLOSE }

internal class GlyphView(ctx: Context, var kind: GlyphKind, tint: Int) : View(ctx) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tint; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
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

/** Spinning Vinyl Disc + Pulsing Neon Halo + Center Album Cover Art */
internal class VinylDiscView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val rect = RectF()
    private var angle = 0f
    private var pulse = 0f
    private var anim: ValueAnimator? = null
    var artBitmap: Bitmap? = null

    var spinning: Boolean = false
        set(value) {
            field = value
            if (value) startSpin() else stopSpin()
        }

    private fun startSpin() {
        if (anim != null) return
        anim = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 6000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                angle = it.animatedValue as Float
                pulse = abs(sin(Math.toRadians(angle.toDouble() * 2.0))).toFloat()
                invalidate()
            }
            start()
        }
    }

    private fun stopSpin() {
        anim?.cancel()
        anim = null
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stopSpin()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxR = min(cx, cy) * 0.90f
        if (maxR <= 0f) return

        // Pulsing outer neon aura ring while playing
        if (spinning) {
            paint.style = Paint.Style.FILL
            paint.shader = RadialGradient(
                cx, cy, maxR * (1.04f + 0.08f * pulse),
                intArrayOf(Color.parseColor("#66FF6B3D"), Color.parseColor("#446366F1"), Color.TRANSPARENT),
                floatArrayOf(0.6f, 0.85f, 1f), Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, maxR * 1.10f, paint)
            paint.shader = null
        }

        val save = c.save()
        c.rotate(angle, cx, cy)

        // Vinyl disc body
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#111218")
        c.drawCircle(cx, cy, maxR, paint)

        // Metallic sheen & grooves
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(45, 255, 255, 255)
        c.drawCircle(cx, cy, maxR * 0.86f, paint)
        c.drawCircle(cx, cy, maxR * 0.72f, paint)
        c.drawCircle(cx, cy, maxR * 0.58f, paint)

        // Center Album Cover or Gradient Label
        val labelR = maxR * 0.44f
        val bmp = artBitmap
        if (bmp != null) {
            val s2 = c.save()
            clipPath.reset()
            clipPath.addCircle(cx, cy, labelR, Path.Direction.CW)
            c.clipPath(clipPath)
            rect.set(cx - labelR, cy - labelR, cx + labelR, cy + labelR)
            c.drawBitmap(bmp, null, rect, paint)
            c.restoreToCount(s2)
        } else {
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                cx - labelR, cy - labelR, cx + labelR, cy + labelR,
                intArrayOf(Color.parseColor("#FF6B3D"), Color.parseColor("#6366F1")),
                null, Shader.TileMode.CLAMP
            )
            c.drawCircle(cx, cy, labelR, paint)
            paint.shader = null
        }

        // Spindle hole
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#181924")
        c.drawCircle(cx, cy, maxR * 0.09f, paint)
        c.restoreToCount(save)
    }
}

/** 28-Bar Animated Audio Waveform Visualizer */
internal class SpectrumWaveView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var anim: ValueAnimator? = null

    var active: Boolean = false
        set(value) {
            field = value
            if (value) startAnim() else stopAnim()
        }

    private fun startAnim() {
        if (anim != null) return
        anim = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            duration = 1100
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

    override fun onDetachedFromWindow() {
        stopAnim()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val count = 28
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val step = w / count
        val barW = step * 0.58f
        paint.shader = LinearGradient(
            0f, 0f, w, 0f,
            intArrayOf(Color.parseColor("#FF6B3D"), Color.parseColor("#EC4899"), Color.parseColor("#6366F1")),
            null, Shader.TileMode.CLAMP
        )
        for (i in 0 until count) {
            val frac = if (active) {
                val wave1 = abs(sin(phase + i * 0.45f))
                val wave2 = abs(cos(phase * 1.6f - i * 0.25f))
                (0.18f + 0.82f * (wave1 * 0.65f + wave2 * 0.35f)).toFloat()
            } else {
                0.20f
            }
            val bh = h * frac
            val left = i * step + (step - barW) / 2f
            val top = (h - bh) / 2f
            c.drawRoundRect(left, top, left + barW, top + bh, barW / 2f, barW / 2f, paint)
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
