import java.util.Vector;
import javax.microedition.rms.*;

// ================================================================
// History.java v1.7
// UPDATED FROM v1.6:
//   - Version bumped to v1.7
//   - NEW: restoreSession(int) - load past session back into current
//   - NEW: getSessionEntries(int) - get raw entries for a session
//   - NEW: getSessionTitle(int) - extract first line of session
//   - NEW: getSessionPreview(int) - short preview for history list
//   - NEW: getSessionMessageCount(int) - message count per session
//   - NEW: deleteSessionAt(int) - delete specific past session
//   - NEW: renameSession(int, String) - rename a saved session
//   - saveCurrentSession() now stores timestamp for retrieval
//   - getAllConversations() header updated to v1.7
//   - Session format updated to include __TITLE__ tag
//   - maxSessions increased to 20
// ================================================================
public class History {

    private Vector sessions;        // past sessions (String)
    private Vector currentSession;  // live entries
    private int maxMessages  = 50;
    private int maxSessions  = 20;  // v1.7: increased from 10
    private int totalMessageCount = 0;
    private String currentSessionTitle = ""; // v1.7: title for current session

    private static final char   PU          = 'U';
    private static final char   PA          = 'A';
    private static final char   SEP         = '|';
    private static final String SESSION_SEP = "\n===SESSION===\n";
    private static final String TITLE_TAG   = "__TITLE__:";   // v1.7
    private static final String TS_TAG      = "__TS__:";      // v1.7

    public History() {
        sessions       = new Vector();
        currentSession = new Vector();
    }

    // ================================================================
    // Add messages
    // ================================================================

    public void addUserMessage(String msg) { addMsg(PU, msg); }
    public void addAIMessage(String msg)   { addMsg(PA, msg); }

    private void addMsg(char type, String msg) {
        if (msg == null) return;
        msg = msg.trim();
        if (msg.length() == 0) return;

        StringBuffer e = new StringBuffer();
        e.append(type)
         .append(SEP)
         .append(Utils.getCurrentTimestamp())
         .append(SEP)
         .append(msg);
        currentSession.addElement(e.toString());
        totalMessageCount++;

        while (currentSession.size() > maxMessages) {
            currentSession.removeElementAt(0);
        }
    }

    // ================================================================
    // Context for API
    // ================================================================

    public String getContext(int maxExchanges) {
        int sz = currentSession.size();
        if (sz == 0) return "";

        StringBuffer ctx  = new StringBuffer();
        int need  = maxExchanges * 2;
        int start = (sz > need) ? sz - need : 0;

        for (int i = start; i < sz; i++) {
            String entry = (String)currentSession.elementAt(i);
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
            String entry = (String)currentSession.elementAt(i);
            if (entry == null || entry.length() < 2) continue;
            String msg = extractMsg(entry);
            if (msg.length() > 80) msg = msg.substring(0, 77) + "...";
            ctx.append(entry.charAt(0) == PU ? "U:" : "A:").append(msg).append('\n');
        }
        return ctx.toString();
    }

    // ================================================================
    // Extract message body from entry string
    // ================================================================

    private String extractMsg(String entry) {
        if (entry == null || entry.length() < 3) return "";
        int p1 = entry.indexOf(SEP);
        if (p1 < 0) return entry;
        int p2 = entry.indexOf(SEP, p1 + 1);
        if (p2 < 0 || p2 + 1 >= entry.length()) return "";
        return entry.substring(p2 + 1);
    }

    private String extractTimestamp(String entry) {
        if (entry == null || entry.length() < 3) return "";
        int p1 = entry.indexOf(SEP);
        if (p1 < 0) return "";
        int p2 = entry.indexOf(SEP, p1 + 1);
        if (p2 < 0) return "";
        return entry.substring(p1 + 1, p2);
    }

    // ================================================================
    // Session management
    // ================================================================

