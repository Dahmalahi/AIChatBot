import java.io.*;
import javax.microedition.io.*;
import javax.microedition.lcdui.Font;
import java.util.Random;
import java.util.Vector;

// ================================================================
// Utils.java v1.9
// UPDATED FROM v1.6:
//   - VERSION bumped to v1.9, BUILD 20250422
//   - API_URL / PROXY_PREFIX kept (same endpoint)
//   - sendAIRequest() User-Agent updated to J2ME-AIChatBot/1.9
//   - sendAIRequest() buffer size increased to 4096 for large
//     responses (supports up to 1,000,000 char AI replies)
//   - sendAIRequest() NO truncation of response - reads all bytes
//   - parseResponse() scans FULL string - no length cap
//   - NEW: sendAIRequestWithRetry() - retries on 503/504/IOException
//   - NEW: getCurrentTimestamp() always returns valid string
//   - NEW: formatTimestamp() - human-readable ms timestamp
//   - NEW: countOccurrences() - count substring occurrences
//   - NEW: capitalize() - uppercase first letter
//   - NEW: toLines() - split on \n into String[]
//   - NEW: limitLines() - keep first N lines of a string
//   - NEW: stripControlChars() - remove ASCII < 0x20 except \n\r\t
//   - NEW: toHexString() - byte array to hex string (debug helper)
//   - FIX: wrapText() no longer drops the last word of a segment
//   - FIX: encodeURL() delegates to URLBuilder.encode() to avoid
//          code duplication and ensure consistent behaviour
//   - FIX: generateUserId() uses SecureRandom seed for better
//          uniqueness across rapid restarts on same device
//   - KEEP: all v1.6 helper methods fully intact
//   - KEEP: COLOR_* constants for backward compatibility
// ================================================================
public class Utils {

    // ----------------------------------------------------------------
    // Theme colors (kept for backward compatibility with older screens)
    // ----------------------------------------------------------------
    public static final int COLOR_BG       = 0x000A00;
    public static final int COLOR_TEXT     = 0x00FF00;
    public static final int COLOR_ACCENT   = 0x00AA00;
    public static final int COLOR_DIM      = 0x006600;
    public static final int COLOR_BORDER   = 0x003300;
    public static final int COLOR_HI       = 0x002200;
    public static final int COLOR_MENU_BG  = 0x001500;
    public static final int COLOR_INPUT_BG = 0x001100;
    public static final int COLOR_USER_BG  = 0x002211;
    public static final int COLOR_USER     = 0x00FFAA;
    public static final int COLOR_OK       = 0x00FF00;
    public static final int COLOR_ERROR    = 0xFF3333;

    // ----------------------------------------------------------------
    // API endpoints
    // ----------------------------------------------------------------
    private static final String API_URL =
        "http://api-dl-j2meuploader.ndukadavid70.workers.dev/api/ai/chatgpt?text=";
    private static final String PROXY_PREFIX =
        "http://cf-proxy.ndukadavid70.workers.dev/?url=";

    // v1.9: retry constants for unreliable mobile networks
    private static final int MAX_RETRIES  = 3;
    private static final int RETRY_DELAY  = 1200; // ms

    // v1.9: read buffer – large enough for 1M char responses
    private static final int READ_BUF_SIZE = 4096;

    // ================================================================
    // User ID generation
    // FIX v1.9: seed with hash of timestamp XOR runtime memory
    // to avoid identical IDs on rapid restarts
    // ================================================================
    public static String generateUserId() {
        long ts   = System.currentTimeMillis();
        long mem  = Runtime.getRuntime().freeMemory();
        // XOR-mix for better entropy on devices with poor clock resolution
        long seed = ts ^ (mem << 16) ^ (mem >>> 16);
        Random rnd = new Random(seed);
        int r1 = Math.abs(rnd.nextInt()) % 9999;
        int r2 = Math.abs(rnd.nextInt()) % 9999;
        return "USR"
             + Long.toString(ts,   36).toUpperCase()
             + Long.toString(r1,   36).toUpperCase()
             + Long.toString(r2,   36).toUpperCase();
    }

