package vn.phuong.iptvplayer;
import org.junit.Test;
public class YouTubeSelectedRequestTest {
    @Test public void coldScreenAndNetworkOrderings() throws Exception { SelectedRequestScenarios.coldLookupBeforeAndAfterScreen(); }
    @Test public void obsoleteCallbacks() throws Exception { SelectedRequestScenarios.obsoleteSuccessAndErrorCannotReplaceNewVideo(); }
    @Test public void ownerIsolation() throws Exception { SelectedRequestScenarios.obsoleteOwnerCannotConsume(); }
    @Test public void cancelAndRetry() throws Exception { SelectedRequestScenarios.cancelAndSameIdRetryRejectOldGeneration(); }
}
