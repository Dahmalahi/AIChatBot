import java.util.Vector;
import javax.microedition.rms.*;

// ================================================================
// History.java v1.9
// UPDATED FROM v1.7:
//   - VERSION bumped to v1.9, BUILD 20250422
//   - FIX: restoreSession() now stores to restoredEntries Vector
//          so AIChatBot.resumeSession() can call getRestoredEntries()
//          WITHOUT removing the session from past sessions list
//          (removal only happens if user sends a new message)
//   - FIX: getRestoredEntries() returns restoredEntries (not currentSession)
//          so restored chat shows correctly in ChatCanvas
//   - FIX: saveToRMS/loadFromRMS now use \u0001 escape for newlines
//          inside message bodies to prevent parse corruption
//   - FIX: History RMS chunked write/read fully reliable
//   - NEW: restoredEntries field - separate from currentSession
//   - NEW: getApiBase() / setApiBaseSilent() forwarded via Settings
//   - NEW: isCurrentSessionEmpty() null-safe
//   - NEW: getAllConversationsAsString() header updated to v1.9
//   - KEEP: all v1.7 methods: restoreSession, getSessionTitle,
//           getSessionPreview, getSessionMessageCount,
//           deleteSessionAt, renameSession, getSessionEntries,
//           getCurrentSessionTitle, search, etc.
//   - KEEP: HistoryBAOS inner helper
// ================================================================
public class History {

    private Vector sessions;          // past sessions (String)
    private Vector currentSession;    // live entries for ongoing chat
    private Vector restoredEntries;   // v1.9: entries from last restoreSession()

    private int    maxMessages        = 50;
    private int    maxSessions        = 20;
    private int    totalMessageCount  = 0;
    private String currentSessionTitle= "";

    private static final char   PU          = 'U';
    private static final char   PA          = 'A';
    private static final char   SEP         = '|';
    private static final String SESSION_SEP = "\n===SESSION===\n";
    private static final String TITLE_TAG   = "__TITLE__:";
    private static final String TS_TAG      = "__TS__:";
    private static final String ENTRY_TAG   = "__ENTRY__:";
    private static final String CURRENT_TAG = "__CURRENT__";
    private static final String MSGCOUNT_TAG= "__MSGCOUNT__";

    // v1.9: newline escape for safe RMS storage
    private static final char NL_ESC = '\u0001';

    public History() {
        sessions        = new Vector();
        currentSession  = new Vector();
        restoredEntries = new Vector();
    }

    // ================================================================
    // Add messages to current session
    // ================================================================

    public void addUserMessage(String msg) { addMsg(PU, msg); }
    public void addAIMessage(String msg)   { addMsg(PA, msg); }

    private void addMsg(char type, String msg) {
        if (msg == null) return;
        msg = msg.trim();
        if (msg.length() == 0) return;

        // v1.9: escape internal newlines so entry stays on one line in RMS
        String safe = msg.replace('\n', NL_ESC).replace('\r', NL_ESC);

        StringBuffer e = new StringBuffer();
        e.append(type)
         .append(SEP)
         .append(safeTimestamp())
         .append(SEP)
         .append(safe);

        currentSession.addElement(e.toString());
        totalMessageCount++;

        while (currentSession.size() > maxMessages) {
            currentSession.removeElementAt(0);
        }
    }

    // ================================================================
    // Context building for API calls
    // ================================================================

    public String getContext(int maxExchanges) {
        int sz = currentSession.size();
        if (sz == 0) return "";
        StringBuffer ctx  = new StringBuffer();
        int need  = maxExchanges * 2;
        int start = (sz > need) ? sz - need : 0;
        for (int i = start; i < sz; i++) {
            String entry = (String) currentSession.elementAt(i);
            if (entry == null || entry.length() < 2) continue;
            ctx.append(entry.charAt(0) == PU ? "User: " : "Assistant: ");
            ctx.append(extractMsg(entry)).append('\n');
        }
        return ctx.toString();
    }

