import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import javax.microedition.rms.*;
import java.io.*;
import java.util.*;

// ==================== T9Input.java CORRIGÉ ====================
class T9Input {
    private StringBuffer buffer = new StringBuffer();
    private String currentDraft = "";
    private int lastKey = -1;
    private int tapCount = 0;
    private Timer timer = new Timer();
    private TimerTask timeoutTask;
    private boolean upperCase = false;
    
    private static final String[] LOWER_MAP = {
        " 0",        // 0 = espace + '0'
        ".,!?1",     // 1 = . , ! ? 1
        "abc2",      // 2 = a b c 2
        "def3",      // 3 = d e f 3
        "ghi4",      // 4 = g h i 4
        "jkl5",      // 5 = j k l 5
        "mno6",      // 6 = m n o 6
        "pqrs7",     // 7 = p q r s 7
        "tuv8",      // 8 = t u v 8
        "wxyz9"      // 9 = w x y z 9
    };
    
    private static final String[] UPPER_MAP = {
        " 0",
        ".,!?1",
        "ABC2",
        "DEF3",
        "GHI4",
        "JKL5",
        "MNO6",
        "PQRS7",
        "TUV8",
        "WXYZ9"
    };
    
    public T9Input() {
        buffer = new StringBuffer();
    }
    
    public void keyPressed(int key) {
        if (key < 0 || key > 9) return;
        
        if (key != lastKey && lastKey != -1) {
            commitCharacter();
        }
        
        if (key == lastKey) {
            tapCount++;
        } else {
            lastKey = key;
            tapCount = 0;
        }
        
        String options = upperCase ? UPPER_MAP[key] : LOWER_MAP[key];
        char selected = options.charAt(tapCount % options.length());
        currentDraft = String.valueOf(selected);
        resetTimer();
    }
    
    private void commitCharacter() {
        if (currentDraft.length() > 0) {
            buffer.append(currentDraft);
            currentDraft = "";
            lastKey = -1;
            tapCount = 0;
        }
    }
    
    private void resetTimer() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }
        timeoutTask = new TimerTask() {
            public void run() {
                commitCharacter();
            }
        };
        timer.schedule(timeoutTask, 1000);
    }
    
    public String getText() {
        return buffer.toString() + currentDraft;
    }
    
    public void backspace() {
        if (currentDraft.length() > 0) {
            currentDraft = "";
            lastKey = -1;
            tapCount = 0;
        } else if (buffer.length() > 0) {
            buffer.setLength(buffer.length() - 1);
        }
    }
    
    public void clear() {
        buffer.setLength(0);
        currentDraft = "";
        lastKey = -1;
        tapCount = 0;
        if (timeoutTask != null) timeoutTask.cancel();
    }
    
    public void toggleCase() {
        commitCharacter();
        upperCase = !upperCase;
    }
    
    public boolean isUpperCase() {
        return upperCase;
    }
    
    public void flush() {
        commitCharacter();
    }
    
    public void setText(String text) {
        clear();
        buffer.append(text);
    }
}
// ============================================================

class BootScreen extends Canvas {
    private Display display;
    private Timer timer;
    private int phase = 0;
    private final String[] SPLASH_TEXT = {
        "Grok AI", 
        "Coder Assistant", 
        "Loading...", 
        "Ready!"
    };
    private final int[] SPLASH_DELAY = {1200, 1000, 1500, 500};
    private AIChatBot midlet;
    private boolean completed = false;

    public BootScreen(Display display, AIChatBot midlet) {
        this.display = display;
        this.midlet = midlet;
        setFullScreenMode(true);
    }

    protected void paint(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        
        for (int y = 0; y < height; y++) {
            int gray = 220 - (y * 120 / height);
            g.setColor(gray, gray, gray);
            g.drawLine(0, y, width, y);
        }
        
        g.setColor(0x000088);
        g.setFont(Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_BOLD, Font.SIZE_LARGE));
        
        if (phase < 3) {
            String dots = "";
            for (int i = 0; i < (phase * 2) % 4; i++) dots += ".";
            g.drawString(SPLASH_TEXT[phase] + dots, width/2, height/2 - 20, 
                        Graphics.TOP | Graphics.HCENTER);
            
            int barWidth = width - 40;
            int progress = (phase + 1) * barWidth / 4;
            g.setColor(0xDDDDDD);
            g.drawRect(20, height/2 + 10, barWidth, 8);
            g.setColor(0x008800);
            g.fillRect(21, height/2 + 11, progress, 6);
        } else {
            g.setColor(0x0000AA);
            g.drawString("GROK", width/2, height/2 - 30, Graphics.TOP | Graphics.HCENTER);
            g.setColor(0x000088);
            g.setFont(Font.getDefaultFont());
            g.drawString("Coder Assistant", width/2, height/2 - 10, Graphics.TOP | Graphics.HCENTER);
            g.setColor(0x555555);
            g.drawString("v1.0 - J2ME Edition", width/2, height/2 + 10, Graphics.TOP | Graphics.HCENTER);
            g.drawString("2=abc 8=tuv  RSK=Del", width/2, height/2 + 25, Graphics.TOP | Graphics.HCENTER);
        }
    }

    public void start() {
        if (timer != null) timer.cancel();
        timer = new Timer();
        timer.schedule(new TimerTask() {
            public void run() {
                phase++;
                if (phase < SPLASH_DELAY.length) {
                    repaint();
                    timer.schedule(new TimerTask() {
                        public void run() {
                            if (!completed) start();
                        }
                    }, SPLASH_DELAY[phase-1]);
                } else {
                    completed = true;
                    display.setCurrent(midlet.getMainDisplayable());
                }
            }
        }, SPLASH_DELAY[0]);
    }

    public void stop() {
        if (timer != null) timer.cancel();
        timer = null;
    }
}

