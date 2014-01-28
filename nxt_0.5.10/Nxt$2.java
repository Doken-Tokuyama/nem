import java.util.concurrent.*;

class Nxt$2 implements Runnable {
    @Override
    public void run() {
        try {
            if (Peer.getNumberOfConnectedPublicPeers() < Nxt.maxNumberOfConnectedPublicPeers) {
                final Peer peer = Peer.getAnyPeer((ThreadLocalRandom.current().nextInt(2) == 0) ? 0 : 2, false);
                if (peer != null) {
                    peer.connect();
                }
            }
        }
        catch (Exception e) {
            Nxt.logDebugMessage("Error connecting to peer", e);
        }
        catch (Throwable t) {
            Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }
    }
}