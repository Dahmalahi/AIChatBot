import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.rms.*;
import javax.microedition.io.*;
import javax.microedition.io.file.*;
import java.io.*;
import java.util.*;

// ================================================================
// AIChatBot v1.9
// UPDATED FROM v1.8:
//   - VERSION bumped to v1.9, BUILD 20250422
//   - RMS stores renamed to acb19prefs / acb19hist
//   - FIX: History resume now properly restores old chat messages
//   - FIX: AITools scrolling fixed with proper persistent scrollOff
//   - NEW: Message selector square (2/8 keys highlight bubble)
//   - NEW: Settings custom file path for save/read/write/connections
//   - FIX: AI generation limit REMOVED - supports up to 1,000,000 chars
//          (display bubble still truncates at 500 for readability,
//           but the FULL text is stored and readable via FullMessageCanvas)
// PATCH (Bug 4):
//   - FIX: FullMessageCanvas CONTENT_TOP_PAD unified to 8px in both
//          buildLines() and paint() — was 8 vs 6 causing last line to
//          bleed into footer by ~1 line height on every page.
// PATCH (API):
//   - FIX: fetchWithRetry() uses URLBuilder.encodeForAPI() so spaces
//          encode as %20 (not +); the Workers endpoint rejects +.
//   - FIX: custom apiBase from Settings is now honoured in requests
//          (previously saved but never used — hardcoded API_BASE won).
// PATCH (UI v2 — ported from 320x240 changelog):
//   - CHANGE: ChatCanvas bubble width 72% -> 70% (wider, more text visible)
//   - CHANGE: MenuScreen item height increased (font+16) & spacing 5px
//   - CHANGE: MenuScreen indicator dots removed (cleaner UI)
//   - NEW:    Quick Replies carousel — single-card view with dot indicators
//   - NEW:    HistoryScreen # (KEY_POUND) to delete selected session directly
//   - NEW:    ProfileScreen auto-scroll when content overflows screen
//   - NEW:    AboutScreen auto-scroll when content overflows screen
// ================================================================
public class AIChatBot extends MIDlet {

    public Display display;

    private ChatCanvas  chatCanvas;
    private MenuScreen  menuScreen;
    private SetupCanvas setupCanvas;

    private String userName = "";
    private String userLang = "English";
    private String userRole = "Assistant";
    private String userId   = "";

    private History     history;
    private Settings    settings;
    private SaveManager saveManager;

    private boolean firstLaunch = true;

    public static final String VERSION  = "v1.9";
    public static final String APP_NAME = "AIChatBot";
    public static final String BUILD    = "20260601";

    private static final String PREFS_STORE   = "acb19prefs";
    private static final String HISTORY_STORE = "acb19hist";

    public AIChatBot() {
        display     = Display.getDisplay(this);
        history     = new History();
        settings    = new Settings();
        saveManager = new SaveManager();
    }

    protected void startApp() throws MIDletStateChangeException {
        loadPrefs();
        if (userId == null || userId.length() == 0) {
            userId = Utils.generateUserId();
        }
        history.loadFromRMS(HISTORY_STORE);
        if (firstLaunch || userName.length() == 0) {
            showSetup();
        } else {
            showMainMenu();
        }
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean u) {}

    public void showMainMenu() {
        menuScreen = new MenuScreen(this);
        display.setCurrent(menuScreen);
    }

    public void showChat() {
        chatCanvas = new ChatCanvas(this, userName, userLang, userRole);
        display.setCurrent(chatCanvas);
    }

    public void showChatWithInput(String input) {
        chatCanvas = new ChatCanvas(this, userName, userLang, userRole);
        chatCanvas.setCurrentInput(input != null ? input : "");
        display.setCurrent(chatCanvas);
    }

    public void showSetup() {
        setupCanvas = new SetupCanvas(this, userName, userLang, userRole);
        display.setCurrent(setupCanvas);
    }

    public void showHistory()  { display.setCurrent(new HistoryScreen(this)); }
    public void showSettings() { display.setCurrent(new SettingsScreen(this)); }
    public void showAbout()    { display.setCurrent(new AboutScreen(this)); }
    public void showFiles()    { display.setCurrent(new FileViewerScreen(this)); }
    public void showAITools()  { display.setCurrent(new AIToolsScreen(this)); }
    public void showProfile()  {
        display.setCurrent(new ProfileScreen(this, userName, userLang, userRole));
    }

    public void openMessageDetail(final String message) {
        display.setCurrent(new FullMessageCanvas(this, message));
    }

    // FIX v1.9: properly restore session into existing chatCanvas
    public void resumeSession(int index) {
        chatCanvas = new ChatCanvas(this, userName, userLang, userRole);
        boolean ok = history.restoreSession(index);
        if (ok) {
            Vector entries = history.getRestoredEntries();
            for (int i = 0; i < entries.size(); i++) {
                String entry = (String) entries.elementAt(i);
                if (entry == null || entry.length() < 3) continue;
                char type = entry.charAt(0);
                int p1 = entry.indexOf('|');
                int p2 = (p1 >= 0) ? entry.indexOf('|', p1 + 1) : -1;
                String msg = (p2 >= 0 && p2 + 1 < entry.length())
                    ? entry.substring(p2 + 1) : entry;
                // FIX v2.0: unescape newlines stored as \u0001 in RMS
                msg = msg.replace('\u0001', '\n');
                if (type == 'U') chatCanvas.addRestoredMessage("User: " + msg);
                else if (type == 'A') chatCanvas.addRestoredMessage("AI: " + msg);
            }
            chatCanvas.setRestoredSessionIndex(index);
        }
        display.setCurrent(chatCanvas);
    }

    public void openSettingsPathInput(String current, final SettingsScreen screen) {
        TextBox tb = new TextBox(
            "File path (e.g. file:///c:/data/)",
            current != null ? current : "", 300, TextField.ANY);
        Command ok = new Command("Save",   Command.OK,   1);
        Command ca = new Command("Cancel", Command.BACK, 2);
        tb.addCommand(ok); tb.addCommand(ca);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable d) {
                if (cmd.getCommandType() == Command.OK)
                    screen.setFilePath(((TextBox) d).getString());
                display.setCurrent(screen);
            }
        });
        display.setCurrent(tb);
    }

    public void exitApp() {
        if (settings.isAutoSaveSession() && !history.isCurrentSessionEmpty()) {
            history.saveCurrentSession();
        }
        history.saveToRMS(HISTORY_STORE);
        savePrefs();
        destroyApp(true);
        notifyDestroyed();
    }

    public void onSetupDone(String name, String lang, String role) {
        userName    = name.length() > 0 ? name : "User";
        userLang    = lang.length() > 0 ? lang : "English";
        userRole    = role.length() > 0 ? role : "Assistant";
        firstLaunch = false;
        savePrefs();
        showMainMenu();
    }

    public void onProfileUpdated(String name, String lang, String role) {
        if (name.length() > 0) userName = name;
        if (lang.length() > 0) userLang = lang;
        if (role.length() > 0) userRole = role;
        savePrefs();
        showMainMenu();
    }

    public void openNativeInput(final ChatCanvas c) {
        // v1.9: No char limit on input - supports massive messages
        TextBox tb = new TextBox("Message", c.getCurrentInput(), 10000, TextField.ANY);
        Command ok = new Command("Send",   Command.OK,   1);
        Command ca = new Command("Cancel", Command.BACK, 2);
        tb.addCommand(ok); tb.addCommand(ca);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable d) {
                if (cmd.getCommandType() == Command.OK)
                    c.setCurrentInput(((TextBox) d).getString());
                display.setCurrent(c);
            }
        });
        display.setCurrent(tb);
    }

    public void openNativeToolInput(String current, final AIToolsScreen screen) {
        TextBox tb = new TextBox("Enter text", current != null ? current : "", 10000, TextField.ANY);
        Command ok = new Command("Use",    Command.OK,   1);
        Command ca = new Command("Cancel", Command.BACK, 2);
        tb.addCommand(ok); tb.addCommand(ca);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable d) {
                if (cmd.getCommandType() == Command.OK)
                    screen.setInputText(((TextBox) d).getString());
                display.setCurrent(screen);
            }
        });
        display.setCurrent(tb);
    }

    public void openFieldInput(final int idx, String cur, final SetupCanvas sc) {
        String[] titles = {"Language", "Your name", "Your role"};
        TextBox tb = new TextBox(titles[idx], cur, 200, TextField.ANY);
        Command ok = new Command("OK",     Command.OK,   1);
        Command ca = new Command("Cancel", Command.BACK, 2);
        tb.addCommand(ok); tb.addCommand(ca);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable d) {
                if (cmd.getCommandType() == Command.OK)
                    sc.setValue(idx, ((TextBox) d).getString());
                display.setCurrent(sc);
            }
        });
        display.setCurrent(tb);
    }

    public void openProfileFieldInput(final int idx, String cur, final ProfileScreen ps) {
        String[] titles = {"Language", "Your name", "Your role"};
        TextBox tb = new TextBox(titles[idx], cur, 200, TextField.ANY);
        Command ok = new Command("OK",     Command.OK,   1);
        Command ca = new Command("Cancel", Command.BACK, 2);
        tb.addCommand(ok); tb.addCommand(ca);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable d) {
                if (cmd.getCommandType() == Command.OK)
                    ps.setValue(idx, ((TextBox) d).getString());
                display.setCurrent(ps);
            }
        });
        display.setCurrent(tb);
    }

    public void showCopyBox(String text, String title) {
        TextBox tb = new TextBox(title, text, text.length() + 10, TextField.ANY);
        Command ok = new Command("Back", Command.BACK, 1);
        tb.addCommand(ok);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) { showMainMenu(); }
        });
        display.setCurrent(tb);
    }

    public void showAsciiMenu(final String ascii, final String full) {
        List m = new List("Options", List.EXCLUSIVE);
        m.append("View content", null);
        m.append("Save as BMP",  null);
        m.append("Copy/Read",    null);
        m.append("Cancel",       null);
        m.addCommand(new Command("OK", Command.OK, 1));
        m.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                int i = ((List) d).getSelectedIndex();
                if (chatCanvas != null) {
                    if      (i == 0) chatCanvas.showDetail(ascii);
                    else if (i == 1) chatCanvas.saveAsBmp(ascii);
                    else if (i == 2) showCopyBox(full, "Content");
                    if (i != 2) display.setCurrent(chatCanvas);
                } else showMainMenu();
            }
        });
        display.setCurrent(m);
    }

    public void confirmClearHistory() {
        List dlg = new List("Clear all history?", List.EXCLUSIVE);
        dlg.append("Yes, clear all", null);
        dlg.append("No, keep it",    null);
        Command ok = new Command("OK", Command.OK, 1);
        dlg.addCommand(ok);
        dlg.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (((List) d).getSelectedIndex() == 0) {
                    history.clearAll();
                    history.saveToRMS(HISTORY_STORE);
                }
                showHistory();
            }
        });
        display.setCurrent(dlg);
    }

    public void savePrefs() {
        try {
            RecordStore rs = RecordStore.openRecordStore(PREFS_STORE, true);
            String fp = settings.getFilePath();
            if (fp == null) fp = "";
            String d = (firstLaunch ? "1" : "0") + "|" + userName + "|" + userLang + "|"
                + userRole + "|" + userId + "|" + settings.getTimeout() + "|"
                + (settings.isContextEnabled() ? "1" : "0") + "|"
                + settings.getMaxContext() + "|"
                + (settings.isProxyEnabled() ? "1" : "0") + "|"
                + settings.getThemeIndex() + "|"
                + (settings.isAIToolsEnabled() ? "1" : "0") + "|"
                + (settings.isAutoSaveSession() ? "1" : "0") + "|"
                + fp;
            byte[] b = d.getBytes();
            if (rs.getNumRecords() == 0) rs.addRecord(b, 0, b.length);
            else                         rs.setRecord(1, b, 0, b.length);
            rs.closeRecordStore();
        } catch (Exception e) {}
    }

    private boolean loadPrefs() {
        try {
            RecordStore rs = RecordStore.openRecordStore(PREFS_STORE, false);
            if (rs.getNumRecords() > 0) {
                String d = new String(rs.getRecord(1));
                int p0 = d.indexOf('|');
                if (p0 > 0) {
                    firstLaunch = d.substring(0, p0).equals("1");
                    int p1=d.indexOf('|',p0+1), p2=d.indexOf('|',p1+1),
                        p3=d.indexOf('|',p2+1), p4=d.indexOf('|',p3+1);
                    if (p1>0&&p2>0&&p3>0&&p4>0) {
                        userName = d.substring(p0+1, p1);
                        userLang = d.substring(p1+1, p2);
                        userRole = d.substring(p2+1, p3);
                        userId   = d.substring(p3+1, p4);
                        try {
                            int p5=d.indexOf('|',p4+1),  p6=d.indexOf('|',p5+1),
                                p7=d.indexOf('|',p6+1),  p8=d.indexOf('|',p7+1),
                                p9=d.indexOf('|',p8+1),  p10=d.indexOf('|',p9+1),
                                p11=d.indexOf('|',p10+1);
                            if (p5>0) settings.setTimeoutSilent(
                                Integer.parseInt(d.substring(p4+1,p5)));
                            if (p6>0) settings.setContextEnabledSilent(
                                d.substring(p5+1,p6).equals("1"));
                            if (p7>0) settings.setMaxContextSilent(
                                Integer.parseInt(d.substring(p6+1,p7)));
                            if (p8>0) settings.setProxyEnabledSilent(
                                d.substring(p7+1,p8).equals("1"));
                            if (p9>0) settings.setThemeIndexSilent(
                                Integer.parseInt(d.substring(p8+1,p9).trim()));
                            if (p10>0) {
                                settings.setAIToolsEnabledSilent(
                                    d.substring(p9+1,p10).equals("1"));
                                if (p11>0) {
                                    settings.setAutoSaveSilent(
                                        d.substring(p10+1,p11).trim().equals("1"));
                                    settings.setFilePathSilent(d.substring(p11+1).trim());
                                } else {
                                    settings.setAutoSaveSilent(
                                        d.substring(p10+1).trim().equals("1"));
                                }
                            }
                        } catch (Exception x) {}
                    }
                }
                rs.closeRecordStore();
                return true;
            }
            rs.closeRecordStore();
        } catch (Exception e) {}
        return false;
    }

    public String      getUserId()      { return userId;      }
    public String      getUserName()    { return userName;    }
    public String      getUserLang()    { return userLang;    }
    public String      getUserRole()    { return userRole;    }
    public History     getHistory()     { return history;     }
    public Settings    getSettings()    { return settings;    }
    public SaveManager getSaveManager() { return saveManager; }
    public Display     getDisplay()     { return display;     }
    public ChatCanvas  getChatCanvas()  { return chatCanvas;  }
    public String      getHistStore()   { return HISTORY_STORE; }
}


