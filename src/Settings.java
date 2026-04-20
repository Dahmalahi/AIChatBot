import javax.microedition.rms.*;

// ================================================================
// Settings.java v1.7
// UPDATED FROM v1.6:
//   - Version bumped to v1.7
//   - STORE_NAME updated to "AIChatCfg17"
//   - themeIndex now supports 9 themes (was 4)
//   - NEW: aiToolsEnabled flag (built-in AI tools on/off)
//   - NEW: autoSaveSession flag (auto-save on exit)
//   - NEW: fontSizeIndex (0=small, 1=medium) for readability
//   - NEW: getThemeNames() returns all 9 theme names
//   - saveAll() / loadSettings() updated for new fields
//   - exportSettings() / importSettings() updated
//   - getConfigSummary() updated with new settings
//   - themeCount updated to 9
// ================================================================
public class Settings {

    // ================================================================
    // Configuration values
    // ================================================================
    private int     timeout        = 30000;  // ms (5s - 120s)
    private boolean contextEnabled = true;
    private int     maxContext     = 5;      // exchanges (1-20)
    private boolean proxyEnabled   = false;
    private int     themeIndex     = 0;      // 0-8 (9 themes)

    // v1.7: new settings
    private boolean aiToolsEnabled  = true;   // built-in AI tools
    private boolean autoSaveSession = true;   // auto-save on exit
    private int     fontSizeIndex   = 0;      // 0=small, 1=medium

    // v1.7: new store name
    private static final String STORE_NAME = "AIChatCfg17";

    private boolean dirty = false;

    // ================================================================
    // Constructor
    // ================================================================
    public Settings() {
        loadSettings();
        dirty = false;
    }

    // ================================================================
    // Getters
    // ================================================================
    public int     getTimeout()          { return timeout;          }
    public boolean isContextEnabled()    { return contextEnabled;   }
    public int     getMaxContext()       { return maxContext;        }
    public boolean isProxyEnabled()      { return proxyEnabled;     }
    public int     getThemeIndex()       { return themeIndex;       }
    public boolean isAIToolsEnabled()    { return aiToolsEnabled;   }
    public boolean isAutoSaveSession()   { return autoSaveSession;  }
    public int     getFontSizeIndex()    { return fontSizeIndex;    }

    // v1.7: 9 theme names
    public String getThemeName() {
        return getThemeNames()[themeIndex % 9];
    }

    public static String[] getThemeNames() {
        return new String[]{
            "Dark",     // 0 - navy + orange
            "ChatGPT",  // 1 - dark + green
            "Ocean",    // 2 - deep blue + cyan
            "Amoled",   // 3 - black + purple
            "Sunset",   // 4 - warm dark + coral
            "Forest",   // 5 - dark green
            "Rose",     // 6 - dark + pink
            "Hacker",   // 7 - black + green (Matrix)
            "Ice"       // 8 - light / bright
        };
    }

    public boolean hasUnsavedChanges() { return dirty; }

    // ================================================================
    // Setters (immediate persist)
    // ================================================================
    public void setTimeout(int t)          { if (validateTimeout(t))   { timeout = t;             saveAll(); } }
    public void setContextEnabled(boolean e){ contextEnabled = e;        saveAll(); }
    public void setMaxContext(int m)        { if (validateMaxContext(m)) { maxContext = m;           saveAll(); } }
    public void setProxyEnabled(boolean p)  { proxyEnabled = p;          saveAll(); }
    public void setThemeIndex(int i)        { themeIndex = clampTheme(i); saveAll(); }
    public void setAIToolsEnabled(boolean a){ aiToolsEnabled = a;         saveAll(); }
    public void setAutoSaveSession(boolean a){ autoSaveSession = a;       saveAll(); }
    public void setFontSizeIndex(int i)     { fontSizeIndex = i % 2;      saveAll(); }

    // ================================================================
    // Silent setters (batch mode)
    // ================================================================
    public void setTimeoutSilent(int t)          { if (validateTimeout(t))   { timeout = t;              dirty = true; } }
    public void setContextEnabledSilent(boolean e){ contextEnabled = e;        dirty = true; }
    public void setMaxContextSilent(int m)        { if (validateMaxContext(m)) { maxContext = m;            dirty = true; } }
    public void setProxyEnabledSilent(boolean p)  { proxyEnabled = p;          dirty = true; }
    public void setThemeIndexSilent(int i)        { themeIndex = clampTheme(i); dirty = true; }
    public void setAIToolsEnabledSilent(boolean a){ aiToolsEnabled = a;         dirty = true; }
    public void setAutoSaveSilent(boolean a)       { autoSaveSession = a;        dirty = true; }
    public void setFontSizeIndexSilent(int i)      { fontSizeIndex = i % 2;      dirty = true; }

    // ================================================================
    // Validation helpers
    // ================================================================
    private boolean validateTimeout(int t)    { return t >= 5000 && t <= 120000; }
    private boolean validateMaxContext(int m) { return m >= 1 && m <= 20; }
    private int clampTheme(int i) {
        if (i < 0) return 0;
        return i % 9; // v1.7: 9 themes
    }

