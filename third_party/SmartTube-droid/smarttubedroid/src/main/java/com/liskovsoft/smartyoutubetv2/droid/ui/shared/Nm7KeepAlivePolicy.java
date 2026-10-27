package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

/** A session survives buffering, renderer replacement and temporary media-clock pauses. */
public final class Nm7KeepAlivePolicy {
    public static boolean keep(boolean owns, boolean stopped, boolean iptv, boolean mini, boolean background) {
        return owns && !stopped && !iptv && (mini || background);
    }
    public static boolean wake(boolean play, boolean recovery, boolean ended) {
        return !ended && (play || recovery);
    }
}
