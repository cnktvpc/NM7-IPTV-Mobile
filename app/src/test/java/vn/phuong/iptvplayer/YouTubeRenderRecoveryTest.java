package vn.phuong.iptvplayer;
import org.junit.Test;
public class YouTubeRenderRecoveryTest {
    @Test public void healthyFrames() throws Exception { RenderRecoveryScenarios.healthyFrames(); }
    @Test public void boundedStalls() throws Exception { RenderRecoveryScenarios.boundedStallsAcrossRecreation(); }
    @Test public void pauseAndBackground() throws Exception { RenderRecoveryScenarios.ineligibleIntervals(); }
    @Test public void handoff() throws Exception { RenderRecoveryScenarios.targetAndCounterChanges(); }
    @Test public void budgetRefill() throws Exception { RenderRecoveryScenarios.onlySustainedFramesRefill(); }
    @Test public void explicitReset() throws Exception { RenderRecoveryScenarios.manualAndNewVideoReset(); }
}

