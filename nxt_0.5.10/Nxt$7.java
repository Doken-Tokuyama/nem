import java.math.*;
import org.json.simple.*;
import java.nio.*;
import java.util.*;

class Nxt$7 implements Runnable {
    private final JSONObject getCumulativeDifficultyRequest = new JSONObject();
    private final JSONObject getMilestoneBlockIdsRequest = new JSONObject();
    
    {
        this.getCumulativeDifficultyRequest.put((Object)"requestType", (Object)"getCumulativeDifficulty");
        this.getMilestoneBlockIdsRequest.put((Object)"requestType", (Object)"getMilestoneBlockIds");
    }
    
    @Override
    public void run() {
        try {
            final Peer peer = Peer.getAnyPeer(1, true);
            if (peer != null) {
                Nxt.lastBlockchainFeeder = peer;
                JSONObject response = peer.send(this.getCumulativeDifficultyRequest);
                if (response != null) {
                    BigInteger curCumulativeDifficulty = Nxt.lastBlock.get().cumulativeDifficulty;
                    final String peerCumulativeDifficulty = (String)response.get((Object)"cumulativeDifficulty");
                    if (peerCumulativeDifficulty == null) {
                        return;
                    }
                    final BigInteger betterCumulativeDifficulty = new BigInteger(peerCumulativeDifficulty);
                    if (betterCumulativeDifficulty.compareTo(curCumulativeDifficulty) > 0) {
                        response = peer.send(this.getMilestoneBlockIdsRequest);
                        if (response != null) {
                            long commonBlockId = 2680262203532249785L;
                            final JSONArray milestoneBlockIds = (JSONArray)response.get((Object)"milestoneBlockIds");
                            for (final Object milestoneBlockId : milestoneBlockIds) {
                                final long blockId = Nxt.parseUnsignedLong((String)milestoneBlockId);
                                final Block block = Nxt.blocks.get(blockId);
                                if (block != null) {
                                    commonBlockId = blockId;
                                    break;
                                }
                            }
                            int i;
                            int numberOfBlocks;
                            do {
                                final JSONObject request = new JSONObject();
                                request.put((Object)"requestType", (Object)"getNextBlockIds");
                                request.put((Object)"blockId", (Object)Nxt.convert(commonBlockId));
                                response = peer.send(request);
                                if (response == null) {
                                    return;
                                }
                                final JSONArray nextBlockIds = (JSONArray)response.get((Object)"nextBlockIds");
                                numberOfBlocks = nextBlockIds.size();
                                if (numberOfBlocks == 0) {
                                    return;
                                }
                                for (i = 0; i < numberOfBlocks; ++i) {
                                    final long blockId2 = Nxt.parseUnsignedLong((String)nextBlockIds.get(i));
                                    if (Nxt.blocks.get(blockId2) == null) {
                                        break;
                                    }
                                    commonBlockId = blockId2;
                                }
                            } while (i == numberOfBlocks);
                            if (Nxt.lastBlock.get().height - Nxt.blocks.get(commonBlockId).height < 720) {
                                long curBlockId = commonBlockId;
                                final LinkedList<Block> futureBlocks = new LinkedList<Block>();
                                final HashMap<Long, Transaction> futureTransactions = new HashMap<Long, Transaction>();
                                while (true) {
                                    final JSONObject request2 = new JSONObject();
                                    request2.put((Object)"requestType", (Object)"getNextBlocks");
                                    request2.put((Object)"blockId", (Object)Nxt.convert(curBlockId));
                                    response = peer.send(request2);
                                    if (response == null) {
                                        break;
                                    }
                                    final JSONArray nextBlocks = (JSONArray)response.get((Object)"nextBlocks");
                                    numberOfBlocks = nextBlocks.size();
                                    if (numberOfBlocks == 0) {
                                        break;
                                    }
                                    synchronized (Nxt.blocksAndTransactionsLock) {
                                        for (i = 0; i < numberOfBlocks; ++i) {
                                            final JSONObject blockData = (JSONObject)nextBlocks.get(i);
                                            final Block block2 = Block.getBlock(blockData);
                                            if (block2 == null) {
                                                peer.blacklist();
                                                return;
                                            }
                                            curBlockId = block2.getId();
                                            boolean alreadyPushed = false;
                                            if (block2.previousBlock == Nxt.lastBlock.get().getId()) {
                                                final ByteBuffer buffer = ByteBuffer.allocate(224 + block2.payloadLength);
                                                buffer.order(ByteOrder.LITTLE_ENDIAN);
                                                buffer.put(block2.getBytes());
                                                final JSONArray transactionsData = (JSONArray)blockData.get((Object)"transactions");
                                                for (final Object transaction : transactionsData) {
                                                    buffer.put(Transaction.getTransaction((JSONObject)transaction).getBytes());
                                                }
                                                if (!Block.pushBlock(buffer, false)) {
                                                    peer.blacklist();
                                                    return;
                                                }
                                                alreadyPushed = true;
                                            }
                                            if (!alreadyPushed && Nxt.blocks.get(block2.getId()) == null && block2.transactions.length <= 255) {
                                                futureBlocks.add(block2);
                                                final JSONArray transactionsData2 = (JSONArray)blockData.get((Object)"transactions");
                                                for (int j = 0; j < block2.transactions.length; ++j) {
                                                    final Transaction transaction2 = Transaction.getTransaction((JSONObject)transactionsData2.get(j));
                                                    block2.transactions[j] = transaction2.getId();
                                                    block2.blockTransactions[j] = transaction2;
                                                    futureTransactions.put(block2.transactions[j], transaction2);
                                                }
                                            }
                                        }
                                    }
                                }
                                if (!futureBlocks.isEmpty() && Nxt.lastBlock.get().height - Nxt.blocks.get(commonBlockId).height < 720) {
                                    synchronized (Nxt.blocksAndTransactionsLock) {
                                        Block.saveBlocks("blocks.nxt.bak", true);
                                        Transaction.saveTransactions("transactions.nxt.bak");
                                        curCumulativeDifficulty = Nxt.lastBlock.get().cumulativeDifficulty;
                                        while (Nxt.lastBlock.get().getId() != commonBlockId && Block.popLastBlock()) {}
                                        if (Nxt.lastBlock.get().getId() == commonBlockId) {
                                            for (final Block block3 : futureBlocks) {
                                                if (block3.previousBlock == Nxt.lastBlock.get().getId()) {
                                                    final ByteBuffer buffer2 = ByteBuffer.allocate(224 + block3.payloadLength);
                                                    buffer2.order(ByteOrder.LITTLE_ENDIAN);
                                                    buffer2.put(block3.getBytes());
                                                    for (final Transaction transaction3 : block3.blockTransactions) {
                                                        buffer2.put(transaction3.getBytes());
                                                    }
                                                    if (!Block.pushBlock(buffer2, false)) {
                                                        break;
                                                    }
                                                    continue;
                                                }
                                            }
                                        }
                                        if (Nxt.lastBlock.get().cumulativeDifficulty.compareTo(curCumulativeDifficulty) < 0) {
                                            Block.loadBlocks("blocks.nxt.bak");
                                            Transaction.loadTransactions("transactions.nxt.bak");
                                            peer.blacklist();
                                            Nxt.accounts.clear();
                                            Nxt.aliases.clear();
                                            Nxt.aliasIdToAliasMappings.clear();
                                            Nxt.unconfirmedTransactions.clear();
                                            Nxt.doubleSpendingTransactions.clear();
                                            Nxt.logMessage("Re-scanning blockchain...");
                                            final Map<Long, Block> loadedBlocks = new HashMap<Long, Block>(Nxt.blocks);
                                            Nxt.blocks.clear();
                                            Block currentBlock;
                                            for (long currentBlockId = 2680262203532249785L; (currentBlock = loadedBlocks.get(currentBlockId)) != null; currentBlockId = currentBlock.nextBlock) {
                                                currentBlock.analyze();
                                            }
                                            Nxt.logMessage("...Done");
                                        }
                                    }
                                }
                                synchronized (Nxt.blocksAndTransactionsLock) {
                                    Block.saveBlocks("blocks.nxt", false);
                                    Transaction.saveTransactions("transactions.nxt");
                                }
                            }
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            Nxt.logDebugMessage("Error in milestone blocks processing thread", e);
        }
        catch (Throwable t) {
            Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }
    }
}