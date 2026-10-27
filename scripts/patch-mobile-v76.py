"""NM7 Mobile 1.10.76: portrait system-bar-safe player + live-chat panel.

Scope is intentionally limited to two user-reported Mobile issues:
1) Keep the portrait YouTube player below the Android status bar instead of drawing
   edge-to-edge over the notification icons. The existing 16:9 player ratio is kept.
2) Wire SmartTube's existing LiveChatService -> ChatReceiver into the phone player
   and render the incoming live messages in a dedicated panel below the player.

No playback engine, thumbnail, navigation, IPTV, decoder recovery, or TV behavior is changed.
"""
from pathlib import Path

ROOT = Path("third_party/SmartTube-droid")
PHONE = ROOT / "smarttubedroid/src/main/java/com/liskovsoft/smartyoutubetv2/droid/ui"
PLAYBACK = PHONE / "playback/PlaybackActivity.java"
LAYOUT = ROOT / "smarttubedroid/src/main/res/layout/playback_activity.xml"
CHAT = ROOT / "common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/models/playback/controllers/ChatController.java"


def once(text, old, new, label):
    if text.count(old) != 1:
        raise SystemExit(f"v76: expected exactly one {label}")
    return text.replace(old, new, 1)


# 1. Portrait player starts below the Android status bar.
s = PLAYBACK.read_text(encoding="utf-8")
s = once(
    s,
    "            boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;\n",
    """            boolean isLandscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

            // NM7 1.10.76: keep portrait playback below the visible Android
            // status/notification bar. The player remains exactly 16:9; only
            // its top edge is inset. In fullscreen/landscape the system bars
            // are hidden and the original edge-to-edge behavior remains.
            if (!isLandscape) {
                ViewGroup.MarginLayoutParams playerLp =
                        (ViewGroup.MarginLayoutParams) mPlayerContainer.getLayoutParams();
                if (playerLp.topMargin != top) {
                    playerLp.topMargin = top;
                    mPlayerContainer.setLayoutParams(playerLp);
                }
            }
""",
    "portrait player inset anchor",
)

# 2. Dedicated live-chat panel below the player.
chat_layout = r'''
    <!-- NM7 1.10.76: live chat occupies the portrait area below the 16:9 player. -->
    <FrameLayout
        android:id="@+id/nm7_live_chat_panel"
        android:layout_width="0dp"
        android:layout_height="0dp"
        android:background="#FAFAFA"
        android:visibility="gone"
        android:elevation="8dp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@id/playback_player_container">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="64dp"
                android:background="#FFFFFF"
                android:gravity="center_vertical"
                android:orientation="horizontal"
                android:paddingStart="16dp"
                android:paddingEnd="8dp">

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    android:layout_weight="1"
                    android:gravity="center_vertical"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Trò chuyện trực tiếp"
                        android:textColor="#111111"
                        android:textSize="18sp"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Tin nhắn hàng đầu"
                        android:textColor="#777777"
                        android:textSize="12sp" />
                </LinearLayout>

                <TextView
                    android:id="@+id/nm7_live_chat_status"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginEnd="8dp"
                    android:text="Đang kết nối..."
                    android:textColor="#777777"
                    android:textSize="12sp" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="#E5E5E5" />

            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/nm7_live_chat_list"
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_weight="1"
                android:clipToPadding="false"
                android:paddingTop="4dp"
                android:paddingBottom="8dp"
                android:scrollbars="vertical" />

            <LinearLayout
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
        </LinearLayout>
    </FrameLayout>
'''
s_layout = LAYOUT.read_text(encoding="utf-8")
if 'android:id="@+id/nm7_live_chat_panel"' not in s_layout:
    marker = '\n</androidx.constraintlayout.widget.ConstraintLayout>'
    if s_layout.count(marker) != 1:
        raise SystemExit("v76: playback_activity.xml closing anchor missing")
    s_layout = s_layout.replace(marker, "\n" + chat_layout + marker, 1)
    LAYOUT.write_text(s_layout, encoding="utf-8")

