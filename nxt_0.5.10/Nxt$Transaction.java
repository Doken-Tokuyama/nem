import java.nio.*;
import java.math.*;
import org.json.simple.*;
import java.io.*;
import java.util.*;
import java.security.*;

static class Transaction implements Comparable<Transaction>, Serializable
{
    static final long serialVersionUID = 0L;
    static final byte TYPE_PAYMENT = 0;
    static final byte TYPE_MESSAGING = 1;
    static final byte TYPE_COLORED_COINS = 2;
    static final byte SUBTYPE_PAYMENT_ORDINARY_PAYMENT = 0;
    static final byte SUBTYPE_MESSAGING_ARBITRARY_MESSAGE = 0;
    static final byte SUBTYPE_MESSAGING_ALIAS_ASSIGNMENT = 1;
    static final byte SUBTYPE_COLORED_COINS_ASSET_ISSUANCE = 0;
    static final byte SUBTYPE_COLORED_COINS_ASSET_TRANSFER = 1;
    static final byte SUBTYPE_COLORED_COINS_ASK_ORDER_PLACEMENT = 2;
    static final byte SUBTYPE_COLORED_COINS_BID_ORDER_PLACEMENT = 3;
    static final byte SUBTYPE_COLORED_COINS_ASK_ORDER_CANCELLATION = 4;
    static final byte SUBTYPE_COLORED_COINS_BID_ORDER_CANCELLATION = 5;
    static final int ASSET_ISSUANCE_FEE = 1000;
    final byte type;
    final byte subtype;
    int timestamp;
    final short deadline;
    final byte[] senderPublicKey;
    final long recipient;
    final int amount;
    final int fee;
    final long referencedTransaction;
    byte[] signature;
    Attachment attachment;
    int index;
    long block;
    int height;
    public static final Comparator<Transaction> timestampComparator;
    private static final int TRANSACTION_BYTES_LENGTH = 128;
    transient volatile long id;
    transient volatile String stringId;
    transient volatile long senderAccountId;
    
    Transaction(final byte type, final byte subtype, final int timestamp, final short deadline, final byte[] senderPublicKey, final long recipient, final int amount, final int fee, final long referencedTransaction, final byte[] signature) {
        super();
        this.stringId = null;
        this.type = type;
        this.subtype = subtype;
        this.timestamp = timestamp;
        this.deadline = deadline;
        this.senderPublicKey = senderPublicKey;
        this.recipient = recipient;
        this.amount = amount;
        this.fee = fee;
        this.referencedTransaction = referencedTransaction;
        this.signature = signature;
        this.height = Integer.MAX_VALUE;
    }
    
    @Override
    public int compareTo(final Transaction o) {
        if (this.height < o.height) {
            return -1;
        }
        if (this.height > o.height) {
            return 1;
        }
        if (this.fee * o.getSize() > o.fee * this.getSize()) {
            return -1;
        }
        if (this.fee * o.getSize() < o.fee * this.getSize()) {
            return 1;
        }
        if (this.timestamp < o.timestamp) {
            return -1;
        }
        if (this.timestamp > o.timestamp) {
            return 1;
        }
        if (this.index < o.index) {
            return -1;
        }
        if (this.index > o.index) {
            return 1;
        }
        return 0;
    }
    
    int getSize() {
        return 128 + ((this.attachment == null) ? 0 : this.attachment.getSize());
    }
    
