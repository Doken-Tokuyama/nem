import java.util.*;

public class Block {
    public int totalAmount;
    public int totalFee;
    public String generatorPublicKey;
    public String previousBlock;
    public List<Transaction> transactions;

    public long generatorBalance;
}
