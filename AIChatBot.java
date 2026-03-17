import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import java.util.Vector;

public class AIChatBot extends MIDlet {
    private Display display;
    private ChatCanvas chatCanvas;
    private MenuScreen menuScreen;
    private SettingsScreen settingsScreen;
    private HistoryScreen historyScreen;
    private AboutScreen aboutScreen;
    
    private String userId;
    private History history;
    private Settings settings;
    private SaveManager saveManager;
    
    public static final String VERSION = "v1.1";
    public static final String APP_NAME = "AI ChatBot";
    
    public AIChatBot() {
        display = Display.getDisplay(this);
        userId = Utils.generateUserId();
        history = new History();
        settings = new Settings();
        saveManager = new SaveManager();
    }
    
    protected void startApp() throws MIDletStateChangeException {
        showMenu();
    }
    
    public void showMenu() {
        menuScreen = new MenuScreen(this);
        display.setCurrent(menuScreen);
    }
    
    public void showChat() {
        chatCanvas = new ChatCanvas(this);
        display.setCurrent(chatCanvas);
    }
    
    public void showSettings() {
        settingsScreen = new SettingsScreen(this);
        display.setCurrent(settingsScreen);
    }
    
    public void showHistory() {
        historyScreen = new HistoryScreen(this);
        display.setCurrent(historyScreen);
    }
    
    public void showAbout() {
        aboutScreen = new AboutScreen(this);
        display.setCurrent(aboutScreen);
    }
    
    public void exitApp() {
        history.saveCurrentSession();
        destroyApp(true);
        notifyDestroyed();
    }
    
    public String getUserId() { return userId; }
    public History getHistory() { return history; }
    public Settings getSettings() { return settings; }
    public SaveManager getSaveManager() { return saveManager; }
    public Display getDisplay() { return display; }
    
    protected void pauseApp() {}
    protected void destroyApp(boolean unconditional) {}
}

/**
 * T9Input - Multi-tap text input for J2ME
 */
class T9Input {
    private StringBuffer buffer = new StringBuffer(256);
    private int lastKey = -1;
    private int tapCount = 0;
    private char draft = 0;
    
    private boolean upperCase = false;
    private boolean numericMode = false;
    
    private static final int MAX_HISTORY = 15;
    private Vector history = new Vector();
    private int historyIndex = -1;
    
    private static final String[] MAP_LOWER = {
        " 0",
        ".,!?&'()-_:;/@#=+*%$1",
        "abc2",
        "def3",
        "ghi4",
        "jkl5",
        "mno6",
        "pqrs7",
        "tuv8",
        "wxyz9"
    };
    
    public T9Input() {}
    
    public void keyPressed(int key) {
        if (key < 0 || key > 9) return;
        
        if (numericMode) {
            if (draft != 0) commitDraft();
            buffer.append((char)('0' + key));
            return;
        }
        
        if (key != lastKey && draft != 0) {
            commitDraft();
        }
        
        if (key == lastKey) {
            tapCount++;
        } else {
            lastKey = key;
            tapCount = 0;
        }
        
        String options = MAP_LOWER[key];
        char c = options.charAt(tapCount % options.length());
        if (upperCase) c = Character.toUpperCase(c);
        draft = c;
    }
    
    public void flush() { commitDraft(); }
    
    private void commitDraft() {
        if (draft != 0) {
            buffer.append(draft);
            draft = 0;
            lastKey = -1;
            tapCount = 0;
        }
    }
    
    public void backspace() {
        if (draft != 0) {
            draft = 0;
            lastKey = -1;
            tapCount = 0;
        } else if (buffer.length() > 0) {
            buffer.deleteCharAt(buffer.length() - 1);
        }
    }
    
    public String getLiveText() {
        if (draft != 0) return buffer.toString() + draft;
        return buffer.toString();
    }
    
    public String getText() { return buffer.toString(); }
    
    public void clear() {
        buffer.setLength(0);
        draft = 0;
        lastKey = -1;
        tapCount = 0;
    }
    
    public void setText(String text) {
        commitDraft();
        buffer.setLength(0);
        if (text != null) buffer.append(text);
        lastKey = -1;
        tapCount = 0;
    }
    
    public void toggleCase() {
        commitDraft();
        upperCase = !upperCase;
    }
    
    public boolean isUpperCase() { return upperCase; }
    
    public void toggleNumericMode() {
        commitDraft();
        numericMode = !numericMode;
    }
    
    public boolean isNumericMode() { return numericMode; }
    
    public String getModeLabel() {
        if (numericMode) return "[123]";
        if (upperCase) return "[ABC]";
        return "[abc]";
    }
    
    public String getCurrentOptions() {
        if (lastKey < 0 || lastKey > 9) return "";
        if (numericMode) return String.valueOf(lastKey);
        return MAP_LOWER[lastKey];
    }
    
    public int getCurrentOptionIndex() {
        if (lastKey < 0 || lastKey > 9) return 0;
        String options = MAP_LOWER[lastKey];
        return tapCount % options.length();
    }
    
    public boolean hasDraft() { return draft != 0; }
    public char getDraft() { return draft; }
    
    public void addToHistory(String text) {
        if (text == null || text.trim().length() == 0) return;
        if (history.size() > 0 && history.elementAt(history.size() - 1).equals(text)) return;
        history.addElement(text);
        if (history.size() > MAX_HISTORY) history.removeElementAt(0);
        historyIndex = history.size();
    }
    
