package vn.phuong.iptvplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.LinearLayout;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.rtsp.RtspMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.AspectRatioFrameLayout;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.GZIPInputStream;

@UnstableApi
public final class PlayerActivity extends Activity {
    private static PlayerActivity currentInstance;
    public static final String EXTRA_NAME = "name", EXTRA_URL = "url";
    public static final String EXTRA_USER_AGENT = "user_agent", EXTRA_REFERER = "referer", EXTRA_ORIGIN = "origin";
    public static final String EXTRA_HEADERS = "headers", EXTRA_MIME = "mime", EXTRA_OPTIONS = "options";
    public static final String EXTRA_LIVE_EVENT = "live_event_json";
    public static final String EXTRA_EPG_URL = "epg_url";
    public static final String EXTRA_TVG_ID = "tvg_id";
    public static final String EXTRA_EXTINF = "original_extinf";
    public static final String EXTRA_LIVE_URL = "original_live_url";
    private LiveEvent liveEvent;
    private ExoPlayer player;
    private PlayerView playerView;
    private TextView status;
    private TextView fpsView;
    private String url, name, mime;
    private String liveUrl = "", epgUrl = "", epgTvgId = "", epgExtInf = "";
    private String liveName = "";
    private boolean catchupMode;
    private String drmSystem = "", drmLicense = "";
    private ArrayList<String> options = new ArrayList<>();
    private Bundle currentHeaders = new Bundle();
    private long position;
    private boolean resumePlayback = true;
    private int quality = Integer.MAX_VALUE;
    private int resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
    private WifiManager.MulticastLock multicastLock;
    private DecoderCounters videoCounters;
    private final FpsMeter fpsMeter = new FpsMeter();
    private final ExecutorService drmIo = Executors.newSingleThreadExecutor();
    private boolean resolvingClearKey;
    private boolean resolvingStreamMime;
    private boolean activityStarted;
    private boolean backgroundPlaybackActive;
    // Explicit handoff guard: switching to YouTube is navigation only; keep the IPTV
    // ExoPlayer alive until SmartTube PlaybackActivity actually starts a video.
    private boolean keepPlayerForTabSwitch;
    private static final String YOUTUBE_HANDOFF_PROPERTY = "nm7.iptv.youtube.handoff";
    private final Handler fpsHandler = new Handler(Looper.getMainLooper());
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Handler recoveryHandler = new Handler(Looper.getMainLooper());
    private int recoveryAttempts;
    private static final int MAX_RECOVERY_ATTEMPTS = 4;
    private final Runnable resetRecoveryAttempts = () -> recoveryAttempts = 0;
    private TextView clockView;
    private View quickPanel;
    private LinearLayout quickGroupRow;
    private ListView quickChannelList;
    private TextView quickEmpty;
    private QuickChannelAdapter quickAdapter;
    private final List<Channel> quickChannels = new ArrayList<>();
    private final LinkedHashMap<String,List<Channel>> quickChannelsByGroup = new LinkedHashMap<>();
    private final List<String> quickGroups = new ArrayList<>();
    private String quickGroup = "";
    private String quickCurrentId = "";
    private float quickSwipeStartX, quickSwipeStartY;
    private long quickSwipeStartTime;
    private long bufferingSinceMs;
    private final Runnable stalledPlaybackCheck = () -> { if(player!=null&&player.getPlaybackState()==Player.STATE_BUFFERING&&bufferingSinceMs>0&&android.os.SystemClock.elapsedRealtime()-bufferingSinceMs>=20_000) scheduleRecovery("Luồng tải quá lâu",false); };
    private TextView gestureFeedback;
    private android.media.AudioManager audioManager;
    private float gestureStartY, gestureStartBrightness;
    private int gestureMode, gestureStartVolume;
    private int consumedRemoteKey = KeyEvent.KEYCODE_UNKNOWN;
    private final Runnable clockUpdate = new Runnable() {
        @Override public void run() {
            if (clockView != null) clockView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
            clockHandler.postDelayed(this, 30_000);
        }
    };
    private final Runnable fpsUpdate = new Runnable() {
        @Override public void run() {
            if (player != null && videoCounters != null) {
                double fps = fpsMeter.sample(android.os.SystemClock.elapsedRealtime(),
                        videoCounters.renderedOutputBufferCount, player.isPlaying());
                fpsView.setText(Double.isNaN(fps) ? "FPS: đang đo…" : String.format(Locale.ROOT, "FPS thực tế: %.1f", fps));
            } else fpsView.setText("FPS: chưa có hình");
            fpsHandler.postDelayed(this, 2000);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        currentInstance = this;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_player);
        Insets.apply(findViewById(R.id.playerRoot));
        url = value(EXTRA_URL); name = value(EXTRA_NAME); mime = value(EXTRA_MIME);
        liveName = name;
        liveUrl = getIntent().getStringExtra(EXTRA_LIVE_URL);
        if (liveUrl == null || liveUrl.isEmpty()) liveUrl = url;
        epgUrl = value(EXTRA_EPG_URL); epgTvgId = value(EXTRA_TVG_ID); epgExtInf = value(EXTRA_EXTINF);
        if (url.isEmpty()) {
            SharedPlaybackSession.State saved = SharedPlaybackSession.loadIptv(this);
            if (saved != null) {
                name = saved.name;
                url = saved.url;
                mime = saved.mime;
                currentHeaders = saved.headers;
                options = saved.options;
                position = saved.position;
                resumePlayback = saved.playing;
            }
        }
        ArrayList<String> passedOptions = getIntent().getStringArrayListExtra(EXTRA_OPTIONS);
        if (passedOptions != null) options = passedOptions;
        Bundle passedHeaders = getIntent().getBundleExtra(EXTRA_HEADERS);
        if (passedHeaders != null) currentHeaders = passedHeaders;
        liveEvent = LivePlaybackSession.decode(value(EXTRA_LIVE_EVENT));
        if (liveEvent == null) {
            LiveEvent active = LivePlaybackSession.activeEvent();
            if (active != null && active.hasSourceUrl(url)) liveEvent = active.withPreferredUrl(url);
        }
        if (liveEvent != null) {
            liveEvent = liveEvent.withPreferredUrl(url);
            LivePlaybackSession.setActive(liveEvent);
        }
        DrmSpec initialDrm = DrmSpec.fromOptions(options); drmSystem = initialDrm.system; drmLicense = initialDrm.license;
        if (state != null) {
            position = state.getLong("position"); resumePlayback = state.getBoolean("playing", true);
            quality = state.getInt("quality", Integer.MAX_VALUE); resizeMode = state.getInt("resize", AspectRatioFrameLayout.RESIZE_MODE_FIT);
            mime = state.getString("mime", mime); drmSystem = state.getString("drm_system", drmSystem); drmLicense = state.getString("drm_license", drmLicense);
        }
        playerView = findViewById(R.id.playerView); status = findViewById(R.id.txtPlayerStatus); fpsView = findViewById(R.id.txtFps); clockView = findViewById(R.id.txtClock);
        quickPanel = findViewById(R.id.quickChannelPanel); quickGroupRow = findViewById(R.id.quickGroupRow); quickChannelList = findViewById(R.id.quickChannelList); quickEmpty = findViewById(R.id.txtQuickEmpty); setupQuickGroupSwipe(findViewById(R.id.quickGroupScroller));
        quickAdapter = new QuickChannelAdapter(this); quickChannelList.setAdapter(quickAdapter); quickChannelList.setEmptyView(quickEmpty);
        quickChannelList.setOnItemClickListener((parent, view, p, id) -> switchChannel(quickAdapter.getItem(p)));
        fpsView.setVisibility(AppPreferences.showFps(this) ? View.VISIBLE : View.GONE); clockView.setVisibility(AppPreferences.showClock(this) ? View.VISIBLE : View.GONE);
        playerView.setResizeMode(resizeMode); ((TextView) findViewById(R.id.txtPlayerTitle)).setText(name);
        TextView source = findViewById(R.id.txtPlayerUrl); source.setText(url); source.setVisibility(AppPreferences.showPlayerSource(this) ? View.VISIBLE : View.GONE); source.setOnClickListener(v -> showSource());
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> findViewById(R.id.playerHeader).setVisibility(visibility));
        findViewById(R.id.btnBack).setOnClickListener(v -> { if (catchupMode) returnToLive(); else finish(); }); findViewById(R.id.btnEpg).setOnClickListener(v -> showPlayerEpg(0)); findViewById(R.id.btnQuality).setOnClickListener(v -> chooseQuality());
        findViewById(R.id.btnFormat).setOnClickListener(v -> chooseFormat()); View rotateButton = findViewById(R.id.btnRotate); if (rotateButton != null) rotateButton.setOnClickListener(v -> chooseOrientation());
        findViewById(R.id.btnDrm).setOnClickListener(v -> configureDrm()); findViewById(R.id.btnResize).setOnClickListener(v -> chooseResizeMode());
        findViewById(R.id.btnLiveServer).setOnClickListener(v -> showLiveServerPicker());
        updateLiveServerButton();
        findViewById(R.id.btnRetry).setOnClickListener(v -> { position = 0; resumePlayback = true; releasePlayer(); startPlayer(); }); setupMobileEdgeGestures(); loadQuickChannels();
    }
    @Override protected void onStart() { super.onStart(); activityStarted = true; boolean youtubeTab = SharedPlaybackSession.TAB_YOUTUBE.equals(SharedPlaybackSession.tab(this)); keepPlayerForTabSwitch = isYoutubeHandoffPending() || youtubeTab; if(!youtubeTab) stopService(new android.content.Intent(this,BackgroundPlaybackService.class)); backgroundPlaybackActive=false; fpsHandler.post(fpsUpdate); clockHandler.post(clockUpdate); if(!keepPlayerForTabSwitch) startPlayer(); }
    @Override protected void onResume(){super.onResume();if(player!=null){player.setWakeMode(C.WAKE_MODE_NONE);if(playerView!=null&&playerView.getPlayer()!=player)playerView.setPlayer(player);}}
    @Override protected void onPause(){if(!keepPlayerForTabSwitch&&!isYoutubeHandoffPending()&&!MobileNm7Application.isTabSwitchPending()&&shouldUseBackgroundPlayback()&&!isFinishing()&&player!=null){backgroundPlaybackActive=true;player.setWakeMode(C.WAKE_MODE_NETWORK);android.content.Intent service=new android.content.Intent(this,BackgroundPlaybackService.class).putExtra(BackgroundPlaybackService.EXTRA_CHANNEL_NAME,name);if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);}super.onPause();}
    private boolean shouldUseBackgroundPlayback(){return !AppPreferences.isTvInterface(this)&&AppPreferences.backgroundPlayback(this);}

    private Channel epgChannel(){
        Map<String,String> headers=new LinkedHashMap<>();
        for(String key:currentHeaders.keySet()){String value=currentHeaders.getString(key);if(value!=null)headers.put(key,value);}
        Channel channel=new Channel(liveName, "", liveUrl, "", epgTvgId, headers);
        channel.setOriginalExtInf(epgExtInf);
        channel.options().addAll(options);
        return channel;
    }

    private void showPlayerEpg(int dayOffset){
        if(epgUrl==null||epgUrl.isEmpty()){new AlertDialog.Builder(this).setTitle("Lịch phát sóng").setMessage("Chưa có URL EPG. Hãy cấu hình lịch trong Tùy chọn IPTV.").setPositiveButton("Đóng",null).show();return;}
        new Thread(()->{
            try{
                HttpURLConnection connection=(HttpURLConnection)new URL(epgUrl).openConnection();
                connection.setConnectTimeout(12000);connection.setReadTimeout(25000);
                connection.setRequestProperty("User-Agent","Nm7-IPTV/1.10.146 Android");
                connection.setRequestProperty("Accept-Encoding","gzip");
                try{
                    int code=connection.getResponseCode();
                    if(code<200||code>=300)throw new Exception("HTTP "+code);
                    try(InputStream raw=new BufferedInputStream(connection.getInputStream());
                        InputStream input=EpgStore.isGzip(epgUrl,connection)?new GZIPInputStream(raw):raw){
                        EpgScheduleStore.Schedule schedule=EpgScheduleStore.parse(input);
                        runOnUiThread(()->showPlayerEpgDialog(schedule,dayOffset));
                    }
                }finally{connection.disconnect();}
            }catch(Exception error){runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Lịch phát sóng").setMessage("Không tải được EPG: "+error.getMessage()).setPositiveButton("Đóng",null).show());}
        },"nm7-player-epg").start();
    }

    private void showPlayerEpgDialog(EpgScheduleStore.Schedule schedule,int dayOffset){
        java.util.Calendar day=java.util.Calendar.getInstance();
        day.add(java.util.Calendar.DAY_OF_YEAR,dayOffset);
        day.set(java.util.Calendar.HOUR_OF_DAY,0);day.set(java.util.Calendar.MINUTE,0);day.set(java.util.Calendar.SECOND,0);day.set(java.util.Calendar.MILLISECOND,0);
        long dayStart=day.getTimeInMillis(),now=System.currentTimeMillis();
        List<EpgProgram> programmes=schedule.forChannel(epgChannel(),dayStart);
        String date=new java.text.SimpleDateFormat("EEE dd/MM/yyyy",Locale.getDefault()).format(new Date(dayStart));
        if(programmes.isEmpty()){
            new AlertDialog.Builder(this).setTitle("LỊCH PHÁT SÓNG · "+date)
                .setMessage("Không tìm thấy lịch cho kênh "+liveName+". Kiểm tra tvg-id và URL EPG.")
                .setNeutralButton("Ngày trước",(d,w)->showPlayerEpg(dayOffset-1))
                .setPositiveButton("Ngày sau",(d,w)->showPlayerEpg(dayOffset+1)).setNegativeButton("Đóng",null).show();return;
        }
        String[] labels=new String[programmes.size()];
        for(int i=0;i<programmes.size();i++){
            EpgProgram p=programmes.get(i);
            String state=p.isCurrent(now)?"  ● ĐANG PHÁT":p.isFuture(now)?"  • Sắp phát":CatchupUrlBuilder.canReplay(epgChannel(),p,now)?"  ◀ Xem lại":"";
            labels[i]=new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(p.startMs))+"–"+
                new java.text.SimpleDateFormat("HH:mm",Locale.getDefault()).format(new Date(p.endMs))+"  "+p.title+state;
        }
        new AlertDialog.Builder(this).setTitle("LỊCH PHÁT SÓNG · "+date)
            .setItems(labels,(d,which)->{
                if(which<0||which>=programmes.size())return;
                EpgProgram selected=programmes.get(which);long at=System.currentTimeMillis();
                if(selected.isCurrent(at)){returnToLive();return;}
                if(selected.isFuture(at)){new AlertDialog.Builder(this).setMessage("Chương trình chưa phát sóng.").setPositiveButton("Đóng",null).show();return;}
                Channel source=epgChannel();
                if(!CatchupUrlBuilder.canReplay(source,selected,at)){
                    new AlertDialog.Builder(this).setTitle(selected.title).setMessage("Kênh không có Catch-up hợp lệ hoặc chương trình đã quá hạn lưu trữ.").setPositiveButton("Đóng",null).show();return;
                }
                try{
                    url=CatchupUrlBuilder.build(source,selected,at);position=0;resumePlayback=true;catchupMode=true;
                    ((TextView)findViewById(R.id.txtPlayerTitle)).setText("DVR · "+selected.title);
                    ((Button)findViewById(R.id.btnEpg)).setText("DVR · Lịch");
                    TextView sourceText=findViewById(R.id.txtPlayerUrl);sourceText.setText(url);
                    releasePlayer();startPlayer();
                }catch(Exception error){new AlertDialog.Builder(this).setTitle("Không thể xem lại").setMessage(error.getMessage()).setPositiveButton("Đóng",null).show();}
            }).setNeutralButton("Ngày trước",(d,w)->showPlayerEpg(dayOffset-1))
            .setPositiveButton("Ngày sau",(d,w)->showPlayerEpg(dayOffset+1)).setNegativeButton("Đóng",null).show();
    }

    private void returnToLive(){
        if(!catchupMode)return;
        catchupMode=false;url=liveUrl;position=0;resumePlayback=true;
        ((TextView)findViewById(R.id.txtPlayerTitle)).setText(liveName);
        ((Button)findViewById(R.id.btnEpg)).setText("Lịch");
        TextView sourceText=findViewById(R.id.txtPlayerUrl);sourceText.setText(url);
        releasePlayer();startPlayer();
    }
    private boolean playbackContextActive(){return activityStarted||backgroundPlaybackActive;}
    private static DefaultLoadControl stableLoadControl(){return new DefaultLoadControl.Builder().setBufferDurationsMs(2_500,10_000,400,1_200).setBackBuffer(0,false).setPrioritizeTimeOverSizeThresholds(true).build();}
    private void startPlayer() {
        if (player != null) return; String scheme = Uri.parse(url).getScheme();
        if (scheme == null || !(scheme.matches("(?i)https?|rtsp|udp|rtmp"))) { showError("Bản này chưa hỗ trợ giao thức " + scheme + ". Không thể bảo đảm mọi giao thức IPTV."); return; }
        try {
            Map<String,String> headers = new LinkedHashMap<>(); if (currentHeaders != null) for (String key : currentHeaders.keySet()) { String v=currentHeaders.getString(key); if(v!=null) headers.put(key,v); } if(!headers.containsKey("Connection"))headers.put("Connection","keep-alive");
            String ua=headers.containsKey("User-Agent")?headers.get("User-Agent"):"Nm7-IPTV/1.10.8 Android";
            OkHttpDataSource.Factory http=new OkHttpDataSource.Factory(NetworkClient.STREAM_HTTP).setUserAgent(ua).setDefaultRequestProperties(headers);
            DefaultDataSource.Factory data=new DefaultDataSource.Factory(this,http); DefaultMediaSourceFactory mediaFactory=new DefaultMediaSourceFactory(data).setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(6));
            MediaItem.Builder builder=new MediaItem.Builder().setUri(url)
                    .setLiveConfiguration(new MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(3_500).setMinPlaybackSpeed(1.0f).setMaxPlaybackSpeed(1.0f).build()); String inferred=mime.isEmpty()?StreamSpec.inferMime(url,options):mime; if(inferred!=null&&!inferred.isEmpty()) builder.setMimeType(inferred);
            DrmSpec drm=DrmSpec.create(drmSystem,drmLicense); findViewById(R.id.btnDrm).setVisibility(drm.hasDrm()?View.VISIBLE:View.GONE); if(drm.remoteClearKey()){resolveRemoteClearKey(drm,headers);return;} DrmPlayback.configure(drm,builder,mediaFactory);
            player=new ExoPlayer.Builder(this,new DefaultRenderersFactory(this).setEnableDecoderFallback(true)).setLoadControl(stableLoadControl()).setMediaSourceFactory(mediaFactory).build();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),false); player.setVolume(0f); player.setHandleAudioBecomingNoisy(true); playerView.setPlayer(player);
            player.addAnalyticsListener(new AnalyticsListener(){@Override public void onVideoEnabled(EventTime e,DecoderCounters c){videoCounters=c;fpsMeter.reset();}}); applyQuality();
            player.addListener(new Player.Listener(){
                @Override public void onIsPlayingChanged(boolean playing) {
                    if(playing && player!=null && player.getVolume()==0f){
                        MobileNm7Application.suspendYoutubeForIptv();
                        player.setVolume(1f);
                        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
                    }
                }
                @Override public void onPlayerError(PlaybackException e){
                    if(e.errorCode==PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED&&mime.isEmpty()&&!resolvingStreamMime){resolveRedirectedMime(e);return;}
                    if(isTransientPlaybackError(e)){scheduleRecovery("Luồng tạm gián đoạn",false);return;}
                    showError(e.getErrorCodeName()+"\nKiểm tra URL, quyền truy cập và codec của thiết bị.");
                }
                @Override public void onVideoSizeChanged(VideoSize s){status.setText(s.width+" × "+s.height+" • độ phân giải thực tế");}
                @Override public void onPlaybackStateChanged(int s){
                    if(s==Player.STATE_BUFFERING){status.setText("Đang tải luồng…");if(bufferingSinceMs==0){bufferingSinceMs=android.os.SystemClock.elapsedRealtime();recoveryHandler.removeCallbacks(stalledPlaybackCheck);recoveryHandler.postDelayed(stalledPlaybackCheck,20_000);}}
                    if(s==Player.STATE_READY){bufferingSinceMs=0;recoveryHandler.removeCallbacks(stalledPlaybackCheck);
                        if(player.getPlayWhenReady() && player.getVolume()==0f){
                            MobileNm7Application.suspendYoutubeForIptv();
                            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
                            player.setVolume(1f);
                        }
                        findViewById(R.id.playerError).setVisibility(View.GONE);VideoSize v=player.getVideoSize();status.setText(v.width>0?v.width+" × "+v.height+" • độ phân giải thực tế":"Đang phát âm thanh");
                        recoveryHandler.removeCallbacks(resetRecoveryAttempts);recoveryHandler.postDelayed(resetRecoveryAttempts,30_000);
                    }
                    if(s==Player.STATE_ENDED&&resumePlayback)scheduleRecovery("Danh sách phát đã hết, đang lấy phiên mới",true);
                }
            });
            MediaItem item=builder.build(); if("rtsp".equalsIgnoreCase(scheme)) player.setMediaSource(new RtspMediaSource.Factory().setForceUseRtpTcp(true).setUserAgent(ua).createMediaSource(item)); else {if("udp".equalsIgnoreCase(scheme))acquireMulticast();player.setMediaItem(item);} if(position>0)player.seekTo(position);player.setPlayWhenReady(resumePlayback);player.prepare();
        } catch(Exception error){releasePlayer();String m=error.getMessage();showError(m==null?"Không mở được nguồn phát: "+error.getClass().getSimpleName():m);}
    }
    private void resolveRemoteClearKey(DrmSpec drm, Map<String,String> streamHeaders) { if(resolvingClearKey)return;resolvingClearKey=true;status.setText("Đang lấy giấy phép ClearKey…");drmIo.execute(()->{HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(drm.license).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(true);c.setRequestMethod("GET");for(Map.Entry<String,String> e:drm.headers.entrySet())c.setRequestProperty(e.getKey(),e.getValue());String sh=Uri.parse(url).getHost(),lh=Uri.parse(drm.license).getHost();if(sh!=null&&sh.equalsIgnoreCase(lh))for(String n:new String[]{"User-Agent","Referer","Origin","Cookie"}){String v=streamHeaders.get(n);if(v!=null&&!v.isEmpty()&&!drm.headers.containsKey(n))c.setRequestProperty(n,v);}if(c.getRequestProperty("User-Agent")==null)c.setRequestProperty("User-Agent","Dalvik/2.1.0");int code=c.getResponseCode();if(code<200||code>=300)throw new IllegalArgumentException("HTTP "+code);ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=c.getInputStream()){byte[] b=new byte[4096];int n,total=0;while((n=in.read(b))>=0){total+=n;if(total>65536)throw new IllegalArgumentException("phản hồi quá lớn");out.write(b,0,n);}}String r=out.toString("UTF-8").trim();DrmPlayback.clearKeyResponse(r);runOnUiThread(()->{resolvingClearKey=false;drmLicense=r;if(activityStarted&&!isFinishing()&&!isDestroyed())startPlayer();});}catch(Exception e){runOnUiThread(()->{resolvingClearKey=false;showError("Không lấy được giấy phép ClearKey bằng GET. Kiểm tra token hoặc quyền truy cập nguồn.");});}finally{if(c!=null)c.disconnect();}}); }
    private boolean isTransientPlaybackError(PlaybackException error){
        if(error.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED||error.errorCode==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)return true;
        Throwable cause=error.getCause();
        while(cause!=null){if(cause instanceof HttpDataSource.InvalidResponseCodeException){int code=((HttpDataSource.InvalidResponseCodeException)cause).responseCode;return code==408||code==429||code>=500;}cause=cause.getCause();}
        return false;
    }
    private void resolveRedirectedMime(PlaybackException originalError){
        resolvingStreamMime=true;status.setText("Đang nhận diện định dạng sau chuyển hướng…");
        Map<String,String> headers=new LinkedHashMap<>();if(currentHeaders!=null)for(String key:currentHeaders.keySet()){String value=currentHeaders.getString(key);if(value!=null)headers.put(key,value);}
        drmIo.execute(()->{
            String current=url,detected="";
            try{
                for(int hop=0;hop<5;hop++){
                    HttpURLConnection connection=(HttpURLConnection)new URL(current).openConnection();
                    try{
                        connection.setConnectTimeout(10_000);connection.setReadTimeout(10_000);connection.setInstanceFollowRedirects(false);connection.setRequestMethod("GET");
                        for(Map.Entry<String,String> header:headers.entrySet())connection.setRequestProperty(header.getKey(),header.getValue());
                        if(!headers.containsKey("User-Agent"))connection.setRequestProperty("User-Agent","Nm7-IPTV/1.10.8 Android");
                        int code=connection.getResponseCode();
                        if(code>=300&&code<400){
                            String location=connection.getHeaderField("Location");if(location==null||location.isEmpty())break;
                            current=new URL(new URL(current),location).toString();String hint=StreamSpec.inferMime(current,options);if(hint!=null&&!hint.isEmpty()){detected=hint;break;}continue;
                        }
                        String type=connection.getContentType();if(type!=null){String lower=type.toLowerCase(Locale.ROOT);if(lower.contains("mpegurl")||lower.contains("m3u8"))detected=MimeTypes.APPLICATION_M3U8;else if(lower.contains("dash+xml"))detected=MimeTypes.APPLICATION_MPD;}break;
                    }finally{connection.disconnect();}
                }
            }catch(Exception ignored){}
            String resolvedMime=detected;
            runOnUiThread(()->{resolvingStreamMime=false;if(!resolvedMime.isEmpty()&&playbackContextActive()&&!isFinishing()&&!isDestroyed()){mime=resolvedMime;position=0;resumePlayback=true;releasePlayer();startPlayer();}else showError(originalError.getErrorCodeName()+"\nKhông tự nhận diện được định dạng sau chuyển hướng.");});
        });
    }
    private void scheduleRecovery(String reason,boolean fromEnd){
        if(!playbackContextActive()||isFinishing()||isDestroyed())return;
        recoveryHandler.removeCallbacks(resetRecoveryAttempts);
        if(recoveryAttempts>=MAX_RECOVERY_ATTEMPTS){showError(reason+". Đã thử nối lại "+MAX_RECOVERY_ATTEMPTS+" lần.\nBấm thử lại để tiếp tục.");return;}
        int attempt=++recoveryAttempts;
        if(fromEnd)position=0;else rememberPosition();
        resumePlayback=true;
        long delay=attempt==1?350L:attempt==2?800L:attempt==3?1800L:3500L;
        status.setText(reason+" • đang giữ phiên, thử lại lần "+attempt+"…");
        recoveryHandler.removeCallbacksAndMessages(null);
        recoveryHandler.postDelayed(()->{
            if(!playbackContextActive()||isFinishing()||isDestroyed())return;
            ExoPlayer active=player;
            if(attempt<=2&&active!=null){
                try{
                    if(fromEnd)active.seekToDefaultPosition();
                    active.prepare();
                    active.play();
                    return;
                }catch(RuntimeException ignored){}
            }
            releasePlayer();startPlayer();
        },delay);
    }
    private void chooseQuality(){String[] l={"Tự động / tối đa theo thiết bị","Full HD — tối đa 1080p","2K/QHD — tối đa 1440p","4K UHD — tối đa 2160p"};int[] h={Integer.MAX_VALUE,1080,1440,2160};new AlertDialog.Builder(this).setTitle("Giới hạn chất lượng").setItems(l,(d,i)->{quality=h[i];applyQuality();}).setNegativeButton("Đóng",null).show();}
    private void applyQuality(){if(player==null)return;int w=quality==Integer.MAX_VALUE?Integer.MAX_VALUE:quality*16/9;player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setViewportSize(Integer.MAX_VALUE,Integer.MAX_VALUE,true).setMaxVideoSize(w,quality).build());}
    private void chooseFormat(){String[] l={"Tự nhận diện","HLS / M3U8","DASH / MPD","SmoothStreaming","MPEG-TS"};String[] t={"",MimeTypes.APPLICATION_M3U8,MimeTypes.APPLICATION_MPD,MimeTypes.APPLICATION_SS,MimeTypes.VIDEO_MP2T};new AlertDialog.Builder(this).setTitle("Định dạng nguồn (khi URL không có đuôi)").setItems(l,(d,i)->{mime=t[i];position=0;resumePlayback=true;releasePlayer();startPlayer();}).setNegativeButton("Đóng",null).show();}
    private void chooseOrientation(){String[] l={"Tự động theo điện thoại","Màn hình ngang","Màn hình dọc"};int[] v={ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT};new AlertDialog.Builder(this).setTitle("Xoay màn hình khi xem").setItems(l,(d,i)->setRequestedOrientation(v[i])).setNegativeButton("Đóng",null).show();}
    private void chooseResizeMode(){String[] l={"Vừa màn hình (Fit)","Phóng đầy màn hình (Zoom)","Kéo đầy khung (Fill)"};int[] v={AspectRatioFrameLayout.RESIZE_MODE_FIT,AspectRatioFrameLayout.RESIZE_MODE_ZOOM,AspectRatioFrameLayout.RESIZE_MODE_FILL};new AlertDialog.Builder(this).setTitle("Tỷ lệ và khung hình").setItems(l,(d,i)->{resizeMode=v[i];playerView.setResizeMode(resizeMode);}).setNegativeButton("Đóng",null).show();}
    private void configureDrm(){LinearLayout f=new LinearLayout(this);f.setOrientation(LinearLayout.VERTICAL);f.setPadding(32,8,32,0);android.widget.Spinner type=new android.widget.Spinner(this);String[] values={"Widevine","ClearKey","PlayReady (Android TV)"};type.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));if("clearkey".equals(drmSystem))type.setSelection(1);if("playready".equals(drmSystem))type.setSelection(2);EditText license=new EditText(this);license.setHint("URL giấy phép hoặc ClearKey KID:KEY");license.setSingleLine(false);license.setMaxLines(4);license.setText(drmLicense);f.addView(type);f.addView(license);new AlertDialog.Builder(this).setTitle("DRM do nhà cung cấp cấp").setMessage("Không nhập khóa hoặc giấy phép bạn không có quyền sử dụng. Dữ liệu này chỉ giữ trong màn hình phát hiện tại.").setView(f).setPositiveButton("Áp dụng",(d,w)->{drmSystem=new String[]{"widevine","clearkey","playready"}[type.getSelectedItemPosition()];drmLicense=license.getText().toString().trim();position=0;resumePlayback=true;releasePlayer();startPlayer();}).setNeutralButton("Tắt DRM",(d,w)->{drmSystem="";drmLicense="";releasePlayer();startPlayer();}).setNegativeButton("Đóng",null).show();}
    private void loadQuickChannels(){SessionStore.IO.execute(()->{try{SessionStore.State s=SessionStore.load(getApplicationContext());runOnUiThread(()->{if(isFinishing()||isDestroyed())return;quickChannels.clear();if(s!=null)quickChannels.addAll(s.result.channels);rebuildQuickGroups();filterQuickChannels();});}catch(Exception ignored){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())filterQuickChannels();});}});}
    private void rebuildQuickGroups(){quickChannelsByGroup.clear();quickGroups.clear();quickGroups.add("");for(Channel c:quickChannels){String group=c.group();List<Channel> values=quickChannelsByGroup.get(group);if(values==null){values=new ArrayList<>();quickChannelsByGroup.put(group,values);quickGroups.add(group);}values.add(c);}if(!quickGroups.contains(quickGroup))quickGroup="";quickCurrentId=currentChannelId();quickGroupRow.removeAllViews();addQuickGroup("Tất cả","");for(String group:quickChannelsByGroup.keySet())addQuickGroup(group,group);updateQuickGroupButtons();}
    private void setupQuickGroupSwipe(View scroller){if(scroller==null||AppPreferences.isTvInterface(this))return;scroller.setLayerType(View.LAYER_TYPE_HARDWARE,null);scroller.setOnTouchListener((v,event)->{int action=event.getActionMasked();if(action==android.view.MotionEvent.ACTION_DOWN){quickSwipeStartX=event.getX();quickSwipeStartY=event.getY();quickSwipeStartTime=android.os.SystemClock.elapsedRealtime();}else if(action==android.view.MotionEvent.ACTION_UP){float dx=event.getX()-quickSwipeStartX,dy=event.getY()-quickSwipeStartY;long elapsed=android.os.SystemClock.elapsedRealtime()-quickSwipeStartTime;if(elapsed<800&&Math.abs(dx)>dp(56)&&Math.abs(dx)>Math.abs(dy)*1.35f)selectQuickGroupBySwipe(dx<0?1:-1);}return false;});}
    private void selectQuickGroupBySwipe(int delta){int current=quickGroups.indexOf(quickGroup);if(current<0)current=0;int next=Math.max(0,Math.min(quickGroups.size()-1,current+delta));if(next==current)return;quickGroup=quickGroups.get(next);updateQuickGroupButtons();filterQuickChannels();View active=quickGroupRow.getChildAt(next);View parent=(View)quickGroupRow.getParent();if(active!=null&&parent instanceof android.widget.HorizontalScrollView){int target=Math.max(0,active.getLeft()-(parent.getWidth()-active.getWidth())/2);((android.widget.HorizontalScrollView)parent).smoothScrollTo(target,0);}}


    private void setupMobileEdgeGestures(){
        if(AppPreferences.isTvInterface(this))return;
        audioManager=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);
        gestureFeedback=new TextView(this);gestureFeedback.setTextColor(android.graphics.Color.WHITE);gestureFeedback.setTextSize(18);gestureFeedback.setGravity(android.view.Gravity.CENTER);gestureFeedback.setPadding(dp(20),dp(12),dp(20),dp(12));gestureFeedback.setBackgroundResource(R.drawable.panel);gestureFeedback.setVisibility(View.GONE);gestureFeedback.setElevation(dp(20));
        android.widget.FrameLayout root=findViewById(R.id.playerRoot);android.widget.FrameLayout.LayoutParams feedbackParams=new android.widget.FrameLayout.LayoutParams(android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,android.view.Gravity.CENTER);root.addView(gestureFeedback,feedbackParams);
        playerView.setOnTouchListener((view,event)->{
            int action=event.getActionMasked();
            if(action==android.view.MotionEvent.ACTION_DOWN){
                if(quickPanel!=null&&quickPanel.getVisibility()==View.VISIBLE)return false;
                float x=event.getX(),width=Math.max(1f,view.getWidth());gestureMode=x<width*.3f?1:x>width*.7f?2:0;if(gestureMode==0)return false;
                gestureStartY=event.getY();
                if(gestureMode==1){float current=getWindow().getAttributes().screenBrightness;gestureStartBrightness=current<0?0.5f:current;}
                else gestureStartVolume=audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
                return true;
            }
            if(gestureMode==0)return false;
            if(action==android.view.MotionEvent.ACTION_MOVE){
                float delta=(gestureStartY-event.getY())/Math.max(1f,view.getHeight())*1.35f;
                if(gestureMode==1){float value=Math.max(.05f,Math.min(1f,gestureStartBrightness+delta));WindowManager.LayoutParams params=getWindow().getAttributes();params.screenBrightness=value;getWindow().setAttributes(params);showGestureFeedback("☀  Độ sáng "+Math.round(value*100)+"%");}
                else{int max=audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);int value=Math.max(0,Math.min(max,Math.round(gestureStartVolume+delta*max)));audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,value,0);showGestureFeedback("🔊  Âm lượng "+Math.round(value*100f/Math.max(1,max))+"%");}
                return true;
            }
            if(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL){gestureMode=0;gestureFeedback.removeCallbacks(hideGestureFeedback);gestureFeedback.postDelayed(hideGestureFeedback,650);return true;}
            return true;
        });
    }
    private final Runnable hideGestureFeedback=()->{if(gestureFeedback!=null)gestureFeedback.setVisibility(View.GONE);};
    private void showGestureFeedback(String text){gestureFeedback.removeCallbacks(hideGestureFeedback);gestureFeedback.setText(text);gestureFeedback.setVisibility(View.VISIBLE);}
    private void addQuickGroup(String l,String v){Button b=new Button(this);b.setTag(v);b.setText(l);b.setAllCaps(false);b.setTextSize(12);b.setSingleLine(true);b.setFocusable(true);b.setFocusableInTouchMode(false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,dp(44));p.setMarginEnd(dp(7));quickGroupRow.addView(b,p);b.setOnClickListener(x->{quickGroup=(String)v;updateQuickGroupButtons();filterQuickChannels();if(quickAdapter.getCount()>0)quickChannelList.requestFocus();});}
    private void updateQuickGroupButtons(){for(int i=0;i<quickGroupRow.getChildCount();i++){View c=quickGroupRow.getChildAt(i);boolean a=quickGroup.equals(c.getTag());c.setSelected(a);c.setBackgroundResource(a?R.drawable.button_primary:R.drawable.button_secondary);if(c instanceof Button)((Button)c).setTextColor(getColor(a?R.color.navy:R.color.text_primary));}}
    private void filterQuickChannels(){List<Channel> f=quickGroup.isEmpty()?quickChannels:quickChannelsByGroup.get(quickGroup);if(f==null)f=java.util.Collections.emptyList();if(quickCurrentId.isEmpty())quickCurrentId=currentChannelId();quickAdapter.submit(f,quickCurrentId);int cur=0;for(int i=0;i<f.size();i++)if(AppPreferences.id(f.get(i)).equals(quickCurrentId)){cur=i;break;}if(!f.isEmpty())quickChannelList.setSelection(cur);}
    private String currentChannelId(){for(Channel c:quickChannels)if(isCurrentChannel(c))return AppPreferences.id(c);return "";}
    private boolean isCurrentChannel(Channel c){if(!c.url().equals(url)||!c.options().equals(options))return false;if(currentHeaders.size()!=c.headers().size())return false;for(Map.Entry<String,String> e:c.headers().entrySet())if(!e.getValue().equals(currentHeaders.getString(e.getKey())))return false;return true;}
    private void showQuickChannels(){quickPanel.setVisibility(View.VISIBLE);filterQuickChannels();if(quickAdapter.getCount()>0)quickChannelList.post(()->quickChannelList.requestFocus());else if(quickGroupRow.getChildCount()>0)quickGroupRow.getChildAt(0).requestFocus();}
    private void hideQuickChannels(){quickPanel.setVisibility(View.GONE);playerView.requestFocus();}
    private void switchChannel(Channel c){
        if(c==null)return;
        hideQuickChannels();
        if(liveEvent!=null&&liveEvent.hasSourceUrl(c.url())){
            liveEvent=liveEvent.withPreferredUrl(c.url());
            LivePlaybackSession.setActive(liveEvent);
            LiveEventStore.setSelectedUrl(this,liveEvent.id(),c.url());
        }else{
            LivePlaybackSession.clearIfNotLiveSource(c);
            liveEvent=null;
        }
        if(isCurrentChannel(c)){
            updateLiveServerButton();
            return;
        }
        name=c.name();url=c.url();mime=c.mimeHint();options=new ArrayList<>(c.options());
        if(liveEvent!=null){
            liveEvent=liveEvent.withPreferredUrl(url);
            LivePlaybackSession.setActive(liveEvent);
        }
        updateLiveServerButton();
        DrmSpec d=DrmSpec.fromOptions(options);drmSystem=d.system;drmLicense=d.license;
        currentHeaders=new Bundle();for(Map.Entry<String,String> e:c.headers().entrySet())currentHeaders.putString(e.getKey(),e.getValue());
        getIntent().putExtra(EXTRA_NAME,name);getIntent().putExtra(EXTRA_URL,url);getIntent().putExtra(EXTRA_HEADERS,currentHeaders);
        getIntent().putExtra(EXTRA_MIME,mime);getIntent().putStringArrayListExtra(EXTRA_OPTIONS,options);
        if(liveEvent!=null)getIntent().putExtra(EXTRA_LIVE_EVENT,LivePlaybackSession.encode(liveEvent));
        else getIntent().removeExtra(EXTRA_LIVE_EVENT);
        ((TextView)findViewById(R.id.txtPlayerTitle)).setText(name);((TextView)findViewById(R.id.txtPlayerUrl)).setText(url);
        findViewById(R.id.playerError).setVisibility(View.GONE);AppPreferences.recordRecent(this,c);
        quickCurrentId=AppPreferences.id(c);position=0;resumePlayback=true;resolvingClearKey=false;
        resolvingStreamMime=false;recoveryAttempts=0;recoveryHandler.removeCallbacksAndMessages(null);
        releasePlayer();filterQuickChannels();startPlayer();
    }

    private void updateLiveServerButton(){
        View button=findViewById(R.id.btnLiveServer);
        if(button==null)return;
        if(liveEvent==null||liveEvent.sources().size()<2||!liveEvent.hasSourceUrl(url)){
            button.setVisibility(View.GONE);
            return;
        }
        liveEvent=liveEvent.withPreferredUrl(url);
        LivePlaybackSession.setActive(liveEvent);
        ((Button)button).setText("Server "+(liveEvent.selectedIndex()+1)+"/"+liveEvent.sources().size()+" ▾");
        button.setContentDescription("Đang chọn "+liveEvent.sourceLabel(liveEvent.selectedIndex())+". Nhấn để đổi máy chủ");
        button.setVisibility(View.VISIBLE);
    }

    private void showLiveServerPicker(){
        if(liveEvent==null||liveEvent.sources().size()<2)return;
        String[] labels=new String[liveEvent.sources().size()];
        for(int i=0;i<labels.length;i++){
            labels[i]=liveEvent.sourceLabel(i)+(i==liveEvent.selectedIndex()?"  ✓ Đang phát":"");
        }
        new AlertDialog.Builder(this).setTitle("Chọn máy chủ phát sóng")
                .setSingleChoiceItems(labels,liveEvent.selectedIndex(),(dialog,which)->{
                    if(which<0||which>=liveEvent.sources().size())return;
                    Channel source=liveEvent.sources().get(which);
                    LiveEventStore.setSelectedUrl(this,liveEvent.id(),source.url());
                    liveEvent=liveEvent.withPreferredUrl(source.url());
                    LivePlaybackSession.setActive(liveEvent);
                    dialog.dismiss();
                    switchChannel(source);
                    ((Button)findViewById(R.id.btnLiveServer)).setText("Server "+(liveEvent.selectedIndex()+1)+"/"+liveEvent.sources().size()+" ▾");
                }).setNegativeButton("Đóng",null).show();
    }
    private boolean switchRelative(int delta){for(int i=0;i<quickChannels.size();i++)if(isCurrentChannel(quickChannels.get(i))){switchChannel(quickChannels.get((i+delta+quickChannels.size())%quickChannels.size()));playerView.hideController();return true;}return false;}
    @Override public boolean dispatchKeyEvent(KeyEvent event){int key=event.getKeyCode();if(event.getAction()==KeyEvent.ACTION_UP&&key==consumedRemoteKey){consumedRemoteKey=KeyEvent.KEYCODE_UNKNOWN;return true;}if(event.getAction()==KeyEvent.ACTION_DOWN){if(event.getRepeatCount()>0&&key==consumedRemoteKey)return true;boolean panel=quickPanel!=null&&quickPanel.getVisibility()==View.VISIBLE;if(key==KeyEvent.KEYCODE_BACK&&panel){hideQuickChannels();consumedRemoteKey=key;return true;}if(AppPreferences.isTvInterface(this)&&!panel){boolean controller=playerView.isControllerFullyVisible();boolean handled=false;if(key==KeyEvent.KEYCODE_BACK&&controller){playerView.hideController();playerView.requestFocus();handled=true;}else if((key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER)&&!controller){playerView.showController();handled=true;}else if(!controller&&key==KeyEvent.KEYCODE_DPAD_LEFT){showQuickChannels();handled=true;}else if(!controller&&(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN)){handled=switchRelative(key==KeyEvent.KEYCODE_DPAD_UP?1:-1);}else if(key==KeyEvent.KEYCODE_MENU){AppPreferences.setShowPlayerSource(this,!AppPreferences.showPlayerSource(this));findViewById(R.id.txtPlayerUrl).setVisibility(AppPreferences.showPlayerSource(this)?View.VISIBLE:View.GONE);handled=true;}if(handled){consumedRemoteKey=key;return true;}}}return super.dispatchKeyEvent(event);}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);} private void acquireMulticast(){WifiManager wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);if(wifi!=null){multicastLock=wifi.createMulticastLock("iptv-stream");multicastLock.setReferenceCounted(false);multicastLock.acquire();}}
    private void showSource(){TextView v=new TextView(this);v.setText(url);v.setTextIsSelectable(true);v.setPadding(24,16,24,16);android.widget.ScrollView s=new android.widget.ScrollView(this);s.addView(v);new AlertDialog.Builder(this).setTitle("URL nguồn").setView(s).setPositiveButton("Đóng",null).show();}
    private void showError(String m){findViewById(R.id.playerError).setVisibility(View.VISIBLE);((TextView)findViewById(R.id.txtPlayerError)).setText(m);playerView.showController();} private String value(String k){String v=getIntent().getStringExtra(k);return v==null?"":v;}
    private void rememberPosition(){
        if(player!=null){
            position=player.isCurrentMediaItemLive()?0:player.getCurrentPosition();
            resumePlayback=player.getPlayWhenReady();
        }
        if(url!=null&&!url.isEmpty()){
            SharedPlaybackSession.saveIptv(this,name,url,mime,currentHeaders,options,position,resumePlayback);
        }
    }

    public static void prepareForYoutubeHandoff(android.content.Context context){
        PlayerActivity activity=currentInstance;
        if(activity==null||activity.isFinishing()||activity.isDestroyed()) return;
        // Merely opening the YouTube tab must not stop IPTV. IPTV remains the active
        // player until SmartTube PlaybackActivity actually starts a video.
        activity.rememberPosition();
        // Explicitly mark the outgoing IPTV Activity. Do not rely on lifecycle callback
        // ordering when Android moves BrowseActivity to the foreground.
        activity.keepPlayerForTabSwitch = true;
        activity.backgroundPlaybackActive=false;
        System.setProperty(YOUTUBE_HANDOFF_PROPERTY, "1");
        SharedPlaybackSession.setTab(activity,SharedPlaybackSession.TAB_YOUTUBE);
    }
    public static boolean isYoutubeHandoffPending(){
        return "1".equals(System.getProperty(YOUTUBE_HANDOFF_PROPERTY, "0"));
    }

    public static void releaseForYoutube() {
        PlayerActivity active = currentInstance;
        if (active == null) return;
        active.rememberPosition();
        active.recoveryHandler.removeCallbacksAndMessages(null);
        active.backgroundPlaybackActive = false;
        active.releasePlayer();
        cancelYoutubeHandoff();
        active.stopService(new android.content.Intent(active, BackgroundPlaybackService.class));
    }

    public static void cancelYoutubeHandoff(){
        System.setProperty(YOUTUBE_HANDOFF_PROPERTY, "0");
    }

    @Override protected void onSaveInstanceState(Bundle out){rememberPosition();out.putLong("position",position);out.putBoolean("playing",resumePlayback);out.putInt("quality",quality);out.putInt("resize",resizeMode);out.putString("mime",mime);super.onSaveInstanceState(out);} @Override protected void onStop(){activityStarted=false;fpsHandler.removeCallbacks(fpsUpdate);clockHandler.removeCallbacks(clockUpdate);rememberPosition();if(!isFinishing()&&(isYoutubeHandoffPending()||SharedPlaybackSession.TAB_YOUTUBE.equals(SharedPlaybackSession.tab(this)))){keepPlayerForTabSwitch=true;}if(MobileNm7Application.isTabSwitchPending()){keepPlayerForTabSwitch=true;}if(!backgroundPlaybackActive&&!keepPlayerForTabSwitch){recoveryHandler.removeCallbacksAndMessages(null);releasePlayer();}super.onStop();}
    private void releasePlayer(){bufferingSinceMs=0;recoveryHandler.removeCallbacks(stalledPlaybackCheck);if(player!=null){playerView.setPlayer(null);player.release();player=null;}videoCounters=null;fpsMeter.reset();if(multicastLock!=null){if(multicastLock.isHeld())multicastLock.release();multicastLock=null;}} @Override protected void onDestroy(){boolean preserveForYoutube=!isFinishing()&&(keepPlayerForTabSwitch||isYoutubeHandoffPending()||SharedPlaybackSession.TAB_YOUTUBE.equals(SharedPlaybackSession.tab(this)));if(currentInstance==this) currentInstance=null; drmIo.shutdownNow();if(!preserveForYoutube&&(isFinishing()||!backgroundPlaybackActive)){stopService(new android.content.Intent(this,BackgroundPlaybackService.class));releasePlayer();}super.onDestroy();}
}
