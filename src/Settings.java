import javax.microedition.rms.*;

// ================================================================
// Settings.java v1.9
// UPDATED FROM v1.7/v1.8:
//   - VERSION bumped to v1.9, BUILD 20250422
//   - STORE_NAME updated to "AIChatCfg19"
//   - NEW: apiBase field - custom API/connection base URL
//          used by ChatCanvas for alternate endpoints
//   - NEW: getApiBase() / setApiBaseSilent() / setApiBase()
//   - FIX: saveAll() / parseSettings() updated for apiBase (field 9)
//   - FIX: filePath and apiBase survive save/load cycle correctly
//   - FIX: importSettings() / exportSettings() include apiBase
//   - FIX: getConfigSummary() shows apiBase
//   - FIX: resetToDefaults() resets apiBase to ""
//   - KEEP: all v1.7 fields: aiToolsEnabled, autoSaveSession,
//           fontSizeIndex, filePath, themeIndex (9 themes)
//   - KEEP: all silent setters, validators, export/import
// ================================================================
public class Settings {

    // ================================================================
    // Configuration fields
    // ================================================================
    private int     timeout         = 30000; // ms  (5000 - 120000)
    private boolean contextEnabled  = true;
    private int     maxContext       = 5;    // exchanges (1-20)
    private boolean proxyEnabled     = false;
    private int     themeIndex       = 0;   // 0-8  (9 themes)

    // v1.7 fields
    private boolean aiToolsEnabled   = true;
    private boolean autoSaveSession  = true;
    private int     fontSizeIndex    = 0;   // 0=small, 1=medium

    // v1.8 field
    private String  filePath         = "";  // custom save/read path

    // v1.9 field
    private String  apiBase          = "";  // custom API / connection URL

    // ================================================================
    // RMS store name  (bump every major version to avoid parse errors)
    // ================================================================
    private static final String STORE_NAME = "AIChatCfg19";

    // Field separator – must not appear in free-text values
    // We use a pipe for fixed fields; apiBase/filePath are last so
    // they may contain anything except '\n'.
    private static final char SEP = '|';

    private boolean dirty = false;

    // ================================================================
    // Constructor – load from RMS, clear dirty flag
    // ================================================================
    public Settings() {
        loadSettings();
        dirty = false;
    }

    // ================================================================
    // Getters
    // ================================================================
    public int     getTimeout()         { return timeout;                      }
    public boolean isContextEnabled()   { return contextEnabled;               }
    public int     getMaxContext()      { return maxContext;                   }
    public boolean isProxyEnabled()     { return proxyEnabled;                 }
    public int     getThemeIndex()      { return themeIndex;                   }
    public boolean isAIToolsEnabled()   { return aiToolsEnabled;               }
    public boolean isAutoSaveSession()  { return autoSaveSession;              }
    public int     getFontSizeIndex()   { return fontSizeIndex;                }
    public String  getFilePath()        { return filePath  != null ? filePath  : ""; }
    public String  getApiBase()         { return apiBase   != null ? apiBase   : ""; }

    public boolean hasUnsavedChanges()  { return dirty; }

    // Convenience
    public int  getTimeoutSeconds()     { return timeout / 1000; }
    public int  getTimeoutMin()         { return 5000;           }
    public int  getTimeoutMax()         { return 120000;         }
    public int  getContextMin()         { return 1;              }
    public int  getContextMax()         { return 20;             }
    public int  getThemeCount()         { return 9;              }

    public String getThemeName() {
        return getThemeNames()[themeIndex % 9];
    }

    public static String[] getThemeNames() {
        return new String[]{
            "Dark",    // 0
            "ChatGPT", // 1
            "Ocean",   // 2
            "Amoled",  // 3
            "Sunset",  // 4
            "Forest",  // 5
            "Rose",    // 6
            "Hacker",  // 7
            "Ice"      // 8
        };
    }

    public boolean isValidTimeout(int t)    { return validateTimeout(t);    }
    public boolean isValidMaxContext(int m) { return validateMaxContext(m); }

    // ================================================================
    // Immediate setters  (persist after each call)
    // ================================================================
    public void setTimeout(int t) {
        if (validateTimeout(t)) { timeout = t; saveAll(); }
    }
    public void setContextEnabled(boolean e) {
        contextEnabled = e; saveAll();
    }
    public void setMaxContext(int m) {
        if (validateMaxContext(m)) { maxContext = m; saveAll(); }
    }
    public void setProxyEnabled(boolean p) {
        proxyEnabled = p; saveAll();
    }
    public void setThemeIndex(int i) {
        themeIndex = clampTheme(i); saveAll();
    }
    public void setAIToolsEnabled(boolean a) {
        aiToolsEnabled = a; saveAll();
    }
    public void setAutoSaveSession(boolean a) {
        autoSaveSession = a; saveAll();
    }
    public void setFontSizeIndex(int i) {
        fontSizeIndex = i % 2; saveAll();
    }
    public void setFilePath(String p) {
        filePath = (p != null ? p.trim() : ""); saveAll();
    }
    public void setApiBase(String u) {
        apiBase = (u != null ? u.trim() : ""); saveAll();
    }

