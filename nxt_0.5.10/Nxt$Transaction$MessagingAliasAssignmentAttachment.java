import java.io.*;
import java.nio.*;
import org.json.simple.*;

static class MessagingAliasAssignmentAttachment implements Attachment, Serializable
{
    static final long serialVersionUID = 0L;
    final String alias;
    final String uri;
    
    MessagingAliasAssignmentAttachment(final String alias, final String uri) {
        super();
        this.alias = alias;
        this.uri = uri;
    }
    
    @Override
    public int getSize() {
        try {
            return 1 + this.alias.getBytes("UTF-8").length + 2 + this.uri.getBytes("UTF-8").length;
        }
        catch (RuntimeException | UnsupportedEncodingException e) {
            Nxt.logMessage("Error in getBytes", e);
            return 0;
        }
    }
    
    @Override
    public byte[] getBytes() {
        try {
            final byte[] alias = this.alias.getBytes("UTF-8");
            final byte[] uri = this.uri.getBytes("UTF-8");
            final ByteBuffer buffer = ByteBuffer.allocate(1 + alias.length + 2 + uri.length);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.put((byte)alias.length);
            buffer.put(alias);
            buffer.putShort((short)uri.length);
            buffer.put(uri);
            return buffer.array();
        }
        catch (RuntimeException | UnsupportedEncodingException e) {
            Nxt.logMessage("Error in getBytes", e);
            return null;
        }
    }
    
    @Override
    public JSONObject getJSONObject() {
        final JSONObject attachment = new JSONObject();
        attachment.put((Object)"alias", (Object)this.alias);
        attachment.put((Object)"uri", (Object)this.uri);
        return attachment;
    }
    
    @Override
    public long getRecipientDeltaBalance() {
        return 0L;
    }
    
    @Override
    public long getSenderDeltaBalance() {
        return 0L;
    }
}
