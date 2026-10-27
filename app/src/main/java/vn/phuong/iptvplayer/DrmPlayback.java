package vn.phuong.iptvplayer;

import android.media.MediaDrm;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback;
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback;
import androidx.media3.exoplayer.drm.MediaDrmCallback;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

@UnstableApi
final class DrmPlayback {
    static void configure(DrmSpec spec, MediaItem.Builder item, DefaultMediaSourceFactory media) throws Exception {
        if (!spec.problem.isEmpty()) throw new IllegalArgumentException(spec.problem);
        if (!spec.hasDrm()) return;
        if (!MediaDrm.isCryptoSchemeSupported(spec.uuid()))
            throw new IllegalArgumentException("Thiết bị không hỗ trợ " + spec.system + ". PlayReady thường cần Android TV.");
        MediaItem.DrmConfiguration.Builder config = new MediaItem.DrmConfiguration.Builder(spec.uuid()).setMultiSession(true);
        MediaDrmCallback callback;
        if (spec.localClearKey()) {
            callback = new LocalMediaDrmCallback(clearKeyResponse(spec.license));
        } else {
            config.setLicenseUri(spec.license).setLicenseRequestHeaders(spec.headers);
            // Stream cookies are intentionally not forwarded to another license host.
            callback = new HttpMediaDrmCallback(spec.license, true, new DefaultHttpDataSource.Factory()
                    .setUserAgent("NM7-IPTV/1.10 Android").setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(20000).setDefaultRequestProperties(spec.headers));
        }
        DefaultDrmSessionManager manager = new DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(spec.uuid(), FrameworkMediaDrm.DEFAULT_PROVIDER)
                .setMultiSession(true).build(callback);
        media.setDrmSessionManagerProvider(unused -> manager);
        item.setDrmConfiguration(config.build());
    }

    /**
     * Normalize provider-supplied ClearKey metadata into the JWK response Android expects.
     * This only accepts keys explicitly present in the playlist/license response; it never
     * derives, discovers or logs key material.
     */
    static byte[] clearKeyResponse(String source) throws Exception {
        JSONArray keys = new JSONArray();
        try {
            String clean = source == null ? "" : source.trim();
            if (clean.isEmpty()) throw new IllegalArgumentException();
            if (clean.startsWith("{") || clean.startsWith("[")) {
                if (clean.startsWith("[")) {
                    appendKeyArray(new JSONArray(clean), keys);
                } else {
                    appendJsonObject(new JSONObject(clean), keys);
                }
            } else if (looksLikeNamedPair(clean)) {
                appendNamedPair(clean, keys);
            } else {
                for (String pair : clean.split("\\s*,\\s*")) {
                    String[] values = pair.trim().split(":", 2);
                    if (values.length != 2) throw new IllegalArgumentException();
                    keys.put(jwk(decodeKeyPart(values[0]), decodeKeyPart(values[1])));
                }
            }
            if (keys.length() == 0 || keys.length() > 32) throw new IllegalArgumentException();
            return new JSONObject().put("keys", keys).put("type", "temporary").toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalArgumentException("ClearKey không hợp lệ: cần KID và KEY 16 byte, dạng hex/Base64 hoặc JWK. Không hiển thị khóa trong thông báo lỗi.");
        }
    }

    private static void appendJsonObject(JSONObject input, JSONArray output) throws Exception {
        if (input.has("keys")) {
            Object value = input.get("keys");
            if (value instanceof JSONArray) {
                appendKeyArray((JSONArray) value, output);
                return;
            }
            if (value instanceof JSONObject) {
                appendKeyMap((JSONObject) value, output);
                return;
            }
        }
        if (input.has("kid") && (input.has("key") || input.has("k"))) {
            String kid = input.getString("kid");
            String value = input.has("key") ? input.getString("key") : input.getString("k");
            output.put(jwk(decodeKeyPart(kid), decodeKeyPart(value)));
            return;
        }
        if (input.has("data") && input.get("data") instanceof JSONObject) {
            appendJsonObject(input.getJSONObject("data"), output);
            return;
        }
        appendKeyMap(input, output);
    }

