import java.math.*;
import java.util.concurrent.*;
import org.json.simple.*;
import java.util.*;
import java.security.*;

class Nxt$8 implements Runnable {
    private final ConcurrentMap<Account, Block> lastBlocks = new ConcurrentHashMap<Account, Block>();
    private final ConcurrentMap<Account, BigInteger> hits = new ConcurrentHashMap<Account, BigInteger>();
    
    @Override
    public void run() {
        try {
            final HashMap<Account, User> unlockedAccounts = new HashMap<Account, User>();
            for (final User user : Nxt.users.values()) {
                if (user.secretPhrase != null) {
                    final Account account = Nxt.accounts.get(Account.getId(user.publicKey));
                    if (account == null || account.getEffectiveBalance() <= 0) {
                        continue;
                    }
                    unlockedAccounts.put(account, user);
                }
            }
            for (final Map.Entry<Account, User> unlockedAccountEntry : unlockedAccounts.entrySet()) {
                final Account account = unlockedAccountEntry.getKey();
                final User user2 = unlockedAccountEntry.getValue();
                final Block lastBlock = Nxt.lastBlock.get();
                if (this.lastBlocks.get(account) != lastBlock) {
                    final long effectiveBalance = account.getEffectiveBalance();
                    if (effectiveBalance <= 0L) {
                        continue;
                    }
                    final MessageDigest digest = Nxt.getMessageDigest("SHA-256");
                    byte[] generationSignatureHash;
                    if (lastBlock.height < 30000) {
                        final byte[] generationSignature = Crypto.sign(lastBlock.generationSignature, user2.secretPhrase);
                        generationSignatureHash = digest.digest(generationSignature);
                    }
                    else {
                        digest.update(lastBlock.generationSignature);
                        generationSignatureHash = digest.digest(user2.publicKey);
                    }
                    final BigInteger hit = new BigInteger(1, new byte[] { generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5], generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2], generationSignatureHash[1], generationSignatureHash[0] });
                    this.lastBlocks.put(account, lastBlock);
                    this.hits.put(account, hit);
                    final JSONObject response = new JSONObject();
                    response.put((Object)"response", (Object)"setBlockGenerationDeadline");
                    response.put((Object)"deadline", (Object)(hit.divide(BigInteger.valueOf(lastBlock.baseTarget).multiply(BigInteger.valueOf(effectiveBalance))).longValue() - (Nxt.getEpochTime(System.currentTimeMillis()) - lastBlock.timestamp)));
                    user2.send(response);
                }
                final int elapsedTime = Nxt.getEpochTime(System.currentTimeMillis()) - lastBlock.timestamp;
                if (elapsedTime > 0) {
                    final BigInteger target = BigInteger.valueOf(lastBlock.baseTarget).multiply(BigInteger.valueOf(account.getEffectiveBalance())).multiply(BigInteger.valueOf(elapsedTime));
                    if (this.hits.get(account).compareTo(target) >= 0) {
                        continue;
                    }
                    account.generateBlock(user2.secretPhrase);
                }
            }
        }
        catch (Exception e) {
            Nxt.logDebugMessage("Error in block generation thread", e);
        }
        catch (Throwable t) {
            Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }
    }
}