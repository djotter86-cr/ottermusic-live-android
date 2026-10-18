package de.ottermusic.livestatus;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import java.net.URLEncoder;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
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
    private static final String UPDATE_URL = "https://ottermusic.de/wp-content/App/tt-live-app/update.json";
    private static final long UPDATE_INTERVAL_MS = 5L * 60 * 1000;
    private static final String LIVE_CHANNEL = "ottermusic_live_starts";
    private final java.util.HashSet<String> observedCreators = new java.util.HashSet<>();
    private long downloadId = -1;
    private boolean updateChecking = false;
    private LinearLayout creatorCards;
    private final BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
            long completed = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (completed != downloadId) return;
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Query q = new DownloadManager.Query().setFilterById(completed);
            try (android.database.Cursor c = dm.query(q)) {
                if (c != null && c.moveToFirst() && c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                    new AlertDialog.Builder(MainActivity.this).setTitle("Download abgeschlossen")
                        .setMessage("Soll die neue Version jetzt installiert werden?")
                        .setNegativeButton("Später", null).setPositiveButton("Installieren", (d, w) -> installDownloadedApk()).show();
                } else Toast.makeText(MainActivity.this, "Download fehlgeschlagen.", Toast.LENGTH_LONG).show();
            } catch (Exception e) { Toast.makeText(MainActivity.this, "Download konnte nicht geprüft werden.", Toast.LENGTH_LONG).show(); }
        }
    };
    private static final String STATUS_URL = "https://www.ottermusic.de/wp-json/ottermusic-live/v1/creators";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ArrayList<String> creators = new ArrayList<>();
    private final Map<String, String> statuses = new HashMap<>();
    private final Map<String, TextView> statusViews = new HashMap<>();
    private final Map<String, ImageView> avatarViews = new HashMap<>();
    private final Map<String, Bitmap> avatarCache = new HashMap<>();
    private final Map<String, String> avatarUrls = new HashMap<>();
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

    private final Runnable updateTask = new Runnable() {
        @Override public void run() {
            if (!visible) return;
            checkForUpdate(false);
            handler.postDelayed(this, UPDATE_INTERVAL_MS);
        }
    };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        preferences = getSharedPreferences("live_status", MODE_PRIVATE);
        loadCreators();
        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 120);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(downloadReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        showHome();
        PushRegistration.sync(this);
    }
    @Override protected void onResume() {
        super.onResume();
        visible = true;
        handler.removeCallbacks(refreshTask);
        handler.post(refreshTask);
        checkForUpdate(false);
        handler.removeCallbacks(updateTask);
        handler.postDelayed(updateTask, UPDATE_INTERVAL_MS);
    }
    @Override protected void onPause() {
        visible = false;
        handler.removeCallbacks(refreshTask);
        handler.removeCallbacks(updateTask);
        super.onPause();
    }
    @Override protected void onDestroy() {
        handler.removeCallbacks(refreshTask);
        handler.removeCallbacks(updateTask);
        try { unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
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
        avatarViews.clear();
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
        creatorCards = vertical();
        content.addView(creatorCards);
        renderSortedCreators();
    }
    private int statusRank(String creator) {
        String state = statuses.get(creator.toLowerCase(Locale.ROOT));
        if ("live".equals(state)) return 0;
        if ("offline".equals(state)) return 1;
        return 2;
    }
    private void renderSortedCreators() {
        if (!onHome || creatorCards == null) return;
        creatorCards.removeAllViews();
        statusViews.clear();
        avatarViews.clear();
        if (creators.isEmpty()) {
            marginTop(creatorCards, text("Du hast noch keine Creator gespeichert.\nFüge oben deinen ersten TikTok-Creator hinzu.", 14, MUTED, false), 18);
            return;
        }
        ArrayList<String> ordered = new ArrayList<>(creators);
        // Java's stable sort preserves the original order within each status group.
        Collections.sort(ordered, (a, b) -> Integer.compare(statusRank(a), statusRank(b)));
        for (String creator : ordered) addCreatorCard(creator);
    }
    private void addCreatorCard(String creator) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(13), dp(7), dp(13));
        card.setBackground(background(PANEL, 16));
        String handle = creator.toLowerCase(Locale.ROOT);
        ImageView avatar = new ImageView(this);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(50), dp(50));
        avatarParams.rightMargin = dp(11);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.setBackground(background(Color.rgb(43, 51, 70), 25));
        avatar.setClipToOutline(true);
        avatarViews.put(handle, avatar);
        if (avatarCache.containsKey(handle)) avatar.setImageBitmap(avatarCache.get(handle));
        card.addView(avatar, avatarParams);

        LinearLayout details = vertical();
        TextView creatorName = text(creator, 16, WHITE, true);
        creatorName.setSingleLine(true);
        creatorName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(creatorName);
        TextView creatorHandle = text("@" + creator, 12, MUTED, false);
        creatorHandle.setSingleLine(true);
        creatorHandle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        marginTop(details, creatorHandle, 4);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, -2, 1);
        detailParams.rightMargin = dp(8);
        card.addView(details, detailParams);
        details.setOnClickListener(v -> openTikTok(creator));

        TextView status = text("", 11, WHITE, true);
        status.setGravity(Gravity.CENTER);
        updateStatusLabel(status, statuses.get(handle));
        statusViews.put(handle, status);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(80), dp(34));
        badgeParams.rightMargin = dp(4);
        card.addView(status, badgeParams);

        TextView remove = text("✕", 21, MUTED, false);
        remove.setGravity(Gravity.CENTER);
        remove.setContentDescription("Creator entfernen: " + creator);
        remove.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Creator entfernen?")
            .setMessage("@" + creator + " aus deiner Liste entfernen?")
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Entfernen", (dialog, which) -> {
                creators.remove(creator); saveCreators(); showHome();
            }).show());
        card.addView(remove, new LinearLayout.LayoutParams(dp(34), dp(48)));
        marginTop(creatorCards, card, 12);
    }
    private void updateStatusLabel(TextView label, String state) {
        int foreground;
        int fill;
        String title;
        if ("live".equals(state)) {
            title = "● Live"; foreground = Color.rgb(43, 231, 144); fill = Color.rgb(10, 55, 43);
        } else if ("offline".equals(state)) {
            title = "● Offline"; foreground = Color.rgb(255, 96, 120); fill = Color.rgb(60, 24, 35);
        } else {
            title = "● Unbek."; foreground = Color.rgb(188, 201, 222); fill = Color.rgb(39, 48, 64);
        }
        label.setText(title);
        label.setTextColor(foreground);
        GradientDrawable pill = background(fill, 24);
        pill.setStroke(dp(1), foreground);
        label.setBackground(pill);
        label.setContentDescription("Status " + title);
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(LIVE_CHANNEL, "Creator geht LIVE", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Benachrichtigungen, wenn ein gespeicherter Creator LIVE geht");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }
    private void notifyLive(String creator) {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@" + creator));
        PendingIntent pending = PendingIntent.getActivity(this, creator.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, LIVE_CHANNEL) : new Notification.Builder(this);
        builder.setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("@" + creator + " ist jetzt LIVE!")
            .setContentText("Tippe hier, um das TikTok-Profil zu öffnen.")
            .setContentIntent(pending).setAutoCancel(true);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(creator.hashCode(), builder.build());
    }
    private void fetchStatuses() {
        if (loading) return;
        loading = true;
        executor.execute(() -> {
            Map<String,String> latest = new HashMap<>();
            Map<String,String> images = new HashMap<>();
            Map<String,Bitmap> newAvatars = new HashMap<>();
            String result = "Server nicht erreichbar";
            HttpURLConnection connection = null;
            try {
                StringBuilder names = new StringBuilder();
                for (String creator : new ArrayList<>(creators)) {
                    if (names.length() > 0) names.append(",");
                    names.append(creator);
                }
                if (names.length() == 0) {
                    result = "Keine Creator gespeichert";
                    throw new Exception("Keine Creator");
                }
                connection = (HttpURLConnection) new URL(STATUS_URL + "?names=" + URLEncoder.encode(names.toString(), "UTF-8")).openConnection();
                connection.setConnectTimeout(3000); connection.setReadTimeout(3000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setUseCaches(false);
                connection.setRequestProperty("Cache-Control", "no-cache, no-store");
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
                // The new bridge returns data keyed by TikTok username, not WordPress IDs.
                java.util.Iterator<String> keys = data.keys();
                while (keys.hasNext()) {
                    String handle = keys.next().toLowerCase(Locale.ROOT);
                    JSONObject item = data.optJSONObject(handle);
                    if (item == null) continue;
                    String state = item.optString("status", "unknown").toLowerCase(Locale.ROOT);
                    if (!"live".equals(state) && !"offline".equals(state)) state = "unknown";
                    latest.put(handle, state);
                    String imageUrl = item.optString("image", "");
                    if (imageUrl.startsWith("https://")) images.put(handle, imageUrl);
                }
                for (Map.Entry<String,String> image : images.entrySet()) {
                    String handle = image.getKey();
                    String imageUrl = image.getValue();
                    if (imageUrl.equals(avatarUrls.get(handle)) && avatarCache.containsKey(handle)) continue;
                    HttpURLConnection imageConnection = null;
                    try {
                        imageConnection = (HttpURLConnection) new URL(imageUrl).openConnection();
                        imageConnection.setConnectTimeout(3000);
                        imageConnection.setReadTimeout(3000);
                        if (imageConnection.getResponseCode() == 200) {
                            try (InputStream imageStream = imageConnection.getInputStream()) {
                                Bitmap bitmap = BitmapFactory.decodeStream(imageStream);
                                if (bitmap != null) newAvatars.put(handle, bitmap);
                            }
                        }
                    } catch (Exception ignored) {
                    } finally {
                        if (imageConnection != null) imageConnection.disconnect();
                    }
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
                for (Map.Entry<String, Bitmap> image : newAvatars.entrySet()) {
                    String handle = image.getKey();
                    avatarCache.put(handle, image.getValue());
                    avatarUrls.put(handle, images.get(handle));
                }
                for (Map.Entry<String, ImageView> view : avatarViews.entrySet()) {
                    Bitmap avatar = avatarCache.get(view.getKey());
                    if (avatar != null) view.getValue().setImageBitmap(avatar);
                }
                boolean changedOrder = false;
                for (String creator : creators) {
                    String handle = creator.toLowerCase(Locale.ROOT);
                    int before = statusRank(creator);
                    if (ok) {
                        String previous = statuses.get(handle);
                        String current = latest.getOrDefault(handle, "unknown");
                        if (observedCreators.contains(handle) && "offline".equals(previous) && "live".equals(current)) notifyLive(creator);
                        if ("live".equals(current) || "offline".equals(current)) observedCreators.add(handle);
                        statuses.put(handle, current);
                    } // Bei Serverfehlern den letzten bekannten Status behalten.
                    if (before != statusRank(creator)) changedOrder = true;
                    TextView label = statusViews.get(handle);
                    if (label != null) updateStatusLabel(label, statuses.get(handle));
                }
                if (changedOrder) renderSortedCreators();
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
        marginTop(content, text("Die App fragt den OtterMusic-Server alle 5 Sekunden ab, solange sie geöffnet ist. Der Server bestimmt selbst, wie oft TikTok geprüft wird.\n\nAuch neue TikTok-Namen werden über den OtterMusic-Server geprüft. Die eigentliche TikTok-Prüfung erfolgt nach dem Server-Intervall.", 14, MUTED, false), 12);
        marginTop(content, text("App-Updates", 19, WHITE, true), 32);
        marginTop(content, text("Die App prüft beim Öffnen und während der Nutzung ungefähr alle 5 Minuten auf neue Versionen.", 14, MUTED, false), 12);
        Button updateButton = actionButton("Jetzt nach Updates suchen");
        marginTop(content, updateButton, 12);
        updateButton.setOnClickListener(v -> checkForUpdate(true));
        marginTop(content, text("Benachrichtigungen", 19, WHITE, true), 32);
        marginTop(content, text("Bei einem erkannten Wechsel von OFFLINE zu LIVE erscheint eine Benachrichtigung, solange die App geöffnet ist. Bei geschlossener App können Push-Nachrichten über den OtterMusic-Server eintreffen, sobald die Server-Anmeldung erfolgreich ist.", 14, MUTED, false), 12);
        marginTop(content, text("Impressum", 19, WHITE, true), 32);
        marginTop(content, text("Angaben gemäß § 5 DDG\n\nChristoph Richrath\nRichrath.eu / OtterMusic\nAm Ringofen 3\n50321 Brühl\nDeutschland\n\nKontakt\nTelefon: 02232 / 5007082", 14, MUTED, false), 12);
        TextView email = text("info@ottermusic.de", 14, BLUE, false);
        marginTop(content, email, 8);
        email.setOnClickListener(v -> {
            Intent mail = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:info@ottermusic.de"));
            try { startActivity(mail); } catch (Exception e) { Toast.makeText(this, "Keine E-Mail-App gefunden.", Toast.LENGTH_SHORT).show(); }
        });
        marginTop(content, text("Umsatzsteuer-ID gemäß § 27a UStG\nDE350214051\n\nApp-Design und Entwicklung\nOtterMusic", 14, MUTED, false), 14);
    }
    private void checkForUpdate(boolean manual) {
        if (updateChecking) return;
        long now = System.currentTimeMillis();
        if (!manual && now - preferences.getLong("last_update_check", 0) < UPDATE_INTERVAL_MS) return;
        updateChecking = true;
        executor.execute(() -> {
            String version = "";
            String apkUrl = "";
            int code = 0;
            String error = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(UPDATE_URL + "?t=" + System.currentTimeMillis()).openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(6000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Cache-Control", "no-cache, no-store");
                if (connection.getResponseCode() != 200) throw new Exception("HTTP " + connection.getResponseCode());
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                try (InputStream in = connection.getInputStream()) {
                    byte[] bytes = new byte[2048]; int n;
                    while ((n = in.read(bytes)) != -1) {
                        if (buffer.size() + n > 16000) throw new Exception("Update-Datei zu groß");
                        buffer.write(bytes, 0, n);
                    }
                }
                JSONObject json = new JSONObject(buffer.toString("UTF-8"));
                code = json.getInt("versionCode");
                version = json.optString("versionName", String.valueOf(code));
                apkUrl = json.getString("apkUrl");
                Uri uri = Uri.parse(apkUrl);
                if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                    !("ottermusic.de".equalsIgnoreCase(uri.getHost()) || "www.ottermusic.de".equalsIgnoreCase(uri.getHost())) ||
                    !uri.getPath().startsWith("/wp-content/App/tt-live-app/") || !uri.getPath().endsWith(".apk"))
                    throw new Exception("Ungültige APK-Adresse");
                preferences.edit().putLong("last_update_check", System.currentTimeMillis()).apply();
            } catch (Exception e) { error = e.getMessage(); }
            finally { if (connection != null) connection.disconnect(); }
            final String latestName = version;
            final String latestApk = apkUrl;
            final int latestCode = code;
            final String problem = error;
            handler.post(() -> {
                updateChecking = false;
                if (isFinishing() || isDestroyed()) return;
                if (problem != null) {
                    if (manual) new AlertDialog.Builder(this).setTitle("Update-Prüfung")
                        .setMessage("Die Update-Datei ist nicht erreichbar oder ungültig. Bitte prüfe update.json auf deiner Webseite.")
                        .setPositiveButton("OK", null).show();
                    return;
                }
                long installed;
                try {
                    android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
                    installed = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
                } catch (Exception e) { installed = 0; }
                if (latestCode > installed) {
                    new AlertDialog.Builder(this).setTitle("Update verfügbar 🦦")
                        .setMessage("Version " + latestName + " ist verfügbar. Soll die neue APK heruntergeladen werden?")
                        .setNegativeButton("Nein", null)
                        .setPositiveButton("Herunterladen", (d, w) -> downloadUpdate(latestApk)).show();
                } else if (manual) new AlertDialog.Builder(this).setTitle("Alles aktuell")
                    .setMessage("Du hast bereits die neueste Version installiert.")
                    .setPositiveButton("OK", null).show();
            });
        });
    }
    private void downloadUpdate(String apkUrl) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("OtterMusic LIVE – App-Update");
            request.setDescription("Neue Android-Version wird heruntergeladen");
            request.setMimeType("application/vnd.android.package-archive");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "OtterMusic-LIVE-Update.apk");
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            downloadId = dm.enqueue(request);
            Toast.makeText(this, "Update wird heruntergeladen …", Toast.LENGTH_LONG).show();
        } catch (Exception e) { Toast.makeText(this, "Download konnte nicht gestartet werden.", Toast.LENGTH_LONG).show(); }
    }
    private void installDownloadedApk() {
        try {
            if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(this).setTitle("Installation erlauben")
                    .setMessage("Android muss einmalig die Installation von Updates aus dieser App erlauben. Danach kannst du die Installation über die Download-Benachrichtigung erneut starten.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Einstellungen öffnen", (d, w) -> {
                        Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName()));
                        startActivity(settings);
                    }).show();
                return;
            }
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            Uri apk = dm.getUriForDownloadedFile(downloadId);
            if (apk == null) throw new Exception("APK fehlt");
            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apk, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(install);
        } catch (Exception e) { Toast.makeText(this, "Installation konnte nicht gestartet werden.", Toast.LENGTH_LONG).show(); }
    }
    private void loadCreators() {
        creators.clear(); String json = preferences.getString("creators", "[]");
        try { JSONArray array = new JSONArray(json); for (int i=0;i<array.length();i++) creators.add(array.getString(i)); }
        catch (Exception ignored) { }
    }
    private void saveCreators() {
        JSONArray array = new JSONArray(); for (String creator : creators) array.put(creator);
        preferences.edit().putString("creators", array.toString()).apply();
        PushRegistration.sync(this);
    }
}
