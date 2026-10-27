package vn.phuong.iptvplayer;

import java.util.concurrent.TimeUnit;

import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import okhttp3.OkHttpClient;

/**
 * Shared HTTP clients for Mobile playback and channel-logo loading.
 *
 * They use separate dispatchers and connection pools so a large batch of
 * logo requests cannot starve the Media3 playback data source.
 */
final class NetworkClient {
    private static final TimeUnit SECONDS = TimeUnit.SECONDS;

    static final OkHttpClient LOGO_HTTP = new OkHttpClient.Builder()
            .dispatcher(logoDispatcher())
            .connectionPool(new ConnectionPool(12, 5, TimeUnit.MINUTES))
            .connectTimeout(4, SECONDS)
            .readTimeout(8, SECONDS)
            .writeTimeout(8, SECONDS)
            .callTimeout(12, SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build();

    /*
     * Media3 performs separate manifest/segment requests during playback.
     * Keep per-read timeouts, but do not use callTimeout here: a total-call
     * deadline would terminate a long-running media request regardless of
     * whether useful data is still arriving.
     */
    static final OkHttpClient STREAM_HTTP = new OkHttpClient.Builder()
            .dispatcher(streamDispatcher())
            .connectionPool(new ConnectionPool(16, 5, TimeUnit.MINUTES))
            .connectTimeout(15, SECONDS)
            .readTimeout(30, SECONDS)
            .writeTimeout(20, SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .build();

    private NetworkClient() { }

    private static Dispatcher logoDispatcher() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(80);
        dispatcher.setMaxRequestsPerHost(12);
        return dispatcher;
    }

    private static Dispatcher streamDispatcher() {
        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequests(64);
        dispatcher.setMaxRequestsPerHost(16);
        return dispatcher;
    }
}