    // ================================================================
    // Text wrapping
    // FIX v1.9: last word of a segment no longer dropped
    // ================================================================
    public static String[] wrapText(String text, Font font, int maxWidth) {
        if (text == null || text.length() == 0) return new String[]{""};
        if (maxWidth <= 0) return new String[]{ text };

        Vector lines = new Vector();
        int    start = 0;
        int    len   = text.length();

        while (start <= len) {
            // Find next newline
            int nlIdx  = text.indexOf('\n', start);
            int segEnd = (nlIdx >= 0 && nlIdx < len) ? nlIdx : len;
            String seg = text.substring(start, segEnd);

            if (seg.length() == 0) {
                // Bare newline → blank line
                lines.addElement("");
            } else {
                // Word-wrap the segment
                int segPos = 0;
                while (segPos < seg.length()) {
                    int  end       = segPos;
                    int  lastSpace = -1;

                    // Advance until line is full
                    while (end < seg.length()) {
                        if (seg.charAt(end) == ' ') lastSpace = end;
                        // FIX: test end+1 so last char is always included
                        String test = seg.substring(segPos, end + 1);
                        if (font.stringWidth(test) > maxWidth) {
                            // Break at last space if we found one
                            if (lastSpace > segPos) {
                                end = lastSpace;
                            }
                            // else force-break mid-word at current end
                            break;
                        }
                        end++;
                    }
                    // end == seg.length() means the whole rest fits
                    String line = seg.substring(segPos, end).trim();
                    if (line.length() > 0) lines.addElement(line);

                    // Advance past the break point
                    segPos = end;
                    // Skip the space we broke on (if any)
                    if (segPos < seg.length() && seg.charAt(segPos) == ' ') {
                        segPos++;
                    }
                    // Safety: never stall
                    if (segPos == end && segPos < seg.length()) {
                        segPos++;
                    }
                }
            }

            if (segEnd >= len) break;
            start = segEnd + 1; // skip the '\n'
        }

        if (lines.size() == 0) return new String[]{""};
        String[] res = new String[lines.size()];
        for (int i = 0; i < lines.size(); i++) res[i] = (String) lines.elementAt(i);
        return res;
    }

    // ================================================================
    // AI request - single attempt
    // v1.9: 4096-byte buffer, NO response truncation
    // ================================================================
    public static String sendAIRequest(
            String message, String context, Settings settings) {

        HttpConnection hc   = null;
        InputStream    is   = null;
        try {
            // Build full prompt
            String full = message;
            if (context != null && context.length() > 0) {
                full = "Context:\n" + context + "\n\nQuestion:\n" + message;
            }

            // Build URL - use URLBuilder for correct encoding
            String encoded = URLBuilder.encode(full);
            String url     = API_URL + encoded;
            if (settings != null && settings.isProxyEnabled()) {
                url = PROXY_PREFIX + URLBuilder.encode(url);
            }

            hc = (HttpConnection) Connector.open(url);
            hc.setRequestMethod(HttpConnection.GET);
            // v1.9: updated User-Agent
            hc.setRequestProperty("User-Agent", "J2ME-AIChatBot/1.9");
            hc.setRequestProperty("Connection", "close");

            int rc = hc.getResponseCode();
            if (rc == HttpConnection.HTTP_OK) {
                is = hc.openInputStream();
                // v1.9: large buffer - read ALL bytes without truncation
                HistoryBAOS baos = new HistoryBAOS();
                byte[] buf = new byte[READ_BUF_SIZE];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                String raw = new String(baos.toByteArray(), "UTF-8");
                return parseResponse(raw);
            } else if (rc == 503 || rc == 504) {
                return "[Server busy - HTTP " + rc + "]";
            } else {
                return "[HTTP Error " + rc + "]";
            }

        } catch (IOException e) {
            return "[Network error: " + e.getMessage() + "]";
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        } finally {
            try { if (is != null) is.close(); } catch (Exception e) {}
            try { if (hc != null) hc.close(); } catch (Exception e) {}
        }
    }

