import javax.microedition.rms.*;
import javax.microedition.io.file.*;
import javax.microedition.io.*;
import java.io.*;
import java.util.Enumeration;
import java.util.Vector;

public class SaveManager {
    
    // JSR-75 enabled flag
    private boolean fileAPIAvailable = false;
    private String defaultSavePath = null;
    
    // Common paths for different devices
    private static final String[] COMMON_PATHS = {
        "file:///E:/",           // Nokia E: drive
        "file:///C:/Data/",      // Nokia C:/Data/
        "file:///SDCard/",       // Generic SD card
        "file:///MemoryCard/",   // Some devices
        "file:///root1/",        // Sony Ericsson
        "file:///Phone/",        // Some Samsung
    };
    
    public SaveManager() {
        detectFileAPI();
        findBestSavePath();
    }
    
    /**
     * Detect if JSR-75 FileConnection API is available
     */
    private void detectFileAPI() {
        try {
            Class.forName("javax.microedition.io.file.FileConnection");
            fileAPIAvailable = true;
        } catch (ClassNotFoundException e) {
            fileAPIAvailable = false;
        }
    }
    
    /**
     * Find the best available path for saving files
     */
    private void findBestSavePath() {
        if (!fileAPIAvailable) return;
        
        try {
            // Try to get roots from FileSystemRegistry
            Enumeration roots = FileSystemRegistry.listRoots();
            
            if (roots != null && roots.hasMoreElements()) {
                // Use first available root
                String root = (String) roots.nextElement();
                defaultSavePath = "file:///" + root + "AIChatBot/";
                
                // Try to create directory
                ensureDirectory(defaultSavePath);
            } else {
                // Fallback to common paths
                for (int i = 0; i < COMMON_PATHS.length; i++) {
                    if (testPath(COMMON_PATHS[i])) {
                        defaultSavePath = COMMON_PATHS[i] + "AIChatBot/";
                        ensureDirectory(defaultSavePath);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Fallback to RMS only
            fileAPIAvailable = false;
        }
    }
    
    /**
     * Test if a path is accessible
     */
    private boolean testPath(String path) {
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(path, Connector.READ);
            boolean exists = fc.exists();
            fc.close();
            return exists;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (fc != null) fc.close(); } catch (Exception e) {}
        }
    }
    
    /**
     * Ensure directory exists, create if not
     */
    private void ensureDirectory(String dirPath) throws IOException {
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(dirPath, Connector.READ_WRITE);
            if (!fc.exists()) {
                fc.mkdir();
            }
        } finally {
            if (fc != null) fc.close();
        }
    }
    
    /**
     * Main save method - tries FileConnection first, falls back to RMS
     */
    public boolean saveConversation(String content, String format, String userId) {
        // Try FileConnection API first
        if (fileAPIAvailable && defaultSavePath != null) {
            boolean success = saveToFile(content, format, userId);
            if (success) return true;
        }
        
        // Fallback to RMS
        return saveToRMS(content, format, userId);
    }
    
