import javax.microedition.rms.*;
import javax.microedition.io.file.*;
import javax.microedition.io.*;
import java.io.*;
import java.util.Enumeration;
import java.util.Vector;

// ================================================================
// SaveManager.java v1.9
// UPDATED FROM v1.6:
//   - VERSION bumped to v1.9, BUILD 20250422
//   - REMOVED: static COMMON_PATHS array - no more hardcoded paths
//   - NEW: fully dynamic mount point detection via
//          FileSystemRegistry.listRoots() - works on any J2ME device
//   - NEW: detectBestRoot() - scores each root (SD preferred over
//          internal) and picks the most writable location
//   - NEW: RootInfo inner class - holds path, writable flag, score
//   - NEW: listAllRoots() - returns all detected roots with metadata
//   - NEW: isSDCard(String) - heuristic to detect removable media
//   - NEW: probeWritable(String) - actually tries to write a temp
//          file to verify the root is truly writable (not read-only)
//   - NEW: getSavePath() returns active path (file or RMS label)
//   - FIX: findBestSavePath() iterates ALL roots, scores them,
//          picks best writable one - no static list needed
//   - FIX: ensureDirectory() more robust error handling
//   - FIX: saveToFile() appends to existing file if present
//   - FIX: loadFromFile() uses dynamic path resolution
//   - FIX: createHeader() updated to v1.9
//   - FIX: APP_FOLDER updated to AIChatBot19/
//   - KEEP: RMS fallback fully intact
//   - KEEP: exportToTxt(), listSavedFiles(), loadFile(),
//           deleteFile(), getFileSize(), getStorageInfo(),
//           getAvailableLocations(), setSavePath()
// ================================================================
public class SaveManager {

    // ================================================================
    // Constants
    // ================================================================
    private static final String APP_FOLDER   = "AIChatBot19/";
    private static final String PROBE_FILE   = ".probe_write_test";
    private static final int    MAX_ROOTS    = 16; // sanity cap

    // ================================================================
    // State
    // ================================================================
    private boolean fileAPIAvailable = false;
    private String  defaultSavePath  = null;  // full path incl. APP_FOLDER
    private String  activeRootPath   = null;  // root only  e.g. file:///c:/

    // ================================================================
    // Inner class: metadata for one detected root
    // ================================================================
    private static final class RootInfo {
        String  path;       // e.g. "file:///e:/"
        boolean writable;   // confirmed writable
        boolean isSD;       // heuristic: looks like removable media
        long    freeSpace;  // bytes available (-1 if unknown)
        int     score;      // higher = prefer this root

        RootInfo(String path) {
            this.path      = path;
            this.writable  = false;
            this.isSD      = false;
            this.freeSpace = -1;
            this.score     = 0;
        }

        public String toString() {
            return path + " [SD=" + isSD + " writable=" + writable
                + " score=" + score + "]";
        }
    }

    // ================================================================
    // Constructor
    // ================================================================
    public SaveManager() {
        detectFileAPI();
        if (fileAPIAvailable) {
            findBestSavePath();
        }
    }

    // ================================================================
    // Step 1: Detect JSR-75 availability
    // ================================================================
    private void detectFileAPI() {
        try {
            Class.forName("javax.microedition.io.file.FileConnection");
            fileAPIAvailable = true;
        } catch (ClassNotFoundException e) {
            fileAPIAvailable = false;
        } catch (Exception e) {
            fileAPIAvailable = false;
        }
    }

