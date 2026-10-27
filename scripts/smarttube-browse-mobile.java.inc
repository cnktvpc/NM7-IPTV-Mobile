    private void installNm7MiniPlayer() {
        try {
            Class.forName("vn.phuong.iptvplayer.MobileMiniPlayer")
                    .getMethod("attach", android.app.Activity.class).invoke(null, this);
        } catch (ReflectiveOperationException error) {
            android.util.Log.e("NM7Playback", "Native Browse mini-player", error);
        }
    }
