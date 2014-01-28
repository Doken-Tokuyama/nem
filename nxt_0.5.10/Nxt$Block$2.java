import java.util.*;

static final class Nxt$Block$2 implements Comparator<Block> {
    @Override
    public int compare(final Block o1, final Block o2) {
        return (o1.height < o2.height) ? -1 : ((o1.height > o2.height) ? 1 : 0);
    }
}