    public String historyUp() {
        if (history.size() == 0) return null;
        historyIndex--;
        if (historyIndex < 0) historyIndex = 0;
        return (String) history.elementAt(historyIndex);
    }
    
    public String historyDown() {
        if (history.size() == 0) return null;
        historyIndex++;
        if (historyIndex >= history.size()) {
            historyIndex = history.size();
            return "";
        }
        return (String) history.elementAt(historyIndex);
    }
    
    public void resetHistoryIndex() { historyIndex = history.size(); }
    public Vector getHistory() { return history; }
}

// Menu Screen
class MenuScreen extends Canvas {
    private AIChatBot midlet;
    private int selected = 0;
    private String[] items = {"Chat", "Historique", "Parametres", "A propos", "Quitter"};
    
    public MenuScreen(AIChatBot midlet) {
        this.midlet = midlet;
        setFullScreenMode(true);
    }
    
    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        boolean small = (w < 180);
        
        Font font = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        int fontH = font.getHeight();
        
        g.setColor(Utils.COLOR_BG);
        g.fillRect(0, 0, w, h);
        
        int headerH = small ? 16 : 22;
        g.setColor(Utils.COLOR_ACCENT);
        g.fillRect(0, 0, w, headerH);
        g.setColor(Utils.COLOR_BG);
        g.setFont(font);
        g.drawString(AIChatBot.APP_NAME + " " + AIChatBot.VERSION, w/2, 2, 
                     Graphics.TOP | Graphics.HCENTER);
        
        int startY = headerH + 10;
        int itemH = fontH + 6;
        
        for (int i = 0; i < items.length; i++) {
            int y = startY + (i * itemH);
            if (i == selected) {
                g.setColor(Utils.COLOR_HI);
                g.fillRect(4, y - 2, w - 8, itemH);
                g.setColor(Utils.COLOR_ACCENT);
                g.drawString("> " + items[i], 8, y, Graphics.TOP | Graphics.LEFT);
            } else {
                g.setColor(Utils.COLOR_TEXT);
                g.drawString("  " + items[i], 8, y, Graphics.TOP | Graphics.LEFT);
            }
        }
        
        g.setColor(Utils.COLOR_DIM);
        g.drawString("ID: " + midlet.getUserId(), 4, h - 28, Graphics.TOP | Graphics.LEFT);
        
        int softH = small ? 12 : 14;
        g.setColor(Utils.COLOR_ACCENT);
        g.fillRect(0, h - softH, w, softH);
        g.setColor(Utils.COLOR_BG);
        g.drawString("OK", w/2, h - softH + 1, Graphics.TOP | Graphics.HCENTER);
    }
    
    protected void keyPressed(int keyCode) {
        int action = getGameAction(keyCode);
        
        if (action == UP) {
            selected = (selected - 1 + items.length) % items.length;
        } else if (action == DOWN) {
            selected = (selected + 1) % items.length;
        } else if (action == FIRE) {
            switch (selected) {
                case 0: midlet.showChat(); break;
                case 1: midlet.showHistory(); break;
                case 2: midlet.showSettings(); break;
                case 3: midlet.showAbout(); break;
                case 4: midlet.exitApp(); break;
            }
        }
        repaint();
    }
}

// Chat Canvas with Enhanced Save Dialog
class ChatCanvas extends Canvas implements Runnable {
    private AIChatBot midlet;
    private Vector messages;
    private T9Input t9;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private boolean cursorVisible = true;
    private int frameCount = 0;
    private boolean isLoading = false;
    
    // Menu
    private boolean showMenu = false;
    private int menuSelected = 0;
    private String[] menuItems = {"Envoyer", "Recherche", "Effacer", "Sauver", "Nouveau", "Retour"};
    
    // Input modes
    private static final int MODE_T9 = 0;
    private static final int MODE_QWERTY = 1;
    private int inputMode = MODE_T9;
    
    // QWERTY keyboard
    private int qwertyRow = 1;
    private int qwertyCol = 0;
    private boolean qwertyShift = false;
    private static final String[] QWERTY_ROWS = {
        "1234567890",
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm",
        " .<"
    };
    
    // Screen metrics
    private int scrW, scrH;
    private boolean isSmall;
    private Font font;
    private int fontH;
    private int headerH, inputH, softH;
    
    // Auto-detect timing
    private long lastNavTime = 0;
    
    public ChatCanvas(AIChatBot midlet) {
        this.midlet = midlet;
        this.messages = new Vector();
        this.t9 = new T9Input();
        setFullScreenMode(true);
        
        addMessage(false, "Bienvenue! Tapez avec T9.");
        addMessage(false, "#=MAJ *=Mode LSK=Menu");
        
        new Thread(this).start();
    }
    
    private void calcMetrics() {
        scrW = getWidth();
        scrH = getHeight();
        isSmall = (scrW < 180 || scrH < 220);
        font = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        fontH = font.getHeight();
        headerH = isSmall ? 14 : 20;
        inputH = isSmall ? 26 : 34;
        softH = isSmall ? 12 : 14;
    }
    
    public void run() {
        while (true) {
            frameCount++;
            if (frameCount % 8 == 0) {
                cursorVisible = !cursorVisible;
                repaint();
            }
            try { Thread.sleep(60); } catch (Exception e) {}
        }
    }
    
