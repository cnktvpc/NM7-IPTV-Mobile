"""NM7 Mobile 1.10.84: real status bar, live-chat composer/send, channel avatars, poster-first startup."""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
PHONE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
PLAYBACK = PHONE / "playback/PlaybackActivity.java"
LAYOUT = ROOT / "smarttubedroid/src/main/res/layout/playback_activity.xml"
MEDIA = ROOT / "MediaServiceCore"

def once(s, old, new, label):
    if s.count(old) != 1:
        raise SystemExit(f"v84: expected exactly one {label}, found {s.count(old)}")
    return s.replace(old, new, 1)

# ---------------------------------------------------------------------------
# 1) Status bar: DroidActivity.onResume() calls applySystemBars() after the player
# asks for bars. Replace any existing PlaybackActivity override in-place instead
# of adding a duplicate method.
s = PLAYBACK.read_text(encoding="utf-8")
status_method = """    @Override
    protected void applySystemBars() {
        // NM7 1.10.84: DroidActivity.onResume() used to hide the status bar again
        // after portrait playback had already requested it. Keep the base nav-bar
        // handling, then explicitly restore the phone status bar in portrait.
        super.applySystemBars();
        if (!isLandscape()) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            if (VERSION.SDK_INT >= 30) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.show(WindowInsets.Type.statusBars());
                }
            } else {
                int flags = getWindow().getDecorView().getSystemUiVisibility();
                flags &= ~(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                getWindow().getDecorView().setSystemUiVisibility(flags | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
            if (mRoot != null) mRoot.post(() -> mRoot.requestApplyInsets());
        }
    }
"""
if "NM7 1.10.84: DroidActivity.onResume()" not in s:
    sig = "    protected void applySystemBars() {"
    pos = s.find(sig)
    if pos >= 0:
        method_start = s.rfind("    @Override\n", 0, pos)
        if method_start < 0 or pos - method_start > 80:
            method_start = pos
        brace = s.find("{", pos)
        depth = 0
        method_end = -1
        for i in range(brace, len(s)):
            if s[i] == "{":
                depth += 1
            elif s[i] == "}":
                depth -= 1
                if depth == 0:
                    method_end = i + 1
                    break
        if method_end < 0:
            raise SystemExit("v84: existing applySystemBars closing brace not found")
        s = s[:method_start] + status_method + s[method_end:]
    else:
        anchor = "    private void applySystemUi(boolean fullscreen) {\n"
        if s.count(anchor) != 1:
            raise SystemExit(f"v84: expected one applySystemUi anchor, found {s.count(anchor)}")
        s = s.replace(anchor, status_method + "\n" + anchor, 1)

# ---------------------------------------------------------------------------
# 2) Poster-first player startup. Keep the selected video thumbnail visible until
# ExoPlayer renders its first frame; suppress only the initial black spinner.
field_anchor = """    private ProgressBar mProgressBar;
"""
field_repl = """    private ProgressBar mProgressBar;
    private boolean mNm7FirstFrameRendered;
"""
if "mNm7FirstFrameRendered" not in s:
    s = once(s, field_anchor, field_repl, "first-frame field")

listener_anchor = """            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
"""
# Add onRenderedFirstFrame before listener closes, using a stable anchor after pending progress logic.
listener_tail = """                if (mProgressHidePending && !isPlayerBuffering()) {
                    showProgressBar(false);
                }
            }
        });
"""
listener_tail_repl = """                if (mProgressHidePending && !isPlayerBuffering()) {
                    showProgressBar(false);
                }
            }

            @Override
            public void onRenderedFirstFrame() {
                mNm7FirstFrameRendered = true;
                if (mBackgroundView != null) {
                    Glide.with(PlaybackActivity.this).clear(mBackgroundView);
                    mBackgroundView.setImageDrawable(null);
                    mBackgroundView.setVisibility(View.GONE);
                }
                if (mProgressBar != null) mProgressBar.setVisibility(View.GONE);
            }
        });
"""
if "public void onRenderedFirstFrame()" not in s:
    s = once(s, listener_tail, listener_tail_repl, "first rendered frame listener")

