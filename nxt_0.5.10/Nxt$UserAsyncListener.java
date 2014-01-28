import javax.servlet.*;
import org.json.simple.*;
import java.io.*;

static class UserAsyncListener implements AsyncListener
{
    final User user;
    
    UserAsyncListener(final User user) {
        super();
        this.user = user;
    }
    
    public void onComplete(final AsyncEvent asyncEvent) throws IOException {
    }
    
    public void onError(final AsyncEvent asyncEvent) throws IOException {
        synchronized (this.user) {
            this.user.asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");
            try (final Writer writer = this.user.asyncContext.getResponse().getWriter()) {
                new JSONObject().writeJSONString(writer);
            }
            this.user.asyncContext.complete();
            this.user.asyncContext = null;
        }
    }
    
    public void onStartAsync(final AsyncEvent asyncEvent) throws IOException {
    }
    
    public void onTimeout(final AsyncEvent asyncEvent) throws IOException {
        synchronized (this.user) {
            this.user.asyncContext.getResponse().setContentType("text/plain; charset=UTF-8");
            try (final Writer writer = this.user.asyncContext.getResponse().getWriter()) {
                new JSONObject().writeJSONString(writer);
            }
            this.user.asyncContext.complete();
            this.user.asyncContext = null;
        }
    }
}
