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
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
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

    private static final String[] PHONK_ASSET_FILES = new String[]{
        "phonk_01.mp3",
        "phonk_02.mp3",
        "phonk_03.mp3"
    };

    private static final String[] PHONK_TITLES = new String[]{
        "KORDHELL - MURDER IN MY MIND",
        "DVRST - CLOSE EYES",
        "HENSONN - SAHARA"
    };

    private static final String[] PHONK_SUBTITLES = new String[]{
        "YouTube Phonk  •  2:25",
        "YouTube Phonk  •  2:12",
        "YouTube Phonk  •  2:51"
    };

    private static final String[] PHONK_YT_IDS = new String[]{
        "w-sQRS-Lc9k",
        "ao4RCon11eY",
        "pIZ0QRWK0zg"
    };

    private static final String[] PHONK_STREAM_URLS = new String[]{
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

        if (activity.getClass().getSimpleName().contains("MainActivity")) {
            stopPhonk();
            return;
        }

        new Thread(() -> {
            ensureGuestAccountExists(activity);
            extractPreloadedVersions(activity, false);
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
        decor.post(() -> injectSenseBentoDashboard(activity));
    }

    // =========================================================================
    // CUSTOM VECTOR ICONS & ARTWORK SHADERS (MATCHING SCREENSHOT 2)
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

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w / 2f, cy = h / 2f;
            float s = Math.min(w, h);
            float st = Math.max(1.8f, s * 0.095f);

            p.reset();
            p.setAntiAlias(true);
            p.setColor(color);
            p.setStrokeWidth(st);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);

            switch (type) {
                case ICON_SENSE_ORB: {
                    float r = s * 0.44f;
                    // Soft coral drop glow underneath
                    p.setStyle(Paint.Style.FILL);
                    p.setShader(new RadialGradient(cx, cy + r * 0.35f, r * 1.15f,
                            new int[]{Color.parseColor("#66FF7A59"), Color.TRANSPARENT},
                            new float[]{0f, 1f}, Shader.TileMode.CLAMP));
                    c.drawCircle(cx, cy + r * 0.25f, r * 1.05f, p);

                    // 3D Peach/Coral/Rose Sphere matching Screenshot 2
                    p.setShader(new RadialGradient(cx - r * 0.28f, cy - r * 0.28f, r * 1.3f,
                            new int[]{
                                Color.parseColor("#FFF0E5"),
                                Color.parseColor("#FF9E7D"),
                                Color.parseColor("#F25C54"),
                                Color.parseColor("#D94E73")
                            },
                            new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP));
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
                    path.moveTo(cx - s * 0.10f, cy - s * 0.14f);
                    path.lineTo(cx + s * 0.16f, cy);
                    path.lineTo(cx - s * 0.10f, cy + s * 0.14f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
                case ICON_EASEL_MODS: {
                    p.setStyle(Paint.Style.STROKE);
                    rect.set(s * 0.20f, s * 0.20f, s * 0.80f, s * 0.62f);
                    c.drawRoundRect(rect, s * 0.08f, s * 0.08f, p);
                    c.drawLine(cx, s * 0.62f, cx, s * 0.84f, p);
                    c.drawLine(s * 0.32f, s * 0.84f, s * 0.68f, s * 0.84f, p);
                    break;
                }
                case ICON_COMMUNITY: {
                    p.setStyle(Paint.Style.STROKE);
                    c.drawCircle(s * 0.38f, s * 0.36f, s * 0.13f, p);
                    c.drawCircle(s * 0.66f, s * 0.38f, s * 0.11f, p);
                    rect.set(s * 0.18f, s * 0.54f, s * 0.58f, s * 0.88f);
                    c.drawArc(rect, 190f, 160f, false, p);
                    rect.set(s * 0.48f, s * 0.56f, s * 0.84f, s * 0.88f);
                    c.drawArc(rect, 190f, 160f, false, p);
                    break;
                }
                case ICON_DOWNLOAD: {
                    p.setStyle(Paint.Style.STROKE);
                    c.drawLine(cx, s * 0.20f, cx, s * 0.64f, p);
                    c.drawLine(cx - s * 0.16f, s * 0.48f, cx, s * 0.64f, p);
                    c.drawLine(cx + s * 0.16f, s * 0.48f, cx, s * 0.64f, p);
                    c.drawLine(s * 0.22f, s * 0.78f, s * 0.78f, s * 0.78f, p);
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
                    rect.set(s * 0.28f, s * 0.22f, s * 0.72f, s * 0.70f);
                    c.drawArc(rect, 180f, 180f, false, p);
                    c.drawLine(s * 0.28f, s * 0.46f, s * 0.24f, s * 0.68f, p);
                    c.drawLine(s * 0.72f, s * 0.46f, s * 0.76f, s * 0.68f, p);
                    c.drawLine(s * 0.22f, s * 0.68f, s * 0.78f, s * 0.68f, p);
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
                    path.moveTo(s * 0.34f, s * 0.26f);
                    path.lineTo(s * 0.74f, cy);
                    path.lineTo(s * 0.34f, s * 0.74f);
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
                    rect.set(s * 0.22f, s * 0.22f, s * 0.78f, s * 0.78f);
                    c.drawRoundRect(rect, s * 0.12f, s * 0.12f, p);
                    c.drawCircle(cx, cy - s * 0.04f, s * 0.11f, p);
                    break;
                }
                case ICON_SHARE: {
                    p.setStyle(Paint.Style.STROKE);
                    c.drawLine(s * 0.26f, s * 0.68f, s * 0.68f, s * 0.32f, p);
                    c.drawLine(s * 0.44f, s * 0.32f, s * 0.68f, s * 0.32f, p);
                    c.drawLine(s * 0.68f, s * 0.32f, s * 0.68f, s * 0.56f, p);
                    break;
                }
                case ICON_STAR: {
                    p.setStyle(Paint.Style.STROKE);
                    c.drawCircle(cx, cy, s * 0.30f, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, cy, s * 0.10f, p);
                    break;
                }
                case ICON_LOGOUT: {
                    p.setStyle(Paint.Style.STROKE);
                    rect.set(s * 0.22f, s * 0.22f, s * 0.56f, s * 0.78f);
                    c.drawArc(rect, 90f, 180f, false, p);
                    c.drawLine(s * 0.42f, cy, s * 0.78f, cy, p);
                    c.drawLine(s * 0.64f, cy - s * 0.14f, s * 0.78f, cy, p);
                    c.drawLine(s * 0.64f, cy + s * 0.14f, s * 0.78f, cy, p);
                    break;
                }
                case ICON_SEND: {
                    p.setStyle(Paint.Style.STROKE);
                    path.reset();
                    path.moveTo(s * 0.20f, s * 0.46f);
                    path.lineTo(s * 0.78f, s * 0.22f);
                    path.lineTo(s * 0.56f, s * 0.80f);
                    path.lineTo(s * 0.44f, s * 0.54f);
                    path.close();
                    c.drawPath(path, p);
                    break;
                }
            }
        }
    }

    // =========================================================================
    // PASTEL ARTWORK CARD CONTAINER (FOR "MEDITATE", "SLEEP", "MUSIC", "MOVE")
    // =========================================================================
    private static class SenseArtworkCard extends FrameLayout {
        private final int styleType;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Path clipPath = new Path();

        // styleType: 0 = Meditate Pink/Violet Wave, 1 = Sleep Misty Hills,
        // 2 = Music Green/Peach Blur, 3 = Move Blue/Pink Aurora, 4 = Dotted Greeting Card,
        // 5 = Breath of Balance Orange/Lavender Bloom
        public SenseArtworkCard(Context ctx, int styleType) {
            super(ctx);
            this.styleType = styleType;
            setWillNotDraw(false);
            dotPaint.setColor(Color.parseColor("#18D9778A"));
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
                // Meditate Card: Pink/Violet/Peach abstract wave fading to soft white at bottom
                paint.setShader(new LinearGradient(0, 0, w, h,
                        new int[]{
                            Color.parseColor("#B7A6D6"),
                            Color.parseColor("#F59BB8"),
                            Color.parseColor("#FCD3DE"),
                            Color.parseColor("#FDF8FA")
                        },
                        new float[]{0f, 0.38f, 0.72f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(new RadialGradient(w * 0.75f, h * 0.25f, w * 0.55f,
                        new int[]{Color.parseColor("#88E879F9"), Color.TRANSPARENT},
                        new float[]{0f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
            } else if (styleType == 1) {
                // Sleep Card: Soft Misty Sage/Peach Hills fading to warm cream
                paint.setShader(new LinearGradient(0, 0, w * 0.4f, h,
                        new int[]{
                            Color.parseColor("#D4E7E1"),
                            Color.parseColor("#E3DFD7"),
                            Color.parseColor("#F7EFE8"),
                            Color.parseColor("#FFFFFF")
                        },
                        new float[]{0f, 0.45f, 0.78f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
            } else if (styleType == 2) {
                // Music Card: Vibrant Pastel Green & Peach diagonal motion blur
                paint.setShader(new LinearGradient(0, 0, w, h,
                        new int[]{
                            Color.parseColor("#F9B49B"),
                            Color.parseColor("#8CD790"),
                            Color.parseColor("#D7F2DC"),
                            Color.parseColor("#FAFDFB")
                        },
                        new float[]{0f, 0.42f, 0.76f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
            } else if (styleType == 3) {
                // Move Card: Soft Periwinkle Blue & Coral Pink vertical aurora
                paint.setShader(new LinearGradient(0, 0, w, h,
                        new int[]{
                            Color.parseColor("#9DA9D8"),
                            Color.parseColor("#F4A4A8"),
                            Color.parseColor("#D6E4F0"),
                            Color.parseColor("#F8FAFC")
                        },
                        new float[]{0f, 0.40f, 0.78f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
            } else if (styleType == 4) {
                // Dotted Greeting / Chat Card: Frosted #F5F3F7 with subtle dot grid
                canvas.drawColor(Color.parseColor("#ECF6F4F8"));
                int step = dp(getContext(), 10);
                float dr = dp(getContext(), 1);
                for (int y = step; y < h; y += step) {
                    for (int x = step; x < w; x += step) {
                        canvas.drawCircle(x, y, dr, dotPaint);
                    }
                }
            } else if (styleType == 5) {
                // Breath of Balance Card: Lavender & Vibrant Orange Petal Bloom
                paint.setShader(new LinearGradient(0, 0, w, h,
                        new int[]{
                            Color.parseColor("#C7C5E8"),
                            Color.parseColor("#F7A072"),
                            Color.parseColor("#F96E2A"),
                            Color.parseColor("#F3D5C8")
                        },
                        new float[]{0f, 0.45f, 0.80f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(0, 0, w, h, paint);
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
                        Color.parseColor("#E9E9EC"),
                        Color.parseColor("#F1EDF3"),
                        Color.parseColor("#EADCE8")
                    },
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, bgPaint);

            // Bottom-left warm rose/peach glow
            glowLeft.setShader(new RadialGradient(w * 0.25f, h * 0.95f, w * 0.55f,
                    new int[]{Color.parseColor("#88F4B8C8"), Color.TRANSPARENT},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, glowLeft);

            // Bottom-right soft violet/lavender glow
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

        // Active White Pill: Orange Grid + "Dashboard"
        sidebarCard.addView(makeSenseSidebarItem(activity, ICON_GRID_ORANGE, "Dashboard", true));

        // Menu Row 2: "My Sessions" (Version Switcher)
        LinearLayout navSessions = makeSenseSidebarItem(activity, ICON_PLAY_CIRCLE, "My Sessions", false);
        navSessions.setOnClickListener(v -> showVersionPickerModal(activity));
        sidebarCard.addView(navSessions);

        // Menu Row 3: "Popular Sessions" -> "Popular Mods" (Modrinth + CurseForge Hub)
        LinearLayout navPopular = makeSenseSidebarItem(activity, ICON_EASEL_MODS, "Popular Mods", false);
        navPopular.setOnClickListener(v -> showModernModHubModal(activity, "both"));
        sidebarCard.addView(navPopular);

        // Menu Row 4: "Community" -> "Microsoft Login" (Medium In-App OAuth Window)
        LinearLayout navMsLogin = makeSenseSidebarItem(activity, ICON_COMMUNITY, "Microsoft Login", false);
        navMsLogin.setOnClickListener(v -> showMediumMicrosoftLoginModal(activity));
        sidebarCard.addView(navMsLogin);

        // Menu Row 5: "Download" (Preloaded 1.0 & rd-132211)
        LinearLayout navDownload = makeSenseSidebarItem(activity, ICON_DOWNLOAD, "Download 1.0", false);
        navDownload.setOnClickListener(v -> new Thread(() -> extractPreloadedVersions(activity, true)).start());
        sidebarCard.addView(navDownload);

        // 1B: Bottom-Left Misty Artwork Card ("Sleep" in Screenshot 2 -> Guest / Offline Status)
        SenseArtworkCard sleepCard = new SenseArtworkCard(activity, 1);
        LinearLayout.LayoutParams slpLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.32f
        );
        sleepCard.setLayoutParams(slpLp);
        TextView sleepLabel = new TextView(activity);
        sleepLabel.setText(isGuest ? "Guest Mode" : "Online Ready");
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
        sleepCard.setOnClickListener(v -> {
            prefs.edit().putBoolean("is_guest", true).apply();
            writeAccountJson(activity, "Guest", false);
            Toast.makeText(activity, "Guest Local Account Active (Singleplayer & Mods Ready)", Toast.LENGTH_SHORT).show();
            injectSenseBentoDashboard(activity);
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

        // 2A: Top 3 Filter Pills ("Mindfulness | Focus | Relaxation")
        LinearLayout topPillsRow = new LinearLayout(activity);
        topPillsRow.setOrientation(LinearLayout.HORIZONTAL);
        topPillsRow.setGravity(Gravity.CENTER_VERTICAL);
        topPillsRow.setPadding(0, 0, 0, dp(activity, 6));

        final TextView p1 = makeSenseTopFilterPill(activity, "Release 1.0", selectedMcVersion.equals("1.0"));
        final TextView p2 = makeSenseTopFilterPill(activity, "rd-132211", selectedMcVersion.equals("rd-132211"));
        final TextView p3 = makeSenseTopFilterPill(activity, "Modded 1.20.1", selectedMcVersion.equals("1.20.1"));

        p1.setOnClickListener(v -> { selectedMcVersion = "1.0"; injectSenseBentoDashboard(activity); });
        p2.setOnClickListener(v -> { selectedMcVersion = "rd-132211"; injectSenseBentoDashboard(activity); });
        p3.setOnClickListener(v -> { selectedMcVersion = "1.20.1"; injectSenseBentoDashboard(activity); });

        topPillsRow.addView(p1);
        topPillsRow.addView(p2);
        topPillsRow.addView(p3);
        colCenter.addView(topPillsRow);

        // 2B: Top Pink/Violet Wave Artwork Card ("Meditate" + Orange ↗ Circle Button)
        SenseArtworkCard meditateCard = new SenseArtworkCard(activity, 0);
        LinearLayout.LayoutParams medLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.29f
        );
        medLp.bottomMargin = dp(activity, 10);
        meditateCard.setLayoutParams(medLp);

        TextView medTitle = new TextView(activity);
        medTitle.setText("Launch " + selectedMcVersion);
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

        // Circular White Button with Orange ↗ Arrow
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
        cOrbLp.bottomMargin = dp(activity, 8);
        greetInner.addView(centerOrb, cOrbLp);

        TextView greetText = new TextView(activity);
        greetText.setText("Good Morning\n" + activeUser + ", What's on\nyour mind?");
        greetText.setGravity(Gravity.CENTER);
        greetText.setTextColor(Color.parseColor("#1E1E24"));
        greetText.setTextSize(15.5f);
        greetText.setTypeface(Typeface.DEFAULT_BOLD);
        greetInner.addView(greetText);
        colCenter.addView(greetingCard);

        // 2D: Bottom "OTHERS" Frosted Card
        LinearLayout othersCard = makeFrostedCard(activity, "#EBF4EFF6", "#FFFFFF", 22, 10);
        LinearLayout.LayoutParams othLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.34f
        );
        othersCard.setLayoutParams(othLp);

        TextView othHeader = new TextView(activity);
        othHeader.setText("OTHERS");
        othHeader.setTextColor(Color.parseColor("#8E8E98"));
        othHeader.setTextSize(8.5f);
        othHeader.setTypeface(Typeface.DEFAULT_BOLD);
        othHeader.setPadding(dp(activity, 4), 0, 0, dp(activity, 2));
        othersCard.addView(othHeader);

        LinearLayout rowPrem = makeSenseCompactRow(activity, ICON_BADGE_PREMIUM, "Microsoft Account");
        rowPrem.setOnClickListener(v -> showMediumMicrosoftLoginModal(activity));
        othersCard.addView(rowPrem);

        LinearLayout rowSaved = makeSenseCompactRow(activity, ICON_STAR, "Saved Mods (.jar)");
        rowSaved.setOnClickListener(v -> showModernModHubModal(activity, "both"));
        othersCard.addView(rowSaved);

        LinearLayout rowLogout = makeSenseCompactRow(activity, ICON_LOGOUT, isGuest ? "Logged as Guest" : "Switch to Guest");
        rowLogout.setOnClickListener(v -> {
            prefs.edit().putBoolean("is_guest", true).apply();
            writeAccountJson(activity, "Guest", false);
            injectSenseBentoDashboard(activity);
        });
        othersCard.addView(rowLogout);
        colCenter.addView(othersCard);

        // =====================================================================
        // COLUMN 3 (RIGHT): SEARCH BAR + PHONK PILL + MUSIC/MOVE CARDS + FEED + ASK BAR
        // =====================================================================
        LinearLayout colRight = new LinearLayout(activity);
        colRight.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams col3Lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0.43f
        );
        colRight.setLayoutParams(col3Lp);
        masterRow.addView(colRight);

        // 3A: Top Row: Search Input Pill + Bell Circle Button + User Circle Button
        LinearLayout searchTopRow = new LinearLayout(activity);
        searchTopRow.setOrientation(LinearLayout.HORIZONTAL);
        searchTopRow.setGravity(Gravity.CENTER_VERTICAL);
        searchTopRow.setPadding(0, 0, 0, dp(activity, 8));

        LinearLayout searchBarPill = makeFrostedCard(activity, "#F5FFFFFF", "#FFFFFF", 20, 0);
        searchBarPill.setOrientation(LinearLayout.HORIZONTAL);
        searchBarPill.setGravity(Gravity.CENTER_VERTICAL);
        searchBarPill.setPadding(dp(activity, 12), dp(activity, 6), dp(activity, 12), dp(activity, 6));
        LinearLayout.LayoutParams sbpLp = new LinearLayout.LayoutParams(0, dp(activity, 32), 1f);
        sbpLp.rightMargin = dp(activity, 8);
        searchBarPill.setLayoutParams(sbpLp);

        SenseVectorIcon sIcon = new SenseVectorIcon(activity, ICON_SEARCH, "#8E8E98");
        LinearLayout.LayoutParams siLp = new LinearLayout.LayoutParams(dp(activity, 14), dp(activity, 14));
        siLp.rightMargin = dp(activity, 6);
        searchBarPill.addView(sIcon, siLp);

        TextView sHint = new TextView(activity);
        sHint.setText("Search Modrinth & CurseForge...");
        sHint.setTextColor(Color.parseColor("#8E8E98"));
        sHint.setTextSize(11f);
        searchBarPill.addView(sHint);
        searchBarPill.setOnClickListener(v -> showModernModHubModal(activity, "both"));
        searchTopRow.addView(searchBarPill);

        FrameLayout bellCircle = makeCircleIconButton(activity, ICON_BELL, "#F5FFFFFF", "#1E1E24", 32);
        LinearLayout.LayoutParams bcLp = new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32));
        bcLp.rightMargin = dp(activity, 6);
        bellCircle.setLayoutParams(bcLp);
        bellCircle.setOnClickListener(v -> showEngineOverlayDialog(activity, senseRoot));
        searchTopRow.addView(bellCircle);

        FrameLayout userCircle = makeCircleIconButton(activity, ICON_USER, "#F5FFFFFF", "#1E1E24", 32);
        userCircle.setLayoutParams(new LinearLayout.LayoutParams(dp(activity, 32), dp(activity, 32)));
        userCircle.setOnClickListener(v -> showMediumMicrosoftLoginModal(activity));
        searchTopRow.addView(userCircle);
        colRight.addView(searchTopRow);

        // 3B: Pill Audio Player Card ("Ease into Sleep" -> Real YouTube Phonk Player)
        LinearLayout phonkPillCard = makeFrostedCard(activity, "#F7FFFFFF", "#FFFFFF", 28, 8);
        phonkPillCard.setOrientation(LinearLayout.HORIZONTAL);
        phonkPillCard.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams ppcLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 52)
        );
        ppcLp.bottomMargin = dp(activity, 10);
        phonkPillCard.setLayoutParams(ppcLp);

        SenseVectorIcon albumOrb = new SenseVectorIcon(activity, ICON_SENSE_ORB, "#FF6B3D");
        LinearLayout.LayoutParams aoLp = new LinearLayout.LayoutParams(dp(activity, 36), dp(activity, 36));
        aoLp.rightMargin = dp(activity, 10);
        phonkPillCard.addView(albumOrb, aoLp);

        LinearLayout trackMeta = new LinearLayout(activity);
        trackMeta.setOrientation(LinearLayout.VERTICAL);
        final TextView trackTitleTv = new TextView(activity);
        trackTitleTv.setText(PHONK_TITLES[currentTrackIdx]);
        trackTitleTv.setTextColor(Color.parseColor("#1E1E24"));
        trackTitleTv.setTextSize(11.5f);
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
        playCircleBtn.setOnClickListener(v -> togglePhonk(activity, trackTitleTv, trackSubTv, playTriIcon));
        phonkPillCard.addView(playCircleBtn, new LinearLayout.LayoutParams(dp(activity, 34), dp(activity, 34)));
        colRight.addView(phonkPillCard);

        // Wire Meditate/Launch Card to auto-start Real YouTube Phonk & launch Minecraft
        meditateCard.setOnClickListener(v -> {
            if (!isPhonkPlaying) {
                playPhonkTrack(activity, currentTrackIdx, trackTitleTv, trackSubTv, playTriIcon);
            }
            launchMinecraftSelectedVersion(activity, rootVg, senseRoot);
        });

        // 3C: Middle Row: "Music" (Green/Peach Blur) + "Move" (Blue/Pink Aurora) Side-by-Side
        LinearLayout midCardsRow = new LinearLayout(activity);
        midCardsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams mcrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.36f
        );
        mcrLp.bottomMargin = dp(activity, 10);
        midCardsRow.setLayoutParams(mcrLp);

        // Left: "Music" Card (Tap to switch to next Real YouTube Phonk track)
        SenseArtworkCard musicCard = new SenseArtworkCard(activity, 2);
        LinearLayout.LayoutParams mcLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.58f);
        mcLp.rightMargin = dp(activity, 10);
        musicCard.setLayoutParams(mcLp);

        TextView musicCardTitle = new TextView(activity);
        musicCardTitle.setText("Music  •  Next Phonk");
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
        musicCard.setOnClickListener(v -> nextPhonk(activity, trackTitleTv, trackSubTv, playTriIcon));
        midCardsRow.addView(musicCard);

        // Right: "Move" -> "CurseForge" Card
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
        moveCard.setOnClickListener(v -> showModernModHubModal(activity, "curseforge"));
        midCardsRow.addView(moveCard);
        colRight.addView(midCardsRow);

        // 3D: Bottom Row: Dotted Chat/Status Card + "Breath of Balance" Square Card
        LinearLayout botCardsRow = new LinearLayout(activity);
        botCardsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams bcrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.48f
        );
        bcrLp.bottomMargin = dp(activity, 8);
        botCardsRow.setLayoutParams(bcrLp);

        // Left: Dotted Sense Assistant / Mod Status Card
        SenseArtworkCard chatCard = new SenseArtworkCard(activity, 4);
        LinearLayout.LayoutParams ccLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.62f);
        ccLp.rightMargin = dp(activity, 10);
        chatCard.setLayoutParams(ccLp);

        LinearLayout chatInner = new LinearLayout(activity);
        chatInner.setOrientation(LinearLayout.VERTICAL);
        int cp = dp(activity, 12);
        chatInner.setPadding(cp, cp, cp, cp);
        chatCard.addView(chatInner);

        TextView chatUserLine = new TextView(activity);
        chatUserLine.setText(activeUser + "  •  " + (isGuest ? "Guest Session (Singleplayer & Mods)" : "Microsoft Verified Account"));
        chatUserLine.setTextColor(Color.parseColor("#1E1E24"));
        chatUserLine.setTextSize(10.5f);
        chatUserLine.setTypeface(Typeface.DEFAULT_BOLD);
        chatInner.addView(chatUserLine);

        TextView chatBody = new TextView(activity);
        chatBody.setText("Modrinth & CurseForge repositories connected.\nPreloaded Minecraft 1.0 & rd-132211 ready.\nTap here to browse and install .jar mods.");
        chatBody.setTextColor(Color.parseColor("#52525B"));
        chatBody.setTextSize(9.5f);
        chatBody.setPadding(0, dp(activity, 4), 0, 0);
        chatInner.addView(chatBody);
        chatCard.setOnClickListener(v -> showModernModHubModal(activity, "both"));
        botCardsRow.addView(chatCard);

        // Right: "Breath of Balance" Artwork Card
        LinearLayout rightMiniCol = new LinearLayout(activity);
        rightMiniCol.setOrientation(LinearLayout.VERTICAL);
        rightMiniCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.38f));

        SenseArtworkCard bloomCard = new SenseArtworkCard(activity, 5);
        bloomCard.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        bloomCard.setOnClickListener(v -> showModernModHubModal(activity, "modrinth"));
        rightMiniCol.addView(bloomCard);

        TextView bloomTitle = new TextView(activity);
        bloomTitle.setText("Modrinth Hub");
        bloomTitle.setTextColor(Color.parseColor("#1E1E24"));
        bloomTitle.setTextSize(10.5f);
        bloomTitle.setTypeface(Typeface.DEFAULT_BOLD);
        bloomTitle.setPadding(dp(activity, 2), dp(activity, 4), 0, 0);
        rightMiniCol.addView(bloomTitle);

        TextView bloomSub = new TextView(activity);
        bloomSub.setText("Fabric & Forge  •  1-Tap");
        bloomSub.setTextColor(Color.parseColor("#8E8E98"));
        bloomSub.setTextSize(8.5f);
        bloomSub.setPadding(dp(activity, 2), 0, 0, 0);
        rightMiniCol.addView(bloomSub);
        botCardsRow.addView(rightMiniCol);
        colRight.addView(botCardsRow);

        // 3E: Bottom "Ask Anything..." Pill Bar
        LinearLayout askBar = makeFrostedCard(activity, "#F7FFFFFF", "#FFFFFF", 20, 0);
        askBar.setOrientation(LinearLayout.HORIZONTAL);
        askBar.setGravity(Gravity.CENTER_VERTICAL);
        askBar.setPadding(dp(activity, 14), dp(activity, 6), dp(activity, 12), dp(activity, 6));
        askBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 32)));

        TextView askHint = new TextView(activity);
        askHint.setText("Install mods from Modrinth or CurseForge...");
        askHint.setTextColor(Color.parseColor("#8E8E98"));
        askHint.setTextSize(10.5f);
        askBar.addView(askHint, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        SenseVectorIcon sendIcon = new SenseVectorIcon(activity, ICON_SEND, "#52525B");
        askBar.addView(sendIcon, new LinearLayout.LayoutParams(dp(activity, 15), dp(activity, 15)));
        askBar.setOnClickListener(v -> showModernModHubModal(activity, "both"));
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

        // Header Bar inside Medium Login Window
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

        TextView closeBtn = makeSenseTopFilterPill(activity, "Close", false);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        header.addView(closeBtn);
        container.addView(header);

        final TextView statusTv = new TextView(activity);
        statusTv.setText("Sign in with your Microsoft account below (Java & Bedrock supported)");
        statusTv.setTextColor(Color.parseColor("#6E6E77"));
        statusTv.setTextSize(10.5f);
        statusTv.setPadding(dp(activity, 6), 0, dp(activity, 6), dp(activity, 6));
        container.addView(statusTv);

        // Real In-App Microsoft OAuth2 WebView
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
                String url = request.getUrl().toString();
                return checkInterceptUrl(url);
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
                    statusTv.setText("Authenticating Xbox Live & Bedrock profile...");
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
        new Thread(() -> {
            String resolvedName = null;
            try {
                // 1. Exchange Microsoft OAuth Code for Live Access Token
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

                // 2. Authenticate with Xbox Live
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

                    // 3. Capture Real Xbox / Bedrock Gamertag (gtg) via XSTS
                    if (!xblToken.isEmpty()) {
                        captureXboxGamertag(xblToken);
                        resolvedName = lastXboxGamertag;
                    }
                }
            } catch (Throwable ignored) {}

            // Also trigger upstream MicrosoftBackgroundLogin in background if present
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

            activity.runOnUiThread(() -> {
                try { dialog.dismiss(); } catch (Throwable ignored) {}
                Toast.makeText(activity, "Welcome, " + finalTag + "!", Toast.LENGTH_LONG).show();
                injectSenseBentoDashboard(activity);
            });
        }).start();
    }

    // =========================================================================
    // REQUIREMENT 4: MODERN SENSE BENTO MODRINTH + CURSEFORGE MOD HUB
    // =========================================================================
    private static void showModernModHubModal(final Activity activity, String initialSource) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        DisplayMetrics dm = activity.getResources().getDisplayMetrics();
        int modalW = (int) (dm.widthPixels * 0.86f);
        int modalH = (int) (dm.heightPixels * 0.88f);

        LinearLayout root = makeFrostedCard(activity, "#F6F4F9", "#FFFFFF", 28, 16);
        root.setOrientation(LinearLayout.VERTICAL);

        // Top Bar: Orb + Title + Source Pills (Both | Modrinth | CurseForge) + Close
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
        title.setTextSize(15f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        topRow.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final String[] activeSource = new String[]{initialSource};
        final String[] activeVer = new String[]{"1.20.1"};
        final String[] activeLoader = new String[]{"fabric"};

        final TextView tabBoth = makeSenseTopFilterPill(activity, "All Sources", activeSource[0].equals("both"));
        final TextView tabModrinth = makeSenseTopFilterPill(activity, "Modrinth", activeSource[0].equals("modrinth"));
        final TextView tabCurse = makeSenseTopFilterPill(activity, "CurseForge", activeSource[0].equals("curseforge"));
        TextView closeBtn = makeSenseTopFilterPill(activity, "Close", false);
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        topRow.addView(tabBoth);
        topRow.addView(tabModrinth);
        topRow.addView(tabCurse);
        topRow.addView(closeBtn);
        root.addView(topRow);

        // Search & Filter Pill Bar
        LinearLayout filterBar = new LinearLayout(activity);
        filterBar.setOrientation(LinearLayout.HORIZONTAL);
        filterBar.setGravity(Gravity.CENTER_VERTICAL);
        filterBar.setPadding(0, 0, 0, dp(activity, 10));

        final EditText searchInput = new EditText(activity);
        searchInput.setHint("Search mods (Sodium, JEI, Iris, Optifine, Waystones, Create)...");
        searchInput.setHintTextColor(Color.parseColor("#8E8E98"));
        searchInput.setTextColor(Color.parseColor("#1E1E24"));
        searchInput.setTextSize(12f);
        searchInput.setSingleLine(true);
        searchInput.setPadding(dp(activity, 14), dp(activity, 6), dp(activity, 14), dp(activity, 6));
        GradientDrawable sBg = new GradientDrawable();
        sBg.setColor(Color.WHITE);
        sBg.setCornerRadius(dp(activity, 18));
        searchInput.setBackground(sBg);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(0, dp(activity, 36), 1f);
        sLp.rightMargin = dp(activity, 8);
        filterBar.addView(searchInput, sLp);

        // Version & Loader Quick Toggle Pills
        final String[] versions = new String[]{"1.21.4", "1.21.1", "1.20.1", "1.19.2", "1.16.5", "1.12.2"};
        final int[] verIdx = new int[]{2};
        final TextView verPill = makeSenseTopFilterPill(activity, "MC " + activeVer[0], true);
        filterBar.addView(verPill);

        final String[] loaders = new String[]{"fabric", "forge", "neoforge"};
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

        // Scrollable Bento Mod Cards Container
        ScrollView scroll = new ScrollView(activity);
        final LinearLayout resultsContainer = new LinearLayout(activity);
        resultsContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(resultsContainer);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final Runnable triggerSearch = () -> searchModrinthAndCurseForge(
                activity,
                searchInput.getText().toString().trim(),
                activeVer[0],
                activeLoader[0],
                activeSource[0],
                resultsContainer
        );

        tabBoth.setOnClickListener(v -> { activeSource[0] = "both"; triggerSearch.run(); });
        tabModrinth.setOnClickListener(v -> { activeSource[0] = "modrinth"; triggerSearch.run(); });
        tabCurse.setOnClickListener(v -> { activeSource[0] = "curseforge"; triggerSearch.run(); });

        verPill.setOnClickListener(v -> {
            verIdx[0] = (verIdx[0] + 1) % versions.length;
            activeVer[0] = versions[verIdx[0]];
            verPill.setText("MC " + activeVer[0]);
            triggerSearch.run();
        });

        ldrPill.setOnClickListener(v -> {
            ldrIdx[0] = (ldrIdx[0] + 1) % loaders.length;
            activeLoader[0] = loaders[ldrIdx[0]];
            ldrPill.setText("Loader: " + activeLoader[0]);
            triggerSearch.run();
        });

        searchBtn.setOnClickListener(v -> triggerSearch.run());
        triggerSearch.run();

        dialog.setContentView(root);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(modalW, modalH);
        }
        dialog.show();
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
        loading.setText("Fetching mods from " + sourceFilter.toUpperCase() + " (" + loader + " " + mcVer + ")...");
        loading.setTextColor(Color.parseColor("#6E6E77"));
        loading.setPadding(dp(activity, 8), dp(activity, 8), dp(activity, 8), dp(activity, 8));
        resultsBox.addView(loading);

        new Thread(() -> {
            final JSONArray combined = new JSONArray();

            // 1. Fetch from CurseForge API Mirror (api.curse.tools) if source is "both" or "curseforge"
            if (sourceFilter.equals("both") || sourceFilter.equals("curseforge")) {
                HttpURLConnection cfConn = null;
                try {
                    String cfUrl = "https://api.curse.tools/v1/cf/mods/search?gameId=432&classId=6&pageSize=10"
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
                                item.put("cf_id", m.optInt("id", 0));
                                JSONArray latestFiles = m.optJSONArray("latestFiles");
                                if (latestFiles != null && latestFiles.length() > 0) {
                                    JSONObject lf = latestFiles.getJSONObject(0);
                                    item.put("direct_url", lf.optString("downloadUrl", ""));
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

            // 2. Fetch from Modrinth v2 API
            if (sourceFilter.equals("both") || sourceFilter.equals("modrinth") || combined.length() == 0) {
                HttpURLConnection mrConn = null;
                try {
                    String facets = "[[\"project_type:mod\"],[\"versions:" + mcVer + "\"],[\"categories:" + loader + "\"]]";
                    String mrUrl = "https://api.modrinth.com/v2/search?limit=14&query="
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
                            item.put("source", sourceFilter.equals("curseforge") ? "CurseForge / Modrinth Mirror" : "Modrinth");
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

            activity.runOnUiThread(() -> {
                resultsBox.removeAllViews();
                if (combined.length() == 0) {
                    TextView empty = new TextView(activity);
                    empty.setText("No mods found for " + loader + " " + mcVer + ". Try another search term.");
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
                    installPill.setOnClickListener(v -> {
                        installPill.setText("Downloading...");
                        installModJar(activity, projId, directUrl, fileName, mcVer, loader, installPill);
                    });
                    card.addView(installPill);
                    resultsBox.addView(card);
                }
            });
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
        new Thread(() -> {
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
                activity.runOnUiThread(() -> {
                    btnPill.setText("Installed");
                    Toast.makeText(activity, "Installed " + savedName + " into .minecraft/mods!", Toast.LENGTH_LONG).show();
                });
            } catch (final Throwable e) {
                activity.runOnUiThread(() -> {
                    btnPill.setText("Retry");
                    Toast.makeText(activity, "Install error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    // =========================================================================
    // REQUIREMENT 2: REAL YOUTUBE PHONK PLAYER ENGINE
    // (BUNDLED YT-DLP MP3 + ARCHIVE MP3 STREAM + YOUTUBE IFRAME AUDIO FALLBACK)
    // =========================================================================
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

        // 2. Fallback: Stream Real Phonk MP3 asynchronously, or play YouTube Embed Audio directly
        new Thread(() -> {
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
                    activity.runOnUiThread(() -> {
                        try {
                            if (!isPhonkPlaying) return;
                            phonkPlayer = new MediaPlayer();
                            phonkPlayer.setDataSource(cacheMp3.getAbsolutePath());
                            phonkPlayer.setLooping(true);
                            phonkPlayer.prepare();
                            phonkPlayer.start();
                        } catch (Throwable ignored) {}
                    });
                    return;
                }
            } catch (Throwable ignored) {}

            // 3. Direct YouTube Audio WebView Fallback
            activity.runOnUiThread(() -> {
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
            });
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
        row.setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4));

        SenseVectorIcon icon = new SenseVectorIcon(ctx, iconType, "#3C3C43");
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(ctx, 15), dp(ctx, 15));
        iLp.rightMargin = dp(ctx, 8);
        row.addView(icon, iLp);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#1E1E24"));
        tv.setTextSize(10.5f);
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
        t.setText("Select Minecraft Profile");
        t.setTextColor(Color.parseColor("#1E1E24"));
        t.setTextSize(15f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(activity, 10));
        card.addView(t);

        String[] vers = new String[]{"1.0", "rd-132211", "1.20.1", "1.21.4"};
        for (final String v : vers) {
            TextView pill = makeSenseTopFilterPill(activity, "Minecraft " + v, selectedMcVersion.equals(v));
            pill.setPadding(dp(activity, 16), dp(activity, 10), dp(activity, 16), dp(activity, 10));
            pill.setOnClickListener(view -> {
                selectedMcVersion = v;
                d.dismiss();
                injectSenseBentoDashboard(activity);
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
        TextView backPill = makeSenseTopFilterPill(activity, "Return to Sense Dashboard", true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        lp.topMargin = dp(activity, 20);
        lp.rightMargin = dp(activity, 16);
        backPill.setOnClickListener(v -> {
            vg.removeView(backPill);
            senseRoot.setVisibility(View.VISIBLE);
        });
        vg.addView(backPill, lp);
    }

    private static void launchMinecraftSelectedVersion(final Activity activity, ViewGroup rootVg, View senseRoot) {
        Toast.makeText(activity, "Launching Minecraft " + selectedMcVersion + " with Phonk Radio...", Toast.LENGTH_SHORT).show();
        Button playBtn = findUnderlyingPlayButton(rootVg, senseRoot);
        if (playBtn != null) {
            playBtn.performClick();
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
    // AUTH & PRELOADED VERSION HELPERS
    // =========================================================================
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
                activity.runOnUiThread(() -> Toast.makeText(activity, "Preloaded Minecraft 1.0 & rd-132211 extracted!", Toast.LENGTH_SHORT).show());
            }
        } catch (Throwable ignored) {}
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }
}