    private static void appendKeyArray(JSONArray array, JSONArray output) throws Exception {
        for (int i = 0; i < array.length(); i++) {
            Object entry = array.get(i);
            if (!(entry instanceof JSONObject)) throw new IllegalArgumentException();
            JSONObject key = (JSONObject) entry;
            if (!key.optString("kty", "oct").equalsIgnoreCase("oct")) throw new IllegalArgumentException();
            if (!key.has("kid") || (!key.has("k") && !key.has("key"))) throw new IllegalArgumentException();
            String value = key.has("k") ? key.getString("k") : key.getString("key");
            output.put(jwk(decodeKeyPart(key.getString("kid")), decodeKeyPart(value)));
        }
    }

    private static void appendKeyMap(JSONObject input, JSONArray output) throws Exception {
        int before = output.length();
        Iterator<String> names = input.keys();
        while (names.hasNext()) {
            String kid = names.next();
            Object raw = input.get(kid);
            if (!(raw instanceof String)) continue;
            try {
                output.put(jwk(decodeKeyPart(kid), decodeKeyPart((String) raw)));
            } catch (IllegalArgumentException ignored) {
                // Ignore non-key metadata fields. If no valid pair remains, the caller rejects it.
            }
        }
        if (output.length() == before) throw new IllegalArgumentException();
    }

    private static boolean looksLikeNamedPair(String source) {
        String lower = source.toLowerCase(Locale.ROOT);
        return lower.contains("kid=") && (lower.contains("key=") || lower.contains("k="));
    }

    private static void appendNamedPair(String source, JSONArray output) throws Exception {
        String kid = "", key = "";
        for (String field : source.split("[&;,|]")) {
            int equals = field.indexOf('=');
            if (equals < 1) continue;
            String name = field.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String value = field.substring(equals + 1).trim();
            if (name.equals("kid")) kid = value;
            else if (name.equals("key") || name.equals("k")) key = value;
        }
        if (kid.isEmpty() || key.isEmpty()) throw new IllegalArgumentException();
        output.put(jwk(decodeKeyPart(kid), decodeKeyPart(key)));
    }

    private static JSONObject jwk(byte[] kid, byte[] key) throws Exception {
        if (kid.length != 16 || key.length != 16) throw new IllegalArgumentException();
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return new JSONObject().put("kty", "oct").put("kid", encoder.encodeToString(kid)).put("k", encoder.encodeToString(key));
    }

    private static byte[] hex(String value) {
        String clean = stripQuotes(value).replace("-", "");
        if (clean.regionMatches(true, 0, "0x", 0, 2)) clean = clean.substring(2);
        if (!clean.matches("(?i)[0-9a-f]{32}")) throw new IllegalArgumentException();
        byte[] bytes = new byte[16];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(clean.substring(i * 2, i * 2 + 2), 16);
        return bytes;
    }

    private static byte[] decodeKeyPart(String value) {
        String clean = stripQuotes(value);
        String hexCandidate = clean;
        if (hexCandidate.regionMatches(true, 0, "0x", 0, 2)) hexCandidate = hexCandidate.substring(2);
        if (hexCandidate.replace("-", "").matches("(?i)[0-9a-f]{32}")) return hex(hexCandidate);
        byte[] decoded;
        try {
            decoded = Base64.getUrlDecoder().decode(clean);
            if (decoded.length == 16) return decoded;
        } catch (IllegalArgumentException ignored) { }
        try {
            decoded = Base64.getDecoder().decode(clean);
            if (decoded.length == 16) return decoded;
        } catch (IllegalArgumentException ignored) { }
        throw new IllegalArgumentException();
    }

    private static String stripQuotes(String value) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() >= 2 && ((clean.startsWith("\"") && clean.endsWith("\""))
                || (clean.startsWith("'") && clean.endsWith("'")))) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        return clean;
    }
}