setvideo_sig = "    public void setVideo(Video item) {\n"
if "NM7 1.10.84: show the selected video's poster immediately" not in s:
    count = s.count(setvideo_sig)
    if count != 1:
        raise SystemExit(f"v84: expected exactly one setVideo signature, found {count}")
    poster = """    public void setVideo(Video item) {
        // NM7 1.10.84: show the selected video's poster immediately, before network
        // format resolution/decoder startup. It is removed only on first rendered frame.
        mNm7FirstFrameRendered = false;
        if (item != null && mBackgroundView != null && !TextUtils.isEmpty(item.getCardImageUrl())) {
            String nm7PosterUrl = item.getCardImageUrl();
            if (nm7PosterUrl.contains("ytimg.com/")) {
                nm7PosterUrl = nm7PosterUrl
                        .replace("/default.jpg", "/maxresdefault.jpg")
                        .replace("/mqdefault.jpg", "/maxresdefault.jpg")
                        .replace("/hqdefault.jpg", "/maxresdefault.jpg")
                        .replace("/sddefault.jpg", "/maxresdefault.jpg");
            }
            mBackgroundView.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(nm7PosterUrl)
                    .error(Glide.with(this).load(item.getCardImageUrl()))
                    .into(mBackgroundView);
        }
"""
    s = s.replace(setvideo_sig, poster, 1)

progress_anchor = """            mProgressHidePending = false;
            mProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
"""
progress_repl = """            mProgressHidePending = false;
            // Initial startup uses the actual video poster instead of a black screen +
            // spinner. Rebuffering after the first frame still shows the spinner.
            boolean nm7PosterVisible = !mNm7FirstFrameRendered && mBackgroundView != null
                    && mBackgroundView.getVisibility() == View.VISIBLE;
            mProgressBar.setVisibility(show && !nm7PosterVisible ? View.VISIBLE : View.GONE);
"""
if "Initial startup uses the actual video poster" not in s:
    s = once(s, progress_anchor, progress_repl, "initial spinner suppression")

PLAYBACK.write_text(s, encoding="utf-8")

# ---------------------------------------------------------------------------
# 3) Channel avatar parser. YouTube's channelThumbnail.thumbnails[0] is an object,
# not a String. Read its url at the parser level, then the existing v83 propagation
# (VideoItem -> YouTubeMediaItem -> Video -> card) receives real data.
video_item = MEDIA / "youtubeapi/src/main/java/com/liskovsoft/youtubeapi/common/models/items/VideoItem.java"
v = video_item.read_text(encoding="utf-8")
v = v.replace(
    '@JsonPath("$.channelThumbnail.thumbnails[0]")\n    private String mChannelThumbnail;',
    '@JsonPath({"$.channelThumbnail.thumbnails[0].url",\n'
    '               "$.channelThumbnailSupportedRenderers.channelThumbnailWithLinkRenderer.thumbnail.thumbnails[0].url",\n'
    '               "$.decoratedAvatarViewModel.avatar.avatarViewModel.image.sources[0].url"})\n'
    '    private String mChannelThumbnail;',
    1,
)
if 'channelThumbnail.thumbnails[0].url' not in v:
    raise SystemExit("v84: channel avatar parser path was not corrected")
video_item.write_text(v, encoding="utf-8")

# ---------------------------------------------------------------------------
# 4) Live-chat send support using YouTube's server-provided send params.
iface = MEDIA / "mediaserviceinterfaces/src/main/java/com/liskovsoft/mediaserviceinterfaces/LiveChatService.java"
x = iface.read_text(encoding="utf-8")
if "sendLiveChatMessageObserve" not in x:
    x = once(
        x,
        "    Observable<ChatItem> openLiveChatObserve(String chatKey);\n",
        "    Observable<ChatItem> openLiveChatObserve(String chatKey);\n"
        "    Observable<Boolean> sendLiveChatMessageObserve(String message);\n"
        "    boolean canSendLiveChatMessage();\n",
        "LiveChatService send methods",
    )
iface.write_text(x, encoding="utf-8")

