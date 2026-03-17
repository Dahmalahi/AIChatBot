import java.util.Vector;

public class History {
    private Vector sessions;
    private Vector currentSession;
    private int maxMessages = 100;
    private int messageCount = 0;
    
    public History() {
        sessions = new Vector();
        currentSession = new Vector();
    }
    
    public void addUserMessage(String message) {
        String entry = "U:" + Utils.getCurrentTimestamp() + ":" + message;
        currentSession.addElement(entry);
        messageCount++;
        trimIfNeeded();
    }
    
    public void addAIMessage(String message) {
        String entry = "A:" + Utils.getCurrentTimestamp() + ":" + message;
        currentSession.addElement(entry);
        messageCount++;
        trimIfNeeded();
    }
    
    private void trimIfNeeded() {
        while (currentSession.size() > maxMessages) {
            currentSession.removeElementAt(0);
        }
    }
    
    public String getContext(int maxExchanges) {
        if (currentSession.size() == 0) {
            return "";
        }
        
        StringBuffer ctx = new StringBuffer();
        int start = Math.max(0, currentSession.size() - (maxExchanges * 2));
        
        for (int i = start; i < currentSession.size(); i++) {
            String entry = (String) currentSession.elementAt(i);
            
            if (entry.startsWith("U:")) {
                String msg = extractMessage(entry);
                ctx.append("Utilisateur: ").append(msg).append("\n");
            } else if (entry.startsWith("A:")) {
                String msg = extractMessage(entry);
                ctx.append("Assistant: ").append(msg).append("\n");
            }
        }
        
        return ctx.toString();
    }
    
    private String extractMessage(String entry) {
        int firstColon = entry.indexOf(':');
        if (firstColon < 0) return entry;
        
        int secondColon = entry.indexOf(':', firstColon + 1);
        if (secondColon < 0) return entry.substring(firstColon + 1);
        
        return entry.substring(secondColon + 1);
    }
    
    public void saveCurrentSession() {
        if (currentSession.size() > 0) {
            StringBuffer sb = new StringBuffer();
            sb.append("Session ").append(Utils.getCurrentTimestamp()).append("\n");
            
            for (int i = 0; i < currentSession.size(); i++) {
                String entry = (String) currentSession.elementAt(i);
                String msg = extractMessage(entry);
                
                if (entry.startsWith("U:")) {
                    sb.append("  > ").append(msg).append("\n");
                } else if (entry.startsWith("A:")) {
                    sb.append("  < ").append(msg).append("\n");
                }
            }
            
            sessions.addElement(sb.toString());
            currentSession = new Vector();
        }
    }
    
    public String[] getAllConversations() {
        int total = sessions.size();
        if (currentSession.size() > 0) {
            total++;
        }
        
        String[] result = new String[total];
        
        for (int i = 0; i < sessions.size(); i++) {
            result[i] = (String) sessions.elementAt(i);
        }
        
        if (currentSession.size() > 0) {
            StringBuffer current = new StringBuffer();
            current.append("Session en cours\n");
            
            for (int i = 0; i < currentSession.size(); i++) {
                String entry = (String) currentSession.elementAt(i);
                String msg = extractMessage(entry);
                
                if (entry.startsWith("U:")) {
                    current.append("  > ").append(msg).append("\n");
                } else if (entry.startsWith("A:")) {
                    current.append("  < ").append(msg).append("\n");
                }
            }
            
            result[result.length - 1] = current.toString();
        }
        
        return result;
    }
    
    public String getAllConversationsAsString() {
        StringBuffer sb = new StringBuffer();
        sb.append("==============================\n");
        sb.append("  HISTORIQUE AI CHATBOT\n");
        sb.append("==============================\n\n");
        
        String[] convs = getAllConversations();
        
        for (int i = 0; i < convs.length; i++) {
            sb.append("[").append(i + 1).append("] ");
            sb.append(convs[i]);
            sb.append("\n------------------------------\n\n");
        }
        
        sb.append("Total: ").append(convs.length).append(" session(s)\n");
        sb.append("Messages: ").append(messageCount).append("\n");
        
        return sb.toString();
    }
    
    public void clearCurrent() {
        currentSession = new Vector();
    }
    
    public void clearAll() {
        sessions = new Vector();
        currentSession = new Vector();
        messageCount = 0;
    }
    
    public int getSessionCount() {
        return sessions.size();
    }
    
    public int getMessageCount() {
        return messageCount;
    }
    
    public int getCurrentSessionSize() {
        return currentSession.size();
    }
}