// ================================================================
// Pal - Color Palette & Theme System (9 themes)
// ================================================================
class Pal {
    private static final int[][] P = {
        // 0: Dark
        { 0x1A1A2E, 0x16213E, 0x0F3460, 0x2D2D44,
          0xEEEEF8, 0x9090A8, 0xDA7756, 0xB05030,
          0xDA7756, 0x252538, 0xFFFFFF, 0xDDDDEE, 0xFF4444 },
        // 1: ChatGPT
        { 0x171717, 0x202020, 0x2A2A2A, 0x383838,
          0xEEEEEE, 0x888888, 0x10A37F, 0x0C7A5F,
          0x10A37F, 0x262626, 0xFFFFFF, 0xCCCCCC, 0xFF5555 },
        // 2: Ocean
        { 0x0A1628, 0x142036, 0x1E3050, 0x2A4060,
          0xDDEEFF, 0x7090B0, 0x4AABFF, 0x2288DD,
          0x4AABFF, 0x162840, 0xFFFFFF, 0xBBDDFF, 0xFF4444 },
        // 3: Amoled
        { 0x000000, 0x0C0C0C, 0x141414, 0x222222,
          0xF0F0F0, 0x808080, 0xBB55FF, 0x8822CC,
          0xBB55FF, 0x0E0E0E, 0xFFFFFF, 0xDDDDDD, 0xFF4444 },
        // 4: Sunset
        { 0x1C1008, 0x2A1A0C, 0x3A2614, 0x4A3020,
          0xFFEECC, 0xBB9966, 0xFF7744, 0xCC4422,
          0xFF7744, 0x221408, 0xFFFFEE, 0xFFDDAA, 0xFF3333 },
        // 5: Forest
        { 0x081408, 0x0E200E, 0x163016, 0x204020,
          0xCCEECC, 0x7AAA7A, 0x44CC55, 0x2A9933,
          0x44CC55, 0x0C180C, 0xFFFFFF, 0xAAFFAA, 0xFF4444 },
        // 6: Rose
        { 0x1A0816, 0x280E22, 0x38163A, 0x48204A,
          0xFFDDEE, 0xBB88AA, 0xFF66AA, 0xCC3388,
          0xFF66AA, 0x200A1E, 0xFFFFFF, 0xFFBBDD, 0xFF4444 },
        // 7: Hacker
        { 0x000000, 0x030803, 0x060E06, 0x0A180A,
          0x00FF41, 0x00AA2A, 0x00FF41, 0x00AA2A,
          0x00AA2A, 0x040C04, 0x00FF41, 0x00CC33, 0xFF0000 },
        // 8: Ice
        { 0xF0F4FF, 0xFFFFFF, 0xE8EEFF, 0xCCDDFF,
          0x222244, 0x6677AA, 0x3366FF, 0x2244CC,
          0x3366FF, 0xEEF2FF, 0xFFFFFF, 0x112244, 0xFF2222 },
    };
    private static final String[] NAMES = {
        "Dark","ChatGPT","Ocean","Amoled","Sunset","Forest","Rose","Hacker","Ice"
    };
    public static int    count()           { return P.length;    }
    public static String name(int t)       { return NAMES[t%9];  }
    public static int    bg(int t)         { return P[t%9][0];   }
    public static int    surface(int t)    { return P[t%9][1];   }
    public static int    surface2(int t)   { return P[t%9][2];   }
    public static int    border(int t)     { return P[t%9][3];   }
    public static int    textPri(int t)    { return P[t%9][4];   }
    public static int    textSec(int t)    { return P[t%9][5];   }
    public static int    accent(int t)     { return P[t%9][6];   }
    public static int    accentDk(int t)   { return P[t%9][7];   }
    public static int    userBubble(int t) { return P[t%9][8];   }
    public static int    aiBubble(int t)   { return P[t%9][9];   }
    public static int    userText(int t)   { return P[t%9][10];  }
    public static int    aiText(int t)     { return P[t%9][11];  }
    public static int    danger(int t)     { return P[t%9][12];  }
}


// ================================================================
// UI - Shared drawing helpers
// ================================================================
class UI {
    public static void fillRR(Graphics g,int col,int x,int y,int w,int h,int arc) {
        g.setColor(col); g.fillRoundRect(x,y,w,h,arc,arc);
    }
    public static void drawRR(Graphics g,int col,int x,int y,int w,int h,int arc) {
        g.setColor(col); g.drawRoundRect(x,y,w,h,arc,arc);
    }
    public static void divider(Graphics g,int col,int x,int y,int w) {
        g.setColor(col); g.drawLine(x,y,x+w,y);
    }
    public static void fillCircle(Graphics g,int col,int cx,int cy,int r) {
        g.setColor(col); g.fillArc(cx-r,cy-r,r*2,r*2,0,360);
    }
    public static void drawCircle(Graphics g,int col,int cx,int cy,int r) {
        g.setColor(col); g.drawArc(cx-r,cy-r,r*2,r*2,0,360);
    }
    public static void avatar(Graphics g,int cx,int cy,int r,
                               int bg,int fg,String init,Font f) {
        fillCircle(g,bg,cx,cy,r);
        g.setColor(fg); g.setFont(f);
        int tw=f.stringWidth(init),fh=f.getHeight();
        g.drawString(init,cx-tw/2,cy-fh/2,Graphics.TOP|Graphics.LEFT);
    }
    public static String initials(String name) {
        if (name==null||name.length()==0) return "?";
        String u=name.trim().toUpperCase();
        int sp=u.indexOf(' ');
        if (sp>0&&sp<u.length()-1) return ""+u.charAt(0)+u.charAt(sp+1);
        return u.substring(0,Math.min(2,u.length()));
    }
    public static int drawHeader(Graphics g,int t,int sw,
                                  Font bold,Font tiny,String title,String right) {
        int hdrH=bold.getHeight()+14;
        g.setColor(Pal.surface(t)); g.fillRect(0,0,sw,hdrH);
        divider(g,Pal.border(t),0,hdrH,sw);
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString("<",8,hdrH/2-tiny.getHeight()/2,Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textPri(t)); g.setFont(bold);
        g.drawString(title,sw/2,hdrH/2-bold.getHeight()/2,Graphics.TOP|Graphics.HCENTER);
        if (right!=null&&right.length()>0) {
            g.setColor(Pal.textSec(t)); g.setFont(tiny);
            g.drawString(right,sw-6,hdrH/2-tiny.getHeight()/2,Graphics.TOP|Graphics.RIGHT);
        }
        return hdrH;
    }
    public static void drawFooter(Graphics g,int t,int sw,int sh,Font tiny,String hint) {
        int sh2=tiny.getHeight()+6;
        g.setColor(Pal.surface(t)); g.fillRect(0,sh-sh2,sw,sh2);
        divider(g,Pal.border(t),0,sh-sh2,sw);
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString(hint,sw/2,sh-sh2+2,Graphics.TOP|Graphics.HCENTER);
    }
    // v1.9: draw selection square around a bubble rect
    public static void drawSelectionSquare(Graphics g,int col,int x,int y,int w,int h) {
        g.setColor(col);
        g.drawRect(x-2,y-2,w+4,h+4);
        g.drawRect(x-3,y-3,w+6,h+6);
        // Corner accents
        int sz=5;
        g.fillRect(x-3,y-3,sz,sz);
        g.fillRect(x+w+3-sz,y-3,sz,sz);
        g.fillRect(x-3,y+h+3-sz,sz,sz);
        g.fillRect(x+w+3-sz,y+h+3-sz,sz,sz);
    }
}


// ================================================================
// SetupCanvas
// ================================================================
class SetupCanvas extends Canvas implements Runnable {
    private AIChatBot midlet;
    private Font  font, bold, tiny;
    private String[] labels = {"Language","Your name","Your role"};
    private String[] values = {"English","","Assistant"};
    private int   focusIndex=0, screenW, screenH;
    private boolean running=false;
    private int   animTick=0;
    private int   theme;

    public SetupCanvas(AIChatBot midlet,String uName,String uLang,String uRole) {
        this.midlet=midlet;
        theme=midlet.getSettings().getThemeIndex();
        font=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        bold=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD, Font.SIZE_SMALL);
        tiny=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        values[0]=uLang.length()>0?uLang:"English";
        values[1]=uName.length()>0?uName:"";
        values[2]=uRole.length()>0?uRole:"Assistant";
        setFullScreenMode(true);
    }

    protected void showNotify() {
        screenW=getWidth(); screenH=getHeight();
        theme=midlet.getSettings().getThemeIndex();
        running=true; new Thread(this).start();
    }
    protected void hideNotify() { running=false; }

    public void run() {
        while (running) {
            animTick++; repaint();
            try { Thread.sleep(150); } catch (Exception e) {}
        }
    }

    protected void paint(Graphics g) {
        int t=theme, fh=font.getHeight(), bh=bold.getHeight(), sh=tiny.getHeight();
        g.setColor(Pal.bg(t)); g.fillRect(0,0,screenW,screenH);
        g.setColor(Pal.surface(t)); g.fillRect(0,0,screenW,50);
        UI.divider(g,Pal.border(t),0,50,screenW);
        int logoR=18,logoCX=screenW/2,logoCY=30;
        UI.fillCircle(g,Pal.accent(t),logoCX,logoCY,logoR);
        int pulse=(animTick%16);
        if (pulse<8) UI.drawCircle(g,Pal.border(t),logoCX,logoCY,logoR+3+pulse/2);
        g.setColor(Pal.userText(t)); g.setFont(bold);
        g.drawString("AI",logoCX-bold.stringWidth("AI")/2,logoCY-bh/2,Graphics.TOP|Graphics.LEFT);
        int y=60;
        g.setColor(Pal.textPri(t)); g.setFont(bold);
        g.drawString("Welcome to "+AIChatBot.APP_NAME,screenW/2,y,Graphics.TOP|Graphics.HCENTER);
        y+=bh+2;
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString("Set up your profile once to get started",screenW/2,y,Graphics.TOP|Graphics.HCENTER);
        y+=sh+10;
        int cx=8,cw=screenW-16,cp=10,fieldH=sh+10;
        int cardH=labels.length*(sh+4+fieldH+10)+cp;
        UI.fillRR(g,Pal.surface(t),cx,y,cw,cardH,10);
        UI.drawRR(g,Pal.border(t), cx,y,cw,cardH,10);
        int fy=y+cp;
        for (int i=0; i<labels.length; i++) {
            boolean focused=(i==focusIndex);
            g.setColor(focused?Pal.accent(t):Pal.textSec(t)); g.setFont(tiny);
            g.drawString(labels[i],cx+cp,fy,Graphics.TOP|Graphics.LEFT);
            fy+=sh+4;
            UI.fillRR(g,focused?Pal.surface2(t):Pal.bg(t),cx+cp,fy,cw-cp*2,fieldH,6);
            UI.drawRR(g,focused?Pal.accent(t):Pal.border(t),cx+cp,fy,cw-cp*2,fieldH,6);
            String disp=values[i].length()>0?values[i]:(labels[i]+"...");
            g.setColor(values[i].length()>0?Pal.textPri(t):Pal.textSec(t)); g.setFont(font);
            g.drawString(disp,cx+cp+6,fy+(fieldH-fh)/2,Graphics.TOP|Graphics.LEFT);
            if (focused&&(animTick/4)%2==0) {
                int cx2=cx+cp+6+font.stringWidth(disp)+1;
                g.setColor(Pal.accent(t)); g.drawLine(cx2,fy+4,cx2,fy+fieldH-4);
            }
            fy+=fieldH+10;
        }
        y+=cardH+12;
        boolean btnFoc=(focusIndex==labels.length);
        int btnH=bh+14;
        UI.fillRR(g,btnFoc?Pal.accent(t):Pal.accentDk(t),8,y,screenW-16,btnH,btnH/2);
        if (btnFoc) UI.drawRR(g,Pal.textPri(t),6,y-2,screenW-12,btnH+4,(btnH+4)/2);
        g.setColor(Pal.userText(t)); g.setFont(bold);
        g.drawString("Get Started  ->",screenW/2,y+btnH/2-bh/2,Graphics.TOP|Graphics.HCENTER);
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString("2/8:move  FIRE:edit or start",screenW/2,screenH-sh-3,Graphics.TOP|Graphics.HCENTER);
    }

    protected void keyPressed(int keyCode) {
        int a=getGameAction(keyCode);
        if      (a==UP  ||keyCode==Canvas.KEY_NUM2){focusIndex--;if(focusIndex<0)focusIndex=labels.length;}
        else if (a==DOWN||keyCode==Canvas.KEY_NUM8){focusIndex++;if(focusIndex>labels.length)focusIndex=0;}
        else if (a==FIRE||keyCode==Canvas.KEY_NUM5||keyCode==-5||keyCode==10) {
            if (focusIndex==labels.length){running=false;midlet.onSetupDone(values[1],values[0],values[2]);}
            else midlet.openFieldInput(focusIndex,values[focusIndex],this);
        }
        repaint();
    }

    public void setValue(int i,String v){if(i>=0&&i<values.length)values[i]=v;repaint();}
}


// ================================================================
// ProfileScreen
// ================================================================
class ProfileScreen extends Canvas {
    private AIChatBot midlet;
    private Font  font,bold,tiny;
    private String[] labels={"Language","Your name","Your role"};
    private String[] values;
    private int   focusIndex=0,screenW,screenH,theme;
    // PATCH: scroll support for ProfileScreen
    private int scrollOffset=0;

    public ProfileScreen(AIChatBot midlet,String name,String lang,String role) {
        this.midlet=midlet;
        theme=midlet.getSettings().getThemeIndex();
        font=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        bold=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD, Font.SIZE_SMALL);
        tiny=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        values=new String[]{lang,name,role};
        setFullScreenMode(true);
    }

    protected void showNotify(){
        screenW=getWidth();screenH=getHeight();
        theme=midlet.getSettings().getThemeIndex();scrollOffset=0;repaint();
    }

    protected void paint(Graphics g) {
        int t=theme,fh=font.getHeight(),bh=bold.getHeight(),sh=tiny.getHeight();
        g.setColor(Pal.bg(t));g.fillRect(0,0,screenW,screenH);
        int hdrH=UI.drawHeader(g,t,screenW,bold,tiny,"Edit Profile",null);
        int ftrH=sh+8,ftrY=screenH-ftrH;
        // PATCH: calculate content height and apply scroll clip
        int cp=10,cx=8,cw=screenW-16,fieldH=sh+10;
        int contentH=(sh+4+fieldH+10)*labels.length+(bh+14)+12;
        int availH=ftrY-hdrH-12;
        int maxScroll=Math.max(0,contentH-availH);
        if(scrollOffset>maxScroll)scrollOffset=maxScroll;
        if(scrollOffset<0)scrollOffset=0;
        g.setClip(0,hdrH,screenW,ftrY-hdrH);
        int y=hdrH+12-scrollOffset;
        for (int i=0;i<labels.length;i++) {
            boolean focused=(i==focusIndex);
            g.setColor(focused?Pal.accent(t):Pal.textSec(t));g.setFont(tiny);
            g.drawString(labels[i],cx+cp,y,Graphics.TOP|Graphics.LEFT);
            y+=sh+4;
            UI.fillRR(g,focused?Pal.surface2(t):Pal.surface(t),cx+cp,y,cw-cp*2,fieldH,6);
            UI.drawRR(g,focused?Pal.accent(t):Pal.border(t),   cx+cp,y,cw-cp*2,fieldH,6);
            String disp=values[i].length()>0?values[i]:"(empty)";
            g.setColor(values[i].length()>0?Pal.textPri(t):Pal.textSec(t));g.setFont(font);
            g.drawString(disp,cx+cp+6,y+(fieldH-fh)/2,Graphics.TOP|Graphics.LEFT);
            y+=fieldH+10;
        }
        int btnH=bh+14;boolean btnFoc=(focusIndex==labels.length);
        UI.fillRR(g,btnFoc?Pal.accent(t):Pal.accentDk(t),8,y,screenW-16,btnH,btnH/2);
        g.setColor(Pal.userText(t));g.setFont(bold);
        g.drawString("Save Profile",screenW/2,y+btnH/2-bh/2,Graphics.TOP|Graphics.HCENTER);
        g.setClip(0,0,screenW,screenH);
        boolean canScroll=(contentH>availH);
        UI.drawFooter(g,t,screenW,screenH,tiny,canScroll?"2/8:scroll  FIRE:edit  LSK:back":"2/8:move  FIRE:edit  LSK:back");
    }

    protected void keyPressed(int k) {
        int a=getGameAction(k);
        int sh=tiny.getHeight();
        boolean canScroll=false;
        {
            int cp=10,fieldH=sh+10;
            int contentH=(sh+4+fieldH+10)*labels.length+(tiny.getHeight()+14+14)+12;
            int hdrH=bold.getHeight()+14+4;
            int ftrH=sh+8;
            int availH=screenH-hdrH-ftrH-12;
            canScroll=(contentH>availH);
        }
        if(canScroll){
            int step=tiny.getHeight()*3;
            if      (a==UP  ||k==Canvas.KEY_NUM2){scrollOffset-=step;if(scrollOffset<0)scrollOffset=0;}
            else if (a==DOWN||k==Canvas.KEY_NUM8){scrollOffset+=step;}
        } else {
            if      (a==UP  ||k==Canvas.KEY_NUM2){focusIndex--;if(focusIndex<0)focusIndex=labels.length;}
            else if (a==DOWN||k==Canvas.KEY_NUM8){focusIndex++;if(focusIndex>labels.length)focusIndex=0;}
        }
        if(a==FIRE||k==Canvas.KEY_NUM5||k==-5||k==10) {
            if(focusIndex==labels.length)midlet.onProfileUpdated(values[1],values[0],values[2]);
            else midlet.openProfileFieldInput(focusIndex,values[focusIndex],this);
        }
        else if (k==-6||k==-21) midlet.showMainMenu();
        repaint();
    }

    public void setValue(int i,String v){if(i>=0&&i<values.length)values[i]=v;repaint();}
}


