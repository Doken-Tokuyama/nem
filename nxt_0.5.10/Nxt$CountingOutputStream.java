import java.io.*;

static class CountingOutputStream extends FilterOutputStream
{
    private long count;
    
    public CountingOutputStream(final OutputStream out) {
        super(out);
    }
    
    @Override
    public void write(final int b) throws IOException {
        ++this.count;
        super.write(b);
    }
    
    public long getCount() {
        return this.count;
    }
}