    public String getCompactContext(int maxExchanges) {
        int sz = currentSession.size();
        if (sz == 0) return "";
        StringBuffer ctx  = new StringBuffer();
        int need  = maxExchanges * 2;
        int start = (sz > need) ? sz - need : 0;
        for (int i = start; i < sz; i++) {
            String entry = (String) currentSession.elementAt(i);
            if (entry == null || entry.length() < 2) continue;
            String msg = extractMsg(entry);
            if (msg.length() > 80) msg = msg.substring(0, 77) + "...";
            ctx.append(entry.charAt(0) == PU ? "U:" : "A:").append(msg).append('\n');
        }
        return ctx.toString();
    }

    // ================================================================
    // Entry parsing helpers
    // ================================================================

    private String extractMsg(String entry) {
        if (entry == null || entry.length() < 3) return "";
        int p1 = entry.indexOf(SEP);
        if (p1 < 0) return entry;
        int p2 = entry.indexOf(SEP, p1 + 1);
        if (p2 < 0 || p2 + 1 >= entry.length()) return "";
        // v1.9: restore escaped newlines
        return entry.substring(p2 + 1).replace(NL_ESC, '\n');
    }

    private String extractTimestamp(String entry) {
        if (entry == null || entry.length() < 3) return "";
        int p1 = entry.indexOf(SEP);
        if (p1 < 0) return "";
        int p2 = entry.indexOf(SEP, p1 + 1);
        if (p2 < 0) return "";
        return entry.substring(p1 + 1, p2);
    }

    private char extractType(String entry) {
        if (entry == null || entry.length() == 0) return '?';
        return entry.charAt(0);
    }

    // ================================================================
    // Save current session to past sessions
    // ================================================================

    public void saveCurrentSession() {
        if (currentSession.size() == 0) return;

        // Build title from first user message
        String title = "Session " + safeTimestamp();
        for (int i = 0; i < currentSession.size(); i++) {
            String e = (String) currentSession.elementAt(i);
            if (e != null && e.length() > 2 && e.charAt(0) == PU) {
                String msg = extractMsg(e);
                if (msg.length() > 32) msg = msg.substring(0, 30) + "..";
                title = msg;
                break;
            }
        }

        StringBuffer sb = new StringBuffer();
        sb.append(TITLE_TAG).append(title).append('\n');
        sb.append(TS_TAG).append(safeTimestamp()).append('\n');
        sb.append("Session (").append(currentSession.size()).append(" msgs)\n");

        for (int i = 0; i < currentSession.size(); i++) {
            String entry = (String) currentSession.elementAt(i);
            if (entry == null || entry.length() < 2) continue;
            sb.append(ENTRY_TAG).append(entry).append('\n');
        }

        sessions.addElement(sb.toString());
        while (sessions.size() > maxSessions) {
            sessions.removeElementAt(0);
        }
        currentSession      = new Vector();
        restoredEntries     = new Vector();
        currentSessionTitle = "";
    }

    // ================================================================
    // v1.9 FIX: restoreSession
    // Loads past session into restoredEntries (NOT currentSession).
    // ChatCanvas reads getRestoredEntries() via addRestoredMessage().
    // currentSession stays empty so new messages start fresh continuation.
    // The past session is NOT removed - user can re-resume anytime.
    // If user sends a new message, it goes into currentSession normally.
    // ================================================================