// ================================================================
// MenuScreen - 7 items
// ================================================================
class MenuScreen extends Canvas {
    private AIChatBot midlet;
    private int sel=0,screenW,screenH,theme,scrollOff=0;

    private static final String[] ICONS  ={"*","#","!","@","~","+","x"};
    private static final String[] LABELS ={
        "New Chat","History","AI Tools","Files","Settings","About","Exit"
    };
    private static final String[] DESC={
        "Start a conversation",
        "Past sessions (resume any)",
        "Built-in AI assistants",
        "Device files + AI analysis",
        "Theme & options",
        "App info",
        "Save and quit"
    };

    public MenuScreen(AIChatBot m){
        midlet=m;theme=m.getSettings().getThemeIndex();setFullScreenMode(true);
    }

    protected void showNotify(){
        screenW=getWidth();screenH=getHeight();
        theme=midlet.getSettings().getThemeIndex();repaint();
    }

    protected void paint(Graphics g) {
        int t=theme;
        Font bold=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD, Font.SIZE_SMALL);
        Font font=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        Font tiny=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        int bh=bold.getHeight(),fh=font.getHeight(),sh=tiny.getHeight();

        g.setColor(Pal.bg(t));g.fillRect(0,0,screenW,screenH);

        int hdrH=bh+16;
        g.setColor(Pal.surface(t));g.fillRect(0,0,screenW,hdrH);
        UI.divider(g,Pal.border(t),0,hdrH,screenW);
        int avR=9,avCX=avR+10,avCY=hdrH/2;
        UI.fillCircle(g,Pal.accent(t),avCX,avCY,avR);
        g.setColor(Pal.userText(t));g.setFont(tiny);
        g.drawString("AI",avCX-tiny.stringWidth("AI")/2,avCY-sh/2,Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textPri(t));g.setFont(bold);
        g.drawString(AIChatBot.APP_NAME,avCX+avR+7,hdrH/2-bh/2,Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString(AIChatBot.VERSION,screenW-6,hdrH/2-sh/2,Graphics.TOP|Graphics.RIGHT);

        int cardY=hdrH+8,cardX=8,cardW=screenW-16,cardH=bh+sh+16;
        UI.fillRR(g,Pal.surface(t),cardX,cardY,cardW,cardH,8);
        int uR=12,uCX=cardX+uR+8,uCY=cardY+cardH/2;
        UI.avatar(g,uCX,uCY,uR,Pal.accent(t),Pal.userText(t),UI.initials(midlet.getUserName()),tiny);
        int infoX=uCX+uR+8;
        g.setColor(Pal.textPri(t));g.setFont(bold);
        g.drawString(midlet.getUserName(),infoX,cardY+6,Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString(midlet.getUserRole()+" | "+midlet.getUserLang(),infoX,cardY+6+bh+2,Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.accent(t));
        g.drawString("Edit >",cardX+cardW-tiny.stringWidth("Edit >")-8,cardY+6+bh+2,Graphics.TOP|Graphics.LEFT);
        UI.divider(g,Pal.border(t),cardX,cardY+cardH+6,cardW);

        int listY=cardY+cardH+14,softH=sh+6,listH=screenH-listY-softH;
        // PATCH: increased item height (font+12) and spacing (5px) for comfort
        int iH=fh+sh+16,iStep=iH+5,visible=Math.max(1,listH/iStep);
        if (sel<scrollOff)          scrollOff=sel;
        if (sel>=scrollOff+visible) scrollOff=sel-visible+1;
        if (scrollOff<0)            scrollOff=0;

        int iW=screenW-16,iX=8;
        g.setClip(0,listY,screenW,listH);
        for (int i=0;i<LABELS.length;i++) {
            int drawIdx=i-scrollOff;
            if (drawIdx<0||drawIdx>=visible) continue;
            int iy=listY+drawIdx*iStep;
            boolean foc=(i==sel);
            if (foc){UI.fillRR(g,Pal.surface(t),iX,iy,iW,iH,8);g.setColor(Pal.accent(t));g.fillRect(iX,iy,3,iH);}
            int icR=iH/2-2,icCX=iX+4+icR,icCY=iy+iH/2;
            UI.fillCircle(g,foc?Pal.accent(t):Pal.surface2(t),icCX,icCY,icR);
            g.setColor(foc?Pal.userText(t):Pal.textSec(t));g.setFont(tiny);
            g.drawString(ICONS[i],icCX-tiny.stringWidth(ICONS[i])/2,icCY-sh/2,Graphics.TOP|Graphics.LEFT);
            int lx=icCX+icR+8;
            g.setColor(foc?Pal.textPri(t):Pal.textSec(t));g.setFont(foc?bold:font);
            g.drawString(LABELS[i],lx,iy+(iH-bh-sh-2)/2,Graphics.TOP|Graphics.LEFT);
            g.setColor(Pal.textSec(t));g.setFont(tiny);
            g.drawString(DESC[i],lx,iy+(iH-bh-sh-2)/2+bh+2,Graphics.TOP|Graphics.LEFT);
            if(foc){g.setColor(Pal.accent(t));g.setFont(bold);g.drawString(">",iX+iW-12,iy+iH/2-bh/2,Graphics.TOP|Graphics.LEFT);}
        }
        g.setClip(0,0,screenW,screenH);
        // PATCH: indicator dots removed for cleaner UI

        int themeBarH=sh+4;
        g.setColor(Pal.surface(t));g.fillRect(0,screenH-themeBarH,screenW,themeBarH);
        UI.divider(g,Pal.border(t),0,screenH-themeBarH,screenW);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString("Theme:"+Pal.name(t)+"  2/8:nav  FIRE:open",screenW/2,screenH-themeBarH+2,Graphics.TOP|Graphics.HCENTER);
    }

    protected void keyPressed(int keyCode) {
        int a=getGameAction(keyCode);
        if      (a==UP  ||keyCode==Canvas.KEY_NUM2) sel=(sel-1+LABELS.length)%LABELS.length;
        else if (a==DOWN||keyCode==Canvas.KEY_NUM8) sel=(sel+1)%LABELS.length;
        else if (a==FIRE||keyCode==Canvas.KEY_NUM5||keyCode==-5||keyCode==10) {
            switch(sel){
                case 0:midlet.showChat();     break;
                case 1:midlet.showHistory();  break;
                case 2:midlet.showAITools();  break;
                case 3:midlet.showFiles();    break;
                case 4:midlet.showSettings(); break;
                case 5:midlet.showAbout();    break;
                case 6:midlet.exitApp();      break;
            }
        }
        else if (keyCode==Canvas.KEY_STAR) midlet.showProfile();
        repaint();
    }
}


// ================================================================
// ChatCanvas v1.9
// FIX: message selector square with 2/8 keys
// FIX: no generation cap (reads unlimited from server)
// NEW: addRestoredMessage for history resume
// ================================================================
class ChatCanvas extends Canvas implements Runnable {
    private AIChatBot midlet;
    private Vector  messages    =new Vector();
    private String  currentInput="";
    private String  userName,userLang,userRole;
    private String  session     ="";
    private int     theme;

    private int  sw,sh,hdrH,barH,barY,inputH;
    private Font font,bold,tiny;
    private boolean layoutDone=false;

    private int  focusIdx =0;
    private int  scrollY  =0;

    private boolean cursorOn=true;
    private Timer   cursorTimer;

    private boolean loading    =false;
    private boolean detailMode =false;
    private String  detailText ="";
    private int     detailScroll=0;
    private int     animTick   =0;

    // v1.9: message selection with square highlight
    private int  selectedMsgIdx=-1; // which message is selected (-1=none)

    private boolean menuOpen=false;
    private int     menuSel =0;
    private static final String[] MENU={
        "Send message","Web Search","Copy last AI reply",
        "Clear chat","Save to file","New chat","Main menu"
    };

    private static final String[] QUICK_LABEL ={
        "Hello!","Help me with...","Explain:","Translate to"
    };
    private static final String[] QUICK_DESC={
        "Say hi and start chatting","Get practical help with a task",
        "Get a clear step-by-step explanation","Translate text to another language"
    };
    private static final String[] QUICK_PROMPT={
        "Hello! How are you?","Help me with: ","Explain: ",
        "Translate the following text to "
    };
    private boolean showQuick=true;
    private int     quickSel =0;

    // v1.9: UNLIMITED generation - no server-side char cap
    private static final String API_BASE  ="http://api-dl-j2meuploader.ndukadavid70.workers.dev/api/ai/chatgpt?text=";
    private static final String PROXY_BASE="http://cf-proxy.ndukadavid70.workers.dev/?url=";
    private static final int MAX_RETRIES=3;
    private static final int RETRY_DELAY=1000;

    // restored session tracking
    private int restoredSessionIndex=-1;

    public ChatCanvas(AIChatBot midlet,String name,String lang,String role) {
        this.midlet=midlet;this.userName=name;this.userLang=lang;this.userRole=role;
        this.theme=midlet.getSettings().getThemeIndex();
        font=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        bold=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD, Font.SIZE_SMALL);
        tiny=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        setFullScreenMode(true);
        cursorTimer=new Timer();
        cursorTimer.schedule(new TimerTask(){public void run(){cursorOn=!cursorOn;repaint();}},0,500);
        new Thread(this).start();
    }

    public void run() {
        while (true) {
            animTick++;
            if (loading||menuOpen) repaint();
            try{Thread.sleep(200);}catch(Exception e){}
        }
    }

    protected void showNotify(){initLayout();}

    private void initLayout(){
        sw=getWidth();sh=getHeight();
        if(sw>0&&sh>0){
            hdrH=bold.getHeight()+14;inputH=font.getHeight()+10;
            barH=inputH+14;barY=sh-barH;layoutDone=true;repaint();
        }
    }

    // Normal add (new messages)
    public void addMessage(String msg){
        int maxW=(int)(sw*0.74f);if(maxW<=0)maxW=120;
        messages.addElement(new ChatMessage(msg,maxW,font));
        if(messages.size()>120)messages.removeElementAt(0);
        scrollY=0;selectedMsgIdx=-1;
        if(!msg.startsWith("AI: Hello"))showQuick=false;
        repaint();
    }

    // v1.9: add restored message WITHOUT clearing selection or scroll
    public void addRestoredMessage(String msg){
        int maxW=(int)(sw*0.74f);if(maxW<=0)maxW=120;
        messages.addElement(new ChatMessage(msg,maxW,font));
        showQuick=false;
        repaint();
    }

    public void setRestoredSessionIndex(int idx){ restoredSessionIndex=idx; }

    protected void paint(Graphics g){
        if(!layoutDone||sh==0){initLayout();if(!layoutDone)return;}
        int t=theme;
        g.setColor(Pal.bg(t));g.fillRect(0,0,sw,sh);
        paintHeader(g,t);
        g.setClip(0,hdrH,sw,barY-hdrH);
        if      (detailMode)                    paintDetail(g,t);
        else if (showQuick&&messages.size()<=1) paintQuickReplies(g,t);
        else                                    paintMessages(g,t);
        g.setClip(0,0,sw,sh);
        g.setClip(0,barY,sw,barH);paintBar(g,t);g.setClip(0,0,sw,sh);
        if(menuOpen)paintSheet(g,t);
    }

    private void paintHeader(Graphics g,int t){
        int bh=bold.getHeight(),sh2=tiny.getHeight();
        g.setColor(Pal.surface(t));g.fillRect(0,0,sw,hdrH);
        UI.divider(g,Pal.border(t),0,hdrH,sw);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString("<  Menu",6,hdrH/2-sh2/2,Graphics.TOP|Graphics.LEFT);
        int avR=8,avCX=sw/2-bold.stringWidth(AIChatBot.APP_NAME)/2-avR-4,avCY=hdrH/2;
        UI.fillCircle(g,Pal.accent(t),avCX,avCY,avR);
        g.setColor(Pal.userText(t));g.setFont(tiny);
        g.drawString("A",avCX-tiny.stringWidth("A")/2,avCY-sh2/2,Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textPri(t));g.setFont(bold);
        g.drawString(AIChatBot.APP_NAME,sw/2,hdrH/2-bh/2,Graphics.TOP|Graphics.HCENTER);
        // show "restored" badge if session was loaded
        if(restoredSessionIndex>=0){
            g.setColor(Pal.accent(t));g.setFont(tiny);
            g.drawString("Restored",sw-6,hdrH/2-tiny.getHeight()/2,Graphics.TOP|Graphics.RIGHT);
        } else {
            int uR=9,uCX=sw-uR-8,uCY=hdrH/2;
            UI.avatar(g,uCX,uCY,uR,Pal.surface2(t),Pal.textSec(t),UI.initials(userName),tiny);
        }
        if(loading){
            int dotY2=hdrH-4;
            for(int d=0;d<3;d++){
                int phase=(animTick+d*3)%9;
                g.setColor(phase<5?Pal.accent(t):Pal.border(t));
                g.fillArc(sw/2-9+d*9-2,dotY2-2,4,4,0,360);
            }
        }
    }

    private void paintMessages(Graphics g,int t){
        int fh=font.getHeight(),sh2=tiny.getHeight();
        int avR=8;
        int curY=barY-8+scrollY;

        for(int i=messages.size()-1;i>=0;i--){
            ChatMessage m=(ChatMessage)messages.elementAt(i);
            curY-=m.height;
            if(curY>barY){curY-=8;continue;}
            if(curY+m.height<hdrH)break;

            boolean isUser=m.isUser;
            boolean isSel=(i==selectedMsgIdx);
            // PATCH: bubble width increased to 70% for better readability
            int bW=(int)(sw*0.70f);

            if(isUser){
                int avCX=sw-avR-5,avCY=curY+avR+2;
                UI.avatar(g,avCX,avCY,avR,Pal.accent(t),Pal.userText(t),UI.initials(userName),tiny);
                int bX=sw-avR*2-bW-10;
                UI.fillRR(g,Pal.userBubble(t),bX,curY,bW,m.height,14);
                g.setColor(Pal.userBubble(t));g.fillRect(bX+bW-8,curY,8,8);
                int ly=curY+5;
                for(int j=0;j<m.wrappedLines.size();j++){
                    g.setColor(Pal.userText(t));g.setFont(font);
                    g.drawString((String)m.wrappedLines.elementAt(j),bX+6,ly,Graphics.TOP|Graphics.LEFT);
                    ly+=fh+2;
                }
                // v1.9: selection square for user bubble
                if(isSel){
                    UI.drawSelectionSquare(g,Pal.accent(t),bX,curY,bW,m.height);
                }
            } else {
                int avCX=avR+5,avCY=curY+avR+2;
                UI.fillCircle(g,Pal.accentDk(t),avCX,avCY,avR);
                g.setColor(Pal.userText(t));g.setFont(tiny);
                g.drawString("A",avCX-tiny.stringWidth("A")/2,avCY-sh2/2,Graphics.TOP|Graphics.LEFT);
                int bX=avCX+avR+5,bW2=Math.min(bW,sw-bX-6);
                UI.fillRR(g,Pal.aiBubble(t),bX,curY,bW2,m.height,14);
                g.setColor(Pal.aiBubble(t));g.fillRect(bX,curY,8,8);
                UI.drawRR(g,Pal.border(t),bX,curY,bW2,m.height,14);
                int ly=curY+5;boolean inCode=false;
                for(int j=0;j<m.wrappedLines.size();j++){
                    String line=(String)m.wrappedLines.elementAt(j);
                    if(line.trim().equals("```")){
                        inCode=!inCode;
                        UI.divider(g,Pal.border(t),bX+5,ly+fh/2,bW2-10);
                    } else {
                        if(inCode){g.setColor(Pal.bg(t));g.fillRect(bX+3,ly,bW2-6,fh+2);g.setColor(0x88EEBB);}
                        else g.setColor(Pal.aiText(t));
                        g.setFont(font);
                        g.drawString(line,bX+6,ly,Graphics.TOP|Graphics.LEFT);
                        ly+=fh+2;
                    }
                }
                // v1.9: selection square for AI bubble
                if(isSel){
                    UI.drawSelectionSquare(g,Pal.accent(t),bX,curY,bW2,m.height);
                }
                // READ badge on long AI messages
                if(m.isLong){
                    int badgeW=tiny.stringWidth("READ")+8,badgeH=tiny.getHeight()+4;
                    int badgeX=bX+bW2-badgeW-4,badgeY=curY+m.height-badgeH-2;
                    UI.fillRR(g,Pal.accent(t),badgeX,badgeY,badgeW,badgeH,3);
                    g.setColor(Pal.userText(t));g.setFont(tiny);
                    g.drawString("READ",badgeX+4,badgeY+2,Graphics.TOP|Graphics.LEFT);
                }
            }
            curY-=8;
        }
    }

