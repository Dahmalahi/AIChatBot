import java.util.Vector;
import javax.microedition.rms.*;

// ================================================================
// History.java v1.5
// NEW: saveToRMS() / loadFromRMS() - history persists across sessions
// ================================================================
public class History {

    private Vector sessions;        // past sessions (String)
    private Vector currentSession;  // live entries
    private int maxMessages  = 50;
    private int maxSessions  = 10;
    private int totalMessageCount = 0;

    private static final char PU  = 'U';
    private static final char PA  = 'A';
    private static final char SEP = '|';
    // Separator between saved sessions in RMS
    private static final String SESSION_SEP = "\n===SESSION===\n";

    public History() {
        sessions       = new Vector();
        currentSession = new Vector();
    }

    // ---- Add ----

    public void addUserMessage(String msg) { addMsg(PU, msg); }
    public void addAIMessage(String msg)   { addMsg(PA, msg); }

    private void addMsg(char type, String msg) {
        if (msg==null||msg.length()==0) return;
        StringBuffer e=new StringBuffer();
        e.append(type).append(SEP).append(Utils.getCurrentTimestamp()).append(SEP).append(msg);
        currentSession.addElement(e.toString());
        totalMessageCount++;
        while(currentSession.size()>maxMessages) currentSession.removeElementAt(0);
    }

    // ---- Context for API ----

    public String getContext(int maxExchanges) {
        int sz=currentSession.size(); if(sz==0) return "";
        StringBuffer ctx=new StringBuffer();
        int need=maxExchanges*2, start=(sz>need)?sz-need:0;
        for(int i=start;i<sz;i++){
            String entry=(String)currentSession.elementAt(i);
            ctx.append(entry.charAt(0)==PU?"User: ":"Assistant: ");
            ctx.append(extractMsg(entry)).append('\n');
        }
        return ctx.toString();
    }

    private String extractMsg(String entry) {
        if(entry==null||entry.length()<3) return "";
        int p1=entry.indexOf(SEP); if(p1<0) return entry;
        int p2=entry.indexOf(SEP,p1+1); if(p2<0||p2+1>=entry.length()) return "";
        return entry.substring(p2+1);
    }

    // ---- Session management ----

    public void saveCurrentSession() {
        if(currentSession.size()==0) return;
        StringBuffer sb=new StringBuffer();
        sb.append("Session ").append(Utils.getCurrentTimestamp())
          .append(" (").append(currentSession.size()).append(" msgs)\n");
        for(int i=0;i<currentSession.size();i++){
            String entry=(String)currentSession.elementAt(i);
            sb.append(entry.charAt(0)==PU?"  > ":"  < ");
            sb.append(extractMsg(entry)).append('\n');
        }
        sessions.addElement(sb.toString());
        while(sessions.size()>maxSessions) sessions.removeElementAt(0);
        currentSession=new Vector();
    }

    // ---- RMS Persistence ----

