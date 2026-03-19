// =============================================================
// URLBuilder.java v1.2
// URL-encoding helper for J2ME (no java.net.URLEncoder)
// =============================================================
public class URLBuilder {

    public static String encode(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append("%20");
            } else {
                sb.append('%');
                String hex = Integer.toHexString(c).toUpperCase();
                if (hex.length() < 2) sb.append('0');
                sb.append(hex);
            }
        }
        return sb.toString();
    }
}
