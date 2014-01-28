import java.util.*;

static final class Nxt$Transaction$2 implements Comparator<Transaction> {
    @Override
    public int compare(final Transaction o1, final Transaction o2) {
        final long id1 = o1.getId();
        final long id2 = o2.getId();
        return (id1 < id2) ? -1 : ((id1 > id2) ? 1 : ((o1.timestamp < o2.timestamp) ? -1 : ((o1.timestamp > o2.timestamp) ? 1 : 0)));
    }
}