    private void paintQuickReplies(Graphics g,int t){
        int fh=font.getHeight(),bh=bold.getHeight(),sh2=tiny.getHeight();
        int ty=hdrH+10;
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString("What would you like to do?",sw/2,ty,Graphics.TOP|Graphics.HCENTER);
        ty+=sh2+8;
        // PATCH: single-card carousel - show one card at a time with prev/next hints
        int cardH=bh+sh2+24,cardX=8,cardW=sw-16;
        int idx=quickSel;
        UI.fillRR(g,Pal.surface2(t),cardX,ty,cardW,cardH,8);
        g.setColor(Pal.accent(t));g.fillRect(cardX,ty,3,cardH);
        UI.drawRR(g,Pal.accent(t),cardX,ty,cardW,cardH,8);
        g.setColor(Pal.accent(t));g.setFont(bold);
        g.drawString(QUICK_LABEL[idx],cardX+12,ty+6,Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString(QUICK_DESC[idx],cardX+12,ty+6+bh+4,Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.accent(t));g.setFont(bold);
        g.drawString(">",cardX+cardW-14,ty+cardH/2-bh/2,Graphics.TOP|Graphics.LEFT);
        ty+=cardH+6;
        // Dot indicators
        int dotSpacing=10,dotsW=QUICK_LABEL.length*dotSpacing,dotX=sw/2-dotsW/2;
        for(int i=0;i<QUICK_LABEL.length;i++){
            g.setColor(i==quickSel?Pal.accent(t):Pal.border(t));
            g.fillArc(dotX+i*dotSpacing,ty,5,5,0,360);
        }
        ty+=10;
        // Prev/Next nav hints
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        if(quickSel>0)g.drawString("< prev",cardX,ty,Graphics.TOP|Graphics.LEFT);
        g.drawString((quickSel+1)+"/"+QUICK_LABEL.length,sw/2,ty,Graphics.TOP|Graphics.HCENTER);
        if(quickSel<QUICK_LABEL.length-1)g.drawString("next >",cardX+cardW-tiny.stringWidth("next >")-2,ty,Graphics.TOP|Graphics.LEFT);
    }

    private void paintDetail(Graphics g,int t){
        int fh=font.getHeight(),sh2=tiny.getHeight();
        g.setColor(Pal.bg(t));g.fillRect(0,hdrH,sw,barY-hdrH);
        UI.fillRR(g,Pal.surface(t),4,hdrH+4,sw-8,barY-hdrH-8,8);
        UI.divider(g,Pal.border(t),8,hdrH+sh2+12,sw-16);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString("Full message  (FIRE=close)",sw/2,hdrH+6,Graphics.TOP|Graphics.HCENTER);
        int ty=hdrH+sh2+16-detailScroll;
        Vector lines=wrapTxt(detailText,sw-24);
        for(int i=0;i<lines.size();i++){
            if(ty+fh>hdrH&&ty<barY){
                g.setColor(Pal.textPri(t));g.setFont(font);
                g.drawString((String)lines.elementAt(i),12,ty,Graphics.TOP|Graphics.LEFT);
            }
            ty+=fh+2;
        }
    }

    private void paintBar(Graphics g,int t){
        int fh=font.getHeight(),sh2=tiny.getHeight();
        g.setColor(Pal.surface(t));g.fillRect(0,barY,sw,barH);
        UI.divider(g,Pal.border(t),0,barY,sw);
        if(scrollY>0){
            int badgeH=sh2+6,badgeW=tiny.stringWidth("v latest")+16;
            int badgeX=sw/2-badgeW/2,badgeY=barY-badgeH-4;
            UI.fillRR(g,Pal.accent(t),badgeX,badgeY,badgeW,badgeH,badgeH/2);
            g.setColor(Pal.userText(t));g.setFont(tiny);
            g.drawString("v latest",badgeX+8,badgeY+(badgeH-sh2)/2,Graphics.TOP|Graphics.LEFT);
        }
        // Show selected msg index hint
        if(selectedMsgIdx>=0){
            int selHintH=sh2+4;
            int selHintX=4,selHintW=sw/2-8;
            UI.fillRR(g,Pal.surface2(t),selHintX,barY-selHintH-2,selHintW,selHintH,3);
            g.setColor(Pal.accent(t));g.setFont(tiny);
            ChatMessage sm=(ChatMessage)messages.elementAt(selectedMsgIdx);
            String selHint="Msg "+(selectedMsgIdx+1)+"/"+ messages.size()
                +(sm.isLong?" [long]":"")+(sm.isUser?" [you]":" [AI]");
            g.drawString(selHint,selHintX+4,barY-selHintH-2+2,Graphics.TOP|Graphics.LEFT);
        }
        int drawY=barY+7;
        boolean pFoc=(focusIdx==2&&!detailMode);
        int plusW=inputH+2,plusX=6;
        UI.fillCircle(g,pFoc?Pal.accent(t):Pal.surface2(t),plusX+plusW/2,drawY+inputH/2,plusW/2);
        if(pFoc)UI.drawCircle(g,Pal.textPri(t),plusX+plusW/2,drawY+inputH/2,plusW/2);
        g.setColor(pFoc?Pal.userText(t):Pal.textSec(t));g.setFont(bold);
        g.drawString("+",plusX+plusW/2-bold.stringWidth("+")/2,drawY+inputH/2-bold.getHeight()/2,Graphics.TOP|Graphics.LEFT);
        boolean sFoc=(focusIdx==1&&!detailMode);
        int sendW=inputH+2,sendX=sw-sendW-6;
        UI.fillCircle(g,loading?Pal.border(t):(sFoc?Pal.accent(t):Pal.accentDk(t)),sendX+sendW/2,drawY+inputH/2,sendW/2);
        if(sFoc)UI.drawCircle(g,Pal.textPri(t),sendX+sendW/2,drawY+inputH/2,sendW/2);
        g.setColor(Pal.userText(t));g.setFont(bold);
        g.drawString(">",sendX+sendW/2-bold.stringWidth(">")/2,drawY+inputH/2-bold.getHeight()/2,Graphics.TOP|Graphics.LEFT);
        boolean tFoc=(focusIdx==0&&!detailMode);
        int boxX=plusX+plusW+5,boxW=sendX-boxX-5;
        UI.fillRR(g,tFoc?Pal.surface2(t):Pal.surface(t),boxX,drawY,boxW,inputH,inputH/2);
        UI.drawRR(g,tFoc?Pal.accent(t):Pal.border(t),boxX,drawY,boxW,inputH,inputH/2);
        if(loading){
            g.setColor(Pal.textSec(t));g.setFont(tiny);
            g.drawString("AI is responding...",boxX+10,drawY+inputH/2-tiny.getHeight()/2,Graphics.TOP|Graphics.LEFT);
        } else {
            String disp=detailMode?"Reading...":currentInput;
            if(disp.length()==0&&!detailMode){
                g.setColor(Pal.textSec(t));g.setFont(font);
                g.drawString("Message AIChatBot...",boxX+10,drawY+inputH/2-fh/2,Graphics.TOP|Graphics.LEFT);
            } else {
                int maxTW=boxW-20;
                if(font.stringWidth(disp)>maxTW){
                    int fit=maxTW/font.charWidth('m');
                    if(fit>0&&fit<disp.length())disp=".."+disp.substring(disp.length()-fit);
                }
                g.setColor(Pal.textPri(t));g.setFont(font);
                g.drawString(disp,boxX+10,drawY+inputH/2-fh/2,Graphics.TOP|Graphics.LEFT);
                if(tFoc&&cursorOn&&!loading){
                    int cx=boxX+10+font.stringWidth(disp);
                    g.setColor(Pal.accent(t));g.fillRect(cx,drawY+4,2,inputH-8);
                }
            }
        }
    }

    private void paintSheet(Graphics g,int t){
        for(int i=0;i<barY;i+=2){g.setColor(Pal.bg(t)&0x333333);g.drawLine(0,i,sw,i);}
        int iH=font.getHeight()+12,shH=MENU.length*iH+36,shY=sh-shH;
        UI.fillRR(g,Pal.surface(t),0,shY,sw,shH,14);
        UI.drawRR(g,Pal.border(t), 0,shY,sw,shH,14);
        g.setColor(Pal.border(t));g.fillRoundRect(sw/2-16,shY+7,32,3,3,3);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString("Actions",sw/2,shY+14,Graphics.TOP|Graphics.HCENTER);
        UI.divider(g,Pal.border(t),8,shY+14+tiny.getHeight()+3,sw-16);
        int iy=shY+20+tiny.getHeight();
        for(int i=0;i<MENU.length;i++){
            boolean sel=(i==menuSel);
            if(sel){UI.fillRR(g,Pal.surface2(t),8,iy,sw-16,iH,6);g.setColor(Pal.accent(t));g.fillRect(8,iy,3,iH);}
            g.setColor(sel?Pal.textPri(t):Pal.textSec(t));g.setFont(sel?bold:font);
            g.drawString(MENU[i],20,iy+(iH-font.getHeight())/2,Graphics.TOP|Graphics.LEFT);
            if(sel){g.setColor(Pal.accent(t));g.setFont(tiny);g.drawString(">",sw-18,iy+(iH-tiny.getHeight())/2,Graphics.TOP|Graphics.LEFT);}
            iy+=iH;
        }
    }

    protected void keyPressed(int keyCode){
        int a=getGameAction(keyCode);
        if(menuOpen)  {doMenuKey(keyCode,a);repaint();return;}
        if(detailMode){doDetailKey(keyCode,a);repaint();return;}
        doMainKey(keyCode,a);repaint();
    }

    private void doMenuKey(int k,int a){
        if      (a==UP  ||k==Canvas.KEY_NUM2){menuSel--;if(menuSel<0)menuSel=MENU.length-1;}
        else if (a==DOWN||k==Canvas.KEY_NUM8){menuSel++;if(menuSel>=MENU.length)menuSel=0;}
        else if (a==FIRE||k==Canvas.KEY_NUM5||k==-5||k==10) execMenu();
        else if (k==-7||k==-22||a==LEFT) menuOpen=false;
    }

    private void execMenu(){
        menuOpen=false;
        switch(menuSel){
            case 0:doSend();              break;
            case 1:doWebSearch();         break;
            case 2:copyLastAI();          break;
            case 3:clearChat();           break;
            case 4:saveChat();            break;
            case 5:newChat();             break;
            case 6:midlet.showMainMenu(); break;
        }
    }

    private void doDetailKey(int k,int a){
        if      (a==UP  ||k==Canvas.KEY_NUM2){detailScroll-=font.getHeight()*3;if(detailScroll<0)detailScroll=0;}
        else if (a==DOWN||k==Canvas.KEY_NUM8){detailScroll+=font.getHeight()*3;}
        else if (a==FIRE||k==10) detailMode=false;
    }

    private void doMainKey(int k,int a){
        int step=font.getHeight()*4;

        // Quick replies mode
        if(showQuick&&messages.size()<=1){
            if      (a==UP  ||k==Canvas.KEY_NUM2) quickSel=(quickSel-1+QUICK_LABEL.length)%QUICK_LABEL.length;
            else if (a==DOWN||k==Canvas.KEY_NUM8) quickSel=(quickSel+1)%QUICK_LABEL.length;
            else if (a==FIRE||k==Canvas.KEY_NUM5||k==-5||k==10){
                currentInput=QUICK_PROMPT[quickSel];showQuick=false;
                if(quickSel==0)doSend();
            }
            else if (a==LEFT) {focusIdx--;if(focusIdx<0)focusIdx=2;}
            else if (a==RIGHT){focusIdx=(focusIdx+1)%3;}
            else if (k==-6||k==-21){menuOpen=true;menuSel=0;}
            return;
        }

        // v1.9: 2/8 keys navigate message selection square
        if(a==UP||k==Canvas.KEY_NUM2){
            if(messages.size()>0){
                if(selectedMsgIdx<0) selectedMsgIdx=messages.size()-1;
                else { selectedMsgIdx--; if(selectedMsgIdx<0)selectedMsgIdx=messages.size()-1; }
                scrollToSelected();
            } else {
                int max=getMaxScroll();
                if(max>0){scrollY+=step;if(scrollY>max)scrollY=max;}
            }
            return;
        }
        if(a==DOWN||k==Canvas.KEY_NUM8){
            if(messages.size()>0){
                if(selectedMsgIdx<0) selectedMsgIdx=0;
                else { selectedMsgIdx++; if(selectedMsgIdx>=messages.size())selectedMsgIdx=0; }
                scrollToSelected();
            } else {
                scrollY-=step;if(scrollY<0)scrollY=0;
            }
            return;
        }

        if(a==LEFT) {selectedMsgIdx=-1;focusIdx--;if(focusIdx<0)focusIdx=2;}
        else if(a==RIGHT){selectedMsgIdx=-1;focusIdx=(focusIdx+1)%3;}
        else if(a==FIRE||k==Canvas.KEY_NUM5||k==-5||k==10){
            // FIRE on a selected long AI message -> open full reader
            if(selectedMsgIdx>=0&&selectedMsgIdx<messages.size()){
                ChatMessage fm=(ChatMessage)messages.elementAt(selectedMsgIdx);
                if(!fm.isUser&&fm.isLong){midlet.openMessageDetail(fm.originalText);return;}
            }
            if      (focusIdx==0){if(!loading)midlet.openNativeInput(this);}
            else if (focusIdx==1){if(!loading)doSend();}
            else if (focusIdx==2){menuOpen=true;menuSel=0;}
        }
        else if(k==-6||k==-21){menuOpen=true;menuSel=0;}
        else if(k==-7||k==-22){if(currentInput.length()>0)currentInput=currentInput.substring(0,currentInput.length()-1);}
    }

    private void doSend(){
        String msg=currentInput.trim();if(msg.length()==0)return;
        addMessage("User: "+msg);midlet.getHistory().addUserMessage(msg);
        currentInput="";selectedMsgIdx=-1;fetchAI(msg);
    }

    private void doWebSearch(){
        String q=currentInput.trim();if(q.length()==0)q="latest AI news";
        addMessage("User: [WEB] "+q);midlet.getHistory().addUserMessage("[WEB] "+q);
        currentInput="";selectedMsgIdx=-1;fetchAI("Search the web and summarize results for: "+q);
    }

    private void copyLastAI(){
        String last=midlet.getHistory().getLastAIResponse();
        if(last!=null)midlet.showCopyBox(last,"Last AI Response");
        else addMessage("AI: No response to copy yet.");
    }

    private void fetchAI(final String text){
        loading=true;repaint();
        final String ctx=midlet.getSettings().isContextEnabled()
            ?midlet.getHistory().getContext(midlet.getSettings().getMaxContext()):"";
        new Thread(new Runnable(){public void run(){fetchWithRetry(text,ctx,0);}}).start();
    }

    private void fetchWithRetry(final String text,final String ctx,final int attempt){
        try {
            // FIX: NEVER prepend context into the text param — it confuses the model.
            // Instead send the clean user message as text= and context as a separate
            // &ctx= param (JSON array). The Workers endpoint reads ctx separately and
            // passes it as proper message history to the model.
            String base=midlet.getSettings().getApiBase();
            if(base==null||base.length()==0)base=API_BASE;
            // Normalise base: strip trailing ?text= / &text= if present so we can
            // always append ?text= ourselves cleanly.
            if(base.endsWith("?text="))base=base.substring(0,base.length()-6);
            else if(base.endsWith("&text="))base=base.substring(0,base.length()-6);
            else if(base.endsWith("="))base=base.substring(0,base.length()-1);
            // Build URL: text= is ONLY the current user message, nothing else.
            String url=base+"?text="+URLBuilder.encodeForAPI(text.trim());
            // Append context as JSON array of {role,content} objects if available.
            if(ctx!=null&&ctx.length()>0){
                url+="&ctx="+URLBuilder.encodeForAPI(buildCtxJson(ctx));
            }
            if(midlet.getSettings().isProxyEnabled())url=PROXY_BASE+URLBuilder.encodeForAPI(url);
            HttpConnection hc=(HttpConnection)Connector.open(url);
            hc.setRequestMethod(HttpConnection.GET);
            // v1.9: request unlimited response
            hc.setRequestProperty("User-Agent","J2ME-AIChatBot/1.9");
            hc.setRequestProperty("Connection","close");
            // v1.9: no max-tokens limit in headers - server returns full response
            String res;int rc=hc.getResponseCode();
            if(rc==HttpConnection.HTTP_OK){
                InputStream is=hc.openInputStream();
                // v1.9: large buffer for 1M char responses
                ByteArrayOutputStream bo=new ByteArrayOutputStream();
                byte[] buf=new byte[4096];int n;
                while((n=is.read(buf))!=-1)bo.write(buf,0,n);
                is.close();
                // v1.9: read ALL bytes - no truncation
                String raw=new String(bo.toByteArray(),"UTF-8");
                res=parseResp(raw);
                String sess=pJson(raw,"\"session\":\"","\"");
                if(sess.length()>0)session=sess;
            } else if(rc==503||rc==504){
                hc.close();
                if(attempt<MAX_RETRIES){try{Thread.sleep(RETRY_DELAY);}catch(Exception x){}fetchWithRetry(text,ctx,attempt+1);return;}
                res="[Server unavailable after "+MAX_RETRIES+" retries]";
            } else res="[HTTP Error "+rc+"]";
            hc.close();
            // v1.9: store FULL response - no char limit
            addMessage("AI: "+res);
            midlet.getHistory().addAIMessage(res);
            midlet.getHistory().saveToRMS(midlet.getHistStore());
        } catch(Exception e){
            if(attempt<MAX_RETRIES){try{Thread.sleep(RETRY_DELAY);}catch(Exception x){}fetchWithRetry(text,ctx,attempt+1);return;}
            addMessage("AI: Error after "+(attempt+1)+" attempts: "+e.getMessage());
        } finally {loading=false;repaint();}
    }

    // Converts the plain-text context string (lines of "User: msg" / "Assistant: msg")
    // into a JSON array of {role,content} objects for the &ctx= param.
    // Uses only ASCII-safe chars; no external JSON lib needed.
    private String buildCtxJson(String ctx){
        StringBuffer sb=new StringBuffer("[");
        int pos=0,len=ctx.length();
        boolean first=true;
        while(pos<len){
            int nl=ctx.indexOf('\n',pos);
            int end=(nl>=0)?nl:len;
            String line=ctx.substring(pos,end).trim();
            pos=(nl>=0)?nl+1:len;
            if(line.length()==0)continue;
            String role,content;
            if(line.startsWith("User: ")){
                role="user";content=line.substring(6);
            } else if(line.startsWith("Assistant: ")){
                role="assistant";content=line.substring(11);
            } else if(line.startsWith("U:")){
                role="user";content=line.substring(2).trim();
            } else if(line.startsWith("A:")){
                role="assistant";content=line.substring(2).trim();
            } else continue;
            if(!first)sb.append(',');
            sb.append("{\"role\":\"").append(role).append("\",\"content\":\"");
            // Escape quotes and backslashes inside content
            for(int i=0;i<content.length();i++){
                char c=content.charAt(i);
                if(c=='"')sb.append("\\\"");
                else if(c=='\\')sb.append("\\\\");
                else if(c=='\n')sb.append("\\n");
                else sb.append(c);
            }
            sb.append("\"}");
            first=false;
        }
        sb.append("]");
        return sb.toString();
    }

    private String parseResp(String r){
        if(r==null||r.length()==0)return "[Empty response]";
        // Workers endpoint now returns plain text directly — use it if not JSON
        String trimmed=r.trim();
        if(trimmed.length()>0&&trimmed.charAt(0)!='{')return clean(trimmed);
        // v1.9: parse full response without truncation
        String[] keys={"result","answer","text","content","response","reply"};
        for(int k=0;k<keys.length;k++){
            String v=pJson(r,"\""+keys[k]+"\":\"","\"");
            if(v.length()>0)return clean(unesc(v));
        }
        return clean(Utils.stripHtml(r));
    }

    private String pJson(String s,String key,String end){
        int i=s.indexOf(key);if(i<0)return "";
        i+=key.length();int e=i;
        // v1.9: scan full string - no length limit
        while(e<s.length()){char c=s.charAt(e);if(c==end.charAt(0)&&(e==0||s.charAt(e-1)!='\\'))break;e++;}
        return e>i?s.substring(i,e):"";
    }

    private String unesc(String s){
        StringBuffer b=new StringBuffer();int i=0;
        while(i<s.length()){
            char c=s.charAt(i);
            if(c=='\\'&&i+1<s.length()){
                char n=s.charAt(i+1);
                if      (n=='n'){b.append('\n');i+=2;continue;}
                else if (n=='r'){b.append('\r');i+=2;continue;}
                else if (n=='t'){b.append('\t');i+=2;continue;}
                else if (n=='"'){b.append('"'); i+=2;continue;}
                else if (n=='\\'){b.append('\\');i+=2;continue;}
            }
            b.append(c);i++;
        }
        return b.toString();
    }

    private String clean(String s){
        if(s==null)return "";
        StringBuffer b=new StringBuffer();
        for(int i=0;i<s.length();i++){
            char c=s.charAt(i);
            if((c>=32&&c<=126)||(c>=160&&c<=255)||c=='\n'||c=='\r'||c=='\t')b.append(c);
            else if(c>255)b.append('?');
        }
        return b.toString().trim();
    }

    public void showDetail(String txt){detailText=txt;detailMode=true;detailScroll=0;repaint();}

    public void saveAsBmp(String ascii){
        try{
            Vector lines=new Vector();int s=0;
            while(s<ascii.length()){
                int nl=ascii.indexOf('\n',s);
                if(nl<0){lines.addElement(ascii.substring(s));break;}
                lines.addElement(ascii.substring(s,nl));s=nl+1;
            }
            int mc=0;
            for(int i=0;i<lines.size();i++){int l=((String)lines.elementAt(i)).length();if(l>mc)mc=l;}
            int cW=6,cH=8,iW=mc*cW,iH=lines.size()*cH,rs=(iW*3+3)/4*4,ps=rs*iH,fs=54+ps;
            byte[] b=new byte[fs];
            b[0]=66;b[1]=77;wi(b,2,fs);wi(b,6,0);wi(b,10,54);wi(b,14,40);
            wi(b,18,iW);wi(b,22,-iH);ws(b,26,1);ws(b,28,24);wi(b,30,0);wi(b,34,ps);wi(b,38,2835);wi(b,42,2835);wi(b,46,0);wi(b,50,0);
            for(int r=0;r<lines.size();r++){
                String ln=(String)lines.elementAt(r);
                for(int c2=0;c2<mc;c2++){
                    char ch=(c2<ln.length())?ln.charAt(c2):' ';boolean lit=(ch!=' ');
                    for(int py=0;py<cH;py++)for(int px=0;px<cW;px++){
                        int o=54+(r*cH+py)*rs+(c2*cW+px)*3;
                        b[o]=(byte)(lit?136:0);b[o+1]=(byte)(lit?255:0);b[o+2]=0;
                    }
                }
            }
            String fname="aichat_"+System.currentTimeMillis()+".bmp";
            String path=rootPath()+fname;
            FileConnection fc=(FileConnection)Connector.open(path,Connector.READ_WRITE);
            if(!fc.exists())fc.create();
            OutputStream os=fc.openOutputStream();
            os.write(b);os.flush();os.close();fc.close();
            addMessage("AI: Saved -> "+path);
        }catch(Exception e){addMessage("Error: "+e.getMessage());}
    }

    private String rootPath(){
        String custom=midlet.getSettings().getFilePath();
        if(custom!=null&&custom.length()>0){return custom.endsWith("/")?custom:custom+"/";}
        try{FileConnection fc=(FileConnection)Connector.open("file:///c:/predefgallery/predeffilereceived/",1);boolean ex=fc.exists();fc.close();if(ex)return "file:///c:/predefgallery/predeffilereceived/";}catch(Exception e){}
        try{Enumeration r=FileSystemRegistry.listRoots();if(r.hasMoreElements()){String root=(String)r.nextElement();return "file:///"+root+(root.endsWith("/")?"":"/");}}catch(Exception e){}
        return "file:///root/";
    }

    private void wi(byte[] b,int o,int v){b[o]=(byte)(v&0xFF);b[o+1]=(byte)(v>>8&0xFF);b[o+2]=(byte)(v>>16&0xFF);b[o+3]=(byte)(v>>24&0xFF);}
    private void ws(byte[] b,int o,int v){b[o]=(byte)(v&0xFF);b[o+1]=(byte)(v>>8&0xFF);}

    private void clearChat(){messages=new Vector();showQuick=true;quickSel=0;selectedMsgIdx=-1;addMessage("AI: Chat cleared.");midlet.getHistory().clearCurrent();}
    private void newChat()  {midlet.getHistory().saveCurrentSession();messages=new Vector();session="";showQuick=true;quickSel=0;selectedMsgIdx=-1;restoredSessionIndex=-1;addMessage("AI: New conversation started.");}
    private void saveChat() {
        String c=midlet.getHistory().getAllConversationsAsString();
        boolean ok=midlet.getSaveManager().saveConversation(c,"txt",midlet.getUserId());
        addMessage(ok?"AI: Saved to "+midlet.getSaveManager().getSavePath():"AI: Save failed.");
    }

    private int getMaxScroll(){
        int tot=0;
        for(int i=0;i<messages.size();i++)tot+=((ChatMessage)messages.elementAt(i)).height+8;
        return Math.max(0,tot-(barY-hdrH));
    }

    // v1.9: scroll so selectedMsgIdx bubble is visible
    private void scrollToSelected(){
        if(selectedMsgIdx<0||selectedMsgIdx>=messages.size()){repaint();return;}
        // Calculate Y position of selected message (messages paint from bottom up)
        int offsetBelow=0;
        for(int i=messages.size()-1;i>selectedMsgIdx;i--)
            offsetBelow+=((ChatMessage)messages.elementAt(i)).height+8;
        int msgH=((ChatMessage)messages.elementAt(selectedMsgIdx)).height;
        // barY-8+scrollY is top of last message paint start
        int msgY=barY-8-offsetBelow-msgH+scrollY;
        if(msgY<hdrH)       scrollY+=(hdrH-msgY+8);
        else if(msgY+msgH>barY) scrollY-=(msgY+msgH-barY+8);
        if(scrollY<0)scrollY=0;
        int max=getMaxScroll();if(scrollY>max)scrollY=max;
        repaint();
    }

    private Vector wrapTxt(String text,int maxW){
        Vector l=new Vector();int s=0,len=text.length();
        while(s<len){
            int nl=text.indexOf('\n',s);int e=(nl!=-1)?nl:len;
            String seg=text.substring(s,e);
            if(font.stringWidth(seg)<=maxW)l.addElement(seg);
            else{String tmp="";for(int i=0;i<seg.length();i++){char c=seg.charAt(i);if(font.stringWidth(tmp+c)>maxW){l.addElement(tmp);tmp=""+c;}else tmp+=c;}if(tmp.length()>0)l.addElement(tmp);}
            s=e+((nl!=-1)?1:0);
        }
        if(l.size()==0)l.addElement("");
        return l;
    }

    public String getCurrentInput()        {return currentInput;}
    public void   setCurrentInput(String s){currentInput=(s==null?"":s);repaint();}

    // v1.9: touch tap on message bubble
    protected void pointerReleased(int px,int py){
        if(!layoutDone||py>=barY)return;
        int avR=8;
        int curY=barY-8+scrollY;
        for(int i=messages.size()-1;i>=0;i--){
            ChatMessage m=(ChatMessage)messages.elementAt(i);
            curY-=m.height;
            if(curY>barY){curY-=8;continue;}
            if(curY+m.height<hdrH)break;
            if(py>=curY&&py<=curY+m.height){
                if(!m.isUser&&m.isLong){midlet.openMessageDetail(m.originalText);return;}
                selectedMsgIdx=(selectedMsgIdx==i)?-1:i;
                repaint();return;
            }
            curY-=8;
        }
    }
}


// ================================================================
// HistoryScreen v1.9
// FIX: resumeSession now properly restores past chat
// ================================================================
class HistoryScreen extends Canvas {
    private AIChatBot midlet;
    private int sel=0,scroll=0,theme,sw,sh;
    private Font font,bold,tiny;
    private int  sessionCount;
    private boolean hasCurrentSession;

