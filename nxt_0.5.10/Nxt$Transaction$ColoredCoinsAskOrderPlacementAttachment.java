import java.io.*;
import java.nio.*;
import org.json.simple.*;

static class ColoredCoinsAskOrderPlacementAttachment implements Attachment, Serializable
{
    static final long serialVersionUID = 0L;
    long asset;
    int quantity;
    long price;
    
    ColoredCoinsAskOrderPlacementAttachment(final long asset, final int quantity, final long price) {
        super();
        this.asset = asset;
        this.quantity = quantity;
        this.price = price;
    }
    
    @Override
    public int getSize() {
        return 20;
    }
    
    @Override
    public byte[] getBytes() {
        final ByteBuffer buffer = ByteBuffer.allocate(this.getSize());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(this.asset);
        buffer.putInt(this.quantity);
        buffer.putLong(this.price);
        return buffer.array();
    }
    
    @Override
    public JSONObject getJSONObject() {
        final JSONObject attachment = new JSONObject();
        attachment.put((Object)"asset", (Object)Nxt.convert(this.asset));
        attachment.put((Object)"quantity", (Object)this.quantity);
        attachment.put((Object)"price", (Object)this.price);
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
