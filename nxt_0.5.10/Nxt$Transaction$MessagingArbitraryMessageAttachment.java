import java.io.*;
import java.nio.*;
import org.json.simple.*;

static class MessagingArbitraryMessageAttachment implements Attachment, Serializable
{
    static final long serialVersionUID = 0L;
    final byte[] message;
    
    MessagingArbitraryMessageAttachment(final byte[] message) {
        super();
        this.message = message;
    }
    
    @Override
    public int getSize() {
        return 4 + this.message.length;
    }
    
    @Override
    public byte[] getBytes() {
        final ByteBuffer buffer = ByteBuffer.allocate(this.getSize());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(this.message.length);
        buffer.put(this.message);
        return buffer.array();
    }
    
    @Override
    public JSONObject getJSONObject() {
        final JSONObject attachment = new JSONObject();
        attachment.put((Object)"message", (Object)Nxt.convert(this.message));
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
