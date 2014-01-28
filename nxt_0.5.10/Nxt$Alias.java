static class Alias
{
    final Account account;
    final long id;
    final String alias;
    volatile String uri;
    volatile int timestamp;
    
    Alias(final Account account, final long id, final String alias, final String uri, final int timestamp) {
        super();
        this.account = account;
        this.id = id;
        this.alias = alias;
        this.uri = uri;
        this.timestamp = timestamp;
    }
}
