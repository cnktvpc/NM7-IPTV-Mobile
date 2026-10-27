package vn.phuong.iptvplayer;

/** Wall-clock rate of rendered decoder buffers, not the declared stream frame rate. */
public final class FpsMeter {
    private long lastTime = -1, lastFrames;
    public void reset() { lastTime = -1; lastFrames = 0; }
    public double sample(long nowMs, long renderedFrames, boolean playing) {
        if (!playing) { lastTime = -1; lastFrames = renderedFrames; return 0; }
        double fps = Double.NaN;
        if (lastTime >= 0 && nowMs > lastTime && renderedFrames >= lastFrames)
            fps = (renderedFrames - lastFrames) * 1000.0 / (nowMs - lastTime);
        lastTime = nowMs;
        lastFrames = renderedFrames;
        return fps;
    }
}
