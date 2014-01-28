import java.util.concurrent.atomic.*;
import java.security.*;
import org.json.simple.*;
import java.math.*;
import java.util.*;

static class Account
{
    final long id;
    private long balance;
    final int height;
    final AtomicReference<byte[]> publicKey;
    private final Map<Long, Integer> assetBalances;
    private long unconfirmedBalance;
    private final Map<Long, Integer> unconfirmedAssetBalances;
    
    private Account(final long id) {
        super();
        this.publicKey = new AtomicReference<byte[]>();
        this.assetBalances = new HashMap<Long, Integer>();
        this.unconfirmedAssetBalances = new HashMap<Long, Integer>();
        this.id = id;
        this.height = Nxt.lastBlock.get().height;
    }
    
    static Account addAccount(final long id) {
        final Account account = new Account(id);
        Nxt.accounts.put(id, account);
        return account;
    }
    
    boolean setOrVerify(final byte[] key) {
        return this.publicKey.compareAndSet(null, key) || Arrays.equals(key, this.publicKey.get());
    }
    
    void generateBlock(final String secretPhrase) {
        final Set<Transaction> sortedTransactions = new TreeSet<Transaction>();
        for (final Transaction transaction : Nxt.unconfirmedTransactions.values()) {
            if (transaction.referencedTransaction == 0L || Nxt.transactions.get(transaction.referencedTransaction) != null) {
                sortedTransactions.add(transaction);
            }
        }
        final Map<Long, Transaction> newTransactions = new HashMap<Long, Transaction>();
        final Set<String> newAliases = new HashSet<String>();
        final Map<Long, Long> accumulatedAmounts = new HashMap<Long, Long>();
        int payloadLength = 0;
        while (payloadLength <= 32640) {
            final int prevNumberOfNewTransactions = newTransactions.size();
            for (final Transaction transaction2 : sortedTransactions) {
                final int transactionLength = transaction2.getSize();
                if (newTransactions.get(transaction2.getId()) == null && payloadLength + transactionLength <= 32640) {
                    final long sender = transaction2.getSenderAccountId();
                    Long accumulatedAmount = accumulatedAmounts.get(sender);
                    if (accumulatedAmount == null) {
                        accumulatedAmount = 0L;
                    }
                    final long amount = (transaction2.amount + transaction2.fee) * 100L;
                    if (accumulatedAmount + amount > Nxt.accounts.get(sender).getBalance() || !transaction2.validateAttachment()) {
                        continue;
                    }
                    Label_0359: {
                        switch (transaction2.type) {
                            case 1: {
                                switch (transaction2.subtype) {
                                    case 1: {
                                        if (!newAliases.add(((Transaction.MessagingAliasAssignmentAttachment)transaction2.attachment).alias.toLowerCase())) {
                                            continue;
                                        }
                                        break Label_0359;
                                    }
                                }
                                break;
                            }
                        }
                    }
                    accumulatedAmounts.put(sender, accumulatedAmount + amount);
                    newTransactions.put(transaction2.getId(), transaction2);
                    payloadLength += transactionLength;
                }
            }
            if (newTransactions.size() == prevNumberOfNewTransactions) {
                break;
            }
        }
        final Block previousBlock = Nxt.lastBlock.get();
        Block block;
        if (previousBlock.height < 30000) {
            block = new Block(1, Nxt.getEpochTime(System.currentTimeMillis()), previousBlock.getId(), newTransactions.size(), 0, 0, 0, null, Crypto.getPublicKey(secretPhrase), null, new byte[64]);
        }
        else {
            final byte[] previousBlockHash = Nxt.getMessageDigest("SHA-256").digest(previousBlock.getBytes());
            block = new Block(2, Nxt.getEpochTime(System.currentTimeMillis()), previousBlock.getId(), newTransactions.size(), 0, 0, 0, null, Crypto.getPublicKey(secretPhrase), null, new byte[64], previousBlockHash);
        }
        int i = 0;
        for (final Map.Entry<Long, Transaction> transactionEntry : newTransactions.entrySet()) {
            final Transaction transaction3 = transactionEntry.getValue();
            final Block block2 = block;
            block2.totalAmount += transaction3.amount;
            final Block block3 = block;
            block3.totalFee += transaction3.fee;
            final Block block4 = block;
            block4.payloadLength += transaction3.getSize();
            block.transactions[i++] = transactionEntry.getKey();
        }
        Arrays.sort(block.transactions);
        final MessageDigest digest = Nxt.getMessageDigest("SHA-256");
        for (i = 0; i < block.transactions.length; ++i) {
            final Transaction transaction4 = newTransactions.get(block.transactions[i]);
            digest.update(transaction4.getBytes());
            block.blockTransactions[i] = transaction4;
        }
        block.payloadHash = digest.digest();
        if (previousBlock.height < 30000) {
            block.generationSignature = Crypto.sign(previousBlock.generationSignature, secretPhrase);
        }
        else {
            digest.update(previousBlock.generationSignature);
            block.generationSignature = digest.digest(Crypto.getPublicKey(secretPhrase));
        }
        final byte[] data = block.getBytes();
        final byte[] data2 = new byte[data.length - 64];
        System.arraycopy(data, 0, data2, 0, data2.length);
        block.blockSignature = Crypto.sign(data2, secretPhrase);
        if (block.verifyBlockSignature() && block.verifyGenerationSignature()) {
            final JSONObject request = block.getJSONObject();
            request.put((Object)"requestType", (Object)"processBlock");
            Peer.sendToSomePeers(request);
        }
        else {
            Nxt.logMessage("Generated an incorrect block. Waiting for the next one...");
        }
    }
    