    protected void paint(Graphics g) {
        calcMetrics();
        
        g.setColor(Utils.COLOR_BG);
        g.fillRect(0, 0, scrW, scrH);
        g.setFont(font);
        
        drawHeader(g);
        
        int msgY = headerH;
        int msgH;
        
        if (inputMode == MODE_QWERTY) {
            int kbH = isSmall ? 60 : 80;
            msgH = scrH - headerH - inputH - kbH - softH;
            drawMessages(g, msgY, msgH);
            drawInputBox(g, msgY + msgH);
            drawQwerty(g, msgY + msgH + inputH, kbH);
        } else {
            msgH = scrH - headerH - inputH - softH;
            drawMessages(g, msgY, msgH);
            drawT9Input(g, scrH - inputH - softH);
        }
        
        drawSoftkeys(g);
        
        if (showMenu) drawMenu(g);
    }
    
    private void drawHeader(Graphics g) {
        g.setColor(Utils.COLOR_ACCENT);
        g.fillRect(0, 0, scrW, headerH);
        g.setColor(Utils.COLOR_BG);
        
        String title = isSmall ? "Chat" : "[IA] Chat";
        g.drawString(title, 3, 1, Graphics.TOP | Graphics.LEFT);
        
        String mode = (inputMode == MODE_QWERTY) ? "QW" : t9.getModeLabel();
        g.drawString(mode, scrW - 3, 1, Graphics.TOP | Graphics.RIGHT);
        
        if (isLoading) {
            g.setColor(Utils.COLOR_TEXT);
            int cx = scrW / 2;
            int cy = headerH / 2;
            int r = 4;
            for (int i = 0; i < 4; i++) {
                double a = ((frameCount * 30) + (i * 90)) * Math.PI / 180;
                int px = cx + (int)(Math.cos(a) * r);
                int py = cy + (int)(Math.sin(a) * r);
                g.fillRect(px - 1, py - 1, 2, 2);
            }
        }
    }
    
    private void drawMessages(Graphics g, int startY, int areaH) {
        g.setClip(0, startY, scrW, areaH);
        
        int pad = isSmall ? 2 : 4;
        int maxW = scrW - (pad * 2) - 4;
        int y = startY + pad - scrollOffset;
        
        for (int i = 0; i < messages.size(); i++) {
            String[] msg = (String[]) messages.elementAt(i);
            boolean isAI = msg[0].equals("AI");
            String text = msg[1];
            
            Vector lines = wrapText(text, maxW - 8);
            int bubbleH = (lines.size() * fontH) + 4;
            int bubbleW = getMaxWidth(lines, g) + 10;
            if (bubbleW > maxW) bubbleW = maxW;
            
            if (y + bubbleH >= startY - 20 && y < startY + areaH + 20) {
                int bx = isAI ? pad : (scrW - bubbleW - pad);
                
                g.setColor(isAI ? Utils.COLOR_HI : Utils.COLOR_USER_BG);
                g.fillRoundRect(bx, y, bubbleW, bubbleH, 4, 4);
                g.setColor(Utils.COLOR_BORDER);
                g.drawRoundRect(bx, y, bubbleW, bubbleH, 4, 4);
                
                g.setColor(isAI ? Utils.COLOR_TEXT : Utils.COLOR_USER);
                for (int j = 0; j < lines.size(); j++) {
                    g.drawString((String)lines.elementAt(j), bx + 4, y + 2 + (j * fontH),
                                 Graphics.TOP | Graphics.LEFT);
                }
            }
            y += bubbleH + pad;
        }
        
        maxScroll = Math.max(0, y + scrollOffset - startY - areaH);
        g.setClip(0, 0, scrW, scrH);
    }
    
    private Vector wrapText(String text, int maxW) {
        Vector lines = new Vector();
        if (text == null || text.length() == 0) {
            lines.addElement("");
            return lines;
        }
        
        int start = 0;
        while (start < text.length()) {
            int end = start;
            int lastSp = -1;
            
            while (end < text.length()) {
                char c = text.charAt(end);
                if (c == '\n') {
                    lines.addElement(text.substring(start, end));
                    start = end + 1;
                    end = start;
                    lastSp = -1;
                    continue;
                }
                if (c == ' ') lastSp = end;
                
                if (font.substringWidth(text, start, end - start + 1) > maxW) {
                    if (lastSp > start) {
                        lines.addElement(text.substring(start, lastSp));
                        start = lastSp + 1;
                    } else {
                        lines.addElement(text.substring(start, end));
                        start = end;
                    }
                    end = start;
                    lastSp = -1;
                    continue;
                }
                end++;
            }
            
            if (start < text.length()) {
                lines.addElement(text.substring(start));
                break;
            }
        }
        
        if (lines.size() == 0) lines.addElement("");
        return lines;
    }
    
    private int getMaxWidth(Vector lines, Graphics g) {
        int max = 0;
        for (int i = 0; i < lines.size(); i++) {
            int w = font.stringWidth((String)lines.elementAt(i));
            if (w > max) max = w;
        }
        return max;
    }
    
