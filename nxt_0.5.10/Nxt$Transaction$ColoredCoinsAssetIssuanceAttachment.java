import java.io.*;
import java.nio.*;
import org.json.simple.*;

static class ColoredCoinsAssetIssuanceAttachment implements Attachment, Serializable
{
    static final long serialVersionUID = 0L;
    String name;
    String description;
    int quantity;
    
    ColoredCoinsAssetIssuanceAttachment(final String name, final String description, final int quantity) {
        super();
        this.name = name;
        this.description = ((description == null) ? "" : description);
        this.quantity = quantity;
    }
    
    @Override
    public int getSize() {
        try {
            return 1 + this.name.getBytes("UTF-8").length + 2 + this.description.getBytes("UTF-8").length + 4;
        }
        catch (RuntimeException | UnsupportedEncodingException e) {
            Nxt.logMessage("Error in getBytes", e);
            return 0;
        }
    }
    
    @Override
    public byte[] getBytes() {
        try {
            final byte[] name = this.name.getBytes("UTF-8");
            final byte[] description = this.description.getBytes("UTF-8");
            final ByteBuffer buffer = ByteBuffer.allocate(1 + name.length + 2 + description.length + 4);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.put((byte)name.length);
            buffer.put(name);
            buffer.putShort((short)description.length);
            buffer.put(description);
            buffer.putInt(this.quantity);
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
        attachment.put((Object)"name", (Object)this.name);
        attachment.put((Object)"description", (Object)this.description);
        attachment.put((Object)"quantity", (Object)this.quantity);
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
