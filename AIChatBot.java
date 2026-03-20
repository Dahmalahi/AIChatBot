import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.rms.*;
import javax.microedition.io.*;
import javax.microedition.io.file.*;
import java.io.*;
import java.util.*;

// ================================================================
// AIChatBot v1.5
// FIXED:
//   - Setup is ONE-TIME only (first launch). Never shown again.
//   - Language/Role changeable in Settings without re-setup.
//   - Theme change applies INSTANTLY when saved - no stale theme.
//   - History saved to RMS (persistent across sessions).
//   - Settings saves all values properly and returns to menu.
// NEW:
//   - Quick Replies (tap a suggestion to fill input)
//   - Copy last AI response to clipboard via native TextBox
//   - Chat stats (msg count, session count)
//   - Clear history confirmation dialog
//   - Profile edit screen (change name/lang/role without full reset)
// APP NAME: AIChatBot
// API: https://api-dl-j2meuploader.ndukadavid70.workers.dev/api/ai/chatgpt?text=
// ================================================================
public class AIChatBot extends MIDlet {

    public Display display;

    // Screens
    private ChatCanvas   chatCanvas;
    private MenuScreen   menuScreen;
    private SetupCanvas  setupCanvas;

    // User profile
    private String userName = "";
    private String userLang = "English";
    private String userRole = "Assistant";
    private String userId   = "";

    // Core components
    private History     history;
    private Settings    settings;
    private SaveManager saveManager;

    // First-launch flag stored in RMS
    private boolean firstLaunch = true;

    public static final String VERSION   = "v1.5";
    public static final String APP_NAME  = "AIChatBot";
    // Separate stores so we don't conflict
    private static final String PREFS_STORE   = "acb15prefs";
    private static final String HISTORY_STORE = "acb15hist";

    public AIChatBot() {
        display     = Display.getDisplay(this);
        history     = new History();
        settings    = new Settings();
        saveManager = new SaveManager();
    }

    protected void startApp() throws MIDletStateChangeException {
        // Generate userId once and keep it
        boolean loaded = loadPrefs();
        if (userId == null || userId.length() == 0) {
            userId = Utils.generateUserId();
        }
        // Load persisted history from RMS
        history.loadFromRMS(HISTORY_STORE);

        if (firstLaunch || userName.length() == 0) {
            // Show setup only on very first launch
            showSetup();
        } else {
            showMainMenu();
        }
    }

    protected void pauseApp() {}
    protected void destroyApp(boolean u) {}

    // ---- Navigation ----

    public void showMainMenu() {
        menuScreen = new MenuScreen(this);
        display.setCurrent(menuScreen);
    }

    public void showChat() {
        // Re-use existing canvas if same session, else create fresh
        chatCanvas = new ChatCanvas(this, userName, userLang, userRole);
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
    public void showProfile()  { display.setCurrent(new ProfileScreen(this, userName, userLang, userRole)); }

    public void exitApp() {
        if (chatCanvas != null) history.saveCurrentSession();
        history.saveToRMS(HISTORY_STORE); // persist history
        savePrefs();
        destroyApp(true);
        notifyDestroyed();
    }

    // ---- Setup done (FIRST TIME only) ----

    public void onSetupDone(String name, String lang, String role) {
        userName    = name.length() > 0 ? name : "User";
        userLang    = lang.length() > 0 ? lang : "English";
        userRole    = role.length() > 0 ? role : "Assistant";
        firstLaunch = false;  // never show setup again
        savePrefs();
        showMainMenu();
    }

    // ---- Profile update (called from ProfileScreen / Settings) ----

    public void onProfileUpdated(String name, String lang, String role) {
        if (name.length() > 0) userName = name;
        if (lang.length() > 0) userLang = lang;
        if (role.length() > 0) userRole = role;
        savePrefs();
        showMainMenu();
    }

    // ---- Native text inputs ----

    public void openNativeInput(final ChatCanvas c) {
        TextBox tb = new TextBox("Message", c.getCurrentInput(), 500, TextField.ANY);
        Command ok = new Command("Send",   Command.OK,   1);
        Command ca = new Command("Cancel", Command.BACK, 2);
        tb.addCommand(ok); tb.addCommand(ca);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable d) {
                if (cmd.getCommandType() == Command.OK) c.setCurrentInput(((TextBox)d).getString());
                display.setCurrent(c);
            }
        });
        display.setCurrent(tb);
    }

    public void openFieldInput(final int idx, String cur, final SetupCanvas sc) {
        String[] titles = {"Language", "Your name", "Your role"};
        TextBox tb = new TextBox(titles[idx], cur, 100, TextField.ANY);
        Command ok = new Command("OK",     Command.OK,   1);
        Command ca = new Command("Cancel", Command.BACK, 2);
        tb.addCommand(ok); tb.addCommand(ca);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable d) {
                if (cmd.getCommandType() == Command.OK) sc.setValue(idx, ((TextBox)d).getString());
                display.setCurrent(sc);
            }
        });
        display.setCurrent(tb);
    }

    public void openProfileFieldInput(final int idx, String cur, final ProfileScreen ps) {
        String[] titles = {"Language", "Your name", "Your role"};
        TextBox tb = new TextBox(titles[idx], cur, 100, TextField.ANY);
        Command ok = new Command("OK",     Command.OK,   1);
        Command ca = new Command("Cancel", Command.BACK, 2);
        tb.addCommand(ok); tb.addCommand(ca);
        tb.setCommandListener(new CommandListener() {
            public void commandAction(Command cmd, Displayable d) {
                if (cmd.getCommandType() == Command.OK) ps.setValue(idx, ((TextBox)d).getString());
                display.setCurrent(ps);
            }
        });
        display.setCurrent(tb);
    }

    // Show text in a native TextBox (read-only copy trick)
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
                int i = ((List)d).getSelectedIndex();
                if (chatCanvas != null) {
                    if      (i==0) chatCanvas.showDetail(ascii);
                    else if (i==1) chatCanvas.saveAsBmp(ascii);
                    else if (i==2) showCopyBox(full, "Content");
                    if (i != 2)   display.setCurrent(chatCanvas);
                } else showMainMenu();
            }
        });
        display.setCurrent(m);
    }

    // ---- History clear confirmation ----

    public void confirmClearHistory() {
        List dlg = new List("Clear all history?", List.EXCLUSIVE);
        dlg.append("Yes, clear all", null);
        dlg.append("No, keep it",    null);
        Command ok = new Command("OK", Command.OK, 1);
        dlg.addCommand(ok);
        dlg.setCommandListener(new CommandListener() {
            public void commandAction(Command c, Displayable d) {
                if (((List)d).getSelectedIndex() == 0) {
                    history.clearAll();
                    history.saveToRMS(HISTORY_STORE);
                }
                showHistory();
            }
        });
        display.setCurrent(dlg);
    }

    // ---- Prefs (RMS) ----

    public void savePrefs() {
        try {
            RecordStore rs = RecordStore.openRecordStore(PREFS_STORE, true);
            // Format: firstLaunch|userName|userLang|userRole|userId|timeout|ctx|maxCtx|proxy|theme
            String d = (firstLaunch?"1":"0") + "|" + userName + "|" + userLang + "|" + userRole + "|"
                     + userId + "|" + settings.getTimeout() + "|"
                     + (settings.isContextEnabled()?"1":"0") + "|"
                     + settings.getMaxContext() + "|"
                     + (settings.isProxyEnabled()?"1":"0") + "|"
                     + settings.getThemeIndex();
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
                // Parse: firstLaunch|userName|userLang|userRole|userId|...
                int p0=d.indexOf('|');
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
                            int p5=d.indexOf('|',p4+1),p6=d.indexOf('|',p5+1),
                                p7=d.indexOf('|',p6+1),p8=d.indexOf('|',p7+1);
                            if(p5>0) settings.setTimeoutSilent(Integer.parseInt(d.substring(p4+1,p5)));
                            if(p6>0) settings.setContextEnabledSilent(d.substring(p5+1,p6).equals("1"));
                            if(p7>0) settings.setMaxContextSilent(Integer.parseInt(d.substring(p6+1,p7)));
                            if(p8>0){
                                settings.setProxyEnabledSilent(d.substring(p7+1,p8).equals("1"));
                                settings.setThemeIndexSilent(Integer.parseInt(d.substring(p8+1).trim()));
                            } else if(p7>0) {
                                settings.setProxyEnabledSilent(d.substring(p7+1).equals("1"));
                            }
                        } catch(Exception x){}
                    }
                }
                rs.closeRecordStore(); return true;
            }
            rs.closeRecordStore();
        } catch (Exception e) {}
        return false;
    }

    // ---- Getters ----

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
// Pal - color palette (theme system)
// ================================================================
class Pal {
    // [bg, surface, surface2, border, textPri, textSec,
    //  accent, accentDk, userBubble, aiBubble, userText, aiText, danger]
    private static final int[][] P = {
        // 0 Dark  (Claude-like navy + orange)
        { 0x1A1A2E, 0x16213E, 0x0F3460, 0x2D2D44,
          0xEEEEF8, 0x9090A8, 0xDA7756, 0xB05030,
          0xDA7756, 0x252538, 0xFFFFFF, 0xDDDDEE, 0xFF4444 },
        // 1 ChatGPT (dark + green)
        { 0x171717, 0x202020, 0x2A2A2A, 0x383838,
          0xEEEEEE, 0x888888, 0x10A37F, 0x0C7A5F,
          0x10A37F, 0x262626, 0xFFFFFF, 0xCCCCCC, 0xFF5555 },
        // 2 Ocean (deep blue + cyan)
        { 0x0A1628, 0x142036, 0x1E3050, 0x2A4060,
          0xDDEEFF, 0x7090B0, 0x4AABFF, 0x2288DD,
          0x4AABFF, 0x162840, 0xFFFFFF, 0xBBDDFF, 0xFF4444 },
        // 3 Amoled (black + purple)
        { 0x000000, 0x0C0C0C, 0x141414, 0x222222,
          0xF0F0F0, 0x808080, 0xBB55FF, 0x8822CC,
          0xBB55FF, 0x0E0E0E, 0xFFFFFF, 0xDDDDDD, 0xFF4444 },
    };
    private static final String[] NAMES = { "Dark", "ChatGPT", "Ocean", "Amoled" };