    // ================================================================
    // Silent setters  (batch mode – call saveAll() manually)
    // ================================================================
    public void setTimeoutSilent(int t) {
        if (validateTimeout(t))   { timeout        = t;              dirty = true; }
    }
    public void setContextEnabledSilent(boolean e) {
        contextEnabled  = e;                                         dirty = true;
    }
    public void setMaxContextSilent(int m) {
        if (validateMaxContext(m)) { maxContext      = m;             dirty = true; }
    }
    public void setProxyEnabledSilent(boolean p) {
        proxyEnabled    = p;                                         dirty = true;
    }
    public void setThemeIndexSilent(int i) {
        themeIndex      = clampTheme(i);                             dirty = true;
    }
    public void setAIToolsEnabledSilent(boolean a) {
        aiToolsEnabled  = a;                                         dirty = true;
    }
    public void setAutoSaveSilent(boolean a) {
        autoSaveSession = a;                                         dirty = true;
    }
    public void setFontSizeIndexSilent(int i) {
        fontSizeIndex   = i % 2;                                     dirty = true;
    }
    public void setFilePathSilent(String p) {
        filePath        = (p != null ? p.trim() : "");               dirty = true;
    }
    public void setApiBaseSilent(String u) {
        apiBase         = (u != null ? u.trim() : "");               dirty = true;
    }

    // ================================================================
    // Validation helpers
    // ================================================================
    private boolean validateTimeout(int t)    { return t >= 5000 && t <= 120000; }
    private boolean validateMaxContext(int m) { return m >= 1   && m <= 20;     }
    private int     clampTheme(int i)         { return (i < 0) ? 0 : i % 9;    }