    private void drawT9Input(Graphics g, int y) {
        g.setColor(Utils.COLOR_INPUT_BG);
        g.fillRect(0, y, scrW, inputH);
        g.setColor(Utils.COLOR_BORDER);
        g.drawLine(0, y, scrW, y);
        
        String opts = t9.getCurrentOptions();
        if (opts.length() > 0) {
            g.setColor(Utils.COLOR_DIM);
            int optIdx = t9.getCurrentOptionIndex();
            String preview = "";
            for (int i = 0; i < opts.length(); i++) {
                if (i == optIdx) {
                    preview += "[" + opts.charAt(i) + "]";
                } else {
                    preview += " " + opts.charAt(i) + " ";
                }
            }
            g.drawString(preview, 3, y + 1, Graphics.TOP | Graphics.LEFT);
        }
        
        int boxY = y + (isSmall ? 9 : 12);
        int boxH = fontH + 4;
        g.setColor(Utils.COLOR_BG);
        g.fillRect(2, boxY, scrW - 4, boxH);
        g.setColor(Utils.COLOR_BORDER);
        g.drawRect(2, boxY, scrW - 4, boxH);
        
        String txt = t9.getLiveText();
        int maxC = (scrW - 16) / font.charWidth('m');
        if (txt.length() > maxC) txt = txt.substring(txt.length() - maxC);
        
        g.setColor(Utils.COLOR_TEXT);
        g.drawString(txt, 5, boxY + 2, Graphics.TOP | Graphics.LEFT);
        
        if (cursorVisible) {
            int cx = 5 + font.stringWidth(txt);
            g.setColor(Utils.COLOR_ACCENT);
            g.fillRect(cx, boxY + 2, 2, fontH);
        }
        
        if (t9.hasDraft()) {
            g.setColor(Utils.COLOR_ACCENT);
            int dx = 5 + font.stringWidth(txt) - font.charWidth(t9.getDraft());
            g.drawLine(dx, boxY + boxH - 2, dx + font.charWidth(t9.getDraft()), boxY + boxH - 2);
        }
    }
    
    private void drawInputBox(Graphics g, int y) {
        g.setColor(Utils.COLOR_INPUT_BG);
        g.fillRect(0, y, scrW, inputH);
        g.setColor(Utils.COLOR_BORDER);
        g.drawLine(0, y, scrW, y);
        
        int boxY = y + 2;
        int boxH = inputH - 4;
        g.setColor(Utils.COLOR_BG);
        g.fillRect(2, boxY, scrW - 4, boxH);
        g.setColor(Utils.COLOR_BORDER);
        g.drawRect(2, boxY, scrW - 4, boxH);
        
        String txt = t9.getLiveText();
        int maxC = (scrW - 16) / font.charWidth('m');
        if (txt.length() > maxC) txt = txt.substring(txt.length() - maxC);
        
        g.setColor(Utils.COLOR_TEXT);
        g.drawString(txt, 5, boxY + 2, Graphics.TOP | Graphics.LEFT);
        
        if (cursorVisible) {
            int cx = 5 + font.stringWidth(txt);
            g.setColor(Utils.COLOR_ACCENT);
            g.fillRect(cx, boxY + 2, 2, fontH);
        }
    }
    
    private void drawQwerty(Graphics g, int startY, int kbH) {
        int rowH = kbH / QWERTY_ROWS.length;
        
        g.setColor(Utils.COLOR_INPUT_BG);
        g.fillRect(0, startY, scrW, kbH);
        
        for (int r = 0; r < QWERTY_ROWS.length; r++) {
            String row = QWERTY_ROWS[r];
            if (qwertyShift && r >= 1 && r <= 3) row = row.toUpperCase();
            
            int rowY = startY + (r * rowH);
            int keyW = scrW / row.length();
            
            for (int c = 0; c < row.length(); c++) {
                int keyX = c * keyW;
                boolean sel = (r == qwertyRow && c == qwertyCol);
                
                if (sel) {
                    g.setColor(Utils.COLOR_ACCENT);
                    g.fillRect(keyX + 1, rowY + 1, keyW - 2, rowH - 2);
                    g.setColor(Utils.COLOR_BG);
                } else {
                    g.setColor(Utils.COLOR_HI);
                    g.fillRect(keyX + 1, rowY + 1, keyW - 2, rowH - 2);
                    g.setColor(Utils.COLOR_TEXT);
                }
                
                char ch = row.charAt(c);
                String lbl = (r == 4 && c == 0) ? "SP" : 
                             (r == 4 && c == 2) ? "<" : String.valueOf(ch);
                g.drawString(lbl, keyX + keyW/2, rowY + 1, Graphics.TOP | Graphics.HCENTER);
            }
        }
    }
    
    private void drawSoftkeys(Graphics g) {
        int y = scrH - softH;
        g.setColor(Utils.COLOR_ACCENT);
        g.fillRect(0, y, scrW, softH);
        g.setColor(Utils.COLOR_BG);
        
        g.drawString("Menu", 2, y + 1, Graphics.TOP | Graphics.LEFT);
        String mid = (inputMode == MODE_QWERTY) ? "T9" : "OK";
        g.drawString(mid, scrW/2, y + 1, Graphics.TOP | Graphics.HCENTER);
        g.drawString("Del", scrW - 2, y + 1, Graphics.TOP | Graphics.RIGHT);
    }
    