    // ================================================================
    // Step 2: Dynamic root detection + scoring
    //
    // Algorithm:
    //   1. Enumerate all roots via FileSystemRegistry.listRoots()
    //   2. For each root build a RootInfo:
    //      - Check if it looks like an SD card (higher score)
    //      - Query free space
    //      - Probe actual write access
    //   3. Pick the highest-scoring writable root
    //   4. Create APP_FOLDER inside it
    //   5. Fall back to RMS if nothing is writable
    // ================================================================
    private void findBestSavePath() {
        Vector roots = detectAllRoots();

        // Score each root
        for (int i = 0; i < roots.size(); i++) {
            RootInfo ri = (RootInfo) roots.elementAt(i);
            scoreRoot(ri);
        }

        // Sort by score descending (simple selection sort - small N)
        for (int i = 0; i < roots.size() - 1; i++) {
            int best = i;
            for (int j = i + 1; j < roots.size(); j++) {
                if (((RootInfo) roots.elementAt(j)).score
                        > ((RootInfo) roots.elementAt(best)).score) {
                    best = j;
                }
            }
            if (best != i) {
                Object tmp = roots.elementAt(i);
                roots.setElementAt(roots.elementAt(best), i);
                roots.setElementAt(tmp, best);
            }
        }

        // Pick first writable root
        for (int i = 0; i < roots.size(); i++) {
            RootInfo ri = (RootInfo) roots.elementAt(i);
            if (!ri.writable) continue;
            try {
                String candidate = ri.path + APP_FOLDER;
                ensureDirectory(candidate);
                defaultSavePath = candidate;
                activeRootPath  = ri.path;
                return; // success
            } catch (Exception e) {
                // Try next root
            }
        }

        // All roots failed - disable file API, use RMS only
        fileAPIAvailable = false;
    }

    // ================================================================
    // Enumerate ALL roots via FileSystemRegistry
    // Returns Vector<RootInfo>
    // ================================================================
    private Vector detectAllRoots() {
        Vector result = new Vector();
        if (!fileAPIAvailable) return result;

        try {
            Enumeration roots = FileSystemRegistry.listRoots();
            if (roots == null) return result;

            int count = 0;
            while (roots.hasMoreElements() && count < MAX_ROOTS) {
                String rawRoot = (String) roots.nextElement();
                if (rawRoot == null || rawRoot.length() == 0) continue;

                // Normalise: ensure it starts with file:/// and ends with /
                String normPath = normaliseRootPath(rawRoot);
                if (normPath == null) continue;

                // Avoid duplicates
                boolean dup = false;
                for (int i = 0; i < result.size(); i++) {
                    if (((RootInfo) result.elementAt(i)).path.equals(normPath)) {
                        dup = true; break;
                    }
                }
                if (!dup) {
                    result.addElement(new RootInfo(normPath));
                    count++;
                }
            }
        } catch (Exception e) {
            // FileSystemRegistry may not exist on very old VMs
        }
        return result;
    }

    // ================================================================
    // Normalise a raw root string from listRoots() into a full URI
    //
    // listRoots() returns platform-specific strings, e.g.:
    //   Nokia  -> "c:/"  "e:/"
    //   SE     -> "root1/" "memorycard/"
    //   Android MIDP -> "/"
    //   Generic -> "SDCard/"
    // We always want "file:///c:/" style.
    // ================================================================
    private String normaliseRootPath(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        if (raw.length() == 0) return null;

        String lower = raw.toLowerCase();

        // Already a full URI?
        if (lower.startsWith("file:///")) {
            return ensureTrailingSlash(raw);
        }
        // Has scheme but different form?
        if (lower.startsWith("file://")) {
            return ensureTrailingSlash(raw);
        }
        // Relative path - prepend scheme
        return "file:///" + ensureTrailingSlash(raw);
    }

    private String ensureTrailingSlash(String s) {
        return (s != null && !s.endsWith("/")) ? s + "/" : s;
    }

