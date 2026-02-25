package ca.ualberta.cs.cmput402.ghdow;

import java.io.IOException;

import org.kohsuke.github.*;

import java.util.*;

public class MyGithub {
    protected GitHub gitHub;
    protected GHPerson myself;
    protected Map<String, GHRepository> myRepos;
    private List<GHCommit> myCommits;
    public MyGithub(String token) throws IOException {
        gitHub = new GitHubBuilder().withOAuthToken(token).build();
    }

    private GHPerson getMyself() throws IOException {
        if (myself == null) {
            myself = gitHub.getMyself();
        }
        return myself;
    }

    public MyGithub(GitHub gitHub) {
        this.gitHub = gitHub;
    }


    private <T> Optional<T> withRetries(IOSupplier<T> op, int maxAttempts) {
        IOException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return Optional.ofNullable(op.get());
            } catch (IOException e) {
                last = e;
            }
        }

        // gracefully reporting an error message
        System.err.println("ERROR: GitHub API operation failed after " + maxAttempts + " attempts."
                + (last != null ? " Cause: " + last.getMessage() : ""));
        return Optional.empty();
    }

    public String getGithubName() {
        return withRetries(() -> gitHub.getMyself().getLogin(), 3)
                .orElse("ERROR");
    }


    private List<GHRepository> getRepos() throws IOException {
        if (myRepos == null) {
            myRepos = getMyself().getRepositories();
        }
        return new ArrayList<>(myRepos.values());
    }

    static private int argMax(int[] days) {
        int max = Integer.MIN_VALUE;
        int arg = -1;
        for (int i = 0; i < days.length; i++) {
            if (days[i] > max) {
                max = days[i];
                arg = i;
            }
        }
        return arg;
    }

    static private String intToDay(int day) {
        return switch (day) {
            case Calendar.SUNDAY -> "Sunday";
            case Calendar.MONDAY -> "Monday";
            case Calendar.TUESDAY -> "Tuesday";
            case Calendar.WEDNESDAY -> "Wednesday";
            case Calendar.THURSDAY -> "Thursday";
            case Calendar.FRIDAY -> "Friday";
            case Calendar.SATURDAY -> "Saturday";
            default -> throw new IllegalArgumentException("Not a day: " + day);
        };
    }

    public String getMostPopularDay() throws IOException {
        final int SIZE = 8;
        int[] days = new int[SIZE];
        Calendar cal = Calendar.getInstance();
        for (GHCommit commit: getCommits()) {
            Date date = commit.getCommitDate();
            cal.setTime(date);
            int day = cal.get(Calendar.DAY_OF_WEEK);
            days[day] += 1;
        }
        return intToDay(argMax(days));
    }

    protected Iterable<? extends GHCommit> getCommits() throws IOException {
        if (myCommits == null) {
            myCommits = new ArrayList<>();
            int count = 0;
            for (GHRepository repo: getRepos()) {
                System.out.println("Loading commits: repo " + repo.getName());
                try {
                    for (GHCommit commit : repo.queryCommits().author(getGithubName()).list()) {
                        myCommits.add(commit);
                        count++;
                        if (count % 100 == 0) {
                            System.out.println("Loading commits: " + count);
                        }
                    }
                } catch (GHException e) {
                    if (!e.getCause().getMessage().contains("Repository is empty")) {
                        throw e;
                    }
                }
            }
        }
        return myCommits;
    }


    public OptionalDouble getAverageTimeBetweenCommitsSeconds() throws IOException {
        // Collect commit times
        ArrayList<Date> commitDates = new ArrayList<>();
        for (GHCommit commit : getCommits()) {
            Date d = commit.getCommitDate();
            if (d != null) commitDates.add(d);
        }

        if (commitDates.size() < 2) return OptionalDouble.empty();

        // Sort ascending
        commitDates.sort(Comparator.naturalOrder());

        long totalSeconds = 0L;
        int gaps = 0;

        for (int i = 1; i < commitDates.size(); i++) {
            long prev = commitDates.get(i - 1).getTime();
            long curr = commitDates.get(i).getTime();
            long deltaSeconds = (curr - prev) / 1000L;
            totalSeconds += deltaSeconds;
            gaps++;
        }

        return OptionalDouble.of((double) totalSeconds / (double) gaps);
    }

    public OptionalDouble getAverageClosedIssueOpenTimeSeconds() throws IOException {
        long totalSeconds = 0L;
        int count = 0;

        for (GHRepository repo : getRepos()) {
            List<GHIssue> issues = repo.getIssues(GHIssueState.CLOSED);
            for (GHIssue issue : issues) {
                GHIssueWrapper w = new GHIssueWrapper(issue);
                Date created = w.getCreatedAt();
                Date closed = w.getClosedAt(); // should be non-null for closed issues, but be safe
                if (created != null && closed != null) {
                    long deltaSeconds = (closed.getTime() - created.getTime()) / 1000L;
                    if (deltaSeconds >= 0) { // ignore weird data
                        totalSeconds += deltaSeconds;
                        count++;
                    }
                }
            }
        }

        if (count == 0) return OptionalDouble.empty();
        return OptionalDouble.of((double) totalSeconds / (double) count);
    }

    public OptionalDouble getAverageClosedPullRequestOpenTimeSeconds() throws IOException {
        long totalSeconds = 0L;
        int count = 0;

        for (GHRepository repo : getRepos()) {
            // github-api provides listPullRequests for PRs
            // We include all states and then filter for closed using closedAt != null.
            PagedIterable<GHPullRequest> prs = repo.listPullRequests(GHIssueState.ALL);
            for (GHPullRequest pr : prs) {
                GHPullRequestWrapper w = new GHPullRequestWrapper(pr);
                Date created = w.getCreatedAt();
                Date closed = w.getClosedAt();
                if (created != null && closed != null) {
                    long deltaSeconds = (closed.getTime() - created.getTime()) / 1000L;
                    if (deltaSeconds >= 0) {
                        totalSeconds += deltaSeconds;
                        count++;
                    }
                }
            }
        }

        if (count == 0) return OptionalDouble.empty();
        return OptionalDouble.of((double) totalSeconds / (double) count);
    }

    public OptionalDouble getAverageBranchesPerRepo() throws IOException {
        List<GHRepository> repos = getRepos();
        if (repos.isEmpty()) return OptionalDouble.empty();

        long totalBranches = 0L;
        int repoCount = 0;

        for (GHRepository repo : repos) {
            // getBranches returns Map<String, GHBranch>
            Map<String, GHBranch> branches = repo.getBranches();
            totalBranches += (branches == null ? 0 : branches.size());
            repoCount++;
        }

        if (repoCount == 0) return OptionalDouble.empty();
        return OptionalDouble.of((double) totalBranches / (double) repoCount);
    }


    public ArrayList<Date> getIssueCreateDates() throws IOException {
        ArrayList<Date> result = new ArrayList<>();
        for (GHRepository repo: getRepos()) {
            List<GHIssue> issues = repo.getIssues(GHIssueState.CLOSED);
            for (GHIssue issue: issues)
                result.add((new GHIssueWrapper(issue)).getCreatedAt());
            }
        return result;
    }
}