    // ================================================================
    // Batch persist (safe delete-then-create)
    // ================================================================
    public void saveAll() {
        RecordStore rs = null;
        try {
            try {
                RecordStore.deleteRecordStore(STORE_NAME);
            } catch (RecordStoreNotFoundException e) {
            } catch (Exception e) {}

            rs = RecordStore.openRecordStore(STORE_NAME, true);

            // Format: timeout|context|maxCtx|proxy|theme|aiTools|autoSave|fontSize
            String d = timeout + "|"
                     + (contextEnabled  ? "1" : "0") + "|"
                     + maxContext + "|"
                     + (proxyEnabled    ? "1" : "0") + "|"
                     + themeIndex + "|"
                     + (aiToolsEnabled  ? "1" : "0") + "|"
                     + (autoSaveSession ? "1" : "0") + "|"
                     + fontSizeIndex;

            byte[] b = d.getBytes("UTF-8");
            rs.addRecord(b, 0, b.length);
            dirty = false;

        } catch (Exception e) {
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    // ================================================================
    // Load from RMS
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
        } catch (Exception e) {
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    private void parseSettings(String s) {
        if (s == null || s.length() == 0) return;
        try {
            int p1 = s.indexOf('|');
            int p2 = s.indexOf('|', p1+1);
            int p3 = s.indexOf('|', p2+1);
            int p4 = s.indexOf('|', p3+1);
            int p5 = s.indexOf('|', p4+1);
            int p6 = s.indexOf('|', p5+1);
            int p7 = s.indexOf('|', p6+1);

            if (p1 > 0 && p2 > p1 && p3 > p2 && p4 > p3) {
                int t = Integer.parseInt(s.substring(0, p1));
                if (validateTimeout(t)) timeout = t;

                contextEnabled = s.substring(p1+1, p2).equals("1");

                int m = Integer.parseInt(s.substring(p2+1, p3));
                if (validateMaxContext(m)) maxContext = m;

                proxyEnabled = s.substring(p3+1, p4).equals("1");

                if (p5 > p4) {
                    int th = Integer.parseInt(s.substring(p4+1, p5).trim());
                    themeIndex = clampTheme(th);
                } else {
                    int th = Integer.parseInt(s.substring(p4+1).trim());
                    themeIndex = clampTheme(th);
                    return; // old format
                }

                // v1.7 fields
                if (p6 > p5) {
                    aiToolsEnabled = s.substring(p5+1, p6).equals("1");
                }
                if (p7 > p6) {
                    autoSaveSession = s.substring(p6+1, p7).equals("1");
                    fontSizeIndex = Integer.parseInt(s.substring(p7+1).trim()) % 2;
                } else if (p6 > p5) {
                    autoSaveSession = s.substring(p6+1).equals("1");
                }
            }
        } catch (Exception e) {}
    }

    // ================================================================
    // Reset to defaults
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
        saveAll();
    }

    // ================================================================
    // Export/Import
    // ================================================================
    public String exportSettings() {
        return timeout + "|"
             + (contextEnabled  ? "1" : "0") + "|"
             + maxContext + "|"
             + (proxyEnabled    ? "1" : "0") + "|"
             + themeIndex + "|"
             + (aiToolsEnabled  ? "1" : "0") + "|"
             + (autoSaveSession ? "1" : "0") + "|"
             + fontSizeIndex;
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
    // Config summary
    // ================================================================
    public String getConfigSummary() {
        StringBuffer sb = new StringBuffer();
        sb.append("Timeout:   ").append(timeout / 1000).append("s\n");
        sb.append("Context:   ").append(contextEnabled ? "ON" : "OFF");
        if (contextEnabled) sb.append(" (").append(maxContext).append(" exchanges)");
        sb.append("\n");
        sb.append("Proxy:     ").append(proxyEnabled    ? "ON" : "OFF").append("\n");
        sb.append("Theme:     ").append(getThemeName()).append("\n");
        sb.append("AI Tools:  ").append(aiToolsEnabled  ? "ON" : "OFF").append("\n");
        sb.append("AutoSave:  ").append(autoSaveSession ? "ON" : "OFF").append("\n");
        sb.append("Font:      ").append(fontSizeIndex == 0 ? "Small" : "Medium").append("\n");
        return sb.toString();
    }

    // ================================================================
    // Utility getters
    // ================================================================
    public boolean isValidTimeout(int t)    { return validateTimeout(t); }
    public boolean isValidMaxContext(int m) { return validateMaxContext(m); }
    public int getTimeoutSeconds()          { return timeout / 1000; }
    public int getTimeoutMin()              { return 5000;   }
    public int getTimeoutMax()              { return 120000; }
    public int getContextMin()              { return 1;  }
    public int getContextMax()              { return 20; }
    public int getThemeCount()              { return 9;  } // v1.7: 9 themes
}