# 3. Wire ChatReceiver into a lightweight RecyclerView adapter.
s = PLAYBACK.read_text(encoding="utf-8")
s = once(
    s,
    "import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;\n",
    "import com.liskovsoft.smartyoutubetv2.common.app.models.playback.ui.ChatReceiver;\n"
    "import com.liskovsoft.mediaserviceinterfaces.data.ChatItem;\n",
    "ChatItem import",
)
s = once(
    s,
    "    private CommentsAdapter mCommentsAdapter;\n",
    """    private CommentsAdapter mCommentsAdapter;
    private View mNm7LiveChatPanel;
    private RecyclerView mNm7LiveChatList;
    private TextView mNm7LiveChatStatus;
    private Nm7LiveChatAdapter mNm7LiveChatAdapter;
    private ChatReceiver mNm7ChatReceiver;
""",
    "live-chat fields",
)
s = once(
    s,
    "        mCommentsCard = findViewById(R.id.playback_comments_card);\n",
    """        mCommentsCard = findViewById(R.id.playback_comments_card);

        mNm7LiveChatPanel = findViewById(R.id.nm7_live_chat_panel);
        mNm7LiveChatList = findViewById(R.id.nm7_live_chat_list);
        mNm7LiveChatStatus = findViewById(R.id.nm7_live_chat_status);
        mNm7LiveChatAdapter = new Nm7LiveChatAdapter(this);
        mNm7LiveChatList.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        mNm7LiveChatList.setAdapter(mNm7LiveChatAdapter);
""",
    "live-chat view initialization",
)
s = once(
    s,
    "        final int descriptionPaddingBottom = mDetailsDescription.getPaddingBottom();\n",
    """        final int descriptionPaddingBottom = mDetailsDescription.getPaddingBottom();
        final int chatPaddingBottom = mNm7LiveChatList.getPaddingBottom();
""",
    "chat inset capture",
)
s = once(
    s,
    """            mDetailsDescription.setPadding(mDetailsDescription.getPaddingLeft(), mDetailsDescription.getPaddingTop(),
                    mDetailsDescription.getPaddingRight(), descriptionPaddingBottom + (isLandscape ? 0 : bottom));

            return insets;
""",
    """            mDetailsDescription.setPadding(mDetailsDescription.getPaddingLeft(), mDetailsDescription.getPaddingTop(),
                    mDetailsDescription.getPaddingRight(), descriptionPaddingBottom + (isLandscape ? 0 : bottom));

            mNm7LiveChatList.setPadding(mNm7LiveChatList.getPaddingLeft(), mNm7LiveChatList.getPaddingTop(),
                    mNm7LiveChatList.getPaddingRight(), chatPaddingBottom + (isLandscape ? 0 : bottom));

            return insets;
""",
    "chat bottom inset",
)

stub = """    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) {
        // v1: live chat isn't part of the phone player yet.
    }
"""
impl = """    @Override
    public void setChatReceiver(ChatReceiver chatReceiver) {
        runOnUiThread(() -> {
            if (mNm7ChatReceiver != null) {
                mNm7ChatReceiver.setCallback(null);
            }

            mNm7ChatReceiver = chatReceiver;
            mNm7LiveChatAdapter.clear();

            if (chatReceiver == null) {
                mNm7LiveChatPanel.setVisibility(View.GONE);
                mNm7LiveChatStatus.setText("Đang kết nối...");
                if (mPanel != null) mPanel.setVisibility(View.VISIBLE);
                return;
            }

            // A live chat stream replaces the metadata/suggestions panel in
            // portrait mode, matching the native YouTube phone layout.
            if (mPanel != null) mPanel.setVisibility(View.GONE);
            mNm7LiveChatPanel.setVisibility(View.VISIBLE);
            mNm7LiveChatStatus.setText("Đang xem trực tiếp");
            mNm7ChatReceiver.setCallback(chatItem -> runOnUiThread(() -> {
                mNm7LiveChatAdapter.add(chatItem);
                int last = mNm7LiveChatAdapter.getItemCount() - 1;
                if (last >= 0) mNm7LiveChatList.scrollToPosition(last);
                mNm7LiveChatStatus.setText("Đang xem trực tiếp");
            }));
        });
    }

"""
s = once(s, stub, impl, "setChatReceiver stub")

