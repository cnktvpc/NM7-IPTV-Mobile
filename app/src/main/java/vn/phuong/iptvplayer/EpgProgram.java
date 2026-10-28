package vn.phuong.iptvplayer;

/** One XMLTV programme with absolute start/end instants. */
final class EpgProgram {
    final String channelId;
    final String title;
    final long startMs;
    final long endMs;
    final String description;

    EpgProgram(String channelId, String title, long startMs, long endMs, String description) {
        this.channelId = channelId == null ? "" : channelId;
        this.title = title == null || title.trim().isEmpty() ? "Chương trình không có tên" : title.trim();
        this.startMs = startMs;
        this.endMs = endMs;
        this.description = description == null ? "" : description.trim();
    }

    boolean isValid() { return startMs > 0 && endMs > startMs; }
    boolean isPast(long nowMs) { return endMs <= nowMs; }
    boolean isCurrent(long nowMs) { return startMs <= nowMs && nowMs < endMs; }
    boolean isFuture(long nowMs) { return startMs > nowMs; }
    long durationSeconds() { return Math.max(0L, (endMs - startMs) / 1000L); }
}
