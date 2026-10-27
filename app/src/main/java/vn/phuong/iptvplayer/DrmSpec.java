package vn.phuong.iptvplayer;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Parses provider-supplied DRM metadata; never discovers or derives keys. */
public final class DrmSpec {
    public final String system, license, problem;
    public final Map<String, String> headers;

    private DrmSpec(String system, String license, Map<String, String> headers, String problem) {
        this.system = system;
        this.license = license;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        this.problem = problem;
    }

    public static DrmSpec fromOptions(List<String> options) {
        String system = "", license = "";
        for (String line : options) {
            int equals = line.indexOf('=');
            if (equals < 0) continue;
            String key = line.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(equals + 1).trim();
            if (key.endsWith("inputstream.adaptive.license_type")) system = value;
            if (key.endsWith("inputstream.adaptive.license_key")) license = value;
            if (key.endsWith("inputstream.adaptive.drm_legacy")) {
                String[] parts = value.split("\\|", 2);
                system = parts[0];
                if (parts.length > 1) license = parts[1];
            }
        }
        return create(system, license);
    }

    public static DrmSpec create(String system, String license) {
        String type = normalizeSystem(system);
        String value = license == null ? "" : license.trim();
        Map<String, String> headers = new LinkedHashMap<>();
        if (type.isEmpty() && value.isEmpty()) return new DrmSpec("", "", headers, "");
        if (type.isEmpty()) return new DrmSpec(type, value, headers, "Playlist có giấy phép nhưng thiếu loại DRM. Chọn DRM để cấu hình.");
        if (type.equals("unknown")) return new DrmSpec(type, value, headers, "Loại DRM này chưa hỗ trợ. Chọn DRM để kiểm tra cấu hình.");
        if (value.isEmpty()) return new DrmSpec(type, value, headers, "Thiếu địa chỉ giấy phép hoặc ClearKey do nhà cung cấp cấp. Chọn DRM để bổ sung.");
        if (isHttp(value.split("\\|", 2)[0])) {
            String[] fields = value.split("\\|", -1);
            value = fields[0];
            if (fields.length > 1) {
                for (String field : fields[1].split("&")) {
                    int sep = field.indexOf('=');
                    if (sep < 1) continue;
                    String name = decode(field.substring(0, sep)), content = decode(field.substring(sep + 1));
                    if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || content.contains("\r") || content.contains("\n"))
                        return new DrmSpec(type, value, headers, "Header giấy phép không hợp lệ.");
                    headers.put(name, content);
                }
            }
            if ((fields.length > 2 && !fields[2].isEmpty() && !fields[2].equals("R{SSM}") && !fields[2].equals("{SSM}"))
                    || (fields.length > 3 && !fields[3].isEmpty()))
                return new DrmSpec(type, value, headers, "Máy chủ yêu cầu mẫu trao đổi giấy phép riêng chưa được hỗ trợ. Cần URL giấy phép trả dữ liệu DRM trực tiếp.");
            return new DrmSpec(type, value, headers, "");
        }
        if (!type.equals("clearkey")) return new DrmSpec(type, value, headers, "Địa chỉ máy chủ giấy phép phải là HTTP hoặc HTTPS.");
        // Provider ClearKey responses are normalized and strictly validated later by DrmPlayback.
        // Pretty-printed JSON may legitimately contain line breaks, so only cap the payload size here.
        if (value.length() > 65536)
            return new DrmSpec(type, value, headers, "ClearKey không hợp lệ hoặc quá lớn.");
        return new DrmSpec(type, value, headers, "");
    }

    public boolean hasDrm() { return !system.isEmpty() || !license.isEmpty(); }
    public boolean localClearKey() { return system.equals("clearkey") && !isHttp(license); }
    public boolean remoteClearKey() { return system.equals("clearkey") && isHttp(license); }
    public UUID uuid() {
        if (system.equals("widevine")) return UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed");
        if (system.equals("clearkey")) return UUID.fromString("e2719d58-a985-b3c9-781a-b030af78d30e");
        if (system.equals("playready")) return UUID.fromString("9a04f079-9840-4286-ab92-e65be0885f95");
        throw new IllegalArgumentException("Unsupported DRM");
    }
    public static String normalizeSystem(String value) {
        String s = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("none")) return "";
        if (s.equals("widevine") || s.equals("com.widevine.alpha") || s.equals("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")) return "widevine";
        if (s.equals("clearkey") || s.equals("org.w3.clearkey") || s.equals("e2719d58-a985-b3c9-781a-b030af78d30e")) return "clearkey";
        if (s.equals("playready") || s.equals("com.microsoft.playready") || s.equals("9a04f079-9840-4286-ab92-e65be0885f95")) return "playready";
        return "unknown";
    }
    private static boolean isHttp(String value) {
        try { URI uri = URI.create(value); return uri.getHost() != null && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())); }
        catch (IllegalArgumentException ignored) { return false; }
    }
    private static String decode(String value) {
        try { return URLDecoder.decode(value, "UTF-8"); }
        catch (Exception ignored) { return value; }
    }
}
