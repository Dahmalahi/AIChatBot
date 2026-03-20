import java.io.*;
import javax.microedition.io.*;
import javax.microedition.lcdui.Font;
import java.util.Random;
import java.util.Vector;

// =============================================================
// Utils.java v1.2
// Theme: Matrix green on black, CP1252 safe
// =============================================================
public class Utils {

    // ---- Theme colors ----
    public static final int COLOR_BG        = 0x000A00;
    public static final int COLOR_TEXT       = 0x00FF00;
    public static final int COLOR_ACCENT     = 0x00AA00;
    public static final int COLOR_DIM        = 0x006600;
    public static final int COLOR_BORDER     = 0x003300;
    public static final int COLOR_HI         = 0x002200;
    public static final int COLOR_MENU_BG    = 0x001500;
    public static final int COLOR_INPUT_BG   = 0x001100;
    public static final int COLOR_USER_BG    = 0x002211;
    public static final int COLOR_USER       = 0x00FFAA;
    public static final int COLOR_OK         = 0x00FF00;
    public static final int COLOR_ERROR      = 0xFF3333;

    // ---- API ----
    private static final String API_URL =
        "http://api-dl-j2meuploader.ndukadavid70.workers.dev/api/ai/chatgpt?text=";
    private static final String PROXY_PREFIX =
        "http://nnp.nnchan.ru/glype/browse.php?u=";

    // ---- Generate unique user ID ----
    public static String generateUserId() {
        Random rnd = new Random();
        long ts = System.currentTimeMillis();
        int r = Math.abs(rnd.nextInt() % 9999);
        return "USR" + Long.toString(ts, 36).toUpperCase() + r;
    }

    // ---- Word-wrap for a string into a fixed pixel width ----
    public static String[] wrapText(String text, Font font, int maxWidth) {
        if (text == null || text.length() == 0) return new String[]{""};
        Vector lines = new Vector();
        int start = 0;
        while (start < text.length()) {
            int end = start, lastSpace = start;
            while (end < text.length()) {
                char c = text.charAt(end);
                if (c == '\n') break;
                if (c == ' ') lastSpace = end;
                if (font.stringWidth(text.substring(start, end+1)) > maxWidth) {
                    if (lastSpace > start) end = lastSpace;
                    break;
                }
                end++;
            }
            if (end >= text.length()) end = text.length();
            String line = text.substring(start, end).trim();
            if (line.length() > 0) lines.addElement(line);
            start = end + 1;
        }
        if (lines.size() == 0) return new String[]{""};
        String[] res = new String[lines.size()];
        for (int i=0;i<lines.size();i++) res[i]=(String)lines.elementAt(i);
        return res;
    }

    // ---- Send AI request (used by standalone callers) ----
    public static String sendAIRequest(String message, String context, Settings settings) {
        HttpConnection conn = null;
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        try {
            String full = message;
            if (context != null && context.length() > 0) {
                full = "Context:\n" + context + "\n\nQuestion:\n" + message;
            }
            String url = API_URL + encodeURL(full);
            if (settings != null && settings.isProxyEnabled()) {
                url = PROXY_PREFIX + encodeURL(url);
            }

            conn = (HttpConnection) Connector.open(url);
            conn.setRequestMethod(HttpConnection.GET);
            conn.setRequestProperty("User-Agent", "J2ME-AIChatBot/1.2");
            conn.setRequestProperty("Connection", "close");

            int rc = conn.getResponseCode();
            if (rc == HttpConnection.HTTP_OK) {
                is   = conn.openInputStream();
                baos = new ByteArrayOutputStream();
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
            try { if (baos != null) baos.close(); } catch (Exception e) {}
            try { if (is   != null) is.close();   } catch (Exception e) {}
            try { if (conn != null) conn.close();  } catch (Exception e) {}
        }
    }

    // ---- Parse API JSON response ----
    public static String parseResponse(String raw) {
        if (raw == null || raw.length() == 0) return "[Empty response]";

        String cleaned = raw;

        // Try <pre> block
        int preS = raw.indexOf("<pre>"), preE = raw.indexOf("</pre>");
        if (preS >= 0 && preE > preS) cleaned = raw.substring(preS+5, preE);

        // Try known JSON keys
        String[] keys = {"result","answer","text","content","response"};
        for (int k=0;k<keys.length;k++) {
            String key = "\"" + keys[k] + "\"";
            int ki = cleaned.indexOf(key);
            if (ki >= 0) {
                int ci = cleaned.indexOf(":", ki);
                if (ci >= 0) {
                    int sq = cleaned.indexOf("\"", ci+1);
                    if (sq >= 0) {
                        int eq = findEndQuote(cleaned, sq+1);
                        if (eq > sq) return cleanResponse(unescapeJson(cleaned.substring(sq+1, eq)));
                    }
                }
            }
        }
        return cleanResponse(stripHtml(cleaned));
    }

    private static int findEndQuote(String s, int start) {
        for (int i=start;i<s.length();i++) {
            char c=s.charAt(i);
            if (c=='"' && (i==0||s.charAt(i-1)!='\\')) return i;
        }
        return -1;
    }

    private static String unescapeJson(String s) {
        if (s==null) return "";
        StringBuffer sb=new StringBuffer(); int i=0;
        while (i<s.length()) {
            char c=s.charAt(i);
            if (c=='\\' && i+1<s.length()) {
                char n=s.charAt(i+1);
                switch(n){
                    case 'n': sb.append('\n'); i+=2; continue;
                    case 'r': sb.append('\r'); i+=2; continue;
                    case 't': sb.append('\t'); i+=2; continue;
                    case '"': sb.append('"');  i+=2; continue;
                    case '\\':sb.append('\\'); i+=2; continue;
                }
            }
            sb.append(c); i++;
        }
        return sb.toString();
    }

    public static String stripHtml(String s) {
        if (s==null) return "";
        StringBuffer sb=new StringBuffer(); boolean in=false;
        for (int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if(c=='<')in=true; else if(c=='>')in=false; else if(!in)sb.append(c);
        }
        return sb.toString();
    }

    public static String encodeURL(String url) {
        if (url==null) return "";
        StringBuffer sb=new StringBuffer();
        for (int i=0;i<url.length();i++){
            char c=url.charAt(i);
            if((c>='A'&&c<='Z')||(c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='-'||c=='_'||c=='.'||c=='~') sb.append(c);
            else if(c==' ') sb.append("%20");
            else { sb.append('%'); sb.append(toHex((c>>4)&0x0F)); sb.append(toHex(c&0x0F)); }
        }
        return sb.toString();
    }

    private static char toHex(int v) { return (char)(v<10?'0'+v:'A'+v-10); }

    // CP1252-safe cleanup
    public static String cleanResponse(String response) {
        if (response==null) return "";
        StringBuffer sb=new StringBuffer();
        for (int i=0;i<response.length();i++){
            char c=response.charAt(i);
            if((c>=32&&c<=126)||(c>=160&&c<=255)||c=='\n'||c=='\r'||c=='\t') sb.append(c);
            else if(c>255) sb.append('?');
        }
        return sb.toString().trim();
    }

    public static String getCurrentTimestamp() {
        return String.valueOf(System.currentTimeMillis());
    }

    // Last index of a string within a string (J2ME compatible)
    public static int lastIndexOfString(String source, String target) {
        if (source==null||target==null) return -1;
        int last=-1, idx=0;
        while (true) { idx=source.indexOf(target,idx); if(idx<0)break; last=idx; idx++; }
        return last;
    }
}