    private void drawMenu(Graphics g) {
        int menuW = scrW - 16;
        int menuH = (menuItems.length * (fontH + 3)) + 18;
        int menuX = 8;
        int menuY = (scrH - menuH) / 2;
        
        g.setColor(0x000000);
        g.fillRect(menuX + 2, menuY + 2, menuW, menuH);
        
        g.setColor(Utils.COLOR_MENU_BG);
        g.fillRect(menuX, menuY, menuW, menuH);
        g.setColor(Utils.COLOR_ACCENT);
        g.drawRect(menuX, menuY, menuW, menuH);
        
        g.fillRect(menuX, menuY, menuW, fontH + 4);
        g.setColor(Utils.COLOR_BG);
        g.drawString("MENU", menuX + menuW/2, menuY + 2, Graphics.TOP | Graphics.HCENTER);
        
        int itemY = menuY + fontH + 6;
        for (int i = 0; i < menuItems.length; i++) {
            if (i == menuSelected) {
                g.setColor(Utils.COLOR_HI);
                g.fillRect(menuX + 2, itemY - 1, menuW - 4, fontH + 2);
                g.setColor(Utils.COLOR_ACCENT);
            } else {
                g.setColor(Utils.COLOR_TEXT);
            }
            g.drawString(menuItems[i], menuX + 6, itemY, Graphics.TOP | Graphics.LEFT);
            itemY += fontH + 3;
        }
    }
    
    protected void keyPressed(int keyCode) {
        if (showMenu) {
            handleMenuKey(keyCode);
        } else if (inputMode == MODE_QWERTY) {
            handleQwertyKey(keyCode);
        } else {
            handleT9Key(keyCode);
        }
        repaint();
    }
    
    private void handleMenuKey(int keyCode) {
        int action = getGameAction(keyCode);
        
        if (action == UP) {
            menuSelected = (menuSelected - 1 + menuItems.length) % menuItems.length;
        } else if (action == DOWN) {
            menuSelected = (menuSelected + 1) % menuItems.length;
        } else if (action == FIRE || keyCode == -6 || keyCode == -21) {
            execMenu();
        } else if (keyCode == -7 || keyCode == -22) {
            showMenu = false;
        }
    }
    
    private void execMenu() {
        showMenu = false;
        switch (menuSelected) {
            case 0: sendMessage(); break;
            case 1: webSearch(); break;
            case 2: clearChat(); break;
            case 3: showSaveDialog(); break;  // ← Updated
            case 4: newChat(); break;
            case 5: midlet.showMenu(); break;
        }
    }
    
    private void handleT9Key(int keyCode) {
        if (keyCode == Canvas.KEY_NUM0) { t9.keyPressed(0); return; }
        if (keyCode == Canvas.KEY_NUM1) { t9.keyPressed(1); return; }
        if (keyCode == Canvas.KEY_NUM2) { t9.keyPressed(2); return; }
        if (keyCode == Canvas.KEY_NUM3) { t9.keyPressed(3); return; }
        if (keyCode == Canvas.KEY_NUM4) { t9.keyPressed(4); return; }
        if (keyCode == Canvas.KEY_NUM5) { t9.keyPressed(5); return; }
        if (keyCode == Canvas.KEY_NUM6) { t9.keyPressed(6); return; }
        if (keyCode == Canvas.KEY_NUM7) { t9.keyPressed(7); return; }
        if (keyCode == Canvas.KEY_NUM8) { t9.keyPressed(8); return; }
        if (keyCode == Canvas.KEY_NUM9) { t9.keyPressed(9); return; }
        
        if (keyCode == Canvas.KEY_POUND) {
            t9.toggleCase();
            return;
        }
        
        if (keyCode == Canvas.KEY_STAR) {
            long now = System.currentTimeMillis();
            if (now - lastNavTime < 400) {
                inputMode = MODE_QWERTY;
                qwertyRow = 1;
                qwertyCol = 0;
            } else {
                t9.toggleNumericMode();
            }
            lastNavTime = now;
            return;
        }
        
        int action = getGameAction(keyCode);
        
        if (action == UP) {
            t9.flush();
            scrollOffset = Math.max(0, scrollOffset - 30);
            return;
        }
        if (action == DOWN) {
            t9.flush();
            scrollOffset = Math.min(maxScroll, scrollOffset + 30);
            return;
        }
        
        if (action == FIRE) {
            t9.flush();
            sendMessage();
            return;
        }
        
        if (action == LEFT) {
            String h = t9.historyUp();
            if (h != null) t9.setText(h);
            return;
        }
        
        if (action == RIGHT) {
            String h = t9.historyDown();
            if (h != null) t9.setText(h);
            return;
        }
        
        if (keyCode == -6 || keyCode == -21) {
            t9.flush();
            showMenu = true;
            menuSelected = 0;
            return;
        }
        
        if (keyCode == -7 || keyCode == -22) {
            t9.backspace();
            return;
        }
    }
    
    private void handleQwertyKey(int keyCode) {
        if (keyCode == Canvas.KEY_STAR) {
            inputMode = MODE_T9;
            return;
        }
        
        if (keyCode == Canvas.KEY_POUND) {
            qwertyShift = !qwertyShift;
            return;
        }
        
        if (keyCode == -6 || keyCode == -21) {
            showMenu = true;
            menuSelected = 0;
            return;
        }
        
        if (keyCode == -7 || keyCode == -22) {
            t9.backspace();
            return;
        }
        
        if (keyCode >= Canvas.KEY_NUM0 && keyCode <= Canvas.KEY_NUM9) {
            inputMode = MODE_T9;
            t9.keyPressed(keyCode - Canvas.KEY_NUM0);
            return;
        }
        
        int action = getGameAction(keyCode);
        
        if (action == UP) {
            qwertyRow = (qwertyRow - 1 + QWERTY_ROWS.length) % QWERTY_ROWS.length;
            fixQwertyCol();
            return;
        }
        if (action == DOWN) {
            qwertyRow = (qwertyRow + 1) % QWERTY_ROWS.length;
            fixQwertyCol();
            return;
        }
        if (action == LEFT) {
            qwertyCol = (qwertyCol - 1 + QWERTY_ROWS[qwertyRow].length()) % QWERTY_ROWS[qwertyRow].length();
            return;
        }
        if (action == RIGHT) {
            qwertyCol = (qwertyCol + 1) % QWERTY_ROWS[qwertyRow].length();
            return;
        }
        
        if (action == FIRE) {
            selectQwertyChar();
            return;
        }
    }
    
