import java.io.*;
import java.nio.*;
import org.json.simple.*;

static class ColoredCoinsBidOrderCancellationAttachment implements Attachment, Serializable
{
    static final long serialVersionUID = 0L;
    long order;
    
    ColoredCoinsBidOrderCancellationAttachment(final long order) {
        super();
        this.order = order;
    }
    
    @Override
    public int getSize() {
        return 8;
    }
    
    @Override
    public byte[] getBytes() {
        final ByteBuffer buffer = ByteBuffer.allocate(this.getSize());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(this.order);
        return buffer.array();
    }
    
    @Override
    public JSONObject getJSONObject() {
        final JSONObject attachment = new JSONObject();
        attachment.put((Object)"order", (Object)Nxt.convert(this.order));
        return attachment;
    }
    
    @Override
    public long getRecipientDeltaBalance() {
        return 0L;
    }
    
    @Override
    public long getSenderDeltaBalance() {
        final BidOrder bidOrder = Nxt.bidOrders.get(this.order);
        if (bidOrder == null) {
            return 0L;
        }
        return bidOrder.quantity * bidOrder.price;
    }
}