    public boolean restoreSession(int index) {
        if (index < 0 || index >= sessions.size()) return false;

        String sess = (String) sessions.elementAt(index);
        if (sess == null) return false;

        restoredEntries = new Vector();
        String[] lines  = splitLines(sess);

        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i];
            if (ln.startsWith(ENTRY_TAG)) {
                String entry = ln.substring(ENTRY_TAG.length());
                if (entry.length() > 2) {
                    restoredEntries.addElement(entry);
                }
            } else if (ln.startsWith(TITLE_TAG)) {
                currentSessionTitle = ln.substring(TITLE_TAG.length());
            }
        }

        // Fallback: old format ("> " user, "< " AI)
        if (restoredEntries.size() == 0) {
            for (int i = 0; i < lines.length; i++) {
                String ln = lines[i].trim();
                if (ln.startsWith("> ")) {
                    String msg = ln.substring(2);
                    StringBuffer e = new StringBuffer();
                    e.append(PU).append(SEP)
                     .append(safeTimestamp()).append(SEP)
                     .append(msg.replace('\n', NL_ESC));
                    restoredEntries.addElement(e.toString());
                } else if (ln.startsWith("< ")) {
                    String msg = ln.substring(2);
                    StringBuffer e = new StringBuffer();
                    e.append(PA).append(SEP)
                     .append(safeTimestamp()).append(SEP)
                     .append(msg.replace('\n', NL_ESC));
                    restoredEntries.addElement(e.toString());
                }
            }
        }

        if (restoredEntries.size() == 0) return false;

        // v1.9: Also copy into currentSession so context works for new replies
        currentSession = new Vector();
        for (int i = 0; i < restoredEntries.size(); i++) {
            currentSession.addElement(restoredEntries.elementAt(i));
        }

        return true;
    }

    // v1.9 FIX: returns restoredEntries (populated by restoreSession)
    // ChatCanvas.resumeSession() calls this after restoreSession()
    public Vector getRestoredEntries() {
        return restoredEntries;
    }

    // ================================================================
    // Session metadata getters (v1.7+)
    // ================================================================

    public String getSessionTitle(int index) {
        if (index < 0 || index >= sessions.size()) return "Session " + (index + 1);
        String sess = (String) sessions.elementAt(index);
        if (sess == null) return "Session " + (index + 1);
        String[] lines = splitLines(sess);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(TITLE_TAG)) {
                return lines[i].substring(TITLE_TAG.length());
            }
        }
        // Fallback: first non-tag non-empty line
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.length() > 3 && !t.startsWith("__")) {
                return t.length() > 40 ? t.substring(0, 38) + ".." : t;
            }
        }
        return "Session " + (index + 1);
    }

    public String getSessionPreview(int index) {
        if (index < 0 || index >= sessions.size()) return "";
        String sess = (String) sessions.elementAt(index);
        if (sess == null) return "";
        String[] lines = splitLines(sess);

        // Try last AI entry
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].startsWith(ENTRY_TAG)) {
                String entry = lines[i].substring(ENTRY_TAG.length());
                if (entry.length() > 2 && entry.charAt(0) == PA) {
                    String msg = extractMsg(entry);
                    return msg.length() > 38 ? msg.substring(0, 36) + ".." : msg;
                }
            }
        }
        // Fallback: last entry of any type
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].startsWith(ENTRY_TAG)) {
                String entry = lines[i].substring(ENTRY_TAG.length());
                if (entry.length() > 2) {
                    String msg = extractMsg(entry);
                    return msg.length() > 38 ? msg.substring(0, 36) + ".." : msg;
                }
            }
        }
        // Old format fallback
        String lastAI = null;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("< ")) lastAI = lines[i].trim().substring(2);
        }
        if (lastAI != null) return lastAI.length() > 38 ? lastAI.substring(0, 36) + ".." : lastAI;
        return "";
    }

    public String getCurrentSessionPreview() {
        if (currentSession.size() == 0) return "(empty)";
        for (int i = currentSession.size() - 1; i >= 0; i--) {
            String e = (String) currentSession.elementAt(i);
            if (e != null && e.length() > 2 && e.charAt(0) == PA) {
                String msg = extractMsg(e);
                if (msg.length() > 40) msg = msg.substring(0, 38) + "..";
                return msg;
            }
        }
        return currentSession.size() + " message(s)";
    }

    public String getCurrentSessionTitle() {
        return currentSessionTitle.length() > 0 ? currentSessionTitle : "";
    }

    public int getSessionMessageCount(int index) {
        if (index < 0 || index >= sessions.size()) return 0;
        String sess = (String) sessions.elementAt(index);
        if (sess == null) return 0;
        String[] lines = splitLines(sess);
        int count = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(ENTRY_TAG)) count++;
        }
        if (count > 0) return count;
        // Old format fallback
        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i].trim();
            if (ln.startsWith("> ") || ln.startsWith("< ")) count++;
        }
        return count;
    }

    // ================================================================
    // Session operations
    // ================================================================

    public boolean deleteSessionAt(int index) {
        if (index < 0 || index >= sessions.size()) return false;
        sessions.removeElementAt(index);
        return true;
    }

    public boolean renameSession(int index, String newTitle) {
        if (index < 0 || index >= sessions.size()) return false;
        if (newTitle == null || newTitle.trim().length() == 0) return false;
        String sess = (String) sessions.elementAt(index);
        if (sess == null) return false;

        StringBuffer sb   = new StringBuffer();
        String[]     lines= splitLines(sess);
        boolean      found= false;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(TITLE_TAG)) {
                sb.append(TITLE_TAG).append(newTitle.trim()).append('\n');
                found = true;
            } else {
                sb.append(lines[i]).append('\n');
            }
        }
        if (!found) sb.insert(0, TITLE_TAG + newTitle.trim() + "\n");
        sessions.setElementAt(sb.toString(), index);
        return true;
    }

    // v1.7: Get raw entries Vector for a past session
    public Vector getSessionEntries(int index) {
        if (index < 0 || index >= sessions.size()) return new Vector();
        String sess = (String) sessions.elementAt(index);
        Vector result = new Vector();
        if (sess == null) return result;
        String[] lines = splitLines(sess);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(ENTRY_TAG)) {
                String entry = lines[i].substring(ENTRY_TAG.length());
                if (entry.length() > 2) result.addElement(entry);
            }
        }
        return result;
    }

    // ================================================================
    // RMS Persistence - v1.9 FIX: reliable chunked write/read
    // ================================================================

    public void saveToRMS(String storeName) {
        RecordStore rs = null;
        try {
            StringBuffer sb = new StringBuffer();

            // Serialise past sessions
            for (int i = 0; i < sessions.size(); i++) {
                if (i > 0) sb.append(SESSION_SEP);
                // v1.9: each session body already has NL_ESC-escaped msgs
                sb.append((String) sessions.elementAt(i));
            }

            // Serialise current session
            if (currentSession.size() > 0) {
                if (sessions.size() > 0) sb.append(SESSION_SEP);
                sb.append(CURRENT_TAG).append('\n');
                if (currentSessionTitle.length() > 0)
                    sb.append(TITLE_TAG).append(currentSessionTitle).append('\n');
                for (int i = 0; i < currentSession.size(); i++) {
                    String entry = (String) currentSession.elementAt(i);
                    if (entry != null && entry.length() > 0) {
                        sb.append(ENTRY_TAG).append(entry).append('\n');
                    }
                }
            }

            sb.append(SESSION_SEP);
            sb.append(MSGCOUNT_TAG).append(totalMessageCount).append('\n');

            // Delete old store and rewrite
            try { RecordStore.deleteRecordStore(storeName); } catch (Exception ignored) {}

            rs = RecordStore.openRecordStore(storeName, true);
            byte[] data  = sb.toString().getBytes("UTF-8");
            int    chunk = 7900;
            int    off   = 0;
            while (off < data.length) {
                int len = Math.min(chunk, data.length - off);
                rs.addRecord(data, off, len);
                off += len;
            }
        } catch (Exception e) {
            // silently ignore - RMS may be unavailable
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    public void loadFromRMS(String storeName) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(storeName, false);
            int n = rs.getNumRecords();
            if (n == 0) { rs.closeRecordStore(); return; }

            HistoryBAOS baos = new HistoryBAOS();
            for (int i = 1; i <= n; i++) {
                byte[] chunk = rs.getRecord(i);
                if (chunk != null) baos.write(chunk, 0, chunk.length);
            }
            rs.closeRecordStore();
            rs = null;

            String data = new String(baos.toByteArray(), "UTF-8");
            parseFromString(data);

        } catch (RecordStoreNotFoundException e) {
            // first launch - no history yet
        } catch (Exception e) {
            // corrupt data - start fresh
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    private void parseFromString(String data) {
        if (data == null || data.length() == 0) return;

        String[] parts = splitBySep(data, SESSION_SEP);
        sessions       = new Vector();
        currentSession = new Vector();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.length() == 0) continue;

            if (part.startsWith(MSGCOUNT_TAG)) {
                // Parse total message count
                try {
                    String numStr = part.substring(MSGCOUNT_TAG.length()).trim();
                    // Only take first line (remove trailing content)
                    int nl = numStr.indexOf('\n');
                    if (nl > 0) numStr = numStr.substring(0, nl).trim();
                    totalMessageCount = Integer.parseInt(numStr);
                } catch (Exception e) {}

            } else if (part.startsWith(CURRENT_TAG)) {
                // Parse current session
                String[] lines = splitLines(part);
                for (int j = 1; j < lines.length; j++) {
                    String ln = lines[j];
                    if (ln.startsWith(TITLE_TAG)) {
                        currentSessionTitle = ln.substring(TITLE_TAG.length());
                    } else if (ln.startsWith(ENTRY_TAG)) {
                        String entry = ln.substring(ENTRY_TAG.length());
                        if (entry.length() > 2) {
                            currentSession.addElement(entry);
                        }
                    } else if (ln.length() > 2
                            && (ln.charAt(0) == PU || ln.charAt(0) == PA)
                            && ln.indexOf(SEP) > 0) {
                        // old format fallback
                        currentSession.addElement(ln);
                    }
                }
            } else {
                // Past session block
                sessions.addElement(part);
            }
        }
    }

    // ================================================================
    // String split helpers
    // ================================================================

    private String[] splitBySep(String data, String sep) {
        Vector v   = new Vector();
        int    pos = 0;
        while (true) {
            int idx = data.indexOf(sep, pos);
            if (idx < 0) {
                v.addElement(data.substring(pos));
                break;
            }
            v.addElement(data.substring(pos, idx));
            pos = idx + sep.length();
        }
        String[] r = new String[v.size()];
        for (int i = 0; i < v.size(); i++) r[i] = (String) v.elementAt(i);
        return r;
    }

    private String[] splitLines(String s) {
        if (s == null) return new String[0];
        Vector v   = new Vector();
        int    pos = 0;
        while (pos <= s.length()) {
            int idx = s.indexOf('\n', pos);
            if (idx < 0) {
                String line = s.substring(pos);
                if (line.endsWith("\r"))
                    line = line.substring(0, line.length() - 1);
                v.addElement(line);
                break;
            }
            String line = s.substring(pos, idx);
            if (line.endsWith("\r"))
                line = line.substring(0, line.length() - 1);
            v.addElement(line);
            pos = idx + 1;
        }
        String[] r = new String[v.size()];
        for (int i = 0; i < v.size(); i++) r[i] = (String) v.elementAt(i);
        return r;
    }

    // ================================================================
    // All conversations export
    // ================================================================

    public String[] getAllConversations() {
        int total = sessions.size() + (currentSession.size() > 0 ? 1 : 0);
        if (total == 0) return new String[0];
        String[] result = new String[total];
        for (int i = 0; i < sessions.size(); i++) {
            result[i] = (String) sessions.elementAt(i);
        }
        if (currentSession.size() > 0) {
            result[result.length - 1] = formatCurrent();
        }
        return result;
    }

    private String formatCurrent() {
        StringBuffer sb = new StringBuffer();
        sb.append(">> Current (").append(currentSession.size()).append(" msgs)\n");
        for (int i = 0; i < currentSession.size(); i++) {
            String e = (String) currentSession.elementAt(i);
            if (e == null || e.length() < 2) continue;
            sb.append(e.charAt(0) == PU ? "  > " : "  < ");
            sb.append(extractMsg(e)).append('\n');
        }
        return sb.toString();
    }

    // v1.9: updated header
    public String getAllConversationsAsString() {
        StringBuffer sb = new StringBuffer();
        sb.append("==============================\n")
          .append("  AIChatBot v1.9 - HISTORY\n")
          .append("==============================\n\n");
        String[] convs = getAllConversations();
        if (convs.length == 0) {
            sb.append("(No history)\n");
            return sb.toString();
        }
        for (int i = 0; i < convs.length; i++) {
            sb.append("[").append(i + 1).append("] ")
              .append(convs[i])
              .append("\n------------------------------\n\n");
        }
        sb.append("Total: ").append(convs.length).append(" session(s)\n")
          .append("Messages: ").append(totalMessageCount).append("\n");
        return sb.toString();
    }

    // ================================================================
    // Individual getters used by AIChatBot / ChatCanvas
    // ================================================================

    public String getSessionAt(int index) {
        if (index < 0 || index >= sessions.size()) return "";
        return (String) sessions.elementAt(index);
    }

    public String getLastAIResponse() {
        for (int i = currentSession.size() - 1; i >= 0; i--) {
            String e = (String) currentSession.elementAt(i);
            if (e != null && e.length() > 2 && e.charAt(0) == PA)
                return extractMsg(e);
        }
        return null;
    }

    public String getLastUserMessage() {
        for (int i = currentSession.size() - 1; i >= 0; i--) {
            String e = (String) currentSession.elementAt(i);
            if (e != null && e.length() > 2 && e.charAt(0) == PU)
                return extractMsg(e);
        }
        return null;
    }

    // ================================================================
    // State management
    // ================================================================

    public void clearCurrent() {
        currentSession      = new Vector();
        restoredEntries     = new Vector();
        currentSessionTitle = "";
    }

    public void clearAll() {
        sessions            = new Vector();
        currentSession      = new Vector();
        restoredEntries     = new Vector();
        currentSessionTitle = "";
        totalMessageCount   = 0;
    }

    public int     getSessionCount()        { return sessions.size();           }
    public int     getTotalMessageCount()   { return totalMessageCount;          }
    public int     getCurrentSessionSize()  { return currentSession.size();     }
    public boolean hasHistory()             { return currentSession.size() > 0 || sessions.size() > 0; }
    public boolean isCurrentSessionEmpty()  { return currentSession.size() == 0; }

    // ================================================================
    // Search
    // ================================================================

    public String search(String keyword) {
        if (keyword == null || keyword.length() == 0) return "No keyword.";
        String       kl  = keyword.toLowerCase();
        StringBuffer res = new StringBuffer();
        int          cnt = 0;

        // Search current session
        for (int i = 0; i < currentSession.size(); i++) {
            String e   = (String) currentSession.elementAt(i);
            if (e == null || e.length() < 2) continue;
            String msg = extractMsg(e);
            if (msg.toLowerCase().indexOf(kl) >= 0) {
                res.append("[cur] ");
                res.append(msg.length() > 50 ? msg.substring(0, 47) + "..." : msg);
                res.append('\n');
                cnt++;
            }
        }

        // Search past sessions
        for (int i = 0; i < sessions.size(); i++) {
            String sess = (String) sessions.elementAt(i);
            if (sess == null) continue;
            if (sess.toLowerCase().indexOf(kl) >= 0) {
                String[] lines = splitLines(sess);
                for (int j = 0; j < lines.length; j++) {
                    String ln = lines[j];
                    if (ln.startsWith(ENTRY_TAG)) {
                        String entry = ln.substring(ENTRY_TAG.length());
                        String msg   = extractMsg(entry);
                        if (msg.toLowerCase().indexOf(kl) >= 0) {
                            res.append("[s").append(i + 1).append("] ");
                            res.append(msg.length() > 50 ? msg.substring(0, 47) + "..." : msg);
                            res.append('\n');
                            cnt++;
                            break;
                        }
                    } else if (!ln.startsWith("__") && ln.toLowerCase().indexOf(kl) >= 0) {
                        String t = ln.trim();
                        res.append("[s").append(i + 1).append("] ");
                        res.append(t.length() > 50 ? t.substring(0, 47) + "..." : t);
                        res.append('\n');
                        cnt++;
                        break;
                    }
                }
            }
        }

        if (cnt == 0) return "No results for: " + keyword;
        return "Found " + cnt + " result(s):\n" + res.toString();
    }

    // ================================================================
    // Safe timestamp - no crashes if System.currentTimeMillis fails
    // ================================================================
    private String safeTimestamp() {
        try {
            return String.valueOf(System.currentTimeMillis());
        } catch (Exception e) {
            return "0";
        }
    }
}


// ================================================================
// HistoryBAOS - Minimal ByteArrayOutputStream for J2ME
// (No java.io.ByteArrayOutputStream available on all J2ME VMs)
// ================================================================
class HistoryBAOS extends java.io.OutputStream {
    private byte[] buf   = new byte[1024];
    private int    count = 0;

    private void ensureCapacity(int need) {
        if (count + need <= buf.length) return;
        int    newLen = Math.max(buf.length * 2, count + need + 256);
        byte[] nb     = new byte[newLen];
        System.arraycopy(buf, 0, nb, 0, count);
        buf = nb;
    }

    public void write(int b) {
        ensureCapacity(1);
        buf[count++] = (byte) b;
    }

    public void write(byte[] b, int off, int len) {
        if (b == null || len <= 0) return;
        ensureCapacity(len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    public byte[] toByteArray() {
        byte[] r = new byte[count];
        System.arraycopy(buf, 0, r, 0, count);
        return r;
    }

    public int  size()  { return count; }
    public void reset() { count = 0;    }
}


