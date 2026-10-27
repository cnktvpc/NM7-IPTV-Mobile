package vn.phuong.iptvplayer;

public final class SleepTimerReceiver extends android.content.BroadcastReceiver {
    @Override public void onReceive(android.content.Context context, android.content.Intent intent) {
        SleepTimer.expire(context);
    }
}
