package vn.phuong.iptvplayer;

/** Exercises the production request state with both possible network/Activity orderings. */
public final class SelectedRequestScenarios {
    private final Class<?> type;
    private final Object request;
    public SelectedRequestScenarios() throws Exception {
        type = Class.forName("com.liskovsoft.smartyoutubetv2.common.misc.Nm7SelectedRequest");
        request = type.getConstructor().newInstance();
    }
    private long start(String id) throws Exception {
        return (Long) type.getMethod("start", String.class).invoke(request, id);
    }
    private boolean attach(String id, Object owner) throws Exception {
        return (Boolean) type.getMethod("attach", String.class, Object.class).invoke(request, id, owner);
    }
    private boolean complete(long token, Object value, Throwable error) throws Exception {
        return (Boolean) type.getMethod("complete", long.class, Object.class, Throwable.class)
                .invoke(request, token, value, error);
    }
    private Object take(Object owner, String id) throws Exception {
        return type.getMethod("take", Object.class, String.class).invoke(request, owner, id);
    }
    private static void check(boolean ok) { if (!ok) throw new AssertionError("Request lifecycle regression"); }
    public static void coldLookupBeforeAndAfterScreen() throws Exception {
        for (boolean resultFirst : new boolean[]{true, false}) {
            SelectedRequestScenarios s = new SelectedRequestScenarios(); Object owner = new Object(), info = new Object();
            long token = s.start("a");
            if (resultFirst) check(s.complete(token, info, null));
            check(s.take(owner, "a") == null); // No consumer until the actual engine attaches.
            check(s.attach("a", owner));
            if (!resultFirst) check(s.complete(token, info, null));
            Object result = s.take(owner, "a");
            check(result != null && result.getClass().getField("value").get(result) == info);
            check(s.take(owner, "a") == null); // No second source prepare on resume.
            check(!s.attach("a", owner)); // Recovery must get a fresh signed source.
        }
    }
    public static void obsoleteSuccessAndErrorCannotReplaceNewVideo() throws Exception {
        SelectedRequestScenarios s = new SelectedRequestScenarios(); Object owner = new Object();
        long a = s.start("a"); check(s.attach("a", owner));
        long b = s.start("b"); check(s.attach("b", owner));
        check(!s.complete(a, "old", null));
        check(!s.complete(a, null, new IllegalStateException("old request")));
        check(s.complete(b, "new", null));
        check(s.take(owner, "a") == null);
        Object result = s.take(owner, "b");
        check("new".equals(result.getClass().getField("value").get(result)));
    }
    public static void obsoleteOwnerCannotConsume() throws Exception {
        SelectedRequestScenarios s = new SelectedRequestScenarios(); Object a = new Object(), b = new Object();
        long token = s.start("same"); check(s.attach("same", a));
        check(!s.attach("same", b)); check(s.complete(token, "data", null));
        check(s.take(b, "same") == null); check(s.take(a, "same") != null);
    }
    public static void cancelAndSameIdRetryRejectOldGeneration() throws Exception {
        SelectedRequestScenarios s = new SelectedRequestScenarios(); Object owner = new Object();
        long old = s.start("a"); check(s.attach("a", owner));
        s.type.getMethod("cancel").invoke(s.request);
        check(!s.complete(old, "old", null));
        long current = s.start("a"); check(current != old); check(s.attach("a", owner));
        check(!s.complete(old, null, new RuntimeException("late error")));
        Throwable failure = new IllegalStateException("new request");
        check(s.complete(current, null, failure));
        Object result = s.take(owner, "a");
        check(result.getClass().getField("error").get(result) == failure);
        check(s.take(owner, "a") == null);
    }
    public static void main(String[] args) throws Exception {
        coldLookupBeforeAndAfterScreen(); obsoleteSuccessAndErrorCannotReplaceNewVideo();
        obsoleteOwnerCannotConsume(); cancelAndSameIdRetryRejectOldGeneration();
        System.out.println("PASS: selected-source timing, stale result/error, owner and retry scenarios");
    }
}
