import java.util.*;

static final class Nxt$Transaction$1 implements Comparator<Transaction> {
    @Override
    public int compare(final Transaction o1, final Transaction o2) {
        return (o1.timestamp < o2.timestamp) ? -1 : ((o1.timestamp > o2.timestamp) ? 1 : 0);
    }
}