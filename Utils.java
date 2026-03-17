import java.io.*;
import javax.microedition.io.*;
import javax.microedition.lcdui.Font;
import java.util.Random;
import java.util.Vector;

public class Utils {
    // Theme Colors (Matrix style)
    public static final int COLOR_BG = 0x000A00;
    public static final int COLOR_TEXT = 0x00FF00;
    public static final int COLOR_ACCENT = 0x00AA00;
    public static final int COLOR_DIM = 0x006600;
    public static final int COLOR_BORDER = 0x003300;
    public static final int COLOR_HI = 0x002200;
    public static final int COLOR_MENU_BG = 0x001500;
    public static final int COLOR_INPUT_BG = 0x001100;
    public static final int COLOR_USER_BG = 0x002211;
    public static final int COLOR_USER = 0x00FFAA;
    public static final int COLOR_OK = 0x00FF00;
    public static final int COLOR_ERROR = 0xFF3333;
    
    private static final String API_URL = 
        "https://api-dl-j2meuploader.ndukadavid70.workers.dev/api/ai/chatgpt?text=";
    
    public static String generateUserId() {
        Random random = new Random();
        long timestamp = System.currentTimeMillis();
        int randomNum = Math.abs(random.nextInt() % 9999);
        return "USR" + Long.toString(timestamp, 36).toUpperCase() + randomNum;
    }
    
    public static String[] wrapText(String text, Font font, int maxWidth) {
        if (text == null || text.length() == 0) {
            return new String[]{""};
        }
        
        Vector lines = new Vector();
        int start = 0;
        
        while (start < text.length()) {
            int end = start;
            int lastSpace = start;
            
            while (end < text.length()) {
                char c = text.charAt(end);
                if (c == '\n') {
                    break;
                }
                if (c == ' ') {
                    lastSpace = end;
                }
                
                String sub = text.substring(start, end + 1);
                if (font.stringWidth(sub) > maxWidth) {
                    if (lastSpace > start) {
                        end = lastSpace;
                    }
                    break;
                }
                end++;
            }
            
            if (end >= text.length()) {
                end = text.length();
            }
            
            String line = text.substring(start, end).trim();
            if (line.length() > 0) {
                lines.addElement(line);
            }
            
            start = end + 1;
        }
        
        if (lines.size() == 0) {
            return new String[]{""};
        }
        
        String[] result = new String[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            result[i] = (String) lines.elementAt(i);
        }
        return result;
    }
    
    public static int lastIndexOfString(String source, String target) {
        if (source == null || target == null) return -1;
        
        int lastIndex = -1;
        int index = 0;
        
        while (true) {
            index = source.indexOf(target, index);
            if (index == -1) break;
            lastIndex = index;
            index++;
        }
        
        return lastIndex;
    }
    