result = MEDIA / "youtubeapi/src/main/java/com/liskovsoft/youtubeapi/chat/gen/LiveChatResult.kt"
x = result.read_text(encoding="utf-8")
old = """        data class LiveChatContinuation(
            val continuations: List<ContinuationItem?>?,
            val actions: List<LiveChatAction?>?
        )
"""
new = """        data class LiveChatContinuation(
            val continuations: List<ContinuationItem?>?,
            val actions: List<LiveChatAction?>?,
            val actionPanel: ActionPanel?
        ) {
            data class ActionPanel(
                val liveChatMessageInputRenderer: LiveChatMessageInputRenderer?
            ) {
                data class LiveChatMessageInputRenderer(
                    val sendButton: SendButton?
                ) {
                    data class SendButton(
                        val buttonRenderer: ButtonRenderer?
                    ) {
                        data class ButtonRenderer(
                            val isDisabled: Boolean?,
                            val serviceEndpoint: ServiceEndpoint?
                        ) {
                            data class ServiceEndpoint(
                                val sendLiveChatMessageEndpoint: SendLiveChatMessageEndpoint?
                            ) {
                                data class SendLiveChatMessageEndpoint(
                                    val params: String?
                                )
                            }
                        }
                    }
                }
            }
        }
"""
if "SendLiveChatMessageEndpoint" not in x:
    x = once(x, old, new, "LiveChatResult actionPanel")
result.write_text(x, encoding="utf-8")

helper = MEDIA / "youtubeapi/src/main/java/com/liskovsoft/youtubeapi/chat/gen/LiveChatHelper.kt"
x = helper.read_text(encoding="utf-8")
if "getSendMessageParams" not in x:
    x += """\ninternal fun LiveChatResult.getSendMessageParams(): String? =
    continuationContents?.liveChatContinuation?.actionPanel
        ?.liveChatMessageInputRenderer?.sendButton?.buttonRenderer
        ?.takeUnless { it.isDisabled == true }
        ?.serviceEndpoint?.sendLiveChatMessageEndpoint?.params
"""
helper.write_text(x, encoding="utf-8")

api = MEDIA / "youtubeapi/src/main/java/com/liskovsoft/youtubeapi/chat/LiveChatApi.kt"
x = api.read_text(encoding="utf-8")
if "fun sendLiveChatMessage" not in x:
    x = x.replace("import retrofit2.http.POST\n", "import retrofit2.http.POST\nimport okhttp3.ResponseBody\n", 1)
    x = x.replace(
        """    fun getLiveChat(@Body chatQuery: String?): Call<LiveChatResult?>
}""",
        """    fun getLiveChat(@Body chatQuery: String?): Call<LiveChatResult?>

    @Headers("Content-Type: application/json")
    @POST("https://www.youtube.com/youtubei/v1/live_chat/send_message")
    fun sendLiveChatMessage(@Body chatQuery: String?): Call<ResponseBody?>
}""",
        1,
    )
api.write_text(x, encoding="utf-8")

params = MEDIA / "youtubeapi/src/main/java/com/liskovsoft/youtubeapi/chat/LiveChatApiParams.kt"
x = params.read_text(encoding="utf-8")
if "getSendMessageQuery" not in x:
    x = x.replace(
        "import com.liskovsoft.youtubeapi.common.helpers.PostDataHelper\n",
        "import com.liskovsoft.youtubeapi.common.helpers.PostDataHelper\n"
        "import com.google.gson.Gson\n"
        "import java.util.UUID\n",
        1,
    )
    x = x.replace(
        """    fun getLiveChatQuery(chatKey: String): String {
        val chatData = String.format("\\"continuation\\":\\"%s\\"", chatKey)
        return PostDataHelper.createQueryTV(chatData)
    }
}""",
        """    fun getLiveChatQuery(chatKey: String): String {
        val chatData = String.format("\\"continuation\\":\\"%s\\"", chatKey)
        return PostDataHelper.createQueryTV(chatData)
    }

    fun getSendMessageQuery(params: String, message: String): String {
        val gson = Gson()
        val sendData = "\\"params\\":" + gson.toJson(params) +
                ",\\"clientMessageId\\":" + gson.toJson(UUID.randomUUID().toString()) +
                ",\\"richMessage\\":{\\"textSegments\\":[{\\"text\\":" +
                gson.toJson(message) + "}]}"
        return PostDataHelper.createQueryTV(sendData)
    }
}""",
        1,
    )
params.write_text(x, encoding="utf-8")