public class AIChatBot extends MIDlet implements CommandListener {
    private Display display;
    private BootScreen bootScreen;
    private TextBox nameBox;
    private Command sendCommand;
    private Command saveCommand;
    private Command clearCommand;
    private Command exitCommand;
    private Command okCommand;
    private String session = null;
    private String userName = "User";
    private final String baseUrl = "https://apidl.asepharyana.tech/api/ai/chatgpt?text=";
    private final String asciiApiUrl = "https://api.ascii-art.io/generate?text=";
    private final String instructions = " Answer in English and act as Coder Assistant. Keep answers concise.";
    private ChatCanvas canvas;
    private boolean isWaitingResponse = false;
    private boolean debugMode = false;
    private StringBuffer debugLog = new StringBuffer();
    private long lastNetworkCheck = 0;
    private boolean networkAvailable = true;
    private Displayable mainDisplayable = null;

    public void startApp() {
        display = Display.getDisplay(this);
        bootScreen = new BootScreen(display, this);
        display.setCurrent(bootScreen);
        bootScreen.start();
        
        new Thread(new Runnable() {
            public void run() {
                loadUserName();
                loadChatSilent();
                checkNetworkAsync();
            }
        }).start();
    }

    private void loadChatSilent() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore("ChatHistory", false);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                String savedChat = new String(data, "UTF-8");
                savedChat = replaceString(savedChat, "G:", "Grok: ");
                savedChat = replaceString(savedChat, "U:", userName + ": ");
                if (canvas != null) canvas.setChatHistory(savedChat);
                else mainDisplayable = createMainDisplayable(savedChat);
            } else mainDisplayable = createMainDisplayable(null);
        } catch (Exception e) {} finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Exception ignored) {}
        }
    }

    private Displayable createMainDisplayable(String history) {
        if (userName.equals("User")) {
            showNamePrompt();
            return nameBox;
        } else {
            canvas = new ChatCanvas(this);
            if (history != null) canvas.setChatHistory(history);
            else canvas.showWelcome();
            return canvas;
        }
    }

    public Displayable getMainDisplayable() {
        if (mainDisplayable == null) mainDisplayable = createMainDisplayable(null);
        return mainDisplayable;
    }

    private void showNamePrompt() {
        okCommand = new Command("OK", Command.OK, 1);
        exitCommand = new Command("Exit", Command.EXIT, 2);
        nameBox = new TextBox("Enter Your Name", "", 30, TextField.ANY);
        nameBox.addCommand(okCommand);
        nameBox.addCommand(exitCommand);
        nameBox.setCommandListener(this);
    }

    private void initChat() {
        canvas = new ChatCanvas(this);
        display.setCurrent(canvas);
        canvas.showWelcome();
    }

    public void pauseApp() {
        if (bootScreen != null) bootScreen.stop();
    }

    public void destroyApp(boolean unconditional) {
        if (bootScreen != null) bootScreen.stop();
        if (canvas != null) canvas.stopTimers();
        notifyDestroyed();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == exitCommand) destroyApp(true);
        else if (c == sendCommand && !isWaitingResponse && canvas != null) canvas.sendInput();
        else if (c == saveCommand && canvas != null) saveChat();
        else if (c == clearCommand && canvas != null) canvas.clearChat();
        else if (c == okCommand && d == nameBox) {
            String rawName = nameBox.getString().trim();
            if (rawName.equals("debug123")) {
                debugMode = !debugMode;
                if (canvas != null) canvas.showStatus(debugMode ? "DEBUG ON" : "DEBUG OFF");
                logDebug("Debug mode toggled: " + debugMode);
                return;
            }
            if (rawName.length() == 0 || rawName.length() > 30) userName = "User";
            else userName = rawName;
            saveUserName();
            initChat();
        }
    }

    private void loadUserName() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore("UserName", false);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                userName = new String(data, "UTF-8");
                if (userName.length() > 30) userName = "User";
            }
        } catch (Exception e) {} finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Exception ignored) {}
        }
    }

    private void saveUserName() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore("UserName", true);
            byte[] data = userName.getBytes("UTF-8");
            if (rs.getNumRecords() == 0) rs.addRecord(data, 0, data.length);
            else rs.setRecord(1, data, 0, data.length);
        } catch (Exception e) {} finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Exception ignored) {}
        }
    }

    void saveChat() {
        if (canvas == null) return;
        RecordStore rs = null;
        try {
            String history = canvas.getChatHistory();
            history = replaceString(history, "\n\n", "\n");
            history = replaceString(history, "Grok: ", "G:");
            history = replaceString(history, userName + ": ", "U:");
            if (history.length() > 8000) history = substringFrom(history, history.length() - 8000);
            
            rs = RecordStore.openRecordStore("ChatHistory", true);
            byte[] data = history.getBytes("UTF-8");
            if (rs.getNumRecords() == 0) rs.addRecord(data, 0, data.length);
            else rs.setRecord(1, data, 0, data.length);
            if (canvas != null) canvas.showStatus("Saved");
            logDebug("RMS saved " + data.length + " bytes");
        } catch (Exception e) {
            if (canvas != null) canvas.showStatus("Save failed");
            logDebug("RMS error: " + e.toString());
        } finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Exception ignored) {}
        }
    }

    private void loadChat() {
        if (canvas == null) return;
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore("ChatHistory", false);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(1);
                String savedChat = new String(data, "UTF-8");
                savedChat = replaceString(savedChat, "G:", "Grok: ");
                savedChat = replaceString(savedChat, "U:", userName + ": ");
                canvas.setChatHistory(savedChat);
            }
        } catch (Exception e) {
            canvas.appendToChat("System: Load failed.\n");
        } finally {
            if (rs != null) try { rs.closeRecordStore(); } catch (Exception ignored) {}
        }
    }

    private String replaceString(String s, String target, String replacement) {
        if (s == null || target == null || replacement == null || target.length() == 0) return s;
        StringBuffer result = new StringBuffer();
        int start = 0;
        int pos;
        while ((pos = indexOf(s, target, start)) != -1) {
            result.append(substring(s, start, pos));
            result.append(replacement);
            start = pos + target.length();
        }
        result.append(substringFrom(s, start));
        return result.toString();
    }
    
    private int indexOf(String s, String target, int fromIndex) {
        if (fromIndex < 0) fromIndex = 0;
        int max = s.length() - target.length();
        if (max < 0) return -1;
        char first = target.charAt(0);
        for (int i = fromIndex; i <= max; i++) {
            if (s.charAt(i) == first) {
                int j = 1;
                while (j < target.length() && s.charAt(i+j) == target.charAt(j)) j++;
                if (j == target.length()) return i;
            }
        }
        return -1;
    }
    
    private String substring(String s, int start, int end) {
        if (start < 0) start = 0;
        if (end > s.length()) end = s.length();
        if (start >= end) return "";
        char[] buf = new char[end - start];
        for (int i = 0; i < buf.length; i++) buf[i] = s.charAt(start + i);
        return new String(buf);
    }
    
    private String substringFrom(String s, int start) {
        return substring(s, start, s.length());
    }

    private void checkNetworkAsync() {
        new Thread(new Runnable() {
            public void run() {
                final boolean available = isNetworkAvailableImpl();
                getDisplay().callSerially(new Runnable() {
                    public void run() {
                        networkAvailable = available;
                        if (canvas != null) canvas.repaint();
                    }
                });
            }
        }).start();
    }

    private boolean isNetworkAvailableImpl() {
        if (System.currentTimeMillis() - lastNetworkCheck < 30000) return networkAvailable;
        lastNetworkCheck = System.currentTimeMillis();
        try {
            HttpConnection c = (HttpConnection)Connector.open("http://connectivitycheck.platform.hicloud.com/generate_204", Connector.READ, true);
            c.setRequestProperty("Connection", "close");
            int rc = c.getResponseCode();
            c.close();
            return (rc == 204 || rc == 200);
        } catch (Exception e) { return false; }
    }

    static final String[][] OFFLINE_SNIPPETS = {
        {"hello", "Welcome! Ask me about:\n- Java ME loops\n- RMS storage\n- HTTP connections\n- Canvas drawing\n- Threading basics\n- /image commands\n- T9: 2=abc 3=def...\n- #=Caps  *=Symbols"},
        {"loop", "for(int i=0;i<10;i++){\n  // iteration\n}\n\nwhile(cond){\n  // repeat\n}"},
        {"array", "// Fixed size\nint[] a = new int[5];\na[0] = 1;\n\n// Access\nint x = a[0];"},
        {"string", "String s = \"text\";\nint len = s.length();\nchar c = s.charAt(0);\nboolean eq = s.equals(\"text\");"},
        {"rms", "// Open\nRecordStore rs = RecordStore.openRecordStore(\"db\",true);\n\n// Write\nbyte[] d = \"data\".getBytes();\nrs.addRecord(d,0,d.length);\n\n// Close\nrs.closeRecordStore();"},
        {"canvas", "class MyCanvas extends Canvas {\n  protected void paint(Graphics g) {\n    g.drawString(\"Hi\",10,10,0);\n  }\n}"},
        {"http", "HttpConnection c = (HttpConnection)Connector.open(url);\nc.setRequestMethod(\"GET\");\nInputStream i = c.openInputStream();\n// Read data...\nc.close();"},
        {"thread", "new Thread(new Runnable(){\n  public void run(){\n    // Background task\n  }\n}).start();"},
        {"list", "List menu = new List(\"Menu\",List.IMPLICIT);\nmenu.append(\"Item1\",null);\nmenu.append(\"Item2\",null);"},
        {"alert", "Alert a = new Alert(\"Title\",\"Msg\",null,AlertType.INFO);\na.setTimeout(3000);\ndisplay.setCurrent(a);"},
        {"key", "// In Canvas:\nprotected void keyPressed(int k){\n  // 2/8 for T9 letters\n  // RSK for backspace\n  // * for symbols\n  // # for CAPS\n}"},
        {"gfx", "Graphics g = image.getGraphics();\ng.setColor(255,0,0);\ng.fillRect(10,10,50,30);\ng.setColor(0,0,0);\ng.drawString(\"Text\",20,20,0);"},
        {"help", "T9 Layout (Nokia/SE):\n1=.,!? 2=abc 3=def\n4=ghi 5=jkl 6=mno\n7=pqrs 8=tuv 9=wxyz\n0=space *=symbols #=caps\nRSK=Backspace"},
        {"java", "J2ME = CLDC 1.1 + MIDP 2.0\n✓ No generics\n✓ No enums\n✓ No StringBuilder\n✓ Max JAR: 512KB\n✓ Max heap: 512KB"},
        {"tips", "J2ME Tips:\n✓ Reuse objects\n✓ Avoid String concat\n✓ Close streams ALWAYS\n✓ Limit RMS records\n✓ Use byte arrays"},
        {"image", "Use /image <description> for ASCII art:\n/image robot\n/image heart\n/image star\n/image house\n/image tree"},
        {"symbol", "Press * repeatedly for symbols:\n! : ; , < > * $ = ) _ - + '\" & % # /"},
        {"history", "Double-press # to view history\nSingle press # = toggle CAPS"}
    };

    String getOfflineResponse(String input) {
        String lower = input.toLowerCase();
        for (int i = 0; i < OFFLINE_SNIPPETS.length; i++) {
            if (indexOf(lower, OFFLINE_SNIPPETS[i][0], 0) != -1) {
                return OFFLINE_SNIPPETS[i][1];
            }
        }
        return null;
    }

    void logDebug(String msg) {
        if (!debugMode) return;
        debugLog.append("[").append(System.currentTimeMillis() % 10000).append("] ").append(msg).append("\n");
        if (debugLog.length() > 2000) {
            StringBuffer newLog = new StringBuffer();
            for (int i = 500; i < debugLog.length(); i++) newLog.append(debugLog.charAt(i));
            debugLog = newLog;
        }
    }

    public String getUserName() { return userName; }
    public Display getDisplay() { return display; }
    public String getBaseUrl() { return baseUrl; }
    public String getAsciiApiUrl() { return asciiApiUrl; }
    public String getInstructions() { return instructions; }
    public String getSession() { return session; }
    public void setSession(String s) { session = s; }
    public boolean isWaitingResponse() { return isWaitingResponse; }
    public void setWaiting(boolean waiting) { isWaitingResponse = waiting; }
    public boolean isDebugMode() { return debugMode; }
    public StringBuffer getDebugLog() { return debugLog; }
    boolean isNetworkAvailable() { return networkAvailable; }
}

