
package de.ottermusic.livestatus;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private final int BG = Color.rgb(12, 15, 25);
    private final int PANEL = Color.rgb(24, 29, 44);
    private final int WHITE = Color.WHITE;
    private final int MUTED = Color.rgb(155, 165, 185);
    private final int ACCENT = Color.rgb(235, 55, 100);

    private LinearLayout root;
    private LinearLayout content;
    private EditText creatorInput;

    private final ArrayList<String> creators = new ArrayList<>();
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        preferences = getSharedPreferences("live_status", MODE_PRIVATE);
        loadCreators();
        showHome();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable background(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private void marginTop(LinearLayout parent, View view, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                -1, -2
        );
        params.topMargin = dp(top);
        parent.addView(view, params);
    }

    private void setup(String title) {
        root = vertical();
        root.setBackgroundColor(BG);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        content = vertical();
        content.setPadding(dp(20), dp(30), dp(20), dp(28));

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(15), dp(12), dp(15), dp(16));
        nav.setBackgroundColor(PANEL);

        Button home = navButton("⌂  Creator");
        Button settings = navButton("⚙  Einstellungen");

        home.setOnClickListener(v -> showHome());
        settings.setOnClickListener(v -> showSettings());

        nav.addView(home, new LinearLayout.LayoutParams(0, dp(46), 1));
        nav.addView(settings, new LinearLayout.LayoutParams(0, dp(46), 1));

        root.addView(nav);
        setContentView(root);

        TextView heading = text(title, 26, WHITE, true);
        content.addView(heading);

        TextView branding = text("powered by OtterMusic", 13, MUTED, false);
        marginTop(content, branding, 5);
    }

    private Button navButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setTextColor(WHITE);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(PANEL)
        );
        return button;
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(WHITE);
        button.setTextSize(14);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(ACCENT)
        );
        return button;
    }

    private void showHome() {
        setup("TikTok-Live Status");

        TextView subtitle = text(
                "Behalte deine Lieblings-Creator im Blick.",
                14, MUTED, false
        );
        marginTop(content, subtitle, 22);

        LinearLayout addPanel = vertical();
        addPanel.setPadding(dp(16), dp(18), dp(16), dp(18));
        addPanel.setBackground(background(PANEL, 18));

        TextView addTitle = text("Creator hinzufügen", 18, WHITE, true);
        addPanel.addView(addTitle);

        creatorInput = new EditText(this);
        creatorInput.setSingleLine(true);
        creatorInput.setHint("@TikTok-Name oder Profil-Link");
        creatorInput.setHintTextColor(MUTED);
        creatorInput.setTextColor(WHITE);
        creatorInput.setTextSize(14);
        creatorInput.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(ACCENT)
        );

        marginTop(addPanel, creatorInput, 12);

        Button add = actionButton("+ Creator hinzufügen");
        marginTop(addPanel, add, 12);

        add.setOnClickListener(v -> addCreator());

        marginTop(content, addPanel, 24);

        TextView listTitle = text(
                "Meine Creator (" + creators.size() + ")",
                20, WHITE, true
        );
        marginTop(content, listTitle, 26);

        if (creators.isEmpty()) {
            TextView empty = text(
                    "Du hast noch keine Creator gespeichert.\n"
                    + "Füge oben deinen ersten TikTok-Creator hinzu.",
                    14, MUTED, false
            );
            marginTop(content, empty, 18);
        } else {
            for (String creator : new ArrayList<>(creators)) {
                addCreatorCard(creator);
            }
        }
    }

    private void addCreatorCard(String creator) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(15), dp(16), dp(10), dp(16));
        card.setBackground(background(PANEL, 16));

        LinearLayout details = vertical();

        TextView name = text("@" + creator, 17, WHITE, true);
        details.addView(name);

        TextView status = text(
                "● Noch nicht geprüft",
                12, MUTED, false
        );
        marginTop(details, status, 6);

        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

        Button remove = new Button(this);
        remove.setText("✕");
        remove.setTextColor(WHITE);
        remove.setTextSize(17);
        remove.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(PANEL)
        );

        remove.setOnClickListener(v -> {
            creators.remove(creator);
            saveCreators();
            showHome();
        });

        card.addView(remove, new LinearLayout.LayoutParams(dp(52), dp(52)));

        details.setOnClickListener(v -> openTikTok(creator));

        marginTop(content, card, 12);
    }

    private void addCreator() {
        String value = creatorInput.getText().toString().trim();

        value = value.replaceFirst(
                "(?i)^https?://(www\\.)?tiktok\\.com/@", ""
        );

        if (value.startsWith("@")) {
            value = value.substring(1);
        }

        int slash = value.indexOf("/");
        if (slash >= 0) {
            value = value.substring(0, slash);
        }

        int question = value.indexOf("?");
        if (question >= 0) {
            value = value.substring(0, question);
        }

        if (!value.matches("[A-Za-z0-9._]{2,24}")) {
            Toast.makeText(
                    this,
                    "Bitte einen gültigen TikTok-Namen eingeben.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        for (String saved : creators) {
            if (saved.equalsIgnoreCase(value)) {
                Toast.makeText(
                        this,
                        "Dieser Creator ist bereits gespeichert.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
        }

        creators.add(value);
        saveCreators();
        showHome();

        Toast.makeText(
                this,
                "Creator gespeichert!",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void openTikTok(String creator) {
        try {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.tiktok.com/@" + creator)
            );
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "TikTok-Profil konnte nicht geöffnet werden.",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void showSettings() {
        setup("Einstellungen");

        TextView title = text("Benachrichtigungen", 19, WHITE, true);
        marginTop(content, title, 30);

        TextView info = text(
                "Hier kannst du später Push-Benachrichtigungen "
                + "für deine eigenen Creator aktivieren.\n\n"
                + "Du wirst benachrichtigt, wenn einer deiner "
                + "gespeicherten Creator live geht.",
                14, MUTED, false
        );
        marginTop(content, info, 12);

        TextView syncTitle = text(
                "OtterMusic-Synchronisierung",
                19, WHITE, true
        );
        marginTop(content, syncTitle, 32);

        TextView syncInfo = text(
                "Optional kannst du deine persönlichen Creator "
                + "später mit deinem OtterMusic-Konto synchronisieren.\n\n"
                + "Die App funktioniert auch ohne Anmeldung.",
                14, MUTED, false
        );
        marginTop(content, syncInfo, 12);
    }

    private void loadCreators() {
        creators.clear();

        String json = preferences.getString("creators", "[]");

        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                creators.add(array.getString(i));
            }
        } catch (Exception ignored) {
        }
    }

    private void saveCreators() {
        JSONArray array = new JSONArray();

        for (String creator : creators) {
            array.put(creator);
        }

        preferences.edit()
                .putString("creators", array.toString())
                .apply();
    }
}
