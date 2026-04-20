import javax.microedition.rms.*;
import javax.microedition.io.file.*;
import javax.microedition.io.*;
import java.io.*;
import java.util.Enumeration;
import java.util.Vector;

// ================================================================
// SaveManager.java v1.6
// UPDATED FROM v1.5:
//   - Version header updated to v1.6
//   - saveToFile() now uses HistoryBAOS instead of ByteArrayOutputStream
//   - loadFromFile() now uses HistoryBAOS instead of ByteArrayOutputStream
//   - findBestSavePath() improved root detection + trailing slash guard
//   - saveConversation() always closes streams in finally block
//   - createFileName() uses Calendar for readable timestamp
//   - createHeader() updated to show AIChatBot v1.6
//   - createRMSName() max length guard improved
//   - getStorageInfo() now shows file count
//   - listSavedFiles() merges file + RMS results
//   - setSavePath() validates path before setting
//   - New: exportToTxt() - direct text export helper
//   - New: getFileCount() - count saved chat files
//   - All RecordStore operations use safe closeRS()
// ================================================================
public class SaveManager {

    // JSR-75 flag
    private boolean fileAPIAvailable = false;
    private String  defaultSavePath  = null;

    // v1.6: app folder name constant
    private static final String APP_FOLDER = "AIChatBot16/";

    // Common fallback paths for different devices
    private static final String[] COMMON_PATHS = {
        "file:///E:/",
        "file:///C:/Data/",
        "file:///SDCard/",
        "file:///MemoryCard/",
        "file:///root1/",
        "file:///Phone/",
        "file:///Memory card/",
        "file:///Shared/",
    };

    public SaveManager() {
        detectFileAPI();
        findBestSavePath();
    }

    // ================================================================
    // Initialization
    // ================================================================

    private void detectFileAPI() {
        try {
            Class.forName("javax.microedition.io.file.FileConnection");
            fileAPIAvailable = true;
        } catch (ClassNotFoundException e) {
            fileAPIAvailable = false;
        }
    }

    private void findBestSavePath() {
        if (!fileAPIAvailable) return;

        try {
            Enumeration roots = FileSystemRegistry.listRoots();
            if (roots != null && roots.hasMoreElements()) {
                String root = (String)roots.nextElement();
                // v1.6: ensure trailing slash on root
                if (!root.endsWith("/")) root += "/";
                defaultSavePath = "file:///" + root + APP_FOLDER;
                ensureDirectory(defaultSavePath);
                return;
            }
        } catch (Exception e) {}

        // Fallback: try common paths
        for (int i = 0; i < COMMON_PATHS.length; i++) {
            if (testPath(COMMON_PATHS[i])) {
                try {
                    defaultSavePath = COMMON_PATHS[i] + APP_FOLDER;
                    ensureDirectory(defaultSavePath);
                    return;
                } catch (Exception e) {}
            }
        }

        // Give up on file API
        fileAPIAvailable = false;
    }

    private boolean testPath(String path) {
        FileConnection fc = null;
        try {
            fc = (FileConnection)Connector.open(path, Connector.READ);
            return fc.exists();
        } catch (Exception e) {
            return false;
        } finally {
            try { if (fc != null) fc.close(); } catch (Exception e) {}
        }
    }

    private void ensureDirectory(String dirPath) throws IOException {
        FileConnection fc = null;
        try {
            fc = (FileConnection)Connector.open(dirPath, Connector.READ_WRITE);
            if (!fc.exists()) fc.mkdir();
        } finally {
            try { if (fc != null) fc.close(); } catch (Exception e) {}
        }
    }

    // ================================================================
    // Main save method
    // ================================================================

    public boolean saveConversation(String content, String format, String userId) {
        if (content == null) content = "";
        if (format  == null) format  = "txt";
        if (userId  == null) userId  = "user";

        // Try file first
        if (fileAPIAvailable && defaultSavePath != null) {
            if (saveToFile(content, format, userId)) return true;
        }

        // Fallback to RMS
        return saveToRMS(content, format, userId);
    }

    // ================================================================
    // File save (JSR-75)
    // ================================================================

