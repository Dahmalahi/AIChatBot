import java.util.Vector;

// ================================================================
// URLBuilder.java v1.9
// UPDATED FROM v1.6:
//   - VERSION bumped to v1.9, BUILD 20250422
//   - FIX: encode() surrogate pair handling for chars > 0xFFFF
//          (safety guard for J2ME VMs that expose surrogates)
//   - FIX: decode() now properly reconstructs multi-byte UTF-8
//          sequences (%C3%A9 → é) instead of treating each byte
//          as a separate char - fixes garbled non-ASCII responses
//   - FIX: buildQueryString() null-safe for pair[1] == null
//   - FIX: parseQueryString() trims whitespace from keys/values
//   - FIX: getParam() case-insensitive option via getParamCI()
//   - NEW: encodeForAPI() - encodes text for the ChatGPT API URL
//          (replaces + with %20, encodes all control chars)
//   - NEW: appendPath() - safely join base URL + path segment
//   - NEW: stripFragment() - remove #fragment from URL
//   - NEW: sanitizeInput() - remove chars unsafe for API queries
//   - NEW: truncateForURL() - safely cap query length for J2ME
//          HTTP stacks that choke on very long GET URLs
//   - NEW: isFileURL() - check for file:/// scheme
//   - NEW: isHTTP() / isHTTPS() split from isValidURL()
//   - KEEP: all v1.6 methods fully intact and compatible
// ================================================================
public class URLBuilder {

    // ================================================================
    // Constants
    // ================================================================

    // Safe maximum URL length for J2ME HTTP stacks.
    // Some Nokia/SE stacks reject URLs longer than 2048 bytes.
    // POST is better for long payloads, but we use GET so cap here.
    public static final int MAX_URL_LENGTH = 2000;

    // Maximum raw text length we will encode into a single GET URL.
    // At ~3x expansion per char worst case, 600 raw chars → 1800 encoded.
    public static final int MAX_QUERY_CHARS = 600;

    // ================================================================
    // Primary encode() - application/x-www-form-urlencoded
    // Space → +, all others percent-encoded, full UTF-8 support.
    // ================================================================

