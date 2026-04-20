import java.io.*;
import javax.microedition.io.*;
import javax.microedition.lcdui.Font;
import java.util.Random;
import java.util.Vector;

// ================================================================
// Utils.java v1.6
// UPDATED FROM v1.2:
//   - Version bumped to v1.6
//   - API_URL updated to ndukadavid70 endpoint
//   - PROXY_PREFIX updated to cf-proxy.ndukadavid70
//   - sendAIRequest() User-Agent updated to J2ME-AIChatBot/1.6
//   - sendAIRequest() uses HistoryBAOS instead of ByteArrayOutputStream
//   - parseResponse() added "reply" JSON key
//   - encodeURL() handles multi-byte chars (>127) correctly
//   - generateUserId() improved uniqueness (adds random suffix)
//   - wrapText() handles null lines and newline chars better
//   - New: truncate() - safe string truncation helper
//   - New: padLeft() / padRight() - string padding helpers
//   - New: isNullOrEmpty() - null-safe empty check
//   - New: joinStrings() - join Vector of strings with separator
//   - New: formatFileSize() - human readable byte count
//   - New: containsIgnoreCase() - case-insensitive search
//   - Theme colors preserved (not used by v1.6 Pal system
//     but kept for backward compatibility)
// ================================================================
public class Utils {

    // ----------------------------------------------------------------
    // Theme colors (kept for backward compatibility)
    // v1.6 UI uses Pal class instead
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
    // API endpoints (v1.6)
    // ----------------------------------------------------------------
    private static final String API_URL =
        "http://api-dl-j2meuploader.ndukadavid70.workers.dev/api/ai/chatgpt?text=";
    private static final String PROXY_PREFIX =
        "http://cf-proxy.ndukadavid70.workers.dev/?url=";

    // ================================================================
    // User ID generation (v1.6: improved uniqueness)
    // ================================================================

    public static String generateUserId() {
        Random rnd = new Random(System.currentTimeMillis());
        long   ts  = System.currentTimeMillis();
        int    r1  = Math.abs(rnd.nextInt() % 9999);
        int    r2  = Math.abs(rnd.nextInt() % 99);
        // Format: USR + base36 timestamp + random suffix
        return "USR"
             + Long.toString(ts, 36).toUpperCase()
             + r1
             + r2;
    }

    // ================================================================
    // Text wrapping (v1.6: handles \n and null lines better)
    // ================================================================

    public static String[] wrapText(String text, Font font, int maxWidth) {
        if (text == null || text.length() == 0) return new String[]{""};

        Vector lines = new Vector();
        int    start = 0;
        int    len   = text.length();

        while (start < len) {
            // Handle explicit newline
            int nlIdx = text.indexOf('\n', start);

            // Find segment end (either newline or end of string)
            int segEnd = (nlIdx >= 0) ? nlIdx : len;
            String seg = text.substring(start, segEnd);

            // Word-wrap the segment to maxWidth
            int segStart = 0;
            while (segStart < seg.length()) {
                int  end       = segStart;
                int  lastSpace = segStart;

                while (end < seg.length()) {
                    char c = seg.charAt(end);
                    if (c == ' ') lastSpace = end;
                    String test = seg.substring(segStart, end + 1);
                    if (font.stringWidth(test) > maxWidth) {
                        // Break at last space if possible
                        if (lastSpace > segStart) {
                            end = lastSpace;
                        }
                        break;
                    }
                    end++;
                }
                // end == seg.length() means whole remaining segment fits
                String line = seg.substring(segStart, end).trim();
                if (line.length() > 0) lines.addElement(line);
                segStart = end + (end < seg.length() && seg.charAt(end) == ' ' ? 1 : 0);
                // Avoid infinite loop on very narrow width
                if (segStart == end && end < seg.length()) segStart = end + 1;
            }

            // Empty line from bare \n
            if (seg.length() == 0) lines.addElement("");

            start = segEnd + 1; // skip \n
        }

        if (lines.size() == 0) return new String[]{""};
        String[] res = new String[lines.size()];
        for (int i = 0; i < lines.size(); i++) res[i] = (String)lines.elementAt(i);
        return res;
    }

    // ================================================================
    // AI request (v1.6: updated User-Agent, uses HistoryBAOS)
    // ================================================================