service_int = MEDIA / "youtubeapi/src/main/java/com/liskovsoft/youtubeapi/chat/LiveChatServiceInt.kt"
x = service_int.read_text(encoding="utf-8")
if "mSendMessageParams" not in x:
    x = x.replace(
        "import com.liskovsoft.youtubeapi.chat.gen.getContinuation\n",
        "import com.liskovsoft.youtubeapi.chat.gen.getContinuation\n"
        "import com.liskovsoft.youtubeapi.chat.gen.getSendMessageParams\n",
        1,
    )
    x = x.replace(
        "    private val mApi = RetrofitHelper.create(LiveChatApi::class.java)\n",
        "    private val mApi = RetrofitHelper.create(LiveChatApi::class.java)\n"
        "    @Volatile private var mSendMessageParams: String? = null\n",
        1,
    )
    x = x.replace(
        "            val continuation = chatResult?.getContinuation()\n",
        "            chatResult?.getSendMessageParams()?.let { mSendMessageParams = it }\n"
        "            val continuation = chatResult?.getContinuation()\n",
        1,
    )
    insert = """\n    fun canSendLiveChatMessage(): Boolean = !mSendMessageParams.isNullOrEmpty()

    fun sendLiveChatMessage(message: String): Boolean {
        val params = mSendMessageParams ?: return false
        if (message.isBlank()) return false
        val response = RetrofitHelper.getResponse(
            mApi.sendLiveChatMessage(LiveChatApiParams.getSendMessageQuery(params, message.trim()))
        )
        return response?.isSuccessful == true
    }
"""
    x = x.replace("\n    interface OnChatItem {", insert + "\n    interface OnChatItem {", 1)
service_int.write_text(x, encoding="utf-8")

service = MEDIA / "youtubeapi/src/main/java/com/liskovsoft/youtubeapi/service/YouTubeLiveChatService.java"
x = service.read_text(encoding="utf-8")
if "sendLiveChatMessageObserve" not in x:
    x = x.replace(
        """    @Override
    public Observable<ChatItem> openLiveChatObserve(String chatKey) {""",
        """    @Override
    public Observable<Boolean> sendLiveChatMessageObserve(String message) {
        return Observable.fromCallable(() -> mLiveChatServiceInt.sendLiveChatMessage(message));
    }

    @Override
    public boolean canSendLiveChatMessage() {
        return mLiveChatServiceInt.canSendLiveChatMessage();
    }

    @Override
    public Observable<ChatItem> openLiveChatObserve(String chatKey) {""",
        1,
    )
service.write_text(x, encoding="utf-8")

# Chat composer UI.
layout = LAYOUT.read_text(encoding="utf-8")
old_footer = """            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="56dp"
                android:gravity="center_vertical"
                android:paddingStart="12dp"
                android:paddingEnd="12dp">

                <TextView
                    android:layout_width="match_parent"
                    android:layout_height="40dp"
                    android:background="#F0F0F0"
                    android:gravity="center_vertical"
                    android:paddingStart="16dp"
                    android:paddingEnd="16dp"
                    android:text="Trò chuyện trực tiếp chỉ hỗ trợ xem trên bản Mobile này"
                    android:textColor="#888888"
                    android:textSize="13sp" />
            </LinearLayout>
"""
new_footer = """            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="60dp"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingStart="10dp"
                android:paddingEnd="10dp">

                <EditText
                    android:id="@+id/nm7_live_chat_input"
                    android:layout_width="0dp"
                    android:layout_height="44dp"
                    android:layout_weight="1"
                    android:background="#F0F0F0"
                    android:hint="Trò chuyện..."
                    android:imeOptions="actionSend"
                    android:inputType="textCapSentences"
                    android:maxLength="200"
                    android:maxLines="2"
                    android:paddingStart="14dp"
                    android:paddingEnd="14dp"
                    android:textColor="#111111"
                    android:textColorHint="#777777"
                    android:textSize="14sp" />

                <TextView
                    android:id="@+id/nm7_live_chat_send"
                    android:layout_width="64dp"
                    android:layout_height="44dp"
                    android:layout_marginStart="8dp"
                    android:background="#F1F1F1"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center"
                    android:text="Gửi"
                    android:textColor="#065FD4"
                    android:textSize="14sp"
                    android:textStyle="bold" />
            </LinearLayout>
"""
if 'android:id="@+id/nm7_live_chat_input"' not in layout:
    layout = once(layout, old_footer, new_footer, "chat read-only footer")