    public static int count()        { return P.length; }
    public static String name(int t) { return NAMES[t%4]; }
    public static int bg(int t)      { return P[t%4][0]; }
    public static int surface(int t) { return P[t%4][1]; }
    public static int surface2(int t){ return P[t%4][2]; }
    public static int border(int t)  { return P[t%4][3]; }
    public static int textPri(int t) { return P[t%4][4]; }
    public static int textSec(int t) { return P[t%4][5]; }
    public static int accent(int t)  { return P[t%4][6]; }
    public static int accentDk(int t){ return P[t%4][7]; }
    public static int userBubble(int t){ return P[t%4][8]; }
    public static int aiBubble(int t){ return P[t%4][9]; }
    public static int userText(int t){ return P[t%4][10]; }
    public static int aiText(int t)  { return P[t%4][11]; }
    public static int danger(int t)  { return P[t%4][12]; }
}


// ================================================================
// UI - shared drawing helpers
// ================================================================
class UI {
    public static void fillRR(Graphics g, int col, int x, int y, int w, int h, int arc) {
        g.setColor(col); g.fillRoundRect(x, y, w, h, arc, arc);
    }
    public static void drawRR(Graphics g, int col, int x, int y, int w, int h, int arc) {
        g.setColor(col); g.drawRoundRect(x, y, w, h, arc, arc);
    }
    public static void divider(Graphics g, int col, int x, int y, int w) {
        g.setColor(col); g.drawLine(x, y, x+w, y);
    }
    public static void fillCircle(Graphics g, int col, int cx, int cy, int r) {
        g.setColor(col); g.fillArc(cx-r, cy-r, r*2, r*2, 0, 360);
    }
    public static void drawCircle(Graphics g, int col, int cx, int cy, int r) {
        g.setColor(col); g.drawArc(cx-r, cy-r, r*2, r*2, 0, 360);
    }
    // Draw avatar: circle + centered initials
    public static void avatar(Graphics g, int cx, int cy, int r, int bg, int fg, String init, Font f) {
        fillCircle(g, bg, cx, cy, r);
        g.setColor(fg); g.setFont(f);
        int tw=f.stringWidth(init), fh=f.getHeight();
        g.drawString(init, cx-tw/2, cy-fh/2, Graphics.TOP|Graphics.LEFT);
    }
    public static String initials(String name) {
        if (name==null||name.length()==0) return "?";
        String u=name.trim().toUpperCase();
        int sp=u.indexOf(' ');
        if (sp>0&&sp<u.length()-1) return ""+u.charAt(0)+u.charAt(sp+1);
        return u.substring(0, Math.min(2, u.length()));
    }
    // Draw standard screen header: surface bar + back arrow + title + optional right text
    public static int drawHeader(Graphics g, int t, int sw, Font bold, Font tiny, String title, String right) {
        int hdrH = bold.getHeight() + 14;
        g.setColor(Pal.surface(t)); g.fillRect(0, 0, sw, hdrH);
        divider(g, Pal.border(t), 0, hdrH, sw);
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString("<", 8, hdrH/2 - tiny.getHeight()/2, Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textPri(t)); g.setFont(bold);
        g.drawString(title, sw/2, hdrH/2 - bold.getHeight()/2, Graphics.TOP|Graphics.HCENTER);
        if (right != null && right.length() > 0) {
            g.setColor(Pal.textSec(t)); g.setFont(tiny);
            g.drawString(right, sw-6, hdrH/2 - tiny.getHeight()/2, Graphics.TOP|Graphics.RIGHT);
        }
        return hdrH;
    }
    // Draw standard bottom bar
    public static void drawFooter(Graphics g, int t, int sw, int sh, Font tiny, String hint) {
        int sh2 = tiny.getHeight() + 6;
        g.setColor(Pal.surface(t)); g.fillRect(0, sh-sh2, sw, sh2);
        divider(g, Pal.border(t), 0, sh-sh2, sw);
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString(hint, sw/2, sh-sh2+2, Graphics.TOP|Graphics.HCENTER);
    }
}


// ================================================================
// SetupCanvas - FIRST-TIME ONLY welcome + profile setup
// ================================================================
class SetupCanvas extends Canvas implements Runnable {
    private AIChatBot midlet;
    private Font  font, bold, tiny;
    private String[] labels = { "Language", "Your name", "Your role" };
    private String[] values = { "English", "", "Assistant" };
    private int   focusIndex = 0;
    private int   screenW, screenH;
    private boolean running = false;
    private int   animTick  = 0;
    private int   theme;

    public SetupCanvas(AIChatBot midlet, String uName, String uLang, String uRole) {
        this.midlet = midlet;
        theme = midlet.getSettings().getThemeIndex();
        font  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        bold  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,  Font.SIZE_SMALL);
        tiny  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        values[0] = uLang.length() > 0 ? uLang : "English";
        values[1] = uName.length() > 0 ? uName : "";
        values[2] = uRole.length() > 0 ? uRole : "Assistant";
        setFullScreenMode(true);
    }

    protected void showNotify() {
        screenW = getWidth(); screenH = getHeight();
        theme = midlet.getSettings().getThemeIndex();
        running = true;
        new Thread(this).start();
    }

    protected void hideNotify() { running = false; }

    public void run() {
        while (running) { animTick++; repaint(); try{Thread.sleep(150);}catch(Exception e){} }
    }

    protected void paint(Graphics g) {
        int t = theme;
        int fh = font.getHeight(), bh = bold.getHeight(), sh = tiny.getHeight();

        g.setColor(Pal.bg(t)); g.fillRect(0, 0, screenW, screenH);

        // Top accent stripe
        g.setColor(Pal.surface(t)); g.fillRect(0, 0, screenW, 50);
        UI.divider(g, Pal.border(t), 0, 50, screenW);

        // Animated AI logo circle
        int logoR = 18, logoCX = screenW/2, logoCY = 30;
        UI.fillCircle(g, Pal.accent(t), logoCX, logoCY, logoR);
        // Pulse ring
        int pulse = (animTick % 16);
        if (pulse < 8) {
            int pr = logoR + 3 + pulse/2;
            g.setColor(Pal.border(t));
            UI.drawCircle(g, Pal.border(t), logoCX, logoCY, pr);
        }
        g.setColor(Pal.userText(t)); g.setFont(bold);
        g.drawString("AI", logoCX - bold.stringWidth("AI")/2, logoCY - bh/2, Graphics.TOP|Graphics.LEFT);

        // Title
        int y = 60;
        g.setColor(Pal.textPri(t)); g.setFont(bold);
        g.drawString("Welcome to " + AIChatBot.APP_NAME, screenW/2, y, Graphics.TOP|Graphics.HCENTER);
        y += bh + 2;
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString("Set up your profile once to get started", screenW/2, y, Graphics.TOP|Graphics.HCENTER);
        y += sh + 10;

        // Form card
        int cx = 8, cw = screenW - 16, cp = 10;
        int fieldH = sh + 10;
        int cardH  = labels.length * (sh + 4 + fieldH + 10) + cp;
        UI.fillRR(g, Pal.surface(t), cx, y, cw, cardH, 10);
        UI.drawRR(g, Pal.border(t),  cx, y, cw, cardH, 10);

        int fy = y + cp;
        for (int i = 0; i < labels.length; i++) {
            boolean focused = (i == focusIndex);
            g.setColor(focused ? Pal.accent(t) : Pal.textSec(t)); g.setFont(tiny);
            g.drawString(labels[i], cx+cp, fy, Graphics.TOP|Graphics.LEFT);
            fy += sh + 4;

            int fieldBg  = focused ? Pal.surface2(t) : Pal.bg(t);
            int fieldBrd = focused ? Pal.accent(t)   : Pal.border(t);
            UI.fillRR(g, fieldBg,  cx+cp,     fy, cw-cp*2, fieldH, 6);
            UI.drawRR(g, fieldBrd, cx+cp,     fy, cw-cp*2, fieldH, 6);

            String disp = values[i].length() > 0 ? values[i] : (labels[i] + "...");
            g.setColor(values[i].length() > 0 ? Pal.textPri(t) : Pal.textSec(t));
            g.setFont(font);
            g.drawString(disp, cx+cp+6, fy + (fieldH-fh)/2, Graphics.TOP|Graphics.LEFT);

            // Blinking cursor on focused field
            if (focused && (animTick/4)%2 == 0) {
                int cx2 = cx+cp+6 + font.stringWidth(disp) + 1;
                g.setColor(Pal.accent(t));
                g.drawLine(cx2, fy+4, cx2, fy+fieldH-4);
            }
            fy += fieldH + 10;
        }
        y += cardH + 12;

        // Continue button
        boolean btnFoc = (focusIndex == labels.length);
        int btnH = bh + 14;
        UI.fillRR(g, btnFoc ? Pal.accent(t) : Pal.accentDk(t), 8, y, screenW-16, btnH, btnH/2);
        if (btnFoc) UI.drawRR(g, Pal.textPri(t), 6, y-2, screenW-12, btnH+4, (btnH+4)/2);
        g.setColor(Pal.userText(t)); g.setFont(bold);
        g.drawString("Get Started  ->", screenW/2, y + btnH/2 - bh/2, Graphics.TOP|Graphics.HCENTER);

        // Hint
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString("2/8:move  FIRE:edit or start", screenW/2, screenH-sh-3, Graphics.TOP|Graphics.HCENTER);
    }

    protected void keyPressed(int keyCode) {
        int a = getGameAction(keyCode);
        if      (a==UP   || keyCode==Canvas.KEY_NUM2) { focusIndex--; if(focusIndex<0) focusIndex=labels.length; }
        else if (a==DOWN || keyCode==Canvas.KEY_NUM8) { focusIndex++; if(focusIndex>labels.length) focusIndex=0; }
        else if (a==FIRE || keyCode==Canvas.KEY_NUM5 || keyCode==-5 || keyCode==10) {
            if (focusIndex == labels.length) {
                running = false;
                midlet.onSetupDone(values[1], values[0], values[2]);
            } else {
                midlet.openFieldInput(focusIndex, values[focusIndex], this);
            }
        }
        repaint();
    }

    public void setValue(int i, String v) { if(i>=0&&i<values.length) values[i]=v; repaint(); }
}


