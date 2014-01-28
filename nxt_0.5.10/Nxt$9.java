import org.json.simple.*;
import java.util.*;

class Nxt$9 implements Runnable {
    @Override
    public void run() {
        try {
            final JSONArray transactionsData = new JSONArray();
            for (final Transaction transaction : Nxt.nonBroadcastedTransactions.values()) {
                if (Nxt.unconfirmedTransactions.get(transaction.id) == null && Nxt.transactions.get(transaction.id) == null) {
                    transactionsData.add((Object)transaction.getJSONObject());
                }
                else {
                    Nxt.nonBroadcastedTransactions.remove(transaction.id);
                }
            }
            if (transactionsData.size() > 0) {
                final JSONObject peerRequest = new JSONObject();
                peerRequest.put((Object)"requestType", (Object)"processTransactions");
                peerRequest.put((Object)"transactions", (Object)transactionsData);
                Peer.sendToSomePeers(peerRequest);
            }
        }
        catch (Exception e) {
            Nxt.logDebugMessage("Error in transaction re-broadcasting thread", e);
        }
        catch (Throwable t) {
            Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }
    }
}