package net.kdt.pojavlaunch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
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
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class NovaModMenu {
    private static final String PREFS_NAME = "nova_bento_prefs";
    public static volatile String lastXboxGamertag = null;
    private static volatile Context appContext = null;
    private static MediaPlayer phonkPlayer = null;
    private static int currentTrackIdx = 0;
    private static boolean isPhonkPlaying = false;

    private static final String[] PHONK_FILES = new String[]{
        "01_nova_drift_160bpm.wav",
        "02_midnight_cowbell_152bpm.wav",
        "03_shadow_808_168bpm.wav"
    };

    private static final String[] PHONK_TITLES = new String[]{
        "NOVA DRIFT  |  160 BPM",
        "MIDNIGHT TOKYO  |  152 BPM",
        "SHADOW REALM  |  168 BPM"
    };

    public static void attach(final Activity activity) {
        if (activity == null) return;
        appContext = activity.getApplicationContext();
        activity.setTitle("NovaLauncher");

        // Force Horizontal (Landscape) Orientation for the 3-Column Bento UI
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

        new Thread(new Runnable() {
            @Override
            public void run() {
                ensureGuestAccountExists(activity);
                extractPreloadedVersions(activity, false);
            }
        }).start();

        try {
            Window w = activity.getWindow();
            w.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#0B0C0E")));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                w.setStatusBarColor(Color.parseColor("#0B0C0E"));
                w.setNavigationBarColor(Color.parseColor("#0B0C0E"));
            }
        } catch (Throwable ignored) {}

        final View decor = activity.getWindow().getDecorView();
        decor.post(new Runnable() {
            @Override
            public void run() {
                injectBentoDashboard(activity);
            }
        });
    }

    // =========================================================================
    // CUSTOM VECTOR LOGO & ICON ENGINE (ZERO EMOJIS)
    // =========================================================================
    public static final int ICON_ORB = 0;
    public static final int ICON_GRID = 1;
    public static final int ICON_MODS = 2;
    public static final int ICON_MUSIC = 3;
    public static final int ICON_USER = 4;
    public static final int ICON_SHIELD = 5;
    public static final int ICON_THEME = 6;
    public static final int ICON_SETTINGS = 7;
    public static final int ICON_PLAY = 8;
    public static final int ICON_PAUSE = 9;
    public static final int ICON_SKIP = 10;
    public static final int ICON_DOWNLOAD = 11;

    public static class NovaVectorIcon extends View {
        private final int type;
        private int tintColor;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        public NovaVectorIcon(Context ctx, int type, String hexColor) {
            super(ctx);
            this.type = type;
            this.tintColor = Color.parseColor(hexColor);
        }

        public void setTint(String hexColor) {
            this.tintColor = Color.parseColor(hexColor);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w / 2f;
            float cy = h / 2f;
            float s = Math.min(w, h);
            float stroke = Math.max(2f, s * 0.11f);

            paint.reset();
            paint.setAntiAlias(true);
            paint.setColor(tintColor);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);

            switch (type) {
                case ICON_ORB: {
                    float rad = s * 0.45f;
                    paint.setStyle(Paint.Style.FILL);
                    paint.setShader(new RadialGradient(
                            cx - rad * 0.25f, cy - rad * 0.25f, rad * 1.25f,
                            new int[]{Color.parseColor("#F9A8D4"), Color.parseColor("#FB7185"), Color.parseColor("#6366F1"), Color.parseColor("#0F172A")},
                            new float[]{0f, 0.35f, 0.75f, 1f}, Shader.TileMode.CLAMP));
                    c.drawCircle(cx, cy, rad, paint);
                    paint.setShader(null);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(Color.parseColor("#66FFFFFF"));
                    paint.setStrokeWidth(Math.max(1.5f, s * 0.04f));
                    c.drawCircle(cx, cy, rad * 0.72f, paint);
                    break;
                }
                case ICON_GRID: {
                    paint.setStyle(Paint.Style.FILL);
                    float pad = s * 0.16f;
                    float gap = s * 0.10f;
                    float box = (s - pad * 2f - gap) / 2f;
                    float r = box * 0.28f;
                    rect.set(pad, pad, pad + box, pad + box);
                    c.drawRoundRect(rect, r, r, paint);
                    rect.set(pad + box + gap, pad, pad + box * 2f + gap, pad + box);
                    c.drawRoundRect(rect, r, r, paint);
                    rect.set(pad, pad + box + gap, pad + box, pad + box * 2f + gap);
                    c.drawRoundRect(rect, r, r, paint);
                    rect.set(pad + box + gap, pad + box + gap, pad + box * 2f + gap, pad + box * 2f + gap);
                    c.drawRoundRect(rect, r, r, paint);
                    break;
                }
                case ICON_MODS: {
                    // Isometric Hexagon / Cube Logo
                    paint.setStyle(Paint.Style.STROKE);
                    float r = s * 0.38f;
                    path.reset();
                    for (int i = 0; i < 6; i++) {
                        double ang = Math.toRadians(60 * i - 30);
                        float px = cx + (float) (r * Math.cos(ang));
                        float py = cy + (float) (r * Math.sin(ang));
                        if (i == 0) path.moveTo(px, py);
                        else path.lineTo(px, py);
                    }
                    path.close();
                    c.drawPath(path, paint);
                    c.drawLine(cx, cy, cx, cy + r, paint);
                    c.drawLine(cx, cy, cx - r * 0.86f, cy - r * 0.5f, paint);
                    c.drawLine(cx, cy, cx + r * 0.86f, cy - r * 0.5f, paint);
                    break;
                }
                case ICON_MUSIC: {
                    // Audio Waveform Bars
                    paint.setStyle(Paint.Style.STROKE);
                    float x1 = s * 0.22f, x2 = s * 0.40f, x3 = s * 0.58f, x4 = s * 0.76f;
                    c.drawLine(x1, cy - s * 0.16f, x1, cy + s * 0.16f, paint);
                    c.drawLine(x2, cy - s * 0.34f, x2, cy + s * 0.34f, paint);
                    c.drawLine(x3, cy - s * 0.22f, x3, cy + s * 0.22f, paint);
                    c.drawLine(x4, cy - s * 0.30f, x4, cy + s * 0.30f, paint);
                    break;
                }
                case ICON_USER: {
                    paint.setStyle(Paint.Style.STROKE);
                    c.drawCircle(cx, s * 0.34f, s * 0.16f, paint);
                    rect.set(s * 0.20f, s * 0.56f, s * 0.80f, s * 1.05f);
                    c.drawArc(rect, 195f, 150f, false, paint);
                    break;
                }
                case ICON_SHIELD: {
                    paint.setStyle(Paint.Style.STROKE);
                    path.reset();
                    path.moveTo(cx, s * 0.15f);
                    path.lineTo(s * 0.80f, s * 0.28f);
                    path.lineTo(s * 0.74f, s * 0.62f);
                    path.lineTo(cx, s * 0.86f);
                    path.lineTo(s * 0.26f, s * 0.62f);
                    path.lineTo(s * 0.20f, s * 0.28f);
                    path.close();
                    c.drawPath(path, paint);
                    break;
                }
                case ICON_THEME: {
                    paint.setStyle(Paint.Style.STROKE);
                    float r = s * 0.34f;
                    c.drawCircle(cx, cy, r, paint);
                    paint.setStyle(Paint.Style.FILL);
                    rect.set(cx - r, cy - r, cx + r, cy + r);
                    c.drawArc(rect, -90f, 180f, true, paint);
                    break;
                }
                case ICON_SETTINGS: {
                    paint.setStyle(Paint.Style.STROKE);
                    c.drawLine(s * 0.20f, s * 0.32f, s * 0.80f, s * 0.32f, paint);
                    c.drawLine(s * 0.20f, s * 0.68f, s * 0.80f, s * 0.68f, paint);
                    paint.setStyle(Paint.Style.FILL);
                    c.drawCircle(s * 0.38f, s * 0.32f, s * 0.11f, paint);
                    c.drawCircle(s * 0.62f, s * 0.68f, s * 0.11f, paint);
                    break;
                }
                case ICON_PLAY: {
                    paint.setStyle(Paint.Style.FILL);
                    path.reset();
                    path.moveTo(s * 0.28f, s * 0.20f);
                    path.lineTo(s * 0.78f, cy);
                    path.lineTo(s * 0.28f, s * 0.80f);
                    path.close();
                    c.drawPath(path, paint);
                    break;
                }
                case ICON_PAUSE: {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(stroke * 1.3f);
                    c.drawLine(s * 0.34f, s * 0.24f, s * 0.34f, s * 0.76f, paint);
                    c.drawLine(s * 0.66f, s * 0.24f, s * 0.66f, s * 0.76f, paint);
                    break;
                }
                case ICON_SKIP: {
                    paint.setStyle(Paint.Style.FILL);
                    path.reset();
                    path.moveTo(s * 0.22f, s * 0.24f);
                    path.lineTo(s * 0.64f, cy);
                    path.lineTo(s * 0.22f, s * 0.76f);
                    path.close();
                    c.drawPath(path, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    c.drawLine(s * 0.74f, s * 0.24f, s * 0.74f, s * 0.76f, paint);
                    break;
                }
                case ICON_DOWNLOAD: {
                    paint.setStyle(Paint.Style.STROKE);
                    c.drawLine(cx, s * 0.18f, cx, s * 0.64f, paint);
                    c.drawLine(cx - s * 0.18f, s * 0.46f, cx, s * 0.64f, paint);
                    c.drawLine(cx + s * 0.18f, s * 0.46f, cx, s * 0.64f, paint);
                    c.drawLine(s * 0.22f, s * 0.80f, s * 0.78f, s * 0.80f, paint);
                    break;
                }
            }
        }
    }

    // =========================================================================
    // HORIZONTAL 3-COLUMN BENTO GRID DASHBOARD (MATCHING REFERENCE IMAGES)
    // =========================================================================
    private static class DotMatrixBentoRoot extends FrameLayout {
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final boolean lightMode;

        public DotMatrixBentoRoot(Context ctx, boolean lightMode) {
            super(ctx);
            this.lightMode = lightMode;
            setWillNotDraw(false);
            dotPaint.setColor(lightMode ? Color.parseColor("#CBD5E1") : Color.parseColor("#222631"));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawColor(lightMode ? Color.parseColor("#EEF2F6") : Color.parseColor("#0B0C0E"));
            int step = dp(getContext(), 22);
            float r = dp(getContext(), 1);
            for (int y = step / 2; y < getHeight(); y += step) {
                for (int x = step / 2; x < getWidth(); x += step) {
                    canvas.drawCircle(x, y, r, dotPaint);
                }
            }
            glowPaint.setShader(new RadialGradient(
                    getWidth() * 0.65f, getHeight() * 0.30f, Math.max(getWidth(), getHeight()) * 0.50f,
                    lightMode ? new int[]{Color.parseColor("#30EC4899"), Color.parseColor("#206366F1"), Color.TRANSPARENT}
                              : new int[]{Color.parseColor("#246366F1"), Color.parseColor("#16EC4899"), Color.TRANSPARENT},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, getWidth(), getHeight(), glowPaint);
            super.onDraw(canvas);
        }
    }

    public static void injectBentoDashboard(final Activity activity) {
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        final ViewGroup rootVg = (ViewGroup) decor;
        View old = rootVg.findViewWithTag("NOVA_BENTO_ROOT");
        if (old != null) rootVg.removeView(old);
        View oldPill = rootVg.findViewWithTag("NOVA_RETURN_PILL");
        if (oldPill != null) rootVg.removeView(oldPill);

        final SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        final boolean light = prefs.getBoolean("bento_light_mode", false);
        final String cardBg = light ? "#F5FFFFFF" : "#13151A";
        final String cardStroke = light ? "#E2E8F0" : "#252834";
        final String textPrimary = light ? "#0F172A" : "#F8FAFC";
        final String textSecondary = light ? "#64748B" : "#94A3B8";
        final String pillIdleBg = light ? "#E2E8F0" : "#1D202A";

        final DotMatrixBentoRoot bentoRoot = new DotMatrixBentoRoot(activity, light);
        bentoRoot.setTag("NOVA_BENTO_ROOT");
        bentoRoot.setClickable(true);

        // Horizontal 3-Column Master Container
        LinearLayout masterRow = new LinearLayout(activity);
        masterRow.setOrientation(LinearLayout.HORIZONTAL);
        int outerPad = dp(activity, 14);
        masterRow.setPadding(outerPad, outerPad, outerPad, outerPad);
        bentoRoot.addView(masterRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        // ---------------------------------------------------------------------
        // COLUMN 1 (LEFT SIDEBAR BENTO CARD - 24% WIDTH)
        // ---------------------------------------------------------------------
        LinearLayout leftSidebar = makeBentoCard(activity, cardBg, cardStroke, 0);
        LinearLayout.LayoutParams col1Lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0.24f
        );
        col1Lp.rightMargin = dp(activity, 12);
        leftSidebar.setLayoutParams(col1Lp);
        masterRow.addView(leftSidebar);

        // Brand Logo Header inside Left Sidebar
        LinearLayout brandRow = new LinearLayout(activity);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);
        brandRow.setPadding(0, 0, 0, dp(activity, 14));
        NovaVectorIcon brandOrb = new NovaVectorIcon(activity, ICON_ORB, "#6366F1");
        LinearLayout.LayoutParams bOrbLp = new LinearLayout.LayoutParams(dp(activity, 30), dp(activity, 30));
        bOrbLp.rightMargin = dp(activity, 10);
        brandRow.addView(brandOrb, bOrbLp);

        TextView brandTitle = new TextView(activity);
        brandTitle.setText("NovaLauncher");
        brandTitle.setTextColor(Color.parseColor(textPrimary));
        brandTitle.setTextSize(16f);
        brandTitle.setTypeface(Typeface.DEFAULT_BOLD);
        brandRow.addView(brandTitle);
        leftSidebar.addView(brandRow);

        TextView navSection = new TextView(activity);
        navSection.setText("WORKSPACE");
        navSection.setTextColor(Color.parseColor(textSecondary));
        navSection.setTextSize(10f);
        navSection.setTypeface(Typeface.DEFAULT_BOLD);
        navSection.setPadding(dp(activity, 4), 0, 0, dp(activity, 6));
        leftSidebar.addView(navSection);

        // Sidebar Navigation Items with Vector Icons
        LinearLayout navDash = makeSidebarRow(activity, ICON_GRID, "Dashboard", true, light, textPrimary, textSecondary);
        leftSidebar.addView(navDash);

        LinearLayout navMods = makeSidebarRow(activity, ICON_MODS, "Modrinth Hub", false, light, textPrimary, textSecondary);
        navMods.setOnClickListener(v -> showModrinthDialog(activity));
        leftSidebar.addView(navMods);

        LinearLayout navUser = makeSidebarRow(activity, ICON_USER, "Accounts", false, light, textPrimary, textSecondary);
        navUser.setOnClickListener(v -> showGamertagOrMsLoginDialog(activity, bentoRoot));
        leftSidebar.addView(navUser);

        LinearLayout navTheme = makeSidebarRow(activity, ICON_THEME, light ? "Obsidian Mode" : "Sense Light", false, light, textPrimary, textSecondary);
        navTheme.setOnClickListener(v -> {
            prefs.edit().putBoolean("bento_light_mode", !light).apply();
            injectBentoDashboard(activity);
        });
        leftSidebar.addView(navTheme);

        LinearLayout navEngine = makeSidebarRow(activity, ICON_SETTINGS, "Engine Controls", false, light, textPrimary, textSecondary);
        navEngine.setOnClickListener(v -> {
            bentoRoot.setVisibility(View.GONE);
            attachReturnToBentoPill(activity, bentoRoot);
        });
        leftSidebar.addView(navEngine);

        // Spacer pushing status footer to bottom of sidebar
        View sideSpacer = new View(activity);
        leftSidebar.addView(sideSpacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final boolean isGuest = prefs.getBoolean("is_guest", getResolvedGamertag().equals("Guest"));
        final String activeUser = getResolvedGamertag();

        TextView sideStatus = new TextView(activity);
        sideStatus.setText(isGuest ? "MODE: GUEST OFFLINE" : "MODE: MICROSOFT AUTH");
        sideStatus.setTextColor(Color.parseColor(isGuest ? "#FACC15" : "#4ADE80"));
        sideStatus.setTextSize(10f);
        sideStatus.setTypeface(Typeface.DEFAULT_BOLD);
        leftSidebar.addView(sideStatus);

        // ---------------------------------------------------------------------
        // COLUMN 2 (CENTER HERO COLUMN - 43% WIDTH)
        // ---------------------------------------------------------------------
        LinearLayout centerCol = new LinearLayout(activity);
        centerCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams col2Lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0.43f
        );
        col2Lp.rightMargin = dp(activity, 12);
        centerCol.setLayoutParams(col2Lp);
        masterRow.addView(centerCol);

        // Top Filter Pill Strip (Like "Mindfulness | Focus | Relaxation" in Image 2)
        LinearLayout topPills = new LinearLayout(activity);
        topPills.setOrientation(LinearLayout.HORIZONTAL);
        topPills.setPadding(0, 0, 0, dp(activity, 10));
        topPills.addView(makeIconButtonPill(activity, ICON_GRID, "Nova Core v3", light ? "#0F172A" : "#FFFFFF", light ? "#FFFFFF" : "#0B0C0E"));
        TextView verChip = makeIconButtonPill(activity, ICON_DOWNLOAD, "Preloaded 1.0 & rd-132211", cardBg, textSecondary);
        verChip.setOnClickListener(v -> new Thread(() -> extractPreloadedVersions(activity, true)).start());
        topPills.addView(verChip);
        centerCol.addView(topPills);

        // Main Hero Bento Card (Fills remaining height of Center Column)
        LinearLayout heroCard = makeBentoCard(activity, cardBg, "#6366F1", 0);
        heroCard.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        );
        heroCard.setLayoutParams(heroLp);
        centerCol.addView(heroCard);

        NovaVectorIcon centerOrb = new NovaVectorIcon(activity, ICON_ORB, "#6366F1");
        LinearLayout.LayoutParams cOrbLp = new LinearLayout.LayoutParams(dp(activity, 52), dp(activity, 52));
        cOrbLp.bottomMargin = dp(activity, 10);
        heroCard.addView(centerOrb, cOrbLp);

        TextView greetingTv = new TextView(activity);
        greetingTv.setText("Welcome back, " + activeUser + "\nSelect your runtime profile");
        greetingTv.setGravity(Gravity.CENTER);
        greetingTv.setTextColor(Color.parseColor(textPrimary));
        greetingTv.setTextSize(19f);
        greetingTv.setTypeface(Typeface.DEFAULT_BOLD);
        heroCard.addView(greetingTv);

        TextView subAuthTv = new TextView(activity);
        subAuthTv.setText(isGuest
                ? "Guest Local Session  |  Singleplayer & Mods Active  |  Multiplayer Disabled"
                : "Microsoft / Bedrock Verified  |  Full Access Enabled");
        subAuthTv.setTextColor(Color.parseColor(textSecondary));
        subAuthTv.setTextSize(11f);
        subAuthTv.setGravity(Gravity.CENTER);
        subAuthTv.setPadding(0, dp(activity, 4), 0, dp(activity, 12));
        heroCard.addView(subAuthTv);

        // Node-Styled Version Selector Spinner
        List<String> verList = getInstalledVersionsList(activity);
        final Spinner heroVerSpinner = new Spinner(activity);
        heroVerSpinner.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, verList));
        GradientDrawable spinBg = new GradientDrawable();
        spinBg.setColor(Color.parseColor(light ? "#F8FAFC" : "#0B0C0E"));
        spinBg.setCornerRadius(dp(activity, 14));
        spinBg.setStroke(dp(activity, 1), Color.parseColor(cardStroke));
        heroVerSpinner.setBackground(spinBg);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 40)
        );
        spinLp.bottomMargin = dp(activity, 12);
        heroCard.addView(heroVerSpinner, spinLp);

        // Primary Launch Action Bar with Play Vector Icon
        LinearLayout launchBtn = makeActionPillWithIcon(
                activity,
                ICON_PLAY,
                "LAUNCH MINECRAFT",
                light ? "#0F172A" : "#FFFFFF",
                light ? "#FFFFFF" : "#0B0C0E"
        );
        launchBtn.setGravity(Gravity.CENTER);
        heroCard.addView(launchBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 44)
        ));

        // ---------------------------------------------------------------------
        // COLUMN 3 (RIGHT WIDGETS COLUMN - 33% WIDTH)
        // ---------------------------------------------------------------------
        LinearLayout rightCol = new LinearLayout(activity);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams col3Lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 0.33f
        );
        rightCol.setLayoutParams(col3Lp);
        masterRow.addView(rightCol);

        // WIDGET 1: PHONK INSTALL RADIO CARD
        LinearLayout phonkCard = makeBentoCard(activity, cardBg, "#EC4899", dp(activity, 10));
        LinearLayout.LayoutParams pCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.36f
        );
        pCardLp.bottomMargin = dp(activity, 10);
        phonkCard.setLayoutParams(pCardLp);
        rightCol.addView(phonkCard);

        LinearLayout phonkTop = new LinearLayout(activity);
        phonkTop.setOrientation(LinearLayout.HORIZONTAL);
        phonkTop.setGravity(Gravity.CENTER_VERTICAL);
        NovaVectorIcon waveIcon = new NovaVectorIcon(activity, ICON_MUSIC, "#F472B6");
        LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(dp(activity, 18), dp(activity, 18));
        wLp.rightMargin = dp(activity, 6);
        phonkTop.addView(waveIcon, wLp);

        TextView phonkHeader = new TextView(activity);
        phonkHeader.setText("PHONK INSTALL RADIO");
        phonkHeader.setTextColor(Color.parseColor("#F472B6"));
        phonkHeader.setTextSize(10.5f);
        phonkHeader.setTypeface(Typeface.DEFAULT_BOLD);
        phonkTop.addView(phonkHeader);
        phonkCard.addView(phonkTop);

        final TextView phonkStatus = new TextView(activity);
        phonkStatus.setText(PHONK_TITLES[currentTrackIdx]);
        phonkStatus.setTextColor(Color.parseColor(textPrimary));
        phonkStatus.setTextSize(12.5f);
        phonkStatus.setTypeface(Typeface.DEFAULT_BOLD);
        phonkStatus.setPadding(0, dp(activity, 4), 0, dp(activity, 8));
        phonkCard.addView(phonkStatus);

        LinearLayout phonkControls = new LinearLayout(activity);
        phonkControls.setOrientation(LinearLayout.HORIZONTAL);
        final NovaVectorIcon playPauseIcon = new NovaVectorIcon(activity, isPhonkPlaying ? ICON_PAUSE : ICON_PLAY, "#FFFFFF");
        final TextView playPauseLabel = new TextView(activity);
        LinearLayout playPhonkBtn = makeDynamicIconPill(
                activity, playPauseIcon, playPauseLabel,
                isPhonkPlaying ? "Pause" : "Play", "#EC4899", "#FFFFFF"
        );
        playPhonkBtn.setOnClickListener(v -> togglePhonk(activity, phonkStatus, playPauseIcon, playPauseLabel));
        phonkControls.addView(playPhonkBtn);

        LinearLayout nextPhonkBtn = makeActionPillWithIcon(activity, ICON_SKIP, "Next", pillIdleBg, textPrimary);
        nextPhonkBtn.setOnClickListener(v -> nextPhonk(activity, phonkStatus, playPauseIcon, playPauseLabel));
        phonkControls.addView(nextPhonkBtn);
        phonkCard.addView(phonkControls);

        // Wire Launch Button to auto-start Phonk Radio & click underlying launch button
        launchBtn.setOnClickListener(v -> {
            if (!isPhonkPlaying) {
                playPhonkTrack(activity, currentTrackIdx, phonkStatus, playPauseIcon, playPauseLabel);
            }
            Toast.makeText(activity, "Starting Minecraft + Phonk Radio...", Toast.LENGTH_SHORT).show();
            Button realPlay = findUnderlyingPlayButton(rootVg, bentoRoot);
            if (realPlay != null) {
                realPlay.performClick();
            } else {
                bentoRoot.setVisibility(View.GONE);
                attachReturnToBentoPill(activity, bentoRoot);
            }
        });

        // WIDGET 2: ACCOUNT & GUEST SWITCHER CARD
        LinearLayout accCard = makeBentoCard(activity, cardBg, cardStroke, dp(activity, 10));
        LinearLayout.LayoutParams aCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.32f
        );
        aCardLp.bottomMargin = dp(activity, 10);
        accCard.setLayoutParams(aCardLp);
        rightCol.addView(accCard);

        TextView accHeader = new TextView(activity);
        accHeader.setText("ACCOUNT ACCESS");
        accHeader.setTextColor(Color.parseColor("#60A5FA"));
        accHeader.setTextSize(10.5f);
        accHeader.setTypeface(Typeface.DEFAULT_BOLD);
        accHeader.setPadding(0, 0, 0, dp(activity, 6));
        accCard.addView(accHeader);

        LinearLayout accBtns = new LinearLayout(activity);
        accBtns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout guestBtn = makeActionPillWithIcon(
                activity, ICON_USER, "Guest Mode",
                isGuest ? "#4ADE80" : pillIdleBg,
                isGuest ? "#0B0C0E" : textPrimary
        );
        guestBtn.setOnClickListener(v -> {
            prefs.edit().putBoolean("is_guest", true).apply();
            writeAccountJson(activity, "Guest", false);
            Toast.makeText(activity, "Guest Mode Active (Singleplayer & Mods Enabled)", Toast.LENGTH_SHORT).show();
            injectBentoDashboard(activity);
        });
        accBtns.addView(guestBtn);

        LinearLayout msBtn = makeActionPillWithIcon(
                activity, ICON_SHIELD, "Microsoft Login", "#6366F1", "#FFFFFF"
        );
        msBtn.setOnClickListener(v -> {
            prefs.edit().putBoolean("is_guest", false).apply();
            showGamertagOrMsLoginDialog(activity, bentoRoot);
        });
        accBtns.addView(msBtn);
        accCard.addView(accBtns);

        // WIDGET 3: MODRINTH MINECRAFT MOD HUB CARD
        LinearLayout modCard = makeBentoCard(activity, cardBg, cardStroke, 0);
        LinearLayout.LayoutParams mCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.32f
        );
        modCard.setLayoutParams(mCardLp);
        rightCol.addView(modCard);

        TextView modHeader = new TextView(activity);
        modHeader.setText("MINECRAFT MODRINTH HUB");
        modHeader.setTextColor(Color.parseColor("#A78BFA"));
        modHeader.setTextSize(10.5f);
        modHeader.setTypeface(Typeface.DEFAULT_BOLD);
        modHeader.setPadding(0, 0, 0, dp(activity, 6));
        modCard.addView(modHeader);

        LinearLayout openModsBtn = makeActionPillWithIcon(
                activity, ICON_MODS, "Install .jar Mods (Modrinth)", "#6366F1", "#FFFFFF"
        );
        openModsBtn.setOnClickListener(v -> showModrinthDialog(activity));
        modCard.addView(openModsBtn);

        rootVg.addView(bentoRoot, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    // =========================================================================
    // UI COMPONENT HELPERS (VECTOR LOGOS + BENTO CARDS)
    // =========================================================================
    private static LinearLayout makeSidebarRow(Context ctx, int iconType, String label, boolean selected, boolean light, String textPrimary, String textSecondary) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padH = dp(ctx, 10);
        int padV = dp(ctx, 8);
        row.setPadding(padH, padV, padH, padV);

        if (selected) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Color.parseColor(light ? "#E2E8F0" : "#1E222D"));
            gd.setCornerRadius(dp(ctx, 14));
            row.setBackground(gd);
        }

        NovaVectorIcon icon = new NovaVectorIcon(ctx, iconType, selected ? "#6366F1" : textSecondary);
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(ctx, 18), dp(ctx, 18));
        iLp.rightMargin = dp(ctx, 10);
        row.addView(icon, iLp);

        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextColor(Color.parseColor(selected ? textPrimary : textSecondary));
        tv.setTextSize(12.5f);
        tv.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        row.addView(tv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(ctx, 4);
        row.setLayoutParams(lp);
        return row;
    }

    private static LinearLayout makeActionPillWithIcon(Context ctx, int iconType, String text, String bgHex, String fgHex) {
        NovaVectorIcon icon = new NovaVectorIcon(ctx, iconType, fgHex);
        TextView tv = new TextView(ctx);
        return makeDynamicIconPill(ctx, icon, tv, text, bgHex, fgHex);
    }

    private static LinearLayout makeDynamicIconPill(Context ctx, NovaVectorIcon icon, TextView tv, String text, String bgHex, String fgHex) {
        LinearLayout pill = new LinearLayout(ctx);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(ctx, 12), dp(ctx, 7), dp(ctx, 12), dp(ctx, 7));

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgHex));
        gd.setCornerRadius(dp(ctx, 18));
        pill.setBackground(gd);

        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(dp(ctx, 15), dp(ctx, 15));
        iLp.rightMargin = dp(ctx, 6);
        pill.addView(icon, iLp);

        tv.setText(text);
        tv.setTextColor(Color.parseColor(fgHex));
        tv.setTextSize(11.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        pill.addView(tv);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.rightMargin = dp(ctx, 8);
        pill.setLayoutParams(lp);
        return pill;
    }

    private static TextView makeIconButtonPill(Context ctx, int iconType, String text, String bgHex, String fgHex) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(fgHex));
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgHex));
        gd.setCornerRadius(dp(ctx, 16));
        tv.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.rightMargin = dp(ctx, 8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static LinearLayout makeBentoCard(Context ctx, String bgHex, String borderHex, int bottomMarginPx) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        int p = dp(ctx, 14);
        card.setPadding(p, p, p, p);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(bgHex));
        gd.setCornerRadius(dp(ctx, 22));
        gd.setStroke(dp(ctx, 1), Color.parseColor(borderHex));
        card.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = bottomMarginPx;
        card.setLayoutParams(lp);
        return card;
    }

    private static void attachReturnToBentoPill(final Activity activity, final View bentoRoot) {
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        final ViewGroup vg = (ViewGroup) decor;
        if (vg.findViewWithTag("NOVA_RETURN_PILL") != null) return;
        LinearLayout pill = makeActionPillWithIcon(activity, ICON_GRID, "Return to Nova Bento UI", "#6366F1", "#FFFFFF");
        pill.setTag("NOVA_RETURN_PILL");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END
        );
        lp.topMargin = dp(activity, 24);
        lp.rightMargin = dp(activity, 16);
        pill.setOnClickListener(v -> {
            vg.removeView(pill);
            injectBentoDashboard(activity);
        });
        vg.addView(pill, lp);
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

    private static List<String> getInstalledVersionsList(Context ctx) {
        List<String> list = new ArrayList<>();
        list.add("1.0 (Preloaded Official Release)");
        list.add("rd-132211 (Preloaded First Edition)");
        try {
            File vDir = new File(getGameDir(ctx), "versions");
            File[] dirs = vDir.listFiles();
            if (dirs != null) {
                for (File d : dirs) {
                    if (d.isDirectory() && !d.getName().equals("1.0") && !d.getName().equals("rd-132211")) {
                        list.add(d.getName());
                    }
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    // =========================================================================
    // PHONK RADIO ENGINE
    // =========================================================================
    public static void togglePhonk(Context ctx, TextView statusLabel, NovaVectorIcon icon, TextView btnText) {
        if (isPhonkPlaying) {
            stopPhonk();
            if (btnText != null) btnText.setText("Play");
            if (statusLabel != null) statusLabel.setText("PAUSED  |  " + PHONK_TITLES[currentTrackIdx]);
        } else {
            playPhonkTrack(ctx, currentTrackIdx, statusLabel, icon, btnText);
        }
    }

    public static void nextPhonk(Context ctx, TextView statusLabel, NovaVectorIcon icon, TextView btnText) {
        currentTrackIdx = (currentTrackIdx + 1) % PHONK_FILES.length;
        playPhonkTrack(ctx, currentTrackIdx, statusLabel, icon, btnText);
    }

    public static void playPhonkTrack(Context ctx, int idx, TextView statusLabel, NovaVectorIcon icon, TextView btnText) {
        try {
            stopPhonk();
            currentTrackIdx = idx % PHONK_FILES.length;
            AssetFileDescriptor afd = ctx.getAssets().openFd("nova_phonk/" + PHONK_FILES[currentTrackIdx]);
            phonkPlayer = new MediaPlayer();
            phonkPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            phonkPlayer.setLooping(true);
            phonkPlayer.prepare();
            phonkPlayer.start();
            isPhonkPlaying = true;
            if (btnText != null) btnText.setText("Pause");
            if (statusLabel != null) statusLabel.setText("ON AIR  |  " + PHONK_TITLES[currentTrackIdx]);
        } catch (Throwable e) {
            if (statusLabel != null) statusLabel.setText("Phonk Ready");
        }
    }

    public static void stopPhonk() {
        try {
            if (phonkPlayer != null) {
                if (phonkPlayer.isPlaying()) phonkPlayer.stop();
                phonkPlayer.release();
                phonkPlayer = null;
            }
        } catch (Throwable ignored) {}
        isPhonkPlaying = false;
    }

    // =========================================================================
    // MICROSOFT / BEDROCK AUTH & GUEST ACCOUNT ENGINE
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
            setFieldIfPresent(acc, "selectedVersion", "1.0");
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
                        if (appContext != null) {
                            Toast.makeText(appContext, "Signed in with Microsoft/Bedrock: " + tag, Toast.LENGTH_LONG).show();
                        }
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
            obj.put("selectedVersion", "1.0");
            obj.put("isMicrosoft", isMs);
            File out = new File(accDir, username + ".json");
            FileWriter fw = new FileWriter(out, false);
            fw.write(obj.toString(2));
            fw.close();
        } catch (Throwable ignored) {}
    }

    private static void showGamertagOrMsLoginDialog(final Activity activity, final View bentoRoot) {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(activity, 20);
        box.setPadding(p, p, p, p);
        box.setBackgroundColor(Color.parseColor("#13151A"));

        TextView title = new TextView(activity);
        title.setText("Microsoft / Xbox Bedrock Account");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        box.addView(title);

        final EditText input = new EditText(activity);
        input.setHint("Enter Xbox / Bedrock Gamertag");
        input.setHintTextColor(Color.parseColor("#64748B"));
        input.setTextColor(Color.WHITE);
        String cur = getResolvedGamertag();
        if (!cur.equals("Guest")) input.setText(cur);
        box.addView(input);

        new AlertDialog.Builder(activity)
                .setView(box)
                .setPositiveButton("Save Xbox Account", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        lastXboxGamertag = name;
                        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .edit().putString("xbox_gamertag", name).putBoolean("is_guest", false).apply();
                        writeAccountJson(activity, name, true);
                        injectBentoDashboard(activity);
                    }
                })
                .setNeutralButton("Microsoft Web Login", (d, w) -> {
                    bentoRoot.setVisibility(View.GONE);
                    attachReturnToBentoPill(activity, bentoRoot);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // =========================================================================
    // MODRINTH MINECRAFT MOD BROWSER & .JAR INSTALLER
    // =========================================================================
    private static void showModrinthDialog(final Activity activity) {
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(Color.parseColor("#0B0C0E"));
        final LinearLayout main = new LinearLayout(activity);
        main.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 18);
        main.setPadding(pad, pad, pad, pad);
        scroll.addView(main);

        TextView title = new TextView(activity);
        title.setText("NovaLauncher  |  Modrinth Mod Hub");
        title.setTextColor(Color.parseColor("#F8FAFC"));
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        main.addView(title);

        LinearLayout filterRow = new LinearLayout(activity);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        final Spinner verSpinner = new Spinner(activity);
        final String[] mcVersions = new String[]{"1.21.4", "1.21.1", "1.20.4", "1.20.1", "1.19.4", "1.19.2", "1.18.2", "1.16.5", "1.12.2"};
        verSpinner.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, mcVersions));
        verSpinner.setSelection(3);

        final Spinner loaderSpinner = new Spinner(activity);
        final String[] loaders = new String[]{"fabric", "forge", "neoforge", "quilt"};
        loaderSpinner.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, loaders));

        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        flp.rightMargin = dp(activity, 8);
        filterRow.addView(verSpinner, flp);
        filterRow.addView(loaderSpinner, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        main.addView(filterRow);

        LinearLayout searchRow = new LinearLayout(activity);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setPadding(0, dp(activity, 10), 0, dp(activity, 10));
        final EditText searchInput = new EditText(activity);
        searchInput.setHint("Search mods (Sodium, JEI, Iris, Waystones)...");
        searchInput.setHintTextColor(Color.parseColor("#64748B"));
        searchInput.setTextColor(Color.parseColor("#F8FAFC"));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp.rightMargin = dp(activity, 8);
        searchRow.addView(searchInput, slp);

        Button searchBtn = new Button(activity);
        searchBtn.setText("Search");
        searchRow.addView(searchBtn);
        main.addView(searchRow);

        final LinearLayout resultsBox = new LinearLayout(activity);
        resultsBox.setOrientation(LinearLayout.VERTICAL);
        main.addView(resultsBox);

        TextView instHeader = new TextView(activity);
        instHeader.setText("Installed Mods (.minecraft/mods)");
        instHeader.setTextColor(Color.parseColor("#F8FAFC"));
        instHeader.setTypeface(Typeface.DEFAULT_BOLD);
        instHeader.setPadding(0, dp(activity, 16), 0, dp(activity, 8));
        main.addView(instHeader);

        final LinearLayout installedBox = new LinearLayout(activity);
        installedBox.setOrientation(LinearLayout.VERTICAL);
        main.addView(installedBox);
        refreshInstalledModsList(activity, installedBox);

        searchBtn.setOnClickListener(v -> searchModrinth(
                activity,
                searchInput.getText().toString().trim(),
                (String) verSpinner.getSelectedItem(),
                (String) loaderSpinner.getSelectedItem(),
                resultsBox,
                installedBox
        ));
        searchModrinth(activity, "", "1.20.1", "fabric", resultsBox, installedBox);
        new AlertDialog.Builder(activity).setView(scroll).setPositiveButton("Close", null).show();
    }

    private static void searchModrinth(final Activity activity, final String query, final String mcVer, final String loader, final LinearLayout resultsBox, final LinearLayout installedBox) {
        resultsBox.removeAllViews();
        TextView loading = new TextView(activity);
        loading.setText("Loading Minecraft mods from Modrinth...");
        loading.setTextColor(Color.parseColor("#94A3B8"));
        resultsBox.addView(loading);
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    String facets = "[ [\"project_type:mod\"], [\"versions:" + mcVer + "\"], [\"categories:" + loader + "\"] ]";
                    String urlStr = "https://api.modrinth.com/v2/search?limit=12&query="
                            + URLEncoder.encode(query, "UTF-8")
                            + "&facets=" + URLEncoder.encode(facets, "UTF-8");
                    conn = (HttpURLConnection) new URL(urlStr).openConnection();
                    conn.setRequestProperty("User-Agent", "NovaLauncher-Android/1.0");
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = br.readLine()) != null) sb.append(l);
                    br.close();
                    JSONObject root = new JSONObject(sb.toString());
                    final JSONArray hits = root.optJSONArray("hits");
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            resultsBox.removeAllViews();
                            if (hits == null || hits.length() == 0) {
                                TextView none = new TextView(activity);
                                none.setText("No mods found for " + loader + " " + mcVer);
                                none.setTextColor(Color.parseColor("#F87171"));
                                resultsBox.addView(none);
                                return;
                            }
                            for (int i = 0; i < hits.length(); i++) {
                                JSONObject hit = hits.optJSONObject(i);
                                if (hit == null) continue;
                                final String projId = hit.optString("project_id");
                                final String modTitle = hit.optString("title", "Mod");
                                final String desc = hit.optString("description", "");
                                LinearLayout card = makeBentoCard(activity, "#14161B", "#262934", dp(activity, 8));
                                TextView t = new TextView(activity);
                                t.setText(modTitle + "  [" + loader + " " + mcVer + "]");
                                t.setTextColor(Color.parseColor("#F8FAFC"));
                                t.setTypeface(Typeface.DEFAULT_BOLD);
                                card.addView(t);
                                TextView d = new TextView(activity);
                                d.setText(desc);
                                d.setTextColor(Color.parseColor("#94A3B8"));
                                d.setTextSize(12f);
                                d.setPadding(0, dp(activity, 4), 0, dp(activity, 8));
                                card.addView(d);
                                final Button dlBtn = new Button(activity);
                                dlBtn.setText("Install Mod (.jar)");
                                dlBtn.setOnClickListener(v -> {
                                    dlBtn.setEnabled(false);
                                    dlBtn.setText("Downloading...");
                                    downloadModFromModrinth(activity, projId, mcVer, loader, dlBtn, installedBox);
                                });
                                card.addView(dlBtn);
                                resultsBox.addView(card);
                            }
                        }
                    });
                } catch (final Throwable e) {
                    activity.runOnUiThread(() -> {
                        resultsBox.removeAllViews();
                        TextView err = new TextView(activity);
                        err.setText("Error: " + e.getMessage());
                        err.setTextColor(Color.parseColor("#F87171"));
                        resultsBox.addView(err);
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    private static void downloadModFromModrinth(final Activity activity, final String projectId, final String mcVer, final String loader, final Button btn, final LinearLayout installedBox) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    String urlStr = "https://api.modrinth.com/v2/project/" + projectId
                            + "/version?loaders=" + URLEncoder.encode("[\"" + loader + "\"]", "UTF-8")
                            + "&game_versions=" + URLEncoder.encode("[\"" + mcVer + "\"]", "UTF-8");
                    conn = (HttpURLConnection) new URL(urlStr).openConnection();
                    conn.setRequestProperty("User-Agent", "NovaLauncher-Android/1.0");
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String l;
                    while ((l = br.readLine()) != null) sb.append(l);
                    br.close();
                    JSONArray arr = new JSONArray(sb.toString());
                    if (arr.length() == 0) throw new RuntimeException("No file for " + loader + " " + mcVer);
                    JSONObject fileObj = arr.getJSONObject(0).getJSONArray("files").getJSONObject(0);
                    String dlUrl = fileObj.getString("url");
                    final String fileName = fileObj.getString("filename");
                    File modsDir = new File(getGameDir(activity), "mods");
                    if (!modsDir.exists()) modsDir.mkdirs();
                    File outFile = new File(modsDir, fileName);
                    HttpURLConnection dlConn = (HttpURLConnection) new URL(dlUrl).openConnection();
                    dlConn.setRequestProperty("User-Agent", "NovaLauncher-Android/1.0");
                    InputStream is = dlConn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte[] buf = new byte[8192];
                    int r;
                    while ((r = is.read(buf)) != -1) fos.write(buf, 0, r);
                    fos.close();
                    is.close();
                    dlConn.disconnect();
                    activity.runOnUiThread(() -> {
                        btn.setText("Installed: " + fileName);
                        Toast.makeText(activity, "Installed " + fileName, Toast.LENGTH_LONG).show();
                        refreshInstalledModsList(activity, installedBox);
                    });
                } catch (final Throwable e) {
                    activity.runOnUiThread(() -> {
                        btn.setEnabled(true);
                        btn.setText("Retry Install");
                        Toast.makeText(activity, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    private static void refreshInstalledModsList(final Activity activity, final LinearLayout installedBox) {
        installedBox.removeAllViews();
        File modsDir = new File(getGameDir(activity), "mods");
        if (!modsDir.exists()) modsDir.mkdirs();
        File[] files = modsDir.listFiles();
        if (files == null || files.length == 0) {
            TextView empty = new TextView(activity);
            empty.setText("No mods installed yet.");
            empty.setTextColor(Color.parseColor("#64748B"));
            installedBox.addView(empty);
            return;
        }
        for (final File f : files) {
            if (!f.getName().endsWith(".jar")) continue;
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView nameTv = new TextView(activity);
            nameTv.setText(f.getName());
            nameTv.setTextColor(Color.parseColor("#E2E8F0"));
            nameTv.setTextSize(12f);
            row.addView(nameTv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            Button delBtn = new Button(activity);
            delBtn.setText("Delete");
            delBtn.setOnClickListener(v -> {
                f.delete();
                refreshInstalledModsList(activity, installedBox);
            });
            row.addView(delBtn);
            installedBox.addView(row);
        }
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
                activity.runOnUiThread(() -> Toast.makeText(activity, "Preloaded Minecraft (1.0 & rd-132211) ready!", Toast.LENGTH_SHORT).show());
            }
        } catch (Throwable ignored) {}
    }

    private static int dp(Context ctx, int v) {
        return (int) (v * ctx.getResources().getDisplayMetrics().density);
    }
}
