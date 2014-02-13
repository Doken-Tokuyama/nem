import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;
import javax.xml.ws.http.HTTPException;

public class Downloader {
    public interface Logger {
        void LogDownloadStatus(int numBlocks, int numTransactions);
        void LogUnknownAccountId(String accountId);
    }

    static final int READ_TIMEOUT = 30000;
    static final int CONNECTION_TIMEOUT = 2000;
    static final String GENESIS_BLOCK_ID = "2680262203532249785";

    Logger logger;
    String nxtNodeUrl;
    List<Block> blocks;
    Map<String, String> idToPublicKeyMap;
    Map<String, Long> publicKeyToBalanceMap;

    public Downloader(final String nxtNode, final Logger logger) {
        this.logger = logger;
        this.nxtNodeUrl = "http://" + nxtNode + ":7874/nxt";
        this.blocks = new ArrayList<>();
        this.idToPublicKeyMap = new HashMap<>();
        this.publicKeyToBalanceMap = new HashMap<>();
    }

    public void LoadGenesisBlocks(final String fileName) throws Exception {
        try (FileReader fileReader = new FileReader(fileName)) {
            try (BufferedReader bufferedReader = new BufferedReader(fileReader)) {
                this.LoadGenesisBlocks(bufferedReader);
            }
        }
    }

    private void LoadGenesisBlocks(final BufferedReader reader) throws Exception {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty())
                continue;

            String[] parts = line.split(",");
            String accountId = parts[0];
            long accountBalance = Long.parseLong(parts[1]);
            this.IncrementBalance(accountId, accountBalance);
        }
    }

    public void DownloadAllBlocks() throws Exception {
        int numTransactions = 0;

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

            this.logger.LogDownloadStatus(blocks.size(), numTransactions);
        }
    }

    public void SaveBlocks(final String fileName) throws Exception {
        final JSONArray blocks = new JSONArray();
        for (final Block block : this.blocks)
            blocks.put(Serializer.Serialize(block));

        try (PrintWriter writer = new PrintWriter(fileName, "UTF-8")) {
            writer.print(blocks.toString());
        }
    }

    private void ProcessBlock(final JSONObject jsonBlock) throws Exception {
        Block block = Serializer.DeserializeBlock(jsonBlock);
        this.UpdateBlock(block);

        for (Transaction transaction : block.transactions) {
            this.UpdateTransaction(transaction);
            this.IncrementBalance(transaction.recipientPublicKey, transaction.amount);
            this.IncrementBalance(transaction.senderPublicKey, -transaction.amount - transaction.fee);
        }

        this.IncrementBalance(block.generatorPublicKey, block.totalFee);
        this.blocks.add(block);
    }

    private void UpdateBlock(final Block block) throws Exception {
        block.generatorBalance = this.GetBalance(block.generatorPublicKey);
    }

    private void UpdateTransaction(final Transaction transaction) throws Exception {
        String recipientPublicKey = this.GetPublicKey(transaction.recipient);
        transaction.senderBalance = this.GetBalance(transaction.senderPublicKey);
        transaction.recipientBalance = this.GetBalance(recipientPublicKey);
    }

    private long GetBalance(final String publicKey) {
        Long currentValue = this.publicKeyToBalanceMap.get(publicKey);
        return null == currentValue ? 0L : currentValue;
    }

    private void IncrementBalance(final String publicKey, long increment) {
        Long currentValue = GetBalance(publicKey);
        this.publicKeyToBalanceMap.put(publicKey, currentValue + increment);
    }

    //region NXT HTTP Wrappers

    private String GetPublicKey(final String accountId) throws Exception {
        String publicKey = this.idToPublicKeyMap.get(accountId);
        if (null != publicKey)
            return publicKey;

        JSONObject response = this.Get("getAccountPublicKey", "account", accountId);
        publicKey = response.optString("publicKey");
        if (null == publicKey || "" == publicKey) {
            // in some cases, NXT does not have public keys for an account
            // in those cases just use the accountId
            logger.LogUnknownAccountId(accountId);
            publicKey = accountId;
        }

        this.idToPublicKeyMap.put(accountId, publicKey);
        return publicKey;
    }

    private JSONArray GetNextBlocks(final String blockId) throws Exception {
        JSONObject json = new JSONObject();
        json.put("blockId", blockId);

        JSONObject response = this.Post("getNextBlocks", json);
        return response.getJSONArray("nextBlocks");
    }

    //endregion

    // region HTTP Wrappers

    private JSONObject Get(final String type, final String key, final String value) throws Exception {
        HttpURLConnection connection = CreateConnection("GET", "?requestType=" + type + "&" + key + "=" + value);
        return Connect(connection);
    }

    private JSONObject Post(final String type, final JSONObject json) throws Exception {
        json.put("requestType", type);
        json.put("protocol", 1);

        HttpURLConnection connection = CreateConnection("POST", "");
        connection.setDoOutput(true);

        try (final Writer writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), "UTF-8"))) {
            writer.write(json.toString());
        }

        return Connect(connection);
    }

    private HttpURLConnection CreateConnection(final String requestMethod, final String queryString) throws Exception {
        URL url = new URL(this.nxtNodeUrl + queryString);
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod(requestMethod);
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        return connection;
    }

    private JSONObject Connect(final HttpURLConnection connection) throws Exception {
        int responseCode = connection.getResponseCode();
        if (responseCode != 200)
            throw new HTTPException(responseCode);

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
            return ConvertToJson(reader);
        }
    }

    //endregion

    private static JSONObject ConvertToJson(final BufferedReader reader) throws Exception {
        StringBuilder builder = new StringBuilder();

        String line;
        while (null != (line = reader.readLine()))
            builder.append(line);

        return new JSONObject(builder.toString());
    }
}
