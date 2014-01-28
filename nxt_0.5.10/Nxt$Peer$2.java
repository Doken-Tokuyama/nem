import java.util.concurrent.*;
import org.json.simple.*;

static final class Nxt$Peer$2 implements Callable<JSONObject> {
    final /* synthetic */ Peer val$peer;
    final /* synthetic */ JSONStreamAware val$jsonStreamAware;
    
    @Override
    public JSONObject call() {
        return this.val$peer.send(this.val$jsonStreamAware);
    }
}