package vn.phuong.iptvplayer;

import android.app.Activity;
import android.app.Application;
import android.net.wifi.WifiManager;
import android.os.Bundle;

/** Process-wide networking defaults for long-running IPTV streams. */
public final class Nm7Application extends Application implements Application.ActivityLifecycleCallbacks {
    private WifiManager.WifiLock wifiLock;
    private int startedActivities;

    @Override public void onCreate() {
        super.onCreate();
        // Media3 DefaultHttpDataSource uses HttpURLConnection. Keep pooled sockets
        // alive longer so HLS/DASH playlist and segment requests can reuse them.
        System.setProperty("http.keepAlive", "true");
        System.setProperty("http.maxConnections", "8");
        System.setProperty("http.keepAliveDuration", "300000");
        registerActivityLifecycleCallbacks(this);
    }

    private void acquireWifiPerformanceLock() {
        if (wifiLock != null && wifiLock.isHeld()) return;
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wifi == null || !wifi.isWifiEnabled()) return;
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Nm7IptvHighPerf");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        } catch (RuntimeException ignored) { }
    }

    private void releaseWifiPerformanceLock() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (RuntimeException ignored) { }
        wifiLock = null;
    }

    @Override public void onActivityStarted(Activity activity) {
        if (++startedActivities == 1) acquireWifiPerformanceLock();
    }
    @Override public void onActivityStopped(Activity activity) {
        if (startedActivities > 0 && --startedActivities == 0) releaseWifiPerformanceLock();
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) {
        if (activity instanceof MainActivity) {
            activity.getWindow().getDecorView().post(() -> HomeTabBar.attach(activity, false));
        }
    }
    @Override public void onActivityResumed(Activity activity) { }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) { }
}