// ================================================================
// ProfileScreen - Edit name/lang/role from settings (not first-launch)
// ================================================================
class ProfileScreen extends Canvas {
    private AIChatBot midlet;
    private Font  font, bold, tiny;
    private String[] labels = { "Language", "Your name", "Your role" };
    private String[] values;
    private int   focusIndex = 0;
    private int   screenW, screenH, theme;

    public ProfileScreen(AIChatBot midlet, String name, String lang, String role) {
        this.midlet = midlet;
        theme = midlet.getSettings().getThemeIndex();
        font  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        bold  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,  Font.SIZE_SMALL);
        tiny  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        values = new String[]{ lang, name, role };
        setFullScreenMode(true);
    }

    protected void showNotify() {
        screenW=getWidth(); screenH=getHeight();
        theme=midlet.getSettings().getThemeIndex();
        repaint();
    }

    protected void paint(Graphics g) {
        int t=theme, fh=font.getHeight(), bh=bold.getHeight(), sh=tiny.getHeight();
        g.setColor(Pal.bg(t)); g.fillRect(0,0,screenW,screenH);
        int hdrH = UI.drawHeader(g, t, screenW, bold, tiny, "Edit Profile", null);

        int y=hdrH+12, cp=10, cx=8, cw=screenW-16;
        int fieldH=sh+10;

        for (int i=0;i<labels.length;i++) {
            boolean focused=(i==focusIndex);
            g.setColor(focused?Pal.accent(t):Pal.textSec(t)); g.setFont(tiny);
            g.drawString(labels[i], cx+cp, y, Graphics.TOP|Graphics.LEFT);
            y += sh+4;
            UI.fillRR(g, focused?Pal.surface2(t):Pal.surface(t), cx+cp, y, cw-cp*2, fieldH, 6);
            UI.drawRR(g, focused?Pal.accent(t):Pal.border(t),    cx+cp, y, cw-cp*2, fieldH, 6);
            String disp=values[i].length()>0?values[i]:"(empty)";
            g.setColor(values[i].length()>0?Pal.textPri(t):Pal.textSec(t)); g.setFont(font);
            g.drawString(disp, cx+cp+6, y+(fieldH-fh)/2, Graphics.TOP|Graphics.LEFT);
            y += fieldH+10;
        }

        // Save button
        int btnH=bh+14;
        boolean btnFoc=(focusIndex==labels.length);
        UI.fillRR(g, btnFoc?Pal.accent(t):Pal.accentDk(t), 8, y, screenW-16, btnH, btnH/2);
        g.setColor(Pal.userText(t)); g.setFont(bold);
        g.drawString("Save Profile", screenW/2, y+btnH/2-bh/2, Graphics.TOP|Graphics.HCENTER);

        UI.drawFooter(g, t, screenW, screenH, tiny, "2/8:move  FIRE:edit  LSK:back");
    }

    protected void keyPressed(int k) {
        int a=getGameAction(k);
        if      (a==UP   ||k==Canvas.KEY_NUM2) { focusIndex--; if(focusIndex<0) focusIndex=labels.length; }
        else if (a==DOWN ||k==Canvas.KEY_NUM8) { focusIndex++; if(focusIndex>labels.length) focusIndex=0; }
        else if (a==FIRE ||k==Canvas.KEY_NUM5||k==-5||k==10) {
            if (focusIndex==labels.length) midlet.onProfileUpdated(values[1], values[0], values[2]);
            else midlet.openProfileFieldInput(focusIndex, values[focusIndex], this);
        }
        else if (k==-6||k==-21) midlet.showMainMenu();
        repaint();
    }

    public void setValue(int i, String v) { if(i>=0&&i<values.length) values[i]=v; repaint(); }
}


// ================================================================
// MenuScreen - sidebar-style main menu with proper scrolling
// ================================================================
class MenuScreen extends Canvas {
    private AIChatBot midlet;
    private int sel=0, screenW, screenH, theme;
    // scrollOffset: index of first visible item
    private int scrollOff = 0;

    private static final String[] ICONS  = { "*", "#", "@", "~", "!", "+" };
    private static final String[] LABELS = { "New Chat", "History", "Files", "Settings", "About", "Exit" };
    private static final String[] DESC   = {
        "Start a conversation", "Past sessions", "Device files + AI",
        "Theme & options",      "App info",      "Save and quit"
    };

    public MenuScreen(AIChatBot m) {
        midlet = m;
        theme  = m.getSettings().getThemeIndex();
        setFullScreenMode(true);
    }

    protected void showNotify() {
        screenW=getWidth(); screenH=getHeight();
        theme = midlet.getSettings().getThemeIndex();
        repaint();
    }

    protected void paint(Graphics g) {
        int t=theme;
        Font bold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,  Font.SIZE_SMALL);
        Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        Font tiny = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        int bh=bold.getHeight(), fh=font.getHeight(), sh=tiny.getHeight();

        g.setColor(Pal.bg(t)); g.fillRect(0, 0, screenW, screenH);

        // Header
        int hdrH = bh+16;
        g.setColor(Pal.surface(t)); g.fillRect(0, 0, screenW, hdrH);
        UI.divider(g, Pal.border(t), 0, hdrH, screenW);
        int avR=9, avCX=avR+10, avCY=hdrH/2;
        UI.fillCircle(g, Pal.accent(t), avCX, avCY, avR);
        g.setColor(Pal.userText(t)); g.setFont(tiny);
        g.drawString("AI", avCX-tiny.stringWidth("AI")/2, avCY-sh/2, Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textPri(t)); g.setFont(bold);
        g.drawString(AIChatBot.APP_NAME, avCX+avR+7, hdrH/2-bh/2, Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString(AIChatBot.VERSION, screenW-6, hdrH/2-sh/2, Graphics.TOP|Graphics.RIGHT);

        // User card
        int cardY=hdrH+8, cardX=8, cardW=screenW-16, cardH=bh+sh+16;
        UI.fillRR(g, Pal.surface(t), cardX, cardY, cardW, cardH, 8);
        int uR=12, uCX=cardX+uR+8, uCY=cardY+cardH/2;
        UI.avatar(g, uCX, uCY, uR, Pal.accent(t), Pal.userText(t), UI.initials(midlet.getUserName()), tiny);
        int infoX=uCX+uR+8;
        g.setColor(Pal.textPri(t)); g.setFont(bold);
        g.drawString(midlet.getUserName(), infoX, cardY+6, Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString(midlet.getUserRole()+" | "+midlet.getUserLang(), infoX, cardY+6+bh+2, Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.accent(t));
        g.drawString("Edit >", cardX+cardW-tiny.stringWidth("Edit >")-8, cardY+6+bh+2, Graphics.TOP|Graphics.LEFT);

        UI.divider(g, Pal.border(t), cardX, cardY+cardH+6, cardW);

        // List area
        int listY  = cardY+cardH+14;
        int softH  = sh+6;
        int listH  = screenH - listY - softH;
        int iH     = fh+sh+12;
        int iGap   = 4;
        int iStep  = iH+iGap;
        // How many items fit
        int visible = listH / iStep;
        if (visible < 1) visible = 1;

        // Clamp scrollOff so selected is always visible
        if (sel < scrollOff)             scrollOff = sel;
        if (sel >= scrollOff + visible)  scrollOff = sel - visible + 1;
        if (scrollOff < 0)               scrollOff = 0;

        int iW=screenW-16, iX=8;

        g.setClip(0, listY, screenW, listH);

        for (int i=0; i<LABELS.length; i++) {
            int drawIdx = i - scrollOff;
            if (drawIdx < 0 || drawIdx >= visible) continue;
            int iy = listY + drawIdx * iStep;
            boolean foc=(i==sel);
            if (foc) {
                UI.fillRR(g, Pal.surface(t), iX, iy, iW, iH, 8);
                g.setColor(Pal.accent(t)); g.fillRect(iX, iy, 3, iH);
            }
            int icR=iH/2-2, icCX=iX+4+icR, icCY=iy+iH/2;
            UI.fillCircle(g, foc?Pal.accent(t):Pal.surface2(t), icCX, icCY, icR);
            g.setColor(foc?Pal.userText(t):Pal.textSec(t)); g.setFont(tiny);
            g.drawString(ICONS[i], icCX-tiny.stringWidth(ICONS[i])/2, icCY-sh/2, Graphics.TOP|Graphics.LEFT);
            int lx=icCX+icR+8;
            g.setColor(foc?Pal.textPri(t):Pal.textSec(t)); g.setFont(foc?bold:font);
            g.drawString(LABELS[i], lx, iy+(iH-bh-sh-2)/2, Graphics.TOP|Graphics.LEFT);
            g.setColor(Pal.textSec(t)); g.setFont(tiny);
            g.drawString(DESC[i], lx, iy+(iH-bh-sh-2)/2+bh+2, Graphics.TOP|Graphics.LEFT);
            if (foc) { g.setColor(Pal.accent(t)); g.setFont(bold); g.drawString(">", iX+iW-12, iy+iH/2-bh/2, Graphics.TOP|Graphics.LEFT); }
        }

        g.setClip(0, 0, screenW, screenH);

        // Scroll indicator dots
        if (LABELS.length > visible) {
            int dotX = screenW - 8;
            int dotAreaH = listH;
            for (int i=0; i<LABELS.length; i++) {
                int dotY = listY + i * dotAreaH / LABELS.length + dotAreaH/LABELS.length/2;
                g.setColor(i==sel ? Pal.accent(t) : Pal.border(t));
                g.fillArc(dotX-2, dotY-2, 4, 4, 0, 360);
            }
        }

        // Footer
        int themeBarH=sh+4;
        g.setColor(Pal.surface(t)); g.fillRect(0, screenH-themeBarH, screenW, themeBarH);
        UI.divider(g, Pal.border(t), 0, screenH-themeBarH, screenW);
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString("Theme: "+Pal.name(t)+"  2/8:scroll  FIRE:open", screenW/2, screenH-themeBarH+2, Graphics.TOP|Graphics.HCENTER);
    }

    protected void keyPressed(int keyCode) {
        int a=getGameAction(keyCode);
        if      (a==UP   ||keyCode==Canvas.KEY_NUM2) { sel=(sel-1+LABELS.length)%LABELS.length; }
        else if (a==DOWN ||keyCode==Canvas.KEY_NUM8) { sel=(sel+1)%LABELS.length; }
        else if (a==FIRE ||keyCode==Canvas.KEY_NUM5||keyCode==-5||keyCode==10) {
            switch(sel){
                case 0: midlet.showChat();     break;
                case 1: midlet.showHistory();  break;
                case 2: midlet.showFiles();    break;
                case 3: midlet.showSettings(); break;
                case 4: midlet.showAbout();    break;
                case 5: midlet.exitApp();      break;
            }
        }
        else if (keyCode==Canvas.KEY_STAR) midlet.showProfile();
        repaint();
    }
}


// ================================================================
// ChatCanvas - main chat interface
// ================================================================
class ChatCanvas extends Canvas implements Runnable {
    private AIChatBot midlet;
    private Vector    messages   = new Vector();
    private String    currentInput = "";
    private String    userName, userLang, userRole;
    private String    session    = "";
    private int       theme;