    public void saveCurrentSession() {
        if (currentSession.size() == 0) return;

        // Build title from first user message
        String title = "Session " + Utils.getCurrentTimestamp();
        for (int i = 0; i < currentSession.size(); i++) {
            String e = (String)currentSession.elementAt(i);
            if (e != null && e.length() > 2 && e.charAt(0) == PU) {
                String msg = extractMsg(e);
                if (msg.length() > 32) msg = msg.substring(0, 30) + "..";
                title = msg;
                break;
            }
        }

        StringBuffer sb = new StringBuffer();
        // v1.7: include title tag and timestamp tag for retrieval
        sb.append(TITLE_TAG).append(title).append('\n');
        sb.append(TS_TAG).append(Utils.getCurrentTimestamp()).append('\n');
        sb.append("Session (").append(currentSession.size()).append(" msgs)\n");

        for (int i = 0; i < currentSession.size(); i++) {
            String entry = (String)currentSession.elementAt(i);
            if (entry == null || entry.length() < 2) continue;
            // v1.7: store full entry (type|ts|msg) for restoration
            sb.append("__ENTRY__:").append(entry).append('\n');
        }

        sessions.addElement(sb.toString());
        while (sessions.size() > maxSessions) {
            sessions.removeElementAt(0);
        }
        currentSession = new Vector();
        currentSessionTitle = "";
    }

    // ================================================================
    // v1.7: Session resumption - load a past session back to current
    // ================================================================