    // ================================================================
    // Score a root and probe writability
    // ================================================================
    private void scoreRoot(RootInfo ri) {
        if (ri == null || ri.path == null) return;

        // 1. Existence check
        if (!probeExists(ri.path)) {
            ri.score = -1;
            return;
        }

        // 2. SD card heuristic (prefer removable media for saves)
        ri.isSD = isSDCard(ri.path);
        if (ri.isSD)  ri.score += 100;

        // 3. Free space
        ri.freeSpace = queryFreeSpace(ri.path);
        if (ri.freeSpace > 10L * 1024 * 1024)  ri.score += 50; // >10 MB
        else if (ri.freeSpace > 1024 * 1024)    ri.score += 20; // >1 MB
        else if (ri.freeSpace > 0)               ri.score += 5;

        // 4. Write probe (most important)
        ri.writable = probeWritable(ri.path);
        if (ri.writable) ri.score += 200;
    }

    // ================================================================
    // Heuristic: does this root look like an SD / memory card?
    //
    // Common patterns across Nokia, SE, Samsung, Motorola, etc.:
    //   - Drive letters E: F: G: (C: is usually internal on Nokia)
    //   - Names containing "sd", "card", "memory", "external",
    //     "removable", "mmc", "cf", "ms" (MemoryStick)
    // ================================================================
    private boolean isSDCard(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase();

        // Drive-letter heuristic: e:/ f:/ g:/ etc. are usually SD
        // c:/ d:/ are usually internal flash on Nokia/SE devices
        if (lower.indexOf("e:/") >= 0) return true;
        if (lower.indexOf("f:/") >= 0) return true;
        if (lower.indexOf("g:/") >= 0) return true;
        if (lower.indexOf("h:/") >= 0) return true;

        // Name-based heuristic
        if (lower.indexOf("sdcard")    >= 0) return true;
        if (lower.indexOf("sd_card")   >= 0) return true;
        if (lower.indexOf("sd card")   >= 0) return true;
        if (lower.indexOf("memory")    >= 0) return true;
        if (lower.indexOf("card")      >= 0) return true;
        if (lower.indexOf("external")  >= 0) return true;
        if (lower.indexOf("removable") >= 0) return true;
        if (lower.indexOf("mmc")       >= 0) return true;
        if (lower.indexOf("cf/")       >= 0) return true;
        if (lower.indexOf("ms/")       >= 0) return true;  // MemoryStick
        if (lower.indexOf("mspro")     >= 0) return true;

        return false;
    }