LAYOUT.write_text(layout, encoding="utf-8")

s = PLAYBACK.read_text(encoding="utf-8")
if "import android.widget.EditText;" not in s:
    s = s.replace("import android.widget.FrameLayout;\n", "import android.widget.FrameLayout;\nimport android.widget.EditText;\n", 1)
if "import android.view.inputmethod.EditorInfo;" not in s:
    s = s.replace("import android.view.WindowManager;\n", "import android.view.WindowManager;\nimport android.view.inputmethod.EditorInfo;\n", 1)
if "import com.liskovsoft.mediaserviceinterfaces.LiveChatService;" not in s:
    s = s.replace("import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;\n",
                  "import com.liskovsoft.mediaserviceinterfaces.LiveChatService;\nimport com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;\n", 1)

if "mNm7LiveChatInput" not in s:
    s = s.replace(
        "    private ChatReceiver mNm7ChatReceiver;\n",
        "    private ChatReceiver mNm7ChatReceiver;\n"
        "    private EditText mNm7LiveChatInput;\n"
        "    private TextView mNm7LiveChatSend;\n"
        "    private io.reactivex.disposables.Disposable mNm7LiveChatSendAction;\n",
        1,
    )

init_anchor = """        mNm7LiveChatStatus = findViewById(R.id.nm7_live_chat_status);
"""
init_repl = """        mNm7LiveChatStatus = findViewById(R.id.nm7_live_chat_status);
        mNm7LiveChatInput = findViewById(R.id.nm7_live_chat_input);
        mNm7LiveChatSend = findViewById(R.id.nm7_live_chat_send);
        View.OnClickListener nm7SendChat = v -> nm7SendLiveChatMessage();
        mNm7LiveChatSend.setOnClickListener(nm7SendChat);
        mNm7LiveChatInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                nm7SendLiveChatMessage();
                return true;
            }
            return false;
        });
"""
if "View.OnClickListener nm7SendChat" not in s:
    s = once(s, init_anchor, init_repl, "chat composer init")

method_anchor = """    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) {
"""
send_method = """    private void nm7SendLiveChatMessage() {
        if (mNm7LiveChatInput == null || mNm7LiveChatSend == null) return;
        String message = mNm7LiveChatInput.getText().toString().trim();
        if (message.isEmpty()) return;

        LiveChatService service = YouTubeServiceManager.instance().getLiveChatService();
        if (service == null || !service.canSendLiveChatMessage()) {
            mNm7LiveChatStatus.setText("Chưa thể gửi chat - kiểm tra đăng nhập/quyền chat");
            return;
        }

        if (mNm7LiveChatSendAction != null && !mNm7LiveChatSendAction.isDisposed()) {
            mNm7LiveChatSendAction.dispose();
        }
        mNm7LiveChatSend.setEnabled(false);
        mNm7LiveChatStatus.setText("Đang gửi...");
        mNm7LiveChatSendAction = service.sendLiveChatMessageObserve(message)
                .subscribeOn(io.reactivex.schedulers.Schedulers.io())
                .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
                .subscribe(success -> {
                    mNm7LiveChatSend.setEnabled(true);
                    if (Boolean.TRUE.equals(success)) {
                        mNm7LiveChatInput.setText("");
                        mNm7LiveChatStatus.setText("Đã gửi");
                    } else {
                        mNm7LiveChatStatus.setText("Không gửi được - kiểm tra tài khoản YouTube");
                    }
                }, error -> {
                    mNm7LiveChatSend.setEnabled(true);
                    mNm7LiveChatStatus.setText("Gửi chat thất bại");
                    android.util.Log.e("NM7LiveChat", "send failed", error);
                });
    }

    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) {
"""
if "private void nm7SendLiveChatMessage()" not in s:
    s = once(s, method_anchor, send_method, "chat send method")

PLAYBACK.write_text(s, encoding="utf-8")
print("NM7 Mobile 1.10.84 status/avatar/poster/live-chat-send patch applied")
