package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

/** One automatic recovery per video. A user retry is explicit; READY does not refill the budget. */
public final class Nm7MiniRecoveryGate {
    private String videoId;
    private boolean attempted;
    public void video(String id) {
        if (id == null ? videoId != null : !id.equals(videoId)) {
            videoId = id; attempted = false;
        }
    }
    public boolean allow(String id, boolean play, boolean suspended, boolean manual) {
        video(id);
        if (id == null || !play || suspended || (attempted && !manual)) return false;
        attempted = true;
        return true;
    }
}
