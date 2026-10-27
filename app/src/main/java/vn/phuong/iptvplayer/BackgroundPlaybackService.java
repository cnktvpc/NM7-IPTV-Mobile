package vn.phuong.iptvplayer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.media3.common.util.UnstableApi;

/** Keeps the mobile player process foreground while playback continues after Home/lock. */
@UnstableApi
public final class BackgroundPlaybackService extends Service {
    static final String EXTRA_CHANNEL_NAME = "channel_name";
    static final String EXTRA_RETURN_MAIN = "return_main";
    static final String EXTRA_YOUTUBE = "youtube";
    private static final String CHANNEL_ID = "background_playback";
    private static final int NOTIFICATION_ID = 180;
    private android.os.PowerManager.WakeLock youtubeWakeLock;
    private android.net.wifi.WifiManager.WifiLock youtubeWifiLock;

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Phát IPTV nền", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Hiển thị khi Nm7 IPTV tiếp tục phát sau khi khóa màn hình hoặc nhấn Home.");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String name = intent == null ? "" : intent.getStringExtra(EXTRA_CHANNEL_NAME);
        boolean returnMain = intent != null && intent.getBooleanExtra(EXTRA_RETURN_MAIN, false);
        boolean youtube = intent != null && intent.getBooleanExtra(EXTRA_YOUTUBE, false);
        Intent launch = new Intent(this, returnMain ? MainActivity.class : PlayerActivity.class);
        if (youtube) {
            launch = new Intent().setClassName(this,
                    "com.liskovsoft.smartyoutubetv2.droid.ui.playback.PlaybackActivity");
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent content = PendingIntent.getActivity(this, returnMain ? 1 : 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Notification notification = builder.setSmallIcon(R.mipmap.ic_launcher).setContentTitle(youtube ? "Nm7 YouTube đang phát nền" : "Nm7 IPTV đang phát nền")
                .setContentText(name == null || name.isEmpty() ? "Chạm để quay lại ứng dụng" : name)
                .setContentIntent(content).setOngoing(true).setCategory(Notification.CATEGORY_SERVICE).build();
        startForeground(NOTIFICATION_ID, notification);
        boolean youtubeActive = youtube && intent.getBooleanExtra("youtube_active", true);
        if (youtubeActive && youtubeWakeLock == null) {
            android.os.PowerManager power = (android.os.PowerManager) getSystemService(POWER_SERVICE);
            if (power != null) {
                youtubeWakeLock = power.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "NM7:YouTubePlayback");
                youtubeWakeLock.setReferenceCounted(false);
                youtubeWakeLock.acquire();
            }
            android.net.wifi.WifiManager wifi = (android.net.wifi.WifiManager)
                    getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi != null) {
                try {
                    youtubeWifiLock = wifi.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                            "NM7:YouTubeBackgroundWifi");
                    youtubeWifiLock.setReferenceCounted(false);
                    youtubeWifiLock.acquire();
                } catch (RuntimeException error) { youtubeWifiLock = null; }
            }
        } else if (!youtubeActive) {
            releaseYoutubeWakeLock();
        }
        return START_NOT_STICKY;
    }

    @Override public void onTaskRemoved(Intent rootIntent) { /* Keep playback service alive; explicit stop is used when playback ends. */ }
    @Override public IBinder onBind(Intent intent) { return null; }
    private void releaseYoutubeWakeLock() {
        if (youtubeWifiLock != null && youtubeWifiLock.isHeld()) youtubeWifiLock.release();
        youtubeWifiLock = null;
        if (youtubeWakeLock != null && youtubeWakeLock.isHeld()) youtubeWakeLock.release();
        youtubeWakeLock = null;
    }
    @Override public void onDestroy() {
        releaseYoutubeWakeLock();
        super.onDestroy();
    }
}
