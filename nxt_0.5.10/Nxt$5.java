import org.json.simple.*;

class Nxt$5 implements Runnable {
    private final JSONObject getUnconfirmedTransactionsRequest;
    
    {
        (this.getUnconfirmedTransactionsRequest = new JSONObject()).put((Object)"requestType", (Object)"getUnconfirmedTransactions");
    }
    
    @Override
    public void run() {
        try {
            final Peer peer = Peer.getAnyPeer(1, true);
            if (peer != null) {
                final JSONObject response = peer.send(this.getUnconfirmedTransactionsRequest);
                if (response != null) {
                    Transaction.processTransactions(response, "unconfirmedTransactions");
                }
            }
        }
        catch (Exception e) {
            Nxt.logDebugMessage("Error processing unconfirmed transactions from peer", e);
        }
        catch (Throwable t) {
            Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }
    }
}