    int getEffectiveBalance() {
        final Block lastBlock = Nxt.lastBlock.get();
        if (lastBlock.height >= 51000 || this.height >= 47000) {
            return (int)(this.getGuaranteedBalance(1440) / 100L);
        }
        if (this.height == 0) {
            return (int)(this.getBalance() / 100L);
        }
        if (lastBlock.height - this.height < 1440) {
            return 0;
        }
        int receivedInlastBlock = 0;
        for (final Transaction transaction : lastBlock.blockTransactions) {
            if (transaction.recipient == this.id) {
                receivedInlastBlock += transaction.amount;
            }
        }
        return (int)(this.getBalance() / 100L) - receivedInlastBlock;
    }
    
    static long getId(final byte[] publicKey) {
        final byte[] publicKeyHash = Nxt.getMessageDigest("SHA-256").digest(publicKey);
        final BigInteger bigInteger = new BigInteger(1, new byte[] { publicKeyHash[7], publicKeyHash[6], publicKeyHash[5], publicKeyHash[4], publicKeyHash[3], publicKeyHash[2], publicKeyHash[1], publicKeyHash[0] });
        return bigInteger.longValue();
    }
    
    synchronized Integer getAssetBalance(final Long assetId) {
        return this.assetBalances.get(assetId);
    }
    
    synchronized Integer getUnconfirmedAssetBalance(final Long assetId) {
        return this.unconfirmedAssetBalances.get(assetId);
    }
    
    synchronized void addToAssetBalance(final Long assetId, final int quantity) {
        final Integer assetBalance = this.assetBalances.get(assetId);
        if (assetBalance == null) {
            this.assetBalances.put(assetId, quantity);
        }
        else {
            this.assetBalances.put(assetId, assetBalance + quantity);
        }
    }
    
    synchronized void addToUnconfirmedAssetBalance(final Long assetId, final int quantity) {
        final Integer unconfirmedAssetBalance = this.unconfirmedAssetBalances.get(assetId);
        if (unconfirmedAssetBalance == null) {
            this.unconfirmedAssetBalances.put(assetId, quantity);
        }
        else {
            this.unconfirmedAssetBalances.put(assetId, unconfirmedAssetBalance + quantity);
        }
    }
    
    synchronized void addToAssetAndUnconfirmedAssetBalance(final Long assetId, final int quantity) {
        final Integer assetBalance = this.assetBalances.get(assetId);
        if (assetBalance == null) {
            this.assetBalances.put(assetId, quantity);
            this.unconfirmedAssetBalances.put(assetId, quantity);
        }
        else {
            this.assetBalances.put(assetId, assetBalance + quantity);
            this.unconfirmedAssetBalances.put(assetId, this.unconfirmedAssetBalances.get(assetId) + quantity);
        }
    }
    
    synchronized long getBalance() {
        return this.balance;
    }
    
    long getGuaranteedBalance(final int numberOfConfirmations) {
        long guaranteedBalance = this.getBalance();
        final ArrayList<Block> lastBlocks = Block.getLastBlocks(numberOfConfirmations - 1);
        final byte[] accountPublicKey = this.publicKey.get();
        for (final Block block : lastBlocks) {
            if (Arrays.equals(block.generatorPublicKey, accountPublicKey) && (guaranteedBalance -= block.totalFee * 100L) <= 0L) {
                return 0L;
            }
            int i = block.blockTransactions.length;
            while (i-- > 0) {
                final Transaction transaction = block.blockTransactions[i];
                if (Arrays.equals(transaction.senderPublicKey, accountPublicKey)) {
                    final long deltaBalance = transaction.getSenderDeltaBalance();
                    if (deltaBalance > 0L && (guaranteedBalance -= deltaBalance) <= 0L) {
                        return 0L;
                    }
                    if (deltaBalance < 0L && (guaranteedBalance += deltaBalance) <= 0L) {
                        return 0L;
                    }
                }
                if (transaction.recipient == this.id) {
                    final long deltaBalance = transaction.getRecipientDeltaBalance();
                    if (deltaBalance > 0L && (guaranteedBalance -= deltaBalance) <= 0L) {
                        return 0L;
                    }
                    if (deltaBalance < 0L && (guaranteedBalance += deltaBalance) <= 0L) {
                        return 0L;
                    }
                    continue;
                }
            }
        }
        return guaranteedBalance;
    }
    
    synchronized long getUnconfirmedBalance() {
        return this.unconfirmedBalance;
    }
    
    void addToBalance(final long amount) {
        synchronized (this) {
            this.balance += amount;
        }
        this.updatePeerWeights();
    }
    
    void addToUnconfirmedBalance(final long amount) {
        synchronized (this) {
            this.unconfirmedBalance += amount;
        }
        this.updateUserUnconfirmedBalance();
    }
    
    void addToBalanceAndUnconfirmedBalance(final long amount) {
        synchronized (this) {
            this.balance += amount;
            this.unconfirmedBalance += amount;
        }
        this.updatePeerWeights();
        this.updateUserUnconfirmedBalance();
    }
    
    private void updatePeerWeights() {
        for (final Peer peer : Nxt.peers.values()) {
            if (peer.accountId == this.id && peer.adjustedWeight > 0L) {
                peer.updateWeight();
            }
        }
    }
    
    private void updateUserUnconfirmedBalance() {
        final JSONObject response = new JSONObject();
        response.put((Object)"response", (Object)"setBalance");
        response.put((Object)"balance", (Object)this.getUnconfirmedBalance());
        final byte[] accountPublicKey = this.publicKey.get();
        for (final User user : Nxt.users.values()) {
            if (user.secretPhrase != null && Arrays.equals(user.publicKey, accountPublicKey)) {
                user.send(response);
            }
        }
    }
}
