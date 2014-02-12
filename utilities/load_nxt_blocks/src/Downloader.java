import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;
import javax.xml.ws.http.HTTPException;

public class Downloader {
    static final int READ_TIMEOUT = 30000;
    static final int CONNECTION_TIMEOUT = 2000;
    static final String GENESIS_BLOCK_ID = "2680262203532249785";

    String nxtNodeUrl;
    List<Block> blocks;
    Map<String, String> idToPublicKeyMap;
    Map<String, Long> publicKeyToBalanceMap;

    public Downloader(String nxtNode) {
        this.nxtNodeUrl = "http://" + nxtNode + ":7874/nxt";
        this.blocks = new ArrayList<>();
        this.idToPublicKeyMap = new HashMap<>();
        this.publicKeyToBalanceMap = new HashMap<>();
    }

    public String DownloadAllBlocks() throws Exception {
        long numTransactions = 0;

        String blockId = GENESIS_BLOCK_ID;
        for (;;) {
            JSONArray lastBlocks = this.GetNextBlocks(blockId);
            if (lastBlocks.length() <= 1)
                break;

            for (int i = blockId.equals(GENESIS_BLOCK_ID) ? 0 : 1; i < lastBlocks.length(); ++i) {
                JSONObject jsonBlock = lastBlocks.getJSONObject(i);
                ProcessBlock(jsonBlock);
                numTransactions += jsonBlock.getJSONArray("transactions").length();
            }

            Block lastBlock = this.blocks.get(this.blocks.size() - 1);
            blockId = lastBlock.previousBlock;

            System.out.println(blocks.size() + " blocks found");
            System.out.println(numTransactions + " transactions found");
        }

        final JSONArray blocks = new JSONArray();
        for (final Block block : this.blocks)
            blocks.put(Serializer.Serialize(block));

        return blocks.toString();
    }

    private void ProcessBlock(JSONObject jsonBlock) {
        Block block = Serializer.DeserializeBlock(jsonBlock);
        for (Transaction transaction : block.transactions) {
//                    UpdateTransaction(transaction);
            IncrementBalance(transaction.recipientPublicKey, transaction.amount);
            IncrementBalance(transaction.senderPublicKey, -transaction.amount - transaction.fee);
        }

        IncrementBalance(block.generatorPublicKey, block.totalFee);
        this.blocks.add(block);
    }

    private void UpdateTransaction(final Transaction transaction) throws Exception {
        String recipientPublicKey = this.idToPublicKeyMap.get(transaction.recipient);
        if (null == recipientPublicKey) {
            recipientPublicKey = this.GetPublicKey(transaction.recipient);
            this.idToPublicKeyMap.put(transaction.recipient, recipientPublicKey);
        }

        transaction.senderBalance = this.publicKeyToBalanceMap.get(transaction.senderPublicKey);
        transaction.recipientBalance = this.publicKeyToBalanceMap.get(recipientPublicKey);
    }

    private void IncrementBalance(String publicKey, long increment) {
        Long currentValue = this.publicKeyToBalanceMap.get(publicKey);
        if (null == currentValue)
            currentValue = 0L;

        this.publicKeyToBalanceMap.put(publicKey, currentValue + increment);
    }

    private String GetPublicKey(String accountId) throws Exception {
        JSONObject response = this.Get("getAccountPublicKey", "account", accountId);
        return response.getString("publicKey");
    }

    private JSONArray GetNextBlocks(final String blockId) throws Exception {
        JSONObject json = new JSONObject();
        json.put("blockId", blockId);

        JSONObject response = this.Post("getNextBlocks", json);
        return response.getJSONArray("nextBlocks");
    }

    private JSONObject Get(final String type, final String key, final String value) throws Exception {
        HttpURLConnection connection = HttpURLConnection();
        connection.setRequestMethod("GET");
        connection.addRequestProperty("requestType", type);
        connection.addRequestProperty("protocol", "1");
        connection.addRequestProperty(key, value);
        return Connect(connection);
    }

    private JSONObject Post(final String type, final JSONObject json) throws Exception {
        json.put("requestType", type);
        json.put("protocol", 1);

        HttpURLConnection connection = HttpURLConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);

        try (final Writer writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"))) {
            writer.write(json.toString());
        }

        return Connect(connection);
    }

    private HttpURLConnection HttpURLConnection() throws Exception {
        URL url = new URL(this.nxtNodeUrl);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        return connection;
    }

    private JSONObject Connect(HttpURLConnection connection) throws Exception {
        int responseCode = connection.getResponseCode();
        if (responseCode != 200)
            throw new HTTPException(responseCode);

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
            return ConvertToJson(reader);
        }
    }

    private static JSONObject ConvertToJson(final BufferedReader reader) throws Exception {
        StringBuilder builder = new StringBuilder();

        String line;
        while (null != (line = reader.readLine()))
            builder.append(line);

        return new JSONObject(builder.toString());
    }
}