    /**
     * URL-encode a string.
     * Handles ASCII, two-byte and three-byte UTF-8 code points.
     * Safe for use in query-string values.
     */
    public static String encode(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer(s.length() * 2);

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            // ---- Unreserved chars (RFC 3986 §2.3) ----
            if ((c >= 'a' && c <= 'z') ||
                (c >= 'A' && c <= 'Z') ||
                (c >= '0' && c <= '9') ||
                c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append(c);

            // ---- Space → + (form encoding) ----
            } else if (c == ' ') {
                sb.append('+');

            // ---- Single-byte ASCII (0x00-0x7F) ----
            } else if (c <= 0x7F) {
                appendHex(sb, c);

            // ---- Two-byte UTF-8 (U+0080 – U+07FF) ----
            } else if (c <= 0x7FF) {
                appendHex(sb, 0xC0 | ((c >> 6) & 0x1F));
                appendHex(sb, 0x80 | ( c       & 0x3F));

            // ---- Surrogate halves (U+D800 – U+DFFF) ----
            // Some J2ME VMs expose surrogate pairs as two chars.
            // Encode the pair as a four-byte UTF-8 sequence.
            } else if (c >= 0xD800 && c <= 0xDBFF && i + 1 < s.length()) {
                char low = s.charAt(i + 1);
                if (low >= 0xDC00 && low <= 0xDFFF) {
                    int cp = 0x10000 + ((c - 0xD800) << 10) + (low - 0xDC00);
                    appendHex(sb, 0xF0 | ((cp >> 18) & 0x07));
                    appendHex(sb, 0x80 | ((cp >> 12) & 0x3F));
                    appendHex(sb, 0x80 | ((cp >>  6) & 0x3F));
                    appendHex(sb, 0x80 | ( cp        & 0x3F));
                    i++; // consume low surrogate
                } else {
                    // Unpaired high surrogate – encode as replacement
                    appendHex(sb, 0xEF);
                    appendHex(sb, 0xBF);
                    appendHex(sb, 0xBD); // U+FFFD
                }

            // ---- Three-byte UTF-8 (U+0800 – U+FFFF) ----
            } else {
                appendHex(sb, 0xE0 | ((c >> 12) & 0x0F));
                appendHex(sb, 0x80 | ((c >>  6) & 0x3F));
                appendHex(sb, 0x80 | ( c        & 0x3F));
            }
        }
        return sb.toString();
    }

    // ================================================================
    // encodeComponent() - strict RFC 3986 (space → %20)
    // Use for path segments and fragment components.
    // ================================================================

    /**
     * Encode a URL component with space as %20 (not +).
     * Use for path parts, not query values.
     */
    public static String encodeComponent(String s) {
        if (s == null) return "";
        return replaceAll(encode(s), "+", "%20");
    }

    // ================================================================
    // v1.9: encodeForAPI()
    // Optimised for ChatGPT / REST API query parameters.
    // - Space → %20 (some APIs reject + in query values)
    // - Strips ASCII control characters (0x00-0x1F, 0x7F)
    // - Caps length at MAX_QUERY_CHARS to stay inside URL limit
    // ================================================================

    /**
     * Encode text for use as a ChatGPT / REST API query parameter.
     * Safer than plain encode() for API endpoints.
     */
    public static String encodeForAPI(String s) {
        if (s == null) return "";
        // Sanitize first
        s = sanitizeInput(s);
        // Cap length
        s = truncateForURL(s, MAX_QUERY_CHARS);
        // Encode with strict %20 for spaces
        return encodeComponent(s);
    }

    // ================================================================
    // v1.9 FIX: decode() - proper multi-byte UTF-8 reconstruction
    //
    // Old version treated each %XX byte as a separate char, which
    // broke non-ASCII text (é became Ã©, etc.).
    // New version accumulates continuation bytes and reconstructs
    // the full Unicode code point.
    // ================================================================

    /**
     * Decode a URL-encoded string.
     * Correctly handles multi-byte UTF-8 sequences like %C3%A9 → é.
     * Handles both + (space) and %20.
     */
    public static String decode(String s) {
        if (s == null) return "";

        // Step 1: collect raw bytes from percent sequences
        byte[]       bytes  = new byte[s.length()];
        int          bCount = 0;
        StringBuffer result = new StringBuffer(s.length());

        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);

            if (c == '+') {
                // Flush any accumulated bytes first
                if (bCount > 0) {
                    result.append(bytesToString(bytes, bCount));
                    bCount = 0;
                }
                result.append(' ');
                i++;

            } else if (c == '%' && i + 2 < s.length()) {
                // Try to parse hex pair
                String hex = s.substring(i + 1, i + 3);
                try {
                    int byteVal = Integer.parseInt(hex, 16);
                    bytes[bCount++] = (byte) byteVal;
                    i += 3;
                } catch (NumberFormatException e) {
                    // Not valid hex - flush bytes, keep literal %
                    if (bCount > 0) {
                        result.append(bytesToString(bytes, bCount));
                        bCount = 0;
                    }
                    result.append('%');
                    i++;
                }

            } else {
                // Regular char - flush accumulated bytes first
                if (bCount > 0) {
                    result.append(bytesToString(bytes, bCount));
                    bCount = 0;
                }
                result.append(c);
                i++;
            }
        }

        // Flush any remaining bytes
        if (bCount > 0) {
            result.append(bytesToString(bytes, bCount));
        }

        return result.toString();
    }

    /**
     * Convert a UTF-8 byte sequence to a Java String.
     * Handles 1, 2, 3 and 4-byte sequences.
     */
    private static String bytesToString(byte[] bytes, int len) {
        try {
            // Use String(byte[], charset) if available
            // J2ME CLDC 1.1 supports "UTF-8"
            return new String(bytes, 0, len, "UTF-8");
        } catch (Exception e) {
            // Fallback: treat each byte as ISO-8859-1
            StringBuffer sb = new StringBuffer(len);
            for (int i = 0; i < len; i++) {
                sb.append((char)(bytes[i] & 0xFF));
            }
            return sb.toString();
        }
    }

    // ================================================================
    // Query string builders
    // ================================================================

    /**
     * Build query string from key-value pairs.
     * Input:  Vector of String[2] where [0]=key, [1]=value
     * Output: "key1=value1&key2=value2"
     */
    public static String buildQueryString(Vector params) {
        if (params == null || params.size() == 0) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sb.append('&');
            Object obj = params.elementAt(i);
            if (!(obj instanceof String[])) continue;
            String[] pair = (String[]) obj;
            if (pair.length < 1) continue;
            String key = pair[0] != null ? pair[0] : "";
            String val = pair.length >= 2 && pair[1] != null ? pair[1] : "";
            sb.append(encode(key));
            sb.append('=');
            sb.append(encode(val));
        }
        return sb.toString();
    }

    /**
     * Parse query string into key-value pairs.
     * Input:  "key1=value1&key2=value2"  (leading ? stripped)
     * Output: Vector of String[2]
     */
    public static Vector parseQueryString(String query) {
        Vector result = new Vector();
        if (query == null || query.length() == 0) return result;
        if (query.startsWith("?")) query = query.substring(1);

        String[] pairs = split(query, '&');
        for (int i = 0; i < pairs.length; i++) {
            String pair = pairs[i];
            if (pair == null || pair.length() == 0) continue;
            int eq = pair.indexOf('=');
            if (eq >= 0) {
                String key = decode(pair.substring(0, eq)).trim();
                String val = decode(pair.substring(eq + 1)).trim();
                result.addElement(new String[]{ key, val });
            } else {
                result.addElement(new String[]{ decode(pair).trim(), "" });
            }
        }
        return result;
    }

    // ================================================================
    // Build full URL
    // ================================================================

    /**
     * Build full URL: base + "?" + encoded params.
     */
    public static String buildURL(String base, Vector params) {
        if (base == null) base = "";
        String query = buildQueryString(params);
        if (query.length() == 0) return base;
        char sep = (base.indexOf('?') >= 0) ? '&' : '?';
        return base + sep + query;
    }

    // ================================================================
    // v1.9: appendPath()
    // Safely join a base URL and a path segment,
    // avoiding double slashes.
    // ================================================================

    /**
     * Join base URL and path segment without double slashes.
     * e.g. appendPath("http://api.com/", "/v1/chat") → "http://api.com/v1/chat"
     */
    public static String appendPath(String base, String path) {
        if (base == null) base = "";
        if (path == null || path.length() == 0) return base;

        boolean baseSlash = base.endsWith("/");
        boolean pathSlash = path.startsWith("/");

        if (baseSlash && pathSlash) {
            return base + path.substring(1);
        } else if (!baseSlash && !pathSlash) {
            return base + "/" + path;
        } else {
            return base + path;
        }
    }

    // ================================================================
    // v1.9: stripFragment()
    // Remove #fragment from a URL.
    // ================================================================

    /**
     * Remove the fragment (#...) from a URL.
     * e.g. "http://x.com/page#top" → "http://x.com/page"
     */
    public static String stripFragment(String url) {
        if (url == null) return "";
        int hash = url.indexOf('#');
        return (hash >= 0) ? url.substring(0, hash) : url;
    }

    // ================================================================
    // v1.9: sanitizeInput()
    // Remove characters that are unsafe or meaningless in an API
    // query: control chars, null bytes, lone surrogates.
    // ================================================================

    /**
     * Strip control characters and other unsafe chars from user input
     * before encoding it for an API query.
     */
    public static String sanitizeInput(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // Keep printable ASCII, space, and chars > 0x7E (Unicode text)
            if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(' ');                   // normalise whitespace
            } else if (c < 0x20 || c == 0x7F) {
                // Drop ASCII control characters
            } else if (c >= 0xD800 && c <= 0xDFFF) {
                // Drop lone surrogates (malformed Unicode)
            } else {
                sb.append(c);
            }
        }
        // Collapse multiple spaces
        return collapseSpaces(sb.toString());
    }

    // ================================================================
    // v1.9: truncateForURL()
    // Cap the length of a raw string before encoding so the final
    // URL stays within MAX_URL_LENGTH bytes on J2ME HTTP stacks.
    // ================================================================

    /**
     * Truncate raw text to maxChars characters.
     * Appends "..." to indicate truncation.
     * Use BEFORE encoding to control final URL length.
     */
    public static String truncateForURL(String s, int maxChars) {
        if (s == null) return "";
        if (maxChars <= 0) return "";
        if (s.length() <= maxChars) return s;
        // Try to break at a word boundary
        int cut = maxChars;
        while (cut > maxChars - 20 && cut > 0 && s.charAt(cut) != ' ') {
            cut--;
        }
        if (cut == 0) cut = maxChars;
        return s.substring(0, cut).trim() + "...";
    }

    // ================================================================
    // Parameter helpers
    // ================================================================

    /**
     * Extract value of a named query parameter from a URL.
     * e.g. getParam("http://api.com?foo=bar", "foo") → "bar"
     * Returns null if not found.
     */
    public static String getParam(String url, String paramName) {
        if (url == null || paramName == null) return null;
        int q = url.indexOf('?');
        if (q < 0) return null;
        Vector params = parseQueryString(url.substring(q + 1));
        for (int i = 0; i < params.size(); i++) {
            String[] pair = (String[]) params.elementAt(i);
            if (pair[0].equals(paramName)) return pair[1];
        }
        return null;
    }

    /**
     * v1.9: Case-insensitive parameter lookup.
     */
    public static String getParamCI(String url, String paramName) {
        if (url == null || paramName == null) return null;
        int q = url.indexOf('?');
        if (q < 0) return null;
        String lower = paramName.toLowerCase();
        Vector params = parseQueryString(url.substring(q + 1));
        for (int i = 0; i < params.size(); i++) {
            String[] pair = (String[]) params.elementAt(i);
            if (pair[0].toLowerCase().equals(lower)) return pair[1];
        }
        return null;
    }

    /**
     * Add or update a single query parameter in a URL.
     */
    public static String setParam(String url, String key, String value) {
        if (url   == null) url   = "";
        if (key   == null) return url;
        if (value == null) value = "";

        int    q     = url.indexOf('?');
        String base  = (q >= 0) ? url.substring(0, q)    : url;
        String query = (q >= 0) ? url.substring(q + 1)   : "";

        Vector params = parseQueryString(query);
        boolean found = false;
        for (int i = 0; i < params.size(); i++) {
            String[] pair = (String[]) params.elementAt(i);
            if (pair[0].equals(key)) {
                pair[1] = value;
                found = true;
                break;
            }
        }
        if (!found) params.addElement(new String[]{ key, value });
        return buildURL(base, params);
    }

    // ================================================================
    // URL inspection helpers
    // ================================================================

    /** True if url starts with http:// */
    public static boolean isHTTP(String url) {
        return url != null && url.startsWith("http://");
    }

    /** True if url starts with https:// */
    public static boolean isHTTPS(String url) {
        return url != null && url.startsWith("https://");
    }

    /** True if url starts with http:// or https:// */
    public static boolean isValidURL(String url) {
        return isHTTP(url) || isHTTPS(url);
    }

    /** v1.9: True if url starts with file:/// */
    public static boolean isFileURL(String url) {
        return url != null && url.toLowerCase().startsWith("file:///");
    }

    /**
     * Extract domain from URL.
     * e.g. getDomain("http://api.example.com/path") → "api.example.com"
     */
    public static String getDomain(String url) {
        if (url == null) return "";
        int start = 0;
        if (url.startsWith("https://")) start = 8;
        else if (url.startsWith("http://")) start = 7;
        int end   = url.length();
        int slash = url.indexOf('/', start);
        int colon = url.indexOf(':', start);
        if (slash > start) end = Math.min(end, slash);
        if (colon > start) end = Math.min(end, colon);
        return url.substring(start, end);
    }

    /**
     * Extract path from URL (without query string or fragment).
     * e.g. getPath("http://api.com/foo/bar?x=1") → "/foo/bar"
     */
    public static String getPath(String url) {
        if (url == null) return "";
        int start = 0;
        if (url.startsWith("https://")) start = 8;
        else if (url.startsWith("http://")) start = 7;
        int slash = url.indexOf('/', start);
        if (slash < 0) return "/";
        int q = url.indexOf('?', slash);
        int h = url.indexOf('#', slash);
        int end = url.length();
        if (q >= 0) end = Math.min(end, q);
        if (h >= 0) end = Math.min(end, h);
        return url.substring(slash, end);
    }

    // ================================================================
    // Internal helpers
    // ================================================================

    /** Append a percent-encoded byte to the buffer. */
    private static void appendHex(StringBuffer sb, int value) {
        sb.append('%');
        String hex = Integer.toHexString(value & 0xFF).toUpperCase();
        if (hex.length() < 2) sb.append('0');
        sb.append(hex);
    }

    /** Replace all occurrences of old with newStr (no regex). */
    private static String replaceAll(String s, String old, String newStr) {
        if (s == null || old == null || old.length() == 0) return s;
        if (newStr == null) newStr = "";
        StringBuffer sb  = new StringBuffer(s.length());
        int          pos = 0;
        while (true) {
            int idx = s.indexOf(old, pos);
            if (idx < 0) { sb.append(s.substring(pos)); break; }
            sb.append(s.substring(pos, idx));
            sb.append(newStr);
            pos = idx + old.length();
        }
        return sb.toString();
    }

    /** Split string on a single delimiter character. */
    private static String[] split(String s, char delim) {
        if (s == null) return new String[0];
        Vector v   = new Vector();
        int    pos = 0;
        while (pos <= s.length()) {
            int idx = s.indexOf(delim, pos);
            if (idx < 0) { v.addElement(s.substring(pos)); break; }
            v.addElement(s.substring(pos, idx));
            pos = idx + 1;
        }
        String[] r = new String[v.size()];
        for (int i = 0; i < v.size(); i++) r[i] = (String) v.elementAt(i);
        return r;
    }

    /** Collapse consecutive spaces into a single space. */
    private static String collapseSpaces(String s) {
        if (s == null) return "";
        StringBuffer sb     = new StringBuffer(s.length());
        boolean      inSpace= false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ') {
                if (!inSpace) { sb.append(' '); inSpace = true; }
            } else {
                sb.append(c); inSpace = false;
            }
        }
        return sb.toString().trim();
    }
}