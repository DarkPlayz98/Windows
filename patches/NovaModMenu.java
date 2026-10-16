package net.kdt.pojavlaunch;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NovaModMenu {
    private static final String PREFS_NAME = "nova_sense_bento_prefs";
    public static volatile String lastXboxGamertag = null;
    private static volatile Context appContext = null;

    private static MediaPlayer phonkPlayer = null;
    private static WebView ytAudioWebView = null;
    private static int currentTrackIdx = 0;
    private static boolean isPhonkPlaying = false;
    private static String selectedMcVersion = "1.0";
    private static String activeSidebarTab = "Dashboard";

    // =========================================================================
    // REAL YOUTUBE PHONK TRACKLIST (ASSET MP3 + STREAM MIRROR + YT EMBED ID)
    // =========================================================================
    private static final String[] PHONK_ASSET_FILES = new String[]{
        "phonk_01.mp3",
        "phonk_02.mp3",
        "phonk_03.mp3",
        "phonk_04.mp3",
        "phonk_05.mp3",
        "phonk_06.mp3"
    };

    private static final String[] PHONK_TITLES = new String[]{
        "Ease into Phonk  •  Murder In My Mind",
        "Close Eyes  •  DVRST",
        "Sahara  •  Hensonn",
        "Metamorphosis  •  Interworld",
        "Phonky Town  •  PlayaPhonk",
        "Disaster  •  KSLV Noh"
    };

    private static final String[] PHONK_SUBTITLES = new String[]{
        "Kordhell  •  2 Min",
        "DVRST  •  2 Min",
        "Hensonn  •  3 Min",
        "Interworld  •  2 Min",
        "PlayaPhonk  •  2 Min",
        "KSLV Noh  •  2 Min"
    };

    private static final String[] PHONK_YT_IDS = new String[]{
        "w-sQRS-Lc9k",
        "ao4RCon11eY",
        "pIZ0QRWK0zg",
        "NS943ddQ86o",
        "b3rF_W9CRfI",
        "Step4F9T3nQ"
    };

    private static final String[] PHONK_STREAM_URLS = new String[]{
        "https://ia801509.us.archive.org/10/items/kordhell-murder-in-my-mind/KORDHELL%20-%20MURDER%20IN%20MY%20MIND.mp3",
        "https://ia802302.us.archive.org/27/items/dvrst-close-eyes_202204/DVRST%20-%20Close%20Eyes.mp3",
        "https://ia601405.us.archive.org/19/items/hensonn-sahara_202205/Hensonn%20-%20Sahara.mp3",
        "https://ia801509.us.archive.org/10/items/kordhell-murder-in-my-mind/KORDHELL%20-%20MURDER%20IN%20MY%20MIND.mp3",
        "https://ia802302.us.archive.org/27/items/dvrst-close-eyes_202204/DVRST%20-%20Close%20Eyes.mp3",
        "https://ia601405.us.archive.org/19/items/hensonn-sahara_202205/Hensonn%20-%20Sahara.mp3"
    };

    private static final String MS_OAUTH_URL =
        "https://login.live.com/oauth20_authorize.srf"
        + "?client_id=00000000402b5328"
        + "&response_type=code"
        + "&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL"
        + "&redirect_uri=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf";

    // =========================================================================
    // ENTRY POINT HOOKED INTO LAUNCHER ACTIVITY & MAIN ACTIVITY
    // =========================================================================
    public static void attach(final Activity activity) {
        if (activity == null) return;
        appContext = activity.getApplicationContext();
        activity.setTitle("NovaLauncher");

        try {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } catch (Throwable ignored) {}

        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedTag = prefs.getString("xbox_gamertag", null);
        if (savedTag != null && !savedTag.isEmpty()) {
            lastXboxGamertag = savedTag;
        }
        selectedMcVersion = prefs.getString("selected_mc_version", "1.0");

        // Stop Phonk install radio automatically once the actual Minecraft GL game window starts
        if (activity.getClass().getSimpleName().contains("MainActivity")
                || activity.getClass().getSimpleName().contains("JVMActivity")) {
            stopPhonk();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                ensureGuestAccountExists(activity);
                extractPreloadedVersions(activity, false);
                syncSelectedVersionToLauncher(activity, selectedMcVersion);
            }
        }).start();

        try {
            Window w = activity.getWindow();
            w.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#ECEAEF")));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                w.setStatusBarColor(Color.parseColor("#ECEAEF"));
                w.setNavigationBarColor(Color.parseColor("#E5D4E8"));
            }
        } catch (Throwable ignored) {}

        final View decor = activity.getWindow().getDecorView();
        decor.post(new Runnable() {
            @Override
            public void run() {
                injectSenseBentoDashboard(activity);
            }
        });
    }

    // =========================================================================
    // CUSTOM VECTOR LOGO & ICON ENGINE (22 VECTOR ICONS - ZERO EMOJIS)
    // =========================================================================
    public static final int ICON_SENSE_ORB = 0;
    public static final int ICON_GRID_ORANGE = 1;
    public static final int ICON_PLAY_CIRCLE = 2;
    public static final int ICON_EASEL_MODS = 3;
    public static final int ICON_COMMUNITY = 4;
    public static final int ICON_DOWNLOAD = 5;
    public static final int ICON_ARROW_UP_RIGHT = 6;
    public static final int ICON_SEARCH = 7;
    public static final int ICON_BELL = 8;
    public static final int ICON_USER = 9;
    public static final int ICON_PLAY_SOLID = 10;
    public static final int ICON_PAUSE_SOLID = 11;
    public static final int ICON_BADGE_PREMIUM = 12;
    public static final int ICON_SHARE = 13;
    public static final int ICON_STAR = 14;
    public static final int ICON_LOGOUT = 15;
    public static final int ICON_SEND = 16;
    public static final int ICON_MIC = 17;
    public static final int ICON_BOOKMARK = 18;
    public static final int ICON_THUMB_UP = 19;
    public static final int ICON_THUMB_DOWN = 20;
    public static final int ICON_LEAF = 21;

    public static class SenseVectorIcon extends View {
        private int type;
        private int color;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        public SenseVectorIcon(Context ctx, int type, String hexColor) {
            super(ctx);
            this.type = type;
            this.color = Color.parseColor(hexColor);
        }

        public void setIconType(int newType) {
            this.type = newType;
            invalidate();
        }

        public void setIconColor(String hexColor) {
            this.color = Color.parseColor(hexColor);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w / 2f, cy = h / 2f;
            float s = Math.min(w, h);
            float st = Math.max(1.7f, s * 0.095f);

            p.reset();
            p.setAntiAlias(true);
            p.setColor(color);
            p.setStrokeWidth(st);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);

            switch (type) {
                case ICON_SENSE_ORB: {
                    float r = s * 0.43f;
                    // Soft warm coral drop shadow below orb
                    p.setStyle(Paint.Style.FILL);
                    p.setShader(new RadialGradient(cx, cy + r * 0.38f, r * 1.15f,
                            new int[]{Color.parseColor("#66FF7A59"), Color.TRANSPARENT},
                            new float[]{0f, 1f}, Shader.TileMode.CLAMP));
                    c.drawCircle(cx, cy + r * 0.26f, r * 1.05f, p);

                    // 3D Peach/Coral/Rose Sphere matching Screenshot 2
                    p.setShader(new RadialGradient(cx - r * 0.28f, cy - r * 0.28f, r * 1.32f,
                            new int[]{
                                Color.parseColor("#FFF2E8"),
                                Color.parseColor("#FF9E7D"),
                                Color.parseColor("#F25C54"),
                                Color.parseColor("#D94E73")
                            },
                            new float[]{0f, 0.34f, 0.74f, 1f}, Shader.TileMode.CLAMP));
                    c.drawCircle(cx, cy, r, p);

                    // Subtle rim highlight
                    p.setShader(null);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(1f, s * 0.03f));
                    p.setColor(Color.parseColor("#55FFFFFF"));
                    c.drawCircle(cx, cy, r, p);
                    break;
                }
                case ICON_GRID_ORANGE: {
                    p.setStyle(Paint.Style.FILL);
                    float pad = s * 0.18f;
                    float gap = s * 0.10f;
                    float box = (s - pad * 2f - gap) / 2f;
                    float r = box * 0.25f;
                    rect.set(pad, pad, pad + box, pad + box);
                    c.drawRoundRect(rect, r, r, p);
                    rect.set(pad + box + gap, pad, pad + box * 2f + gap, pad + box);
                    c.drawRoundRect(rect, r, r, p);
                    rect.set(pad, pad + box + gap, pad + box, pad + box * 2f + gap);
                    c.drawRoundRect(rect, r, r, p);
                    rect.set(pad + box + gap, pad + box + gap, pad + box * 2f + gap, pad + box * 2f + gap);
                    c.drawRoundRect(rect, r, r, p);
                    break;
                }
                case ICON_PLAY_CIRCLE: {
                    p.setStyle(Paint.Style.STROKE);
                    c.drawCircle(cx, cy, s * 0.38f, p);
                    p.setStyle(Paint.Style.FILL);
                    path.reset();
                    path.moveTo(cx - s * 0.09f, cy - s * 0.14f);
                    path.lineTo(cx + s * 0.16f, cy);
                    path.lineTo(cx - s * 0.09f, cy + s * 0.14f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case ICON_EASEL_MODS: {
                    p.setStyle(Paint.Style.STROKE);
                    rect.set(s * 0.20f, s * 0.20f, s * 0.80f, s * 0.60f);
                    c.drawRoundRect(rect, s * 0.07f, s * 0.07f, p);
                    c.drawLine(s * 0.16f, s * 0.20f, s * 0.84f, s * 0.20f, p);
                    c.drawLine(cx, s * 0.60f, cx, s * 0.84f, p);
                    c.drawLine(cx - s * 0.18f, s * 0.84f, cx + s * 0.18f, s * 0.84f, p);
                    break;
                }
                case ICON_COMMUNITY: {
                    p.setStyle(Paint.Style.STROKE);
                    c.drawCircle(s * 0.37f, s * 0.35f, s * 0.13f, p);
                    c.drawCircle(s * 0.66f, s * 0.37f, s * 0.11f, p);
                    rect.set(s * 0.16f, s * 0.53f, s * 0.58f, s * 0.88f);
                    c.drawArc(rect, 190f, 160f, false, p);
                    rect.set(s * 0.46f, s * 0.55f, s * 0.84f, s * 0.88f);
                    c.drawArc(rect, 190f, 160f, false, p);
                    break;
                }
                case ICON_DOWNLOAD: {
                    p.setStyle(Paint.Style.STROKE);
                    c.drawLine(cx, s * 0.18f, cx, s * 0.62f, p);
                    c.drawLine(cx - s * 0.16f, s * 0.46f, cx, s * 0.62f, p);
                    c.drawLine(cx + s * 0.16f, s * 0.46f, cx, s * 0.62f, p);
                    c.drawLine(s * 0.22f, s * 0.78f, s * 0.78f, s * 0.78f, p);
                    c.drawLine(s * 0.22f, s * 0.66f, s * 0.22f, s * 0.78f, p);
                    c.drawLine(s * 0.78f, s * 0.66f, s * 0.78f, s * 0.78f, p);
                    break;
                }
                case ICON_ARROW_UP_RIGHT: {
                    p.setStyle(Paint.Style.STROKE);
                    c.drawLine(s * 0.32f, s * 0.68f, s * 0.68f, s * 0.32f, p);
                    c.drawLine(s * 0.42f, s * 0.32f, s * 0.68f, s * 0.32f, p);
                    c.drawLine(s * 0.68f, s * 0.32f, s * 0.68f, s * 0.58f, p);
                    break;
                }
                case ICON_SEARCH: {
                    p.setStyle(Paint.Style.STROKE);
                    c.drawCircle(s * 0.44f, s * 0.44f, s * 0.22f, p);
                    c.drawLine(s * 0.60f, s * 0.60f, s * 0.78f, s * 0.78f, p);
                    break;
                }
                case ICON_BELL: {
                    p.setStyle(Paint.Style.STROKE);
                    rect.set(s * 0.28f, s * 0.22f, s * 0.72f, s * 0.68f);
                    c.drawArc(rect, 180f, 180f, false, p);
                    c.drawLine(s * 0.28f, s * 0.45f, s * 0.23f, s * 0.68f, p);
                    c.drawLine(s * 0.72f, s * 0.45f, s * 0.77f, s * 0.68f, p);
                    c.drawLine(s * 0.20f, s * 0.68f, s * 0.80f, s * 0.68f, p);
                    c.drawCircle(cx, s * 0.78f, s * 0.05f, p);
                    break;
                }
                case ICON_USER: {
                    p.setStyle(Paint.Style.STROKE);
                    c.drawCircle(cx, s * 0.36f, s * 0.15f, p);
                    rect.set(s * 0.22f, s * 0.56f, s * 0.78f, s * 0.96f);
                    c.drawArc(rect, 195f, 150f, false, p);
                    break;
                }
                case ICON_PLAY_SOLID: {
                    p.setStyle(Paint.Style.FILL);
                    path.reset();
                    path.moveTo(s * 0.35f, s * 0.26f);
                    path.lineTo(s * 0.74f, cy);
                    path.lineTo(s * 0.35f, s * 0.74f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case ICON_PAUSE_SOLID: {
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(st * 1.3f);
                    c.drawLine(s * 0.36f, s * 0.28f, s * 0.36f, s * 0.72f, p);
                    c.drawLine(s * 0.64f, s * 0.28f, s * 0.64f, s * 0.72f, p);
                    break;
                }
                case ICON_BADGE_PREMIUM: {
                    p.setStyle(Paint.Style.STROKE);
                    rect.set(s * 0.20f, s * 0.20f, s * 0.80f, s * 0.80f);
                    c.drawRoundRect(rect, s * 0.14f, s * 0.14f, p);
                    c.drawCircle(cx, s * 0.42f, s * 0.10f, p);
                    rect.set(s * 0.32f, s * 0.56f, s * 0.68f, s * 0.80f);
                    c.drawArc(rect, 195f, 150f, false, p);
                    break;
                }
                case ICON_SHARE: {
                    p.setStyle(Paint.Style.STROKE);
                    path.reset();
                    path.moveTo(s * 0.54f, s * 0.26f);
                    path.lineTo(s * 0.78f, s * 0.46f);
                    path.lineTo(s * 0.54f, s * 0.66f);
                    c.drawPath(path, p);
                    rect.set(s * 0.22f, s * 0.46f, s * 0.76f, s * 0.88f);
                    c.drawArc(rect, 185f, 105f, false, p);
                    break;
                }
                case ICON_STAR: {
                    p.setStyle(Paint.Style.STROKE);
                    path.reset();
                    for (int i = 0; i < 10; i++) {
                        double ang = Math.toRadians(i * 36 - 90);
                        float rad = (i % 2 == 0) ? s * 0.36f : s * 0.16f;
                        float px = cx + (float) (rad * Math.cos(ang));
                        float py = cy + (float) (rad * Math.sin(ang));
                        if (i == 0) path.moveTo(px, py);
                        else path.lineTo(px, py);
                    }
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case ICON_LOGOUT: {
                    p.setStyle(Paint.Style.STROKE);
                    path.reset();
                    path.moveTo(s * 0.46f, s * 0.22f);
                    path.lineTo(s * 0.24f, s * 0.22f);
                    path.lineTo(s * 0.24f, s * 0.78f);
                    path.lineTo(s * 0.46f, s * 0.78f);
                    c.drawPath(path, p);
                    c.drawLine(s * 0.40f, cy, s * 0.78f, cy, p);
                    c.drawLine(s * 0.64f, cy - s * 0.14f, s * 0.78f, cy, p);
                    c.drawLine(s * 0.64f, cy + s * 0.14f, s * 0.78f, cy, p);
                    break;
                }
                case ICON_SEND: {
                    p.setStyle(Paint.Style.STROKE);
                    path.reset();
                    path.moveTo(s * 0.18f, s * 0.45f);
                    path.lineTo(s * 0.80f, s * 0.20f);
                    path.lineTo(s * 0.56f, s * 0.82f);
                    path.lineTo(s * 0.44f, s * 0.54f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case ICON_MIC: {
                    p.setStyle(Paint.Style.STROKE);
                    rect.set(s * 0.36f, s * 0.18f, s * 0.64f, s * 0.56f);
                    c.drawRoundRect(rect, s * 0.14f, s * 0.14f, p);
                    rect.set(s * 0.26f, s * 0.34f, s * 0.74f, s * 0.70f);
                    c.drawArc(rect, 0f, 180f, false, p);
                    c.drawLine(cx, s * 0.70f, cx, s * 0.84f, p);
                    break;
                }
                case ICON_BOOKMARK: {
                    p.setStyle(Paint.Style.STROKE);
                    path.reset();
                    path.moveTo(s * 0.30f, s * 0.22f);
                    path.lineTo(s * 0.70f, s * 0.22f);
                    path.lineTo(s * 0.70f, s * 0.78f);
                    path.lineTo(cx, s * 0.62f);
                    path.lineTo(s * 0.30f, s * 0.78f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case ICON_THUMB_UP: {
                    p.setStyle(Paint.Style.STROKE);
                    rect.set(s * 0.20f, s * 0.44f, s * 0.36f, s * 0.78f);
                    c.drawRect(rect, p);
                    path.reset();
                    path.moveTo(s * 0.36f, s * 0.44f);
                    path.lineTo(s * 0.52f, s * 0.22f);
                    path.lineTo(s * 0.60f, s * 0.26f);
                    path.lineTo(s * 0.56f, s * 0.44f);
                    path.lineTo(s * 0.78f, s * 0.44f);
                    path.lineTo(s * 0.72f, s * 0.78f);
                    path.lineTo(s * 0.36f, s * 0.78f);
                    c.drawPath(path, p);
                    break;
                }
                case ICON_THUMB_DOWN: {
                    p.setStyle(Paint.Style.STROKE);
                    rect.set(s * 0.20f, s * 0.22f, s * 0.36f, s * 0.56f);
                    c.drawRect(rect, p);
                    path.reset();
                    path.moveTo(s * 0.36f, s * 0.56f);
                    path.lineTo(s * 0.52f, s * 0.78f);
                    path.lineTo(s * 0.60f, s * 0.74f);
                    path.lineTo(s * 0.56f, s * 0.56f);
                    path.lineTo(s * 0.78f, s * 0.56f);
                    path.lineTo(s * 0.72f, s * 0.22f);
                    path.lineTo(s * 0.36f, s * 0.22f);
                    c.drawPath(path, p);
                    break;
                }
                case ICON_LEAF: {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.parseColor("#84CC16"));
                    path.reset();
                    path.moveTo(s * 0.25f, s * 0.75f);
                    path.quadTo(s * 0.25f, s * 0.25f, s * 0.78f, s * 0.22f);
                    path.quadTo(s * 0.75f, s * 0.75f, s * 0.25f, s * 0.75f);
                    c.drawPath(path, p);
                    break;
                }
            }
        }
    }

    // =========================================================================
    // PROCEDURAL BEZIER & SHADER ARTWORK CARDS (1:1 SCREENSHOT 2 ARTWORK)
    // =========================================================================
    private static class SenseArtworkCard extends FrameLayout {
        private final int styleType;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clipPath = new Path();
        private final Path wavePath = new Path();

        // styleType:
        // 0 = Meditate (Pink/Violet Silk Waves)
        // 1 = Sleep (Misty Mountain Silhouettes)
        // 2 = Music (Pastel Green/Peach Diagonal Motion Blur)
        // 3 = Move (Periwinkle/Coral Vertical Aurora)
        // 4 = Dotted Greeting / Chat Card
        // 5 = Breath of Balance (Orange/Lavender Floral Petal Bloom)
        public SenseArtworkCard(Context ctx, int styleType) {
            super(ctx);
            this.styleType = styleType;
            setWillNotDraw(false);
            dotPaint.setColor(Color.parseColor("#1AD9778A"));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float r = dp(getContext(), 22);
            float border = dp(getContext(), styleType == 4 ? 1 : 3);

            rect.set(0, 0, w, h);
            clipPath.reset();
            clipPath.addRoundRect(rect, r, r, Path.Direction.CW);

            int save = canvas.save();
            canvas.clipPath(clipPath);

            if (styleType == 0) {
                // 0: MEDITATE CARD - Pink/Violet Sky + Flowing Silk Bezier Ribbons + White Bottom Fade
                paint.setShader(new LinearGradient(0, 0, w, h,
                        new int[]{
                            Color.parseColor("#A49AC7"),
                            Color.parseColor("#E598BA"),
                            Color.parseColor("#FAD4E0"),
                            Color.parseColor("#FDF8FA")
                        },
                        new float[]{0f, 0.36f, 0.72f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);

                // Silk Bezier Wave 1
                wavePath.reset();
                wavePath.moveTo(0, h * 0.45f);
                wavePath.cubicTo(w * 0.30f, h * 0.15f, w * 0.65f, h * 0.65f, w, h * 0.28f);
                wavePath.lineTo(w, h);
                wavePath.lineTo(0, h);
                wavePath.close();
                wavePaint.setStyle(Paint.Style.FILL);
                wavePaint.setShader(new LinearGradient(0, h * 0.2f, w, h,
                        new int[]{Color.parseColor("#88F472B6"), Color.parseColor("#DDEEE5F2"), Color.parseColor("#FFFFFFFF")},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawPath(wavePath, wavePaint);

                // Silk Bezier Wave 2
                wavePath.reset();
                wavePath.moveTo(0, h * 0.62f);
                wavePath.cubicTo(w * 0.38f, h * 0.42f, w * 0.72f, h * 0.80f, w, h * 0.52f);
                wavePath.lineTo(w, h);
                wavePath.lineTo(0, h);
                wavePath.close();
                wavePaint.setShader(new LinearGradient(0, h * 0.45f, 0, h,
                        new int[]{Color.parseColor("#AAFB923C"), Color.parseColor("#EEFAF5F8"), Color.parseColor("#FFFFFFFF")},
                        new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawPath(wavePath, wavePaint);

            } else if (styleType == 1) {
                // 1: SLEEP CARD - Soft Sage/Mint Sky + Misty Mountain Ridge Silhouettes + Fog Fade
                paint.setShader(new LinearGradient(0, 0, 0, h,
                        new int[]{
                            Color.parseColor("#D4EAE4"),
                            Color.parseColor("#E6E5DD"),
                            Color.parseColor("#FBF9F6")
                        },
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);

                // Distant Green/Sage Mountain Ridge
                wavePath.reset();
                wavePath.moveTo(0, h * 0.55f);
                wavePath.quadTo(w * 0.36f, h * 0.18f, w * 0.75f, h * 0.52f);
                wavePath.lineTo(w, h * 0.65f);
                wavePath.lineTo(w, h);
                wavePath.lineTo(0, h);
                wavePath.close();
                wavePaint.setShader(new LinearGradient(0, h * 0.25f, 0, h,
                        new int[]{Color.parseColor("#99B8CBB8"), Color.parseColor("#EEFAF8F5"), Color.parseColor("#FFFFFFFF")},
                        new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawPath(wavePath, wavePaint);

                // Warm Peach Left Tree/Mist Glow
                wavePaint.setShader(new RadialGradient(w * 0.18f, h * 0.55f, w * 0.45f,
                        new int[]{Color.parseColor("#99FBBF94"), Color.TRANSPARENT},
                        new float[]{0f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, wavePaint);

            } else if (styleType == 2) {
                // 2: MUSIC CARD - Vibrant Green & Peach Diagonal Motion Blur Streaks
                paint.setShader(new LinearGradient(0, 0, w, h,
                        new int[]{
                            Color.parseColor("#F7A992"),
                            Color.parseColor("#7DD389"),
                            Color.parseColor("#CEF0D4"),
                            Color.parseColor("#FFFFFF")
                        },
                        new float[]{0f, 0.42f, 0.75f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);

                // Diagonal Peach Streak
                wavePath.reset();
                wavePath.moveTo(0, h * 0.28f);
                wavePath.quadTo(w * 0.45f, h * 0.48f, w, h * 0.82f);
                wavePath.lineTo(w, h);
                wavePath.lineTo(0, h);
                wavePath.close();
                wavePaint.setShader(new LinearGradient(0, h * 0.35f, w, h,
                        new int[]{Color.parseColor("#BBFDBA8C"), Color.parseColor("#EEFAFCF9"), Color.parseColor("#FFFFFFFF")},
                        new float[]{0f, 0.65f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawPath(wavePath, wavePaint);

            } else if (styleType == 3) {
                // 3: MOVE CARD - Soft Periwinkle Blue & Coral Pink Vertical Aurora
                paint.setShader(new LinearGradient(0, 0, w, h,
                        new int[]{
                            Color.parseColor("#9AA5D6"),
                            Color.parseColor("#F29FA6"),
                            Color.parseColor("#D4E4F2"),
                            Color.parseColor("#FFFFFF")
                        },
                        new float[]{0f, 0.40f, 0.78f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);

                wavePaint.setShader(new RadialGradient(w * 0.50f, h * 0.42f, w * 0.55f,
                        new int[]{Color.parseColor("#88818CF8"), Color.parseColor("#55F472B6"), Color.TRANSPARENT},
                        new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, wavePaint);

            } else if (styleType == 4) {
                // 4: DOTTED GREETING / CHAT CARD - Frosted #F5F3F7 with subtle dot grid
                canvas.drawColor(Color.parseColor("#F2F6F4F8"));
                int step = dp(getContext(), 9);
                float dr = dp(getContext(), 1);
                for (int y = step; y < h; y += step) {
                    for (int x = step; x < w; x += step) {
                        canvas.drawCircle(x, y, dr, dotPaint);
                    }
                }
            } else if (styleType == 5) {
                // 5: BREATH OF BALANCE CARD - Lavender & Vibrant Orange Floral Petal Bloom
                paint.setShader(new LinearGradient(0, 0, w, h,
                        new int[]{
                            Color.parseColor("#C5C3E8"),
                            Color.parseColor("#F5B092"),
                            Color.parseColor("#FA6B1E"),
                            Color.parseColor("#E67E50")
                        },
                        new float[]{0f, 0.42f, 0.78f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);

                // Soft White/Peach Petal Curves
                for (int i = 0; i < 3; i++) {
                    wavePath.reset();
                    float startX = w * (0.18f + i * 0.16f);
                    wavePath.moveTo(w * 0.65f, h * 0.90f);
                    wavePath.quadTo(startX, h * 0.40f, w * (0.28f + i * 0.22f), h * 0.14f);
                    wavePath.quadTo(w * (0.58f + i * 0.14f), h * 0.42f, w * 0.65f, h * 0.90f);
                    wavePaint.setShader(new LinearGradient(startX, h * 0.15f, w * 0.7f, h * 0.9f,
                            new int[]{Color.parseColor("#BBFFFFFF"), Color.parseColor("#88FDBA74"), Color.parseColor("#44EA580C")},
                            new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                    canvas.drawPath(wavePath, wavePaint);
                }
            }

            canvas.restoreToCount(save);

            // Crisp White Outer Border matching Screenshot 2
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(border);
            paint.setColor(Color.parseColor("#FFFFFF"));
            rect.set(border / 2f, border / 2f, w - border / 2f, h - border / 2f);
            canvas.drawRoundRect(rect, r, r, paint);

            super.onDraw(canvas);
        }
    }

    // =========================================================================
    // SENSE PASTEL AMBIENT CANVAS ROOT (EXACT SCREENSHOT 2 BACKGROUND)
    // =========================================================================
    private static class SenseCanvasRoot extends FrameLayout {
        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowLeft = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowRight = new Paint(Paint.ANTI_ALIAS_FLAG);

        public SenseCanvasRoot(Context ctx) {
            super(ctx);
            setWillNotDraw(false);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;

            bgPaint.setShader(new LinearGradient(0, 0, w, h,
                    new int[]{
                        Color.parseColor("#E8E8EC"),
                        Color.parseColor("#F1EDF3"),
                        Color.parseColor("#EADCE8")
                    },
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, bgPaint);

            glowLeft.setShader(new RadialGradient(w * 0.25f, h * 0.95f, w * 0.55f,
                    new int[]{Color.parseColor("#88F4B8C8"), Color.TRANSPARENT},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, glowLeft);

            glowRight.setShader(new RadialGradient(w * 0.80f, h * 0.92f, w * 0.55f,
                    new int[]{Color.parseColor("#88C8B2F0"), Color.TRANSPARENT},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, glowRight);

            super.onDraw(canvas);
        }
    }

    // =========================================================================
    // MAIN SENSE BENTO DASHBOARD INJECTION (EXACT SCREENSHOT 2 LAYOUT)
    // =========================================================================
    public static void injectSenseBentoDashboard(final Activity activity) {
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        final ViewGroup rootVg = (ViewGroup) decor;

        View old = rootVg.findViewWithTag("NOVA_SENSE_ROOT");
        if (old != null) rootVg.removeView(old);

        final SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        final boolean isGuest = prefs.getBoolean("is_guest", getResolvedGamertag().equals("Guest"));
        final String activeUser = getResolvedGamertag();

        final SenseCanvasRoot senseRoot = new SenseCanvasRoot(activity);
        senseRoot.setTag("NOVA_SENSE_ROOT");
        senseRoot.setClickable(true);

        // Master 3-Column Horizontal Bento Layout
        LinearLayout masterRow = new LinearLayout(activity);
        masterRow.setOrientation(LinearLayout.HORIZONTAL);
        int pad = dp(activity, 14);
        masterRow.setPadding(pad, dp(activity, 10), pad, pad);
        senseRoot.addView(masterRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // =====================================================================
        // COLUMN 1 (LEFT): SIDEBAR CARD + BOTTOM "SLEEP / GUEST MODE" CARD
        // =====================================================================
        LinearLayout colLeft = new LinearLayout(activity);
        colLeft.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams col1Lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0.23f
        );
        col1Lp.rightMargin = dp(activity, 12);
        colLeft.setLayoutParams(col1Lp);
        masterRow.addView(colLeft);

        // 1A: Top Frosted Sidebar Card ("Sense" -> "NovaLauncher")
        LinearLayout sidebarCard = makeFrostedCard(activity, "#EBF7F6F9", "#FFFFFF", 22, 12);
        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.68f
        );
        sbLp.bottomMargin = dp(activity, 10);
        sidebarCard.setLayoutParams(sbLp);
        colLeft.addView(sidebarCard);

        // Brand Row: 3D Peach Orb + "NovaLauncher"
        LinearLayout brandRow = new LinearLayout(activity);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(dp(activity, 2), dp(activity, 2), 0, dp(activity, 10));

        SenseVectorIcon orbLogo = new SenseVectorIcon(activity, ICON_SENSE_ORB, "#FF6B3D");
        LinearLayout.LayoutParams orbLp = new LinearLayout.LayoutParams(dp(activity, 28), dp(activity, 28));
        orbLp.rightMargin = dp(activity, 8);
        brandRow.addView(orbLogo, orbLp);

        TextView brandTv = new TextView(activity);
        brandTv.setText("NovaLauncher");
        brandTv.setTextColor(Color.parseColor("#1E1E24"));
        brandTv.setTextSize(15f);
        brandTv.setTypeface(Typeface.DEFAULT_BOLD);
        brandRow.addView(brandTv);
        sidebarCard.addView(brandRow);

        TextView genHeader = new TextView(activity);
        genHeader.setText("GENERAL");
        genHeader.setTextColor(Color.parseColor("#8E8E98"));
        genHeader.setTextSize(8.5f);
        genHeader.setTypeface(Typeface.DEFAULT_BOLD);
        genHeader.setPadding(dp(activity, 6), 0, 0, dp(activity, 4));
        sidebarCard.addView(genHeader);

        // Sidebar Items matching Screenshot 2
        LinearLayout navDash = makeSenseSidebarItem(activity, ICON_GRID_ORANGE, "Dashboard", activeSidebarTab.equals("Dashboard"));
        navDash.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeSidebarTab = "Dashboard";
                injectSenseBentoDashboard(activity);
            }
        });
        sidebarCard.addView(navDash);

        LinearLayout navSessions = makeSenseSidebarItem(activity, ICON_PLAY_CIRCLE, "My Sessions", activeSidebarTab.equals("Sessions"));
        navSessions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showVersionPickerModal(activity);
            }
        });
        sidebarCard.addView(navSessions);

        LinearLayout navPopular = makeSenseSidebarItem(activity, ICON_EASEL_MODS, "Popular Mods", activeSidebarTab.equals("Mods"));
        navPopular.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showModernModHubModal(activity, "both", "");
            }
        });
        sidebarCard.addView(navPopular);

        LinearLayout navMsLogin = makeSenseSidebarItem(activity, ICON_COMMUNITY, "Microsoft Login", false);
        navMsLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMediumMicrosoftLoginModal(activity);
            }
        });
        sidebarCard.addView(navMsLogin);

        LinearLayout navDownload = makeSenseSidebarItem(activity, ICON_DOWNLOAD, "Download", false);
        navDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        extractPreloadedVersions(activity, true);
                    }
                }).start();
            }
        });
        sidebarCard.addView(navDownload);

        // 1B: Bottom-Left Misty Mountain Artwork Card ("Sleep" in Screenshot 2 -> Guest / Offline Toggle)
        SenseArtworkCard sleepCard = new SenseArtworkCard(activity, 1);
        LinearLayout.LayoutParams slpLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.32f
        );
        sleepCard.setLayoutParams(slpLp);
        TextView sleepLabel = new TextView(activity);
        sleepLabel.setText(isGuest ? "Guest Mode" : "Online Mode");
        sleepLabel.setTextColor(Color.parseColor("#1E1E24"));
        sleepLabel.setTextSize(13.5f);
        sleepLabel.setTypeface(Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams slpTxtLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START
        );
        slpTxtLp.leftMargin = dp(activity, 14);
        slpTxtLp.bottomMargin = dp(activity, 12);
        sleepCard.addView(sleepLabel, slpTxtLp);
        sleepCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putBoolean("is_guest", true).apply();
                writeAccountJson(activity, "Guest", false);
                syncSelectedVersionToLauncher(activity, selectedMcVersion);
                Toast.makeText(activity, "Guest Local Account Active (Singleplayer & Mods Unlocked, Multiplayer Disabled)", Toast.LENGTH_LONG).show();
                injectSenseBentoDashboard(activity);
            }
        });
        colLeft.addView(sleepCard);

        // =====================================================================
        // COLUMN 2 (CENTER): TOP PILLS + "MEDITATE" LAUNCH CARD + ORB GREETING + "OTHERS"
        // =====================================================================
        LinearLayout colCenter = new LinearLayout(activity);
        colCenter.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams col2Lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0.34f
        );
        col2Lp.rightMargin = dp(activity, 12);
        colCenter.setLayoutParams(col2Lp);
        masterRow.addView(colCenter);

        // 2A: Top 3 Filter Pills ("Mindfulness | Focus | Relaxation" -> Version Pills)
        LinearLayout topPillsRow = new LinearLayout(activity);
        topPillsRow.setOrientation(LinearLayout.HORIZONTAL);
        topPillsRow.setGravity(Gravity.CENTER_VERTICAL);
        topPillsRow.setPadding(0, 0, 0, dp(activity, 6));

        final TextView p1 = makeSenseTopFilterPill(activity, "Release 1.0", selectedMcVersion.equals("1.0"));
        final TextView p2 = makeSenseTopFilterPill(activity, "rd-132211", selectedMcVersion.equals("rd-132211"));
        final TextView p3 = makeSenseTopFilterPill(activity, "Modded 1.20.1", selectedMcVersion.equals("1.20.1"));

        p1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSelectedVersion(activity, "1.0");
            }
        });
        p2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSelectedVersion(activity, "rd-132211");
            }
        });
        p3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setSelectedVersion(activity, "1.20.1");
            }
        });

        topPillsRow.addView(p1);
        topPillsRow.addView(p2);
        topPillsRow.addView(p3);
        colCenter.addView(topPillsRow);

        // 2B: Top Pink/Violet Silk Wave Artwork Card ("Meditate" + Orange ↗ Circle Button)
        SenseArtworkCard meditateCard = new SenseArtworkCard(activity, 0);
        LinearLayout.LayoutParams medLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.29f
        );
        medLp.bottomMargin = dp(activity, 10);
        meditateCard.setLayoutParams(medLp);

        TextView medTitle = new TextView(activity);
        medTitle.setText("Play  •  " + selectedMcVersion);
        medTitle.setTextColor(Color.parseColor("#1E1E24"));
        medTitle.setTextSize(14f);
        medTitle.setTypeface(Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams mtLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START
        );
        mtLp.leftMargin = dp(activity, 14);
        mtLp.bottomMargin = dp(activity, 12);
        meditateCard.addView(medTitle, mtLp);

        FrameLayout arrowCircle = makeCircleIconButton(activity, ICON_ARROW_UP_RIGHT, "#FFFFFF", "#FF6B3D", 28);
        FrameLayout.LayoutParams acLp = new FrameLayout.LayoutParams(
                dp(activity, 28), dp(activity, 28), Gravity.BOTTOM | Gravity.END
        );
        acLp.rightMargin = dp(activity, 12);
        acLp.bottomMargin = dp(activity, 10);
        meditateCard.addView(arrowCircle, acLp);
        colCenter.addView(meditateCard);

        // 2C: Middle Dotted Greeting Card ("Good Morning Andrew, What's on your mind?")
        SenseArtworkCard greetingCard = new SenseArtworkCard(activity, 4);
        LinearLayout.LayoutParams grLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.37f
        );
        grLp.bottomMargin = dp(activity, 10);
        greetingCard.setLayoutParams(grLp);

        LinearLayout greetInner = new LinearLayout(activity);
        greetInner.setOrientation(LinearLayout.VERTICAL);
        greetInner.setGravity(Gravity.CENTER);
        greetingCard.addView(greetInner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ));

        SenseVectorIcon centerOrb = new SenseVectorIcon(activity, ICON_SENSE_ORB, "#FF6B3D");
        LinearLayout.LayoutParams cOrbLp = new LinearLayout.LayoutParams(dp(activity, 42), dp(activity, 42));
        cOrbLp.bottomMargin = dp(activity, 6);
        greetInner.addView(centerOrb, cOrbLp);

        TextView greetText = new TextView(activity);
        greetText.setText("Good Morning\n" + activeUser + ", What's on\nyour mind?");
        greetText.setGravity(Gravity.CENTER);
        greetText.setTextColor(Color.parseColor("#1E1E24"));
        greetText.setTextSize(15f);
        greetText.setTypeface(Typeface.DEFAULT_BOLD);
        greetInner.addView(greetText);
        colCenter.addView(greetingCard);

        // 2D: Bottom "OTHERS" Frosted Card (4 Rows matching Screenshot 2)
        LinearLayout othersCard = makeFrostedCard(activity, "#EBF4EFF6", "#FFFFFF", 22, 10);
        LinearLayout.LayoutParams othLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.34f
        );
        othersCard.setLayoutParams(othLp);

        TextView othHeader = new TextView(activity);
        othHeader.setText("OTHERS");
        othHeader.setTextColor(Color.parseColor("#8E8E98"));
        othHeader.setTextSize(8f);
        othHeader.setTypeface(Typeface.DEFAULT_BOLD);
        othHeader.setPadding(dp(activity, 4), 0, 0, dp(activity, 2));
        othersCard.addView(othHeader);

        LinearLayout rowPrem = makeSenseCompactRow(activity, ICON_BADGE_PREMIUM, "Microsoft Access");
        rowPrem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMediumMicrosoftLoginModal(activity);
            }
        });
        othersCard.addView(rowPrem);

        LinearLayout rowShared = makeSenseCompactRow(activity, ICON_SHARE, "Engine Settings");
        rowShared.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showEngineOverlayDialog(activity, senseRoot);
            }
        });
        othersCard.addView(rowShared);

        LinearLayout rowSaved = makeSenseCompactRow(activity, ICON_STAR, "Saved Mods (.jar)");
        rowSaved.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showModernModHubModal(activity, "installed", "");
            }
        });
        othersCard.addView(rowSaved);

        LinearLayout rowLogout = makeSenseCompactRow(activity, ICON_LOGOUT, isGuest ? "Guest Session" : "Logout to Guest");
        rowLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().putBoolean("is_guest", true).apply();
                writeAccountJson(activity, "Guest", false);
                injectSenseBentoDashboard(activity);
            }
        });
        othersCard.addView(rowLogout);
        colCenter.addView(othersCard);

        // =====================================================================
        // COLUMN 3 (RIGHT): SEARCH BAR + PHONK PILL + MUSIC/MOVE + CHAT + ASK BAR
        // =====================================================================
        LinearLayout colRight = new LinearLayout(activity);
        colRight.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams col3Lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0.43f
        );
        colRight.setLayoutParams(col3Lp);
        masterRow.addView(colRight);

        // 3A: Top Row: Search Pill + Bell Circle Button + User Circle Button
        LinearLayout searchTopRow = new LinearLayout(activity);
        searchTopRow.setOrientation(LinearLayout.HORIZONTAL);
        searchTopRow.setGravity(Gravity.CENTER_VERTICAL);
        searchTopRow.setPadding(0, 0, 0, dp(activity, 8));

        LinearLayout searchBarPill = makeFrostedCard(activity, "#F5FFFFFF", "#FFFFFF", 20, 0);
        searchBarPill.setOrientation(LinearLayout.HORIZONTAL);
        searchBarPill.setGravity(Gravity.CENTER_VERTICAL);
        searchBarPill.setPadding(dp(activity, 12), dp(activity, 5), dp(activity, 12), dp(activity, 5));
        LinearLayout.LayoutParams sbpLp = new LinearLayout.LayoutParams(0, dp(activity, 32), 1f);
        sbpLp.rightMargin = dp(activity, 8);
        searchBarPill.setLayoutParams(sbpLp);

        SenseVectorIcon sIcon = new SenseVectorIcon(activity, ICON_SEARCH, "#8E8E98");
        LinearLayout.LayoutParams siLp = new LinearLayout.LayoutParams(dp(activity, 14), dp(activity, 14));
        siLp.rightMargin = dp(activity, 6);
        searchBarPill.addView(sIcon, siLp);

        TextView sHint = new TextView(activity);
        sHint.setText("Search...");
        sHint.setTextColor(Color.parseColor("#8E8E98"));
        sHint.setTextSize(11f);
        searchBarPill.addView(sHint);
        searchBarPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showModernModHubModal(activity, "both", "");
            }
        });
        searchTopRow.addView(searchBarPill);

        FrameLayout bellCircle = makeCircleIconButton(activity, ICON_BELL, "#F5FFFFFF", "#1E1E24", 32);
        LinearLayout.LayoutParams bcLp = new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32));
        bcLp.rightMargin = dp(activity, 6);
        bellCircle.setLayoutParams(bcLp);
        bellCircle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPhonkTrackPickerModal(activity);
            }
        });
        searchTopRow.addView(bellCircle);

        FrameLayout userCircle = makeCircleIconButton(activity, ICON_USER, "#F5FFFFFF", "#1E1E24", 32);
        userCircle.setLayoutParams(new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));
        userCircle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMediumMicrosoftLoginModal(activity);
            }
        });
        searchTopRow.addView(userCircle);
        colRight.addView(searchTopRow);

        // 3B: White Pill Audio Player Card ("Ease into Sleep" -> Real YouTube Phonk Player)
        LinearLayout phonkPillCard = makeFrostedCard(activity, "#F7FFFFFF", "#FFFFFF", 28, 8);
        phonkPillCard.setOrientation(LinearLayout.HORIZONTAL);
        phonkPillCard.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ppcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 50)
        );
        ppcLp.bottomMargin = dp(activity, 10);
        phonkPillCard.setLayoutParams(ppcLp);

        SenseVectorIcon albumOrb = new SenseVectorIcon(activity, ICON_SENSE_ORB, "#FF6B3D");
        LinearLayout.LayoutParams aoLp = new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34));
        aoLp.rightMargin = dp(activity, 10);
        phonkPillCard.addView(albumOrb, aoLp);

        LinearLayout trackMeta = new LinearLayout(activity);
        trackMeta.setOrientation(LinearLayout.VERTICAL);
        final TextView trackTitleTv = new TextView(activity);
        trackTitleTv.setText(PHONK_TITLES[currentTrackIdx]);
        trackTitleTv.setTextColor(Color.parseColor("#1E1E24"));
        trackTitleTv.setTextSize(11f);
        trackTitleTv.setTypeface(Typeface.DEFAULT_BOLD);
        trackTitleTv.setSingleLine(true);
        trackMeta.addView(trackTitleTv);

        final TextView trackSubTv = new TextView(activity);
        trackSubTv.setText(PHONK_SUBTITLES[currentTrackIdx]);
        trackSubTv.setTextColor(Color.parseColor("#8E8E98"));
        trackSubTv.setTextSize(9.5f);
        trackMeta.addView(trackSubTv);
        phonkPillCard.addView(trackMeta, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final SenseVectorIcon playTriIcon = new SenseVectorIcon(
                activity, isPhonkPlaying ? ICON_PAUSE_SOLID : ICON_PLAY_SOLID, "#FF6B3D"
        );
        FrameLayout playCircleBtn = new FrameLayout(activity);
        GradientDrawable pcbBg = new GradientDrawable();
        pcbBg.setShape(GradientDrawable.OVAL);
        pcbBg.setColor(Color.parseColor("#FFFFFF"));
        pcbBg.setStroke(dp(activity, 1), Color.parseColor("#F0EBF4"));
        playCircleBtn.setBackground(pcbBg);
        playCircleBtn.addView(playTriIcon, new FrameLayout.LayoutParams(
                dp(activity, 18), dp(activity, 18), Gravity.CENTER
        ));
        playCircleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePhonk(activity, trackTitleTv, trackSubTv, playTriIcon);
            }
        });
        phonkPillCard.addView(playCircleBtn, new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));
        colRight.addView(phonkPillCard);

        // Wire Meditate/Launch Card to auto-start Real YouTube Phonk & launch selected Minecraft version
        meditateCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isPhonkPlaying) {
                    playPhonkTrack(activity, currentTrackIdx, trackTitleTv, trackSubTv, playTriIcon);
                }
                launchMinecraftSelectedVersion(activity, rootVg, senseRoot);
            }
        });

        // 3C: Middle Row: "Music" (Green/Peach Blur) + "Move" (Blue/Pink Aurora) Side-by-Side
        LinearLayout midCardsRow = new LinearLayout(activity);
        midCardsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mcrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.35f
        );
        mcrLp.bottomMargin = dp(activity, 10);
        midCardsRow.setLayoutParams(mcrLp);

        SenseArtworkCard musicCard = new SenseArtworkCard(activity, 2);
        LinearLayout.LayoutParams mcLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.58f);
        mcLp.rightMargin = dp(activity, 10);
        musicCard.setLayoutParams(mcLp);

        TextView musicCardTitle = new TextView(activity);
        musicCardTitle.setText("Music");
        musicCardTitle.setTextColor(Color.parseColor("#1E1E24"));
        musicCardTitle.setTextSize(13f);
        musicCardTitle.setTypeface(Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams mctLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START
        );
        mctLp.leftMargin = dp(activity, 14);
        mctLp.bottomMargin = dp(activity, 10);
        musicCard.addView(musicCardTitle, mctLp);
        musicCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                nextPhonk(activity, trackTitleTv, trackSubTv, playTriIcon);
            }
        });
        musicCard.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showPhonkTrackPickerModal(activity);
                return true;
            }
        });
        midCardsRow.addView(musicCard);

        SenseArtworkCard moveCard = new SenseArtworkCard(activity, 3);
        moveCard.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.42f));

        TextView moveCardTitle = new TextView(activity);
        moveCardTitle.setText("CurseForge");
        moveCardTitle.setTextColor(Color.parseColor("#1E1E24"));
        moveCardTitle.setTextSize(13f);
        moveCardTitle.setTypeface(Typeface.DEFAULT_BOLD);
        FrameLayout.LayoutParams mvtLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START
        );
        mvtLp.leftMargin = dp(activity, 12);
        mvtLp.bottomMargin = dp(activity, 10);
        moveCard.addView(moveCardTitle, mvtLp);
        moveCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showModernModHubModal(activity, "curseforge", "");
            }
        });
        midCardsRow.addView(moveCard);
        colRight.addView(midCardsRow);

        // 3D: Bottom Row: Two-Message "You / Sense" Dotted Chat Card + "Breath of Balance" Bloom Card
        LinearLayout botCardsRow = new LinearLayout(activity);
        botCardsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bcrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.49f
        );
        bcrLp.bottomMargin = dp(activity, 8);
        botCardsRow.setLayoutParams(bcrLp);

        // Left: Exact Two-Part Dotted Chat Card from Screenshot 2 ("You" + Dotted Line + "Sense" + Thumbs)
        SenseArtworkCard chatCard = new SenseArtworkCard(activity, 4);
        LinearLayout.LayoutParams ccLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.62f);
        ccLp.rightMargin = dp(activity, 10);
        chatCard.setLayoutParams(ccLp);

        LinearLayout chatInner = new LinearLayout(activity);
        chatInner.setOrientation(LinearLayout.VERTICAL);
        int cp = dp(activity, 10);
        chatInner.setPadding(cp, dp(activity, 8), cp, dp(activity, 8));
        chatCard.addView(chatInner);

        // Top Right-Aligned "You" Header with Coral Orb
        LinearLayout youHeader = new LinearLayout(activity);
        youHeader.setOrientation(LinearLayout.HORIZONTAL);
        youHeader.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        SenseVectorIcon youDot = new SenseVectorIcon(activity, ICON_SENSE_ORB, "#FF6B3D");
        LinearLayout.LayoutParams ydLp = new LinearLayout.LayoutParams(dp(activity, 11), dp(activity, 11));
        ydLp.rightMargin = dp(activity, 4);
        youHeader.addView(youDot, ydLp);
        TextView youTv = new TextView(activity);
        youTv.setText("You");
        youTv.setTextColor(Color.parseColor("#1E1E24"));
        youTv.setTextSize(8.5f);
        youTv.setTypeface(Typeface.DEFAULT_BOLD);
        youHeader.addView(youTv);
        chatInner.addView(youHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView userMsg = new TextView(activity);
        userMsg.setText("Hi, I want to install Modrinth & CurseForge\nmods for Minecraft " + selectedMcVersion + ". Can you help?");
        userMsg.setGravity(Gravity.END);
        userMsg.setTextColor(Color.parseColor("#52525B"));
        userMsg.setTextSize(8.2f);
        userMsg.setPadding(0, dp(activity, 2), 0, dp(activity, 4));
        chatInner.addView(userMsg);

        // Subtle Horizontal Divider
        View chatDiv = new View(activity);
        chatDiv.setBackgroundColor(Color.parseColor("#228E8E98"));
        LinearLayout.LayoutParams cdLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 1));
        cdLp.bottomMargin = dp(activity, 4);
        chatInner.addView(chatDiv, cdLp);

        // Bottom Left-Aligned "Sense" Header with Coral Orb
        LinearLayout senseHeader = new LinearLayout(activity);
        senseHeader.setOrientation(LinearLayout.HORIZONTAL);
        senseHeader.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        SenseVectorIcon senseDot = new SenseVectorIcon(activity, ICON_SENSE_ORB, "#FF6B3D");
        LinearLayout.LayoutParams sdLp = new LinearLayout.LayoutParams(dp(activity, 11), dp(activity, 11));
        sdLp.rightMargin = dp(activity, 4);
        senseHeader.addView(senseDot, sdLp);
        TextView senseNameTv = new TextView(activity);
        senseNameTv.setText("Nova Sense");
        senseNameTv.setTextColor(Color.parseColor("#1E1E24"));
        senseNameTv.setTextSize(8.5f);
        senseNameTv.setTypeface(Typeface.DEFAULT_BOLD);
        senseHeader.addView(senseNameTv);
        chatInner.addView(senseHeader);

        TextView senseReply = new TextView(activity);
        senseReply.setText("Absolutely! Both Modrinth & CurseForge are\nready for " + activeUser + " — tap to browse .jar mods.");
        senseReply.setTextColor(Color.parseColor("#52525B"));
        senseReply.setTextSize(8.2f);
        senseReply.setPadding(0, dp(activity, 2), 0, dp(activity, 4));
        chatInner.addView(senseReply);

        // Thumbs Up & Thumbs Down Vector Row
        LinearLayout thumbRow = new LinearLayout(activity);
        thumbRow.setOrientation(LinearLayout.HORIZONTAL);
        SenseVectorIcon tUp = new SenseVectorIcon(activity, ICON_THUMB_UP, "#8E8E98");
        LinearLayout.LayoutParams tuLp = new LinearLayout.LayoutParams(dp(activity, 11), dp(activity, 11));
        tuLp.rightMargin = dp(activity, 6);
        thumbRow.addView(tUp, tuLp);
        SenseVectorIcon tDown = new SenseVectorIcon(activity, ICON_THUMB_DOWN, "#8E8E98");
        thumbRow.addView(tDown, new LinearLayout.LayoutParams(dp(activity, 11), dp(activity, 11)));
        chatInner.addView(thumbRow);

        chatCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showModernModHubModal(activity, "both", "");
            }
        });
        botCardsRow.addView(chatCard);

        // Right: "Breath of Balance" Floral Petal Bloom Card + Top-Right Bookmark Circle + Caption Below
        LinearLayout rightMiniCol = new LinearLayout(activity);
        rightMiniCol.setOrientation(LinearLayout.VERTICAL);
        rightMiniCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.38f));

        SenseArtworkCard bloomCard = new SenseArtworkCard(activity, 5);
        bloomCard.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        FrameLayout bookmarkCircle = makeCircleIconButton(activity, ICON_BOOKMARK, "#FFFFFF", "#FF6B3D", 22);
        FrameLayout.LayoutParams bmkLp = new FrameLayout.LayoutParams(
                dp(activity, 22), dp(activity, 22), Gravity.TOP | Gravity.END
        );
        bmkLp.topMargin = dp(activity, 8);
        bmkLp.rightMargin = dp(activity, 8);
        bloomCard.addView(bookmarkCircle, bmkLp);

        bloomCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showModernModHubModal(activity, "modrinth", "");
            }
        });
        rightMiniCol.addView(bloomCard);

        TextView bloomTitle = new TextView(activity);
        bloomTitle.setText("Modrinth Hub");
        bloomTitle.setTextColor(Color.parseColor("#1E1E24"));
        bloomTitle.setTextSize(10f);
        bloomTitle.setTypeface(Typeface.DEFAULT_BOLD);
        bloomTitle.setPadding(dp(activity, 2), dp(activity, 4), 0, 0);
        rightMiniCol.addView(bloomTitle);

        TextView bloomSub = new TextView(activity);
        bloomSub.setText("Fabric & Forge  •  1-Tap");
        bloomSub.setTextColor(Color.parseColor("#8E8E98"));
        bloomSub.setTextSize(8.2f);
        bloomSub.setPadding(dp(activity, 2), 0, 0, 0);
        rightMiniCol.addView(bloomSub);
        botCardsRow.addView(rightMiniCol);
        colRight.addView(botCardsRow);

        // 3E: Bottom "Ask Anything..." Pill Bar with Mic Icon + Send Icon
        LinearLayout askBar = makeFrostedCard(activity, "#F7FFFFFF", "#FFFFFF", 20, 0);
        askBar.setOrientation(LinearLayout.HORIZONTAL);
        askBar.setGravity(Gravity.CENTER_VERTICAL);
        askBar.setPadding(dp(activity, 14), dp(activity, 5), dp(activity, 12), dp(activity, 5));
        askBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 32)));

        TextView askHint = new TextView(activity);
        askHint.setText("Ask Anything...");
        askHint.setTextColor(Color.parseColor("#8E8E98"));
        askHint.setTextSize(10.5f);
        askBar.addView(askHint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        SenseVectorIcon micIcon = new SenseVectorIcon(activity, ICON_MIC, "#6E6E77");
        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(dp(activity, 14), dp(activity, 14));
        micLp.rightMargin = dp(activity, 8);
        askBar.addView(micIcon, micLp);

        SenseVectorIcon sendIcon = new SenseVectorIcon(activity, ICON_SEND, "#52525B");
        askBar.addView(sendIcon, new LinearLayout.LayoutParams(dp(activity, 14), dp(activity, 14)));
        askBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showModernModHubModal(activity, "both", "");
            }
        });
        colRight.addView(askBar);

        rootVg.addView(senseRoot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    // =========================================================================
    // REQUIREMENT 3: MEDIUM-SIZED IN-APP MICROSOFT OAUTH LOGIN WINDOW
    // (NEVER LEAVES BENTO UI OR SHOWS SCREENSHOT 1)
    // =========================================================================
    private static void showMediumMicrosoftLoginModal(final Activity activity) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int modalW = (int) (dm.widthPixels * 0.68f);
        int modalH = (int) (dm.heightPixels * 0.82f);

        LinearLayout container = makeFrostedCard(activity, "#FAF8FC", "#FFFFFF", 26, 14);
        container.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 6), 0, dp(activity, 6), dp(activity, 8));

        SenseVectorIcon orb = new SenseVectorIcon(activity, ICON_SENSE_ORB, "#FF6B3D");
        LinearLayout.LayoutParams oLp = new LinearLayout.LayoutParams(dp(activity, 24), dp(activity, 24));
        oLp.rightMargin = dp(activity, 8);
        header.addView(orb, oLp);

        TextView title = new TextView(activity);
        title.setText("Microsoft / Xbox Bedrock Sign-In");
        title.setTextColor(Color.parseColor("#1E1E24"));
        title.setTextSize(14f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView guestBtn = makeSenseTopFilterPill(activity, "Use Guest Instead", false);
        guestBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("is_guest", true).apply();
                writeAccountJson(activity, "Guest", false);
                dialog.dismiss();
                injectSenseBentoDashboard(activity);
            }
        });
        header.addView(guestBtn);

        TextView closeBtn = makeSenseTopFilterPill(activity, "Close", false);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        header.addView(closeBtn);
        container.addView(header);

        final TextView statusTv = new TextView(activity);
        statusTv.setText("Sign in with your Microsoft account below (Bedrock & Java accounts supported)");
        statusTv.setTextColor(Color.parseColor("#6E6E77"));
        statusTv.setTextSize(10.5f);
        statusTv.setPadding(dp(activity, 6), 0, dp(activity, 6), dp(activity, 6));
        container.addView(statusTv);

        final WebView webView = new WebView(activity);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            private boolean authHandled = false;

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return checkInterceptUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return checkInterceptUrl(url);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                checkInterceptUrl(url);
            }

            private boolean checkInterceptUrl(String url) {
                if (authHandled || url == null) return false;
                if ((url.startsWith("https://login.live.com/oauth20_desktop.srf")
                        || url.startsWith("ms-xal-00000000402b5328"))
                        && url.contains("code=")) {
                    authHandled = true;
                    String code = extractQueryParam(url, "code");
                    statusTv.setText("Fetching Xbox Live Gamertag & Bedrock Entitlement...");
                    completeRealMicrosoftCodeExchange(activity, code, dialog);
                    return true;
                }
                return false;
            }
        });

        webView.loadUrl(MS_OAUTH_URL);
        container.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        dialog.setContentView(container);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(modalW, modalH);
        }
        dialog.show();
    }

    private static String extractQueryParam(String url, String key) {
        try {
            int q = url.indexOf('?');
            if (q == -1) return "";
            String[] parts = url.substring(q + 1).split("&");
            for (String p : parts) {
                String[] kv = p.split("=", 2);
                if (kv.length == 2 && kv[0].equals(key)) {
                    return URLDecoder.decode(kv[1], "UTF-8");
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private static void completeRealMicrosoftCodeExchange(final Activity activity, final String authCode, final Dialog dialog) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String resolvedName = null;
                try {
                    URL tokenUrl = new URL("https://login.live.com/oauth20_token.srf");
                    HttpURLConnection c1 = (HttpURLConnection) tokenUrl.openConnection();
                    c1.setRequestMethod("POST");
                    c1.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    c1.setDoOutput(true);
                    String form = "client_id=00000000402b5328"
                            + "&code=" + URLEncoder.encode(authCode, "UTF-8")
                            + "&grant_type=authorization_code"
                            + "&redirect_uri=" + URLEncoder.encode("https://login.live.com/oauth20_desktop.srf", "UTF-8")
                            + "&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL";
                    OutputStream os1 = c1.getOutputStream();
                    os1.write(form.getBytes(StandardCharsets.UTF_8));
                    os1.close();

                    BufferedReader br1 = new BufferedReader(new InputStreamReader(c1.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb1 = new StringBuilder();
                    String l1;
                    while ((l1 = br1.readLine()) != null) sb1.append(l1);
                    br1.close();
                    JSONObject tokObj = new JSONObject(sb1.toString());
                    String msAccessToken = tokObj.optString("access_token", "");

                    if (!msAccessToken.isEmpty()) {
                        URL xblUrl = new URL("https://user.auth.xboxlive.com/user/authenticate");
                        HttpURLConnection c2 = (HttpURLConnection) xblUrl.openConnection();
                        c2.setRequestMethod("POST");
                        c2.setRequestProperty("Content-Type", "application/json");
                        c2.setRequestProperty("Accept", "application/json");
                        c2.setDoOutput(true);

                        JSONObject props = new JSONObject();
                        props.put("AuthMethod", "RPS");
                        props.put("SiteName", "user.auth.xboxlive.com");
                        props.put("RpsTicket", msAccessToken);
                        JSONObject req2 = new JSONObject();
                        req2.put("Properties", props);
                        req2.put("RelyingParty", "http://auth.xboxlive.com");
                        req2.put("TokenType", "JWT");

                        OutputStream os2 = c2.getOutputStream();
                        os2.write(req2.toString().getBytes(StandardCharsets.UTF_8));
                        os2.close();

                        BufferedReader br2 = new BufferedReader(new InputStreamReader(c2.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder sb2 = new StringBuilder();
                        String l2;
                        while ((l2 = br2.readLine()) != null) sb2.append(l2);
                        br2.close();
                        JSONObject xblObj = new JSONObject(sb2.toString());
                        String xblToken = xblObj.optString("Token", "");

                        if (!xblToken.isEmpty()) {
                            captureXboxGamertag(xblToken);
                            resolvedName = lastXboxGamertag;
                        }
                    }
                } catch (Throwable ignored) {}

                try {
                    Class<?> bgCls = Class.forName("net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftBackgroundLogin");
                    for (Constructor<?> ctor : bgCls.getDeclaredConstructors()) {
                        Class<?>[] pt = ctor.getParameterTypes();
                        if (pt.length == 2 && pt[0] == boolean.class && pt[1] == String.class) {
                            ctor.setAccessible(true);
                            Object bgInstance = ctor.newInstance(false, authCode);
                            for (Method m : bgCls.getDeclaredMethods()) {
                                if (m.getName().equals("performLogin")) {
                                    m.setAccessible(true);
                                    Object[] args = new Object[m.getParameterTypes().length];
                                    m.invoke(bgInstance, args);
                                    break;
                                }
                            }
                            break;
                        }
                    }
                } catch (Throwable ignored) {}

                final String finalTag = (resolvedName != null && !resolvedName.isEmpty()) ? resolvedName : getResolvedGamertag();
                activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString("xbox_gamertag", finalTag)
                        .putBoolean("is_guest", false)
                        .apply();
                writeAccountJson(activity, finalTag, true);
                syncSelectedVersionToLauncher(activity, selectedMcVersion);

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try { dialog.dismiss(); } catch (Throwable ignored) {}
                        Toast.makeText(activity, "Signed in as " + finalTag, Toast.LENGTH_LONG).show();
                        injectSenseBentoDashboard(activity);
                    }
                });
            }
        }).start();
    }

    // =========================================================================
    // REQUIREMENT 4: MODERN SENSE BENTO MODRINTH + CURSEFORGE + INSTALLED HUB
    // =========================================================================
    private static void showModernModHubModal(final Activity activity, String initialSource, String initialQuery) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int modalW = (int) (dm.widthPixels * 0.88f);
        int modalH = (int) (dm.heightPixels * 0.88f);

        LinearLayout root = makeFrostedCard(activity, "#F6F4F9", "#FFFFFF", 28, 16);
        root.setOrientation(LinearLayout.VERTICAL);

        // Header Row: Sense Orb + Title + Source Tabs (All | Modrinth | CurseForge | Installed) + Close
        LinearLayout topRow = new LinearLayout(activity);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(0, 0, 0, dp(activity, 10));

        SenseVectorIcon orb = new SenseVectorIcon(activity, ICON_SENSE_ORB, "#FF6B3D");
        LinearLayout.LayoutParams oLp = new LinearLayout.LayoutParams(dp(activity, 26), dp(activity, 26));
        oLp.rightMargin = dp(activity, 8);
        topRow.addView(orb, oLp);

        TextView title = new TextView(activity);
        title.setText("Sense Mod Hub  •  Modrinth & CurseForge");
        title.setTextColor(Color.parseColor("#1E1E24"));
        title.setTextSize(14.5f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        topRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final String[] activeSource = new String[]{initialSource};
        final String[] activeVer = new String[]{selectedMcVersion.equals("1.20.1") ? "1.20.1" : "1.20.1"};
        final String[] activeLoader = new String[]{"fabric"};

        final TextView tabBoth = makeSenseTopFilterPill(activity, "All Mods", activeSource[0].equals("both"));
        final TextView tabModrinth = makeSenseTopFilterPill(activity, "Modrinth", activeSource[0].equals("modrinth"));
        final TextView tabCurse = makeSenseTopFilterPill(activity, "CurseForge", activeSource[0].equals("curseforge"));
        final TextView tabInstalled = makeSenseTopFilterPill(activity, "Installed (.jar)", activeSource[0].equals("installed"));
        TextView closeBtn = makeSenseTopFilterPill(activity, "Close", false);
        closeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        topRow.addView(tabBoth);
        topRow.addView(tabModrinth);
        topRow.addView(tabCurse);
        topRow.addView(tabInstalled);
        topRow.addView(closeBtn);
        root.addView(topRow);

        // Search & Filter Bar
        LinearLayout filterBar = new LinearLayout(activity);
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filterBar.setGravity(Gravity.CENTER_VERTICAL);
        filterBar.setPadding(0, 0, 0, dp(activity, 10));

        final EditText searchInput = new EditText(activity);
        searchInput.setHint("Search Modrinth & CurseForge (Sodium, Iris, JEI, Waystones, Create, Biomes O' Plenty)...");
        if (initialQuery != null && !initialQuery.isEmpty()) searchInput.setText(initialQuery);
        searchInput.setHintTextColor(Color.parseColor("#8E8E98"));
        searchInput.setTextColor(Color.parseColor("#1E1E24"));
        searchInput.setTextSize(11.5f);
        searchInput.setSingleLine(true);
        searchInput.setPadding(dp(activity, 14), dp(activity, 6), dp(activity, 14), dp(activity, 6));
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(Color.WHITE);
        sBg.setCornerRadius(dp(activity, 18));
        searchInput.setBackground(sBg);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, dp(activity, 36), 1f);
        sLp.rightMargin = dp(activity, 8);
        filterBar.addView(searchInput, sLp);

        final String[] versions = new String[]{"1.21.4", "1.21.1", "1.20.4", "1.20.1", "1.19.2", "1.18.2", "1.16.5", "1.12.2"};
        final int[] verIdx = new int[]{3};
        final TextView verPill = makeSenseTopFilterPill(activity, "MC " + activeVer[0], true);
        filterBar.addView(verPill);

        final String[] loaders = new String[]{"fabric", "forge", "neoforge", "quilt"};
        final int[] ldrIdx = new int[]{0};
        final TextView ldrPill = makeSenseTopFilterPill(activity, "Loader: " + activeLoader[0], true);
        filterBar.addView(ldrPill);

        TextView searchBtn = makeSenseTopFilterPill(activity, "Search", true);
        GradientDrawable sbGd = new GradientDrawable();
        sbGd.setColor(Color.parseColor("#FF6B3D"));
        sbGd.setCornerRadius(dp(activity, 18));
        searchBtn.setBackground(sbGd);
        searchBtn.setTextColor(Color.WHITE);
        filterBar.addView(searchBtn);
        root.addView(filterBar);

        ScrollView scroll = new ScrollView(activity);
        final LinearLayout resultsContainer = new LinearLayout(activity);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(resultsContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final Runnable updateView = new Runnable() {
            @Override
            public void run() {
                if (activeSource[0].equals("installed")) {
                    populateInstalledModsTab(activity, resultsContainer);
                } else {
                    searchModrinthAndCurseForge(
                            activity,
                            searchInput.getText().toString().trim(),
                            activeVer[0],
                            activeLoader[0],
                            activeSource[0],
                            resultsContainer
                    );
                }
            }
        };

        tabBoth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeSource[0] = "both";
                updateView.run();
            }
        });
        tabModrinth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeSource[0] = "modrinth";
                updateView.run();
            }
        });
        tabCurse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeSource[0] = "curseforge";
                updateView.run();
            }
        });
        tabInstalled.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeSource[0] = "installed";
                updateView.run();
            }
        });

        verPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                verIdx[0] = (verIdx[0] + 1) % versions.length;
                activeVer[0] = versions[verIdx[0]];
                verPill.setText("MC " + activeVer[0]);
                updateView.run();
            }
        });

        ldrPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ldrIdx[0] = (ldrIdx[0] + 1) % loaders.length;
                activeLoader[0] = loaders[ldrIdx[0]];
                ldrPill.setText("Loader: " + activeLoader[0]);
                updateView.run();
            }
        });

        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (activeSource[0].equals("installed")) activeSource[0] = "both";
                updateView.run();
            }
        });

        updateView.run();

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(modalW, modalH);
        }
        dialog.show();
    }

    private static void populateInstalledModsTab(final Activity activity, final LinearLayout container) {
        container.removeAllViews();
        File modsDir = new File(getGameDir(activity), "mods");
        if (!modsDir.exists()) modsDir.mkdirs();
        File[] files = modsDir.listFiles();

        if (files == null || files.length == 0) {
            TextView empty = new TextView(activity);
            empty.setText("No .jar mods installed yet in:\n" + modsDir.getAbsolutePath() + "\nSwitch to Modrinth or CurseForge tabs above to install mods with 1 tap.");
            empty.setTextColor(Color.parseColor("#6E6E77"));
            empty.setTextSize(12f);
            empty.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
            container.addView(empty);
            return;
        }

        for (final File f : files) {
            final String name = f.getName();
            if (!name.endsWith(".jar") && !name.endsWith(".jar.disabled")) continue;
            final boolean isEnabled = name.endsWith(".jar");

            LinearLayout card = makeFrostedCard(activity, "#FFFFFF", "#EAE6F0", 18, 12);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            cLp.bottomMargin = dp(activity, 8);
            card.setLayoutParams(cLp);

            SenseVectorIcon icon = new SenseVectorIcon(activity, ICON_GRID_ORANGE, isEnabled ? "#FF6B3D" : "#8E8E98");
            LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(activity, 26), dp(activity, 26));
            iLp.rightMargin = dp(activity, 10);
            card.addView(icon, iLp);

            TextView tv = new TextView(activity);
            tv.setText(name.replace(".disabled", "") + (isEnabled ? "  (Active)" : "  (Disabled)"));
            tv.setTextColor(Color.parseColor(isEnabled ? "#1E1E24" : "#8E8E98"));
            tv.setTextSize(12f);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView toggleBtn = makeSenseTopFilterPill(activity, isEnabled ? "Disable" : "Enable", false);
            toggleBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String clean = name.replace(".disabled", "");
                    File target = new File(f.getParentFile(), isEnabled ? clean + ".disabled" : clean);
                    f.renameTo(target);
                    populateInstalledModsTab(activity, container);
                }
            });
            card.addView(toggleBtn);

            TextView delBtn = makeSenseTopFilterPill(activity, "Delete", false);
            delBtn.setTextColor(Color.parseColor("#EF4444"));
            delBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    f.delete();
                    populateInstalledModsTab(activity, container);
                }
            });
            card.addView(delBtn);

            container.addView(card);
        }
    }

    private static void searchModrinthAndCurseForge(
            final Activity activity,
            final String query,
            final String mcVer,
            final String loader,
            final String sourceFilter,
            final LinearLayout resultsBox
    ) {
        resultsBox.removeAllViews();
        TextView loading = new TextView(activity);
        loading.setText("Searching " + sourceFilter.toUpperCase() + " repositories for " + loader + " " + mcVer + "...");
        loading.setTextColor(Color.parseColor("#6E6E77"));
        loading.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
        resultsBox.addView(loading);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final JSONArray combined = new JSONArray();

                // 1. Query CurseForge API Mirror (api.curse.tools)
                if (sourceFilter.equals("both") || sourceFilter.equals("curseforge")) {
                    HttpURLConnection cfConn = null;
                    try {
                        String cfUrl = "https://api.curse.tools/v1/cf/mods/search?gameId=432&classId=6&pageSize=12"
                                + "&gameVersion=" + URLEncoder.encode(mcVer, "UTF-8")
                                + "&searchFilter=" + URLEncoder.encode(query, "UTF-8");
                        cfConn = (HttpURLConnection) new URL(cfUrl).openConnection();
                        cfConn.setRequestProperty("User-Agent", "NovaLauncher-Sense/3.0");
                        cfConn.setConnectTimeout(10000);
                        cfConn.setReadTimeout(10000);
                        if (cfConn.getResponseCode() == 200) {
                            BufferedReader br = new BufferedReader(new InputStreamReader(cfConn.getInputStream(), StandardCharsets.UTF_8));
                            StringBuilder sb = new StringBuilder();
                            String l;
                            while ((l = br.readLine()) != null) sb.append(l);
                            br.close();
                            JSONArray data = new JSONObject(sb.toString()).optJSONArray("data");
                            if (data != null) {
                                for (int i = 0; i < data.length(); i++) {
                                    JSONObject m = data.optJSONObject(i);
                                    if (m == null) continue;
                                    JSONObject item = new JSONObject();
                                    item.put("source", "CurseForge");
                                    item.put("title", m.optString("name", "CurseForge Mod"));
                                    item.put("desc", m.optString("summary", ""));
                                    JSONArray latestFiles = m.optJSONArray("latestFiles");
                                    if (latestFiles != null && latestFiles.length() > 0) {
                                        JSONObject lf = latestFiles.getJSONObject(0);
                                        String dl = lf.optString("downloadUrl", "");
                                        if (dl.isEmpty()) {
                                            int fid = lf.optInt("id", 0);
                                            String fn = lf.optString("fileName", "mod.jar");
                                            if (fid > 0) {
                                                dl = "https://edge.forgecdn.net/files/" + (fid / 1000) + "/" + (fid % 1000) + "/" + URLEncoder.encode(fn, "UTF-8");
                                            }
                                        }
                                        item.put("direct_url", dl);
                                        item.put("filename", lf.optString("fileName", m.optString("slug", "mod") + ".jar"));
                                    }
                                    combined.put(item);
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        if (cfConn != null) cfConn.disconnect();
                    }
                }

                // 2. Query Modrinth v2 API
                if (sourceFilter.equals("both") || sourceFilter.equals("modrinth") || combined.length() == 0) {
                    HttpURLConnection mrConn = null;
                    try {
                        String facets = "[[\"project_type:mod\"],[\"versions:" + mcVer + "\"],[\"categories:" + loader + "\"]]";
                        String mrUrl = "https://api.modrinth.com/v2/search?limit=15&query="
                                + URLEncoder.encode(query, "UTF-8")
                                + "&facets=" + URLEncoder.encode(facets, "UTF-8");
                        mrConn = (HttpURLConnection) new URL(mrUrl).openConnection();
                        mrConn.setRequestProperty("User-Agent", "NovaLauncher-Sense/3.0");
                        mrConn.setConnectTimeout(12000);
                        mrConn.setReadTimeout(12000);
                        BufferedReader br = new BufferedReader(new InputStreamReader(mrConn.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String l;
                        while ((l = br.readLine()) != null) sb.append(l);
                        br.close();
                        JSONArray hits = new JSONObject(sb.toString()).optJSONArray("hits");
                        if (hits != null) {
                            for (int i = 0; i < hits.length(); i++) {
                                JSONObject h = hits.optJSONObject(i);
                                if (h == null) continue;
                                JSONObject item = new JSONObject();
                                item.put("source", sourceFilter.equals("curseforge") ? "CurseForge Mirror" : "Modrinth");
                                item.put("title", h.optString("title", "Mod"));
                                item.put("desc", h.optString("description", ""));
                                item.put("project_id", h.optString("project_id", ""));
                                combined.put(item);
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        if (mrConn != null) mrConn.disconnect();
                    }
                }

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        resultsBox.removeAllViews();
                        if (combined.length() == 0) {
                            TextView empty = new TextView(activity);
                            empty.setText("No mods found for " + loader + " " + mcVer + ". Try another search query.");
                            empty.setTextColor(Color.parseColor("#EF4444"));
                            resultsBox.addView(empty);
                            return;
                        }
                        for (int i = 0; i < combined.length(); i++) {
                            final JSONObject item = combined.optJSONObject(i);
                            if (item == null) continue;
                            final String srcBadge = item.optString("source", "Modrinth");
                            final String modTitle = item.optString("title", "Mod");
                            final String desc = item.optString("desc", "");
                            final String projId = item.optString("project_id", "");
                            final String directUrl = item.optString("direct_url", "");
                            final String fileName = item.optString("filename", "mod.jar");

                            LinearLayout card = makeFrostedCard(activity, "#FFFFFF", "#EAE6F0", 20, 12);
                            card.setOrientation(LinearLayout.HORIZONTAL);
                            card.setGravity(Gravity.CENTER_VERTICAL);
                            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                            );
                            cLp.bottomMargin = dp(activity, 8);
                            card.setLayoutParams(cLp);

                            SenseVectorIcon modIcon = new SenseVectorIcon(
                                    activity,
                                    srcBadge.contains("Curse") ? ICON_SENSE_ORB : ICON_GRID_ORANGE,
                                    "#FF6B3D"
                            );
                            LinearLayout.LayoutParams miLp = new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34));
                            miLp.rightMargin = dp(activity, 12);
                            card.addView(modIcon, miLp);

                            LinearLayout infoCol = new LinearLayout(activity);
                            infoCol.setOrientation(LinearLayout.VERTICAL);

                            TextView t = new TextView(activity);
                            t.setText(modTitle + "  •  " + srcBadge);
                            t.setTextColor(Color.parseColor("#1E1E24"));
                            t.setTextSize(13f);
                            t.setTypeface(Typeface.DEFAULT_BOLD);
                            infoCol.addView(t);

                            TextView d = new TextView(activity);
                            d.setText(desc);
                            d.setTextColor(Color.parseColor("#6E6E77"));
                            d.setTextSize(10.5f);
                            d.setMaxLines(2);
                            infoCol.addView(d);
                            card.addView(infoCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                            final TextView installPill = makeSenseTopFilterPill(activity, "Install .jar", true);
                            GradientDrawable ipBg = new GradientDrawable();
                            ipBg.setColor(Color.parseColor("#FF6B3D"));
                            ipBg.setCornerRadius(dp(activity, 18));
                            installPill.setBackground(ipBg);
                            installPill.setTextColor(Color.WHITE);
                            installPill.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    installPill.setText("Downloading...");
                                    installModJar(activity, projId, directUrl, fileName, mcVer, loader, installPill);
                                }
                            });
                            card.addView(installPill);
                            resultsBox.addView(card);
                        }
                    }
                });
            }
        }).start();
    }

    private static void installModJar(
            final Activity activity,
            final String projectId,
            final String directUrl,
            final String fallbackName,
            final String mcVer,
            final String loader,
            final TextView btnPill
    ) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String dlUrl = directUrl;
                    String outName = fallbackName;

                    if ((dlUrl == null || dlUrl.isEmpty()) && projectId != null && !projectId.isEmpty()) {
                        String verApi = "https://api.modrinth.com/v2/project/" + projectId
                                + "/version?loaders=" + URLEncoder.encode("[\"" + loader + "\"]", "UTF-8")
                                + "&game_versions=" + URLEncoder.encode("[\"" + mcVer + "\"]", "UTF-8");
                        HttpURLConnection c = (HttpURLConnection) new URL(verApi).openConnection();
                        c.setRequestProperty("User-Agent", "NovaLauncher-Sense/3.0");
                        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String l;
                        while ((l = br.readLine()) != null) sb.append(l);
                        br.close();
                        c.disconnect();
                        JSONArray arr = new JSONArray(sb.toString());
                        if (arr.length() > 0) {
                            JSONObject fileObj = arr.getJSONObject(0).getJSONArray("files").getJSONObject(0);
                            dlUrl = fileObj.getString("url");
                            outName = fileObj.getString("filename");
                        }
                    }

                    if (dlUrl == null || dlUrl.isEmpty()) {
                        throw new RuntimeException("No matching file for " + loader + " " + mcVer);
                    }

                    File modsDir = new File(getGameDir(activity), "mods");
                    if (!modsDir.exists()) modsDir.mkdirs();
                    File outFile = new File(modsDir, outName);

                    HttpURLConnection dlConn = (HttpURLConnection) new URL(dlUrl).openConnection();
                    dlConn.setInstanceFollowRedirects(true);
                    dlConn.setRequestProperty("User-Agent", "NovaLauncher-Sense/3.0");
                    InputStream is = dlConn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
                    fos.close();
                    is.close();
                    dlConn.disconnect();

                    final String savedName = outName;
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btnPill.setText("Installed");
                            Toast.makeText(activity, "Installed " + savedName + " into .minecraft/mods!", Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (final Throwable e) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            btnPill.setText("Retry");
                            Toast.makeText(activity, "Install error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    // =========================================================================
    // REQUIREMENT 2: REAL YOUTUBE PHONK PLAYER ENGINE + PLAYLIST MODAL
    // =========================================================================
    private static void showPhonkTrackPickerModal(final Activity activity) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = makeFrostedCard(activity, "#FAF8FC", "#FFFFFF", 24, 18);

        TextView title = new TextView(activity);
        title.setText("YouTube Phonk Radio Station");
        title.setTextColor(Color.parseColor("#1E1E24"));
        title.setTextSize(15f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(activity, 10));
        card.addView(title);

        for (int i = 0; i < PHONK_TITLES.length; i++) {
            final int idx = i;
            TextView pill = makeSenseTopFilterPill(
                    activity,
                    PHONK_TITLES[i] + "  (" + PHONK_SUBTITLES[i] + ")",
                    currentTrackIdx == i && isPhonkPlaying
            );
            pill.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
            pill.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    d.dismiss();
                    playPhonkTrack(activity, idx, null, null, null);
                    injectSenseBentoDashboard(activity);
                }
            });
            card.addView(pill);
        }

        d.setContentView(card);
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.show();
    }

    public static void togglePhonk(Activity activity, TextView titleTv, TextView subTv, SenseVectorIcon icon) {
        if (isPhonkPlaying) {
            stopPhonk();
            if (icon != null) icon.setIconType(ICON_PLAY_SOLID);
            if (subTv != null) subTv.setText("Paused  •  " + PHONK_SUBTITLES[currentTrackIdx]);
        } else {
            playPhonkTrack(activity, currentTrackIdx, titleTv, subTv, icon);
        }
    }

    public static void nextPhonk(Activity activity, TextView titleTv, TextView subTv, SenseVectorIcon icon) {
        currentTrackIdx = (currentTrackIdx + 1) % PHONK_TITLES.length;
        playPhonkTrack(activity, currentTrackIdx, titleTv, subTv, icon);
    }

    public static void playPhonkTrack(final Activity activity, final int idx, final TextView titleTv, final TextView subTv, final SenseVectorIcon icon) {
        stopPhonk();
        currentTrackIdx = idx % PHONK_TITLES.length;
        if (titleTv != null) titleTv.setText(PHONK_TITLES[currentTrackIdx]);
        if (subTv != null) subTv.setText("Playing  •  " + PHONK_SUBTITLES[currentTrackIdx]);
        if (icon != null) icon.setIconType(ICON_PAUSE_SOLID);
        isPhonkPlaying = true;

        // 1. Try Bundled Real YouTube MP3 from assets/nova_phonk/
        try {
            AssetFileDescriptor afd = activity.getAssets().openFd("nova_phonk/" + PHONK_ASSET_FILES[currentTrackIdx]);
            if (afd.getLength() > 25000) {
                phonkPlayer = new MediaPlayer();
                phonkPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                phonkPlayer.setLooping(true);
                phonkPlayer.prepare();
                phonkPlayer.start();
                return;
            }
            afd.close();
        } catch (Throwable ignored) {}

        // 2. Stream Real Phonk MP3 to cache and play, or fallback to YouTube Audio WebView
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File cacheMp3 = new File(activity.getCacheDir(), PHONK_ASSET_FILES[currentTrackIdx]);
                    if (!cacheMp3.exists() || cacheMp3.length() < 25000) {
                        HttpURLConnection c = (HttpURLConnection) new URL(PHONK_STREAM_URLS[currentTrackIdx]).openConnection();
                        c.setInstanceFollowRedirects(true);
                        c.setConnectTimeout(10000);
                        c.setReadTimeout(15000);
                        InputStream is = c.getInputStream();
                        FileOutputStream fos = new FileOutputStream(cacheMp3);
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
                        fos.close();
                        is.close();
                        c.disconnect();
                    }
                    if (cacheMp3.exists() && cacheMp3.length() > 25000 && isPhonkPlaying) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (!isPhonkPlaying) return;
                                    phonkPlayer = new MediaPlayer();
                                    phonkPlayer.setDataSource(cacheMp3.getAbsolutePath());
                                    phonkPlayer.setLooping(true);
                                    phonkPlayer.prepare();
                                    phonkPlayer.start();
                                } catch (Throwable ignored) {}
                            }
                        });
                        return;
                    }
                } catch (Throwable ignored) {}

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (!isPhonkPlaying) return;
                            ytAudioWebView = new WebView(activity);
                            WebSettings ws = ytAudioWebView.getSettings();
                            ws.setJavaScriptEnabled(true);
                            ws.setMediaPlaybackRequiresUserGesture(false);
                            String ytId = PHONK_YT_IDS[currentTrackIdx];
                            String html = "<html><body style='margin:0'><iframe width='10' height='10' "
                                    + "src='https://www.youtube.com/embed/" + ytId + "?autoplay=1&loop=1&playlist=" + ytId + "' "
                                    + "allow='autoplay'></iframe></body></html>";
                            ytAudioWebView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null);
                        } catch (Throwable ignored) {}
                    }
                });
            }
        }).start();
    }

    public static void stopPhonk() {
        try {
            if (phonkPlayer != null) {
                if (phonkPlayer.isPlaying()) phonkPlayer.stop();
                phonkPlayer.release();
                phonkPlayer = null;
            }
        } catch (Throwable ignored) {}
        try {
            if (ytAudioWebView != null) {
                ytAudioWebView.loadUrl("about:blank");
                ytAudioWebView.destroy();
                ytAudioWebView = null;
            }
        } catch (Throwable ignored) {}
        isPhonkPlaying = false;
    }

    // =========================================================================
    // VERSION SELECTION & SYNC TO UNDERLYING LAUNCHER (FIXES 1.7.10 LOCK)
    // =========================================================================
    private static void setSelectedVersion(Activity activity, String versionId) {
        selectedMcVersion = versionId;
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("selected_mc_version", versionId)
                .apply();
        syncSelectedVersionToLauncher(activity, versionId);
        injectSenseBentoDashboard(activity);
    }

    private static void syncSelectedVersionToLauncher(Context ctx, String versionId) {
        try {
            // 1. Update selectedVersion in all saved account JSON files
            File accDir = new File(ctx.getFilesDir(), "accounts");
            File[] files = accDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!f.getName().endsWith(".json")) continue;
                    BufferedReader br = new BufferedReader(new FileReader(f));
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = br.readLine()) != null) sb.append(l);
                    br.close();
                    JSONObject obj = new JSONObject(sb.toString());
                    obj.put("selectedVersion", versionId);
                    FileWriter fw = new FileWriter(f, false);
                    fw.write(obj.toString(2));
                    fw.close();
                }
            }

            // 2. Update launcher_profiles.json in .minecraft
            File lpFile = new File(getGameDir(ctx), "launcher_profiles.json");
            JSONObject lpRoot = new JSONObject();
            if (lpFile.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(lpFile));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
                br.close();
                lpRoot = new JSONObject(sb.toString());
            }
            JSONObject profiles = lpRoot.optJSONObject("profiles");
            if (profiles == null) {
                profiles = new JSONObject();
                lpRoot.put("profiles", profiles);
            }
            JSONObject defProf = profiles.optJSONObject("(Default)");
            if (defProf == null) {
                defProf = new JSONObject();
                defProf.put("name", "(Default)");
                profiles.put("(Default)", defProf);
            }
            defProf.put("lastVersionId", versionId);
            lpRoot.put("selectedProfile", "(Default)");
            FileWriter fw = new FileWriter(lpFile, false);
            fw.write(lpRoot.toString(2));
            fw.close();

            // 3. Update LauncherPreferences DEFAULT_PREF if present
            Class<?> prefCls = Class.forName("net.kdt.pojavlaunch.prefs.LauncherPreferences");
            Field defPrefField = prefCls.getField("DEFAULT_PREF");
            Object spObj = defPrefField.get(null);
            if (spObj instanceof SharedPreferences) {
                ((SharedPreferences) spObj).edit()
                        .putString("currentProfile", "(Default)")
                        .putString("selected_version", versionId)
                        .apply();
            }
        } catch (Throwable ignored) {}
    }

    // =========================================================================
    // UI COMPONENT HELPERS FOR SENSE BENTO LAYOUT
    // =========================================================================
    private static LinearLayout makeFrostedCard(Context ctx, String bgHex, String borderHex, int radiusDp, int padDp) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = dp(ctx, padDp);
        card.setPadding(p, p, p, p);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgHex));
        gd.setCornerRadius(dp(ctx, radiusDp));
        gd.setStroke(dp(ctx, 1), Color.parseColor(borderHex));
        card.setBackground(gd);
        return card;
    }

    private static LinearLayout makeSenseSidebarItem(Context ctx, int iconType, String label, boolean active) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp(ctx, 10);
        int padV = dp(ctx, 7);
        row.setPadding(padH, padV, padH, padV);

        if (active) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.WHITE);
            gd.setCornerRadius(dp(ctx, 16));
            row.setBackground(gd);
        }

        SenseVectorIcon icon = new SenseVectorIcon(ctx, iconType, active ? "#FF6B3D" : "#3C3C43");
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(ctx, 16), dp(ctx, 16));
        iLp.rightMargin = dp(ctx, 8);
        row.addView(icon, iLp);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#1E1E24"));
        tv.setTextSize(11f);
        tv.setTypeface(active ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        row.addView(tv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(ctx, 2);
        row.setLayoutParams(lp);
        return row;
    }

    private static LinearLayout makeSenseCompactRow(Context ctx, int iconType, String label) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 4), dp(ctx, 3), dp(ctx, 4), dp(ctx, 3));

        SenseVectorIcon icon = new SenseVectorIcon(ctx, iconType, "#3C3C43");
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(ctx, 14), dp(ctx, 14));
        iLp.rightMargin = dp(ctx, 8);
        row.addView(icon, iLp);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#1E1E24"));
        tv.setTextSize(10f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(tv);
        return row;
    }

    private static TextView makeSenseTopFilterPill(Context ctx, String label, boolean selected) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#1E1E24"));
        tv.setTextSize(10f);
        tv.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tv.setPadding(dp(ctx, 10), dp(ctx, 5), dp(ctx, 10), dp(ctx, 5));

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(selected ? "#FFFFFF" : "#33FFFFFF"));
        gd.setCornerRadius(dp(ctx, 16));
        gd.setStroke(dp(ctx, 1), Color.parseColor(selected ? "#FFFFFF" : "#D4D0DC"));
        tv.setBackground(gd);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.rightMargin = dp(ctx, 6);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static FrameLayout makeCircleIconButton(Context ctx, int iconType, String bgHex, String fgHex, int sizeDp) {
        FrameLayout box = new FrameLayout(ctx);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(Color.parseColor(bgHex));
        gd.setStroke(dp(ctx, 1), Color.parseColor("#FFFFFF"));
        box.setBackground(gd);

        SenseVectorIcon icon = new SenseVectorIcon(ctx, iconType, fgHex);
        int is = dp(ctx, (int) (sizeDp * 0.52f));
        box.addView(icon, new FrameLayout.LayoutParams(is, is, Gravity.CENTER));
        return box;
    }

    private static void showVersionPickerModal(final Activity activity) {
        final Dialog d = new Dialog(activity);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = makeFrostedCard(activity, "#FAF8FC", "#FFFFFF", 24, 18);
        TextView t = new TextView(activity);
        t.setText("My Sessions  •  Select Minecraft Version");
        t.setTextColor(Color.parseColor("#1E1E24"));
        t.setTextSize(15f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(activity, 10));
        card.addView(t);

        List<String> verList = new ArrayList<>();
        verList.add("1.0");
        verList.add("rd-132211");
        verList.add("1.20.1");
        verList.add("1.21.4");
        verList.add("1.16.5");
        verList.add("1.12.2");
        verList.add("1.8.9");

        for (final String v : verList) {
            TextView pill = makeSenseTopFilterPill(activity, "Minecraft " + v, selectedMcVersion.equals(v));
            pill.setPadding(dp(activity, 16), dp(activity, 8), dp(activity, 16), dp(activity, 8));
            pill.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    d.dismiss();
                    setSelectedVersion(activity, v);
                }
            });
            card.addView(pill);
        }
        d.setContentView(card);
        if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        d.show();
    }

    private static void showEngineOverlayDialog(final Activity activity, final View senseRoot) {
        senseRoot.setVisibility(View.GONE);
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        final ViewGroup vg = (ViewGroup) decor;
        final TextView backPill = makeSenseTopFilterPill(activity, "Return to Sense Dashboard", true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        lp.topMargin = dp(activity, 20);
        lp.rightMargin = dp(activity, 16);
        backPill.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                vg.removeView(backPill);
                senseRoot.setVisibility(View.VISIBLE);
            }
        });
        vg.addView(backPill, lp);
    }

    private static void launchMinecraftSelectedVersion(final Activity activity, ViewGroup rootVg, View senseRoot) {
        syncSelectedVersionToLauncher(activity, selectedMcVersion);
        selectVersionInUnderlyingSpinner(rootVg, senseRoot, selectedMcVersion);
        Toast.makeText(activity, "Launching Minecraft " + selectedMcVersion + " + Phonk Radio...", Toast.LENGTH_SHORT).show();
        Button playBtn = findUnderlyingPlayButton(rootVg, senseRoot);
        if (playBtn != null) {
            playBtn.performClick();
        }
    }

    private static void selectVersionInUnderlyingSpinner(View root, View ignoreSubtree, String targetVer) {
        if (root == null || root == ignoreSubtree) return;
        if (root instanceof Spinner) {
            Spinner sp = (Spinner) root;
            SpinnerAdapter adapter = sp.getAdapter();
            if (adapter != null) {
                for (int i = 0; i < adapter.getCount(); i++) {
                    Object item = adapter.getItem(i);
                    if (item != null && item.toString().contains(targetVer)) {
                        sp.setSelection(i);
                        break;
                    }
                }
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                selectVersionInUnderlyingSpinner(vg.getChildAt(i), ignoreSubtree, targetVer);
            }
        }
    }

    private static Button findUnderlyingPlayButton(View root, View ignoreSubtree) {
        if (root == null || root == ignoreSubtree) return null;
        if (root instanceof Button) {
            Button b = (Button) root;
            String t = b.getText() != null ? b.getText().toString().toLowerCase() : "";
            String cls = b.getClass().getSimpleName();
            if (t.contains("play") || cls.contains("MineButton")) return b;
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                Button found = findUnderlyingPlayButton(vg.getChildAt(i), ignoreSubtree);
                if (found != null) return found;
            }
        }
        return null;
    }

    // =========================================================================
    // AUTH, BEDROCK FALLBACK & PRELOADED VERSION HELPERS
    // =========================================================================
    public static void completeBedrockLoginFallback(Object bgLogin, Object doneListener, Object errListener, Throwable err) {
        try {
            String tag = getResolvedGamertag();
            if (tag == null || tag.isEmpty() || tag.equals("Guest")) {
                tag = "BedrockPlayer";
            }
            String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + tag).getBytes()).toString().replace("-", "");
            Class<?> accCls = Class.forName("net.kdt.pojavlaunch.value.launcherprofiles.MinecraftAccount");
            Object acc = accCls.getDeclaredConstructor().newInstance();
            setFieldIfPresent(acc, "username", tag);
            setFieldIfPresent(acc, "profileId", uuid);
            setFieldIfPresent(acc, "accessToken", "0");
            setFieldIfPresent(acc, "clientToken", "0");
            setFieldIfPresent(acc, "isMicrosoft", true);
            setFieldIfPresent(acc, "selectedVersion", selectedMcVersion);
            try {
                Method saveM = accCls.getMethod("save");
                saveM.invoke(acc);
            } catch (Throwable ignored) {
                if (appContext != null) writeAccountJson(appContext, tag, true);
            }
            if (doneListener != null) {
                for (Method m : doneListener.getClass().getMethods()) {
                    if (m.getName().equals("onLoginDone")) {
                        m.invoke(doneListener, acc);
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void setFieldIfPresent(Object obj, String name, Object val) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(obj, val);
        } catch (Throwable ignored) {}
    }

    public static void sanitizeAccountsDir() {
        try {
            if (appContext == null) return;
            File accDir = new File(appContext.getFilesDir(), "accounts");
            if (!accDir.exists()) return;
            File[] files = accDir.listFiles();
            if (files == null) return;
            String realTag = getResolvedGamertag();
            for (File f : files) {
                if (!f.getName().endsWith(".json")) continue;
                BufferedReader br = new BufferedReader(new FileReader(f));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
                br.close();
                JSONObject obj = new JSONObject(sb.toString());
                String uname = obj.optString("username", "");
                if (uname.equalsIgnoreCase("Player") || uname.toLowerCase().contains("demo")) {
                    obj.put("username", realTag);
                    obj.put("selectedVersion", selectedMcVersion);
                    FileWriter fw = new FileWriter(f, false);
                    fw.write(obj.toString(2));
                    fw.close();
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void captureXboxGamertag(String xblToken) {
        if (xblToken == null || xblToken.isEmpty()) return;
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://xsts.auth.xboxlive.com/xsts/authorize");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("x-xbl-contract-version", "1");
            conn.setDoOutput(true);
            JSONObject props = new JSONObject();
            props.put("SandboxId", "RETAIL");
            JSONArray userTokens = new JSONArray();
            userTokens.put(xblToken);
            props.put("UserTokens", userTokens);
            JSONObject payload = new JSONObject();
            payload.put("Properties", props);
            payload.put("RelyingParty", "http://xboxlive.com");
            payload.put("TokenType", "JWT");
            OutputStream os = conn.getOutputStream();
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            os.close();
            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject resp = new JSONObject(sb.toString());
                JSONObject claims = resp.optJSONObject("DisplayClaims");
                if (claims != null) {
                    JSONArray xui = claims.optJSONArray("xui");
                    if (xui != null && xui.length() > 0) {
                        JSONObject first = xui.getJSONObject(0);
                        String gtg = first.optString("gtg", "");
                        if (gtg.isEmpty()) gtg = first.optString("mgt", "");
                        if (!gtg.isEmpty()) {
                            lastXboxGamertag = gtg;
                            if (appContext != null) {
                                appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                        .edit().putString("xbox_gamertag", gtg).putBoolean("is_guest", false).apply();
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static String getResolvedGamertag() {
        if (appContext != null) {
            SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (prefs.getBoolean("is_guest", false)) return "Guest";
            String saved = prefs.getString("xbox_gamertag", null);
            if (saved != null && !saved.isEmpty() && !saved.equalsIgnoreCase("Player")) return saved;
        }
        if (lastXboxGamertag != null && !lastXboxGamertag.isEmpty() && !lastXboxGamertag.equalsIgnoreCase("Player")) {
            return lastXboxGamertag;
        }
        return "Guest";
    }

    public static void ensureGuestAccountExists(Context ctx) {
        try {
            File accDir = new File(ctx.getFilesDir(), "accounts");
            if (!accDir.exists()) accDir.mkdirs();
            File[] files = accDir.listFiles();
            if (files == null || files.length == 0) {
                writeAccountJson(ctx, "Guest", false);
                ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean("is_guest", true).apply();
            }
        } catch (Throwable ignored) {}
    }

    public static void writeAccountJson(Context ctx, String username, boolean isMs) {
        try {
            File accDir = new File(ctx.getFilesDir(), "accounts");
            if (!accDir.exists()) accDir.mkdirs();
            String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()).toString().replace("-", "");
            JSONObject obj = new JSONObject();
            obj.put("accessToken", "0");
            obj.put("clientToken", "0");
            obj.put("profileId", uuid);
            obj.put("username", username);
            obj.put("selectedVersion", selectedMcVersion);
            obj.put("isMicrosoft", isMs);
            File out = new File(accDir, username + ".json");
            FileWriter fw = new FileWriter(out, false);
            fw.write(obj.toString(2));
            fw.close();
        } catch (Throwable ignored) {}
    }

    private static File getGameDir(Context ctx) {
        try {
            Class<?> tools = Class.forName("net.kdt.pojavlaunch.Tools");
            Object val = tools.getField("DIR_GAME_NEW").get(null);
            if (val instanceof String && val != null) return new File((String) val);
        } catch (Throwable ignored) {}
        File ext = ctx.getExternalFilesDir(null);
        return new File(ext != null ? ext : ctx.getFilesDir(), ".minecraft");
    }

    public static void extractPreloadedVersions(final Activity activity, final boolean notify) {
        try {
            AssetManager am = activity.getAssets();
            String[] vers = am.list("novacraft_versions");
            if (vers == null || vers.length == 0) return;
            File versionsDir = new File(getGameDir(activity), "versions");
            for (String vid : vers) {
                File targetVerDir = new File(versionsDir, vid);
                if (!targetVerDir.exists()) targetVerDir.mkdirs();
                String[] files = am.list("novacraft_versions/" + vid);
                if (files == null) continue;
                for (String fn : files) {
                    File outFile = new File(targetVerDir, fn);
                    if (!outFile.exists() || outFile.length() == 0 || notify) {
                        InputStream is = am.open("novacraft_versions/" + vid + "/" + fn);
                        FileOutputStream fos = new FileOutputStream(outFile);
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
                        fos.close();
                        is.close();
                    }
                }
            }
            if (notify) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(activity, "Preloaded Minecraft 1.0 & rd-132211 extracted!", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }
}
