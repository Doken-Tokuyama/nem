import org.json.simple.*;
import java.util.*;

class Nxt$6 implements Runnable {
    @Override
    public void run() {
        try {
            final int curTime = Nxt.getEpochTime(System.currentTimeMillis());
            final JSONArray removedUnconfirmedTransactions = new JSONArray();
            final Iterator<Transaction> iterator = Nxt.unconfirmedTransactions.values().iterator();
            while (iterator.hasNext()) {
                final Transaction transaction = iterator.next();
                if (transaction.timestamp + transaction.deadline * 60 < curTime || !transaction.validateAttachment()) {
                    iterator.remove();
                    final Account account = Nxt.accounts.get(transaction.getSenderAccountId());
                    account.addToUnconfirmedBalance((transaction.amount + transaction.fee) * 100L);
                    final JSONObject removedUnconfirmedTransaction = new JSONObject();
                    removedUnconfirmedTransaction.put((Object)"index", (Object)transaction.index);
                    removedUnconfirmedTransactions.add((Object)removedUnconfirmedTransaction);
                }
            }
            if (removedUnconfirmedTransactions.size() > 0) {
                final JSONObject response = new JSONObject();
                response.put((Object)"response", (Object)"processNewData");
                response.put((Object)"removedUnconfirmedTransactions", (Object)removedUnconfirmedTransactions);
                for (final User user : Nxt.users.values()) {
                    user.send(response);
                }
            }
        }
        catch (Exception e) {
            Nxt.logDebugMessage("Error removing unconfirmed transactions", e);
        }
        catch (Throwable t) {
            Nxt.logMessage("CRITICAL ERROR. PLEASE REPORT TO THE DEVELOPERS.\n" + t.toString());
            t.printStackTrace();
            System.exit(1);
        }
    }
}