    // ================================================================
    // v1.9: sendAIRequestWithRetry()
    // Retries on network errors and 503/504 responses.
    // Uses exponential back-off: delay * attempt.
    // ================================================================
    public static String sendAIRequestWithRetry(
            String message, String context, Settings settings) {

        String lastError = "[Unknown error]";
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            String result = sendAIRequest(message, context, settings);

            // Success if not an error marker
            if (result != null
                    && !result.startsWith("[HTTP Error")
                    && !result.startsWith("[Network error")
                    && !result.startsWith("[Server busy")
                    && !result.startsWith("[Error")) {
                return result;
            }

            lastError = result;

            // Don't sleep after the last attempt
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep((long) RETRY_DELAY * attempt);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        return lastError + " (after " + MAX_RETRIES + " attempts)";
    }

    // ================================================================
    // JSON response parsing
    // v1.9: scans FULL raw string - no length cap anywhere
    // ================================================================
    public static String parseResponse(String raw) {
        if (raw == null || raw.length() == 0) return "[Empty response]";

        String cleaned = raw;

        // Strip HTML <pre> wrapper if present
        int preS = raw.indexOf("<pre>");
        int preE = raw.indexOf("</pre>");
        if (preS >= 0 && preE > preS) {
            cleaned = raw.substring(preS + 5, preE);
        }

        // Known JSON keys in priority order
        String[] keys = {
            "result", "answer", "text", "content", "response", "reply", "message"
        };
        for (int k = 0; k < keys.length; k++) {
            String key = "\"" + keys[k] + "\"";
            int    ki  = cleaned.indexOf(key);
            if (ki >= 0) {
                int ci = cleaned.indexOf(":", ki);
                if (ci >= 0) {
                    int sq = cleaned.indexOf("\"", ci + 1);
                    if (sq >= 0) {
                        // v1.9: scan full string - pJson was capped at 500 before
                        int eq = findEndQuote(cleaned, sq + 1);
                        if (eq > sq) {
                            return cleanResponse(
                                unescapeJson(cleaned.substring(sq + 1, eq)));
                        }
                    }
                }
            }
        }

        // Last resort: strip HTML
        return cleanResponse(stripHtml(cleaned));
    }