    byte[] getBytes() {
        final ByteBuffer buffer = ByteBuffer.allocate(this.getSize());
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(this.type);
        buffer.put(this.subtype);
        buffer.putInt(this.timestamp);
        buffer.putShort(this.deadline);
        buffer.put(this.senderPublicKey);
        buffer.putLong(this.recipient);
        buffer.putInt(this.amount);
        buffer.putInt(this.fee);
        buffer.putLong(this.referencedTransaction);
        buffer.put(this.signature);
        if (this.attachment != null) {
            buffer.put(this.attachment.getBytes());
        }
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
    
    long getSenderAccountId() {
        this.calculateIds();
        return this.senderAccountId;
    }
    
    private void calculateIds() {
        if (this.stringId != null) {
            return;
        }
        final byte[] hash = Nxt.getMessageDigest("SHA-256").digest(this.getBytes());
        final BigInteger bigInteger = new BigInteger(1, new byte[] { hash[7], hash[6], hash[5], hash[4], hash[3], hash[2], hash[1], hash[0] });
        this.id = bigInteger.longValue();
        this.stringId = bigInteger.toString();
        this.senderAccountId = Account.getId(this.senderPublicKey);
    }
    
    JSONObject getJSONObject() {
        final JSONObject transaction = new JSONObject();
        transaction.put((Object)"type", (Object)this.type);
        transaction.put((Object)"subtype", (Object)this.subtype);
        transaction.put((Object)"timestamp", (Object)this.timestamp);
        transaction.put((Object)"deadline", (Object)this.deadline);
        transaction.put((Object)"senderPublicKey", (Object)Nxt.convert(this.senderPublicKey));
        transaction.put((Object)"recipient", (Object)Nxt.convert(this.recipient));
        transaction.put((Object)"amount", (Object)this.amount);
        transaction.put((Object)"fee", (Object)this.fee);
        transaction.put((Object)"referencedTransaction", (Object)Nxt.convert(this.referencedTransaction));
        transaction.put((Object)"signature", (Object)Nxt.convert(this.signature));
        if (this.attachment != null) {
            transaction.put((Object)"attachment", (Object)this.attachment.getJSONObject());
        }
        return transaction;
    }
    
    long getRecipientDeltaBalance() {
        return this.amount * 100L + ((this.attachment == null) ? 0L : this.attachment.getRecipientDeltaBalance());
    }
    
    long getSenderDeltaBalance() {
        return -(this.amount + this.fee) * 100L + ((this.attachment == null) ? 0L : this.attachment.getSenderDeltaBalance());
    }
    
    static Transaction getTransaction(final ByteBuffer buffer) {
        final byte type = buffer.get();
        final byte subtype = buffer.get();
        final int timestamp = buffer.getInt();
        final short deadline = buffer.getShort();
        final byte[] senderPublicKey = new byte[32];
        buffer.get(senderPublicKey);
        final long recipient = buffer.getLong();
        final int amount = buffer.getInt();
        final int fee = buffer.getInt();
        final long referencedTransaction = buffer.getLong();
        final byte[] signature = new byte[64];
        buffer.get(signature);
        final Transaction transaction = new Transaction(type, subtype, timestamp, deadline, senderPublicKey, recipient, amount, fee, referencedTransaction, signature);
        Label_0581: {
            switch (type) {
                case 1: {
                    switch (subtype) {
                        case 0: {
                            final int messageLength = buffer.getInt();
                            if (messageLength <= 1000) {
                                final byte[] message = new byte[messageLength];
                                buffer.get(message);
                                transaction.attachment = new MessagingArbitraryMessageAttachment(message);
                            }
                            break;
                        }
                        case 1: {
                            final int aliasLength = buffer.get();
                            final byte[] alias = new byte[aliasLength];
                            buffer.get(alias);
                            final int uriLength = buffer.getShort();
                            final byte[] uri = new byte[uriLength];
                            buffer.get(uri);
                            try {
                                transaction.attachment = new MessagingAliasAssignmentAttachment(new String(alias, "UTF-8").intern(), new String(uri, "UTF-8").intern());
                            }
                            catch (RuntimeException | UnsupportedEncodingException e) {
                                Nxt.logDebugMessage("Error parsing alias assignment", e);
                            }
                            break;
                        }
                    }
                    break;
                }
                case 2: {
                    switch (subtype) {
                        case 0: {
                            final int nameLength = buffer.get();
                            final byte[] name = new byte[nameLength];
                            buffer.get(name);
                            final int descriptionLength = buffer.getShort();
                            final byte[] description = new byte[descriptionLength];
                            buffer.get(description);
                            final int quantity = buffer.getInt();
                            try {
                                transaction.attachment = new ColoredCoinsAssetIssuanceAttachment(new String(name, "UTF-8").intern(), new String(description, "UTF-8").intern(), quantity);
                            }
                            catch (RuntimeException | UnsupportedEncodingException e2) {
                                Nxt.logDebugMessage("Error in asset issuance", e2);
                            }
                            break Label_0581;
                        }
                        case 1: {
                            final long asset = buffer.getLong();
                            final int quantity2 = buffer.getInt();
                            transaction.attachment = new ColoredCoinsAssetTransferAttachment(asset, quantity2);
                            break Label_0581;
                        }
                        case 2: {
                            final long asset = buffer.getLong();
                            final int quantity2 = buffer.getInt();
                            final long price = buffer.getLong();
                            transaction.attachment = new ColoredCoinsAskOrderPlacementAttachment(asset, quantity2, price);
                            break Label_0581;
                        }
                        case 3: {
                            final long asset = buffer.getLong();
                            final int quantity2 = buffer.getInt();
                            final long price = buffer.getLong();
                            transaction.attachment = new ColoredCoinsBidOrderPlacementAttachment(asset, quantity2, price);
                            break Label_0581;
                        }
                        case 4: {
                            final long order = buffer.getLong();
                            transaction.attachment = new ColoredCoinsAskOrderCancellationAttachment(order);
                            break Label_0581;
                        }
                        case 5: {
                            final long order = buffer.getLong();
                            transaction.attachment = new ColoredCoinsBidOrderCancellationAttachment(order);
                            break Label_0581;
                        }
                    }
                    break;
                }
            }
        }
        return transaction;
    }
    
    static Transaction getTransaction(final JSONObject transactionData) {
        final byte type = (byte)transactionData.get((Object)"type");
        final byte subtype = (byte)transactionData.get((Object)"subtype");
        final int timestamp = (int)transactionData.get((Object)"timestamp");
        final short deadline = (short)transactionData.get((Object)"deadline");
        final byte[] senderPublicKey = Nxt.convert((String)transactionData.get((Object)"senderPublicKey"));
        final long recipient = Nxt.parseUnsignedLong((String)transactionData.get((Object)"recipient"));
        final int amount = (int)transactionData.get((Object)"amount");
        final int fee = (int)transactionData.get((Object)"fee");
        final long referencedTransaction = Nxt.parseUnsignedLong((String)transactionData.get((Object)"referencedTransaction"));
        final byte[] signature = Nxt.convert((String)transactionData.get((Object)"signature"));
        final Transaction transaction = new Transaction(type, subtype, timestamp, deadline, senderPublicKey, recipient, amount, fee, referencedTransaction, signature);
        final JSONObject attachmentData = (JSONObject)transactionData.get((Object)"attachment");
        Label_0648: {
            switch (type) {
                case 1: {
                    switch (subtype) {
                        case 0: {
                            final String message = (String)attachmentData.get((Object)"message");
                            transaction.attachment = new MessagingArbitraryMessageAttachment(Nxt.convert(message));
                            break;
                        }
                        case 1: {
                            final String alias = (String)attachmentData.get((Object)"alias");
                            final String uri = (String)attachmentData.get((Object)"uri");
                            transaction.attachment = new MessagingAliasAssignmentAttachment(alias.trim(), uri.trim());
                            break;
                        }
                    }
                    break;
                }
                case 2: {
                    switch (subtype) {
                        case 0: {
                            final String name = (String)attachmentData.get((Object)"name");
                            final String description = (String)attachmentData.get((Object)"description");
                            final int quantity = (int)attachmentData.get((Object)"quantity");
                            transaction.attachment = new ColoredCoinsAssetIssuanceAttachment(name.trim(), description.trim(), quantity);
                            break Label_0648;
                        }
                        case 1: {
                            final long asset = Nxt.parseUnsignedLong((String)attachmentData.get((Object)"asset"));
                            final int quantity = (int)attachmentData.get((Object)"quantity");
                            transaction.attachment = new ColoredCoinsAssetTransferAttachment(asset, quantity);
                            break Label_0648;
                        }
                        case 2: {
                            final long asset = Nxt.parseUnsignedLong((String)attachmentData.get((Object)"asset"));
                            final int quantity = (int)attachmentData.get((Object)"quantity");
                            final long price = (long)attachmentData.get((Object)"price");
                            transaction.attachment = new ColoredCoinsAskOrderPlacementAttachment(asset, quantity, price);
                            break Label_0648;
                        }
                        case 3: {
                            final long asset = Nxt.parseUnsignedLong((String)attachmentData.get((Object)"asset"));
                            final int quantity = (int)attachmentData.get((Object)"quantity");
                            final long price = (long)attachmentData.get((Object)"price");
                            transaction.attachment = new ColoredCoinsBidOrderPlacementAttachment(asset, quantity, price);
                            break Label_0648;
                        }
                        case 4: {
                            transaction.attachment = new ColoredCoinsAskOrderCancellationAttachment(Nxt.parseUnsignedLong((String)attachmentData.get((Object)"order")));
                            break Label_0648;
                        }
                        case 5: {
                            transaction.attachment = new ColoredCoinsBidOrderCancellationAttachment(Nxt.parseUnsignedLong((String)attachmentData.get((Object)"order")));
                            break Label_0648;
                        }
                    }
                    break;
                }
            }
        }
        return transaction;
    }
    
    static void loadTransactions(final String fileName) throws FileNotFoundException {
        try (final FileInputStream fileInputStream = new FileInputStream(fileName);
             final ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
            Nxt.transactionCounter.set(objectInputStream.readInt());
            Nxt.transactions.clear();
            Nxt.transactions.putAll((Map<?, ?>)objectInputStream.readObject());
        }
        catch (FileNotFoundException e) {
            throw e;
        }
        catch (IOException | ClassNotFoundException e2) {
            Nxt.logMessage("Error loading transactions from " + fileName, e2);
            System.exit(1);
        }
    }
    
    static void processTransactions(final JSONObject request, final String parameterName) {
        final JSONArray transactionsData = (JSONArray)request.get((Object)parameterName);
        final JSONArray validTransactionsData = new JSONArray();
        for (final Object transactionData : transactionsData) {
            final Transaction transaction = getTransaction((JSONObject)transactionData);
            try {
                final int curTime = Nxt.getEpochTime(System.currentTimeMillis());
                if (transaction.timestamp > curTime + 15 || transaction.deadline < 1 || transaction.timestamp + transaction.deadline * 60 < curTime || transaction.fee <= 0 || !transaction.validateAttachment()) {
                    continue;
                }
                final long senderId;
                boolean doubleSpendingTransaction;
                synchronized (Nxt.blocksAndTransactionsLock) {
                    final long id = transaction.getId();
                    if (Nxt.transactions.get(id) != null || Nxt.unconfirmedTransactions.get(id) != null || Nxt.doubleSpendingTransactions.get(id) != null || !transaction.verify()) {
                        continue;
                    }
                    senderId = transaction.getSenderAccountId();
                    final Account account = Nxt.accounts.get(senderId);
                    if (account == null) {
                        doubleSpendingTransaction = true;
                    }
                    else {
                        final int amount = transaction.amount + transaction.fee;
                        synchronized (account) {
                            if (account.getUnconfirmedBalance() < amount * 100L) {
                                doubleSpendingTransaction = true;
                            }
                            else {
                                doubleSpendingTransaction = false;
                                account.addToUnconfirmedBalance(-amount * 100L);
                                if (transaction.type == 2) {
                                    if (transaction.subtype == 1) {
                                        final ColoredCoinsAssetTransferAttachment attachment = (ColoredCoinsAssetTransferAttachment)transaction.attachment;
                                        final Integer unconfirmedAssetBalance = account.getUnconfirmedAssetBalance(attachment.asset);
                                        if (unconfirmedAssetBalance == null || unconfirmedAssetBalance < attachment.quantity) {
                                            doubleSpendingTransaction = true;
                                            account.addToUnconfirmedBalance(amount * 100L);
                                        }
                                        else {
                                            account.addToUnconfirmedAssetBalance(attachment.asset, -attachment.quantity);
                                        }
                                    }
                                    else if (transaction.subtype == 2) {
                                        final ColoredCoinsAskOrderPlacementAttachment attachment2 = (ColoredCoinsAskOrderPlacementAttachment)transaction.attachment;
                                        final Integer unconfirmedAssetBalance = account.getUnconfirmedAssetBalance(attachment2.asset);
                                        if (unconfirmedAssetBalance == null || unconfirmedAssetBalance < attachment2.quantity) {
                                            doubleSpendingTransaction = true;
                                            account.addToUnconfirmedBalance(amount * 100L);
                                        }
                                        else {
                                            account.addToUnconfirmedAssetBalance(attachment2.asset, -attachment2.quantity);
                                        }
                                    }
                                    else if (transaction.subtype == 3) {
                                        final ColoredCoinsBidOrderPlacementAttachment attachment3 = (ColoredCoinsBidOrderPlacementAttachment)transaction.attachment;
                                        if (account.getUnconfirmedBalance() < attachment3.quantity * attachment3.price) {
                                            doubleSpendingTransaction = true;
                                            account.addToUnconfirmedBalance(amount * 100L);
                                        }
                                        else {
                                            account.addToUnconfirmedBalance(-attachment3.quantity * attachment3.price);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    transaction.index = Nxt.transactionCounter.incrementAndGet();
                    if (doubleSpendingTransaction) {
                        Nxt.doubleSpendingTransactions.put(transaction.getId(), transaction);
                    }
                    else {
                        Nxt.unconfirmedTransactions.put(transaction.getId(), transaction);
                        if (parameterName.equals("transactions")) {
                            validTransactionsData.add(transactionData);
                        }
                    }
                }
                final JSONObject response = new JSONObject();
                response.put((Object)"response", (Object)"processNewData");
                final JSONArray newTransactions = new JSONArray();
                final JSONObject newTransaction = new JSONObject();
                newTransaction.put((Object)"index", (Object)transaction.index);
                newTransaction.put((Object)"timestamp", (Object)transaction.timestamp);
                newTransaction.put((Object)"deadline", (Object)transaction.deadline);
                newTransaction.put((Object)"recipient", (Object)Nxt.convert(transaction.recipient));
                newTransaction.put((Object)"amount", (Object)transaction.amount);
                newTransaction.put((Object)"fee", (Object)transaction.fee);
                newTransaction.put((Object)"sender", (Object)Nxt.convert(senderId));
                newTransaction.put((Object)"id", (Object)transaction.getStringId());
                newTransactions.add((Object)newTransaction);
                if (doubleSpendingTransaction) {
                    response.put((Object)"addedDoubleSpendingTransactions", (Object)newTransactions);
                }
                else {
                    response.put((Object)"addedUnconfirmedTransactions", (Object)newTransactions);
                }
                for (final User user : Nxt.users.values()) {
                    user.send(response);
                }
            }
            catch (RuntimeException e) {
                Nxt.logMessage("Error processing transaction", e);
            }
        }
        if (validTransactionsData.size() > 0) {
            final JSONObject peerRequest = new JSONObject();
            peerRequest.put((Object)"requestType", (Object)"processTransactions");
            peerRequest.put((Object)"transactions", (Object)validTransactionsData);
            Peer.sendToSomePeers(peerRequest);
        }
    }
    
    static void saveTransactions(final String fileName) {
        try (final FileOutputStream fileOutputStream = new FileOutputStream(fileName);
             final ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
            objectOutputStream.writeInt(Nxt.transactionCounter.get());
            objectOutputStream.writeObject(new HashMap(Nxt.transactions));
            objectOutputStream.close();
        }
        catch (IOException e) {
            Nxt.logMessage("Error saving transactions to " + fileName, e);
            throw new RuntimeException(e);
        }
    }
    
    void sign(final String secretPhrase) {
        this.signature = Crypto.sign(this.getBytes(), secretPhrase);
        try {
            while (!this.verify()) {
                ++this.timestamp;
                this.signature = new byte[64];
                this.signature = Crypto.sign(this.getBytes(), secretPhrase);
            }
        }
        catch (RuntimeException e) {
            Nxt.logMessage("Error signing transaction", e);
        }
    }
    
    boolean validateAttachment() {
        if (this.fee > 1000000000L) {
            return false;
        }
        switch (this.type) {
            case 0: {
                switch (this.subtype) {
                    case 0: {
                        return this.amount > 0 && this.amount < 1000000000L;
                    }
                    default: {
                        return false;
                    }
                }
                break;
            }
            case 1: {
                switch (this.subtype) {
                    case 0: {
                        if (Nxt.lastBlock.get().height < 40000) {
                            return false;
                        }
                        try {
                            final MessagingArbitraryMessageAttachment attachment = (MessagingArbitraryMessageAttachment)this.attachment;
                            return this.amount == 0 && attachment.message.length <= 1000;
                        }
                        catch (RuntimeException e) {
                            Nxt.logDebugMessage("Error validating arbitrary message", e);
                            return false;
                        }
                    }
                    case 1: {
                        if (Nxt.lastBlock.get().height < 22000) {
                            return false;
                        }
                        try {
                            final MessagingAliasAssignmentAttachment attachment2 = (MessagingAliasAssignmentAttachment)this.attachment;
                            if (this.recipient != 1739068987193023818L || this.amount != 0 || attachment2.alias.length() == 0 || attachment2.alias.length() > 100 || attachment2.uri.length() > 1000) {
                                return false;
                            }
                            final String normalizedAlias = attachment2.alias.toLowerCase();
                            for (int i = 0; i < normalizedAlias.length(); ++i) {
                                if ("0123456789abcdefghijklmnopqrstuvwxyz".indexOf(normalizedAlias.charAt(i)) < 0) {
                                    return false;
                                }
                            }
                            final Alias alias = Nxt.aliases.get(normalizedAlias);
                            return alias == null || Arrays.equals(alias.account.publicKey.get(), this.senderPublicKey);
                        }
                        catch (RuntimeException e) {
                            Nxt.logDebugMessage("Error in alias assignment validation", e);
                            return false;
                        }
                        break;
                    }
                }
                return false;
            }
            default: {
                return false;
            }
        }
    }
    
    boolean verify() {
        final Account account = Nxt.accounts.get(this.getSenderAccountId());
        if (account == null) {
            return false;
        }
        final byte[] data = this.getBytes();
        for (int i = 64; i < 128; ++i) {
            data[i] = 0;
        }
        return Crypto.verify(this.signature, data, this.senderPublicKey) && account.setOrVerify(this.senderPublicKey);
    }
    
    public static byte[] calculateTransactionsChecksum() {
        synchronized (Nxt.blocksAndTransactionsLock) {
            final PriorityQueue<Transaction> sortedTransactions = new PriorityQueue<Transaction>(Nxt.transactions.size(), new Comparator<Transaction>() {
                @Override
                public int compare(final Transaction o1, final Transaction o2) {
                    final long id1 = o1.getId();
                    final long id2 = o2.getId();
                    return (id1 < id2) ? -1 : ((id1 > id2) ? 1 : ((o1.timestamp < o2.timestamp) ? -1 : ((o1.timestamp > o2.timestamp) ? 1 : 0)));
                }
            });
            sortedTransactions.addAll(Nxt.transactions.values());
            final MessageDigest digest = Nxt.getMessageDigest("SHA-256");
            while (!sortedTransactions.isEmpty()) {
                digest.update(sortedTransactions.poll().getBytes());
            }
            return digest.digest();
        }
    }
    
    static {
        timestampComparator = new Comparator<Transaction>() {
            @Override
            public int compare(final Transaction o1, final Transaction o2) {
                return (o1.timestamp < o2.timestamp) ? -1 : ((o1.timestamp > o2.timestamp) ? 1 : 0);
            }
        };
    }
    
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
    
    static class ColoredCoinsAssetTransferAttachment implements Attachment, Serializable
    {
        static final long serialVersionUID = 0L;
        long asset;
        int quantity;
        
        ColoredCoinsAssetTransferAttachment(final long asset, final int quantity) {
            super();
            this.asset = asset;
            this.quantity = quantity;
        }
        
        @Override
        public int getSize() {
            return 12;
        }
        
        @Override
        public byte[] getBytes() {
            final ByteBuffer buffer = ByteBuffer.allocate(this.getSize());
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            buffer.putLong(this.asset);
            buffer.putInt(this.quantity);
            return buffer.array();
        }
        
        @Override
        public JSONObject getJSONObject() {
            final JSONObject attachment = new JSONObject();
            attachment.put((Object)"asset", (Object)Nxt.convert(this.asset));
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
    
    static class ColoredCoinsBidOrderPlacementAttachment implements Attachment, Serializable
    {
        static final long serialVersionUID = 0L;
        long asset;
        int quantity;
        long price;
        
        ColoredCoinsBidOrderPlacementAttachment(final long asset, final int quantity, final long price) {
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
            return -this.quantity * this.price;
        }
    }
    
    static class ColoredCoinsAskOrderCancellationAttachment implements Attachment, Serializable
    {
        static final long serialVersionUID = 0L;
        long order;
        
        ColoredCoinsAskOrderCancellationAttachment(final long order) {
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
            return 0L;
        }
    }
    
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
    
    interface Attachment
    {
        int getSize();
        
        byte[] getBytes();
        
        JSONObject getJSONObject();
        
        long getRecipientDeltaBalance();
        
        long getSenderDeltaBalance();
    }
}