    public static String sendAIRequest(String message, String context, Settings settings) {
        HttpConnection hc   = null;
        InputStream    is   = null;
        HistoryBAOS    baos = null;
        try {
            String full = message;
            if (context != null && context.length() > 0) {
                full = "Context:\n" + context + "\n\nQuestion:\n" + message;
            }

            String url = API_URL + encodeURL(full);
            if (settings != null && settings.isProxyEnabled()) {
                url = PROXY_PREFIX + encodeURL(url);
            }

            hc = (HttpConnection)Connector.open(url);
            hc.setRequestMethod(HttpConnection.GET);
            // v1.6: updated User-Agent
            hc.setRequestProperty("User-Agent", "J2ME-AIChatBot/1.6");
            hc.setRequestProperty("Connection", "close");

            int rc = hc.getResponseCode();
            if (rc == HttpConnection.HTTP_OK) {
                is   = hc.openInputStream();
                // v1.6: use HistoryBAOS (no java.io.ByteArrayOutputStream needed)
                baos = new HistoryBAOS();
                byte[] buf = new byte[512]; int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
                String raw = new String(baos.toByteArray(), "UTF-8");
                return parseResponse(raw);
            } else {
                return "[HTTP Error " + rc + "]";
            }
        } catch (IOException e) {
            return "[Network error: " + e.getMessage() + "]";
        } catch (Exception e) {
            return "[Error: " + e.getMessage() + "]";
        } finally {
            try { if (is   != null) is.close();   } catch (Exception e) {}
            try { if (hc   != null) hc.close();   } catch (Exception e) {}
        }
    }

    // ================================================================
    // JSON response parsing (v1.6: added "reply" key)
    // ================================================================

    public static String parseResponse(String raw) {
        if (raw == null || raw.length() == 0) return "[Empty response]";

        String cleaned = raw;

        // Try <pre> block (some proxies wrap in HTML)
        int preS = raw.indexOf("<pre>");
        int preE = raw.indexOf("</pre>");
        if (preS >= 0 && preE > preS) {
            cleaned = raw.substring(preS + 5, preE);
        }

        // v1.6: added "reply" to known keys
        String[] keys = {"result", "answer", "text", "content", "response", "reply"};
        for (int k = 0; k < keys.length; k++) {
            String key = "\"" + keys[k] + "\"";
            int    ki  = cleaned.indexOf(key);
            if (ki >= 0) {
                int ci = cleaned.indexOf(":", ki);
                if (ci >= 0) {
                    int sq = cleaned.indexOf("\"", ci + 1);
                    if (sq >= 0) {
                        int eq = findEndQuote(cleaned, sq + 1);
                        if (eq > sq) {
                            return cleanResponse(
                                unescapeJson(cleaned.substring(sq + 1, eq)));
                        }
                    }
                }
            }
        }

        // Last resort: strip HTML and clean
        return cleanResponse(stripHtml(cleaned));
    }