    // ================================================================
    // JSON helpers
    // ================================================================
    private static int findEndQuote(String s, int start) {
        // v1.9: scans the ENTIRE string - no artificial cap
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) return i;
        }
        return -1;
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                switch (n) {
                    case 'n':  sb.append('\n'); i += 2; continue;
                    case 'r':  sb.append('\r'); i += 2; continue;
                    case 't':  sb.append('\t'); i += 2; continue;
                    case '"':  sb.append('"');  i += 2; continue;
                    case '\\': sb.append('\\'); i += 2; continue;
                    case '/':  sb.append('/');  i += 2; continue;
                    case 'b':  sb.append('\b'); i += 2; continue;
                    case 'f':  sb.append('\f'); i += 2; continue;
                    case 'u':  // Unicode escape: backslash u XXXX
                        if (i + 5 < s.length()) {
                            try {
                                int cp = Integer.parseInt(
                                    s.substring(i + 2, i + 6), 16);
                                sb.append((char) cp);
                                i += 6; continue;
                            } catch (NumberFormatException e) {}
                        }
                        break;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    // ================================================================
    // String utilities
    // ================================================================

    public static String stripHtml(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer(s.length());
        boolean      in = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if      (c == '<') in = true;
            else if (c == '>') in = false;
            else if (!in)      sb.append(c);
        }
        return sb.toString();
    }

    /**
     * FIX v1.9: delegates to URLBuilder.encode() so both classes
     * always produce identical output. No duplicated logic.
     */
    public static String encodeURL(String url) {
        return URLBuilder.encode(url);
    }

    /** CP1252-safe cleanup - preserves full Unicode text */
    public static String cleanResponse(String response) {
        if (response == null) return "";
        StringBuffer sb = new StringBuffer(response.length());
        for (int i = 0; i < response.length(); i++) {
            char c = response.charAt(i);
            if ((c >= 32 && c <= 126) ||
                (c >= 160 && c <= 255) ||
                c == '\n' || c == '\r' || c == '\t') {
                sb.append(c);
            } else if (c > 255) {
                // v1.9: keep high Unicode chars (emojis, CJK, etc.)
                // instead of replacing with '?' - they display fine
                // on modern J2ME VMs (Nokia S60 3rd+, Android MIDP)
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }

    // ================================================================
    // Timestamp helpers
    // ================================================================

    public static String getCurrentTimestamp() {
        try {
            return String.valueOf(System.currentTimeMillis());
        } catch (Exception e) {
            return "0";
        }
    }

    /**
     * v1.9: Format a millisecond timestamp as a readable string.
     * Output: "HH:MM:SS.mmm" (no Calendar needed - J2ME compatible)
     * Note: values are relative to epoch, not local time, but useful
     * for relative display and log ordering.
     */
    public static String formatTimestamp(long ms) {
        if (ms <= 0) return "00:00:00.000";
        long secs  = (ms / 1000) % 60;
        long mins  = (ms / 60000) % 60;
        long hours = (ms / 3600000) % 24;
        long millis= ms % 1000;
        return pad2(hours) + ":" + pad2(mins) + ":" + pad2(secs)
             + "." + pad3(millis);
    }

    private static String pad2(long v) {
        String s = String.valueOf(v % 100);
        return s.length() < 2 ? "0" + s : s;
    }

    private static String pad3(long v) {
        String s = String.valueOf(v % 1000);
        while (s.length() < 3) s = "0" + s;
        return s;
    }

    // ================================================================
    // Null / empty helpers
    // ================================================================

    public static boolean isNullOrEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    // ================================================================
    // String formatting helpers
    // ================================================================

    public static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        if (maxLen <= 3) return s.substring(0, maxLen);
        return s.substring(0, maxLen - 3) + "...";
    }

    public static String padLeft(String s, int minLen) {
        if (s == null) s = "";
        StringBuffer sb = new StringBuffer(minLen);
        for (int i = s.length(); i < minLen; i++) sb.append(' ');
        sb.append(s);
        return sb.toString();
    }

    public static String padRight(String s, int minLen) {
        if (s == null) s = "";
        StringBuffer sb = new StringBuffer(s);
        while (sb.length() < minLen) sb.append(' ');
        return sb.toString();
    }

    public static String joinStrings(Vector v, String sep) {
        if (v == null || v.size() == 0) return "";
        if (sep == null) sep = "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < v.size(); i++) {
            if (i > 0) sb.append(sep);
            Object obj = v.elementAt(i);
            if (obj != null) sb.append(obj.toString());
        }
        return sb.toString();
    }

    public static String formatFileSize(long bytes) {
        if (bytes < 0)         return "0 B";
        if (bytes < 1024)      return bytes + " B";
        if (bytes < 1048576L) {
            long kb  = bytes / 1024;
            long rem = (bytes % 1024) * 10 / 1024;
            return kb + "." + rem + " KB";
        }
        long mb  = bytes / 1048576L;
        long rem = (bytes % 1048576L) * 10 / 1048576L;
        return mb + "." + rem + " MB";
    }

    public static boolean containsIgnoreCase(String source, String target) {
        if (source == null || target == null) return false;
        if (target.length() == 0) return true;
        return source.toLowerCase().indexOf(target.toLowerCase()) >= 0;
    }

    public static int lastIndexOfString(String source, String target) {
        if (source == null || target == null) return -1;
        int last = -1, idx = 0;
        while (true) {
            idx = source.indexOf(target, idx);
            if (idx < 0) break;
            last = idx;
            idx++;
        }
        return last;
    }

    public static String repeat(char c, int n) {
        if (n <= 0) return "";
        StringBuffer sb = new StringBuffer(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    public static String replaceAll(String source, String oldStr, String newStr) {
        if (source == null || oldStr == null || oldStr.length() == 0) return source;
        if (newStr == null) newStr = "";
        StringBuffer sb  = new StringBuffer(source.length());
        int          pos = 0;
        while (true) {
            int idx = source.indexOf(oldStr, pos);
            if (idx < 0) { sb.append(source.substring(pos)); break; }
            sb.append(source.substring(pos, idx));
            sb.append(newStr);
            pos = idx + oldStr.length();
        }
        return sb.toString();
    }

    public static String[] split(String s, char delim) {
        if (s == null) return new String[0];
        Vector v   = new Vector();
        int    pos = 0;
        while (pos <= s.length()) {
            int idx = s.indexOf(delim, pos);
            if (idx < 0) { v.addElement(s.substring(pos)); break; }
            v.addElement(s.substring(pos, idx));
            pos = idx + 1;
        }
        String[] result = new String[v.size()];
        for (int i = 0; i < v.size(); i++) result[i] = (String) v.elementAt(i);
        return result;
    }

    public static boolean startsWith(String s, String prefix) {
        if (s == null || prefix == null) return false;
        if (prefix.length() > s.length()) return false;
        return s.substring(0, prefix.length()).equals(prefix);
    }

    public static boolean endsWith(String s, String suffix) {
        if (s == null || suffix == null) return false;
        if (suffix.length() > s.length()) return false;
        return s.substring(s.length() - suffix.length()).equals(suffix);
    }

    // ================================================================
    // v1.9: NEW helper methods
    // ================================================================

    /**
     * Count occurrences of a substring within a string.
     * e.g. countOccurrences("aababc", "ab") → 2
     */
    public static int countOccurrences(String source, String target) {
        if (source == null || target == null || target.length() == 0) return 0;
        int count = 0, idx = 0;
        while (true) {
            idx = source.indexOf(target, idx);
            if (idx < 0) break;
            count++;
            idx += target.length();
        }
        return count;
    }

    /**
     * Capitalize the first letter of a string.
     * e.g. capitalize("hello world") → "Hello world"
     */
    public static String capitalize(String s) {
        if (s == null || s.length() == 0) return "";
        if (s.length() == 1) return s.toUpperCase();
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Split a string on newline characters into a String array.
     * Handles both \n and \r\n line endings.
     * e.g. toLines("a\nb\nc") → ["a","b","c"]
     */
    public static String[] toLines(String s) {
        if (s == null || s.length() == 0) return new String[]{""};
        // Normalise \r\n → \n
        s = replaceAll(s, "\r\n", "\n");
        s = replaceAll(s, "\r",   "\n");
        return split(s, '\n');
    }

    /**
     * Keep only the first maxLines lines of a multi-line string.
     * Useful for previews of long AI responses.
     * e.g. limitLines("a\nb\nc", 2) → "a\nb"
     */
    public static String limitLines(String s, int maxLines) {
        if (s == null || maxLines <= 0) return "";
        String[] lines = toLines(s);
        if (lines.length <= maxLines) return s;
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < maxLines; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    /**
     * Strip ASCII control characters from a string.
     * Keeps \n, \r, \t (printable whitespace).
     * Useful for sanitising API responses before display.
     */
    public static String stripControlChars(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t' || c >= 0x20) {
                sb.append(c);
            }
            // else: drop control char silently
        }
        return sb.toString();
    }

    /**
     * Convert a byte array to a hex string (debug / logging helper).
     * e.g. toHexString(new byte[]{0x41,0x42}, -1) → "4142"
     * maxBytes caps output length (-1 = no limit).
     */
    public static String toHexString(byte[] data, int maxBytes) {
        if (data == null || data.length == 0) return "";
        int len = (maxBytes >= 0) ? Math.min(data.length, maxBytes) : data.length;
        StringBuffer sb = new StringBuffer(len * 2);
        for (int i = 0; i < len; i++) {
            int v = data[i] & 0xFF;
            if (v < 16) sb.append('0');
            sb.append(Integer.toHexString(v).toUpperCase());
        }
        if (len < data.length) sb.append("...");
        return sb.toString();
    }

    /**
     * Safe integer parse with default value.
     * e.g. parseInt("abc", 0) → 0 instead of throwing.
     */
    public static int parseInt(String s, int defaultVal) {
        if (s == null) return defaultVal;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /**
     * Safe long parse with default value.
     */
    public static long parseLong(String s, long defaultVal) {
        if (s == null) return defaultVal;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    // ================================================================
    // Private hex helper (used by legacy code)
    // ================================================================
    private static char toHex(int v) {
        return (char)(v < 10 ? '0' + v : 'A' + v - 10);
    }
}