    private boolean actionOpen=false;
    private int     actionSel =0;
    private static final String[] ACTIONS={
        "Resume & Continue Chat","View Full Session","Delete Session","Cancel"
    };

    public HistoryScreen(AIChatBot m){
        midlet=m;theme=m.getSettings().getThemeIndex();
        font=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        bold=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD, Font.SIZE_SMALL);
        tiny=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        refreshData();setFullScreenMode(true);
    }

    private void refreshData(){
        sessionCount=midlet.getHistory().getSessionCount();
        hasCurrentSession=!midlet.getHistory().isCurrentSessionEmpty();
    }

    protected void showNotify(){
        sw=getWidth();sh=getHeight();
        theme=midlet.getSettings().getThemeIndex();refreshData();repaint();
    }

    private int totalItems(){return sessionCount+(hasCurrentSession?1:0);}
    private boolean isCurrentItem(int idx){return hasCurrentSession&&idx==sessionCount;}

    protected void paint(Graphics g){
        if(actionOpen){paintActionSheet(g);return;}
        int t=theme,fh=font.getHeight(),bh=bold.getHeight(),sh2=tiny.getHeight();
        g.setColor(Pal.bg(t));g.fillRect(0,0,sw,sh);
        int hdrH=UI.drawHeader(g,t,sw,bold,tiny,"History",totalItems()+" sessions");

        int stH=sh2+8;
        g.setColor(Pal.surface(t));g.fillRect(0,hdrH,sw,stH);
        UI.divider(g,Pal.border(t),0,hdrH+stH,sw);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString("Msgs: "+midlet.getHistory().getTotalMessageCount()
            +"  Sessions: "+midlet.getHistory().getSessionCount(),8,hdrH+4,Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.danger(t));g.setFont(tiny);
        g.drawString("Clear >",sw-6,hdrH+4,Graphics.TOP|Graphics.RIGHT);

        int listTop=hdrH+stH+4,softH=sh2+6,listH=sh-listTop-softH;
        int cardH=bh+sh2+sh2+18,cardStep=cardH+6;
        int visible=Math.max(1,listH/cardStep);

        // FIX v1.9: correct scroll clamping
        if(sel<scroll)           scroll=sel;
        if(sel>=scroll+visible)  scroll=sel-visible+1;
        if(scroll<0)             scroll=0;
        int maxScroll=Math.max(0,totalItems()-visible);
        if(scroll>maxScroll)     scroll=maxScroll;

        g.setClip(0,listTop,sw,listH);
        if(totalItems()==0){
            g.setColor(Pal.textSec(t));g.setFont(font);
            g.drawString("No history yet.",sw/2,listTop+listH/2,Graphics.TOP|Graphics.HCENTER);
        } else {
            int y=listTop+4-scroll*cardStep;
            for(int i=0;i<totalItems();i++){
                if(y+cardH>listTop&&y<sh-softH){
                    boolean focused=(i==sel),isCur=isCurrentItem(i);
                    int cardX=focused?4:8,cardW=sw-cardX*2;
                    UI.fillRR(g,focused?Pal.surface2(t):Pal.surface(t),cardX,y,cardW,cardH,8);
                    if(focused){g.setColor(Pal.accent(t));g.fillRect(cardX,y,3,cardH);UI.drawRR(g,Pal.accent(t),cardX,y,cardW,cardH,8);}
                    int badgeR=sh2/2+3,badgeCX=cardX+8+badgeR,badgeCY=y+cardH/2;
                    UI.fillCircle(g,isCur?Pal.accent(t):Pal.surface2(t),badgeCX,badgeCY,badgeR);
                    g.setColor(isCur?Pal.userText(t):Pal.textSec(t));g.setFont(tiny);
                    String num=isCur?"~":String.valueOf(i+1);
                    g.drawString(num,badgeCX-tiny.stringWidth(num)/2,badgeCY-sh2/2,Graphics.TOP|Graphics.LEFT);
                    int textX=cardX+badgeR*2+14,textW=cardW-badgeR*2-28;
                    String title;
                    if(isCur){title="[Active] "+midlet.getHistory().getCurrentSessionTitle();if(title.length()==9)title="[Active session]";}
                    else title=midlet.getHistory().getSessionTitle(i);
                    if(font.stringWidth(title)>textW)title=title.substring(0,Math.max(1,textW/font.charWidth('m')-2))+"..";
                    g.setColor(focused?Pal.textPri(t):(isCur?Pal.accent(t):Pal.textPri(t)));g.setFont(focused?bold:font);
                    g.drawString(title,textX,y+5,Graphics.TOP|Graphics.LEFT);
                    String preview=isCur?midlet.getHistory().getCurrentSessionPreview():midlet.getHistory().getSessionPreview(i);
                    if(preview.length()>0){
                        if(tiny.stringWidth(preview)>textW)preview=preview.substring(0,Math.max(1,textW/tiny.charWidth('m')-2))+"..";
                        g.setColor(Pal.textSec(t));g.setFont(tiny);
                        g.drawString(preview,textX,y+5+bh+3,Graphics.TOP|Graphics.LEFT);
                    }
                    int cnt=isCur?midlet.getHistory().getCurrentSessionSize():midlet.getHistory().getSessionMessageCount(i);
                    g.setColor(Pal.textSec(t));g.setFont(tiny);
                    g.drawString(cnt+" msg"+(cnt!=1?"s":""),textX,y+5+bh+3+sh2+2,Graphics.TOP|Graphics.LEFT);
                    if(focused){
                        String badge=isCur?"Open >":"Resume >";
                        int bW=tiny.stringWidth(badge)+10,bH=sh2+4;
                        int bX=cardX+cardW-bW-6,bY=y+cardH-bH-4;
                        UI.fillRR(g,Pal.accent(t),bX,bY,bW,bH,bH/2);
                        g.setColor(Pal.userText(t));g.setFont(tiny);
                        g.drawString(badge,bX+5,bY+2,Graphics.TOP|Graphics.LEFT);
                    }
                }
                y+=cardStep;
            }
        }
        g.setClip(0,0,sw,sh);
        UI.drawFooter(g,t,sw,sh,tiny,"2/8:scroll  FIRE:actions  #:del  *:clear  LSK:back");
    }

