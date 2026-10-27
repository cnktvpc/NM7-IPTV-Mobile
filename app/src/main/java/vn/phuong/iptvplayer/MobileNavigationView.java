package vn.phuong.iptvplayer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Mobile-only navigation. Built synchronously and placed directly in the main layout
 * so the YouTube entry is visible without lifecycle callbacks or delayed posts.
 */
public final class MobileNavigationView extends LinearLayout {
    public MobileNavigationView(Context context) { super(context); init(context); }
    public MobileNavigationView(Context context, android.util.AttributeSet attrs) { super(context, attrs); init(context); }
    public MobileNavigationView(Context context, android.util.AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(context); }

    private void init(Context context) {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setBackground(new ColorDrawable(Color.rgb(23, 23, 28)));
        setPadding(dp(context, 4), dp(context, 3), dp(context, 4), dp(context, 3));
        buildItems(context);
    }

    private void buildItems(Context context) {
        if (getChildCount() != 0) return;
        addItem(context, R.drawable.nm7_nav_youtube, "YouTube", false, v -> {
            if (context instanceof Activity) {
                Intent intent = new Intent((Activity) context, SmartTubeHomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                ((Activity) context).startActivity(intent);
            }
        });
        addItem(context, R.drawable.nm7_nav_iptv, "IPTV", true, v -> { });
    }

    private void addItem(Context context, int iconRes, String label, boolean selected, View.OnClickListener listener) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);
        item.setOnClickListener(listener);
        item.setBackgroundResource(selected ? R.drawable.button_primary : R.drawable.button_secondary);

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setColorFilter(selected ? Color.rgb(23, 23, 28) : Color.rgb(255, 122, 0));
        item.addView(icon, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));

        TextView text = new TextView(context);
        text.setText(label);
        text.setTextSize(12);
        text.setGravity(Gravity.CENTER);
        text.setTextColor(selected ? Color.rgb(23, 23, 28) : Color.WHITE);
        item.addView(text, new LinearLayout.LayoutParams(-2, dp(context, 22)));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
        p.setMargins(dp(context, 3), 0, dp(context, 3), 0);
        addView(item, p);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
