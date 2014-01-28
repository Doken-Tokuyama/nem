import org.json.simple.*;
import java.util.*;

class Nxt$4 implements Runnable {
    private final JSONObject getPeersRequest;
    
    {
        (this.getPeersRequest = new JSONObject()).put((Object)"requestType", (Object)"getPeers");
    }
    
    @Override
    public void run() {
        try {
            final Peer peer = Peer.getAnyPeer(1, true);
            if (peer != null) {
                final JSONObject response = peer.send(this.getPeersRequest);
                if (response != null) {
                    final JSONArray peers = (JSONArray)response.get((Object)"peers");
                    for (final Object peerAddress : peers) {
                        final String address = ((String)peerAddress).trim();
                        if (address.length() > 0) {
                            Peer.addPeer(address, address);
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            Nxt.logDebugMessage("Error requesting peers from a peer", e);
        }
        catch (Throwable t) {
            Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }
    }
}