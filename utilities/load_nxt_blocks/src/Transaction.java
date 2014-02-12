public class Transaction {
    public int type;
    public int subtype;
    public String senderPublicKey;
    public String recipient;
    public int amount;
    public int fee;

    public long senderBalance;
    public long recipientBalance;
    public String recipientPublicKey;
}