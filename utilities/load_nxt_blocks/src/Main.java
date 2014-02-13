import java.lang.System;
import java.io.*;
import java.nio.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.*;
import org.json.JSONArray;

public class Main {
    private static final String NXT_GENESIS_BLOCK_CSV  = "data/NxtGenesisBlock.csv";
    private static final String NXT_BLOCKS_JSON  = "out/nxtblocks.json";
    private static final String NXT_FEES_CSV  = "out/nxtfees.txt";

    private static void PrintUsage() {
        System.out.println("Usage: application <mode> (options)");
        System.out.println("[modes]");
        System.out.println("  blocks <hostname>: downloads all account blocks");
        System.out.println("  analyze: analyzes the fee generation of downloaded block");
    }

    public static void main(String[] args) {
        try {
            String mode = args.length < 1 ? "" : args[0];
            mode = mode.toLowerCase();
            switch (mode) {
                case "blocks":
                    if (args.length < 2) {
                        PrintUsage();
                        return;
                    }

                    final String hostname = args[1];
                    DownloadBlocks(hostname);
                    break;

                case "analyze":
                    AnalyzeForgingFees();
                    break;

                default:
                    PrintUsage();
                    return;
            }
        }

        catch (Exception e) {
            System.out.format("Exception encountered: %s", e.toString());
        }
    }

    private static void DownloadBlocks(final String hostname) throws Exception {
        Downloader.Logger logger = new Downloader.Logger() {
            public void LogDownloadStatus(final int numBlocks, final int numTransactions) {
                System.out.format("Blocks: %d, Transactions: %d", numBlocks, numTransactions);
                System.out.println();
            }

            public void LogUnknownAccountId(final String accountId) {
                System.out.format("[%d]: %s", ++numUnknownAccountIds, accountId);
                System.out.println();
            }

            int numUnknownAccountIds;
        };

        System.out.println("Connecting to hostname " + hostname + " ...");
        Downloader downloader = new Downloader(hostname, logger);

        System.out.println("Loading NXT genesis block data ...");
        downloader.LoadGenesisBlocks(NXT_GENESIS_BLOCK_CSV);

        System.out.println("Downloading all NXT blocks ...");
        downloader.DownloadAllBlocks();

        System.out.println("Saving all NXT blocks ...");
        downloader.SaveBlocks(NXT_BLOCKS_JSON);
    }

    private static void AnalyzeForgingFees() throws Exception {
        System.out.println("Loading all NXT blocks ...");
        List<Block> blocks = LoadBlocksFromFile(NXT_BLOCKS_JSON);

        System.out.println("Analyzing fees ...");
        try (FileWriter fileWriter = new FileWriter(NXT_FEES_CSV)) {
            try (BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {
                for (Block block : blocks) {
                    String line = String.format("%s,%s,%s", block.generatorPublicKey, block.generatorPublicKey, block.totalFee);
                    bufferedWriter.write(line);
                    bufferedWriter.newLine();
                }
            }
        }
    }

    private static List<Block> LoadBlocksFromFile(String fileName) throws Exception {
        byte[] encoded = Files.readAllBytes(Paths.get(fileName));
        String contents = Charset.forName("UTF-8").decode(ByteBuffer.wrap(encoded)).toString();
        JSONArray jsonBlocks = new JSONArray(contents);

        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < jsonBlocks.length(); ++i) {
            Block block = Serializer.DeserializeBlock(jsonBlocks.getJSONObject(i));
            blocks.add(block);
        }

        return blocks;
    }
}