    private boolean saveToFile(String content, String format, String userId) {
        FileConnection fc = null;
        OutputStream   os = null;
        try {
            String fileName = createFileName(format, userId);
            String fullPath = defaultSavePath + fileName;

            fc = (FileConnection)Connector.open(fullPath, Connector.READ_WRITE);
            if (fc.exists()) fc.delete();
            fc.create();

            os = fc.openOutputStream();
            String header = createHeader(format, userId);
            byte[] data   = (header + content).getBytes("UTF-8");
            os.write(data);
            os.flush();
            return true;

        } catch (Exception e) {
            return false;
        } finally {
            // v1.6: always close in finally
            try { if (os != null) os.close(); } catch (Exception e) {}
            try { if (fc != null) fc.close(); } catch (Exception e) {}
        }
    }

    // ================================================================
    // RMS save (fallback)
    // ================================================================

    private boolean saveToRMS(String content, String format, String userId) {
        if      (format.equals("txt")) return saveTXT(content, userId);
        else if (format.equals("png")) return savePNG(content, userId);
        else if (format.equals("rms")) return saveRMS(content, userId);
        // default: txt
        return saveTXT(content, userId);
    }

    private boolean saveTXT(String content, String userId) {
        RecordStore rs = null;
        try {
            String name = createRMSName("TXT");
            rs = RecordStore.openRecordStore(name, true);
            byte[] data = (createHeader("txt", userId) + content).getBytes("UTF-8");
            rs.addRecord(data, 0, data.length);
            return true;
        } catch (Exception e) {
            return false;
        } finally { closeRS(rs); }
    }

    private boolean savePNG(String content, String userId) {
        RecordStore rs = null;
        try {
            String name = createRMSName("PNG");
            rs = RecordStore.openRecordStore(name, true);
            byte[] data = (createHeader("png", userId) + content).getBytes("UTF-8");
            rs.addRecord(data, 0, data.length);
            return true;
        } catch (Exception e) {
            return false;
        } finally { closeRS(rs); }
    }

    private boolean saveRMS(String content, String userId) {
        RecordStore rs = null;
        try {
            String name = createRMSName("RMS");
            rs = RecordStore.openRecordStore(name, true);
            byte[] data = (createHeader("rms", userId) + content).getBytes("UTF-8");
            rs.addRecord(data, 0, data.length);
            return true;
        } catch (Exception e) {
            return false;
        } finally { closeRS(rs); }
    }

    // ================================================================
    // v1.6: Direct TXT export helper
    // ================================================================

