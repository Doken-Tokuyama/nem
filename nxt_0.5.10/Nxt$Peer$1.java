import org.json.simple.*;
import java.io.*;

static final class Nxt$Peer$1 implements JSONStreamAware {
    final char[] jsonChars = this.val$request.toJSONString().toCharArray();
    final /* synthetic */ JSONObject val$request;
    
    public void writeJSONString(final Writer out) throws IOException {
        out.write(this.jsonChars);
    }
}