adapter_marker = "    // -------------------------------------------------------- PlayerManager\n"
adapter = r'''    private static final class Nm7LiveChatAdapter
            extends RecyclerView.Adapter<Nm7LiveChatAdapter.Holder> {
        private static final int MAX_ITEMS = 80;
        private final android.content.Context mContext;
        private final java.util.List<ChatItem> mItems = new java.util.ArrayList<>();

        Nm7LiveChatAdapter(android.content.Context context) {
            mContext = context;
        }

        void clear() {
            mItems.clear();
            notifyDataSetChanged();
        }

        void add(ChatItem item) {
            if (item == null || TextUtils.isEmpty(item.getMessage())) return;
            mItems.add(item);
            if (mItems.size() > MAX_ITEMS) {
                mItems.remove(0);
                notifyDataSetChanged();
            } else {
                notifyItemInserted(mItems.size() - 1);
            }
        }

        @Override
        public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(mContext);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(12, 6, 12, 6);

            ImageView avatar = new ImageView(mContext);
            avatar.setLayoutParams(new LinearLayout.LayoutParams(34, 34));
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(avatar);

            LinearLayout textBox = new LinearLayout(mContext);
            textBox.setOrientation(LinearLayout.VERTICAL);
            textBox.setPadding(10, 0, 0, 0);
            textBox.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView author = new TextView(mContext);
            author.setTextColor(0xFF555555);
            author.setTextSize(12);
            author.setMaxLines(1);

            TextView message = new TextView(mContext);
            message.setTextColor(0xFF222222);
            message.setTextSize(14);
            message.setPadding(0, 1, 0, 0);

            textBox.addView(author);
            textBox.addView(message);
            row.addView(textBox);

            return new Holder(row, avatar, author, message);
        }

        @Override
        public void onBindViewHolder(Holder holder, int position) {
            ChatItem item = mItems.get(position);
            holder.author.setText(item.getAuthorName());
            holder.message.setText(item.getMessage());

            String photo = item.getAuthorPhoto();
            if (!TextUtils.isEmpty(photo)) {
                Glide.with(mContext).load(photo).circleCrop()
                        .placeholder(R.drawable.playback_channel_placeholder)
                        .error(R.drawable.playback_channel_placeholder)
                        .into(holder.avatar);
            } else {
                holder.avatar.setImageResource(R.drawable.playback_channel_placeholder);
            }
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView avatar;
            final TextView author;
            final TextView message;

            Holder(View itemView, ImageView avatar, TextView author, TextView message) {
                super(itemView);
                this.avatar = avatar;
                this.author = author;
                this.message = message;
            }
        }
    }

'''
s = once(s, adapter_marker, adapter + adapter_marker, "live-chat adapter insertion")
PLAYBACK.write_text(s, encoding="utf-8")

# 4. Automatically enable the existing receive-only ChatController on Mobile.
cs = CHAT.read_text(encoding="utf-8")
cs = once(
    cs,
    """        mLiveChatKey = metadata != null ? metadata.getLiveChatKey() : null;

        if (mLiveChatKey != null) {
""",
    """        mLiveChatKey = metadata != null ? metadata.getLiveChatKey() : null;

        // NM7 Mobile: show the existing SmartTube live-chat stream automatically
        // when a video exposes a liveChatKey. The service remains receive-only.
        if (mLiveChatKey != null && "true".equals(System.getProperty("nm7.mobile.livechat"))) {
            getPlayerData().setLiveChatEnabled(true);
        }

        if (mLiveChatKey != null) {
""",
    "ChatController metadata hook",
)
CHAT.write_text(cs, encoding="utf-8")

print("NM7 1.10.76 live-chat + status-bar-safe player patch applied")
