package ca.ualberta.cs.cmput402.ghdow;

import org.kohsuke.github.GHIssue;

import java.io.IOException;
import java.util.Date;

public class GHIssueWrapper {
    protected GHIssue ghIssue;

    public GHIssueWrapper(GHIssue ghIssue) {
        this.ghIssue = ghIssue;
    }

    public Date getCreatedAt() throws IOException {
        return ghIssue.getCreatedAt();
    }
}
