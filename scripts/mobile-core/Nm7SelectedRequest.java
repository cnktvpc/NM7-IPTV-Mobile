package com.liskovsoft.smartyoutubetv2.common.misc;

/** One selected-video lookup, started before the screen exists and consumed once by its owner. */
public final class Nm7SelectedRequest<T> {
    public static final class Result<T> {
        public final T value;
        public final Throwable error;
        Result(T value, Throwable error) { this.value = value; this.error = error; }
    }
    private long generation;
    private String videoId;
    private Object owner;
    private boolean complete, consumed;
    private T value;
    private Throwable error;

    public long start(String id) {
        cancel();
        videoId = id;
        return generation;
    }
    public void cancel() {
        generation++;
        videoId = null; owner = null; value = null; error = null;
        complete = consumed = false;
    }
    public boolean pendingFor(String id) {
        return id != null && id.equals(videoId) && !consumed;
    }
    public boolean attach(String id, Object newOwner) {
        if (!pendingFor(id) || newOwner == null || (owner != null && owner != newOwner)) return false;
        owner = newOwner;
        return true;
    }
    public boolean complete(long token, T data, Throwable failure) {
        if (token != generation || videoId == null || complete || consumed) return false;
        value = data; error = failure; complete = true;
        return true;
    }
    public Result<T> take(Object activeOwner, String activeVideoId) {
        if (owner == null || owner != activeOwner || !pendingFor(activeVideoId) || !complete) return null;
        Result<T> result = new Result<>(value, error);
        value = null; error = null; consumed = true;
        return result;
    }
}
