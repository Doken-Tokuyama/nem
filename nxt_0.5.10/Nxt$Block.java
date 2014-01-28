import java.math.*;
import java.lang.ref.*;
import java.nio.*;
import org.json.simple.*;
import java.util.*;
import java.security.*;
import java.io.*;

static class Block implements Serializable
{
    static final long serialVersionUID = 0L;
    static final long[] emptyLong;
    static final Transaction[] emptyTransactions;
    final int version;
    final int timestamp;
    final long previousBlock;
    int totalAmount;
    int totalFee;
    int payloadLength;
    byte[] payloadHash;
    final byte[] generatorPublicKey;
    byte[] generationSignature;
    byte[] blockSignature;
    final byte[] previousBlockHash;
    int index;
    final long[] transactions;
    long baseTarget;
    int height;
    volatile long nextBlock;
    BigInteger cumulativeDifficulty;
    transient Transaction[] blockTransactions;
    transient volatile long id;
    transient volatile String stringId;
    transient volatile long generatorAccountId;
    private transient SoftReference<JSONStreamAware> jsonRef;
    public static final Comparator<Block> heightComparator;
    
    Block(final int version, final int timestamp, final long previousBlock, final int numberOfTransactions, final int totalAmount, final int totalFee, final int payloadLength, final byte[] payloadHash, final byte[] generatorPublicKey, final byte[] generationSignature, final byte[] blockSignature) {
        this(version, timestamp, previousBlock, numberOfTransactions, totalAmount, totalFee, payloadLength, payloadHash, generatorPublicKey, generationSignature, blockSignature, null);
    }
    
