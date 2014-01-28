static class BidOrder implements Comparable<BidOrder>
{
    final long id;
    final long height;
    final Account account;
    final long asset;
    volatile int quantity;
    final long price;
    
    BidOrder(final long id, final Account account, final long asset, final int quantity, final long price) {
        super();
        this.id = id;
        this.height = Nxt.lastBlock.get().height;
        this.account = account;
        this.asset = asset;
        this.quantity = quantity;
        this.price = price;
    }
    
    @Override
    public int compareTo(final BidOrder o) {
        if (this.price > o.price) {
            return -1;
        }
        if (this.price < o.price) {
            return 1;
        }
        if (this.height < o.height) {
            return -1;
        }
        if (this.height > o.height) {
            return 1;
        }
        if (this.id < o.id) {
            return -1;
        }
        if (this.id > o.id) {
            return 1;
        }
        return 0;
    }
}
