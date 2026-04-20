// ================================================================
// URLBuilder.java v1.6
// UPDATED FROM v1.2:
//   - Version bumped to v1.6
//   - encode() now handles UTF-8 multi-byte characters correctly
//   - Added decode() method for URL decoding
//   - Added encodeComponent() for strict RFC 3986 compliance
//   - Added buildQueryString() for Vector/key-value pairs
//   - Added parseQueryString() to extract params from URL
//   - Improved null safety throughout
//   - Full compatibility with J2ME (no java.net.URLEncoder)
// ================================================================
public class URLBuilder {

    // ================================================================
    // Main encoding method (v1.6: UTF-8 multi-byte support)
    // ================================================================

    /**
     * URL-encode a string with proper UTF-8 multi-byte handling
     * Compatible with application/x-www-form-urlencoded
     */
    public static String encode(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            // Unreserved characters (RFC 3986)
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') ||
                c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append(c);
            }
            // Space → +
            else if (c == ' ') {
                sb.append('+');
            }
            // v1.6: Multi-byte UTF-8 encoding
            else if (c <= 0x7F) {
                // Single-byte ASCII
                appendHex(sb, c);
            }
            else if (c <= 0x7FF) {
                // Two-byte UTF-8: 110xxxxx 10xxxxxx
                int b1 = 0xC0 | ((c >> 6) & 0x1F);
                int b2 = 0x80 | (c & 0x3F);
                appendHex(sb, b1);
                appendHex(sb, b2);
            }
            else {
                // Three-byte UTF-8: 1110xxxx 10xxxxxx 10xxxxxx
                int b1 = 0xE0 | ((c >> 12) & 0x0F);
                int b2 = 0x80 | ((c >>  6) & 0x3F);
                int b3 = 0x80 | (c & 0x3F);
                appendHex(sb, b1);
                appendHex(sb, b2);
                appendHex(sb, b3);
            }
        }
        return sb.toString();
    }

    /**
     * v1.6: Strict RFC 3986 encoding (space → %20, not +)
     * Use for path/fragment components
     */
    public static String encodeComponent(String s) {
        if (s == null) return "";
        String encoded = encode(s);
        // Replace + with %20 for strict RFC 3986
        return replaceAll(encoded, "+", "%20");
    }

    // ================================================================
    // v1.6: URL decoding
    // ================================================================

    /**
     * Decode a URL-encoded string
     * Handles both + and %20 for spaces
     */
    public static String decode(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer();
        int i = 0;

        while (i < s.length()) {
            char c = s.charAt(i);

            if (c == '+') {
                sb.append(' ');
                i++;
            }
            else if (c == '%' && i + 2 < s.length()) {
                try {
                    int hex = Integer.parseInt(s.substring(i+1, i+3), 16);
                    sb.append((char)hex);
                    i += 3;
                } catch (NumberFormatException e) {
                    // Invalid hex, keep literal %
                    sb.append(c);
                    i++;
                }
            }
            else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    // ================================================================
    // v1.6: Query string builders
    // ================================================================

    /**
     * Build query string from key-value pairs
     * Input: Vector of String[2] where [0]=key, [1]=value
     * Output: "key1=value1&key2=value2"
     */
    public static String buildQueryString(java.util.Vector params) {
        if (params == null || params.size() == 0) return "";
        StringBuffer sb = new StringBuffer();

        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append('&');
            String[] pair = (String[])params.elementAt(i);
            if (pair != null && pair.length >= 2) {
                sb.append(encode(pair[0]));
                sb.append('=');
                sb.append(encode(pair[1]));
            }
        }
        return sb.toString();
    }

    /**
     * v1.6: Parse query string into key-value pairs
     * Input: "key1=value1&key2=value2"
     * Output: Vector of String[2]
     */
    public static java.util.Vector parseQueryString(String query) {
        java.util.Vector result = new java.util.Vector();
        if (query == null || query.length() == 0) return result;

        // Remove leading ? if present
        if (query.startsWith("?")) query = query.substring(1);

        String[] pairs = split(query, '&');
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i];
            int eq = pair.indexOf('=');
            if (eq >= 0) {
                String key = decode(pair.substring(0, eq));
                String val = decode(pair.substring(eq + 1));
                result.addElement(new String[]{key, val});
            } else {
                // Key with no value
                result.addElement(new String[]{decode(pair), ""});
            }
        }
        return result;
    }

    // ================================================================
    // v1.6: Build full URL with query params
    // ================================================================

    /**
     * Build full URL: base + "?" + params
     * params is Vector of String[2]
     */
    public static String buildURL(String base, java.util.Vector params) {
        if (base == null) base = "";
        String query = buildQueryString(params);
        if (query.length() == 0) return base;
        return base + (base.indexOf('?') >= 0 ? "&" : "?") + query;
    }

    // ================================================================
    // Internal helpers
    // ================================================================

    private static void appendHex(StringBuffer sb, int value) {
        sb.append('%');
        String hex = Integer.toHexString(value & 0xFF).toUpperCase();
        if (hex.length() < 2) sb.append('0');
        sb.append(hex);
    }

    // Simple replace (no regex)
    private static String replaceAll(String s, String old, String newStr) {
        if (s == null || old == null || old.length() == 0) return s;
        if (newStr == null) newStr = "";
        StringBuffer sb  = new StringBuffer();
        int          pos = 0;
        while (true) {
            int idx = s.indexOf(old, pos);
            if (idx < 0) {
                sb.append(s.substring(pos));
                break;
            }
            sb.append(s.substring(pos, idx));
            sb.append(newStr);
            pos = idx + old.length();
        }
        return sb.toString();
    }

    // Split by delimiter (J2ME compatible)
    private static String[] split(String s, char delim) {
        if (s == null) return new String[0];
        java.util.Vector v = new java.util.Vector();
        int pos = 0;
        while (pos <= s.length()) {
            int idx = s.indexOf(delim, pos);
            if (idx < 0) {
                v.addElement(s.substring(pos));
                break;
            }
            v.addElement(s.substring(pos, idx));
            pos = idx + 1;
        }
        String[] result = new String[v.size()];
        for (int i = 0; i < v.size(); i++) result[i] = (String)v.elementAt(i);
        return result;
    }

    // ================================================================
    // v1.6: Additional utility methods
    // ================================================================

    /**
     * Extract value of a query parameter from a URL
     * e.g. getParam("http://api.com?foo=bar&x=y", "foo") → "bar"
     */
    public static String getParam(String url, String paramName) {
        if (url == null || paramName == null) return null;
        int q = url.indexOf('?');
        if (q < 0) return null;

        String query = url.substring(q + 1);
        java.util.Vector params = parseQueryString(query);
        for (int i = 0; i < params.size(); i++) {
            String[] pair = (String[])params.elementAt(i);
            if (pair[0].equals(paramName)) return pair[1];
        }
        return null;
    }

    /**
     * Add or update a single query parameter in a URL
     * e.g. setParam("http://api.com?x=1", "y", "2") → "http://api.com?x=1&y=2"
     */
    public static String setParam(String url, String key, String value) {
        if (url == null) url = "";
        if (key == null) return url;

        int q = url.indexOf('?');
        String base  = (q >= 0) ? url.substring(0, q) : url;
        String query = (q >= 0) ? url.substring(q + 1) : "";

        java.util.Vector params = parseQueryString(query);

        // Update existing or add new
        boolean found = false;
        for (int i = 0; i < params.size(); i++) {
            String[] pair = (String[])params.elementAt(i);
            if (pair[0].equals(key)) {
                pair[1] = value != null ? value : "";
                found = true;
                break;
            }
        }
        if (!found) {
            params.addElement(new String[]{key, value != null ? value : ""});
        }

        return buildURL(base, params);
    }

    /**
     * Check if URL is valid (basic check)
     */
    public static boolean isValidURL(String url) {
        if (url == null || url.length() == 0) return false;
        return url.startsWith("http://") || url.startsWith("https://");
    }

    /**
     * Extract domain from URL
     * e.g. getDomain("http://api.example.com/path") → "api.example.com"
     */
    public static String getDomain(String url) {
        if (url == null) return "";
        int start = 0;
        if (url.startsWith("http://"))  start = 7;
        if (url.startsWith("https://")) start = 8;
        int slash = url.indexOf('/', start);
        int colon = url.indexOf(':', start);
        int end = url.length();
        if (slash > start) end = Math.min(end, slash);
        if (colon > start) end = Math.min(end, colon);
        return url.substring(start, end);
    }

    /**
     * Extract path from URL (without query)
     * e.g. getPath("http://api.com/foo/bar?x=1") → "/foo/bar"
     */
    public static String getPath(String url) {
        if (url == null) return "";
        int start = 0;
        if (url.startsWith("http://"))  start = 7;
        if (url.startsWith("https://")) start = 8;
        int slash = url.indexOf('/', start);
        if (slash < 0) return "/";
        int q = url.indexOf('?', slash);
        return (q >= 0) ? url.substring(slash, q) : url.substring(slash);
    }
}