    Block(final int version, final int timestamp, final long previousBlock, final int numberOfTransactions, final int totalAmount, final int totalFee, final int payloadLength, final byte[] payloadHash, final byte[] generatorPublicKey, final byte[] generationSignature, final byte[] blockSignature, final byte[] previousBlockHash) {
        super();
        this.stringId = null;
        if (numberOfTransactions > 255 || numberOfTransactions < 0) {
            throw new IllegalArgumentException("attempted to create a block with " + numberOfTransactions + " transactions");
        }
        if (payloadLength > 32640 || payloadLength < 0) {
            throw new IllegalArgumentException("attempted to create a block with payloadLength " + payloadLength);
        }
        this.version = version;
        this.timestamp = timestamp;
        this.previousBlock = previousBlock;
        this.totalAmount = totalAmount;
        this.totalFee = totalFee;
        this.payloadLength = payloadLength;
        this.payloadHash = payloadHash;
        this.generatorPublicKey = generatorPublicKey;
        this.generationSignature = generationSignature;
        this.blockSignature = blockSignature;
        this.previousBlockHash = previousBlockHash;
        this.transactions = ((numberOfTransactions == 0) ? Block.emptyLong : new long[numberOfTransactions]);
        this.blockTransactions = ((numberOfTransactions == 0) ? Block.emptyTransactions : new Transaction[numberOfTransactions]);
    }
    
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        this.blockTransactions = ((this.transactions.length == 0) ? Block.emptyTransactions : new Transaction[this.transactions.length]);
    }
    
    void analyze() {
        synchronized (Nxt.blocksAndTransactionsLock) {
            for (int i = 0; i < this.transactions.length; ++i) {
                this.blockTransactions[i] = Nxt.transactions.get(this.transactions[i]);
                if (this.blockTransactions[i] == null) {
                    throw new IllegalStateException("Missing transaction " + Nxt.convert(this.transactions[i]));
                }
            }
            if (this.previousBlock == 0L) {
                this.baseTarget = 153722867L;
                this.cumulativeDifficulty = BigInteger.ZERO;
                Nxt.blocks.put(2680262203532249785L, this);
                Nxt.lastBlock.set(this);
                Account.addAccount(1739068987193023818L);
            }
            else {
                final Block previousLastBlock = Nxt.lastBlock.get();
                previousLastBlock.nextBlock = this.getId();
                this.height = previousLastBlock.height + 1;
                this.baseTarget = this.calculateBaseTarget();
                this.cumulativeDifficulty = previousLastBlock.cumulativeDifficulty.add(Nxt.two64.divide(BigInteger.valueOf(this.baseTarget)));
                if (previousLastBlock.getId() != this.previousBlock || !Nxt.lastBlock.compareAndSet(previousLastBlock, this)) {
                    throw new IllegalStateException("Last block not equal to this.previousBlock");
                }
                final Account generatorAccount = Nxt.accounts.get(this.getGeneratorAccountId());
                generatorAccount.addToBalanceAndUnconfirmedBalance(this.totalFee * 100L);
                if (Nxt.blocks.putIfAbsent(this.getId(), this) != null) {
                    throw new IllegalStateException("duplicate block id: " + this.getId());
                }
            }
            for (final Transaction transaction : this.blockTransactions) {
                transaction.height = this.height;
                final long sender = transaction.getSenderAccountId();
                final Account senderAccount = Nxt.accounts.get(sender);
                if (!senderAccount.setOrVerify(transaction.senderPublicKey)) {
                    throw new RuntimeException("sender public key mismatch");
                }
                senderAccount.addToBalanceAndUnconfirmedBalance(-(transaction.amount + transaction.fee) * 100L);
                Account recipientAccount = Nxt.accounts.get(transaction.recipient);
                if (recipientAccount == null) {
                    recipientAccount = Account.addAccount(transaction.recipient);
                }
                Label_1298: {
                    switch (transaction.type) {
                        case 0: {
                            switch (transaction.subtype) {
                                case 0: {
                                    recipientAccount.addToBalanceAndUnconfirmedBalance(transaction.amount * 100L);
                                    break;
                                }
                            }
                            break;
                        }
                        case 1: {
                            switch (transaction.subtype) {
                                case 1: {
                                    final Transaction.MessagingAliasAssignmentAttachment attachment = (Transaction.MessagingAliasAssignmentAttachment)transaction.attachment;
                                    final String normalizedAlias = attachment.alias.toLowerCase();
                                    Alias alias = Nxt.aliases.get(normalizedAlias);
                                    if (alias == null) {
                                        final long aliasId = transaction.getId();
                                        alias = new Alias(senderAccount, aliasId, attachment.alias, attachment.uri, this.timestamp);
                                        Nxt.aliases.put(normalizedAlias, alias);
                                        Nxt.aliasIdToAliasMappings.put(aliasId, alias);
                                        break;
                                    }
                                    alias.uri = attachment.uri;
                                    alias.timestamp = this.timestamp;
                                    break;
                                }
                            }
                            break;
                        }
                        case 2: {
                            switch (transaction.subtype) {
                                case 0: {
                                    final Transaction.ColoredCoinsAssetIssuanceAttachment attachment2 = (Transaction.ColoredCoinsAssetIssuanceAttachment)transaction.attachment;
                                    final long assetId = transaction.getId();
                                    final Asset asset = new Asset(sender, attachment2.name, attachment2.description, attachment2.quantity);
                                    Nxt.assets.put(assetId, asset);
                                    Nxt.assetNameToIdMappings.put(attachment2.name.toLowerCase(), assetId);
                                    Nxt.sortedAskOrders.put(assetId, new TreeSet<AskOrder>());
                                    Nxt.sortedBidOrders.put(assetId, new TreeSet<BidOrder>());
                                    senderAccount.addToAssetAndUnconfirmedAssetBalance(assetId, attachment2.quantity);
                                    break Label_1298;
                                }
                                case 1: {
                                    final Transaction.ColoredCoinsAssetTransferAttachment attachment3 = (Transaction.ColoredCoinsAssetTransferAttachment)transaction.attachment;
                                    senderAccount.addToAssetAndUnconfirmedAssetBalance(attachment3.asset, -attachment3.quantity);
                                    recipientAccount.addToAssetAndUnconfirmedAssetBalance(attachment3.asset, attachment3.quantity);
                                    break Label_1298;
                                }
                                case 2: {
                                    final Transaction.ColoredCoinsAskOrderPlacementAttachment attachment4 = (Transaction.ColoredCoinsAskOrderPlacementAttachment)transaction.attachment;
                                    final AskOrder order = new AskOrder(transaction.getId(), senderAccount, attachment4.asset, attachment4.quantity, attachment4.price);
                                    senderAccount.addToAssetAndUnconfirmedAssetBalance(attachment4.asset, -attachment4.quantity);
                                    Nxt.askOrders.put(order.id, order);
                                    Nxt.sortedAskOrders.get(attachment4.asset).add(order);
                                    Nxt.matchOrders(attachment4.asset);
                                    break Label_1298;
                                }
                                case 3: {
                                    final Transaction.ColoredCoinsBidOrderPlacementAttachment attachment5 = (Transaction.ColoredCoinsBidOrderPlacementAttachment)transaction.attachment;
                                    final BidOrder order2 = new BidOrder(transaction.getId(), senderAccount, attachment5.asset, attachment5.quantity, attachment5.price);
                                    senderAccount.addToBalanceAndUnconfirmedBalance(-attachment5.quantity * attachment5.price);
                                    Nxt.bidOrders.put(order2.id, order2);
                                    Nxt.sortedBidOrders.get(attachment5.asset).add(order2);
                                    Nxt.matchOrders(attachment5.asset);
                                    break Label_1298;
                                }
                                case 4: {
                                    final Transaction.ColoredCoinsAskOrderCancellationAttachment attachment6 = (Transaction.ColoredCoinsAskOrderCancellationAttachment)transaction.attachment;
                                    final AskOrder order = Nxt.askOrders.remove(attachment6.order);
                                    Nxt.sortedAskOrders.get(order.asset).remove(order);
                                    senderAccount.addToAssetAndUnconfirmedAssetBalance(order.asset, order.quantity);
                                    break Label_1298;
                                }
                                case 5: {
                                    final Transaction.ColoredCoinsBidOrderCancellationAttachment attachment7 = (Transaction.ColoredCoinsBidOrderCancellationAttachment)transaction.attachment;
                                    final BidOrder order2 = Nxt.bidOrders.remove(attachment7.order);
                                    Nxt.sortedBidOrders.get(order2.asset).remove(order2);
                                    senderAccount.addToBalanceAndUnconfirmedBalance(order2.quantity * order2.price);
                                    break Label_1298;
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
    }
    
    private long calculateBaseTarget() {
        if (this.getId() == 2680262203532249785L) {
            return 153722867L;
        }
        final Block previousBlock = Nxt.blocks.get(this.previousBlock);
        final long curBaseTarget = previousBlock.baseTarget;
        long newBaseTarget = BigInteger.valueOf(curBaseTarget).multiply(BigInteger.valueOf(this.timestamp - previousBlock.timestamp)).divide(BigInteger.valueOf(60L)).longValue();
        if (newBaseTarget < 0L || newBaseTarget > 153722867000000000L) {
            newBaseTarget = 153722867000000000L;
        }
        if (newBaseTarget < curBaseTarget / 2L) {
            newBaseTarget = curBaseTarget / 2L;
        }
        if (newBaseTarget == 0L) {
            newBaseTarget = 1L;
        }
        long twofoldCurBaseTarget = curBaseTarget * 2L;
        if (twofoldCurBaseTarget < 0L) {
            twofoldCurBaseTarget = 153722867000000000L;
        }
        if (newBaseTarget > twofoldCurBaseTarget) {
            newBaseTarget = twofoldCurBaseTarget;
        }
        return newBaseTarget;
    }
    
    static Block getBlock(final JSONObject blockData) {
        try {
            final int version = (int)blockData.get((Object)"version");
            final int timestamp = (int)blockData.get((Object)"timestamp");
            final long previousBlock = Nxt.parseUnsignedLong((String)blockData.get((Object)"previousBlock"));
            final int numberOfTransactions = (int)blockData.get((Object)"numberOfTransactions");
            final int totalAmount = (int)blockData.get((Object)"totalAmount");
            final int totalFee = (int)blockData.get((Object)"totalFee");
            final int payloadLength = (int)blockData.get((Object)"payloadLength");
            final byte[] payloadHash = Nxt.convert((String)blockData.get((Object)"payloadHash"));
            final byte[] generatorPublicKey = Nxt.convert((String)blockData.get((Object)"generatorPublicKey"));
            final byte[] generationSignature = Nxt.convert((String)blockData.get((Object)"generationSignature"));
            final byte[] blockSignature = Nxt.convert((String)blockData.get((Object)"blockSignature"));
            final byte[] previousBlockHash = (byte[])((version == 1) ? null : Nxt.convert((String)blockData.get((Object)"previousBlockHash")));
            if (numberOfTransactions > 255 || payloadLength > 32640) {
                return null;
            }
            return new Block(version, timestamp, previousBlock, numberOfTransactions, totalAmount, totalFee, payloadLength, payloadHash, generatorPublicKey, generationSignature, blockSignature, previousBlockHash);
        }
        catch (RuntimeException e) {
            return null;
        }
    }
    
    byte[] getBytes() {
        final ByteBuffer buffer = ByteBuffer.allocate(224);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(this.version);
        buffer.putInt(this.timestamp);
        buffer.putLong(this.previousBlock);
        buffer.putInt(this.transactions.length);
        buffer.putInt(this.totalAmount);
        buffer.putInt(this.totalFee);
        buffer.putInt(this.payloadLength);
        buffer.put(this.payloadHash);
        buffer.put(this.generatorPublicKey);
        buffer.put(this.generationSignature);
        if (this.version > 1) {
            buffer.put(this.previousBlockHash);
        }
        buffer.put(this.blockSignature);
        return buffer.array();
    }
    
    long getId() {
        this.calculateIds();
        return this.id;
    }
    
    String getStringId() {
        this.calculateIds();
        return this.stringId;
    }
    
    long getGeneratorAccountId() {
        this.calculateIds();
        return this.generatorAccountId;
    }
    
    private void calculateIds() {
        if (this.stringId != null) {
            return;
        }
        final byte[] hash = Nxt.getMessageDigest("SHA-256").digest(this.getBytes());
        final BigInteger bigInteger = new BigInteger(1, new byte[] { hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0] });
        this.id = bigInteger.longValue();
        this.stringId = bigInteger.toString();
        this.generatorAccountId = Account.getId(this.generatorPublicKey);
    }
    
    JSONObject getJSONObject() {
        final JSONObject block = new JSONObject();
        block.put((Object)"version", (Object)this.version);
        block.put((Object)"timestamp", (Object)this.timestamp);
        block.put((Object)"previousBlock", (Object)Nxt.convert(this.previousBlock));
        block.put((Object)"numberOfTransactions", (Object)this.transactions.length);
        block.put((Object)"totalAmount", (Object)this.totalAmount);
        block.put((Object)"totalFee", (Object)this.totalFee);
        block.put((Object)"payloadLength", (Object)this.payloadLength);
        block.put((Object)"payloadHash", (Object)Nxt.convert(this.payloadHash));
        block.put((Object)"generatorPublicKey", (Object)Nxt.convert(this.generatorPublicKey));
        block.put((Object)"generationSignature", (Object)Nxt.convert(this.generationSignature));
        if (this.version > 1) {
            block.put((Object)"previousBlockHash", (Object)Nxt.convert(this.previousBlockHash));
        }
        block.put((Object)"blockSignature", (Object)Nxt.convert(this.blockSignature));
        final JSONArray transactionsData = new JSONArray();
        for (final Transaction transaction : this.blockTransactions) {
            transactionsData.add((Object)transaction.getJSONObject());
        }
        block.put((Object)"transactions", (Object)transactionsData);
        return block;
    }
    
    synchronized JSONStreamAware getJSONStreamAware() {
        if (this.jsonRef != null) {
            final JSONStreamAware json = this.jsonRef.get();
            if (json != null) {
                return json;
            }
        }
        final JSONStreamAware json = (JSONStreamAware)new JSONStreamAware() {
            private char[] jsonChars = Block.this.getJSONObject().toJSONString().toCharArray();
            
            public void writeJSONString(final Writer out) throws IOException {
                out.write(this.jsonChars);
            }
        };
        this.jsonRef = new SoftReference<JSONStreamAware>(json);
        return json;
    }
    
    static ArrayList<Block> getLastBlocks(final int numberOfBlocks) {
        final ArrayList<Block> lastBlocks = new ArrayList<Block>(numberOfBlocks);
        long curBlock = Nxt.lastBlock.get().getId();
        do {
            final Block block = Nxt.blocks.get(curBlock);
            lastBlocks.add(block);
            curBlock = block.previousBlock;
        } while (lastBlocks.size() < numberOfBlocks && curBlock != 0L);
        return lastBlocks;
    }
    
    static void loadBlocks(final String fileName) throws FileNotFoundException {
        try (final FileInputStream fileInputStream = new FileInputStream(fileName);
             final ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
            Nxt.blockCounter.set(objectInputStream.readInt());
            Nxt.blocks.clear();
            Nxt.blocks.putAll((Map<?, ?>)objectInputStream.readObject());
        }
        catch (FileNotFoundException e) {
            throw e;
        }
        catch (IOException | ClassNotFoundException e2) {
            Nxt.logMessage("Error loading blocks from " + fileName, e2);
            System.exit(1);
        }
    }
    
    static boolean popLastBlock() {
        try {
            final JSONObject response = new JSONObject();
            response.put((Object)"response", (Object)"processNewData");
            final JSONArray addedUnconfirmedTransactions = new JSONArray();
            final Block block;
            synchronized (Nxt.blocksAndTransactionsLock) {
                block = Nxt.lastBlock.get();
                if (block.getId() == 2680262203532249785L) {
                    return false;
                }
                final Block previousBlock = Nxt.blocks.get(block.previousBlock);
                if (previousBlock == null) {
                    Nxt.logMessage("Previous block is null");
                    throw new IllegalStateException();
                }
                if (!Nxt.lastBlock.compareAndSet(block, previousBlock)) {
                    Nxt.logMessage("This block is no longer last block");
                    throw new IllegalStateException();
                }
                final Account generatorAccount = Nxt.accounts.get(block.getGeneratorAccountId());
                generatorAccount.addToBalanceAndUnconfirmedBalance(-block.totalFee * 100L);
                for (final long transactionId : block.transactions) {
                    final Transaction transaction = Nxt.transactions.remove(transactionId);
                    Nxt.unconfirmedTransactions.put(transactionId, transaction);
                    final Account senderAccount = Nxt.accounts.get(transaction.getSenderAccountId());
                    senderAccount.addToBalance((transaction.amount + transaction.fee) * 100L);
                    final Account recipientAccount = Nxt.accounts.get(transaction.recipient);
                    recipientAccount.addToBalanceAndUnconfirmedBalance(-transaction.amount * 100L);
                    final JSONObject addedUnconfirmedTransaction = new JSONObject();
                    addedUnconfirmedTransaction.put((Object)"index", (Object)transaction.index);
                    addedUnconfirmedTransaction.put((Object)"timestamp", (Object)transaction.timestamp);
                    addedUnconfirmedTransaction.put((Object)"deadline", (Object)transaction.deadline);
                    addedUnconfirmedTransaction.put((Object)"recipient", (Object)Nxt.convert(transaction.recipient));
                    addedUnconfirmedTransaction.put((Object)"amount", (Object)transaction.amount);
                    addedUnconfirmedTransaction.put((Object)"fee", (Object)transaction.fee);
                    addedUnconfirmedTransaction.put((Object)"sender", (Object)Nxt.convert(transaction.getSenderAccountId()));
                    addedUnconfirmedTransaction.put((Object)"id", (Object)transaction.getStringId());
                    addedUnconfirmedTransactions.add((Object)addedUnconfirmedTransaction);
                }
            }
            final JSONArray addedOrphanedBlocks = new JSONArray();
            final JSONObject addedOrphanedBlock = new JSONObject();
            addedOrphanedBlock.put((Object)"index", (Object)block.index);
            addedOrphanedBlock.put((Object)"timestamp", (Object)block.timestamp);
            addedOrphanedBlock.put((Object)"numberOfTransactions", (Object)block.transactions.length);
            addedOrphanedBlock.put((Object)"totalAmount", (Object)block.totalAmount);
            addedOrphanedBlock.put((Object)"totalFee", (Object)block.totalFee);
            addedOrphanedBlock.put((Object)"payloadLength", (Object)block.payloadLength);
            addedOrphanedBlock.put((Object)"generator", (Object)Nxt.convert(block.getGeneratorAccountId()));
            addedOrphanedBlock.put((Object)"height", (Object)block.height);
            addedOrphanedBlock.put((Object)"version", (Object)block.version);
            addedOrphanedBlock.put((Object)"block", (Object)block.getStringId());
            addedOrphanedBlock.put((Object)"baseTarget", (Object)BigInteger.valueOf(block.baseTarget).multiply(BigInteger.valueOf(100000L)).divide(BigInteger.valueOf(153722867L)));
            addedOrphanedBlocks.add((Object)addedOrphanedBlock);
            response.put((Object)"addedOrphanedBlocks", (Object)addedOrphanedBlocks);
            if (addedUnconfirmedTransactions.size() > 0) {
                response.put((Object)"addedUnconfirmedTransactions", (Object)addedUnconfirmedTransactions);
            }
            for (final User user : Nxt.users.values()) {
                user.send(response);
            }
        }
        catch (RuntimeException e) {
            Nxt.logMessage("Error popping last block", e);
            return false;
        }
        return true;
    }
    
    static boolean pushBlock(final ByteBuffer buffer, final boolean savingFlag) {
        final int curTime = Nxt.getEpochTime(System.currentTimeMillis());
        Block block;
        JSONArray addedConfirmedTransactions;
        JSONArray removedUnconfirmedTransactions;
        synchronized (Nxt.blocksAndTransactionsLock) {
            try {
                final Block previousLastBlock = Nxt.lastBlock.get();
                buffer.flip();
                final int version = buffer.getInt();
                if (version != ((previousLastBlock.height < 30000) ? 1 : 2)) {
                    return false;
                }
                if (previousLastBlock.height == 30000) {
                    final byte[] checksum = Transaction.calculateTransactionsChecksum();
                    if (Nxt.CHECKSUM_TRANSPARENT_FORGING == null) {
                        System.out.println(Arrays.toString(checksum));
                    }
                    else {
                        if (!Arrays.equals(checksum, Nxt.CHECKSUM_TRANSPARENT_FORGING)) {
                            Nxt.logMessage("Checksum failed at block 30000");
                            return false;
                        }
                        Nxt.logMessage("Checksum passed at block 30000");
                    }
                }
                final int blockTimestamp = buffer.getInt();
                final long previousBlock = buffer.getLong();
                final int numberOfTransactions = buffer.getInt();
                final int totalAmount = buffer.getInt();
                final int totalFee = buffer.getInt();
                final int payloadLength = buffer.getInt();
                final byte[] payloadHash = new byte[32];
                buffer.get(payloadHash);
                final byte[] generatorPublicKey = new byte[32];
                buffer.get(generatorPublicKey);
                byte[] generationSignature;
                byte[] previousBlockHash;
                if (version == 1) {
                    generationSignature = new byte[64];
                    buffer.get(generationSignature);
                    previousBlockHash = null;
                }
                else {
                    generationSignature = new byte[32];
                    buffer.get(generationSignature);
                    previousBlockHash = new byte[32];
                    buffer.get(previousBlockHash);
                    if (!Arrays.equals(Nxt.getMessageDigest("SHA-256").digest(previousLastBlock.getBytes()), previousBlockHash)) {
                        return false;
                    }
                }
                final byte[] blockSignature = new byte[64];
                buffer.get(blockSignature);
                if (blockTimestamp > curTime + 15 || blockTimestamp <= previousLastBlock.timestamp) {
                    return false;
                }
                if (payloadLength > 32640 || 224 + payloadLength != buffer.capacity() || numberOfTransactions > 255) {
                    return false;
                }
                block = new Block(version, blockTimestamp, previousBlock, numberOfTransactions, totalAmount, totalFee, payloadLength, payloadHash, generatorPublicKey, generationSignature, blockSignature, previousBlockHash);
                if (block.transactions.length > 255 || block.previousBlock != previousLastBlock.getId() || block.getId() == 0L || Nxt.blocks.get(block.getId()) != null || !block.verifyGenerationSignature() || !block.verifyBlockSignature()) {
                    return false;
                }
                block.index = Nxt.blockCounter.incrementAndGet();
                final HashMap<Long, Transaction> blockTransactions = new HashMap<Long, Transaction>();
                final HashSet<String> blockAliases = new HashSet<String>();
                for (int i = 0; i < block.transactions.length; ++i) {
                    final Transaction transaction = Transaction.getTransaction(buffer);
                    transaction.index = Nxt.transactionCounter.incrementAndGet();
                    final HashMap<Long, Transaction> hashMap = blockTransactions;
                    final long[] transactions = block.transactions;
                    final int n = i;
                    final long id = transaction.getId();
                    transactions[n] = id;
                    if (hashMap.put(id, transaction) != null) {
                        return false;
                    }
                    switch (transaction.type) {
                        case 1: {
                            switch (transaction.subtype) {
                                case 1: {
                                    if (!blockAliases.add(((Transaction.MessagingAliasAssignmentAttachment)transaction.attachment).alias.toLowerCase())) {
                                        return false;
                                    }
                                    continue;
                                }
                            }
                            break;
                        }
                    }
                }
                Arrays.sort(block.transactions);
                final HashMap<Long, Long> accumulatedAmounts = new HashMap<Long, Long>();
                final HashMap<Long, HashMap<Long, Long>> accumulatedAssetQuantities = new HashMap<Long, HashMap<Long, Long>>();
                int calculatedTotalAmount = 0;
                int calculatedTotalFee = 0;
                final MessageDigest digest = Nxt.getMessageDigest("SHA-256");
                int j;
                for (j = 0; j < block.transactions.length; ++j) {
                    final Transaction transaction2 = blockTransactions.get(block.transactions[j]);
                    if (transaction2.timestamp > curTime + 15 || transaction2.deadline < 1 || (transaction2.timestamp + transaction2.deadline * 60 < blockTimestamp && previousLastBlock.height > 303) || transaction2.fee <= 0 || transaction2.fee > 1000000000L || transaction2.amount < 0 || transaction2.amount > 1000000000L || !transaction2.validateAttachment() || Nxt.transactions.get(block.transactions[j]) != null || (transaction2.referencedTransaction != 0L && Nxt.transactions.get(transaction2.referencedTransaction) == null && blockTransactions.get(transaction2.referencedTransaction) == null)) {
                        break;
                    }
                    if (Nxt.unconfirmedTransactions.get(block.transactions[j]) == null && !transaction2.verify()) {
                        break;
                    }
                    final long sender = transaction2.getSenderAccountId();
                    Long accumulatedAmount = accumulatedAmounts.get(sender);
                    if (accumulatedAmount == null) {
                        accumulatedAmount = 0L;
                    }
                    accumulatedAmounts.put(sender, accumulatedAmount + (transaction2.amount + transaction2.fee) * 100L);
                    if (transaction2.type == 0) {
                        if (transaction2.subtype != 0) {
                            break;
                        }
                        calculatedTotalAmount += transaction2.amount;
                    }
                    else if (transaction2.type == 1) {
                        if (transaction2.subtype != 0 && transaction2.subtype != 1) {
                            break;
                        }
                    }
                    else {
                        if (transaction2.type != 2) {
                            break;
                        }
                        if (transaction2.subtype == 1) {
                            final Transaction.ColoredCoinsAssetTransferAttachment attachment = (Transaction.ColoredCoinsAssetTransferAttachment)transaction2.attachment;
                            HashMap<Long, Long> accountAccumulatedAssetQuantities = accumulatedAssetQuantities.get(sender);
                            if (accountAccumulatedAssetQuantities == null) {
                                accountAccumulatedAssetQuantities = new HashMap<Long, Long>();
                                accumulatedAssetQuantities.put(sender, accountAccumulatedAssetQuantities);
                            }
                            Long assetAccumulatedAssetQuantities = accountAccumulatedAssetQuantities.get(attachment.asset);
                            if (assetAccumulatedAssetQuantities == null) {
                                assetAccumulatedAssetQuantities = 0L;
                            }
                            accountAccumulatedAssetQuantities.put(attachment.asset, assetAccumulatedAssetQuantities + attachment.quantity);
                        }
                        else if (transaction2.subtype == 2) {
                            final Transaction.ColoredCoinsAskOrderPlacementAttachment attachment2 = (Transaction.ColoredCoinsAskOrderPlacementAttachment)transaction2.attachment;
                            HashMap<Long, Long> accountAccumulatedAssetQuantities = accumulatedAssetQuantities.get(sender);
                            if (accountAccumulatedAssetQuantities == null) {
                                accountAccumulatedAssetQuantities = new HashMap<Long, Long>();
                                accumulatedAssetQuantities.put(sender, accountAccumulatedAssetQuantities);
                            }
                            Long assetAccumulatedAssetQuantities = accountAccumulatedAssetQuantities.get(attachment2.asset);
                            if (assetAccumulatedAssetQuantities == null) {
                                assetAccumulatedAssetQuantities = 0L;
                            }
                            accountAccumulatedAssetQuantities.put(attachment2.asset, assetAccumulatedAssetQuantities + attachment2.quantity);
                        }
                        else if (transaction2.subtype == 3) {
                            final Transaction.ColoredCoinsBidOrderPlacementAttachment attachment3 = (Transaction.ColoredCoinsBidOrderPlacementAttachment)transaction2.attachment;
                            accumulatedAmounts.put(sender, accumulatedAmount + attachment3.quantity * attachment3.price);
                        }
                        else if (transaction2.subtype != 0 && transaction2.subtype != 4 && transaction2.subtype != 5) {
                            break;
                        }
                    }
                    calculatedTotalFee += transaction2.fee;
                    digest.update(transaction2.getBytes());
                }
                if (j != block.transactions.length || calculatedTotalAmount != block.totalAmount || calculatedTotalFee != block.totalFee) {
                    return false;
                }
                if (!Arrays.equals(digest.digest(), block.payloadHash)) {
                    return false;
                }
                for (final Map.Entry<Long, Long> accumulatedAmountEntry : accumulatedAmounts.entrySet()) {
                    final Account senderAccount = Nxt.accounts.get(accumulatedAmountEntry.getKey());
                    if (senderAccount.getBalance() < accumulatedAmountEntry.getValue()) {
                        return false;
                    }
                }
                for (final Map.Entry<Long, HashMap<Long, Long>> accumulatedAssetQuantitiesEntry : accumulatedAssetQuantities.entrySet()) {
                    final Account senderAccount = Nxt.accounts.get(accumulatedAssetQuantitiesEntry.getKey());
                    for (final Map.Entry<Long, Long> accountAccumulatedAssetQuantitiesEntry : accumulatedAssetQuantitiesEntry.getValue().entrySet()) {
                        final long asset = accountAccumulatedAssetQuantitiesEntry.getKey();
                        final long quantity = accountAccumulatedAssetQuantitiesEntry.getValue();
                        if (senderAccount.getAssetBalance(asset) < quantity) {
                            return false;
                        }
                    }
                }
                block.height = previousLastBlock.height + 1;
                for (final Map.Entry<Long, Transaction> transactionEntry : blockTransactions.entrySet()) {
                    final Transaction transaction3 = transactionEntry.getValue();
                    transaction3.height = block.height;
                    transaction3.block = block.getId();
                    if (Nxt.transactions.putIfAbsent(transactionEntry.getKey(), transaction3) != null) {
                        Nxt.logMessage("duplicate transaction id " + transactionEntry.getKey());
                        return false;
                    }
                }
                block.analyze();
                addedConfirmedTransactions = new JSONArray();
                removedUnconfirmedTransactions = new JSONArray();
                for (final Map.Entry<Long, Transaction> transactionEntry : blockTransactions.entrySet()) {
                    final Transaction transaction3 = transactionEntry.getValue();
                    final JSONObject addedConfirmedTransaction = new JSONObject();
                    addedConfirmedTransaction.put((Object)"index", (Object)transaction3.index);
                    addedConfirmedTransaction.put((Object)"blockTimestamp", (Object)block.timestamp);
                    addedConfirmedTransaction.put((Object)"transactionTimestamp", (Object)transaction3.timestamp);
                    addedConfirmedTransaction.put((Object)"sender", (Object)Nxt.convert(transaction3.getSenderAccountId()));
                    addedConfirmedTransaction.put((Object)"recipient", (Object)Nxt.convert(transaction3.recipient));
                    addedConfirmedTransaction.put((Object)"amount", (Object)transaction3.amount);
                    addedConfirmedTransaction.put((Object)"fee", (Object)transaction3.fee);
                    addedConfirmedTransaction.put((Object)"id", (Object)transaction3.getStringId());
                    addedConfirmedTransactions.add((Object)addedConfirmedTransaction);
                    final Transaction removedTransaction = Nxt.unconfirmedTransactions.remove(transactionEntry.getKey());
                    if (removedTransaction != null) {
                        final JSONObject removedUnconfirmedTransaction = new JSONObject();
                        removedUnconfirmedTransaction.put((Object)"index", (Object)removedTransaction.index);
                        removedUnconfirmedTransactions.add((Object)removedUnconfirmedTransaction);
                        final Account senderAccount2 = Nxt.accounts.get(removedTransaction.getSenderAccountId());
                        senderAccount2.addToUnconfirmedBalance((removedTransaction.amount + removedTransaction.fee) * 100L);
                    }
                }
                if (savingFlag) {
                    Transaction.saveTransactions("transactions.nxt");
                    saveBlocks("blocks.nxt", false);
                }
            }
            catch (RuntimeException e) {
                Nxt.logMessage("Error pushing block", e);
                return false;
            }
        }
        if (block.timestamp >= curTime - 15) {
            final JSONObject request = block.getJSONObject();
            request.put((Object)"requestType", (Object)"processBlock");
            Peer.sendToSomePeers(request);
        }
        final JSONArray addedRecentBlocks = new JSONArray();
        final JSONObject addedRecentBlock = new JSONObject();
        addedRecentBlock.put((Object)"index", (Object)block.index);
        addedRecentBlock.put((Object)"timestamp", (Object)block.timestamp);
        addedRecentBlock.put((Object)"numberOfTransactions", (Object)block.transactions.length);
        addedRecentBlock.put((Object)"totalAmount", (Object)block.totalAmount);
        addedRecentBlock.put((Object)"totalFee", (Object)block.totalFee);
        addedRecentBlock.put((Object)"payloadLength", (Object)block.payloadLength);
        addedRecentBlock.put((Object)"generator", (Object)Nxt.convert(block.getGeneratorAccountId()));
        addedRecentBlock.put((Object)"height", (Object)block.height);
        addedRecentBlock.put((Object)"version", (Object)block.version);
        addedRecentBlock.put((Object)"block", (Object)block.getStringId());
        addedRecentBlock.put((Object)"baseTarget", (Object)BigInteger.valueOf(block.baseTarget).multiply(BigInteger.valueOf(100000L)).divide(BigInteger.valueOf(153722867L)));
        addedRecentBlocks.add((Object)addedRecentBlock);
        final JSONObject response = new JSONObject();
        response.put((Object)"response", (Object)"processNewData");
        response.put((Object)"addedConfirmedTransactions", (Object)addedConfirmedTransactions);
        if (removedUnconfirmedTransactions.size() > 0) {
            response.put((Object)"removedUnconfirmedTransactions", (Object)removedUnconfirmedTransactions);
        }
        response.put((Object)"addedRecentBlocks", (Object)addedRecentBlocks);
        for (final User user : Nxt.users.values()) {
            user.send(response);
        }
        return true;
    }
    
    static void saveBlocks(final String fileName, final boolean flag) {
        try (final FileOutputStream fileOutputStream = new FileOutputStream(fileName);
             final ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
            objectOutputStream.writeInt(Nxt.blockCounter.get());
            objectOutputStream.writeObject(new HashMap(Nxt.blocks));
        }
        catch (IOException e) {
            Nxt.logMessage("Error saving blocks to " + fileName, e);
            throw new RuntimeException(e);
        }
    }
    
    boolean verifyBlockSignature() {
        final Account account = Nxt.accounts.get(this.getGeneratorAccountId());
        if (account == null) {
            return false;
        }
        final byte[] data = this.getBytes();
        final byte[] data2 = new byte[data.length - 64];
        System.arraycopy(data, 0, data2, 0, data2.length);
        return Crypto.verify(this.blockSignature, data2, this.generatorPublicKey) && account.setOrVerify(this.generatorPublicKey);
    }
    
    boolean verifyGenerationSignature() {
        try {
            final Block previousBlock = Nxt.blocks.get(this.previousBlock);
            if (previousBlock == null) {
                return false;
            }
            if (this.version == 1 && !Crypto.verify(this.generationSignature, previousBlock.generationSignature, this.generatorPublicKey)) {
                return false;
            }
            final Account account = Nxt.accounts.get(this.getGeneratorAccountId());
            if (account == null || account.getEffectiveBalance() <= 0) {
                return false;
            }
            final int elapsedTime = this.timestamp - previousBlock.timestamp;
            final BigInteger target = BigInteger.valueOf(Nxt.lastBlock.get().baseTarget).multiply(BigInteger.valueOf(account.getEffectiveBalance())).multiply(BigInteger.valueOf(elapsedTime));
            final MessageDigest digest = Nxt.getMessageDigest("SHA-256");
            byte[] generationSignatureHash;
            if (this.version == 1) {
                generationSignatureHash = digest.digest(this.generationSignature);
            }
            else {
                digest.update(previousBlock.generationSignature);
                generationSignatureHash = digest.digest(this.generatorPublicKey);
                if (!Arrays.equals(this.generationSignature, generationSignatureHash)) {
                    return false;
                }
            }
            final BigInteger hit = new BigInteger(1, new byte[] { generationSignatureHash[7], generationSignatureHash[6], generationSignatureHash[5], generationSignatureHash[4], generationSignatureHash[3], generationSignatureHash[2], generationSignatureHash[1], generationSignatureHash[0] });
            return hit.compareTo(target) < 0;
        }
        catch (RuntimeException e) {
            Nxt.logMessage("Error verifying block generation signature", e);
            return false;
        }
    }
    
    static {
        emptyLong = new long[0];
        emptyTransactions = new Transaction[0];
        heightComparator = new Comparator<Block>() {
            @Override
            public int compare(final Block o1, final Block o2) {
                return (o1.height < o2.height) ? -1 : ((o1.height > o2.height) ? 1 : 0);
            }
        };
    }
}
