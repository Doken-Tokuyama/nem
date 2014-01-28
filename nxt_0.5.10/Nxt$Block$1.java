import org.json.simple.*;
import java.io.*;

class Nxt$Block$1 implements JSONStreamAware {
    private char[] jsonChars = Block.this.getJSONObject().toJSONString().toCharArray();
    
    public void writeJSONString(final Writer out) throws IOException {
        out.write(this.jsonChars);
    }
}