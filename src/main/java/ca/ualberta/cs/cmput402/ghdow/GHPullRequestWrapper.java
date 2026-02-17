package ca.ualberta.cs.cmput402.ghdow;

import org.kohsuke.github.GHPullRequest;

import java.io.IOException;
import java.util.Date;

public class GHPullRequestWrapper {
    protected final GHPullRequest pr;

    public GHPullRequestWrapper(GHPullRequest pr) {
        this.pr = pr;
    }

    public Date getCreatedAt() throws IOException {
        return pr.getCreatedAt();
    }

    public Date getClosedAt() throws IOException {
        return pr.getClosedAt(); // null if still open
    }
}
