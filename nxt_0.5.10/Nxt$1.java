import java.text.*;

static final class Nxt$1 extends ThreadLocal<SimpleDateFormat> {
    @Override
    protected SimpleDateFormat initialValue() {
        return new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss.SSS] ");
    }
}