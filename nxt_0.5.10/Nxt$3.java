import java.util.*;

class Nxt$3 implements Runnable {
    @Override
    public void run() {
        try {
            final long curTime = System.currentTimeMillis();
            for (final Peer peer : Nxt.peers.values()) {
                if (peer.blacklistingTime > 0L && peer.blacklistingTime + Nxt.blacklistingPeriod <= curTime) {
                    peer.removeBlacklistedStatus();
                }
            }
        }
        catch (Exception e) {
            Nxt.logDebugMessage("Error un-blacklisting peer", e);
        }
        catch (Throwable t) {
            Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }
    }
}