    private void fixQwertyCol() {
        if (qwertyCol >= QWERTY_ROWS[qwertyRow].length()) {
            qwertyCol = QWERTY_ROWS[qwertyRow].length() - 1;
        }
    }
    
    private void selectQwertyChar() {
        String row = QWERTY_ROWS[qwertyRow];
        if (qwertyShift && qwertyRow >= 1 && qwertyRow <= 3) {
            row = row.toUpperCase();
        }
        
        if (qwertyRow == 4) {
            if (qwertyCol == 0) {
                t9.setText(t9.getLiveText() + " ");
            } else if (qwertyCol == 1) {
                t9.setText(t9.getLiveText() + ".");
            } else if (qwertyCol == 2) {
                t9.backspace();
            }
        } else {
            char c = row.charAt(qwertyCol);
            t9.setText(t9.getLiveText() + c);
        }
    }
    
    /**
     * ENHANCED SAVE DIALOG with Format + Location selection
     */
    private void showSaveDialog() {
        final List saveList = new List("Sauvegarder", Choice.EXCLUSIVE);
        
        // Format options
        saveList.append("--- Format ---", null);
        saveList.append("Format TXT", null);
        saveList.append("Format PNG", null);
        saveList.append("Format RMS/DAT", null);
        
        // Separator
        saveList.append("--- Emplacement ---", null);
        
        // Available locations
        String[] locations = midlet.getSaveManager().getAvailableLocations();
        for (int i = 0; i < locations.length; i++) {
            saveList.append(locations[i], null);
        }
        
        saveList.addCommand(new Command("OK", Command.OK, 1));
        saveList.addCommand(new Command("Annuler", Command.BACK, 1));
        
        saveList.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (c.getCommandType() == Command.OK) {
                    int selected = saveList.getSelectedIndex();
                    
                    // Determine format
                    String format = "txt";
                    if (selected == 2) format = "png";
                    else if (selected == 3) format = "rms";
                    
                    // Determine location (if selected)
                    String[] locs = midlet.getSaveManager().getAvailableLocations();
                    if (selected > 4 && selected < 5 + locs.length) {
                        int locIdx = selected - 5;
                        if (!locs[locIdx].equals("RMS (Internal)")) {
                            midlet.getSaveManager().setSavePath(locs[locIdx]);
                        }
                    }
                    
                    // Save
                    String content = midlet.getHistory().getAllConversationsAsString();
                    boolean success = midlet.getSaveManager().saveConversation(
                        content, format, midlet.getUserId());
                    
                    // Feedback
                    String msg = success ? 
                        "Sauvegarde OK!\n" + midlet.getSaveManager().getSavePath() :
                        "Erreur de sauvegarde!";
                    
                    addMessage(false, msg);
                    
                    midlet.getDisplay().setCurrent(ChatCanvas.this);
                } else {
                    midlet.getDisplay().setCurrent(ChatCanvas.this);
                }
            }
        });
        
        midlet.getDisplay().setCurrent(saveList);
    }
    
    private void addMessage(boolean isUser, String text) {
        String[] msg = new String[2];
        msg[0] = isUser ? "USER" : "AI";
        msg[1] = text;
        messages.addElement(msg);
        scrollOffset = Integer.MAX_VALUE;
    }
    
    private void sendMessage() {
        final String userMsg = t9.getLiveText().trim();
        if (userMsg.length() == 0) return;
        
        t9.addToHistory(userMsg);
        t9.clear();
        addMessage(true, userMsg);
        midlet.getHistory().addUserMessage(userMsg);
        
        isLoading = true;
        repaint();
        
        new Thread(new Runnable() {
            public void run() {
                String ctx = "";
                if (midlet.getSettings().isContextEnabled()) {
                    ctx = midlet.getHistory().getContext(midlet.getSettings().getMaxContext());
                }
                
                String resp = Utils.sendAIRequest(userMsg, ctx, midlet.getSettings());
                addMessage(false, resp);
                midlet.getHistory().addAIMessage(resp);
                isLoading = false;
                repaint();
            }
        }).start();
    }
    
    private void webSearch() {
        final String q = t9.getLiveText().trim();
        if (q.length() == 0) return;
        
        t9.clear();
        addMessage(true, "[Web] " + q);
        isLoading = true;
        repaint();
        
        new Thread(new Runnable() {
            public void run() {
                String resp = Utils.sendAIRequest("Recherche: " + q, "", midlet.getSettings());
                addMessage(false, resp);
                midlet.getHistory().addUserMessage("[WEB] " + q);
                midlet.getHistory().addAIMessage(resp);
                isLoading = false;
                repaint();
            }
        }).start();
    }
    
    private void clearChat() {
        messages = new Vector();
        scrollOffset = 0;
        addMessage(false, "Chat efface.");
        midlet.getHistory().clearCurrent();
    }
    
    private void newChat() {
        midlet.getHistory().saveCurrentSession();
        messages = new Vector();
        scrollOffset = 0;
        addMessage(false, "Nouveau chat.");
    }
}

