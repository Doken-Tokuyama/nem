import java.util.concurrent.*;
import javax.servlet.*;
import java.math.*;
import org.json.simple.*;
import java.io.*;

static class User
{
    final ConcurrentLinkedQueue<JSONObject> pendingResponses;
    AsyncContext asyncContext;
    volatile boolean isInactive;
    volatile String secretPhrase;
    volatile byte[] publicKey;
    
    User() {
        super();
        this.pendingResponses = new ConcurrentLinkedQueue<JSONObject>();
    }
    
    void deinitializeKeyPair() {
        this.secretPhrase = null;
        this.publicKey = null;
    }
    
    BigInteger initializeKeyPair(final String secretPhrase) {
        this.publicKey = Crypto.getPublicKey(secretPhrase);
        this.secretPhrase = secretPhrase;
        final byte[] publicKeyHash = Nxt.getMessageDigest("SHA-256").digest(this.publicKey);
        return new BigInteger(1, new byte[] { publicKeyHash[7], publicKeyHash[6], publicKeyHash[5], publicKeyHash[4], publicKeyHash[3], publicKeyHash[2], publicKeyHash[1], publicKeyHash[0] });
    }
    
    void send(final JSONObject response) {
        synchronized (this) {
            if (this.asyncContext == null) {
                if (this.isInactive) {
                    return;
                }
                if (this.pendingResponses.size() > 1000) {
                    this.pendingResponses.clear();
                    this.isInactive = true;
                    if (this.secretPhrase == null) {
                        Nxt.users.values().remove(this);
                    }
                    return;
                }
                this.pendingResponses.offer(response);
            }
            else {
                final JSONArray responses = new JSONArray();
                JSONObject pendingResponse;
                while ((pendingResponse = this.pendingResponses.poll()) != null) {
                    responses.add((Object)pendingResponse);
                }
                responses.add((Object)response);
                final JSONObject combinedResponse = new JSONObject();
                combinedResponse.put((Object)"responses", (Object)responses);
                this.asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");
                try (final Writer writer = this.asyncContext.getResponse().getWriter()) {
                    combinedResponse.writeJSONString(writer);
                }
                catch (IOException e) {
                    Nxt.logMessage("Error sending response to user", e);
                }
                this.asyncContext.complete();
                this.asyncContext = null;
            }
        }
    }
}