    // Layout
    private int  sw, sh, hdrH, barH, barY, inputH;
    private Font font, bold, tiny;
    private boolean layoutDone = false;

    // Focus: 0=input, 1=send, 2=+menu
    private int  focusIdx  = 0;
    private int  scrollY   = 0;  // 0=bottom(latest), positive=scrolled up into history

    // Cursor
    private boolean cursorOn = true;
    private Timer   cursorTimer;

    // State
    private boolean loading    = false;
    private boolean detailMode = false;
    private String  detailText = "";
    private int     detailScroll = 0;
    private int     animTick   = 0;

    // Bottom-sheet menu
    private boolean menuOpen = false;
    private int     menuSel  = 0;
    private static final String[] MENU = {
        "Send message", "Web Search", "Copy last AI reply",
        "Clear chat", "Save to file", "New chat", "Main menu"
    };

    // Quick reply suggestions with labels + prompts sent to AI
    private static final String[] QUICK_LABEL = { "Hello!", "Help me with...", "Explain:", "Translate to" };
    private static final String[] QUICK_DESC  = {
        "Say hi and start chatting",
        "Get practical help with a task",
        "Get a clear step-by-step explanation",
        "Translate text to another language"
    };
    private static final String[] QUICK_PROMPT = {
        "Hello! How are you?",
        "Help me with: ",
        "Explain: ",
        "Translate the following text to "
    };
    private boolean showQuick = true;
    private int     quickSel  = 0;  // selected quick reply chip index

    private static final String API   = "http://api-dl-j2meuploader.ndukadavid70.workers.dev/api/ai/chatgpt?text=";
    private static final String PROXY = "http://nnp.nnchan.ru/glype/browse.php?u=";

    public ChatCanvas(AIChatBot midlet, String name, String lang, String role) {
        this.midlet   = midlet;
        this.userName = name;
        this.userLang = lang;
        this.userRole = role;
        this.theme    = midlet.getSettings().getThemeIndex();
        font  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        bold  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,  Font.SIZE_SMALL);
        tiny  = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        setFullScreenMode(true);

        cursorTimer = new Timer();
        cursorTimer.schedule(new TimerTask() {
            public void run() { cursorOn=!cursorOn; if(layoutDone) repaint(0,barY,sw,barH); }
        }, 500L, 500L);

        addMessage("AI: Hello " + name + "! I am your AI assistant. How can I help you today?");
        new Thread(this).start();
    }

    public void run() {
        while (true) { animTick++; if(loading||menuOpen) repaint(); try{Thread.sleep(200);}catch(Exception e){} }
    }

    protected void showNotify() { initLayout(); }

    private void initLayout() {
        sw=getWidth(); sh=getHeight();
        if (sw>0&&sh>0) {
            hdrH   = bold.getHeight()+14;
            inputH = font.getHeight()+10;
            barH   = inputH+14;
            barY   = sh-barH;
            layoutDone = true;
            repaint();
        }
    }

    public void addMessage(String msg) {
        int maxW=(int)(sw*0.74f); if(maxW<=0)maxW=120;
        messages.addElement(new ChatMessage(msg, maxW, font));
        if(messages.size()>60) messages.removeElementAt(0);
        scrollY=0; focusIdx=0;
        // Hide quick replies once real conversation starts (keep for first welcome msg)
        if (!msg.startsWith("AI: Hello")) { showQuick=false; }
        repaint();
    }

    // ---- Paint ----

    protected void paint(Graphics g) {
        if (!layoutDone||sh==0) { initLayout(); if(!layoutDone) return; }
        int t=theme;

        g.setColor(Pal.bg(t)); g.fillRect(0, 0, sw, sh);
        paintHeader(g, t);

        g.setClip(0, hdrH, sw, barY-hdrH);
        if (detailMode)  paintDetail(g, t);
        else if (showQuick && messages.size()<=1) paintQuickReplies(g, t);
        else             paintMessages(g, t);
        g.setClip(0, 0, sw, sh);

        g.setClip(0, barY, sw, barH);
        paintBar(g, t);
        g.setClip(0, 0, sw, sh);

        if (menuOpen) paintSheet(g, t);
    }

    private void paintHeader(Graphics g, int t) {
        int bh=bold.getHeight(), sh2=tiny.getHeight();
        g.setColor(Pal.surface(t)); g.fillRect(0, 0, sw, hdrH);
        UI.divider(g, Pal.border(t), 0, hdrH, sw);

        // Back
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString("<  Menu", 6, hdrH/2-sh2/2, Graphics.TOP|Graphics.LEFT);

        // AI avatar + title center
        int avR=8, avCX=sw/2-bold.stringWidth("AIChatBot")/2-avR-4, avCY=hdrH/2;
        UI.fillCircle(g, Pal.accent(t), avCX, avCY, avR);
        g.setColor(Pal.userText(t)); g.setFont(tiny);
        g.drawString("A", avCX-tiny.stringWidth("A")/2, avCY-sh2/2, Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textPri(t)); g.setFont(bold);
        g.drawString(AIChatBot.APP_NAME, sw/2, hdrH/2-bh/2, Graphics.TOP|Graphics.HCENTER);

        // User avatar right
        int uR=9, uCX=sw-uR-8, uCY=hdrH/2;
        UI.avatar(g, uCX, uCY, uR, Pal.surface2(t), Pal.textSec(t), UI.initials(userName), tiny);

        // Animated loading dots below title
        if (loading) {
            int dotY2=hdrH-4;
            for (int d=0;d<3;d++) {
                int phase=(animTick+d*3)%9;
                g.setColor(phase<5?Pal.accent(t):Pal.border(t));
                int dx=sw/2-9+d*9;
                g.fillArc(dx-2, dotY2-2, 4, 4, 0, 360);
            }
        }
    }

    private void paintMessages(Graphics g, int t) {
        int fh=font.getHeight(), sh2=tiny.getHeight();
        int curY=barY-8+scrollY;

        for (int i=messages.size()-1;i>=0;i--) {
            ChatMessage m=(ChatMessage)messages.elementAt(i);
            curY -= m.height;
            if (curY>barY) { curY-=8; continue; }
            if (curY+m.height<hdrH) break;

            boolean isUser=m.isUser;
            int avR=8, bW=(int)(sw*0.72f);

            if (isUser) {
                int avCX=sw-avR-5, avCY=curY+avR+2;
                UI.avatar(g, avCX, avCY, avR, Pal.accent(t), Pal.userText(t), UI.initials(userName), tiny);
                int bX=sw-avR*2-bW-10;
                UI.fillRR(g, Pal.userBubble(t), bX, curY, bW, m.height, 14);
                g.setColor(Pal.userBubble(t)); g.fillRect(bX+bW-8, curY, 8, 8);
                int ly=curY+5;
                for (int j=0;j<m.wrappedLines.size();j++) {
                    g.setColor(Pal.userText(t)); g.setFont(font);
                    g.drawString((String)m.wrappedLines.elementAt(j), bX+6, ly, Graphics.TOP|Graphics.LEFT);
                    ly+=fh+2;
                }
            } else {
                int avCX=avR+5, avCY=curY+avR+2;
                UI.fillCircle(g, Pal.accentDk(t), avCX, avCY, avR);
                g.setColor(Pal.userText(t)); g.setFont(tiny);
                g.drawString("A", avCX-tiny.stringWidth("A")/2, avCY-sh2/2, Graphics.TOP|Graphics.LEFT);
                int bX=avCX+avR+5, bW2=Math.min(bW, sw-bX-6);
                UI.fillRR(g, Pal.aiBubble(t), bX, curY, bW2, m.height, 14);
                g.setColor(Pal.aiBubble(t)); g.fillRect(bX, curY, 8, 8);
                UI.drawRR(g, Pal.border(t), bX, curY, bW2, m.height, 14);
                int ly=curY+5;
                boolean inCode=false;
                for (int j=0;j<m.wrappedLines.size();j++) {
                    String line=(String)m.wrappedLines.elementAt(j);
                    if (line.trim().equals("```")) {
                        inCode=!inCode;
                        UI.divider(g, Pal.border(t), bX+5, ly+fh/2, bW2-10);
                    } else {
                        if (inCode) { g.setColor(Pal.bg(t)); g.fillRect(bX+3,ly,bW2-6,fh+2); g.setColor(0x88EEBB); }
                        else g.setColor(Pal.aiText(t));
                        g.setFont(font);
                        g.drawString(line, bX+6, ly, Graphics.TOP|Graphics.LEFT);
                        ly+=fh+2;
                    }
                }
            }
            curY-=8;
        }
    }