// Settings Screen with Storage Info
class SettingsScreen extends Canvas {
    private AIChatBot midlet;
    private int selected = 0;
    private int timeout;
    private boolean contextEnabled;
    private int maxContext;
    private int scrollOffset = 0;
    
    public SettingsScreen(AIChatBot midlet) {
        this.midlet = midlet;
        setFullScreenMode(true);
        timeout = midlet.getSettings().getTimeout() / 1000;
        contextEnabled = midlet.getSettings().isContextEnabled();
        maxContext = midlet.getSettings().getMaxContext();
    }
    
    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        boolean small = w < 180;
        Font font = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        int fontH = font.getHeight();
        g.setFont(font);
        
        g.setColor(Utils.COLOR_BG);
        g.fillRect(0, 0, w, h);
        
        int headerH = small ? 14 : 20;
        g.setColor(Utils.COLOR_ACCENT);
        g.fillRect(0, 0, w, headerH);
        g.setColor(Utils.COLOR_BG);
        g.drawString("Parametres", w/2, 2, Graphics.TOP | Graphics.HCENTER);
        
        int softH = small ? 12 : 14;
        int contentY = headerH + 4;
        int contentH = h - headerH - softH;
        
        g.setClip(0, contentY, w, contentH);
        
        int y = contentY - scrollOffset;
        int itemH = fontH + 6;
        
        drawItem(g, w, y, 0, "Timeout:", timeout + "s"); y += itemH;
        drawItem(g, w, y, 1, "Contexte:", contextEnabled ? "OUI" : "NON"); y += itemH;
        drawItem(g, w, y, 2, "Max ctx:", "" + maxContext); y += itemH + 6;
        
        if (selected == 3) {
            g.setColor(Utils.COLOR_ACCENT);
            g.fillRoundRect(w/4, y, w/2, fontH + 4, 4, 4);
            g.setColor(Utils.COLOR_BG);
        } else {
            g.setColor(Utils.COLOR_BORDER);
            g.drawRoundRect(w/4, y, w/2, fontH + 4, 4, 4);
            g.setColor(Utils.COLOR_TEXT);
        }
        g.drawString("SAVE", w/2, y + 2, Graphics.TOP | Graphics.HCENTER);
        y += fontH + 10;
        
        // STORAGE INFO
        g.setColor(Utils.COLOR_BORDER);
        g.drawLine(8, y, w - 8, y);
        y += 6;
        
        g.setColor(Utils.COLOR_ACCENT);
        g.drawString("=== STOCKAGE ===", w/2, y, Graphics.TOP | Graphics.HCENTER);
        y += fontH + 4;
        
        g.setColor(Utils.COLOR_TEXT);
        String storageInfo = midlet.getSaveManager().getStorageInfo();
        String[] lines = splitLines(storageInfo);
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], 10, y, Graphics.TOP | Graphics.LEFT);
            y += fontH + 2;
        }
        
        g.setClip(0, 0, w, h);
        
        g.setColor(Utils.COLOR_ACCENT);
        g.fillRect(0, h - softH, w, softH);
        g.setColor(Utils.COLOR_BG);
        g.drawString("Back", 2, h - softH + 1, Graphics.TOP | Graphics.LEFT);
        g.drawString("Scroll: 2/8", w - 2, h - softH + 1, Graphics.TOP | Graphics.RIGHT);
    }
    
    private String[] splitLines(String text) {
        Vector lines = new Vector();
        int start = 0;
        while (start < text.length()) {
            int end = text.indexOf('\n', start);
            if (end == -1) {
                lines.addElement(text.substring(start));
                break;
            }
            lines.addElement(text.substring(start, end));
            start = end + 1;
        }
        
        String[] result = new String[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            result[i] = (String) lines.elementAt(i);
        }
        return result;
    }
    
    private void drawItem(Graphics g, int w, int y, int idx, String lbl, String val) {
        if (selected == idx) {
            g.setColor(Utils.COLOR_HI);
            g.fillRect(4, y - 2, w - 8, 18);
        }
        g.setColor(Utils.COLOR_TEXT);
        g.drawString(lbl, 8, y, Graphics.TOP | Graphics.LEFT);
        g.setColor(Utils.COLOR_ACCENT);
        g.drawString("<" + val + ">", w - 8, y, Graphics.TOP | Graphics.RIGHT);
    }
    
    protected void keyPressed(int keyCode) {
        int action = getGameAction(keyCode);
        
        if (action == UP) {
            if (keyCode == Canvas.KEY_NUM2) {
                scrollOffset = Math.max(0, scrollOffset - 20);
            } else {
                selected = (selected - 1 + 4) % 4;
            }
        } else if (action == DOWN) {
            if (keyCode == Canvas.KEY_NUM8) {
                scrollOffset += 20;
            } else {
                selected = (selected + 1) % 4;
            }
        } else if (action == LEFT) {
            adjust(-1);
        } else if (action == RIGHT) {
            adjust(1);
        } else if (action == FIRE && selected == 3) {
            save();
        } else if (keyCode == -6 || keyCode == -21) {
            midlet.showMenu();
        }
        
        repaint();
    }
    
    private void adjust(int d) {
        if (selected == 0) timeout = Math.max(5, Math.min(120, timeout + d * 5));
        else if (selected == 1) contextEnabled = !contextEnabled;
        else if (selected == 2) maxContext = Math.max(1, Math.min(20, maxContext + d));
    }
    
    private void save() {
        midlet.getSettings().setTimeout(timeout * 1000);
        midlet.getSettings().setContextEnabled(contextEnabled);
        midlet.getSettings().setMaxContext(maxContext);
        midlet.showMenu();
    }
}

