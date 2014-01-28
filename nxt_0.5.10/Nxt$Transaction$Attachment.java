import org.json.simple.*;

interface Attachment
{
    int getSize();
    
    byte[] getBytes();
    
    JSONObject getJSONObject();
    
    long getRecipientDeltaBalance();
    
    long getSenderDeltaBalance();
}