    private void paintQuickReplies(Graphics g, int t) {
        int fh=font.getHeight(), bh=bold.getHeight(), sh2=tiny.getHeight();

        // Title
        int ty = hdrH + 10;
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString("What would you like to do?", sw/2, ty, Graphics.TOP|Graphics.HCENTER);
        ty += sh2 + 8;

        // Hint about D-pad
        g.setColor(Pal.border(t)); g.setFont(tiny);
        g.drawString("2/8:select  FIRE:use", sw/2, ty, Graphics.TOP|Graphics.HCENTER);
        ty += sh2 + 6;

        int cardH = bh + sh2 + 14;
        int cardX = 8, cardW = sw - 16;

        for (int i = 0; i < QUICK_LABEL.length; i++) {
            boolean sel = (i == quickSel);

            // Card background
            UI.fillRR(g, sel ? Pal.surface2(t) : Pal.surface(t), cardX, ty, cardW, cardH, 8);

            // Left accent bar on selected
            if (sel) {
                g.setColor(Pal.accent(t));
                g.fillRect(cardX, ty, 3, cardH);
                UI.drawRR(g, Pal.accent(t), cardX, ty, cardW, cardH, 8);
            }

            // Label (bold when selected)
            g.setColor(sel ? Pal.accent(t) : Pal.textPri(t));
            g.setFont(sel ? bold : font);
            g.drawString(QUICK_LABEL[i], cardX + 12, ty + 5, Graphics.TOP|Graphics.LEFT);

            // Description
            g.setColor(Pal.textSec(t)); g.setFont(tiny);
            g.drawString(QUICK_DESC[i], cardX + 12, ty + 5 + bh + 2, Graphics.TOP|Graphics.LEFT);

            // Chevron on selected
            if (sel) {
                g.setColor(Pal.accent(t)); g.setFont(bold);
                g.drawString(">", cardX + cardW - 14, ty + cardH/2 - bh/2, Graphics.TOP|Graphics.LEFT);
            }

            ty += cardH + 5;
        }
    }

    private void paintDetail(Graphics g, int t) {
        int fh=font.getHeight(), sh2=tiny.getHeight();
        g.setColor(Pal.bg(t)); g.fillRect(0, hdrH, sw, barY-hdrH);
        UI.fillRR(g, Pal.surface(t), 4, hdrH+4, sw-8, barY-hdrH-8, 8);
        UI.divider(g, Pal.border(t), 8, hdrH+sh2+12, sw-16);
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString("Full message  (FIRE=close)", sw/2, hdrH+6, Graphics.TOP|Graphics.HCENTER);
        int ty=hdrH+sh2+16-detailScroll;
        Vector lines=wrapTxt(detailText, sw-24);
        for (int i=0;i<lines.size();i++) {
            if (ty+fh>hdrH&&ty<barY) { g.setColor(Pal.textPri(t)); g.setFont(font); g.drawString((String)lines.elementAt(i), 12, ty, Graphics.TOP|Graphics.LEFT); }
            ty+=fh+2;
        }
    }

    private void paintBar(Graphics g, int t) {
        int fh=font.getHeight(), sh2=tiny.getHeight();
        g.setColor(Pal.surface(t)); g.fillRect(0, barY, sw, barH);
        UI.divider(g, Pal.border(t), 0, barY, sw);

        // Scroll indicator: show "^ scrolled" badge when not at bottom
        if (scrollY > 0) {
            int badgeH = sh2 + 6;
            int badgeW = tiny.stringWidth("v latest") + 16;
            int badgeX = sw/2 - badgeW/2;
            int badgeY = barY - badgeH - 4;
            UI.fillRR(g, Pal.accent(t), badgeX, badgeY, badgeW, badgeH, badgeH/2);
            g.setColor(Pal.userText(t)); g.setFont(tiny);
            g.drawString("v latest", badgeX+8, badgeY+(badgeH-sh2)/2, Graphics.TOP|Graphics.LEFT);
        }

        int drawY=barY+7;
        // [+] left circle
        boolean pFoc=(focusIdx==2&&!detailMode);
        int plusW=inputH+2, plusX=6;
        UI.fillCircle(g, pFoc?Pal.accent(t):Pal.surface2(t), plusX+plusW/2, drawY+inputH/2, plusW/2);
        if (pFoc) UI.drawCircle(g, Pal.textPri(t), plusX+plusW/2, drawY+inputH/2, plusW/2);
        g.setColor(pFoc?Pal.userText(t):Pal.textSec(t)); g.setFont(bold);
        g.drawString("+", plusX+plusW/2-bold.stringWidth("+")/2, drawY+inputH/2-bold.getHeight()/2, Graphics.TOP|Graphics.LEFT);

        // [>] right circle
        boolean sFoc=(focusIdx==1&&!detailMode);
        int sendW=inputH+2, sendX=sw-sendW-6;
        UI.fillCircle(g, loading?Pal.border(t):(sFoc?Pal.accent(t):Pal.accentDk(t)), sendX+sendW/2, drawY+inputH/2, sendW/2);
        if (sFoc) UI.drawCircle(g, Pal.textPri(t), sendX+sendW/2, drawY+inputH/2, sendW/2);
        g.setColor(Pal.userText(t)); g.setFont(bold);
        g.drawString(">", sendX+sendW/2-bold.stringWidth(">")/2, drawY+inputH/2-bold.getHeight()/2, Graphics.TOP|Graphics.LEFT);

        // Pill input (center)
        boolean tFoc=(focusIdx==0&&!detailMode);
        int boxX=plusX+plusW+5, boxW=sendX-boxX-5;
        UI.fillRR(g, tFoc?Pal.surface2(t):Pal.surface(t), boxX, drawY, boxW, inputH, inputH/2);
        UI.drawRR(g, tFoc?Pal.accent(t):Pal.border(t),    boxX, drawY, boxW, inputH, inputH/2);

        if (loading) {
            g.setColor(Pal.textSec(t)); g.setFont(tiny);
            g.drawString("AI is responding...", boxX+10, drawY+inputH/2-tiny.getHeight()/2, Graphics.TOP|Graphics.LEFT);
        } else {
            String disp=detailMode?"Reading...":currentInput;
            if (disp.length()==0 && !detailMode) {
                g.setColor(Pal.textSec(t)); g.setFont(font);
                g.drawString("Message AIChatBot...", boxX+10, drawY+inputH/2-fh/2, Graphics.TOP|Graphics.LEFT);
            } else {
                int maxTW=boxW-20;
                if (font.stringWidth(disp)>maxTW) {
                    int fit=maxTW/font.charWidth('m');
                    if (fit>0&&fit<disp.length()) disp=".."+disp.substring(disp.length()-fit);
                }
                g.setColor(Pal.textPri(t)); g.setFont(font);
                g.drawString(disp, boxX+10, drawY+inputH/2-fh/2, Graphics.TOP|Graphics.LEFT);
                if (tFoc&&cursorOn&&!loading) {
                    int cx=boxX+10+font.stringWidth(disp);
                    g.setColor(Pal.accent(t)); g.fillRect(cx, drawY+4, 2, inputH-8);
                }
            }
        }
    }

    private void paintSheet(Graphics g, int t) {
        // Dim background
        int alpha=0x99;
        for (int i=0;i<barY;i+=2) { g.setColor(Pal.bg(t)&0x333333); g.drawLine(0,i,sw,i); }

        int iH=font.getHeight()+12;
        int shH=MENU.length*iH+36;
        int shY=sh-shH;

        UI.fillRR(g, Pal.surface(t), 0, shY, sw, shH, 14);
        UI.drawRR(g, Pal.border(t),  0, shY, sw, shH, 14);

        // Handle
        g.setColor(Pal.border(t));
        g.fillRoundRect(sw/2-16, shY+7, 32, 3, 3, 3);

        // Title
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString("Actions", sw/2, shY+14, Graphics.TOP|Graphics.HCENTER);
        UI.divider(g, Pal.border(t), 8, shY+14+tiny.getHeight()+3, sw-16);

        int iy=shY+20+tiny.getHeight();
        for (int i=0;i<MENU.length;i++) {
            boolean sel=(i==menuSel);
            if (sel) {
                UI.fillRR(g, Pal.surface2(t), 8, iy, sw-16, iH, 6);
                g.setColor(Pal.accent(t)); g.fillRect(8, iy, 3, iH);
            }
            g.setColor(sel?Pal.textPri(t):Pal.textSec(t));
            g.setFont(sel?bold:font);
            g.drawString(MENU[i], 20, iy+(iH-font.getHeight())/2, Graphics.TOP|Graphics.LEFT);
            if (sel) { g.setColor(Pal.accent(t)); g.setFont(tiny); g.drawString(">", sw-18, iy+(iH-tiny.getHeight())/2, Graphics.TOP|Graphics.LEFT); }
            iy+=iH;
        }
    }

    // ---- Key handling ----

    protected void keyPressed(int keyCode) {
        int a = getGameAction(keyCode);
        if (menuOpen)   { doMenuKey(keyCode, a); repaint(); return; }
        if (detailMode) { doDetailKey(keyCode, a); repaint(); return; }
        doMainKey(keyCode, a);
        repaint();
    }

    private void doMenuKey(int k, int a) {
        if      (a==UP  ||k==Canvas.KEY_NUM2) { menuSel--; if(menuSel<0) menuSel=MENU.length-1; }
        else if (a==DOWN||k==Canvas.KEY_NUM8) { menuSel++; if(menuSel>=MENU.length) menuSel=0; }
        else if (a==FIRE||k==Canvas.KEY_NUM5||k==-5||k==10) execMenu();
        else if (k==-7||k==-22||a==LEFT) menuOpen=false;
    }

    private void execMenu() {
        menuOpen = false;
        switch (menuSel) {
            case 0: doSend();             break;
            case 1: doWebSearch();        break;
            case 2: copyLastAI();         break;
            case 3: clearChat();          break;
            case 4: saveChat();           break;
            case 5: newChat();            break;
            case 6: midlet.showMainMenu(); break;
        }
    }

    private void doDetailKey(int k, int a) {
        if      (a==UP  ||k==Canvas.KEY_NUM2) { detailScroll -= font.getHeight()*3; if(detailScroll<0) detailScroll=0; }
        else if (a==DOWN||k==Canvas.KEY_NUM8)   detailScroll += font.getHeight()*3;
        else if (a==FIRE||k==10)                detailMode=false;
    }