    /**
     * Restore a past session into currentSession so chat can continue.
     * Saves any active current session first.
     * Returns true if successful.
     */
    public boolean restoreSession(int index) {
        if (index < 0 || index >= sessions.size()) return false;

        // Save current session first if not empty
        if (currentSession.size() > 0) {
            saveCurrentSession();
        }

        String sess = (String)sessions.elementAt(index);
        if (sess == null) return false;

        Vector restored = new Vector();
        String[] lines = splitLines(sess);

        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i];
            if (ln.startsWith("__ENTRY__:")) {
                String entry = ln.substring("__ENTRY__:".length());
                if (entry.length() > 2) {
                    restored.addElement(entry);
                }
            } else if (ln.startsWith(TITLE_TAG)) {
                currentSessionTitle = ln.substring(TITLE_TAG.length());
            }
        }

        // Fallback: rebuild from old format (lines starting with "> " or "< ")
        if (restored.size() == 0) {
            for (int i = 0; i < lines.length; i++) {
                String ln = lines[i].trim();
                if (ln.startsWith("> ")) {
                    String msg = ln.substring(2);
                    StringBuffer e = new StringBuffer();
                    e.append(PU).append(SEP)
                     .append(Utils.getCurrentTimestamp()).append(SEP)
                     .append(msg);
                    restored.addElement(e.toString());
                } else if (ln.startsWith("< ")) {
                    String msg = ln.substring(2);
                    StringBuffer e = new StringBuffer();
                    e.append(PA).append(SEP)
                     .append(Utils.getCurrentTimestamp()).append(SEP)
                     .append(msg);
                    restored.addElement(e.toString());
                }
            }
        }

        if (restored.size() == 0) return false;

        // Remove this session from past sessions
        sessions.removeElementAt(index);

        currentSession = restored;
        return true;
    }

    /**
     * v1.7: Get session title (first line / user message preview)
     */
    public String getSessionTitle(int index) {
        if (index < 0 || index >= sessions.size()) return "";
        String sess = (String)sessions.elementAt(index);
        if (sess == null) return "";
        String[] lines = splitLines(sess);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(TITLE_TAG)) {
                return lines[i].substring(TITLE_TAG.length());
            }
        }
        // Fallback: first non-empty line
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().length() > 3 && !lines[i].startsWith("__")) {
                String t = lines[i].trim();
                return t.length() > 40 ? t.substring(0, 38) + ".." : t;
            }
        }
        return "Session " + (index + 1);
    }

    /**
     * v1.7: Short preview of a past session (last AI message)
     */
    public String getSessionPreview(int index) {
        if (index < 0 || index >= sessions.size()) return "";
        String sess = (String)sessions.elementAt(index);
        if (sess == null) return "";
        String[] lines = splitLines(sess);
        // Find last entry line
        String lastEntry = null;
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].startsWith("__ENTRY__:")) {
                lastEntry = lines[i].substring("__ENTRY__:".length());
                break;
            }
        }
        if (lastEntry != null && lastEntry.length() > 2) {
            String msg = extractMsg(lastEntry);
            return msg.length() > 38 ? msg.substring(0, 36) + ".." : msg;
        }
        // Fallback: find lines starting with "< " (AI messages)
        String lastAI = null;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("< ")) lastAI = lines[i].trim().substring(2);
        }
        if (lastAI != null) return lastAI.length() > 38 ? lastAI.substring(0, 36) + ".." : lastAI;
        return "";
    }

    /**
     * v1.7: Get message count for a specific past session
     */
    public int getSessionMessageCount(int index) {
        if (index < 0 || index >= sessions.size()) return 0;
        String sess = (String)sessions.elementAt(index);
        if (sess == null) return 0;
        String[] lines = splitLines(sess);
        int count = 0;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("__ENTRY__:")) count++;
        }
        if (count > 0) return count;
        // Fallback: count > / < lines
        for (int i = 0; i < lines.length; i++) {
            String ln = lines[i].trim();
            if (ln.startsWith("> ") || ln.startsWith("< ")) count++;
        }
        return count;
    }

    /**
     * v1.7: Delete a specific past session
     */
    public boolean deleteSessionAt(int index) {
        if (index < 0 || index >= sessions.size()) return false;
        sessions.removeElementAt(index);
        return true;
    }

    /**
     * v1.7: Rename a saved session
     */
    public boolean renameSession(int index, String newTitle) {
        if (index < 0 || index >= sessions.size()) return false;
        if (newTitle == null || newTitle.trim().length() == 0) return false;
        String sess = (String)sessions.elementAt(index);
        if (sess == null) return false;

        // Replace or prepend title tag
        StringBuffer sb = new StringBuffer();
        String[] lines = splitLines(sess);
        boolean found = false;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(TITLE_TAG)) {
                sb.append(TITLE_TAG).append(newTitle.trim()).append('\n');
                found = true;
            } else {
                sb.append(lines[i]).append('\n');
            }
        }
        if (!found) {
            sb.insert(0, TITLE_TAG + newTitle.trim() + "\n");
        }
        sessions.setElementAt(sb.toString(), index);
        return true;
    }

    /**
     * v1.7: Get raw entries Vector for a past session (for ChatCanvas rendering)
     */
    public Vector getSessionEntries(int index) {
        if (index < 0 || index >= sessions.size()) return new Vector();
        String sess = (String)sessions.elementAt(index);
        Vector result = new Vector();
        if (sess == null) return result;
        String[] lines = splitLines(sess);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("__ENTRY__:")) {
                String entry = lines[i].substring("__ENTRY__:".length());
                if (entry.length() > 2) result.addElement(entry);
            }
        }
        return result;
    }

    /**
     * v1.7: Get current session title
     */
    public String getCurrentSessionTitle() {
        return currentSessionTitle.length() > 0 ? currentSessionTitle : "";
    }

    // ================================================================
    // RMS Persistence
    // ================================================================

    public void saveToRMS(String storeName) {
        RecordStore rs = null;
        try {
            StringBuffer sb = new StringBuffer();

            for (int i = 0; i < sessions.size(); i++) {
                if (i > 0) sb.append(SESSION_SEP);
                sb.append((String)sessions.elementAt(i));
            }

            if (currentSession.size() > 0) {
                if (sessions.size() > 0) sb.append(SESSION_SEP);
                sb.append("__CURRENT__\n");
                if (currentSessionTitle.length() > 0)
                    sb.append(TITLE_TAG).append(currentSessionTitle).append('\n');
                for (int i = 0; i < currentSession.size(); i++) {
                    String entry = (String)currentSession.elementAt(i);
                    if (entry != null && entry.length() > 0) {
                        sb.append(entry).append('\n');
                    }
                }
            }

            sb.append(SESSION_SEP);
            sb.append("__MSGCOUNT__").append(totalMessageCount).append('\n');

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
        } catch (Exception e) {
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    private void parseFromString(String data) {
        if (data == null || data.length() == 0) return;

        String[] parts = splitBySep(data, SESSION_SEP);
        sessions = new Vector();
        currentSession = new Vector();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.length() == 0) continue;

            if (part.startsWith("__MSGCOUNT__")) {
                try {
                    totalMessageCount = Integer.parseInt(
                        part.substring("__MSGCOUNT__".length()).trim());
                } catch (Exception e) {}
            } else if (part.startsWith("__CURRENT__")) {
                // Parse current session
                String[] lines = splitLines(part);
                for (int j = 1; j < lines.length; j++) {
                    String ln = lines[j];
                    if (ln.startsWith(TITLE_TAG)) {
                        currentSessionTitle = ln.substring(TITLE_TAG.length());
                    } else if (ln.length() > 2 &&
                               (ln.charAt(0) == PU || ln.charAt(0) == PA) &&
                               ln.indexOf(SEP) > 0) {
                        currentSession.addElement(ln);
                    }
                }
            } else {
                sessions.addElement(part);
            }
        }
    }

    private String[] splitBySep(String data, String sep) {
        Vector v = new Vector();
        int pos = 0;
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
        for (int i = 0; i < v.size(); i++) r[i] = (String)v.elementAt(i);
        return r;
    }

    private String[] splitLines(String s) {
        if (s == null) return new String[0];
        Vector v = new Vector();
        int pos = 0;
        while (pos <= s.length()) {
            int idx = s.indexOf('\n', pos);
            if (idx < 0) {
                String line = s.substring(pos);
                if (line.endsWith("\r"))
                    line = line.substring(0, line.length()-1);
                v.addElement(line);
                break;
            }
            String line = s.substring(pos, idx);
            if (line.endsWith("\r"))
                line = line.substring(0, line.length()-1);
            v.addElement(line);
            pos = idx + 1;
        }
        String[] r = new String[v.size()];
        for (int i = 0; i < v.size(); i++) r[i] = (String)v.elementAt(i);
        return r;
    }

    // ================================================================
    // All conversations
    // ================================================================

    public String[] getAllConversations() {
        int total = sessions.size() + (currentSession.size() > 0 ? 1 : 0);
        if (total == 0) return new String[0];
        String[] result = new String[total];
        for (int i = 0; i < sessions.size(); i++) {
            result[i] = (String)sessions.elementAt(i);
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
            String e = (String)currentSession.elementAt(i);
            if (e == null || e.length() < 2) continue;
            sb.append(e.charAt(0) == PU ? "  > " : "  < ");
            sb.append(extractMsg(e)).append('\n');
        }
        return sb.toString();
    }

    // v1.7: header updated
    public String getAllConversationsAsString() {
        StringBuffer sb = new StringBuffer();
        sb.append("==============================\n")
          .append("  AIChatBot v1.7 - HISTORY\n")
          .append("==============================\n\n");
        String[] convs = getAllConversations();
        if (convs.length == 0) {
            sb.append("(No history)\n");
            return sb.toString();
        }
        for (int i = 0; i < convs.length; i++) {
            sb.append("[").append(i+1).append("] ")
              .append(convs[i])
              .append("\n------------------------------\n\n");
        }
        sb.append("Total: ").append(convs.length).append(" session(s)\n")
          .append("Messages: ").append(totalMessageCount).append("\n");
        return sb.toString();
    }

    // ================================================================
    // v1.6 helpers preserved
    // ================================================================

    public String getSessionAt(int index) {
        if (index < 0 || index >= sessions.size()) return "";
        return (String)sessions.elementAt(index);
    }

    public String getCurrentSessionPreview() {
        if (currentSession.size() == 0) return "(empty)";
        for (int i = currentSession.size()-1; i >= 0; i--) {
            String e = (String)currentSession.elementAt(i);
            if (e != null && e.length() > 2 && e.charAt(0) == PA) {
                String msg = extractMsg(e);
                if (msg.length() > 40) msg = msg.substring(0, 38) + "..";
                return msg;
            }
        }
        return currentSession.size() + " message(s)";
    }

    public String getLastAIResponse() {
        for (int i = currentSession.size()-1; i >= 0; i--) {
            String e = (String)currentSession.elementAt(i);
            if (e != null && e.length() > 2 && e.charAt(0) == PA)
                return extractMsg(e);
        }
        return null;
    }

    public String getLastUserMessage() {
        for (int i = currentSession.size()-1; i >= 0; i--) {
            String e = (String)currentSession.elementAt(i);
            if (e != null && e.length() > 2 && e.charAt(0) == PU)
                return extractMsg(e);
        }
        return null;
    }

    public void clearCurrent() { currentSession = new Vector(); }

    public void clearAll() {
        sessions          = new Vector();
        currentSession    = new Vector();
        totalMessageCount = 0;
    }

    public int     getSessionCount()       { return sessions.size(); }
    public int     getTotalMessageCount()  { return totalMessageCount; }
    public int     getCurrentSessionSize() { return currentSession.size(); }
    public boolean hasHistory()            { return currentSession.size()>0 || sessions.size()>0; }
    public boolean isCurrentSessionEmpty() { return currentSession.size() == 0; }

    public String search(String keyword) {
        if (keyword == null || keyword.length() == 0) return "No keyword.";
        String kl  = keyword.toLowerCase();
        StringBuffer res = new StringBuffer();
        int count = 0;

        for (int i = 0; i < currentSession.size(); i++) {
            String e   = (String)currentSession.elementAt(i);
            if (e == null || e.length() < 2) continue;
            String msg = extractMsg(e);
            if (msg.toLowerCase().indexOf(kl) >= 0) {
                res.append("[cur] ");
                res.append(msg.length() > 50 ? msg.substring(0,47)+"..." : msg);
                res.append('\n');
                count++;
            }
        }

        for (int i = 0; i < sessions.size(); i++) {
            String sess = (String)sessions.elementAt(i);
            if (sess == null) continue;
            if (sess.toLowerCase().indexOf(kl) >= 0) {
                String[] lines = splitLines(sess);
                for (int j = 0; j < lines.length; j++) {
                    if (lines[j].toLowerCase().indexOf(kl) >= 0) {
                        String ln = lines[j].trim();
                        if (ln.startsWith("__")) continue;
                        res.append("[s").append(i+1).append("] ");
                        res.append(ln.length() > 50 ? ln.substring(0,47)+"..." : ln);
                        res.append('\n');
                        count++;
                        break;
                    }
                }
            }
        }

        if (count == 0) return "No results for: " + keyword;
        return "Found " + count + " result(s):\n" + res.toString();
    }
}


// ================================================================
// HistoryBAOS - Simple ByteArrayOutputStream for J2ME (v1.6/v1.7)
// ================================================================
class HistoryBAOS extends java.io.OutputStream {
    private byte[] buf   = new byte[512];
    private int    count = 0;

    private void grow(int need) {
        if (count + need <= buf.length) return;
        int    newLen = Math.max(buf.length * 2, count + need);
        byte[] nb     = new byte[newLen];
        System.arraycopy(buf, 0, nb, 0, count);
        buf = nb;
    }

    public void write(int b) { grow(1); buf[count++] = (byte)b; }

    public void write(byte[] b, int off, int len) {
        if (b == null || len <= 0) return;
        grow(len);
        System.arraycopy(b, off, buf, count, len);
        count += len;
    }

    public byte[] toByteArray() {
        byte[] r = new byte[count];
        System.arraycopy(buf, 0, r, 0, count);
        return r;
    }

    public int size()  { return count; }
    public void reset(){ count = 0; }
}