package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

/** Per-video recovery budget; READY and engine recreation are not proof of rendered output. */
public final class Nm7RenderRecoveryPolicy {
    public static final int NONE = 0, REBIND = 1, RESTART = 2, LIMIT_AND_RESTART = 3, EXHAUSTED = 4;
    private long lastOutputAt = -1, healthySince = -1;
    private int lastOutput = -1, attempts;
    private Object player, target;
    private String videoId;

    public void video(String id) {
        if (id == null ? videoId != null : !id.equals(videoId)) {
            videoId = id;
            manualRetry();
        }
    }

    public void manualRetry() {
        attempts = 0;
        resetObservation();
    }

    public void resetObservation() {
        lastOutputAt = healthySince = -1;
        lastOutput = -1;
        player = target = null;
    }

    public boolean exhausted() { return attempts >= EXHAUSTED; }

    public int failure() {
        resetObservation();
        if (attempts < EXHAUSTED) attempts++;
        return attempts;
    }

    public int sample(long now, Object actualPlayer, Object actualTarget, int rendered, boolean eligible) {
        if (!eligible) { resetObservation(); return NONE; }
        if (player != actualPlayer || target != actualTarget || lastOutputAt < 0 || rendered < lastOutput) {
            player = actualPlayer; target = actualTarget;
            lastOutput = rendered; lastOutputAt = now; healthySince = -1;
            return NONE;
        }
        if (rendered > lastOutput) {
            lastOutput = rendered; lastOutputAt = now;
            if (healthySince < 0) healthySince = now;
            // Only sustained real frame output refills the budget.
            if (now - healthySince >= 30_000L) attempts = 0;
            return NONE;
        }
        healthySince = -1;
        // Buffering, missing/hidden surfaces, audio-only, screen-off and user pause are
        // filtered by the caller. Allow six seconds of READY without output before acting.
        if (exhausted() || now - lastOutputAt < 6_000L) return NONE;
        return failure();
    }
}