    // ================================================================
    // saveAll() – delete-then-create pattern (safe on all J2ME VMs)
    //
    // Format (9 pipe-separated fields + 2 free-text tail fields):
    //   timeout|ctx|maxCtx|proxy|theme|aiTools|autoSave|fontSize|filePath|apiBase
    //
    // filePath and apiBase are stored LAST so they can contain any
    // character except the record separator (we use \n between them
    // via a second pipe at the end of fontSize).
    // ================================================================
    public void saveAll() {
        RecordStore rs = null;
        try {
            // Delete old record to avoid size mismatch on setRecord
            try {
                RecordStore.deleteRecordStore(STORE_NAME);
            } catch (RecordStoreNotFoundException ignored) {
            } catch (Exception ignored) {}

            rs = RecordStore.openRecordStore(STORE_NAME, true);

            String fp = (filePath != null) ? filePath : "";
            String ab = (apiBase  != null) ? apiBase  : "";

            // Escape any pipe characters in free-text fields so parsing
            // stays deterministic (replace with \u0002, restore on load)
            fp = fp.replace('|', '\u0002');
            ab = ab.replace('|', '\u0002');

            StringBuffer sb = new StringBuffer();
            sb.append(timeout);                          sb.append(SEP); // 0
            sb.append(contextEnabled  ? "1" : "0");      sb.append(SEP); // 1
            sb.append(maxContext);                        sb.append(SEP); // 2
            sb.append(proxyEnabled    ? "1" : "0");      sb.append(SEP); // 3
            sb.append(themeIndex);                        sb.append(SEP); // 4
            sb.append(aiToolsEnabled  ? "1" : "0");      sb.append(SEP); // 5
            sb.append(autoSaveSession ? "1" : "0");      sb.append(SEP); // 6
            sb.append(fontSizeIndex);                     sb.append(SEP); // 7
            sb.append(fp);                                sb.append(SEP); // 8
            sb.append(ab);                                               // 9 (last)

            byte[] b = sb.toString().getBytes("UTF-8");
            rs.addRecord(b, 0, b.length);
            dirty = false;

        } catch (Exception e) {
            // Silently ignore – settings fall back to in-memory defaults
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    // ================================================================
    // loadSettings() – read from RMS
    // ================================================================
    private void loadSettings() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, false);
            if (rs.getNumRecords() > 0) {
                String s = new String(rs.getRecord(1), "UTF-8");
                parseSettings(s);
            }
        } catch (RecordStoreNotFoundException e) {
            // First launch – use compiled-in defaults
        } catch (Exception e) {
            // Corrupt record – use defaults
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    // ================================================================
    // parseSettings() – tolerant parser
    //
    // Expected pipe count: 9  (10 tokens)
    // Older formats with fewer pipes are handled gracefully.
    // ================================================================
    private void parseSettings(String s) {
        if (s == null || s.length() == 0) return;
        try {
            // Split on SEP into at most 10 tokens
            String[] tok = splitMax(s, SEP, 10);

            // Field 0 – timeout
            if (tok.length > 0 && tok[0].length() > 0) {
                int t = Integer.parseInt(tok[0].trim());
                if (validateTimeout(t)) timeout = t;
            }
            // Field 1 – contextEnabled
            if (tok.length > 1) contextEnabled = "1".equals(tok[1].trim());

            // Field 2 – maxContext
            if (tok.length > 2 && tok[2].length() > 0) {
                int m = Integer.parseInt(tok[2].trim());
                if (validateMaxContext(m)) maxContext = m;
            }
            // Field 3 – proxyEnabled
            if (tok.length > 3) proxyEnabled = "1".equals(tok[3].trim());

            // Field 4 – themeIndex
            if (tok.length > 4 && tok[4].length() > 0) {
                themeIndex = clampTheme(Integer.parseInt(tok[4].trim()));
            }
            // Field 5 – aiToolsEnabled  (v1.7+)
            if (tok.length > 5) aiToolsEnabled = "1".equals(tok[5].trim());

            // Field 6 – autoSaveSession  (v1.7+)
            if (tok.length > 6) autoSaveSession = "1".equals(tok[6].trim());

            // Field 7 – fontSizeIndex  (v1.7+)
            if (tok.length > 7 && tok[7].length() > 0) {
                fontSizeIndex = Integer.parseInt(tok[7].trim()) % 2;
            }
            // Field 8 – filePath  (v1.8+)
            if (tok.length > 8) {
                filePath = tok[8].trim().replace('\u0002', '|');
            }
            // Field 9 – apiBase  (v1.9+)
            if (tok.length > 9) {
                apiBase = tok[9].trim().replace('\u0002', '|');
            }

        } catch (Exception e) {
            // Leave already-parsed fields; rest stay at defaults
        }
    }

    // ================================================================
    // Split string by separator, returning at most maxParts tokens.
    // The LAST token absorbs the remainder (no further splitting).
    // ================================================================
    private String[] splitMax(String s, char sep, int maxParts) {
        String[]     result = new String[maxParts];
        int          count  = 0;
        int          start  = 0;
        int          len    = s.length();

        while (start <= len && count < maxParts - 1) {
            int idx = s.indexOf(sep, start);
            if (idx < 0) break;
            result[count++] = s.substring(start, idx);
            start = idx + 1;
        }
        // Remainder goes into last slot
        if (count < maxParts) {
            result[count++] = (start <= len) ? s.substring(start) : "";
        }
        // Trim array to actual count
        String[] out = new String[count];
        System.arraycopy(result, 0, out, 0, count);
        return out;
    }

    // ================================================================
    // Reset to factory defaults
    // ================================================================
    public void resetToDefaults() {
        timeout         = 30000;
        contextEnabled  = true;
        maxContext       = 5;
        proxyEnabled     = false;
        themeIndex       = 0;
        aiToolsEnabled   = true;
        autoSaveSession  = true;
        fontSizeIndex    = 0;
        filePath         = "";
        apiBase          = "";
        saveAll();
    }

    // ================================================================
    // Export / Import  (portable string representation)
    // ================================================================
    public String exportSettings() {
        String fp = (filePath != null) ? filePath.replace('|', '\u0002') : "";
        String ab = (apiBase  != null) ? apiBase .replace('|', '\u0002') : "";
        return timeout         + "|"
             + (contextEnabled  ? "1" : "0") + "|"
             + maxContext        + "|"
             + (proxyEnabled    ? "1" : "0") + "|"
             + themeIndex        + "|"
             + (aiToolsEnabled  ? "1" : "0") + "|"
             + (autoSaveSession ? "1" : "0") + "|"
             + fontSizeIndex     + "|"
             + fp                + "|"
             + ab;
    }

    public boolean importSettings(String data) {
        if (data == null || data.length() == 0) return false;
        try {
            parseSettings(data);
            saveAll();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ================================================================
    // Human-readable config summary
    // ================================================================
    public String getConfigSummary() {
        StringBuffer sb = new StringBuffer();
        sb.append("Timeout:   ").append(timeout / 1000).append("s\n");
        sb.append("Context:   ").append(contextEnabled ? "ON" : "OFF");
        if (contextEnabled) sb.append(" (").append(maxContext).append(" exchanges)");
        sb.append('\n');
        sb.append("Proxy:     ").append(proxyEnabled    ? "ON"    : "OFF").append('\n');
        sb.append("Theme:     ").append(getThemeName()).append('\n');
        sb.append("AI Tools:  ").append(aiToolsEnabled  ? "ON"    : "OFF").append('\n');
        sb.append("AutoSave:  ").append(autoSaveSession ? "ON"    : "OFF").append('\n');
        sb.append("Font:      ").append(fontSizeIndex == 0 ? "Small" : "Medium").append('\n');
        if (filePath != null && filePath.length() > 0)
            sb.append("FilePath:  ").append(filePath).append('\n');
        if (apiBase  != null && apiBase .length() > 0)
            sb.append("APIBase:   ").append(apiBase).append('\n');
        return sb.toString();
    }
}