    // Unified key handler - scroll always works, quick reply nav when showQuick
    private void doMainKey(int k, int a) {
        int step = font.getHeight() * 4;

        if (showQuick && messages.size() <= 1) {
            // D-pad navigates quick reply cards
            if (a==UP || k==Canvas.KEY_NUM2) {
                quickSel = (quickSel - 1 + QUICK_LABEL.length) % QUICK_LABEL.length;
            } else if (a==DOWN || k==Canvas.KEY_NUM8) {
                quickSel = (quickSel + 1) % QUICK_LABEL.length;
            } else if (a==FIRE || k==Canvas.KEY_NUM5 || k==-5 || k==10) {
                // Fill input with the prompt for that quick reply and dismiss chips
                currentInput = QUICK_PROMPT[quickSel];
                showQuick = false;
                // For "Hello!" send immediately, others let user complete
                if (quickSel == 0) {
                    doSend();
                }
                // else: user sees filled input and can edit before sending
            } else if (a==LEFT) {
                focusIdx--; if(focusIdx<0) focusIdx=2;
            } else if (a==RIGHT) {
                focusIdx = (focusIdx+1) % 3;
            } else if (k==-6||k==-21) {
                menuOpen=true; menuSel=0;
            } else if (k==-7||k==-22) {
                if(currentInput.length()>0) currentInput=currentInput.substring(0,currentInput.length()-1);
            }
            return;
        }

        // Normal mode
        if (a==UP || k==Canvas.KEY_NUM2) {
            int max = getMaxScroll();
            if (max > 0) { scrollY += step; if(scrollY>max) scrollY=max; }
        }
        else if (a==DOWN || k==Canvas.KEY_NUM8) {
            scrollY -= step;
            if (scrollY < 0) scrollY = 0;
        }
        else if (a==LEFT) {
            focusIdx--; if(focusIdx<0) focusIdx=2;
        }
        else if (a==RIGHT) {
            focusIdx = (focusIdx+1) % 3;
        }
        else if (a==FIRE || k==Canvas.KEY_NUM5 || k==-5 || k==10) {
            if      (focusIdx==0) { if(!loading) midlet.openNativeInput(this); }
            else if (focusIdx==1) { if(!loading) doSend(); }
            else if (focusIdx==2) { menuOpen=true; menuSel=0; }
        }
        else if (k==-6 || k==-21) { menuOpen=true; menuSel=0; }
        else if (k==-7 || k==-22) {
            if (currentInput.length()>0)
                currentInput = currentInput.substring(0, currentInput.length()-1);
        }
    }

    // ---- API ----

    private void doSend() {
        String msg=currentInput.trim(); if(msg.length()==0) return;
        addMessage("User: "+msg);
        midlet.getHistory().addUserMessage(msg);
        currentInput="";
        fetchAI(msg);
    }

    private void doWebSearch() {
        String q=currentInput.trim(); if(q.length()==0) q="latest AI news";
        addMessage("User: [WEB] "+q);
        midlet.getHistory().addUserMessage("[WEB] "+q);
        currentInput="";
        fetchAI("Search the web and summarize results for: "+q);
    }

    private void copyLastAI() {
        String last=midlet.getHistory().getLastAIResponse();
        if (last!=null) midlet.showCopyBox(last, "Last AI Response");
        else addMessage("AI: No response to copy yet.");
    }

    private void fetchAI(final String text) {
        loading=true; repaint();
        final String ctx=midlet.getSettings().isContextEnabled()
            ?midlet.getHistory().getContext(midlet.getSettings().getMaxContext()):"";
        new Thread(new Runnable(){public void run(){
            try {
                String full=text;
                if(ctx.length()>0) full="Context:\n"+ctx+"\n\nQuestion:\n"+text;
                String url=API+URLBuilder.encode(full);
                if(midlet.getSettings().isProxyEnabled()) url=PROXY+URLBuilder.encode(url);

                HttpConnection hc=(HttpConnection)Connector.open(url);
                hc.setRequestMethod(HttpConnection.GET);
                hc.setRequestProperty("User-Agent","J2ME-AIChatBot/1.5");
                hc.setRequestProperty("Connection","close");

                String res;
                int rc=hc.getResponseCode();
                if(rc==HttpConnection.HTTP_OK){
                    InputStream is=hc.openInputStream();
                    ByteArrayOutputStream bo=new ByteArrayOutputStream();
                    byte[]buf=new byte[512];int n;
                    while((n=is.read(buf))!=-1) bo.write(buf,0,n);
                    is.close();
                    String raw=new String(bo.toByteArray(),"UTF-8");
                    res=parseResp(raw);
                    String sess=pJson(raw,"\"session\":\"","\"");
                    if(sess.length()>0) session=sess;
                } else res="[HTTP "+rc+"]";
                hc.close();
                addMessage("AI: "+res);
                midlet.getHistory().addAIMessage(res);
                // Auto-save history after every AI reply
                midlet.getHistory().saveToRMS(midlet.getHistStore());
            } catch(Exception e){ addMessage("Error: "+e.getMessage()); }
            finally{ loading=false; repaint(); }
        }}).start();
    }

    private String parseResp(String r){
        if(r==null||r.length()==0) return "[Empty response]";
        String[]keys={"result","answer","text","content","response"};
        for(int k=0;k<keys.length;k++){String v=pJson(r,"\""+keys[k]+"\":\"","\"");if(v.length()>0)return clean(unesc(v));}
        return clean(Utils.stripHtml(r));
    }
    private String pJson(String s,String key,String end){
        int i=s.indexOf(key);if(i<0)return "";i+=key.length();int e=i;
        while(e<s.length()){char c=s.charAt(e);if(c==end.charAt(0)&&(e==0||s.charAt(e-1)!='\\'))break;e++;}
        return e>i?s.substring(i,e):"";
    }
    private String unesc(String s){
        StringBuffer b=new StringBuffer();int i=0;
        while(i<s.length()){char c=s.charAt(i);
            if(c=='\\'&&i+1<s.length()){char n=s.charAt(i+1);
                if(n=='n'){b.append('\n');i+=2;continue;}if(n=='r'){b.append('\r');i+=2;continue;}
                if(n=='t'){b.append('\t');i+=2;continue;}if(n=='"'){b.append('"');i+=2;continue;}
                if(n=='\\'){b.append('\\');i+=2;continue;}}
            b.append(c);i++;}
        return b.toString();
    }
    private String clean(String s){
        if(s==null)return "";StringBuffer b=new StringBuffer();
        for(int i=0;i<s.length();i++){char c=s.charAt(i);
            if((c>=32&&c<=126)||(c>=160&&c<=255)||c=='\n'||c=='\r'||c=='\t')b.append(c);
            else if(c>255)b.append('?');}
        return b.toString().trim();
    }

    private boolean hasCode(String t){return t.indexOf("```")!=-1;}
    private String extractCode(String t){
        int s=t.indexOf("```");if(s<0)return null;s+=3;
        if(s<t.length()&&t.charAt(s)=='\n')s++;
        int e=t.indexOf("```",s);return e<0?t.substring(s):t.substring(s,e);
    }

    public void showDetail(String txt){ detailText=txt;detailMode=true;detailScroll=0;repaint(); }

    public void saveAsBmp(String ascii){
        try{
            Vector lines=new Vector();int s=0;
            while(s<ascii.length()){int nl=ascii.indexOf('\n',s);if(nl<0){lines.addElement(ascii.substring(s));break;}lines.addElement(ascii.substring(s,nl));s=nl+1;}
            int mc=0;for(int i=0;i<lines.size();i++){int l=((String)lines.elementAt(i)).length();if(l>mc)mc=l;}
            int cW=6,cH=8,iW=mc*cW,iH=lines.size()*cH,rs=(iW*3+3)/4*4,ps=rs*iH,fs=54+ps;
            byte[]b=new byte[fs];b[0]=66;b[1]=77;wi(b,2,fs);wi(b,6,0);wi(b,10,54);wi(b,14,40);wi(b,18,iW);wi(b,22,-iH);ws(b,26,1);ws(b,28,24);wi(b,30,0);wi(b,34,ps);wi(b,38,2835);wi(b,42,2835);wi(b,46,0);wi(b,50,0);
            for(int r=0;r<lines.size();r++){String ln=(String)lines.elementAt(r);for(int c=0;c<mc;c++){char ch=(c<ln.length())?ln.charAt(c):' ';boolean lit=(ch!=' ');for(int py=0;py<cH;py++)for(int px=0;px<cW;px++){int o=54+(r*cH+py)*rs+(c*cW+px)*3;b[o]=(byte)(lit?136:0);b[o+1]=(byte)(lit?255:0);b[o+2]=0;}}}
            String path=rootPath()+"ascii.bmp";
            FileConnection fc=(FileConnection)Connector.open(path,Connector.READ_WRITE);
            if(!fc.exists())fc.create();
            OutputStream os=fc.openOutputStream();os.write(b);os.flush();os.close();fc.close();
            addMessage("AI: Saved -> "+path);
        }catch(Exception e){addMessage("Error: "+e.getMessage());}
    }
    private String rootPath(){
        try{FileConnection fc=(FileConnection)Connector.open("file:///c:/predefgallery/predeffilereceived/",1);boolean ex=fc.exists();fc.close();if(ex)return "file:///c:/predefgallery/predeffilereceived/";}catch(Exception e){}
        try{Enumeration r=FileSystemRegistry.listRoots();if(r.hasMoreElements())return "file:///"+r.nextElement();}catch(Exception e){}
        return "file:///root/";
    }
    private void wi(byte[]b,int o,int v){b[o]=(byte)(v&0xFF);b[o+1]=(byte)(v>>8&0xFF);b[o+2]=(byte)(v>>16&0xFF);b[o+3]=(byte)(v>>24&0xFF);}
    private void ws(byte[]b,int o,int v){b[o]=(byte)(v&0xFF);b[o+1]=(byte)(v>>8&0xFF);}

    private void clearChat(){ messages=new Vector();showQuick=true;quickSel=0;addMessage("AI: Chat cleared.");midlet.getHistory().clearCurrent(); }
    private void newChat()  { midlet.getHistory().saveCurrentSession();messages=new Vector();session="";showQuick=true;quickSel=0;addMessage("AI: New conversation started."); }
    private void saveChat() {
        String c=midlet.getHistory().getAllConversationsAsString();
        boolean ok=midlet.getSaveManager().saveConversation(c,"txt",midlet.getUserId());
        addMessage(ok?"AI: Saved to "+midlet.getSaveManager().getSavePath():"AI: Save failed.");
    }