    public void saveToRMS(String storeName) {
        RecordStore rs = null;
        try {
            // Serialize all sessions + current into one string
            StringBuffer sb = new StringBuffer();
            // Save past sessions
            for (int i=0;i<sessions.size();i++) {
                if (i>0) sb.append(SESSION_SEP);
                sb.append((String)sessions.elementAt(i));
            }
            // Save current session entries (raw)
            if (currentSession.size()>0) {
                if (sessions.size()>0) sb.append(SESSION_SEP);
                sb.append("__CURRENT__\n");
                for (int i=0;i<currentSession.size();i++) {
                    sb.append((String)currentSession.elementAt(i)).append('\n');
                }
                sb.append("__MSGCOUNT__").append(totalMessageCount).append('\n');
            } else {
                sb.append("__MSGCOUNT__").append(totalMessageCount).append('\n');
            }

            try { RecordStore.deleteRecordStore(storeName); } catch (Exception e) {}
            rs = RecordStore.openRecordStore(storeName, true);
            byte[] data = sb.toString().getBytes("UTF-8");
            // Split into chunks of 8KB if needed (RMS record size limit on some devices)
            int chunk = 7900, off = 0;
            while (off < data.length) {
                int len = Math.min(chunk, data.length - off);
                rs.addRecord(data, off, len);
                off += len;
            }
        } catch (Exception e) {
        } finally {
            try { if(rs!=null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    public void loadFromRMS(String storeName) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(storeName, false);
            int n = rs.getNumRecords();
            if (n == 0) { rs.closeRecordStore(); return; }
            // Reassemble chunks
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int i=1;i<=n;i++) {
                byte[] chunk = rs.getRecord(i);
                baos.write(chunk, 0, chunk.length);
            }
            rs.closeRecordStore();

            String data = new String(baos.toByteArray(), "UTF-8");

            // Parse sessions
            sessions       = new Vector();
            currentSession = new Vector();

            // Split by SESSION_SEP
            int pos = 0;
            while (pos < data.length()) {
                int sep = data.indexOf(SESSION_SEP, pos);
                String block = (sep >= 0) ? data.substring(pos, sep) : data.substring(pos);
                pos = (sep >= 0) ? sep + SESSION_SEP.length() : data.length();

                if (block.startsWith("__CURRENT__\n")) {
                    // Parse raw current session entries
                    String cur = block.substring("__CURRENT__\n".length());
                    // Strip __MSGCOUNT__ line
                    int mc = cur.indexOf("__MSGCOUNT__");
                    if (mc >= 0) { cur = cur.substring(0, mc); }
                    String[] lines = splitLines(cur);
                    for (int i=0;i<lines.length;i++) {
                        if (lines[i].trim().length()>0) currentSession.addElement(lines[i]);
                    }
                } else if (block.startsWith("__MSGCOUNT__")) {
                    try {
                        String mc = block.substring("__MSGCOUNT__".length()).trim();
                        totalMessageCount = Integer.parseInt(mc);
                    } catch (Exception x) {}
                } else if (block.trim().length() > 0) {
                    // Check if last segment contains __MSGCOUNT__
                    int mc2 = block.indexOf("__MSGCOUNT__");
                    if (mc2 >= 0) {
                        String sessBlock = block.substring(0, mc2);
                        try {
                            String mcStr = block.substring(mc2 + "__MSGCOUNT__".length()).trim();
                            int nl = mcStr.indexOf('\n');
                            totalMessageCount = Integer.parseInt(nl>0?mcStr.substring(0,nl):mcStr);
                        } catch (Exception x) {}
                        if (sessBlock.trim().length()>0) sessions.addElement(sessBlock);
                    } else {
                        sessions.addElement(block);
                    }
                }
            }
        } catch (RecordStoreNotFoundException e) {
            // No history saved yet - that's fine
        } catch (Exception e) {
        } finally {
            try { if(rs!=null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    private String[] splitLines(String text) {
        Vector v=new Vector(); int s=0;
        while(s<text.length()){int e=text.indexOf('\n',s);if(e<0){v.addElement(text.substring(s));break;}v.addElement(text.substring(s,e));s=e+1;}
        String[]r=new String[v.size()];for(int i=0;i<v.size();i++)r[i]=(String)v.elementAt(i);
        return r;
    }

    // ---- All conversations ----

    public String[] getAllConversations() {
        int total=sessions.size()+(currentSession.size()>0?1:0);
        if(total==0) return new String[0];
        String[]result=new String[total];
        for(int i=0;i<sessions.size();i++) result[i]=(String)sessions.elementAt(i);
        if(currentSession.size()>0) result[result.length-1]=formatCurrent();
        return result;
    }

    private String formatCurrent(){
        StringBuffer sb=new StringBuffer();
        sb.append(">> Current (").append(currentSession.size()).append(" msgs)\n");
        for(int i=0;i<currentSession.size();i++){
            String e=(String)currentSession.elementAt(i);
            sb.append(e.charAt(0)==PU?"  > ":"  < ").append(extractMsg(e)).append('\n');
        }
        return sb.toString();
    }

    public String getAllConversationsAsString(){
        StringBuffer sb=new StringBuffer();
        sb.append("==============================\n  AIChatBot v1.5 - HISTORY\n==============================\n\n");
        String[]convs=getAllConversations();
        if(convs.length==0){sb.append("(No history)\n");return sb.toString();}
        for(int i=0;i<convs.length;i++){sb.append("[").append(i+1).append("] ").append(convs[i]).append("\n------------------------------\n\n");}
        sb.append("Total: ").append(convs.length).append(" session(s)\nMessages: ").append(totalMessageCount).append("\n");
        return sb.toString();
    }

    // ---- Last messages ----

    public String getLastAIResponse(){
        for(int i=currentSession.size()-1;i>=0;i--){String e=(String)currentSession.elementAt(i);if(e.charAt(0)==PA)return extractMsg(e);}return null;
    }
    public String getLastUserMessage(){
        for(int i=currentSession.size()-1;i>=0;i--){String e=(String)currentSession.elementAt(i);if(e.charAt(0)==PU)return extractMsg(e);}return null;
    }

    // ---- Clear ----

    public void clearCurrent()  { currentSession=new Vector(); }
    public void clearAll()      { sessions=new Vector();currentSession=new Vector();totalMessageCount=0; }

    // ---- Stats ----

    public int     getSessionCount()       { return sessions.size(); }
    public int     getTotalMessageCount()  { return totalMessageCount; }
    public int     getCurrentSessionSize() { return currentSession.size(); }
    public boolean hasHistory()            { return currentSession.size()>0||sessions.size()>0; }
    public boolean isCurrentSessionEmpty() { return currentSession.size()==0; }

    // ---- Compact context ----

    public String getCompactContext(int maxExchanges){
        int sz=currentSession.size();if(sz==0)return "";
        StringBuffer ctx=new StringBuffer();int need=maxExchanges*2,start=(sz>need)?sz-need:0;
        for(int i=start;i<sz;i++){String e=(String)currentSession.elementAt(i);String msg=extractMsg(e);if(msg.length()>80)msg=msg.substring(0,77)+"...";ctx.append(e.charAt(0)==PU?"U:":"A:").append(msg).append('\n');}
        return ctx.toString();
    }

    public String search(String keyword){
        if(keyword==null||keyword.length()==0)return "No keyword.";
        String kl=keyword.toLowerCase();StringBuffer res=new StringBuffer();int count=0;
        for(int i=0;i<currentSession.size();i++){String e=(String)currentSession.elementAt(i);String msg=extractMsg(e);if(msg.toLowerCase().indexOf(kl)>=0){res.append("- ").append(msg.length()>50?msg.substring(0,47)+"...":msg).append('\n');count++;}}
        return count==0?"No results for: "+keyword:"Found "+count+" result(s):\n"+res.toString();
    }
}

// Simple BAOS for J2ME
class ByteArrayOutputStream extends java.io.OutputStream {
    private byte[] buf = new byte[256];
    private int count = 0;
    private void grow(int need){
        if(count+need<=buf.length)return;
        int newLen=Math.max(buf.length*2,count+need);
        byte[]nb=new byte[newLen];System.arraycopy(buf,0,nb,0,count);buf=nb;
    }
    public void write(int b){grow(1);buf[count++]=(byte)b;}
    public void write(byte[]b,int off,int len){grow(len);System.arraycopy(b,off,buf,count,len);count+=len;}
    public byte[]toByteArray(){byte[]r=new byte[count];System.arraycopy(buf,0,r,0,count);return r;}
}
