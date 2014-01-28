import java.io.*;

static class CountingInputStream extends FilterInputStream
{
    private long count;
    
    public CountingInputStream(final InputStream in) {
        super(in);
    }
    
    @Override
    public int read() throws IOException {
        final int read = super.read();
        if (read >= 0) {
            ++this.count;
        }
        return read;
    }
    
    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        final int read = super.read(b, off, len);
        if (read >= 0) {
            ++this.count;
        }
        return read;
    }
    
    @Override
    public long skip(final long n) throws IOException {
        final long skipped = super.skip(n);
        if (skipped >= 0L) {
            this.count += skipped;
        }
        return skipped;
    }
    
    public long getCount() {
        return this.count;
    }
}