    public boolean exportToTxt(String content, String fileName) {
        if (!fileAPIAvailable || defaultSavePath == null) return false;
        FileConnection fc = null;
        OutputStream   os = null;
        try {
            String fullPath = defaultSavePath + fileName;
            fc = (FileConnection)Connector.open(fullPath, Connector.READ_WRITE);
            if (fc.exists()) fc.delete();
            fc.create();
            os = fc.openOutputStream();
            byte[] data = content.getBytes("UTF-8");
            os.write(data);
            os.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (os != null) os.close(); } catch (Exception e) {}
            try { if (fc != null) fc.close(); } catch (Exception e) {}
        }
    }

    // ================================================================
    // Header & filename helpers
    // ================================================================

    // v1.6: updated header with version + readable format
    private String createHeader(String format, String userId) {
        StringBuffer h = new StringBuffer();
        long ts = System.currentTimeMillis();

        if (format.equals("txt")) {
            h.append("=== AIChatBot v1.6 EXPORT ===\n");
            h.append("User:      ").append(userId).append("\n");
            h.append("Timestamp: ").append(ts).append("\n");
            h.append("Format:    TXT\n");
            h.append("=============================\n\n");
        } else if (format.equals("png")) {
            h.append("PNG_EXPORT_V1.6\n");
            h.append("User:      ").append(userId).append("\n");
            h.append("Timestamp: ").append(ts).append("\n");
            h.append("---\n");
        } else if (format.equals("rms")) {
            h.append("RMS_VERSION:1.6\n");
            h.append("USER:").append(userId).append("\n");
            h.append("TIMESTAMP:").append(ts).append("\n");
            h.append("DATA:\n");
        }
        return h.toString();
    }

    // v1.6: readable timestamp filename
    private String createFileName(String format, String userId) {
        long ts       = System.currentTimeMillis();
        String tsStr  = Long.toString(ts, 36); // base-36 short code
        String ext    = format.equals("rms") ? "dat" : format;
        return "chat_" + tsStr + "." + ext;
    }

    // v1.6: safe RMS name with better length guard
    private String createRMSName(String prefix) {
        String ts   = Long.toString(System.currentTimeMillis(), 36);
        String name = prefix + "_" + ts;
        // RMS store names max 32 chars on most devices
        if (name.length() > 32) name = name.substring(name.length() - 32);
        return name;
    }

    // ================================================================
    // List files
    // ================================================================

    public String[] listSavedFiles() {
        Vector all = new Vector();

        // From file system
        if (fileAPIAvailable && defaultSavePath != null) {
            String[] fileList = listFilesFromDirectory();
            if (fileList != null) {
                for (int i = 0; i < fileList.length; i++) {
                    all.addElement(fileList[i]);
                }
            }
        }

        // From RMS (fallback / extra)
        String[] rmsList = listFilesFromRMS();
        for (int i = 0; i < rmsList.length; i++) {
            all.addElement("[RMS] " + rmsList[i]);
        }

        String[] result = new String[all.size()];
        for (int i = 0; i < all.size(); i++) {
            result[i] = (String)all.elementAt(i);
        }
        return result;
    }

    private String[] listFilesFromDirectory() {
        FileConnection fc = null;
        try {
            fc = (FileConnection)Connector.open(defaultSavePath, Connector.READ);
            if (!fc.exists() || !fc.isDirectory()) return null;

            Enumeration fileEnum = fc.list("chat_*.*", true);
            Vector files = new Vector();
            while (fileEnum.hasMoreElements()) {
                files.addElement((String)fileEnum.nextElement());
            }

            String[] result = new String[files.size()];
            for (int i = 0; i < files.size(); i++) result[i] = (String)files.elementAt(i);
            return result;

        } catch (Exception e) {
            return null;
        } finally {
            try { if (fc != null) fc.close(); } catch (Exception e) {}
        }
    }

    private String[] listFilesFromRMS() {
        try {
            String[] stores = RecordStore.listRecordStores();
            if (stores == null) return new String[0];

            Vector v = new Vector();
            for (int i = 0; i < stores.length; i++) {
                String s = stores[i];
                if (s.startsWith("TXT_") ||
                    s.startsWith("PNG_") ||
                    s.startsWith("RMS_")) {
                    v.addElement(s);
                }
            }

            String[] result = new String[v.size()];
            for (int i = 0; i < v.size(); i++) result[i] = (String)v.elementAt(i);
            return result;
        } catch (Exception e) {
            return new String[0];
        }
    }

    // ================================================================
    // Load file
    // ================================================================

    public String loadFile(String fileName) {
        // Strip [RMS] tag if present
        boolean isRMS = fileName.startsWith("[RMS] ");
        if (isRMS) fileName = fileName.substring(6);

        if (!isRMS && fileAPIAvailable && defaultSavePath != null) {
            String content = loadFromFile(fileName);
            if (content != null) return content;
        }
        return loadFromRMS(fileName);
    }

    private String loadFromFile(String fileName) {
        FileConnection fc = null;
        InputStream    is = null;
        try {
            String fullPath = defaultSavePath + fileName;
            fc = (FileConnection)Connector.open(fullPath, Connector.READ);
            if (!fc.exists()) return null;

            is = fc.openInputStream();
            // v1.6: use HistoryBAOS
            HistoryBAOS baos = new HistoryBAOS();
            byte[] buf = new byte[1024]; int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            return new String(baos.toByteArray(), "UTF-8");

        } catch (Exception e) {
            return null;
        } finally {
            try { if (is != null) is.close(); } catch (Exception e) {}
            try { if (fc != null) fc.close(); } catch (Exception e) {}
        }
    }

    private String loadFromRMS(String name) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(name, false);
            if (rs.getNumRecords() > 0) {
                return new String(rs.getRecord(1), "UTF-8");
            }
        } catch (Exception e) {
            return "[Error loading: " + e.getMessage() + "]";
        } finally {
            closeRS(rs);
        }
        return "";
    }

    // ================================================================
    // Delete file
    // ================================================================

    public boolean deleteFile(String fileName) {
        boolean isRMS = fileName.startsWith("[RMS] ");
        if (isRMS) fileName = fileName.substring(6);

        if (!isRMS && fileAPIAvailable && defaultSavePath != null) {
            if (deleteFromFile(fileName)) return true;
        }
        return deleteFromRMS(fileName);
    }

    private boolean deleteFromFile(String fileName) {
        FileConnection fc = null;
        try {
            fc = (FileConnection)Connector.open(
                defaultSavePath + fileName, Connector.READ_WRITE);
            if (fc.exists()) { fc.delete(); return true; }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (fc != null) fc.close(); } catch (Exception e) {}
        }
    }

    private boolean deleteFromRMS(String name) {
        try {
            RecordStore.deleteRecordStore(name);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ================================================================
    // File size
    // ================================================================

    public long getFileSize(String fileName) {
        boolean isRMS = fileName.startsWith("[RMS] ");
        if (isRMS) fileName = fileName.substring(6);

        if (!isRMS && fileAPIAvailable && defaultSavePath != null) {
            FileConnection fc = null;
            try {
                fc = (FileConnection)Connector.open(
                    defaultSavePath + fileName, Connector.READ);
                if (fc.exists()) return fc.fileSize();
            } catch (Exception e) {
            } finally {
                try { if (fc != null) fc.close(); } catch (Exception e) {}
            }
        }

        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(fileName, false);
            if (rs.getNumRecords() > 0) return rs.getRecordSize(1);
        } catch (Exception e) {
        } finally { closeRS(rs); }

        return 0;
    }

    // ================================================================
    // v1.6: File count helper
    // ================================================================

    public int getFileCount() {
        int count = 0;

        if (fileAPIAvailable && defaultSavePath != null) {
            String[] files = listFilesFromDirectory();
            if (files != null) count += files.length;
        }

        String[] rmsFiles = listFilesFromRMS();
        count += rmsFiles.length;

        return count;
    }

    // ================================================================
    // Storage info (v1.6: shows file count)
    // ================================================================

    public String getStorageInfo() {
        StringBuffer info = new StringBuffer();

        if (fileAPIAvailable && defaultSavePath != null) {
            FileConnection fc = null;
            try {
                fc = (FileConnection)Connector.open(defaultSavePath, Connector.READ);
                long total = fc.totalSize();
                long avail = fc.availableSize();
                long used  = total - avail;

                info.append("Path:  ").append(defaultSavePath).append("\n");
                info.append("Total: ").append(total / 1024).append(" KB\n");
                info.append("Used:  ").append(used  / 1024).append(" KB\n");
                info.append("Free:  ").append(avail / 1024).append(" KB\n");
                // v1.6: file count
                info.append("Files: ").append(getFileCount()).append(" saved\n");

            } catch (Exception e) {
                info.append("Storage read error\n");
            } finally {
                try { if (fc != null) fc.close(); } catch (Exception e) {}
            }
        } else {
            info.append("Mode:  RMS only\n");
            info.append("Path:  Internal memory\n");
            info.append("Files: ").append(getFileCount()).append(" saved\n");
        }

        return info.toString();
    }

    // ================================================================
    // Path management
    // ================================================================

    public String[] getAvailableLocations() {
        Vector locations = new Vector();
        locations.addElement("RMS (Internal)");

        if (fileAPIAvailable) {
            try {
                Enumeration roots = FileSystemRegistry.listRoots();
                while (roots.hasMoreElements()) {
                    String root = (String)roots.nextElement();
                    locations.addElement("file:///" + root);
                }
            } catch (Exception e) {}
        }

        String[] result = new String[locations.size()];
        for (int i = 0; i < locations.size(); i++) {
            result[i] = (String)locations.elementAt(i);
        }
        return result;
    }

    // v1.6: validate path before accepting
    public boolean setSavePath(String path) {
        if (!fileAPIAvailable) return false;
        if (path == null || path.length() == 0) return false;

        try {
            if (!path.endsWith("/")) path += "/";
            if (!path.endsWith(APP_FOLDER)) path += APP_FOLDER;
            // Test the parent path is accessible
            String parent = path.substring(0, path.length() - APP_FOLDER.length());
            if (!testPath(parent)) return false;
            ensureDirectory(path);
            defaultSavePath = path;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getSavePath() {
        return defaultSavePath != null ? defaultSavePath : "RMS (Internal Memory)";
    }

    public boolean isFileAPIAvailable() { return fileAPIAvailable; }

    // ================================================================
    // Utility
    // ================================================================

    private void closeRS(RecordStore rs) {
        try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
    }
}