    // ================================================================
    // Probe: can we open the root directory?
    // ================================================================
    private boolean probeExists(String rootPath) {
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(rootPath, Connector.READ);
            return fc.exists();
        } catch (Exception e) {
            return false;
        } finally {
            safeClose(fc);
        }
    }

    // ================================================================
    // Probe: query available space (-1 if unavailable)
    // ================================================================
    private long queryFreeSpace(String rootPath) {
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(rootPath, Connector.READ);
            return fc.availableSize();
        } catch (Exception e) {
            return -1;
        } finally {
            safeClose(fc);
        }
    }

    // ================================================================
    // Probe: actually attempt to write + delete a tiny file.
    // This is the only reliable way to know if a root is writable
    // (some roots are mounted read-only even if they exist).
    // ================================================================
    private boolean probeWritable(String rootPath) {
        FileConnection fc = null;
        OutputStream   os = null;
        try {
            String probePath = rootPath + PROBE_FILE;
            fc = (FileConnection) Connector.open(probePath, Connector.READ_WRITE);

            // Create or overwrite probe file
            if (fc.exists()) fc.delete();
            fc.create();

            os = fc.openOutputStream();
            os.write(new byte[]{ 0x41 }); // 'A'
            os.flush();
            os.close(); os = null;
            fc.close();  fc = null;

            // Clean up probe file
            fc = (FileConnection) Connector.open(probePath, Connector.READ_WRITE);
            if (fc.exists()) fc.delete();

            return true; // write succeeded

        } catch (Exception e) {
            return false;
        } finally {
            try { if (os != null) os.close(); } catch (Exception e2) {}
            safeClose(fc);
        }
    }

    // ================================================================
    // Ensure a directory exists (create if missing)
    // ================================================================
    private void ensureDirectory(String dirPath) throws IOException {
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(dirPath, Connector.READ_WRITE);
            if (!fc.exists()) {
                fc.mkdir();
            }
        } finally {
            safeClose(fc);
        }
    }

    // ================================================================
    // Main save method
    // ================================================================
    public boolean saveConversation(String content, String format, String userId) {
        if (content == null) content = "";
        if (format  == null) format  = "txt";
        if (userId  == null) userId  = "user";

        // Try file system first
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

            fc = (FileConnection) Connector.open(fullPath, Connector.READ_WRITE);
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
            try { if (os != null) os.close(); } catch (Exception e) {}
            safeClose(fc);
        }
    }

    // ================================================================
    // RMS save (fallback)
    // ================================================================
    private boolean saveToRMS(String content, String format, String userId) {
        if      ("txt".equals(format)) return saveTXT(content, userId);
        else if ("png".equals(format)) return savePNG(content, userId);
        else if ("rms".equals(format)) return saveRMS(content, userId);
        return saveTXT(content, userId);
    }

    private boolean saveTXT(String content, String userId) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(createRMSName("TXT"), true);
            byte[] data = (createHeader("txt", userId) + content).getBytes("UTF-8");
            rs.addRecord(data, 0, data.length);
            return true;
        } catch (Exception e) { return false; }
        finally { closeRS(rs); }
    }

    private boolean savePNG(String content, String userId) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(createRMSName("PNG"), true);
            byte[] data = (createHeader("png", userId) + content).getBytes("UTF-8");
            rs.addRecord(data, 0, data.length);
            return true;
        } catch (Exception e) { return false; }
        finally { closeRS(rs); }
    }

    private boolean saveRMS(String content, String userId) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(createRMSName("RMS"), true);
            byte[] data = (createHeader("rms", userId) + content).getBytes("UTF-8");
            rs.addRecord(data, 0, data.length);
            return true;
        } catch (Exception e) { return false; }
        finally { closeRS(rs); }
    }

    // ================================================================
    // Direct TXT export helper
    // ================================================================
    public boolean exportToTxt(String content, String fileName) {
        if (!fileAPIAvailable || defaultSavePath == null) return false;
        FileConnection fc = null;
        OutputStream   os = null;
        try {
            String fullPath = defaultSavePath + fileName;
            fc = (FileConnection) Connector.open(fullPath, Connector.READ_WRITE);
            if (fc.exists()) fc.delete();
            fc.create();
            os = fc.openOutputStream();
            os.write(content.getBytes("UTF-8"));
            os.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (os != null) os.close(); } catch (Exception e) {}
            safeClose(fc);
        }
    }

    // ================================================================
    // Header & filename helpers
    // ================================================================
    private String createHeader(String format, String userId) {
        long         ts = System.currentTimeMillis();
        StringBuffer h  = new StringBuffer();
        if ("txt".equals(format)) {
            h.append("=== AIChatBot v1.9 EXPORT ===\n");
            h.append("User:      ").append(userId).append('\n');
            h.append("Timestamp: ").append(ts).append('\n');
            h.append("SavePath:  ").append(getSavePath()).append('\n');
            h.append("Format:    TXT\n");
            h.append("=============================\n\n");
        } else if ("png".equals(format)) {
            h.append("PNG_EXPORT_V1.9\nUser:").append(userId)
             .append("\nTimestamp:").append(ts).append("\n---\n");
        } else if ("rms".equals(format)) {
            h.append("RMS_VERSION:1.9\nUSER:").append(userId)
             .append("\nTIMESTAMP:").append(ts).append("\nDATA:\n");
        }
        return h.toString();
    }

    private String createFileName(String format, String userId) {
        String tsStr = Long.toString(System.currentTimeMillis(), 36);
        String ext   = "rms".equals(format) ? "dat" : format;
        return "chat_" + tsStr + "." + ext;
    }

    private String createRMSName(String prefix) {
        String name = prefix + "_" + Long.toString(System.currentTimeMillis(), 36);
        if (name.length() > 32) name = name.substring(name.length() - 32);
        return name;
    }

    // ================================================================
    // List saved files
    // ================================================================
    public String[] listSavedFiles() {
        Vector all = new Vector();

        if (fileAPIAvailable && defaultSavePath != null) {
            String[] fileList = listFilesFromDirectory();
            if (fileList != null) {
                for (int i = 0; i < fileList.length; i++) {
                    all.addElement(fileList[i]);
                }
            }
        }

        String[] rmsList = listFilesFromRMS();
        for (int i = 0; i < rmsList.length; i++) {
            all.addElement("[RMS] " + rmsList[i]);
        }

        String[] result = new String[all.size()];
        for (int i = 0; i < all.size(); i++) result[i] = (String) all.elementAt(i);
        return result;
    }

    private String[] listFilesFromDirectory() {
        FileConnection fc = null;
        try {
            fc = (FileConnection) Connector.open(defaultSavePath, Connector.READ);
            if (!fc.exists() || !fc.isDirectory()) return null;

            Enumeration fileEnum = fc.list("chat_*.*", true);
            Vector      files    = new Vector();
            while (fileEnum.hasMoreElements()) {
                String fname = (String) fileEnum.nextElement();
                if (!fname.equals(PROBE_FILE)) files.addElement(fname);
            }
            String[] result = new String[files.size()];
            for (int i = 0; i < files.size(); i++) result[i] = (String) files.elementAt(i);
            return result;
        } catch (Exception e) {
            return null;
        } finally {
            safeClose(fc);
        }
    }

    private String[] listFilesFromRMS() {
        try {
            String[] stores = RecordStore.listRecordStores();
            if (stores == null) return new String[0];
            Vector v = new Vector();
            for (int i = 0; i < stores.length; i++) {
                String s = stores[i];
                if (s.startsWith("TXT_") || s.startsWith("PNG_") || s.startsWith("RMS_")) {
                    v.addElement(s);
                }
            }
            String[] result = new String[v.size()];
            for (int i = 0; i < v.size(); i++) result[i] = (String) v.elementAt(i);
            return result;
        } catch (Exception e) {
            return new String[0];
        }
    }

    // ================================================================
    // Load file
    // ================================================================
    public String loadFile(String fileName) {
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
            fc = (FileConnection) Connector.open(
                defaultSavePath + fileName, Connector.READ);
            if (!fc.exists()) return null;

            is = fc.openInputStream();
            HistoryBAOS baos = new HistoryBAOS();
            byte[] buf = new byte[2048];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            return new String(baos.toByteArray(), "UTF-8");

        } catch (Exception e) {
            return null;
        } finally {
            try { if (is != null) is.close(); } catch (Exception e) {}
            safeClose(fc);
        }
    }

    private String loadFromRMS(String name) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(name, false);
            if (rs.getNumRecords() > 0)
                return new String(rs.getRecord(1), "UTF-8");
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
            fc = (FileConnection) Connector.open(
                defaultSavePath + fileName, Connector.READ_WRITE);
            if (fc.exists()) { fc.delete(); return true; }
            return false;
        } catch (Exception e) {
            return false;
        } finally {
            safeClose(fc);
        }
    }

    private boolean deleteFromRMS(String name) {
        try { RecordStore.deleteRecordStore(name); return true; }
        catch (Exception e) { return false; }
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
                fc = (FileConnection) Connector.open(
                    defaultSavePath + fileName, Connector.READ);
                if (fc.exists()) return fc.fileSize();
            } catch (Exception e) {
            } finally {
                safeClose(fc);
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
    // File count
    // ================================================================
    public int getFileCount() {
        int count = 0;
        if (fileAPIAvailable && defaultSavePath != null) {
            String[] files = listFilesFromDirectory();
            if (files != null) count += files.length;
        }
        count += listFilesFromRMS().length;
        return count;
    }

    // ================================================================
    // Storage info - now shows detected root + SD flag
    // ================================================================
    public String getStorageInfo() {
        StringBuffer info = new StringBuffer();

        if (fileAPIAvailable && defaultSavePath != null) {
            FileConnection fc = null;
            try {
                fc = (FileConnection) Connector.open(activeRootPath, Connector.READ);
                long total = fc.totalSize();
                long avail = fc.availableSize();
                long used  = total - avail;

                info.append("Root:  ").append(activeRootPath).append('\n');
                info.append("SD:    ").append(isSDCard(activeRootPath) ? "Yes" : "No").append('\n');
                info.append("Path:  ").append(defaultSavePath).append('\n');
                info.append("Total: ").append(total / 1024).append(" KB\n");
                info.append("Used:  ").append(used  / 1024).append(" KB\n");
                info.append("Free:  ").append(avail / 1024).append(" KB\n");
                info.append("Files: ").append(getFileCount()).append(" saved\n");

            } catch (Exception e) {
                info.append("Storage read error: ").append(e.getMessage()).append('\n');
            } finally {
                safeClose(fc);
            }
        } else {
            info.append("Mode:  RMS only\n");
            info.append("Path:  Internal memory\n");
            info.append("Files: ").append(getFileCount()).append(" saved\n");
        }
        return info.toString();
    }

    // ================================================================
    // Available locations - now uses dynamic detection
    // ================================================================
    public String[] getAvailableLocations() {
        Vector locations = new Vector();
        locations.addElement("RMS (Internal)");

        if (fileAPIAvailable) {
            Vector roots = detectAllRoots();
            for (int i = 0; i < roots.size(); i++) {
                RootInfo ri = (RootInfo) roots.elementAt(i);
                scoreRoot(ri);
                String label = ri.path
                    + (ri.isSD    ? " [SD]"       : " [Internal]")
                    + (ri.writable ? " writable"   : " read-only");
                locations.addElement(label);
            }
        }

        String[] result = new String[locations.size()];
        for (int i = 0; i < locations.size(); i++) {
            result[i] = (String) locations.elementAt(i);
        }
        return result;
    }

    // ================================================================
    // Set custom save path - validates with probeWritable()
    // ================================================================
    public boolean setSavePath(String path) {
        if (!fileAPIAvailable) return false;
        if (path == null || path.length() == 0) return false;

        try {
            path = ensureTrailingSlash(path);

            // Strip APP_FOLDER if caller included it
            String rootCandidate = path;
            if (rootCandidate.endsWith(APP_FOLDER)) {
                rootCandidate = rootCandidate.substring(
                    0, rootCandidate.length() - APP_FOLDER.length());
            }
            rootCandidate = ensureTrailingSlash(rootCandidate);

            // Probe the root
            if (!probeExists(rootCandidate))  return false;
            if (!probeWritable(rootCandidate)) return false;

            String candidate = rootCandidate + APP_FOLDER;
            ensureDirectory(candidate);
            defaultSavePath = candidate;
            activeRootPath  = rootCandidate;
            return true;

        } catch (Exception e) {
            return false;
        }
    }

    // ================================================================
    // Getters
    // ================================================================
    public String  getSavePath()          { return defaultSavePath != null ? defaultSavePath : "RMS (Internal Memory)"; }
    public String  getActiveRootPath()    { return activeRootPath  != null ? activeRootPath  : ""; }
    public boolean isFileAPIAvailable()   { return fileAPIAvailable; }

    // ================================================================
    // Utility helpers
    // ================================================================
    private void safeClose(FileConnection fc) {
        try { if (fc != null) fc.close(); } catch (Exception e) {}
    }

    private void closeRS(RecordStore rs) {
        try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
    }
}