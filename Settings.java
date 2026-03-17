import javax.microedition.rms.*;

public class Settings {
    private int timeout;
    private boolean contextEnabled;
    private int maxContext;
    private String storeName = "AIChatCfg";
    
    public Settings() {
        timeout = 30000;
        contextEnabled = true;
        maxContext = 5;
        loadSettings();
    }
    
    public int getTimeout() { return timeout; }
    
    public void setTimeout(int t) {
        if (t >= 5000 && t <= 120000) {
            timeout = t;
            saveSettings();
        }
    }
    
    public boolean isContextEnabled() { return contextEnabled; }
    
    public void setContextEnabled(boolean e) {
        contextEnabled = e;
        saveSettings();
    }
    
    public int getMaxContext() { return maxContext; }
    
    public void setMaxContext(int m) {
        if (m >= 1 && m <= 20) {
            maxContext = m;
            saveSettings();
        }
    }
    
    private void saveSettings() {
        RecordStore rs = null;
        
        try {
            try { RecordStore.deleteRecordStore(storeName); } 
            catch (Exception e) {}
            
            rs = RecordStore.openRecordStore(storeName, true);
            
            String data = timeout + "|" + (contextEnabled ? "1" : "0") + "|" + maxContext;
            byte[] bytes = data.getBytes();
            rs.addRecord(bytes, 0, bytes.length);
            
        } catch (Exception e) {
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } 
            catch (Exception e) {}
        }
    }
    
    private void loadSettings() {
        RecordStore rs = null;
        
        try {
            rs = RecordStore.openRecordStore(storeName, false);
            
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                String s = new String(data);
                
                int p1 = s.indexOf('|');
                int p2 = s.indexOf('|', p1 + 1);
                
                if (p1 > 0 && p2 > p1) {
                    timeout = Integer.parseInt(s.substring(0, p1));
                    contextEnabled = s.substring(p1 + 1, p2).equals("1");
                    maxContext = Integer.parseInt(s.substring(p2 + 1));
                }
            }
            
        } catch (RecordStoreNotFoundException e) {
        } catch (Exception e) {
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } 
            catch (Exception e) {}
        }
    }
    
    public void resetToDefaults() {
        timeout = 30000;
        contextEnabled = true;
        maxContext = 5;
        saveSettings();
    }
}