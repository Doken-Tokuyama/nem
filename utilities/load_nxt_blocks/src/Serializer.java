import org.json.*;

import java.util.ArrayList;

public class Serializer {
    public static Block DeserializeBlock(JSONObject object) {
        Block block = new Block();
        block.generatorPublicKey = object.getString("generatorPublicKey");
        block.totalAmount = object.getInt("totalAmount");
        block.totalFee = object.getInt("totalFee");
        block.previousBlock = object.getString("previousBlock");

        block.transactions = new ArrayList<>();
        JSONArray transactions = object.getJSONArray("transactions");
        for (int i = 0; i < transactions.length(); ++i) {
            JSONObject jsonTransaction = transactions.getJSONObject(i);
            block.transactions.add(DeserializeTransaction(jsonTransaction));
        }

        return block;
    }

    public static Transaction DeserializeTransaction(JSONObject object) {
        Transaction transaction = new Transaction();
        transaction.amount = object.getInt("amount");
        transaction.fee = object.getInt("fee");
        transaction.recipient = object.getString("recipient");
        transaction.senderPublicKey = object.getString("senderPublicKey");
        transaction.subtype = object.getInt("subtype");
        transaction.type = object.getInt("type");
        return transaction;
    }

    public static JSONObject Serialize(Block block) {
        final JSONObject object = new JSONObject();
        object.put("totalAmount", block.totalAmount);
        object.put("totalFee", block.totalFee);
        object.put("generatorPublicKey", block.generatorPublicKey);
        object.put("previousBlock", block.previousBlock);

        final JSONArray transactions = new JSONArray();
        for (final Transaction transaction : block.transactions)
            transactions.put(Serialize(transaction));

        object.put("transactions", transactions);
        return object;
    }

    public static JSONObject Serialize(Transaction transaction) {
        final JSONObject object = new JSONObject();
        object.put("type", transaction.type);
        object.put("subtype", transaction.subtype);
        object.put("senderPublicKey", transaction.senderPublicKey);
        object.put("recipient", transaction.recipient);
        object.put("amount", transaction.amount);
        object.put("fee", transaction.fee);

        object.put("senderBalance", transaction.senderBalance);
        object.put("recipientBalance", transaction.recipientBalance);
        object.put("recipientPublicKey", transaction.recipientPublicKey);
        return object;
    }
}