    private void paintActionSheet(Graphics g){
        int t=theme,fh=font.getHeight(),sh2=tiny.getHeight();
        g.setColor(Pal.bg(t)&0x888888);g.fillRect(0,0,sw,sh);
        boolean isCur=isCurrentItem(sel);
        String[] acts=isCur?new String[]{"Open Current Chat","Cancel"}:ACTIONS;
        int iH=fh+14,shH=acts.length*iH+36,shY=sh-shH;
        UI.fillRR(g,Pal.surface(t),0,shY,sw,shH,14);
        UI.drawRR(g,Pal.border(t), 0,shY,sw,shH,14);
        g.setColor(Pal.border(t));g.fillRoundRect(sw/2-16,shY+7,32,3,3,3);
        String titleStr=isCur?"Active session":midlet.getHistory().getSessionTitle(sel);
        if(titleStr.length()>30)titleStr=titleStr.substring(0,28)+"..";
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString(titleStr,sw/2,shY+14,Graphics.TOP|Graphics.HCENTER);
        UI.divider(g,Pal.border(t),8,shY+14+sh2+3,sw-16);
        int iy=shY+22+sh2;
        for(int i=0;i<acts.length;i++){
            boolean focused=(i==actionSel);
            if(focused){UI.fillRR(g,Pal.surface2(t),8,iy,sw-16,iH,6);g.setColor(Pal.accent(t));g.fillRect(8,iy,3,iH);}
            int col;
            if(acts[i].indexOf("Delete")>=0)col=Pal.danger(t);
            else if(focused&&(acts[i].indexOf("Resume")>=0||acts[i].indexOf("Open")>=0))col=Pal.accent(t);
            else col=focused?Pal.textPri(t):Pal.textSec(t);
            g.setColor(col);g.setFont(focused?bold:font);
            g.drawString(acts[i],20,iy+(iH-fh)/2,Graphics.TOP|Graphics.LEFT);
            iy+=iH;
        }
    }

    protected void keyPressed(int k){
        int a=getGameAction(k);
        if(actionOpen){doActionKey(k,a);repaint();return;}
        if      (a==UP  ||k==Canvas.KEY_NUM2)sel=Math.max(0,sel-1);
        else if (a==DOWN||k==Canvas.KEY_NUM8)sel=Math.min(totalItems()-1,sel+1);
        else if (a==FIRE||k==Canvas.KEY_NUM5||k==-5||k==10){if(totalItems()>0){actionSel=0;actionOpen=true;}}
        // PATCH: KEY_POUND (#) deletes the selected session directly (with confirmation)
        else if (k==Canvas.KEY_POUND){
            if(totalItems()>0&&!isCurrentItem(sel)){
                confirmDeleteSession(sel);
                return;
            }
        }
        else if (k==Canvas.KEY_STAR)midlet.confirmClearHistory();
        else if (k==-6||k==-21)midlet.showMainMenu();
        repaint();
    }

    // PATCH: shows a LCDUI confirmation dialog then deletes the session
    private void confirmDeleteSession(final int idx){
        List dlg=new List("Delete session "+(idx+1)+"?",List.EXCLUSIVE);
        dlg.append("Yes, delete",null);
        dlg.append("No, keep it",null);
        Command ok=new Command("OK",Command.OK,1);
        dlg.addCommand(ok);
        final HistoryScreen self=this;
        dlg.setCommandListener(new CommandListener(){
            public void commandAction(Command c,Displayable d){
                if(((List)d).getSelectedIndex()==0){
                    midlet.getHistory().deleteSessionAt(idx);
                    midlet.getHistory().saveToRMS(midlet.getHistStore());
                    refreshData();
                    if(sel>=totalItems()&&sel>0)sel--;
                }
                midlet.getDisplay().setCurrent(self);
                repaint();
            }
        });
        midlet.getDisplay().setCurrent(dlg);
    }

    private void doActionKey(int k,int a){
        boolean isCur=isCurrentItem(sel);
        int maxAct=isCur?2:ACTIONS.length;
        if      (a==UP  ||k==Canvas.KEY_NUM2)actionSel=(actionSel-1+maxAct)%maxAct;
        else if (a==DOWN||k==Canvas.KEY_NUM8)actionSel=(actionSel+1)%maxAct;
        else if (a==FIRE||k==Canvas.KEY_NUM5||k==-5||k==10)execAction(isCur);
        else if (k==-7||k==-22||a==LEFT)actionOpen=false;
    }

    private void execAction(boolean isCur){
        actionOpen=false;
        if(isCur){
            // FIX v2.0: return to existing active chat, not a new one
            if(actionSel==0){
                if(midlet.getChatCanvas()!=null)
                    midlet.getDisplay().setCurrent(midlet.getChatCanvas());
                else
                    midlet.showChat();
            }
            return;
        }
        switch(actionSel){
            case 0: // v1.9: FIX - resume properly restores session
                midlet.resumeSession(sel);
                break;
            case 1:viewSession(sel);break;
            case 2:
                midlet.getHistory().deleteSessionAt(sel);
                midlet.getHistory().saveToRMS(midlet.getHistStore());
                refreshData();
                if(sel>=totalItems()&&sel>0)sel--;
                repaint();break;
            case 3:break;
        }
    }

    private void viewSession(int idx){
        String content=midlet.getHistory().getSessionAt(idx);
        if(content!=null&&content.length()>0)midlet.showCopyBox(content,"Session "+(idx+1));
    }
}


// ================================================================
// AIToolsScreen v1.9
// FIX: scrolling now correct with persistent scrollOff
// ================================================================
class AIToolsScreen extends Canvas {
    private AIChatBot midlet;
    private int sel=0,scrollOff=0,theme,sw,sh;
    private Font font,bold,tiny;

    private static final String[] TOOL_NAMES={
        "Summarize","Translate","Explain","Fix Grammar","Code Helper",
        "Brainstorm","File Analyse","Web Search","Q&A Mode","Story Writer"
    };
    private static final String[] TOOL_ICONS={"=","T","?","G","<>","*","F","W","Q","S"};
    private static final String[] TOOL_DESC={
        "Summarize text or last AI reply",
        "Translate text to another language",
        "Explain a concept step by step",
        "Fix grammar and improve writing",
        "Get help with code or debugging",
        "Brainstorm ideas on a topic",
        "Analyse a file from your device",
        "Search and answer from the web",
        "Ask questions about any topic",
        "Continue or write a story"
    };
    private static final String[] TOOL_PROMPTS={
        "Please summarize the following text concisely:\n",
        "Translate the following text to English (specify target language if needed):\n",
        "Please explain this concept clearly in simple step-by-step terms:\n",
        "Please fix the grammar and improve the writing style of this text:\n",
        "Help me with this code. Explain what it does and fix any issues:\n",
        "Brainstorm 5 creative ideas about:\n",
        "[FILE_TOOL]",
        "Search: ",
        "Please answer this question thoroughly:\n",
        "Continue this story or write a short story about:\n"
    };

    private boolean inputStep=false;
    private String  inputText="";
    private boolean cursorOn =true;
    private Timer   blinkTimer;
    private int     animTick =0;

    public AIToolsScreen(AIChatBot m){
        midlet=m;theme=m.getSettings().getThemeIndex();
        font=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        bold=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD, Font.SIZE_SMALL);
        tiny=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        setFullScreenMode(true);
        blinkTimer=new Timer();
        blinkTimer.schedule(new TimerTask(){public void run(){cursorOn=!cursorOn;animTick++;repaint();}},0,500);
    }

    protected void showNotify(){
        sw=getWidth();sh=getHeight();
        theme=midlet.getSettings().getThemeIndex();repaint();
    }

    protected void paint(Graphics g){
        int t=theme,fh=font.getHeight(),bh=bold.getHeight(),sh2=tiny.getHeight();
        g.setColor(Pal.bg(t));g.fillRect(0,0,sw,sh);
        if(inputStep){paintInputStep(g,t);return;}

        int hdrH=UI.drawHeader(g,t,sw,bold,tiny,"AI Tools",null);
        int subH=sh2+8;
        g.setColor(Pal.surface(t));g.fillRect(0,hdrH,sw,subH);
        UI.divider(g,Pal.border(t),0,hdrH+subH,sw);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString("Select a tool to use in chat",sw/2,hdrH+4,Graphics.TOP|Graphics.HCENTER);

        int listTop=hdrH+subH+4,softH=sh2+6,listH=sh-listTop-softH;
        int cardH=bh+sh2+12,step=cardH+4;
        int visible=Math.max(1,listH/step);

        // FIX v1.9: correct persistent scroll for AITools
        if(sel<scrollOff)          scrollOff=sel;
        if(sel>=scrollOff+visible) scrollOff=sel-visible+1;
        if(scrollOff<0)            scrollOff=0;
        int maxScroll=Math.max(0,TOOL_NAMES.length-visible);
        if(scrollOff>maxScroll)    scrollOff=maxScroll;

        g.setClip(0,listTop,sw,listH);
        int y=listTop+2-scrollOff*step;
        for(int i=0;i<TOOL_NAMES.length;i++){
            if(y+cardH>listTop&&y<sh-softH){
                boolean foc=(i==sel);
                int cx=foc?4:8,cw=sw-cx*2;
                UI.fillRR(g,foc?Pal.surface2(t):Pal.surface(t),cx,y,cw,cardH,8);
                if(foc){g.setColor(Pal.accent(t));g.fillRect(cx,y,3,cardH);}
                int icR=sh2/2+3,icCX=cx+8+icR,icCY=y+cardH/2;
                UI.fillCircle(g,foc?Pal.accent(t):Pal.surface2(t),icCX,icCY,icR);
                g.setColor(foc?Pal.userText(t):Pal.textSec(t));g.setFont(tiny);
                String ic=TOOL_ICONS[i];
                g.drawString(ic,icCX-tiny.stringWidth(ic)/2,icCY-sh2/2,Graphics.TOP|Graphics.LEFT);
                int tx=cx+icR*2+14;
                g.setColor(foc?Pal.textPri(t):Pal.textSec(t));g.setFont(foc?bold:font);
                g.drawString(TOOL_NAMES[i],tx,y+4,Graphics.TOP|Graphics.LEFT);
                g.setColor(Pal.textSec(t));g.setFont(tiny);
                g.drawString(TOOL_DESC[i],tx,y+4+bh+2,Graphics.TOP|Graphics.LEFT);
                if(foc){g.setColor(Pal.accent(t));g.setFont(bold);g.drawString(">",cx+cw-12,y+cardH/2-bh/2,Graphics.TOP|Graphics.LEFT);}
            }
            y+=step;
        }
        g.setClip(0,0,sw,sh);
        UI.drawFooter(g,t,sw,sh,tiny,"2/8:select  FIRE:use  LSK:back");
    }

    private void paintInputStep(Graphics g,int t){
        int fh=font.getHeight(),bh=bold.getHeight(),sh2=tiny.getHeight();
        int hdrH=UI.drawHeader(g,t,sw,bold,tiny,TOOL_NAMES[sel],"FIRE=send");
        int y=hdrH+8;
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString(TOOL_DESC[sel],sw/2,y,Graphics.TOP|Graphics.HCENTER);
        y+=sh2+8;
        UI.divider(g,Pal.border(t),8,y,sw-16);y+=6;
        int boxH=sh-y-(sh2+8)-8;
        UI.fillRR(g,Pal.surface(t),6,y,sw-12,boxH,8);
        UI.drawRR(g,Pal.accent(t), 6,y,sw-12,boxH,8);
        if(inputText.length()==0){
            g.setColor(Pal.textSec(t));g.setFont(font);
            g.drawString("Type your text here...",12,y+6,Graphics.TOP|Graphics.LEFT);
        } else {
            int ty2=y+6,maxW=sw-32;
            String rest=inputText;
            while(rest.length()>0&&ty2+fh<y+boxH-4){
                int nl=rest.indexOf('\n');
                String seg=(nl>=0)?rest.substring(0,nl):rest;
                while(seg.length()>0){
                    int fit=seg.length();
                    while(fit>0&&font.stringWidth(seg.substring(0,fit))>maxW)fit--;
                    if(fit==0)fit=1;
                    g.setColor(Pal.textPri(t));g.setFont(font);
                    g.drawString(seg.substring(0,fit),12,ty2,Graphics.TOP|Graphics.LEFT);
                    ty2+=fh+2;seg=seg.substring(fit);
                }
                if(nl>=0)rest=rest.substring(nl+1);else break;
            }
            if(cursorOn&&ty2<y+boxH-4){g.setColor(Pal.accent(t));g.fillRect(12,ty2,2,fh);}
        }
        UI.drawFooter(g,t,sw,sh,tiny,"FIRE:send  *:native input  RSK:del  LSK:back");
    }

    protected void keyPressed(int k){
        int a=getGameAction(k);
        if(inputStep){doInputKey(k,a);repaint();return;}
        if      (a==UP  ||k==Canvas.KEY_NUM2) sel=(sel-1+TOOL_NAMES.length)%TOOL_NAMES.length;
        else if (a==DOWN||k==Canvas.KEY_NUM8) sel=(sel+1)%TOOL_NAMES.length;
        else if (a==FIRE||k==Canvas.KEY_NUM5||k==-5||k==10) activateTool(sel);
        else if (k==-6||k==-21){blinkTimer.cancel();midlet.showMainMenu();}
        repaint();
    }

    private void activateTool(int idx){
        String p=TOOL_PROMPTS[idx];
        if(p.equals("[FILE_TOOL]")){blinkTimer.cancel();midlet.showFiles();return;}
        inputText="";inputStep=true;
    }

    private void doInputKey(int k,int a){
        if(a==FIRE||k==Canvas.KEY_NUM5||k==-5||k==10){
            if(inputText.trim().length()>0){
                String full=TOOL_PROMPTS[sel]+inputText.trim();
                blinkTimer.cancel();midlet.showChatWithInput(full);
            }return;
        }
        if(k==-6||k==-21){inputStep=false;return;}
        if(k==-7||k==-22){if(inputText.length()>0)inputText=inputText.substring(0,inputText.length()-1);return;}
        if(k==Canvas.KEY_STAR||k==Canvas.KEY_POUND){midlet.openNativeToolInput(inputText,this);return;}
        if(k==Canvas.KEY_NUM0){inputText+=' ';return;}
    }

    public void setInputText(String text){if(text!=null)inputText=text;repaint();}
}


