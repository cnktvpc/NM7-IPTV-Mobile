package vn.phuong.iptvplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import java.util.Arrays;

/** Mobile-only IPTV navigation refinement. Bottom toolbar keeps the player area clear. */
public final class MobileIptvUi {
    private MobileIptvUi() {}

    public static void install(Activity activity) {
        if (!(activity instanceof MainActivity)) return;

        View importPanel = activity.findViewById(R.id.importPanel);
        if (importPanel != null) importPanel.setVisibility(View.GONE);

        EditText search = activity.findViewById(R.id.inputSearch);
        Button reload = activity.findViewById(R.id.btnReloadUrl);
        Button options = activity.findViewById(R.id.btnIptvOptions);
        View all = activity.findViewById(R.id.btnAllChannels);
        View favorites = activity.findViewById(R.id.btnFavorites);
        View recent = activity.findViewById(R.id.btnRecent);

        // The old four-button section row is intentionally removed from the visible UI.
        // Section switching is still preserved by routing it through Tùy chọn IPTV.
        if (all != null) all.setVisibility(View.GONE);
        if (favorites != null) favorites.setVisibility(View.GONE);
        if (recent != null) recent.setVisibility(View.GONE);

        if (search != null) {
            search.setSingleLine(true);
            search.setTextSize(14);
            search.setHint("Tìm kênh…");
        }

        if (reload != null) {
            reload.setText("↻ Tải lại");
            reload.setTextSize(11);
            reload.setAllCaps(false);
            reload.setContentDescription("Tải lại playlist hiện tại");
        }

        if (options != null) {
            options.setText("⚙");
            options.setTextSize(18);
            options.setAllCaps(false);
            options.setContentDescription("Tùy chọn IPTV");
            options.setOnClickListener(v -> showNavigationOptions(activity, all, favorites, recent));
        }
    }

    private static void showNavigationOptions(Activity activity, View all, View favorites, View recent) {
        // App-level settings are deliberately no longer duplicated here; they live in the
        // third bottom-navigation destination.
        String[] items = {"Tất cả kênh", "★ Yêu thích", "◷ Gần đây"};
        new AlertDialog.Builder(activity)
                .setTitle("Tùy chọn IPTV")
                .setItems(items, (dialog, which) -> {
                    if (which == 0 && all != null) all.performClick();
                    else if (which == 1 && favorites != null) favorites.performClick();
                    else if (which == 2 && recent != null) recent.performClick();
                })
                .setNegativeButton("Đóng", null)
                .show();
    }
}