    private static int findEndQuote(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i-1) != '\\')) return i;
        }
        return -1;
    }

    private static String unescapeJson(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer();
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
                }
            }
            sb.append(c); i++;
        }
        return sb.toString();
    }

    // ================================================================
    // String utilities
    // ================================================================

    public static String stripHtml(String s) {
        if (s == null) return "";
        StringBuffer sb  = new StringBuffer();
        boolean      in  = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if      (c == '<') in = true;
            else if (c == '>') in = false;
            else if (!in)      sb.append(c);
        }
        return sb.toString();
    }

    // v1.6: handles multi-byte chars > 127 via UTF-8 encoding
    public static String encodeURL(String url) {
        if (url == null) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
                (c >= '0' && c <= '9') ||
                c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append("%20");
            } else if (c <= 0x7F) {
                // Single byte ASCII
                sb.append('%');
                sb.append(toHex((c >> 4) & 0x0F));
                sb.append(toHex(c & 0x0F));
            } else if (c <= 0x7FF) {
                // Two-byte UTF-8
                int b1 = 0xC0 | ((c >> 6) & 0x1F);
                int b2 = 0x80 | (c & 0x3F);
                sb.append('%'); sb.append(toHex((b1>>4)&0xF)); sb.append(toHex(b1&0xF));
                sb.append('%'); sb.append(toHex((b2>>4)&0xF)); sb.append(toHex(b2&0xF));
            } else {
                // Three-byte UTF-8
                int b1 = 0xE0 | ((c >> 12) & 0x0F);
                int b2 = 0x80 | ((c >>  6) & 0x3F);
                int b3 = 0x80 | (c & 0x3F);
                sb.append('%'); sb.append(toHex((b1>>4)&0xF)); sb.append(toHex(b1&0xF));
                sb.append('%'); sb.append(toHex((b2>>4)&0xF)); sb.append(toHex(b2&0xF));
                sb.append('%'); sb.append(toHex((b3>>4)&0xF)); sb.append(toHex(b3&0xF));
            }
        }
        return sb.toString();
    }

    private static char toHex(int v) {
        return (char)(v < 10 ? '0' + v : 'A' + v - 10);
    }

    // CP1252-safe cleanup
    public static String cleanResponse(String response) {
        if (response == null) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < response.length(); i++) {
            char c = response.charAt(i);
            if ((c >= 32 && c <= 126) || (c >= 160 && c <= 255) ||
                c == '\n' || c == '\r' || c == '\t') {
                sb.append(c);
            } else if (c > 255) {
                sb.append('?');
            }
        }
        return sb.toString().trim();
    }

    // ================================================================
    // Timestamp
    // ================================================================

    public static String getCurrentTimestamp() {
        return String.valueOf(System.currentTimeMillis());
    }

    // ================================================================
    // v1.6: New helper methods
    // ================================================================

    /**
     * Null-safe empty check
     */
    public static boolean isNullOrEmpty(String s) {
        return s == null || s.trim().length() == 0;
    }

    /**
     * Safe string truncation with ellipsis
     * e.g. truncate("Hello World", 8) -> "Hello..."
     */
    public static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        if (maxLen <= 3) return s.substring(0, maxLen);
        return s.substring(0, maxLen - 3) + "...";
    }

    /**
     * Pad string on the left with spaces to reach minLen
     * e.g. padLeft("AI", 5) -> "   AI"
     */
    public static String padLeft(String s, int minLen) {
        if (s == null) s = "";
        StringBuffer sb = new StringBuffer();
        for (int i = s.length(); i < minLen; i++) sb.append(' ');
        sb.append(s);
        return sb.toString();
    }

    /**
     * Pad string on the right with spaces to reach minLen
     * e.g. padRight("AI", 5) -> "AI   "
     */
    public static String padRight(String s, int minLen) {
        if (s == null) s = "";
        StringBuffer sb = new StringBuffer(s);
        while (sb.length() < minLen) sb.append(' ');
        return sb.toString();
    }

    /**
     * Join a Vector of strings with a separator
     * e.g. joinStrings(["a","b","c"], ", ") -> "a, b, c"
     */
    public static String joinStrings(Vector v, String sep) {
        if (v == null || v.size() == 0) return "";
        if (sep == null) sep = "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < v.size(); i++) {
            if (i > 0) sb.append(sep);
            String item = (String)v.elementAt(i);
            if (item != null) sb.append(item);
        }
        return sb.toString();
    }

    /**
     * Format byte count as human-readable size string
     * e.g. 1536 -> "1.5 KB"
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 0)          return "0 B";
        if (bytes < 1024)       return bytes + " B";
        if (bytes < 1024*1024)  {
            long kb  = bytes / 1024;
            long rem = (bytes % 1024) * 10 / 1024;
            return kb + "." + rem + " KB";
        }
        long mb  = bytes / (1024*1024);
        long rem = (bytes % (1024*1024)) * 10 / (1024*1024);
        return mb + "." + rem + " MB";
    }

    /**
     * Case-insensitive substring search
     */
    public static boolean containsIgnoreCase(String source, String target) {
        if (source == null || target == null) return false;
        if (target.length() == 0) return true;
        return source.toLowerCase().indexOf(target.toLowerCase()) >= 0;
    }

    /**
     * Last index of target string within source (J2ME compatible)
     */
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

    /**
     * Repeat a character n times
     * e.g. repeat('-', 10) -> "----------"
     */
    public static String repeat(char c, int n) {
        if (n <= 0) return "";
        StringBuffer sb = new StringBuffer(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    /**
     * Replace all occurrences of oldStr with newStr (J2ME compatible)
     */
    public static String replaceAll(String source, String oldStr, String newStr) {
        if (source == null || oldStr == null || oldStr.length() == 0) return source;
        if (newStr == null) newStr = "";
        StringBuffer sb  = new StringBuffer();
        int          pos = 0;
        while (true) {
            int idx = source.indexOf(oldStr, pos);
            if (idx < 0) {
                sb.append(source.substring(pos));
                break;
            }
            sb.append(source.substring(pos, idx));
            sb.append(newStr);
            pos = idx + oldStr.length();
        }
        return sb.toString();
    }

    /**
     * Split string by delimiter (J2ME has no String.split())
     * e.g. split("a,b,c", ',') -> ["a","b","c"]
     */
    public static String[] split(String s, char delim) {
        if (s == null) return new String[0];
        Vector v   = new Vector();
        int    pos = 0;
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

    /**
     * Check if string starts with prefix (null-safe)
     */
    public static boolean startsWith(String s, String prefix) {
        if (s == null || prefix == null) return false;
        if (prefix.length() > s.length()) return false;
        return s.substring(0, prefix.length()).equals(prefix);
    }

    /**
     * Check if string ends with suffix (null-safe)
     */
    public static boolean endsWith(String s, String suffix) {
        if (s == null || suffix == null) return false;
        if (suffix.length() > s.length()) return false;
        return s.substring(s.length() - suffix.length()).equals(suffix);
    }
}