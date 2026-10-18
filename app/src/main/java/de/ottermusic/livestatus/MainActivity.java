package de.ottermusic.livestatus;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(12, 15, 25);
    private final int PANEL = Color.rgb(24, 29, 44);
    private final int WHITE = Color.WHITE;
    private final int MUTED = Color.rgb(155, 165, 185);
    private final int ACCENT = Color.rgb(235, 55, 100);
    private final int BLUE = Color.rgb(38, 195, 244);
    private static final String STATUS_URL = "https://www.ottermusic.de/wp-admin/admin-ajax.php?action=olc_status";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<String> creators = new ArrayList<>();
    private final Map<String, String> statuses = new HashMap<>();
    private final Map<String, TextView> statusViews = new HashMap<>();
    private SharedPreferences preferences;
    private LinearLayout root, content;
    private EditText creatorInput;
    private TextView refreshInfo;
    private boolean visible = false;
    private boolean onHome = false;
    private boolean loading = false;
    private String lastUpdate = "Warte auf erste Server-Abfrage";
    private final Runnable refreshTask = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            fetchStatuses();
            handler.postDelayed(this, 5000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        preferences = getSharedPreferences("live_status", MODE_PRIVATE);
        loadCreators();
        showHome();
    }
    @Override protected void onResume() {
        super.onResume();
        visible = true;
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
    }
    @Override protected void onPause() {
        visible = false;
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(refreshTask);
        executor.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD); return t;
    }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private void marginTop(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); parent.addView(view, p);
    }
    private void setup(boolean homeScreen) {
        onHome = homeScreen;
        statusViews.clear();
        refreshInfo = null;
        root = vertical(); root.setBackgroundColor(BG); root.setFitsSystemWindows(false);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top, bottom;
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top; bottom = bars.bottom;
            } else { top = insets.getSystemWindowInsetTop(); bottom = insets.getSystemWindowInsetBottom(); }
            view.setPadding(0, top, 0, bottom); return insets;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        content = vertical(); content.setPadding(dp(20), dp(18), dp(20), dp(28)); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout nav = new LinearLayout(this); nav.setPadding(dp(15), dp(10), dp(15), dp(10)); nav.setBackgroundColor(PANEL);
        Button home = navButton("⌂  Creator", homeScreen);
        Button settings = navButton("⚙  Einstellungen", !homeScreen);
        home.setOnClickListener(v -> showHome()); settings.setOnClickListener(v -> showSettings());
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, dp(52), 1); left.rightMargin = dp(7); nav.addView(home, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(52), 1); right.leftMargin = dp(7); nav.addView(settings, right);
        root.addView(nav); setContentView(root); root.requestApplyInsets();
        if (homeScreen) {
            ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.live_status_header);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER); logo.setAdjustViewBounds(true);
            logo.setContentDescription("TikTok LIVE STATUS – OtterMusic");
            content.addView(logo, new LinearLayout.LayoutParams(-1, -2));
        } else content.addView(text("Einstellungen", 26, WHITE, true));
    }
    private Button navButton(String label, boolean selected) {
        Button b = new Button(this); b.setText(label); b.setTextSize(13); b.setAllCaps(false); b.setTextColor(WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected ? ACCENT : Color.rgb(43, 51, 70))); return b;
    }
    private Button actionButton(String label) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextColor(WHITE); b.setTextSize(14);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT)); return b;
    }
    private void showHome() {
        setup(true);
        marginTop(content, text("Behalte deine Lieblings-Creator im Blick.", 14, MUTED, false), 18);
        refreshInfo = text(lastUpdate + " · Automatisch alle 5 Sekunden", 12, BLUE, false);
        marginTop(content, refreshInfo, 12);
        LinearLayout addPanel = vertical(); addPanel.setPadding(dp(16), dp(18), dp(16), dp(18)); addPanel.setBackground(background(PANEL, 18));
        addPanel.addView(text("Creator hinzufügen", 18, WHITE, true));
        creatorInput = new EditText(this); creatorInput.setSingleLine(true);
        creatorInput.setHint("@TikTok-Name oder Profil-Link"); creatorInput.setHintTextColor(MUTED);
        creatorInput.setTextColor(WHITE); creatorInput.setTextSize(14);
        creatorInput.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        marginTop(addPanel, creatorInput, 12);
        Button add = actionButton("+ Creator hinzufügen"); marginTop(addPanel, add, 12); add.setOnClickListener(v -> addCreator());
        marginTop(content, addPanel, 24);
        marginTop(content, text("Meine Creator (" + creators.size() + ")", 20, WHITE, true), 26);
        if (creators.isEmpty()) marginTop(content, text("Du hast noch keine Creator gespeichert.\nFüge oben deinen ersten TikTok-Creator hinzu.", 14, MUTED, false), 18);
        else for (String creator : new ArrayList<>(creators)) addCreatorCard(creator);
    }
    private void addCreatorCard(String creator) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL); card.setPadding(dp(15), dp(16), dp(10), dp(16)); card.setBackground(background(PANEL, 16));
        LinearLayout details = vertical(); details.addView(text("@" + creator, 17, WHITE, true));
        TextView status = text("", 12, MUTED, false); updateStatusLabel(status, statuses.get(creator.toLowerCase(Locale.ROOT)));
        marginTop(details, status, 6); statusViews.put(creator.toLowerCase(Locale.ROOT), status);
        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        Button remove = new Button(this); remove.setText("✕"); remove.setContentDescription("Creator entfernen: " + creator);
        remove.setTextColor(WHITE); remove.setTextSize(17);
        remove.setBackgroundTintList(android.content.res.ColorStateList.valueOf(PANEL));
        remove.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Creator entfernen?")
            .setMessage("@" + creator + " aus deiner Liste entfernen?").setNegativeButton("Abbrechen", null)
            .setPositiveButton("Entfernen", (dialog, which) -> {
                creators.remove(creator); saveCreators(); showHome();
            }).show());
        card.addView(remove, new LinearLayout.LayoutParams(dp(52), dp(52)));
        details.setOnClickListener(v -> openTikTok(creator)); marginTop(content, card, 12);
    }
    private void updateStatusLabel(TextView label, String state) {
        if ("live".equals(state)) { label.setText("● LIVE"); label.setTextColor(Color.rgb(55, 220, 140)); }
        else if ("offline".equals(state)) { label.setText("● OFFLINE"); label.setTextColor(MUTED); }
        else if ("missing".equals(state)) { label.setText("● Noch nicht auf dem Server eingetragen"); label.setTextColor(MUTED); }
        else if ("error".equals(state)) { label.setText("● Server nicht erreichbar"); label.setTextColor(ACCENT); }
        else { label.setText("● Status unbekannt – nächste Prüfung folgt"); label.setTextColor(BLUE); }
    }
    private void fetchStatuses() {
        if (loading) return;
        loading = true;
        executor.execute(() -> {
            Map<String,String> latest = new HashMap<>();
            String result = "Server nicht erreichbar";
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(STATUS_URL).openConnection();
                connection.setConnectTimeout(6000); connection.setReadTimeout(6000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setUseCaches(false);
                if (connection.getResponseCode() != 200) throw new Exception("HTTP " + connection.getResponseCode());
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream stream = connection.getInputStream()) {
                    byte[] buf = new byte[4096]; int n;
                    while ((n = stream.read(buf)) != -1) {
                        if (bytes.size() > 200000) throw new Exception("Antwort zu groß");
                        bytes.write(buf, 0, n);
                    }
                }
                JSONObject response = new JSONObject(bytes.toString("UTF-8"));
                if (!response.optBoolean("success")) throw new Exception("Ungültige Server-Antwort");
                JSONObject data = response.getJSONObject("data");
                // These six IDs are the creators returned by the existing OtterMusic endpoint.
                // The endpoint does not provide handles; mappings must not be guessed for other IDs.
                String[][] known = {
                    {"1498", "_dj_otter_"}, {"1502", "kimbossible3"},
                    {"1505", "neag_offiziell_"}, {"1510", "darki3201"},
                    {"1513", "sebastianbackinsehhaus"}, {"1517", ".kendry"}
                };
                for (String[] entry : known) {
                    JSONObject item = data.optJSONObject(entry[0]);
                    if (item == null) continue;
                    String state = item.optString("status", "unknown").toLowerCase(Locale.ROOT);
                    if (!"live".equals(state) && !"offline".equals(state)) state = "unknown";
                    latest.put(entry[1].toLowerCase(Locale.ROOT), state);
                }
                result = "Server-Abfrage erfolgreich";
            } catch (Exception ignored) {
                result = "Server nicht erreichbar";
            } finally { if (connection != null) connection.disconnect(); }
            final String info = result;
            handler.post(() -> {
                loading = false;
                if (!visible || isFinishing() || isDestroyed()) return;
                boolean ok = "Server-Abfrage erfolgreich".equals(info);
                for (String creator : creators) {
                    String handle = creator.toLowerCase(Locale.ROOT);
                    statuses.put(handle, ok ? latest.getOrDefault(handle, "missing") : "error");
                    TextView label = statusViews.get(handle);
                    if (label != null) updateStatusLabel(label, statuses.get(handle));
                }
                lastUpdate = info;
                if (refreshInfo != null && onHome) refreshInfo.setText(info + " · Automatisch alle 5 Sekunden");
            });
        });
    }
    private void addCreator() {
        String value = creatorInput.getText().toString().trim();
        value = value.replaceFirst("(?i)^https?://(www\\.)?tiktok\\.com/@", "");
        if (value.startsWith("@")) value = value.substring(1);
        int slash = value.indexOf("/"); if (slash >= 0) value = value.substring(0, slash);
        int question = value.indexOf("?"); if (question >= 0) value = value.substring(0, question);
        if (!value.matches("[A-Za-z0-9._]{2,24}")) {
            Toast.makeText(this, "Bitte einen gültigen TikTok-Namen eingeben.", Toast.LENGTH_LONG).show(); return;
        }
        for (String saved : creators) if (saved.equalsIgnoreCase(value)) {
            Toast.makeText(this, "Dieser Creator ist bereits gespeichert.", Toast.LENGTH_SHORT).show(); return;
        }
        creators.add(value); saveCreators(); showHome();
        Toast.makeText(this, "Creator gespeichert!", Toast.LENGTH_SHORT).show();
    }
    private void openTikTok(String creator) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@" + creator))); }
        catch (Exception e) { Toast.makeText(this, "TikTok-Profil konnte nicht geöffnet werden.", Toast.LENGTH_SHORT).show(); }
    }
    private void showSettings() {
        setup(false);
        marginTop(content, text("LIVE-Status", 19, WHITE, true), 30);
        marginTop(content, text("Die App fragt den OtterMusic-Server alle 5 Sekunden ab, solange sie geöffnet ist. Der Server bestimmt selbst, wie oft TikTok geprüft wird.\n\nCreator, die noch nicht auf dem Server eingetragen sind, können derzeit nicht zugeordnet werden.", 14, MUTED, false), 12);
        marginTop(content, text("Benachrichtigungen", 19, WHITE, true), 32);
        marginTop(content, text("Benachrichtigungen beim LIVE-Start werden im nächsten Schritt ergänzt.", 14, MUTED, false), 12);
    }
    private void loadCreators() {
        creators.clear(); String json = preferences.getString("creators", "[]");
        try { JSONArray array = new JSONArray(json); for (int i=0;i<array.length();i++) creators.add(array.getString(i)); }
        catch (Exception ignored) { }
    }
    private void saveCreators() {
        JSONArray array = new JSONArray(); for (String creator : creators) array.put(creator);
        preferences.edit().putString("creators", array.toString()).apply();
    }
}
