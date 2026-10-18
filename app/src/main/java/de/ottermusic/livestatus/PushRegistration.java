package de.ottermusic.livestatus;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.messaging.FirebaseMessaging;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;
import java.util.concurrent.Executors;

public final class PushRegistration {
    private static final String ENDPOINT = "https://www.ottermusic.de/wp-json/ottermusic-live/v1/push/register";
    private PushRegistration() {}
    public static void sync(Context context) {
        Context app = context.getApplicationContext();
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token ->
            Executors.newSingleThreadExecutor().execute(() -> send(app, token)));
    }
    private static void send(Context context, String token) {
        HttpURLConnection conn = null;
        try {
            SharedPreferences prefs = context.getSharedPreferences("live_status", Context.MODE_PRIVATE);
            JSONArray names = new JSONArray(prefs.getString("creators", "[]"));
            JSONObject payload = new JSONObject();
            payload.put("token", token);
            payload.put("creators", names);
            byte[] body = payload.toString().getBytes("UTF-8");
            conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setFixedLengthStreamingMode(body.length);
            try (OutputStream out = conn.getOutputStream()) { out.write(body); }
            android.util.Log.i("OtterPush", "Subscription HTTP " + conn.getResponseCode());
        } catch (Exception e) {
            android.util.Log.w("OtterPush", "Subscription failed", e);
        } finally { if (conn != null) conn.disconnect(); }
    }
}