    private int getMaxScroll(){
        int tot=0;for(int i=0;i<messages.size();i++)tot+=((ChatMessage)messages.elementAt(i)).height+8;
        return Math.max(0,tot-(barY-hdrH));
    }
    private Vector wrapTxt(String text,int maxW){
        Vector l=new Vector();int s=0,len=text.length();
        while(s<len){int nl=text.indexOf('\n',s);int e=(nl!=-1)?nl:len;String seg=text.substring(s,e);
            if(font.stringWidth(seg)<=maxW)l.addElement(seg);
            else{String tmp="";for(int i=0;i<seg.length();i++){char c=seg.charAt(i);if(font.stringWidth(tmp+c)>maxW){l.addElement(tmp);tmp=""+c;}else tmp+=c;}if(tmp.length()>0)l.addElement(tmp);}
            s=e+((nl!=-1)?1:0);}
        if(l.size()==0)l.addElement("");return l;
    }
    public String getCurrentInput()       {return currentInput;}
    public void setCurrentInput(String s) {currentInput=s==null?"":s;repaint();}
}


// ================================================================
// HistoryScreen - sessions list with stats + clear button
// ================================================================
class HistoryScreen extends Canvas {
    private AIChatBot midlet;
    private String[] convs;
    private int scroll=0,theme,sw,sh;
    private Font font,bold,tiny;

    public HistoryScreen(AIChatBot m){
        midlet=m;theme=m.getSettings().getThemeIndex();
        font=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        bold=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD, Font.SIZE_SMALL);
        tiny=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        convs=m.getHistory().getAllConversations();
        setFullScreenMode(true);
    }

    protected void showNotify(){sw=getWidth();sh=getHeight();theme=midlet.getSettings().getThemeIndex();repaint();}

    protected void paint(Graphics g){
        int t=theme,fh=font.getHeight(),bh=bold.getHeight(),sh2=tiny.getHeight();
        g.setColor(Pal.bg(t));g.fillRect(0,0,sw,sh);
        int hdrH=UI.drawHeader(g,t,sw,bold,tiny,"History",convs.length+" sessions");

        // Stats bar
        int stH=sh2+8;
        g.setColor(Pal.surface(t));g.fillRect(0,hdrH,sw,stH);
        UI.divider(g,Pal.border(t),0,hdrH+stH,sw);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString("Total msgs: "+midlet.getHistory().getTotalMessageCount()
            +"  |  Sessions: "+midlet.getHistory().getSessionCount(), 8, hdrH+4, Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.danger(t));g.setFont(tiny);
        g.drawString("Clear all >", sw-6, hdrH+4, Graphics.TOP|Graphics.RIGHT);

        int listTop=hdrH+stH+4;
        int softH=sh2+6;
        g.setClip(0,listTop,sw,sh-listTop-softH);
        int y=listTop+4-scroll;

        if(convs.length==0){
            g.setColor(Pal.textSec(t));g.setFont(font);
            g.drawString("No history yet.",sw/2,sh/2,Graphics.TOP|Graphics.HCENTER);
        } else {
            for(int i=0;i<convs.length;i++){
                int cardH=bh+sh2+16;
                if(y+cardH>listTop&&y<sh-softH){
                    UI.fillRR(g,Pal.surface(t),8,y,sw-16,cardH,8);
                    // Number badge
                    UI.fillCircle(g,Pal.surface2(t),8+sh2/2+2,y+cardH/2,sh2/2+2);
                    g.setColor(Pal.textSec(t));g.setFont(tiny);
                    g.drawString(""+(i+1),8+4,y+cardH/2-sh2/2,Graphics.TOP|Graphics.LEFT);
                    // Text preview
                    String tx=convs[i];
                    int nl=tx.indexOf('\n');
                    String l1=nl>0?tx.substring(0,nl):tx;
                    if(l1.length()>34)l1=l1.substring(0,32)+"..";
                    g.setColor(Pal.textPri(t));g.setFont(bold);
                    g.drawString(l1,sh2+18,y+6,Graphics.TOP|Graphics.LEFT);
                    String rest=nl>0?tx.substring(nl+1):"";
                    int nl2=rest.indexOf('\n');if(nl2>0)rest=rest.substring(0,nl2);
                    if(rest.length()>38)rest=rest.substring(0,36)+"..";
                    g.setColor(Pal.textSec(t));g.setFont(tiny);
                    g.drawString(rest,sh2+18,y+8+bh,Graphics.TOP|Graphics.LEFT);
                }
                y+=cardH+5;
            }
        }
        g.setClip(0,0,sw,sh);
        UI.drawFooter(g,t,sw,sh,tiny,"2/8:scroll  *:clear  LSK:back");
    }

    protected void keyPressed(int k){
        int a=getGameAction(k);
        if(a==UP||k==Canvas.KEY_NUM2)scroll=Math.max(0,scroll-25);
        else if(a==DOWN||k==Canvas.KEY_NUM8)scroll+=25;
        else if(k==Canvas.KEY_STAR)midlet.confirmClearHistory();
        else if(k==-6||k==-21||a==FIRE)midlet.showMainMenu();
        repaint();
    }
}


// ================================================================
// SettingsScreen - settings apply IMMEDIATELY on every change
// LSK / Back = save and return to menu
// No "Save button" needed - every L/R/FIRE change writes to RMS
// ================================================================
class SettingsScreen extends Canvas {
    private AIChatBot midlet;
    private int sel=0, sw, sh;
    private Font font, bold, tiny;

    // These are LIVE references - editing them edits settings directly
    private int     timeout;
    private boolean ctxEnabled;
    private int     maxCtx;
    private boolean proxyEnabled;
    private int     themeIdx;

    // Number of rows: Timeout, Context, MaxCtx, Proxy, Theme, Edit Profile
    private static final int NROWS = 6;

    public SettingsScreen(AIChatBot m) {
        midlet = m;
        font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        bold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD,  Font.SIZE_SMALL);
        tiny = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        // Read current values
        syncFromSettings();
        setFullScreenMode(true);
    }

    private void syncFromSettings() {
        timeout     = midlet.getSettings().getTimeout() / 1000;
        ctxEnabled  = midlet.getSettings().isContextEnabled();
        maxCtx      = midlet.getSettings().getMaxContext();
        proxyEnabled= midlet.getSettings().isProxyEnabled();
        themeIdx    = midlet.getSettings().getThemeIndex();
    }

    // Write local values into Settings and persist immediately
    private void applyAndSave() {
        midlet.getSettings().setTimeoutSilent(timeout * 1000);
        midlet.getSettings().setContextEnabledSilent(ctxEnabled);
        midlet.getSettings().setMaxContextSilent(maxCtx);
        midlet.getSettings().setProxyEnabledSilent(proxyEnabled);
        midlet.getSettings().setThemeIndexSilent(themeIdx);
        midlet.getSettings().saveAll();
        midlet.savePrefs(); // keep main prefs in sync
    }

    protected void showNotify() { sw=getWidth(); sh=getHeight(); repaint(); }

    protected void paint(Graphics g) {
        // Always paint with the CURRENTLY SELECTED themeIdx so preview is live
        int t = themeIdx;
        int fh=font.getHeight(), bh=bold.getHeight(), sh2=tiny.getHeight();

        g.setColor(Pal.bg(t)); g.fillRect(0, 0, sw, sh);
        int hdrH = UI.drawHeader(g, t, sw, bold, tiny, "Settings", "Back=save");

        int y = hdrH + 10, rowH = fh + 18;

        String[] labs = { "Timeout", "Context", "Max ctx", "Proxy", "Theme", "Edit Profile" };
        String[] vals = {
            timeout + "s",
            ctxEnabled  ? "ON" : "OFF",
            "" + maxCtx,
            proxyEnabled ? "ON" : "OFF",
            Pal.name(themeIdx),
            ">"   // Profile is a navigation action
        };

        for (int i = 0; i < labs.length; i++) {
            boolean foc = (i == sel);
            int ry = y + i * (rowH + 4);

            // Focused row highlight
            if (foc) {
                UI.fillRR(g, Pal.surface(t), 4, ry, sw-8, rowH, 8);
                g.setColor(Pal.accent(t)); g.fillRect(4, ry, 3, rowH);
            }

            // Label
            g.setColor(foc ? Pal.textPri(t) : Pal.textSec(t));
            g.setFont(foc ? bold : font);
            g.drawString(labs[i], 16, ry + (rowH-fh)/2, Graphics.TOP | Graphics.LEFT);

            // Value pill (skip for Profile row)
            if (i < 5) {
                int vW = tiny.stringWidth(vals[i]) + 24;
                int vH = sh2 + 8;
                int vX = sw - vW - 8;
                int vY = ry + (rowH - vH) / 2;
                UI.fillRR(g, foc ? Pal.accent(t) : Pal.surface2(t), vX, vY, vW, vH, vH/2);
                g.setColor(foc ? Pal.userText(t) : Pal.textSec(t));
                g.setFont(tiny);
                g.drawString(foc ? "< " + vals[i] + " >" : vals[i], vX + 8, vY + (vH-sh2)/2, Graphics.TOP | Graphics.LEFT);
            } else {
                // Profile row: just a chevron
                g.setColor(foc ? Pal.accent(t) : Pal.textSec(t));
                g.setFont(bold);
                g.drawString(">", sw - 18, ry + (rowH - bh)/2, Graphics.TOP | Graphics.LEFT);
            }
        }

        // Storage info at bottom
        int infoY = y + labs.length * (rowH + 4) + 10;
        UI.divider(g, Pal.border(t), 8, infoY, sw-16);
        g.setColor(Pal.textSec(t)); g.setFont(tiny);
        g.drawString("Path: " + midlet.getSaveManager().getSavePath(), 8, infoY + 6, Graphics.TOP | Graphics.LEFT);

        UI.drawFooter(g, t, sw, sh, tiny, "L/R:change  2/8:select  LSK:save+back");
    }

    protected void keyPressed(int k) {
        int a = getGameAction(k);
        if      (a==UP   || k==Canvas.KEY_NUM2) { sel=(sel-1+NROWS)%NROWS; }
        else if (a==DOWN || k==Canvas.KEY_NUM8) { sel=(sel+1)%NROWS; }
        else if (a==LEFT)  { adj(-1); applyAndSave(); }
        else if (a==RIGHT) { adj(1);  applyAndSave(); }
        else if (a==FIRE || k==Canvas.KEY_NUM5 || k==-5 || k==10) {
            if (sel == 5) {
                applyAndSave();
                midlet.showProfile();
                return;
            }
            adj(1); applyAndSave();
        }
        // LSK = save all and go back
        else if (k==-6 || k==-21) {
            applyAndSave();
            midlet.showMainMenu();
            return;
        }
        repaint();
    }

    private void adj(int d) {
        switch (sel) {
            case 0: timeout     = Math.max(5,  Math.min(120, timeout + d*5)); break;
            case 1: ctxEnabled  = !ctxEnabled;                                 break;
            case 2: maxCtx      = Math.max(1,  Math.min(20,  maxCtx  + d));   break;
            case 3: proxyEnabled= !proxyEnabled;                               break;
            case 4: themeIdx    = (themeIdx + Pal.count() + d) % Pal.count(); break;
        }
    }
}