class ChatCanvas extends Canvas implements CommandListener {
    private AIChatBot midlet;
    private StringBuffer chatHistory = new StringBuffer();
    private T9Input t9Input = new T9Input();
    private int scrollY = 0;
    private Timer cursorTimer;
    private boolean showCursor = true;
    private long lastActivity = System.currentTimeMillis();
    private boolean cursorActive = true;
    private long lastSendTime = 0;
    private Font font;
    private int lineHeight;
    private String statusMessage = "";
    private long statusExpiry = 0;
    private static final int MAX_HISTORY = 8000;
    private Vector questionHistory = new Vector();
    private int historyIndex = -1;
    private static final char[] HEX_DIGITS = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};
    
    // Symboles COMPLETS pour touche *
    private static final String[] SYMBOLS = {
        "!", ":", ";", ",", "<", ">", "*", "$", "=", ")", 
        "_", "-", "+", "'", "\"", "&", "%", "#", "/", "\\",
        "(", "[", "]", "{", "}", "@", "~", "`", "^", "|"
    };
    private int starTapCount = 0;
    private int lastSymbolPos = -1;
    private Timer starTimer = null;
    private int lastKey = -1;
    
    // Gestion historique #
    private boolean lastKeyWasHash = false;
    private long lastHashPress = 0;
    private static final long DOUBLE_PRESS_TIMEOUT = 400;
    
    // Commandes avec position soft keys
    private Command sendCommand;
    private Command backspaceCommand; // RSK = Right Soft Key
    private Command exitCommand;      // Menu option

    public ChatCanvas(AIChatBot midlet) {
        this.midlet = midlet;
        this.font = Font.getDefaultFont();
        this.lineHeight = font.getHeight();
        
        // LSK (Left Soft Key) = Send
        sendCommand = new Command("Send", Command.OK, 1);
        // RSK (Right Soft Key) = Backspace (priorité haute pour position droite)
        backspaceCommand = new Command("<", Command.BACK, 1);
        // Exit dans menu
        exitCommand = new Command("Exit", Command.EXIT, 3);
        
        addCommand(sendCommand);
        addCommand(backspaceCommand);
        addCommand(exitCommand);
        setCommandListener(this);
        startCursorTimer();
    }

    private void startCursorTimer() {
        if (cursorTimer != null) cursorTimer.cancel();
        cursorTimer = new Timer();
        cursorTimer.schedule(new TimerTask() {
            public void run() {
                long now = System.currentTimeMillis();
                if (now - lastActivity > 15000) cursorActive = false;
                else {
                    cursorActive = true;
                    showCursor = !showCursor;
                }
                repaint();
            }
        }, 0, 400);
    }

    public void showWelcome() {
        String[] welcomes = {
            "Grok: Hello " + midlet.getUserName() + "! I'm your Coder Assistant.\nT9 Layout: 2=abc 3=def 4=ghi...\n#=Caps  *=Symbols  RSK=Del\n",
            "Grok: Hi " + midlet.getUserName() + "! Ready to code?\nTry: 'loop example' or '/image heart'\nT9: Press 2->a 22->b 222->c\nRSK deletes last character\n",
            "Grok: Welcome " + midlet.getUserName() + "!\nJ2ME questions? I've got answers!\n# = toggle CAPS mode\n* = symbols !:;,<>\nRSK = backspace\n",
            "Grok: Hey " + midlet.getUserName() + "! Coder\nWhat shall we build today?\nT9: 8->t 88->u 888->v\nRSK erases mistakes\n"
        };
        int idx = (int)(System.currentTimeMillis() % welcomes.length);
        appendToChat(welcomes[idx] + "\n");
    }

    public void clearChat() {
        chatHistory.setLength(0);
        questionHistory.removeAllElements();
        historyIndex = -1;
        t9Input.clear();
        showWelcome();
        scrollY = 0;
        repaint();
    }

    public void setChatHistory(String history) {
        chatHistory = new StringBuffer(history);
        scrollY = 32767;
        repaint();
    }

    public String getChatHistory() {
        return chatHistory.toString();
    }

    public void showStatus(String msg) {
        statusMessage = msg;
        statusExpiry = System.currentTimeMillis() + (midlet.isDebugMode() ? 5000 : 2000);
        repaint();
    }

    protected void paint(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        boolean isSmallScreen = (width <= 130 || height <= 130);
        
        g.setColor(0xFFFFFF); g.fillRect(0, 0, width, height);
        int inputHeight = isSmallScreen ? lineHeight + 2 : lineHeight + 6;
        int chatHeight = height - inputHeight;
        
        g.setClip(0, 0, width, chatHeight);
        g.translate(0, -scrollY);
        g.setColor(0x000000);
        
        int y = 4;
        int textLen = chatHistory.length();
        int pos = 0;
        
        while (pos < textLen && y < scrollY + chatHeight + 200) {
            if (y + lineHeight < scrollY - 100) {
                while (pos < textLen && chatHistory.charAt(pos) != '\n') pos++;
                if (pos < textLen) pos++;
                y += lineHeight + 2;
                continue;
            }
            
            int lineEnd = pos;
            while (lineEnd < textLen && chatHistory.charAt(lineEnd) != '\n') lineEnd++;
            
            String line = "";
            if (lineEnd > pos) {
                char[] buf = new char[lineEnd - pos];
                for (int i = 0; i < buf.length; i++) buf[i] = chatHistory.charAt(pos + i);
                line = new String(buf);
            }
            
            if (isSmallScreen) {
                if (startsWith(line, "Grok: ")) line = "G: " + substring(line, 6, line.length());
                else if (startsWith(line, midlet.getUserName() + ": ")) 
                    line = "U: " + substring(line, midlet.getUserName().length() + 2, line.length());
            }
            
            y = drawWrappedLine(g, line, 4, y, width - 8);
            pos = lineEnd + 1;
            y += (isSmallScreen ? 1 : 2);
        }
        
        if (scrollY == 32767) scrollY = Math.max(0, y - chatHeight);
        else {
            if (y < chatHeight) scrollY = 0;
            else scrollY = Math.min(scrollY, y - chatHeight);
        }
        
        g.translate(0, scrollY);
        g.setClip(0, 0, width, height);
        g.setColor(0xDDDDDD); g.drawLine(0, chatHeight - 1, width, chatHeight - 1);
        
        g.setColor(0xF0F0FF); g.fillRect(0, chatHeight, width, inputHeight);
        g.setColor(0x000000); g.drawRect(0, chatHeight, width - 1, inputHeight - 1);
        
        String displayText = t9Input.getText();
        if (t9Input.isUpperCase()) {
            g.setColor(0xAA0000);
            g.drawString("[CAPS]", 4, chatHeight + 2, Graphics.TOP | Graphics.LEFT);
            g.setColor(0x000000);
            g.drawString(displayText + (cursorActive && showCursor ? "_" : ""), 45, chatHeight + 2, Graphics.TOP | Graphics.LEFT);
        } else {
            if (cursorActive && showCursor && !midlet.isWaitingResponse()) displayText += "_";
            else if (midlet.isWaitingResponse()) displayText += "...";
            g.drawString("You: " + displayText, 4, chatHeight + 2, Graphics.TOP | Graphics.LEFT);
        }
        
        int signal = getSignalStrength();
        int barX = width - 8;
        for (int i = 0; i < 4; i++) {
            g.setColor(i < signal ? 0x00AA00 : 0xDDDDDD);
            g.fillRect(barX - i*3, 4, 2, 6 - i);
        }
        if (!midlet.isNetworkAvailable()) {
            g.setColor(0xAA0000);
            g.drawString("X", barX - 12, 4, Graphics.TOP | Graphics.LEFT);
        }
        
        if (statusMessage.length() > 0 && System.currentTimeMillis() < statusExpiry) {
            g.setColor(0x0000AA);
            g.drawString(statusMessage, width - 4, 16, Graphics.TOP | Graphics.RIGHT);
        }
        
        if (midlet.isDebugMode() && midlet.getDebugLog().length() > 0) {
            g.setColor(0x880088);
            StringBuffer log = midlet.getDebugLog();
            int start = Math.max(0, log.length() - 60);
            char[] debugChars = new char[log.length() - start];
            for (int i = 0; i < debugChars.length; i++) debugChars[i] = log.charAt(start + i);
            g.drawString(new String(debugChars), 2, 2, Graphics.TOP | Graphics.LEFT);
        }
    }

    private int drawWrappedLine(Graphics g, String line, int x, int y, int maxWidth) {
        if (line.length() == 0) return y + lineHeight;
        int start = 0;
        while (start < line.length()) {
            int end = start + 1;
            while (end <= line.length()) {
                String substr = substring(line, start, end);
                if (font.stringWidth(substr) > maxWidth) { end--; break; }
                end++;
            }
            end--;
            
            if (end < line.length() && end > start) {
                int lastSpace = -1;
                for (int i = end; i >= start; i--) {
                    if (line.charAt(i) == ' ') { lastSpace = i; break; }
                }
                if (lastSpace > start && lastSpace < end) end = lastSpace;
            }
            
            String fragment = substring(line, start, end).trim();
            g.drawString(fragment, x, y, Graphics.TOP | Graphics.LEFT);
            y += lineHeight;
            start = end;
            if (start < line.length() && line.charAt(start) == ' ') start++;
        }
        return y;
    }

    private boolean startsWith(String s, String prefix) {
        if (s.length() < prefix.length()) return false;
        for (int i = 0; i < prefix.length(); i++) {
            if (s.charAt(i) != prefix.charAt(i)) return false;
        }
        return true;
    }
    
    private String substring(String s, int start, int end) {
        if (start < 0) start = 0;
        if (end > s.length()) end = s.length();
        if (start >= end) return "";
        char[] buf = new char[end - start];
        for (int i = 0; i < buf.length; i++) buf[i] = s.charAt(start + i);
        return new String(buf);
    }

    private int getSignalStrength() {
        try {
            String sig = System.getProperty("com.sonyericsson.net.signalstrength");
            if (sig != null) {
                int val = 0;
                for (int i = 0; i < sig.length(); i++) {
                    if (sig.charAt(i) >= '0' && sig.charAt(i) <= '9') val = val * 10 + (sig.charAt(i) - '0');
                }
                return Math.min(4, Math.max(0, val / 8));
            }
        } catch (Exception e) {}
        return midlet.isNetworkAvailable() ? 3 : 0;
    }

    protected void keyPressed(int keyCode) {
        lastActivity = System.currentTimeMillis();
        if (cursorTimer == null) startCursorTimer();
        if (midlet.isWaitingResponse()) return;
        
        // CORRECTION CRITIQUE : Prioriser les touches numériques AVANT game actions
        int keyIndex = keyCode - Canvas.KEY_NUM0;
        if (keyIndex >= 0 && keyIndex <= 9) {
            t9Input.keyPressed(keyIndex);
            historyIndex = -1;
            repaint();
            return;
        }
        
        // Game actions (directionnelles physiques)
        int gameAction = getGameAction(keyCode);
        
        if (gameAction == Canvas.LEFT && questionHistory.size() > 0) {
            if (historyIndex < questionHistory.size() - 1) {
                historyIndex++;
                t9Input.setText((String)questionHistory.elementAt(historyIndex));
                repaint();
            }
            return;
        }
        if (gameAction == Canvas.RIGHT && questionHistory.size() > 0) {
            if (historyIndex > 0) {
                historyIndex--;
                t9Input.setText((String)questionHistory.elementAt(historyIndex));
                repaint();
            } else if (historyIndex == 0) {
                historyIndex = -1;
                t9Input.clear();
                repaint();
            }
            return;
        }
        if (gameAction == Canvas.UP) { 
            scrollY = Math.max(0, scrollY - lineHeight * 3); 
            repaint(); 
            return; 
        }
        if (gameAction == Canvas.DOWN) { 
            scrollY += lineHeight * 3; 
            repaint(); 
            return; 
        }
        if (gameAction == Canvas.FIRE) { 
            sendInput(); 
            return; 
        }
        
        // Backspace traditionnel (codes clavier émulateur)
        if (keyCode == -8 || keyCode == -21 || keyCode == -6 || keyCode == -7) {
            t9Input.backspace();
            repaint();
            return;
        }
        
        // Gestion touche # : simple press = CAPS, double press = historique
        if (keyCode == Canvas.KEY_POUND) {
            long now = System.currentTimeMillis();
            if (lastKeyWasHash && (now - lastHashPress) < DOUBLE_PRESS_TIMEOUT) {
                showHistory();
                lastKeyWasHash = false;
                return;
            } else {
                t9Input.toggleCase();
                showStatus(t9Input.isUpperCase() ? "CAPS ON" : "caps off");
                lastKeyWasHash = true;
                lastHashPress = now;
                repaint();
                return;
            }
        }
        lastKeyWasHash = false;
        
        // Touche * : symboles COMPLETS
        if (keyCode == Canvas.KEY_STAR) {
            handleStarTap();
            repaint();
            return;
        }
        
        // Saisie directe ASCII (clavier PC/émulateur)
        if (keyCode >= 32 && keyCode <= 126) {
            char c = (char) keyCode;
            t9Input.flush();
            t9Input.setText(t9Input.getText() + c);
            historyIndex = -1;
            repaint();
            return;
        }
    }

    private void handleStarTap() {
        if (lastKey != Canvas.KEY_STAR) {
            lastSymbolPos = t9Input.getText().length();
            t9Input.setText(t9Input.getText() + SYMBOLS[0]);
            starTapCount = 0;
        } else {
            String current = t9Input.getText();
            if (lastSymbolPos >= 0 && lastSymbolPos <= current.length()) {
                t9Input.setText(current.substring(0, lastSymbolPos));
                starTapCount = (starTapCount + 1) % SYMBOLS.length;
                t9Input.setText(t9Input.getText() + SYMBOLS[starTapCount]);
            }
        }
        lastKey = Canvas.KEY_STAR;
        resetStarTimer();
    }
    
    private void resetStarTimer() {
        if (starTimer != null) starTimer.cancel();
        starTimer = new Timer();
        starTimer.schedule(new TimerTask() {
            public void run() {
                lastKey = -1;
                lastSymbolPos = -1;
                starTimer = null;
            }
        }, 800);
    }

    private void showHistory() {
        String historyText = extractRecentHistory(15);
        final TextBox historyBox = new TextBox("Conversation History", historyText, 1000, TextField.UNEDITABLE);
        historyBox.addCommand(new Command("Back", Command.BACK, 1));
        historyBox.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                midlet.getDisplay().setCurrent(ChatCanvas.this);
            }
        });
        midlet.getDisplay().setCurrent(historyBox);
    }
    
    private String extractRecentHistory(int maxLines) {
        if (chatHistory.length() == 0) return "No history yet";
        StringBuffer result = new StringBuffer();
        int count = 0;
        int pos = chatHistory.length() - 1;
        
        while (pos >= 0 && count < maxLines) {
            int lineStart = pos;
            while (lineStart > 0 && chatHistory.charAt(lineStart - 1) != '\n') lineStart--;
            
            if (lineStart <= pos) {
                for (int i = lineStart; i <= pos; i++) result.insert(0, chatHistory.charAt(i));
                result.insert(0, '\n');
                count++;
            }
            pos = lineStart - 2;
            if (pos < 0) break;
        }
        
        if (result.length() > 0 && result.charAt(0) == '\n') result.deleteCharAt(0);
        return result.toString();
    }

    public void sendInput() {
        long now = System.currentTimeMillis();
        if (now - lastSendTime < 1500) { showStatus("Slow down!"); return; }
        lastSendTime = now;
        
        t9Input.flush();
        final String input = t9Input.getText().trim();
        if (input.length() == 0) return;
        
        if (input.startsWith("/image ")) {
            handleImageCommand(input.substring(7).trim());
            t9Input.clear();
            return;
        }
        
        if (questionHistory.size() == 0 || !((String)questionHistory.elementAt(0)).equals(input)) {
            questionHistory.insertElementAt(input, 0);
            if (questionHistory.size() > 10) questionHistory.setElementAt(null, 10);
        }
        historyIndex = -1;
        
        appendToChat(midlet.getUserName() + ": " + input + "\n");
        t9Input.clear();
        
        try { Display.getDisplay(midlet).vibrate(80); } catch (Exception e) {}
        
        if (!midlet.isNetworkAvailable() || indexOf(input.toLowerCase(), "help", 0) != -1 || 
            indexOf(input.toLowerCase(), "hello", 0) != -1 || indexOf(input.toLowerCase(), "hi ", 0) != -1) {
            String offlineResp = midlet.getOfflineResponse(input);
            if (offlineResp != null) {
                appendToChat("Grok: [Offline]\n" + offlineResp + "\n\n");
                return;
            }
        }
        
        midlet.setWaiting(true);
        repaint();
        
        new Thread(new Runnable() {
            public void run() { sendRequest(input); }
        }).start();
    }
    
    private int indexOf(String s, String target, int fromIndex) {
        if (fromIndex < 0) fromIndex = 0;
        int max = s.length() - target.length();
        if (max < 0) return -1;
        char first = target.charAt(0);
        for (int i = fromIndex; i <= max; i++) {
            if (s.charAt(i) == first) {
                int j = 1;
                while (j < target.length() && s.charAt(i+j) == target.charAt(j)) j++;
                if (j == target.length()) return i;
            }
        }
        return -1;
    }

    private void handleImageCommand(final String prompt) {
        if (prompt.length() == 0) {
            appendToChat(midlet.getUserName() + ": /image\n");
            appendToChat("Grok: Usage: /image <description>\nExamples: robot, heart, star, house, tree\n\n");
            return;
        }
        
        appendToChat(midlet.getUserName() + ": /image " + prompt + "\n");
        appendToChat("Grok: Generating ASCII art for '" + prompt + "'...\n\n");
        String localArt = generateSimpleAsciiArt(prompt);
        appendToChat(localArt + "\n");
        
        if (midlet.isNetworkAvailable()) {
            midlet.setWaiting(true);
            repaint();
            new Thread(new Runnable() {
                public void run() { fetchRemoteAsciiArt(prompt); }
            }).start();
        } else {
            appendToChat("Note: Real-time ASCII generation unavailable (no network).\n");
            appendToChat("J2ME devices cannot display real images due to memory limits.\n\n");
        }
    }
    
    private String generateSimpleAsciiArt(String prompt) {
        prompt = prompt.toLowerCase();
        if (prompt.indexOf("robot") != -1) return "   *****\n  * o o *\n  *  -  *\n   *****\n   |   |\n  /|   |\\\n  /     \\\n";
        else if (prompt.indexOf("heart") != -1) return "  **   **\n *  * *  *\n*    *    *\n *       *\n  *     *\n   *****\n    ***\n     *\n";
        else if (prompt.indexOf("star") != -1) return "    .\n   ...\n  ..*..\n .*****.\n  ..*..\n   ...\n    .\n";
        else if (prompt.indexOf("house") != -1) return "    /\\\n   /  \\\n  /____\\\n  |    |\n  | [] |\n  |____|\n";
        else if (prompt.indexOf("tree") != -1) return "    *\n   ***\n  *****\n *******\n    |\n    |\n";
        else if (prompt.indexOf("cat") != -1) return "  /\\_/\\\n ( o.o )\n  > ^ <\n";
        else if (prompt.indexOf("dog") != -1) return "  / \\__\n (    @\\___\n /         O\n/   (_____/\n/_____/   U\n";
        else if (prompt.indexOf("car") != -1) return "   __________\n  //  ||  ||\\\n //   ||  || \\\n//___ ||__||__\\\n   O       O\n";
        else if (prompt.indexOf("smile") != -1) return "  _______\n /       \\\n|  o   o  |\n|    -    |\n|  \\___/  |\n \\_______/\n";
        else return "   ____\n  /    \\\n |  ?   |\n  \\____/\n   |  |\n   |  |\n";
    }
    
    private void fetchRemoteAsciiArt(final String prompt) {
        HttpConnection conn = null;
        InputStream is = null;
        try {
            String urlStr = midlet.getAsciiApiUrl() + urlEncode(prompt) + "&width=20&height=10";
            conn = (HttpConnection) Connector.open(urlStr, Connector.READ, true);
            conn.setRequestMethod(HttpConnection.GET);
            conn.setRequestProperty("Connection", "close");
            conn.setRequestProperty("User-Agent", "J2ME-AIChatBot/1.0");
            
            int rc = conn.getResponseCode();
            if (rc == HttpConnection.HTTP_OK) {
                is = conn.openInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[256];
                int len, total = 0;
                long start = System.currentTimeMillis();
                
                while ((len = is.read(buffer)) != -1 && total < 2048 && 
                       (System.currentTimeMillis() - start) < 10000) {
                    baos.write(buffer, 0, len);
                    total += len;
                }
                
                final String asciiArt = new String(baos.toByteArray(), "UTF-8");
                if (asciiArt.indexOf("\n") != -1 && (asciiArt.indexOf("*") != -1 || asciiArt.indexOf("#") != -1)) {
                    midlet.getDisplay().callSerially(new Runnable() {
                        public void run() {
                            appendToChat("\n[Enhanced via API]\n" + asciiArt + "\n\n");
                            midlet.setWaiting(false);
                        }
                    });
                    return;
                }
            }
        } catch (Exception e) {} finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
        
        midlet.getDisplay().callSerially(new Runnable() {
            public void run() {
                appendToChat("Note: Real image generation unavailable on J2ME devices.\n");
                appendToChat("Memory limits (max 512KB heap) prevent image decoding.\n");
                appendToChat("ASCII art is the optimal solution for feature phones.\n\n");
                midlet.setWaiting(false);
            }
        });
    }

    public void appendToChat(String text) {
        chatHistory.append(text);
        if (chatHistory.length() > MAX_HISTORY) {
            int cutPos = -1;
            int searchStart = MAX_HISTORY / 2;
            for (int i = searchStart; i < chatHistory.length() - 1; i++) {
                if (chatHistory.charAt(i) == '\n' && chatHistory.charAt(i+1) == '\n') {
                    cutPos = i + 2; break;
                }
            }
            if (cutPos == -1) cutPos = searchStart;
            if (cutPos < chatHistory.length()) {
                StringBuffer newHist = new StringBuffer();
                for (int i = cutPos; i < chatHistory.length(); i++) newHist.append(chatHistory.charAt(i));
                chatHistory = newHist;
            }
        }
        scrollY = 32767;
        repaint();
    }

    private void sendRequest(final String input) {
        final int MAX_RETRIES = 2;
        String lastError = "";
        
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            final HttpConnection[] connHolder = new HttpConnection[1];
            InputStream is = null;
            try {
                final int timeout = 15000 + (attempt * 5000);
                String prompt = input + midlet.getInstructions();
                String urlStr = midlet.getBaseUrl() + urlEncode(prompt);
                if (midlet.getSession() != null) urlStr += "&session=" + urlEncode(midlet.getSession());
                
                connHolder[0] = (HttpConnection) Connector.open(urlStr, Connector.READ, true);
                connHolder[0].setRequestMethod(HttpConnection.GET);
                connHolder[0].setRequestProperty("Connection", "close");
                connHolder[0].setRequestProperty("User-Agent", "J2ME-AIChatBot/1.0");
                
                final boolean[] timedOut = {false};
                Thread watchdog = new Thread(new Runnable() {
                    public void run() {
                        try { Thread.sleep(timeout); } catch (Exception e) {}
                        timedOut[0] = true;
                        try { if (connHolder[0] != null) connHolder[0].close(); } catch (Exception ex) {}
                    }
                });
                watchdog.start();
                
                int rc = connHolder[0].getResponseCode();
                watchdog.interrupt();
                
                if (timedOut[0]) throw new IOException("timeout");
                if (rc != HttpConnection.HTTP_OK) throw new IOException("HTTP " + rc);
                
                is = connHolder[0].openInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[256];
                int len, total = 0;
                long start = System.currentTimeMillis();
                
                while ((len = is.read(buffer)) != -1 && total < 4096 && 
                       (System.currentTimeMillis() - start) < timeout) {
                    baos.write(buffer, 0, len);
                    total += len;
                }
                
                final String response = new String(baos.toByteArray(), "UTF-8");
                if (indexOf(response, "\"result\"", 0) == -1) throw new IOException("bad json");
                
                final String result = extractField(response, "result");
                final String session = extractField(response, "session");
                
                midlet.getDisplay().callSerially(new Runnable() {
                    public void run() {
                        midlet.setSession(session);
                        appendToChat("Grok: " + (result.equals("") ? "Try rephrasing?" : result) + "\n\n");
                        midlet.setWaiting(false);
                        midlet.logDebug("Req success, " + response.length() + " bytes");
                    }
                });
                return;
                
            } catch (Exception e) {
                lastError = e.toString().toLowerCase();
                midlet.logDebug("Req fail attempt " + attempt + ": " + lastError);
                if (attempt < MAX_RETRIES) try { Thread.sleep(2000); } catch (Exception ex) {}
            } finally {
                try { if (is != null) is.close(); } catch (Exception ignored) {}
                try { if (connHolder[0] != null) connHolder[0].close(); } catch (Exception ignored) {}
            }
        }
        
        final String userMsg = 
            indexOf(lastError, "timeout", 0) != -1 ? "Slow network. Shorter questions." :
            indexOf(lastError, "connect", 0) != -1 ? "No signal. Move near window." :
            indexOf(lastError, "bad json", 0) != -1 ? "Server error. Try again." :
            "Server busy. Wait 10s.";
        
        midlet.getDisplay().callSerially(new Runnable() {
            public void run() {
                appendToChat("Grok: [WARN] " + userMsg + "\n\n");
                midlet.setWaiting(false);
            }
        });
    }

    private String urlEncode(String s) {
        if (s == null) return "";
        StringBuffer sb = new StringBuffer();
        try {
            byte[] bytes = s.getBytes("UTF-8");
            for (int i = 0; i < bytes.length; i++) {
                int b = bytes[i] & 0xFF;
                if ((b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || 
                    (b >= '0' && b <= '9') || b == '-' || b == '_' || 
                    b == '.' || b == '~') sb.append((char) b);
                else if (b == ' ') sb.append('+');
                else {
                    sb.append('%');
                    sb.append(HEX_DIGITS[(b >> 4) & 0xF]);
                    sb.append(HEX_DIGITS[b & 0xF]);
                }
            }
        } catch (Exception e) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == ' ') sb.append('+');
                else if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) sb.append(c);
                else {
                    sb.append('%');
                    sb.append(HEX_DIGITS[(c >> 4) & 0xF]);
                    sb.append(HEX_DIGITS[c & 0xF]);
                }
            }
        }
        return sb.toString();
    }

    private String extractField(String json, String field) {
        if (json == null) return "";
        String tag = "\"" + field + "\":\"";
        int start = -1;
        int tagLen = tag.length();
        for (int i = 0; i < json.length() - tagLen; i++) {
            boolean match = true;
            for (int j = 0; j < tagLen; j++) {
                if (json.charAt(i + j) != tag.charAt(j)) { match = false; break; }
            }
            if (match) { start = i; break; }
        }
        if (start == -1) return "";
        start += tagLen;
        
        int end = -1;
        for (int i = start; i < json.length(); i++) {
            if (json.charAt(i) == '"') {
                if (i > start && json.charAt(i-1) != '\\') { end = i; break; }
            }
        }
        if (end == -1) return "";
        
        StringBuffer result = new StringBuffer();
        for (int i = start; i < end; i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < end) {
                i++;
                char next = json.charAt(i);
                if (next == 'n') result.append('\n');
                else if (next == 'r') result.append('\r');
                else if (next == 't') result.append('\t');
                else result.append(next);
            } else result.append(c);
        }
        return result.toString();
    }

    public void stopTimers() {
        if (cursorTimer != null) { cursorTimer.cancel(); cursorTimer = null; }
        if (starTimer != null) { starTimer.cancel(); starTimer = null; }
        t9Input.flush();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == sendCommand && !midlet.isWaitingResponse()) {
            sendInput();
        } else if (c == backspaceCommand) {
            // RSK = Backspace
            t9Input.backspace();
            repaint();
        } else if (c == exitCommand) {
            midlet.destroyApp(true);
        }
    }
    
    protected void hideNotify() {
        if (cursorTimer != null) cursorTimer.cancel();
    }
    
    protected void showNotify() {
        startCursorTimer();
    }
}