// History Screen (unchanged)
class HistoryScreen extends Canvas {
    private AIChatBot midlet;
    private String[] convs;
    private int scroll = 0;
    
    public HistoryScreen(AIChatBot midlet) {
        this.midlet = midlet;
        setFullScreenMode(true);
        convs = midlet.getHistory().getAllConversations();
    }
    
    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        boolean small = w < 180;
        Font font = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        int fontH = font.getHeight();
        g.setFont(font);
        
        g.setColor(Utils.COLOR_BG);
        g.fillRect(0, 0, w, h);
        
        int headerH = small ? 14 : 20;
        g.setColor(Utils.COLOR_ACCENT);
        g.fillRect(0, 0, w, headerH);
        g.setColor(Utils.COLOR_BG);
        g.drawString("Historique (" + convs.length + ")", w/2, 2, Graphics.TOP | Graphics.HCENTER);
        
        int softH = small ? 12 : 14;
        g.setClip(0, headerH, w, h - headerH - softH);
        
        int y = headerH + 4 - scroll;
        if (convs.length == 0) {
            g.setColor(Utils.COLOR_DIM);
            g.drawString("Vide", w/2, h/2, Graphics.TOP | Graphics.HCENTER);
        } else {
            for (int i = 0; i < convs.length; i++) {
                if (y > headerH - fontH && y < h - softH) {
                    g.setColor(Utils.COLOR_BORDER);
                    g.drawLine(4, y, w - 4, y);
                    g.setColor(Utils.COLOR_TEXT);
                    String t = convs[i];
                    if (t.length() > 28) t = t.substring(0, 28) + "..";
                    g.drawString("[" + (i+1) + "] " + t, 6, y + 2, Graphics.TOP | Graphics.LEFT);
                }
                y += fontH + 6;
            }
        }
        
        g.setClip(0, 0, w, h);
        g.setColor(Utils.COLOR_ACCENT);
        g.fillRect(0, h - softH, w, softH);
        g.setColor(Utils.COLOR_BG);
        g.drawString("Back", 2, h - softH + 1, Graphics.TOP | Graphics.LEFT);
    }
    
    protected void keyPressed(int keyCode) {
        int action = getGameAction(keyCode);
        if (action == UP) scroll = Math.max(0, scroll - 25);
        else if (action == DOWN) scroll += 25;
        else if (keyCode == -6 || keyCode == -21) midlet.showMenu();
        repaint();
    }
}

// About Screen (unchanged)
class AboutScreen extends Canvas {
    private AIChatBot midlet;
    
    public AboutScreen(AIChatBot midlet) {
        this.midlet = midlet;
        setFullScreenMode(true);
    }
    
    protected void paint(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        boolean small = w < 180;
        Font font = Font.getFont(Font.FACE_MONOSPACE, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        int fontH = font.getHeight();
        g.setFont(font);
        
        g.setColor(Utils.COLOR_BG);
        g.fillRect(0, 0, w, h);
        
        int headerH = small ? 14 : 20;
        g.setColor(Utils.COLOR_ACCENT);
        g.fillRect(0, 0, w, headerH);
        g.setColor(Utils.COLOR_BG);
        g.drawString("A propos", w/2, 2, Graphics.TOP | Graphics.HCENTER);
        
        int y = headerH + 8;
        int lh = fontH + 2;
        
        g.setColor(Utils.COLOR_ACCENT);
        g.drawString("AI ChatBot " + AIChatBot.VERSION, w/2, y, Graphics.TOP | Graphics.HCENTER);
        y += lh * 2;
        
        String[] lines = {
            "=== T9 INPUT ===",
            "2: a b c 2",
            "8: t u v 8",
            "#: Toggle MAJ/min",
            "*: Mode 123",
            "**: QWERTY mode",
            "",
            "=== NAV ===",
            "D-pad: Scroll",
            "Fire: Envoyer",
            "LSK: Menu",
            "RSK: Effacer",
            "",
            "=== SAVE ===",
            "JSR-75: " + (midlet.getSaveManager().isFileAPIAvailable() ? "OUI" : "NON"),
            "",
            "ID: " + midlet.getUserId()
        };
        
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("===")) {
                g.setColor(Utils.COLOR_ACCENT);
            } else {
                g.setColor(Utils.COLOR_TEXT);
            }
            g.drawString(lines[i], w/2, y, Graphics.TOP | Graphics.HCENTER);
            y += lh;
        }
        
        int softH = small ? 12 : 14;
        g.setColor(Utils.COLOR_ACCENT);
        g.fillRect(0, h - softH, w, softH);
        g.setColor(Utils.COLOR_BG);
        g.drawString("Back", 2, h - softH + 1, Graphics.TOP | Graphics.LEFT);
    }
    
    protected void keyPressed(int keyCode) {
        if (keyCode == -6 || keyCode == -21) midlet.showMenu();
    }
}