// ================================================================
// AboutScreen
// ================================================================
class AboutScreen extends Canvas {
    private AIChatBot midlet;
    private int theme,sw,sh;
    private Font font,bold,tiny;

    public AboutScreen(AIChatBot m){
        midlet=m;theme=m.getSettings().getThemeIndex();
        font=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        bold=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_BOLD, Font.SIZE_SMALL);
        tiny=Font.getFont(Font.FACE_SYSTEM,Font.STYLE_PLAIN,Font.SIZE_SMALL);
        setFullScreenMode(true);
    }

    protected void showNotify(){sw=getWidth();sh=getHeight();theme=midlet.getSettings().getThemeIndex();repaint();}

    protected void paint(Graphics g){
        int t=theme,fh=font.getHeight(),bh=bold.getHeight(),sh2=tiny.getHeight();
        g.setColor(Pal.bg(t));g.fillRect(0,0,sw,sh);
        int hdrH=UI.drawHeader(g,t,sw,bold,tiny,"About",null);

        int y=hdrH+10;
        // App card
        int cH=40;
        UI.fillRR(g,Pal.surface(t),8,y,sw-16,cH,10);
        int avR=14,avCX=8+avR+8,avCY=y+cH/2;
        UI.fillCircle(g,Pal.accent(t),avCX,avCY,avR);
        g.setColor(Pal.userText(t));g.setFont(bold);
        g.drawString("AI",avCX-bold.stringWidth("AI")/2,avCY-bh/2,Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textPri(t));g.setFont(bold);
        g.drawString(AIChatBot.APP_NAME+" "+AIChatBot.VERSION,avCX+avR+8,y+6,Graphics.TOP|Graphics.LEFT);
        g.setColor(Pal.textSec(t));g.setFont(tiny);
        g.drawString("Powered by ChatGPT API",avCX+avR+8,y+6+bh+2,Graphics.TOP|Graphics.LEFT);

        y+=cH+10;
        String[][]info={
            {"Name",  midlet.getUserName()},
            {"Role",  midlet.getUserRole()},
            {"Lang",  midlet.getUserLang()},
            {"ID",    midlet.getUserId()},
            {"Theme", Pal.name(midlet.getSettings().getThemeIndex())},
            {"Proxy", midlet.getSettings().isProxyEnabled()?"ON":"OFF"},
            {"Files", midlet.getSaveManager().isFileAPIAvailable()?"Yes":"No"},
            {"Msgs",  ""+midlet.getHistory().getTotalMessageCount()},
        };
        int rowH=fh+12;
        for(int i=0;i<info.length;i++){
            int ry=y+i*(rowH+2);
            if(i%2==0){g.setColor(Pal.surface(t));g.fillRect(8,ry,sw-16,rowH);}
            g.setColor(Pal.textSec(t));g.setFont(tiny);g.drawString(info[i][0],16,ry+(rowH-sh2)/2,Graphics.TOP|Graphics.LEFT);
            g.setColor(Pal.textPri(t));g.setFont(font);g.drawString(info[i][1],sw-12,ry+(rowH-fh)/2,Graphics.TOP|Graphics.RIGHT);
        }

        UI.drawFooter(g,t,sw,sh,tiny,"FIRE or LSK: back");
    }

    protected void keyPressed(int k){
        int a=getGameAction(k);
        if(k==-6||k==-21||a==FIRE)midlet.showMainMenu();
        repaint();
    }
}


// ================================================================
// FileViewerScreen - browse files, send to AI
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

    protected void showNotify(){sw=getWidth();sh=getHeight();theme=midlet.getSettings().getThemeIndex();loadRoots();}

    private void loadRoots(){
        entries=new Vector();sel=0;scroll=0;curPath=null;loading=true;repaint();
        new Thread(new Runnable(){public void run(){
            try{Enumeration r=FileSystemRegistry.listRoots();
                while(r.hasMoreElements()){String rt=(String)r.nextElement();entries.addElement(new String[]{"["+rt+"]","dir","file:///"+rt});}
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
                if(fc.isDirectory()){Enumeration lst=fc.list();while(lst.hasMoreElements()){String n=(String)lst.nextElement();boolean d=n.endsWith("/");entries.addElement(new String[]{d?"["+n.substring(0,n.length()-1)+"]":n,d?"dir":"file",path+n});}}
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
                int max=2000;byte[]buf=new byte[(int)Math.min(sz,max)];int n=is.read(buf);
                String raw=n>0?new String(buf,0,n):"(empty)";
                StringBuffer sb=new StringBuffer();
                for(int i=0;i<raw.length();i++){char c=raw.charAt(i);if((c>=32&&c<=126)||(c>=160&&c<=255)||c=='\n'||c=='\r')sb.append(c);}
                String fname=path.substring(path.lastIndexOf('/')+1);
                String prompt="File: "+fname+"\nSize: "+sz+" bytes\n---\n"+sb.toString()+"\n---\nBriefly describe this file.";
                midlet.showChat();
                ChatCanvas cc=midlet.getChatCanvas();
                if(cc!=null){cc.addMessage("User: [FILE] "+fname);cc.setCurrentInput(prompt);}
            }catch(Exception e){status="Read error: "+e.getMessage();loading=false;repaint();}
            finally{try{if(is!=null)is.close();}catch(Exception x){}try{if(fc!=null)fc.close();}catch(Exception x){}}
        }}).start();
    }

    protected void paint(Graphics g){
        int t=theme,fh=font.getHeight(),bh=bold.getHeight(),sh2=tiny.getHeight();
        g.setColor(Pal.bg(t));g.fillRect(0,0,sw,sh);
        int hdrH=UI.drawHeader(g,t,sw,bold,tiny,"File Browser",null);

        // Path bar
        int pathH=sh2+8;
        g.setColor(Pal.surface(t));g.fillRect(0,hdrH,sw,pathH);
        UI.divider(g,Pal.border(t),0,hdrH+pathH,sw);
        g.setColor(Pal.textSec(t));g.setFont(tiny);g.drawString(status,6,hdrH+4,Graphics.TOP|Graphics.LEFT);

        int listTop=hdrH+pathH+4,softH=sh2+6,listH=sh-listTop-softH;
        int iH=fh+12;

        if(loading){g.setColor(Pal.accent(t));g.setFont(font);g.drawString("Loading...",sw/2,listTop+listH/2,Graphics.TOP|Graphics.HCENTER);}
        else if(entries.size()==0){g.setColor(Pal.textSec(t));g.setFont(font);g.drawString("(empty)",sw/2,listTop+listH/2,Graphics.TOP|Graphics.HCENTER);}
        else{
            g.setClip(0,listTop,sw,listH);
            int y=listTop-scroll;
            for(int i=0;i<entries.size();i++){
                String[]e=(String[])entries.elementAt(i);
                boolean isDir=e[1].equals("dir"),foc=(i==sel);
                if(foc){UI.fillRR(g,Pal.surface(t),4,y,sw-8,iH,6);g.setColor(Pal.accent(t));g.fillRect(4,y,3,iH);}
                g.setColor(isDir?Pal.accent(t):(foc?Pal.textPri(t):Pal.textSec(t)));
                g.setFont(foc?bold:font);
                String name=e[0];
                int maxNW=sw-28;
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
        int iH=font.getHeight()+12,listH=sh-bold.getHeight()-tiny.getHeight()*2-24,vis=listH/iH;
        if(a==UP||k==Canvas.KEY_NUM2){sel=Math.max(0,sel-1);if(sel*iH<scroll)scroll=sel*iH;}
        else if(a==DOWN||k==Canvas.KEY_NUM8){sel=Math.min(entries.size()-1,sel+1);if(sel>=(scroll/iH)+vis)scroll=Math.max(0,(sel-vis+1)*iH);}
        else if(a==FIRE||k==Canvas.KEY_NUM5||k==-5||k==10){
            if(entries.size()>0){
                String[]e=(String[])entries.elementAt(sel);
                if(e[2]==null){if(curPath!=null){String p=curPath;if(p.endsWith("/"))p=p.substring(0,p.length()-1);int last=p.lastIndexOf('/');if(last>8)loadDir(p.substring(0,last+1));else loadRoots();}else loadRoots();}
                else if(e[1].equals("dir"))loadDir(e[2]);
                else readFile(e[2]);
            }
        }
        else if(k==-6||k==-21){if(curPath!=null){String p=curPath;if(p.endsWith("/"))p=p.substring(0,p.length()-1);int last=p.lastIndexOf('/');if(last>8)loadDir(p.substring(0,last+1));else loadRoots();}else midlet.showMainMenu();}
        repaint();
    }
}


// ================================================================
// ChatMessage - wrapping + render metadata
// ================================================================
class ChatMessage {
    public String originalText;
    public Vector wrappedLines;
    public boolean isUser;
    public int height;

    public ChatMessage(String text, int maxWidth, Font font) {
        originalText=text;
        isUser=text.startsWith("User:");
        wrappedLines=wrap(text, maxWidth-12, font);
        int h=10;
        for(int i=0;i<wrappedLines.size();i++)
            if(!((String)wrappedLines.elementAt(i)).trim().equals("```"))h+=font.getHeight()+2;
        height=h;
    }

    private Vector wrap(String text, int maxW, Font font){
        Vector l=new Vector();int s=0,len=text.length();
        while(s<len){int nl=text.indexOf('\n',s);int e=(nl!=-1)?nl:len;String seg=text.substring(s,e);
            if(font.stringWidth(seg)<=maxW)l.addElement(seg);
            else{String t2="";for(int i=0;i<seg.length();i++){char c=seg.charAt(i);if(font.stringWidth(t2+c)>maxW){if(t2.length()>0)l.addElement(t2);t2=""+c;}else t2+=c;}if(t2.length()>0)l.addElement(t2);}
            s=e+((nl!=-1)?1:0);}
        if(l.size()==0)l.addElement("");return l;
    }
}