    /**
     * Save using JSR-75 FileConnection
     */
    private boolean saveToFile(String content, String format, String userId) {
        FileConnection fc = null;
        OutputStream os = null;
        
        try {
            String fileName = createFileName(format, userId);
            String fullPath = defaultSavePath + fileName;
            
            fc = (FileConnection) Connector.open(fullPath, Connector.READ_WRITE);
            
            // Delete if exists
            if (fc.exists()) {
                fc.delete();
            }
            
            // Create file
            fc.create();
            
            // Write content
            os = fc.openOutputStream();
            
            String header = createHeader(format, userId);
            byte[] data = (header + content).getBytes("UTF-8");
            
            os.write(data);
            os.flush();
            
            return true;
            
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (os != null) os.close();
                if (fc != null) fc.close();
            } catch (Exception e) {}
        }
    }
    
    /**
     * Save using RMS (fallback)
     */
    private boolean saveToRMS(String content, String format, String userId) {
        if (format.equals("txt")) {
            return saveTXT(content, userId);
        } else if (format.equals("png")) {
            return savePNG(content, userId);
        } else if (format.equals("rms")) {
            return saveRMS(content, userId);
        }
        return false;
    }
    
    /**
     * Create appropriate header based on format
     */
    private String createHeader(String format, String userId) {
        StringBuffer header = new StringBuffer();
        long timestamp = System.currentTimeMillis();
        
        if (format.equals("txt")) {
            header.append("=== AI CHATBOT EXPORT ===\n");
            header.append("User: ").append(userId).append("\n");
            header.append("Date: ").append(timestamp).append("\n");
            header.append("Format: TXT\n");
            header.append("===========================\n\n");
        } else if (format.equals("png")) {
            header.append("PNG_EXPORT_V1\n");
            header.append("User: ").append(userId).append("\n");
            header.append("Timestamp: ").append(timestamp).append("\n");
            header.append("---\n");
        } else if (format.equals("rms")) {
            header.append("RMS_VERSION:1.1\n");
            header.append("USER:").append(userId).append("\n");
            header.append("TIMESTAMP:").append(timestamp).append("\n");
            header.append("DATA:\n");
        }
        
        return header.toString();
    }
    
    /**
     * Create filename based on format and timestamp
     */
    private String createFileName(String format, String userId) {
        long timestamp = System.currentTimeMillis();
        String timeStr = Long.toString(timestamp, 36);
        
        String extension = format;
        if (format.equals("rms")) extension = "dat";
        
        return "chat_" + timeStr + "." + extension;
    }
    
    /**
     * Get list of available save locations
     */
    public String[] getAvailableLocations() {
        if (!fileAPIAvailable) {
            return new String[] {"RMS (Internal)"};
        }
        
        Vector locations = new Vector();
        locations.addElement("RMS (Internal)");
        
        try {
            Enumeration roots = FileSystemRegistry.listRoots();
            
            while (roots.hasMoreElements()) {
                String root = (String) roots.nextElement();
                locations.addElement("file:///" + root);
            }
        } catch (Exception e) {
            // Ignore
        }
        
        String[] result = new String[locations.size()];
        for (int i = 0; i < locations.size(); i++) {
            result[i] = (String) locations.elementAt(i);
        }
        return result;
    }
    
    /**
     * Set custom save path
     */
    public boolean setSavePath(String path) {
        if (!fileAPIAvailable) return false;
        
        try {
            if (!path.endsWith("/")) path += "/";
            if (!path.endsWith("AIChatBot/")) path += "AIChatBot/";
            
            ensureDirectory(path);
            defaultSavePath = path;
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get current save path
     */
    public String getSavePath() {
        if (defaultSavePath != null) {
            return defaultSavePath;
        }
        return "RMS (Internal Memory)";
    }
    
    /**
     * List files in current save directory
     */
    public String[] listSavedFiles() {
        // Try FileConnection first
        if (fileAPIAvailable && defaultSavePath != null) {
            String[] files = listFilesFromDirectory();
            if (files != null && files.length > 0) {
                return files;
            }
        }
        
        // Fallback to RMS
        return listFilesFromRMS();
    }
    
    /**
     * List files from FileConnection directory
     */
    private String[] listFilesFromDirectory() {
        FileConnection fc = null;
        
        try {
            fc = (FileConnection) Connector.open(defaultSavePath, Connector.READ);
            
            if (!fc.exists() || !fc.isDirectory()) {
                return null;
            }
            
            Enumeration fileEnum = fc.list("chat_*.*", true);
            Vector files = new Vector();
            
            while (fileEnum.hasMoreElements()) {
                String fileName = (String) fileEnum.nextElement();
                files.addElement(fileName);
            }
            
            String[] result = new String[files.size()];
            for (int i = 0; i < files.size(); i++) {
                result[i] = (String) files.elementAt(i);
            }
            
            return result;
            
        } catch (Exception e) {
            return null;
        } finally {
            try { if (fc != null) fc.close(); } catch (Exception e) {}
        }
    }
    
    /**
     * List files from RMS
     */
    private String[] listFilesFromRMS() {
        try {
            String[] stores = RecordStore.listRecordStores();
            if (stores == null) return new String[0];
            
            int count = 0;
            for (int i = 0; i < stores.length; i++) {
                if (stores[i].startsWith("TXT_") || 
                    stores[i].startsWith("PNG_") || 
                    stores[i].startsWith("RMS_")) {
                    count++;
                }
            }
            
            String[] result = new String[count];
            int idx = 0;
            
            for (int i = 0; i < stores.length; i++) {
                if (stores[i].startsWith("TXT_") || 
                    stores[i].startsWith("PNG_") || 
                    stores[i].startsWith("RMS_")) {
                    result[idx++] = stores[i];
                }
            }
            
            return result;
        } catch (Exception e) {
            return new String[0];
        }
    }
    
    /**
     * Load file content
     */
    public String loadFile(String fileName) {
        // Try FileConnection first
        if (fileAPIAvailable && defaultSavePath != null && !fileName.startsWith("TXT_")) {
            String content = loadFromFile(fileName);
            if (content != null) return content;
        }
        
        // Fallback to RMS
        return loadFromRMS(fileName);
    }
    
    /**
     * Load from FileConnection
     */
    private String loadFromFile(String fileName) {
        FileConnection fc = null;
        InputStream is = null;
        
        try {
            String fullPath = defaultSavePath + fileName;
            fc = (FileConnection) Connector.open(fullPath, Connector.READ);
            
            if (!fc.exists()) return null;
            
            is = fc.openInputStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            byte[] buffer = new byte[1024];
            int bytesRead;
            
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            
            return new String(baos.toByteArray(), "UTF-8");
            
        } catch (Exception e) {
            return null;
        } finally {
            try {
                if (is != null) is.close();
                if (fc != null) fc.close();
            } catch (Exception e) {}
        }
    }
    
    /**
     * Load from RMS
     */
    private String loadFromRMS(String name) {
        RecordStore rs = null;
        
        try {
            rs = RecordStore.openRecordStore(name, false);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                return new String(data, "UTF-8");
            }
        } catch (Exception e) {
            return "[Erreur: " + e.getMessage() + "]";
        } finally {
            closeRS(rs);
        }
        
        return "";
    }
    
    /**
     * Delete file
     */
    public boolean deleteFile(String fileName) {
        // Try FileConnection first
        if (fileAPIAvailable && defaultSavePath != null && !fileName.startsWith("TXT_")) {
            if (deleteFromFile(fileName)) return true;
        }
        
        // Fallback to RMS
        return deleteFromRMS(fileName);
    }
    
    /**
     * Delete from FileConnection
     */
    private boolean deleteFromFile(String fileName) {
        FileConnection fc = null;
        
        try {
            String fullPath = defaultSavePath + fileName;
            fc = (FileConnection) Connector.open(fullPath, Connector.READ_WRITE);
            
            if (fc.exists()) {
                fc.delete();
                return true;
            }
            
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (fc != null) fc.close(); } catch (Exception e) {}
        }
    }
    
    /**
     * Delete from RMS
     */
    private boolean deleteFromRMS(String name) {
        try {
            RecordStore.deleteRecordStore(name);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get file size
     */
    public long getFileSize(String fileName) {
        if (fileAPIAvailable && defaultSavePath != null) {
            FileConnection fc = null;
            try {
                String fullPath = defaultSavePath + fileName;
                fc = (FileConnection) Connector.open(fullPath, Connector.READ);
                if (fc.exists()) {
                    return fc.fileSize();
                }
            } catch (Exception e) {
            } finally {
                try { if (fc != null) fc.close(); } catch (Exception e) {}
            }
        }
        
        // RMS fallback
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(fileName, false);
            if (rs.getNumRecords() > 0) {
                return rs.getRecordSize(1);
            }
        } catch (Exception e) {
        } finally {
            closeRS(rs);
        }
        
        return 0;
    }
    
    /**
     * Check if FileConnection API is available
     */
    public boolean isFileAPIAvailable() {
        return fileAPIAvailable;
    }
    
    /**
     * Get storage info
     */
    public String getStorageInfo() {
        StringBuffer info = new StringBuffer();
        
        if (fileAPIAvailable && defaultSavePath != null) {
            FileConnection fc = null;
            try {
                fc = (FileConnection) Connector.open(defaultSavePath, Connector.READ);
                
                long total = fc.totalSize();
                long avail = fc.availableSize();
                long used = total - avail;
                
                info.append("Stockage: ").append(defaultSavePath).append("\n");
                info.append("Total: ").append(total / 1024).append(" KB\n");
                info.append("Utilise: ").append(used / 1024).append(" KB\n");
                info.append("Libre: ").append(avail / 1024).append(" KB\n");
                
            } catch (Exception e) {
                info.append("Erreur lecture stockage\n");
            } finally {
                try { if (fc != null) fc.close(); } catch (Exception e) {}
            }
        } else {
            info.append("Mode: RMS uniquement\n");
            info.append("Stockage: Memoire interne\n");
        }
        
        return info.toString();
    }
    
    // ========== RMS FALLBACK METHODS ==========
    
    private boolean saveTXT(String content, String userId) {
        RecordStore rs = null;
        
        try {
            String name = createRMSName("TXT");
            rs = RecordStore.openRecordStore(name, true);
            
            String header = createHeader("txt", userId);
            byte[] data = (header + content).getBytes("UTF-8");
            rs.addRecord(data, 0, data.length);
            
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            closeRS(rs);
        }
    }
    
    private boolean savePNG(String content, String userId) {
        RecordStore rs = null;
        
        try {
            String name = createRMSName("PNG");
            rs = RecordStore.openRecordStore(name, true);
            
            String header = createHeader("png", userId);
            byte[] data = (header + content).getBytes("UTF-8");
            rs.addRecord(data, 0, data.length);
            
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            closeRS(rs);
        }
    }
    
    private boolean saveRMS(String content, String userId) {
        RecordStore rs = null;
        
        try {
            String name = createRMSName("RMS");
            rs = RecordStore.openRecordStore(name, true);
            
            String header = createHeader("rms", userId);
            byte[] data = (header + content).getBytes("UTF-8");
            rs.addRecord(data, 0, data.length);
            
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            closeRS(rs);
        }
    }
    
    private String createRMSName(String prefix) {
        String time = Long.toString(System.currentTimeMillis(), 36);
        String name = prefix + "_" + time;
        if (name.length() > 32) name = name.substring(0, 32);
        return name;
    }
    
    private void closeRS(RecordStore rs) {
        try { if (rs != null) rs.closeRecordStore(); } 
        catch (Exception e) {}
    }
}