// ================================================================
// SettingsScreen v1.9
// NEW: custom file path row + connection path for save/read/write
// ================================================================
class SettingsScreen extends Canvas {
    private AIChatBot midlet;
    private int sel=0,sw,sh,scrollOff=0;
    private Font font,bold,tiny;

    private int     timeout;
    private boolean ctxEnabled;
    private int     maxCtx;
    private boolean proxyEnabled;
    private int     themeIdx;
    private boolean aiTools;
    private boolean autoSave;
    private String  filePath="";
    private String  apiBase ="";  // v1.9: custom API/connection base URL

    // v1.9: 10 rows (added API Base Path)
    private static final int NROWS=10;

    public SettingsScreen(AIChatBot m){
        midlet=m;
        font=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        bold=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD, Font.SIZE_SMALL);
        tiny=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        syncFromSettings();setFullScreenMode(true);
    }

    private void syncFromSettings(){
        timeout      =midlet.getSettings().getTimeout()/1000;
        ctxEnabled   =midlet.getSettings().isContextEnabled();
        maxCtx       =midlet.getSettings().getMaxContext();
        proxyEnabled =midlet.getSettings().isProxyEnabled();
        themeIdx     =midlet.getSettings().getThemeIndex();
        aiTools      =midlet.getSettings().isAIToolsEnabled();
        autoSave     =midlet.getSettings().isAutoSaveSession();
        filePath     =midlet.getSettings().getFilePath();
        apiBase      =midlet.getSettings().getApiBase();
    }

    private void applyAndSave(){
        midlet.getSettings().setTimeoutSilent(timeout*1000);
        midlet.getSettings().setContextEnabledSilent(ctxEnabled);
        midlet.getSettings().setMaxContextSilent(maxCtx);
        midlet.getSettings().setProxyEnabledSilent(proxyEnabled);
        midlet.getSettings().setThemeIndexSilent(themeIdx);
        midlet.getSettings().setAIToolsEnabledSilent(aiTools);
        midlet.getSettings().setAutoSaveSilent(autoSave);
        midlet.getSettings().setFilePathSilent(filePath);
        midlet.getSettings().setApiBaseSilent(apiBase);
        midlet.getSettings().saveAll();
        midlet.savePrefs();
    }

    protected void showNotify(){sw=getWidth();sh=getHeight();repaint();}

    protected void paint(Graphics g){
        int t=themeIdx;
        int fh=font.getHeight(),bh=bold.getHeight(),sh2=tiny.getHeight();
        g.setColor(Pal.bg(t));g.fillRect(0,0,sw,sh);
        int hdrH=UI.drawHeader(g,t,sw,bold,tiny,"Settings","Back=save");

        String[] labs={
            "Timeout","Context","Max ctx","Proxy","Theme",
            "AI Tools","Auto Save","File Path","API Base","Profile"
        };
        String fpDisp=filePath.length()>0
            ?(filePath.length()>16?filePath.substring(filePath.length()-16):filePath):"(default)";
        String abDisp=apiBase.length()>0
            ?(apiBase.length()>16?apiBase.substring(apiBase.length()-16):apiBase):"(default)";
        String[] vals={
            timeout+"s",
            ctxEnabled?"ON":"OFF",
            ""+maxCtx,
            proxyEnabled?"ON":"OFF",
            Pal.name(themeIdx)+" ("+(themeIdx+1)+"/9)",
            aiTools?"ON":"OFF",
            autoSave?"ON":"OFF",
            fpDisp,
            abDisp,
            ">"
        };

        int rowH=fh+18,y=hdrH+10;
        int maxVisible=(sh-y-(sh2+8)-20)/(rowH+4);
        if(maxVisible<1)maxVisible=1;
        if(sel<scrollOff)             scrollOff=sel;
        if(sel>=scrollOff+maxVisible) scrollOff=sel-maxVisible+1;
        if(scrollOff<0)               scrollOff=0;

        g.setClip(0,hdrH,sw,sh-hdrH-sh2-8-20);
        for(int i=0;i<labs.length;i++){
            boolean foc=(i==sel);
            int ry=y+(i-scrollOff)*(rowH+4);
            if(ry+rowH<hdrH||ry>sh-sh2-8-20)continue;
            if(foc){UI.fillRR(g,Pal.surface(t),4,ry,sw-8,rowH,8);g.setColor(Pal.accent(t));g.fillRect(4,ry,3,rowH);}
            g.setColor(foc?Pal.textPri(t):Pal.textSec(t));g.setFont(foc?bold:font);
            g.drawString(labs[i],16,ry+(rowH-fh)/2,Graphics.TOP|Graphics.LEFT);
            if(i<labs.length-1){
                int vW=tiny.stringWidth(vals[i])+24,vH=sh2+8,vX=sw-vW-8,vY=ry+(rowH-vH)/2;
                UI.fillRR(g,foc?Pal.accent(t):Pal.surface2(t),vX,vY,vW,vH,vH/2);
                g.setColor(foc?Pal.userText(t):Pal.textSec(t));g.setFont(tiny);
                // For path rows show FIRE hint
                boolean isPathRow=(i==7||i==8);
                g.drawString(isPathRow?(foc?"FIRE:edit":vals[i]):(foc?"< "+vals[i]+" >":vals[i]),
                    vX+8,vY+(vH-sh2)/2,Graphics.TOP|Graphics.LEFT);
            } else {
                g.setColor(foc?Pal.accent(t):Pal.textSec(t));g.setFont(bold);
                g.drawString(">",sw-18,ry+(rowH-bh)/2,Graphics.TOP|Graphics.LEFT);
            }
        }
        g.setClip(0,0,sw,sh);

        // Theme color strip
        int stripY=sh-(sh2+8)-12,stripH=6,stripW=(sw-16)/9;
        for(int i=0;i<9;i++){
            g.setColor(Pal.accent(i));g.fillRect(8+i*stripW,stripY,stripW-2,stripH);
            if(i==themeIdx){g.setColor(Pal.textPri(themeIdx));g.drawRect(8+i*stripW,stripY,stripW-2,stripH);}
        }

        UI.drawFooter(g,t,sw,sh,tiny,"L/R:change  2/8:select  LSK:save+back");
    }

    protected void keyPressed(int k){
        int a=getGameAction(k);
        if      (a==UP  ||k==Canvas.KEY_NUM2) sel=(sel-1+NROWS)%NROWS;
        else if (a==DOWN||k==Canvas.KEY_NUM8) sel=(sel+1)%NROWS;
        else if (a==LEFT) {adj(-1);applyAndSave();}
        else if (a==RIGHT){adj(1); applyAndSave();}
        else if (a==FIRE||k==Canvas.KEY_NUM5||k==-5||k==10){
            if(sel==9){applyAndSave();midlet.showProfile();return;}
            if(sel==7){applyAndSave();midlet.openSettingsPathInput(filePath,this);return;}
            if(sel==8){applyAndSave();openApiBaseInput();return;}
            adj(1);applyAndSave();
        }
        else if(k==-6||k==-21){applyAndSave();midlet.showMainMenu();return;}
        repaint();
    }

    // v1.9: open TextBox to edit custom API/connection base URL
    private void openApiBaseInput(){
        TextBox tb=new TextBox(
            "API/Connection Base URL",
            apiBase!=null?apiBase:"",300,TextField.ANY);
        Command ok=new Command("Save",  Command.OK,  1);
        Command ca=new Command("Cancel",Command.BACK,2);
        tb.addCommand(ok);tb.addCommand(ca);
        final SettingsScreen self=this;
        tb.setCommandListener(new CommandListener(){
            public void commandAction(Command cmd,Displayable d){
                if(cmd.getCommandType()==Command.OK){
                    apiBase=((TextBox)d).getString().trim();
                    applyAndSave();
                }
                midlet.getDisplay().setCurrent(self);
            }
        });
        midlet.getDisplay().setCurrent(tb);
    }

    private void adj(int d){
        switch(sel){
            case 0:timeout=Math.max(5,Math.min(120,timeout+d*5));break;
            case 1:ctxEnabled=!ctxEnabled;break;
            case 2:maxCtx=Math.max(1,Math.min(20,maxCtx+d));break;
            case 3:proxyEnabled=!proxyEnabled;break;
            case 4:themeIdx=(themeIdx+d+9)%9;break;
            case 5:aiTools=!aiTools;break;
            case 6:autoSave=!autoSave;break;
            // 7=filePath, 8=apiBase handled via TextBox
        }
    }

    public void setFilePath(String p){if(p!=null)filePath=p.trim();applyAndSave();repaint();}
}


// ================================================================
// AboutScreen
// ================================================================
class AboutScreen extends Canvas {
    private AIChatBot midlet;
    private int theme,sw,sh;
    private Font font,bold,tiny;
    // PATCH: scroll support for AboutScreen
    private int scrollOffset=0;

    public AboutScreen(AIChatBot m){
        midlet=m;theme=m.getSettings().getThemeIndex();
        font=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        bold=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD, Font.SIZE_SMALL);
        tiny=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        setFullScreenMode(true);
    }

    protected void showNotify(){
        sw=getWidth();sh=getHeight();
        theme=midlet.getSettings().getThemeIndex();scrollOffset=0;repaint();
    }

    protected void paint(Graphics g){
        int t=theme,fh=font.getHeight(),bh=bold.getHeight(),sh2=tiny.getHeight();
        g.setColor(Pal.bg(t));g.fillRect(0,0,sw,sh);
        int hdrH=UI.drawHeader(g,t,sw,bold,tiny,"About",null);
        int ftrH=sh2+8;
        // PATCH: compute content height for scroll
        int rowH=fh+12;
        int cardH=40;
        int contentH=cardH+10+8*(rowH+2)+10;
        int availH=sh-hdrH-ftrH;
        int maxScroll=Math.max(0,contentH-availH);
        if(scrollOffset>maxScroll)scrollOffset=maxScroll;
        if(scrollOffset<0)scrollOffset=0;
        g.setClip(0,hdrH,sw,sh-hdrH-ftrH);
        int y=hdrH+10-scrollOffset;
        UI.fillRR(g,Pal.surface(t),8,y,sw-16,cardH,10);
        int avR=14,avCX=8+avR+8,avCY=y+cardH/2;
        UI.fillCircle(g,Pal.accent(t),avCX,avCY,avR);
        g.setColor(Pal.userText(t));g.setFont(bold);
        g.drawString("AI",avCX-bold.stringWidth("AI")/2,avCY-bh/2,Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textPri(t));g.setFont(bold);
        g.drawString(AIChatBot.APP_NAME+" "+AIChatBot.VERSION,avCX+avR+8,y+6,Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString("Build "+AIChatBot.BUILD+" | Claude API",avCX+avR+8,y+6+bh+2,Graphics.TOP|Graphics.LEFT);
        y+=cardH+10;
        String uid=midlet.getUserId();
        String uidDisplay=uid.length()>12?uid.substring(0,10)+"..":uid;
        String[][] info={
            {"Name",  midlet.getUserName()},
            {"Role",  midlet.getUserRole()},
            {"Lang",  midlet.getUserLang()},
            {"ID",    uidDisplay},
            {"Theme", Pal.name(midlet.getSettings().getThemeIndex())},
            {"Proxy", midlet.getSettings().isProxyEnabled()?"ON":"OFF"},
            {"Files", midlet.getSaveManager().isFileAPIAvailable()?"Yes":"No"},
            {"Msgs",  ""+midlet.getHistory().getTotalMessageCount()},
        };
        for(int i=0;i<info.length;i++){
            int ry=y+i*(rowH+2);
            if(i%2==0){g.setColor(Pal.surface(t));g.fillRect(8,ry,sw-16,rowH);}
            g.setColor(Pal.textSec(t));g.setFont(tiny);
            g.drawString(info[i][0],16,ry+(rowH-sh2)/2,Graphics.TOP|Graphics.LEFT);
            g.setColor(Pal.textPri(t));g.setFont(font);
            g.drawString(info[i][1],sw-12,ry+(rowH-fh)/2,Graphics.TOP|Graphics.RIGHT);
        }
        g.setClip(0,0,sw,sh);
        boolean canScroll=(contentH>availH);
        UI.drawFooter(g,t,sw,sh,tiny,canScroll?"2/8:scroll  FIRE/LSK:back":"FIRE or LSK: back");
    }

    protected void keyPressed(int k){
        int a=getGameAction(k);
        if(k==-6||k==-21||a==FIRE)midlet.showMainMenu();
        else {
            // PATCH: scroll About content with 2/8 keys
            int step=font.getHeight()*3;
            if      (a==UP  ||k==Canvas.KEY_NUM2){scrollOffset-=step;if(scrollOffset<0)scrollOffset=0;}
            else if (a==DOWN||k==Canvas.KEY_NUM8){scrollOffset+=step;}
        }
        repaint();
    }
}


// ================================================================
// FileViewerScreen
// ================================================================
class FileViewerScreen extends Canvas {
    private AIChatBot midlet;
    private int theme,sw,sh;
    private Font font,bold,tiny;
    private Vector entries=new Vector();
    private int sel=0,scroll=0;
    private String curPath=null,status="Loading...";
    private boolean loading=true;

