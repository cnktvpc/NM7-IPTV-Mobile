package vn.phuong.iptvplayer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Compatibility entry point retained for existing NM7 settings and shortcuts. */
public final class YoutubeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(this, SmartTubeHomeActivity.class);
        if (getIntent().getData() != null) {
            intent.setData(getIntent().getData());
        }
        Bundle extras = getIntent().getExtras();
        if (extras != null) intent.putExtras(extras);
        startActivity(intent);
        finish();
    }
}
