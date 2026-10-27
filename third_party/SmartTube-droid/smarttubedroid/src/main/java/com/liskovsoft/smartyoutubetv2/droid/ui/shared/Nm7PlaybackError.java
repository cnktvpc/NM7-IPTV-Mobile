package com.liskovsoft.smartyoutubetv2.droid.ui.shared;

/** Safe diagnostics: never display exception messages containing signed URLs or tokens. */
public final class Nm7PlaybackError {
    private Nm7PlaybackError() { }
    public static String describe(Throwable error, int type) {
        String label = type == 0 ? "Lỗi nguồn phát" : type == 1 ? "Lỗi giải mã" : "Lỗi bộ phát";
        for (int i = 0; i < 12 && error != null; i++, error = error.getCause()) {
            if (error instanceof OutOfMemoryError) return "Thiếu bộ nhớ";
            String name = error.getClass().getSimpleName();
            if (name.contains("Timeout") || name.contains("UnknownHost") || name.contains("Connect")) label = "Lỗi kết nối mạng";
            try {
                Object code = error.getClass().getField("responseCode").get(error);
                if (code instanceof Integer) return "Nguồn phát HTTP " + code;
            } catch (ReflectiveOperationException ignored) { }
        }
        return label;
    }
}