    public FileViewerScreen(AIChatBot m){
        midlet=m;theme=m.getSettings().getThemeIndex();
        font=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        bold=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD, Font.SIZE_SMALL);
        tiny=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        setFullScreenMode(true);
    }

    protected void showNotify(){
        sw=getWidth();sh=getHeight();
        theme=midlet.getSettings().getThemeIndex();loadRoots();
    }

    private void loadRoots(){
        entries=new Vector();sel=0;scroll=0;curPath=null;loading=true;repaint();
        new Thread(new Runnable(){public void run(){
            try{
                Enumeration r=FileSystemRegistry.listRoots();
                while(r.hasMoreElements()){
                    String rt=(String)r.nextElement();
                    entries.addElement(new String[]{"["+rt+"]","dir","file:///"+rt});
                }
                status=entries.size()>0?"Pick a root:":"No file system";
            }catch(Exception e){status="Error: "+e.getMessage();}
            loading=false;repaint();
        }}).start();
    }

    private void loadDir(final String path){
        entries=new Vector();sel=0;scroll=0;loading=true;status="...";repaint();
        new Thread(new Runnable(){public void run(){
            FileConnection fc=null;
            try{
                entries.addElement(new String[]{".. (go up)","dir",null});
                fc=(FileConnection)Connector.open(path,Connector.READ);
                if(fc.isDirectory()){
                    Enumeration lst=fc.list();
                    while(lst.hasMoreElements()){
                        String n=(String)lst.nextElement();
                        boolean d=n.endsWith("/");
                        entries.addElement(new String[]{d?"["+n.substring(0,n.length()-1)+"]":n,d?"dir":"file",path+n});
                    }
                }
                curPath=path;status=path.length()>28?"..."+path.substring(path.length()-25):path;
            }catch(Exception e){status="Error: "+e.getMessage();}
            finally{try{if(fc!=null)fc.close();}catch(Exception x){}loading=false;repaint();}
        }}).start();
    }

    private void readFile(final String path){
        status="Reading...";loading=true;repaint();
        new Thread(new Runnable(){public void run(){
            FileConnection fc=null;InputStream is=null;
            try{
                fc=(FileConnection)Connector.open(path,Connector.READ);
                long sz=fc.fileSize();is=fc.openInputStream();
                // v1.9: read up to 8000 chars for AI analysis
                int max=8000;byte[] buf=new byte[(int)Math.min(sz,max)];
                int n=is.read(buf);String raw=n>0?new String(buf,0,n):"(empty)";
                StringBuffer sb=new StringBuffer();
                for(int i=0;i<raw.length();i++){char c=raw.charAt(i);if((c>=32&&c<=126)||(c>=160&&c<=255)||c=='\n'||c=='\r')sb.append(c);}
                String fname=path.substring(path.lastIndexOf('/')+1);
                String prompt="File: "+fname+"\nSize: "+sz+" bytes\n---\n"+sb.toString()+"\n---\nBriefly describe this file.";
                midlet.showChatWithInput(prompt);
            }catch(Exception e){status="Read error: "+e.getMessage();loading=false;repaint();}
            finally{try{if(is!=null)is.close();}catch(Exception x){}try{if(fc!=null)fc.close();}catch(Exception x){}}
        }}).start();
    }

    protected void paint(Graphics g){
        int t=theme,fh=font.getHeight(),bh=bold.getHeight(),sh2=tiny.getHeight();
        g.setColor(Pal.bg(t));g.fillRect(0,0,sw,sh);
        int hdrH=UI.drawHeader(g,t,sw,bold,tiny,"File Browser",null);
        int pathH=sh2+8;
        g.setColor(Pal.surface(t));g.fillRect(0,hdrH,sw,pathH);
        UI.divider(g,Pal.border(t),0,hdrH+pathH,sw);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString(status,6,hdrH+4,Graphics.TOP|Graphics.LEFT);
        int listTop=hdrH+pathH+4,softH=sh2+6,listH=sh-listTop-softH,iH=fh+12;
        if(loading){
            g.setColor(Pal.accent(t));g.setFont(font);
            g.drawString("Loading...",sw/2,listTop+listH/2,Graphics.TOP|Graphics.HCENTER);
        } else if(entries.size()==0){
            g.setColor(Pal.textSec(t));g.setFont(font);
            g.drawString("(empty)",sw/2,listTop+listH/2,Graphics.TOP|Graphics.HCENTER);
        } else {
            g.setClip(0,listTop,sw,listH);
            int y=listTop-scroll;
            for(int i=0;i<entries.size();i++){
                String[] e=(String[])entries.elementAt(i);
                boolean isDir=e[1].equals("dir"),foc=(i==sel);
                if(foc){UI.fillRR(g,Pal.surface(t),4,y,sw-8,iH,6);g.setColor(Pal.accent(t));g.fillRect(4,y,3,iH);}
                g.setColor(isDir?Pal.accent(t):(foc?Pal.textPri(t):Pal.textSec(t)));g.setFont(foc?bold:font);
                String name=e[0];int maxNW=sw-28;
                if(font.stringWidth(name)>maxNW)name=name.substring(0,Math.max(1,maxNW/font.charWidth('m')))+"..";
                g.drawString(isDir?"> ":"  ",8,y+(iH-fh)/2,Graphics.TOP|Graphics.LEFT);
                g.drawString(name,22,y+(iH-fh)/2,Graphics.TOP|Graphics.LEFT);
                y+=iH;
            }
            g.setClip(0,0,sw,sh);
        }
        UI.drawFooter(g,t,sw,sh,tiny,"FIRE:open  LSK:back  2/8:scroll");
    }

    protected void keyPressed(int k){
        int a=getGameAction(k);
        int iH=font.getHeight()+12,listH=sh-bold.getHeight()-tiny.getHeight()*2-24,vis=Math.max(1,listH/iH);
        if      (a==UP  ||k==Canvas.KEY_NUM2){sel=Math.max(0,sel-1);if(sel*iH<scroll)scroll=sel*iH;}
        else if (a==DOWN||k==Canvas.KEY_NUM8){sel=Math.min(entries.size()-1,sel+1);if(sel>=(scroll/iH)+vis)scroll=Math.max(0,(sel-vis+1)*iH);}
        else if (a==FIRE||k==Canvas.KEY_NUM5||k==-5||k==10){
            if(entries.size()>0){
                String[] e=(String[])entries.elementAt(sel);
                if(e[2]==null){
                    if(curPath!=null){String p=curPath;if(p.endsWith("/"))p=p.substring(0,p.length()-1);int last=p.lastIndexOf('/');if(last>8)loadDir(p.substring(0,last+1));else loadRoots();}
                    else loadRoots();
                } else if(e[1].equals("dir"))loadDir(e[2]);
                else readFile(e[2]);
            }
        }
        else if(k==-6||k==-21){
            if(curPath!=null){String p=curPath;if(p.endsWith("/"))p=p.substring(0,p.length()-1);int last=p.lastIndexOf('/');if(last>8)loadDir(p.substring(0,last+1));else loadRoots();}
            else midlet.showMainMenu();
        }
        repaint();
    }
}


// ================================================================
// FullMessageCanvas v1.9 - paginated reader
// ================================================================
class FullMessageCanvas extends Canvas {
    private AIChatBot midlet;
    private String    message;
    private int       theme,sw,sh;
    private Font      font,bold,tiny;
    private Vector    lines       =new Vector();
    private int       linesPerPage=0;
    private int       currentPage =0;
    private boolean   ready       =false;
    // BUG-4 FIX: single source of truth for top padding inside content area.
    // buildLines() and paint() must use the same value or linesPerPage is
    // miscalculated and the last line bleeds into the footer.
    private static final int CONTENT_TOP_PAD=8;

    public FullMessageCanvas(AIChatBot m,String msg){
        midlet=m;message=(msg!=null)?msg:"";
        theme=m.getSettings().getThemeIndex();
        font=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        bold=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD, Font.SIZE_SMALL);
        tiny=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        setFullScreenMode(true);
    }

    protected void showNotify(){
        sw=getWidth();sh=getHeight();
        theme=midlet.getSettings().getThemeIndex();
        buildLines();repaint();
    }

    private void buildLines(){
        lines=new Vector();
        // FIX v2.0: word-boundary aware wrap with accurate maxW
        int maxW=sw-22,s=0,len=message.length();
        while(s<len){
            int nl=message.indexOf('\n',s);int e=(nl>=0)?nl:len;
            String seg=message.substring(s,e);
            if(seg.length()==0){lines.addElement("");}
            else{
                while(seg.length()>0){
                    // Find maximum chars that fit
                    int fit=seg.length();
                    while(fit>0&&font.stringWidth(seg.substring(0,fit))>maxW)fit--;
                    if(fit==0)fit=1;
                    // FIX v2.0: prefer word boundary break
                    if(fit<seg.length()){
                        int wb=fit;
                        while(wb>1&&seg.charAt(wb-1)!=' ')wb--;
                        if(wb>1)fit=wb; // break at space
                    }
                    lines.addElement(seg.substring(0,fit));
                    // Skip leading space on next line
                    seg=seg.substring(fit);
                    if(seg.length()>0&&seg.charAt(0)==' ')seg=seg.substring(1);
                }
            }
            s=(nl>=0)?nl+1:len;
        }
        if(lines.size()==0)lines.addElement("");
        // FIX v2.0: precise linesPerPage - use 8px top pad + 2px line spacing
        int hdrH2=bold.getHeight()+14,ftrH2=tiny.getHeight()+10;
        int lineH=font.getHeight()+2;
        int usable=sh-hdrH2-ftrH2-CONTENT_TOP_PAD; // top padding inside content area
        linesPerPage=Math.max(1,usable/lineH);
        if(linesPerPage>0&&lines.size()>0&&currentPage*linesPerPage>=lines.size())
            currentPage=Math.max(0,(lines.size()-1)/linesPerPage);
        ready=true;
    }

    private int totalPages(){
        if(linesPerPage<=0)return 1;
        return Math.max(1,(lines.size()+linesPerPage-1)/linesPerPage);
    }

    protected void paint(Graphics g){
        int t=theme,fh=font.getHeight(),bh=bold.getHeight(),sh2=tiny.getHeight();
        g.setColor(Pal.bg(t));g.fillRect(0,0,sw,sh);
        int hdrH2=bh+14;
        g.setColor(Pal.surface(t));g.fillRect(0,0,sw,hdrH2);
        UI.divider(g,Pal.border(t),0,hdrH2,sw);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString("<Back",6,hdrH2/2-sh2/2,Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textPri(t));g.setFont(bold);
        g.drawString("Full Message",sw/2,hdrH2/2-bh/2,Graphics.TOP|Graphics.HCENTER);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString((currentPage+1)+"/"+totalPages(),sw-6,hdrH2/2-sh2/2,Graphics.TOP|Graphics.RIGHT);
        int ftrH2=sh2+10,ftrY=sh-ftrH2;
        g.setColor(Pal.surface(t));g.fillRect(0,ftrY,sw,ftrH2);
        UI.divider(g,Pal.border(t),0,ftrY,sw);
        boolean hasPrev=(currentPage>0),hasNext=(currentPage<totalPages()-1);
        int btnW=sw/3-4;
        if(hasPrev){
            UI.fillRR(g,Pal.surface2(t),4,ftrY+2,btnW,ftrH2-4,4);
            g.setColor(Pal.accent(t));g.setFont(tiny);
            g.drawString("< Prev",4+btnW/2,ftrY+ftrH2/2-sh2/2,Graphics.TOP|Graphics.HCENTER);
        }
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString((!hasPrev&&!hasNext)?"FIRE/LSK:back":(currentPage+1)+"/"+totalPages(),
            sw/2,ftrY+ftrH2/2-sh2/2,Graphics.TOP|Graphics.HCENTER);
        if(hasNext){
            UI.fillRR(g,Pal.surface2(t),sw-btnW-4,ftrY+2,btnW,ftrH2-4,4);
            g.setColor(Pal.accent(t));g.setFont(tiny);
            g.drawString("Next >",sw-btnW-4+btnW/2,ftrY+ftrH2/2-sh2/2,Graphics.TOP|Graphics.HCENTER);
        }
        if(!ready||lines.size()==0){
            g.setColor(Pal.textSec(t));g.setFont(font);
            g.drawString("(empty)",sw/2,(ftrY+hdrH2)/2,Graphics.TOP|Graphics.HCENTER);
            return;
        }
        // FIX v2.0: clip content area so text never bleeds into footer
        g.setClip(0,hdrH2,sw,ftrY-hdrH2);
        int startLine=currentPage*linesPerPage,endLine=Math.min(startLine+linesPerPage,lines.size());
        int y=hdrH2+CONTENT_TOP_PAD; // BUG-4 FIX: was 6, must match buildLines()
        for(int i=startLine;i<endLine;i++){
            String line=(String)lines.elementAt(i);
            g.setColor(Pal.textPri(t));g.setFont(font);
            g.drawString(line,10,y,Graphics.TOP|Graphics.LEFT);
            y+=fh+2;
        }
        g.setClip(0,0,sw,sh);
    }

    protected void keyPressed(int k){
        int a=getGameAction(k);int tp=totalPages();
        if      (a==LEFT||k==Canvas.KEY_NUM4||a==UP||k==Canvas.KEY_NUM2)    {if(currentPage>0){currentPage--;repaint();}}
        else if (a==RIGHT||k==Canvas.KEY_NUM6||a==DOWN||k==Canvas.KEY_NUM8)  {if(currentPage<tp-1){currentPage++;repaint();}}
        else if (k==-6||k==-21||a==FIRE||k==Canvas.KEY_NUM5||k==-5||k==10){
            // FIX v2.0: return to existing ChatCanvas, not a new blank one
            if(midlet.getChatCanvas()!=null)
                midlet.getDisplay().setCurrent(midlet.getChatCanvas());
            else
                midlet.showChat();
        }
    }

    protected void pointerReleased(int x,int y){
        int ftrH2=tiny.getHeight()+10,ftrY=sh-ftrH2,tp=totalPages();
        if(y>=ftrY){
            if      (x<sw/3  &&currentPage>0)    {currentPage--;repaint();}
            else if (x>sw*2/3&&currentPage<tp-1) {currentPage++;repaint();}
            else {
                // FIX v2.0: return to existing ChatCanvas
                if(midlet.getChatCanvas()!=null)
                    midlet.getDisplay().setCurrent(midlet.getChatCanvas());
                else midlet.showChat();
            }
        } else {
            if      (x<sw/3  &&currentPage>0)    {currentPage--;repaint();}
            else if (x>sw*2/3&&currentPage<tp-1) {currentPage++;repaint();}
        }
    }
}


// ================================================================
// ChatMessage v1.9
// DISPLAY truncated at 500 chars for bubble readability,
// but originalText always stores THE FULL response (no cap).
// ================================================================
class ChatMessage {
    public static final int LONG_THRESHOLD=500;
    public String  originalText;
    public Vector  wrappedLines;
    public boolean isUser;
    public boolean isLong;
    public int     height;

    public ChatMessage(String text,int maxWidth,Font font){
        originalText=text;
        isUser=text.startsWith("User:");
        // v1.9: isLong based on full text length - no generation cap
        isLong=(!isUser&&text.length()>LONG_THRESHOLD);

        // Bubble shows truncated preview; full text always available in originalText
        String display=text;
        if(isLong){
            int cut=LONG_THRESHOLD;
            while(cut>400&&cut<text.length()
                &&text.charAt(cut)!=' '&&text.charAt(cut)!='\n')cut--;
            display=text.substring(0,cut)+"\n[...] Tap/OK to read full";
        }

        wrappedLines=wrap(display,maxWidth-12,font);
        int h=10;
        for(int i=0;i<wrappedLines.size();i++)
            if(!((String)wrappedLines.elementAt(i)).trim().equals("```"))h+=font.getHeight()+2;
        height=h;
    }

    private Vector wrap(String text,int maxW,Font font){
        Vector l=new Vector();int s=0,len=text.length();
        while(s<len){
            int nl=text.indexOf('\n',s);int e=(nl!=-1)?nl:len;
            String seg=text.substring(s,e);
            if(font.stringWidth(seg)<=maxW)l.addElement(seg);
            else{
                String t2="";
                for(int i=0;i<seg.length();i++){
                    char c=seg.charAt(i);
                    if(font.stringWidth(t2+c)>maxW){if(t2.length()>0)l.addElement(t2);t2=""+c;}
                    else t2+=c;
                }
                if(t2.length()>0)l.addElement(t2);
            }
            s=e+((nl!=-1)?1:0);
        }
        if(l.size()==0)l.addElement("");
        return l;
    }
}