    public static String sendAIRequest(String message, String context, Settings settings) {
        HttpConnection conn = null;
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        
        try {
            String fullMessage = message;
            if (context != null && context.length() > 0) {
                fullMessage = "Contexte:\n" + context + "\n\nQuestion:\n" + message;
            }
            
            String url = API_URL + encodeURL(fullMessage);
            
            conn = (HttpConnection) Connector.open(url);
            conn.setRequestMethod(HttpConnection.GET);
            conn.setRequestProperty("User-Agent", "J2ME-AI-ChatBot/1.1");
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Connection", "close");
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode == HttpConnection.HTTP_OK) {
                is = conn.openInputStream();
                baos = new ByteArrayOutputStream();
                
                byte[] buffer = new byte[512];
                int bytesRead;
                
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                
                String raw = new String(baos.toByteArray(), "UTF-8");
                return parseResponse(raw);
            } else {
                return "[Erreur HTTP " + responseCode + "]";
            }
            
        } catch (IOException e) {
            return "[Erreur: " + e.getMessage() + "]";
        } catch (Exception e) {
            return "[Erreur: " + e.getMessage() + "]";
        } finally {
            try {
                if (baos != null) baos.close();
                if (is != null) is.close();
                if (conn != null) conn.close();
            } catch (IOException e) {}
        }
    }
    
    public static String parseResponse(String raw) {
        if (raw == null || raw.length() == 0) {
            return "[Reponse vide]";
        }
        
        String cleaned = raw;
        
        // Extract from <pre> tags
        int preStart = raw.indexOf("<pre>");
        int preEnd = raw.indexOf("</pre>");
        if (preStart >= 0 && preEnd > preStart) {
            cleaned = raw.substring(preStart + 5, preEnd);
        }
        
        // Try JSON keys
        String[] jsonKeys = {"result", "answer", "text", "content", "response"};
        
        for (int k = 0; k < jsonKeys.length; k++) {
            String key = "\"" + jsonKeys[k] + "\"";
            int keyIdx = cleaned.indexOf(key);
            
            if (keyIdx >= 0) {
                int colonIdx = cleaned.indexOf(":", keyIdx);
                if (colonIdx >= 0) {
                    int startQuote = cleaned.indexOf("\"", colonIdx + 1);
                    if (startQuote >= 0) {
                        int endQuote = findEndQuote(cleaned, startQuote + 1);
                        if (endQuote > startQuote) {
                            String value = cleaned.substring(startQuote + 1, endQuote);
                            return cleanResponse(unescapeJson(value));
                        }
                    }
                }
            }
        }
        
        return cleanResponse(stripHtml(cleaned));
    }
    
    private static int findEndQuote(String s, int start) {
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                return i;
            }
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
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n': sb.append('\n'); i += 2; continue;
                    case 'r': sb.append('\r'); i += 2; continue;
                    case 't': sb.append('\t'); i += 2; continue;
                    case '"': sb.append('"'); i += 2; continue;
                    case '\\': sb.append('\\'); i += 2; continue;
                }
            }
            
            sb.append(c);
            i++;
        }
        
        return sb.toString();
    }
    
    public static String stripHtml(String s) {
        if (s == null) return "";
        
        StringBuffer result = new StringBuffer();
        boolean inTag = false;
        
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            
            if (c == '<') {
                inTag = true;
            } else if (c == '>') {
                inTag = false;
            } else if (!inTag) {
                result.append(c);
            }
        }
        
        return result.toString();
    }
    
    public static String encodeURL(String url) {
        if (url == null) return "";
        
        StringBuffer encoded = new StringBuffer();
        
        for (int i = 0; i < url.length(); i++) {
            char c = url.charAt(i);
            
            if ((c >= 'A' && c <= 'Z') || 
                (c >= 'a' && c <= 'z') || 
                (c >= '0' && c <= '9') ||
                c == '-' || c == '_' || c == '.' || c == '~') {
                encoded.append(c);
            } else if (c == ' ') {
                encoded.append("%20");
            } else {
                encoded.append('%');
                encoded.append(toHex((c >> 4) & 0x0F));
                encoded.append(toHex(c & 0x0F));
            }
        }
        
        return encoded.toString();
    }
    
    private static char toHex(int value) {
        return (char)(value < 10 ? '0' + value : 'A' + value - 10);
    }
    
    public static String cleanResponse(String response) {
        if (response == null) return "";
        
        StringBuffer cleaned = new StringBuffer();
        
        for (int i = 0; i < response.length(); i++) {
            char c = response.charAt(i);
            
            // CP1252 compatible characters
            if ((c >= 32 && c <= 126) || 
                (c >= 160 && c <= 255) || 
                c == '\n' || c == '\r' || c == '\t') {
                cleaned.append(c);
            } else if (c > 255) {
                cleaned.append('?');
            }
        }
        
        return cleaned.toString().trim();
    }
    
    public static String getCurrentTimestamp() {
        return String.valueOf(System.currentTimeMillis());
    }
}