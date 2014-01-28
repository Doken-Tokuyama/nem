static class Asset
{
    final long accountId;
    final String name;
    final String description;
    final int quantity;
    
    Asset(final long accountId, final String name, final String description, final int quantity) {
        super();
        this.accountId = accountId;
        this.name = name;
        this.description = description;
        this.quantity = quantity;
    }
}
