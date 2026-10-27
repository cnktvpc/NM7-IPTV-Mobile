package vn.phuong.iptvplayer;

/** Runs the production policy on a deterministic clock, also runnable without Android. */
public final class RenderRecoveryScenarios {
    private final Object policy, player = new Object(), target = new Object();
    private final Class<?> type;
    public RenderRecoveryScenarios() throws Exception {
        type = Class.forName("com.liskovsoft.smartyoutubetv2.droid.ui.shared.Nm7RenderRecoveryPolicy");
        policy = type.getConstructor().newInstance();
    }
    private int sample(long time, Object p, Object t, int frames, boolean eligible) throws Exception {
        return (Integer) type.getMethod("sample", long.class, Object.class, Object.class, int.class, boolean.class)
                .invoke(policy, time, p, t, frames, eligible);
    }
    private int failure() throws Exception { return (Integer) type.getMethod("failure").invoke(policy); }
    private void call(String method) throws Exception { type.getMethod(method).invoke(policy); }
    private void video(String id) throws Exception { type.getMethod("video", String.class).invoke(policy, id); }
    private static void equal(int expected, int actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    public static void healthyFrames() throws Exception {
        RenderRecoveryScenarios s = new RenderRecoveryScenarios();
        for (int i = 0; i < 120; i++) equal(0, s.sample(i * 1000L, s.player, s.target, i * 30, true));
    }
    public static void boundedStallsAcrossRecreation() throws Exception {
        RenderRecoveryScenarios s = new RenderRecoveryScenarios();
        for (int attempt = 1; attempt <= 4; attempt++) {
            Object replacement = new Object();
            equal(0, s.sample(attempt * 10000L, replacement, s.target, 0, true));
            equal(0, s.sample(attempt * 10000L + 5999, replacement, s.target, 0, true));
            equal(attempt, s.sample(attempt * 10000L + 6000, replacement, s.target, 0, true));
            s.call("resetObservation");
        }
        if (!(Boolean) s.type.getMethod("exhausted").invoke(s.policy)) throw new AssertionError("Unbounded retries");
        equal(0, s.sample(100000, s.player, s.target, 0, true));
        equal(0, s.sample(200000, s.player, s.target, 0, true));
    }
    public static void ineligibleIntervals() throws Exception {
        RenderRecoveryScenarios s = new RenderRecoveryScenarios();
        // Same false eligibility is used for pause, background, buffering and absent surface.
        for (int i = 0; i < 4; i++) {
            long t = i * 100000L;
            equal(0, s.sample(t, s.player, s.target, 1, true));
            equal(0, s.sample(t + 60000, s.player, s.target, 1, false));
            equal(0, s.sample(t + 70000, s.player, s.target, 1, true));
            equal(0, s.sample(t + 75999, s.player, s.target, 1, true));
            equal(0, s.sample(t + 76000, s.player, s.target, 1, false));
        }
        equal(1, s.failure());
    }
    public static void targetAndCounterChanges() throws Exception {
        RenderRecoveryScenarios s = new RenderRecoveryScenarios(); Object mini = new Object();
        equal(0, s.sample(0, s.player, s.target, 100, true));
        equal(0, s.sample(5999, s.player, mini, 100, true));
        equal(0, s.sample(11998, s.player, mini, 100, true));
        equal(0, s.sample(11999, s.player, mini, 0, true));
        equal(0, s.sample(17998, s.player, mini, 0, true));
        equal(1, s.sample(17999, s.player, mini, 0, true));
    }
    public static void onlySustainedFramesRefill() throws Exception {
        RenderRecoveryScenarios s = new RenderRecoveryScenarios();
        equal(1, s.failure()); equal(2, s.failure());
        s.sample(0, s.player, s.target, 0, true);
        s.sample(1000, s.player, s.target, 30, true);
        equal(3, s.failure()); // One first frame / READY cannot refill.
        s.sample(2000, s.player, s.target, 30, true);
        for (int i = 3; i <= 33; i++) s.sample(i * 1000L, s.player, s.target, i * 30, true);
        equal(1, s.failure());
    }
    public static void manualAndNewVideoReset() throws Exception {
        RenderRecoveryScenarios s = new RenderRecoveryScenarios(); s.video("a");
        equal(1, s.failure()); s.video("a"); equal(2, s.failure());
        s.video("b"); equal(1, s.failure());
        s.call("manualRetry"); equal(1, s.failure());
    }
    public static void main(String[] args) throws Exception {
        healthyFrames(); boundedStallsAcrossRecreation(); ineligibleIntervals();
        targetAndCounterChanges(); onlySustainedFramesRefill(); manualAndNewVideoReset();
        System.out.println("PASS: six production recovery-policy scenarios");
    }
}
