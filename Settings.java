import javax.microedition.rms.*;

// ================================================================
// Settings.java v1.5
// - Silent setters: don't write RMS every single field change
// - saveAll(): write everything at once when user hits Save
// - Theme change previews in SettingsScreen immediately
// ================================================================
public class Settings {

    private int     timeout        = 30000;  // ms
    private boolean contextEnabled = true;
    private int     maxContext     = 5;
    private boolean proxyEnabled   = false;
    private int     themeIndex     = 0;      // 0=Dark 1=ChatGPT 2=Ocean 3=Amoled

    private static final String STORE_NAME = "AIChatCfg15";

    public Settings() { loadSettings(); }

    // ---- Getters ----
    public int     getTimeout()       { return timeout;        }
    public boolean isContextEnabled() { return contextEnabled; }
    public int     getMaxContext()    { return maxContext;     }
    public boolean isProxyEnabled()   { return proxyEnabled;   }
    public int     getThemeIndex()    { return themeIndex;     }

    // ---- Setters that also persist immediately (for external callers) ----
    public void setTimeout(int t)          { if(t>=5000&&t<=120000){timeout=t; saveAll();} }
    public void setContextEnabled(boolean e){ contextEnabled=e; saveAll(); }
    public void setMaxContext(int m)        { if(m>=1&&m<=20){maxContext=m; saveAll();} }
    public void setProxyEnabled(boolean p)  { proxyEnabled=p; saveAll(); }
    public void setThemeIndex(int i)        { themeIndex=i%4; saveAll(); }

    // ---- Silent setters: update in-memory only, call saveAll() separately ----
    public void setTimeoutSilent(int t)          { if(t>=5000&&t<=120000) timeout=t; }
    public void setContextEnabledSilent(boolean e){ contextEnabled=e; }
    public void setMaxContextSilent(int m)        { if(m>=1&&m<=20) maxContext=m; }
    public void setProxyEnabledSilent(boolean p)  { proxyEnabled=p; }
    public void setThemeIndexSilent(int i)        { themeIndex=i%4; }

    // ---- Batch persist (call after all silent sets) ----
    public void saveAll() {
        RecordStore rs = null;
        try {
            try { RecordStore.deleteRecordStore(STORE_NAME); } catch (Exception e) {}
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            String d = timeout + "|"
                     + (contextEnabled ? "1" : "0") + "|"
                     + maxContext + "|"
                     + (proxyEnabled ? "1" : "0") + "|"
                     + themeIndex;
            byte[] b = d.getBytes();
            rs.addRecord(b, 0, b.length);
        } catch (Exception e) {
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    private void loadSettings() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, false);
            if (rs.getNumRecords() > 0) {
                String s = new String(rs.getRecord(1));
                int p1=s.indexOf('|'), p2=s.indexOf('|',p1+1),
                    p3=s.indexOf('|',p2+1), p4=s.indexOf('|',p3+1);
                if (p1>0&&p2>p1&&p3>p2) {
                    timeout        = Integer.parseInt(s.substring(0,p1));
                    contextEnabled = s.substring(p1+1,p2).equals("1");
                    maxContext     = Integer.parseInt(s.substring(p2+1,p3));
                    proxyEnabled   = s.substring(p3+1, p4>0?p4:s.length()).equals("1");
                    if (p4>0) themeIndex = Integer.parseInt(s.substring(p4+1).trim());
                }
            }
        } catch (RecordStoreNotFoundException e) {
            // First run — use defaults
        } catch (Exception e) {
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    public void resetToDefaults() {
        timeout=30000; contextEnabled=true; maxContext=5; proxyEnabled=false; themeIndex=0;
        saveAll();
    }
}
