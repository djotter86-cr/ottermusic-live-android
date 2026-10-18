package de.ottermusic.livestatus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import org.json.JSONArray;

public class OtterFirebaseMessagingService extends FirebaseMessagingService {
    private static final String CHANNEL_ID = "ottermusic_live_starts";
    @Override public void onNewToken(String token) { super.onNewToken(token); PushRegistration.sync(this); }
    @Override public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        String creator = message.getData().get("creator");
        if (creator == null || !creator.matches("[A-Za-z0-9._]{2,24}")) return;
        try {
            JSONArray names = new JSONArray(getSharedPreferences("live_status", MODE_PRIVATE).getString("creators", "[]"));
            boolean saved = false;
            for (int i = 0; i < names.length(); i++) if (creator.equalsIgnoreCase(names.optString(i))) saved = true;
            if (!saved) return;
        } catch (Exception e) { return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "Creator geht LIVE", NotificationManager.IMPORTANCE_DEFAULT));
        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.tiktok.com/@" + creator));
        PendingIntent pending = PendingIntent.getActivity(this, creator.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle("@" + creator + " ist jetzt LIVE!")
            .setContentText("Tippe hier, um das TikTok-Profil zu öffnen.")
            .setContentIntent(pending).setAutoCancel(true).build();
        manager.notify(creator.hashCode(), notification);
    }
}
