package ca.ualberta.cs.cmput402.ghdow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.*;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MyGithubTest {

    // -----------------------------
    // Helper methods
    // -----------------------------
    private static Date dateUTC(int year, int monthZeroBased, int day, int hh, int mm, int ss) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, monthZeroBased);
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.HOUR_OF_DAY, hh);
        cal.set(Calendar.MINUTE, mm);
        cal.set(Calendar.SECOND, ss);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private static GHCommit commitAt(Date d) throws IOException {
        GHCommit c = mock(GHCommit.class);
        when(c.getCommitDate()).thenReturn(d);
        return c;
    }

    // -----------------------------
    // Existing test (kept): getIssueCreateDates()
    // -----------------------------
    @Test
    void getIssueCreateDates_returnsCreatedDatesForClosedIssues_usingWrapperConstructorMock() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = new MyGithub(gh);

        GHRepository fakeRepo = mock(GHRepository.class);
        my.myRepos = new HashMap<>();
        my.myRepos.put("fakeRepo", fakeRepo);

        final int DATES = 30;

        List<GHIssue> mockIssues = new ArrayList<>();
        List<Date> expectedDates = new ArrayList<>();
        Map<String, Date> issueToDate = new HashMap<>();

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        for (int i = 1; i <= DATES; i++) {
            calendar.set(2000, Calendar.JANUARY, i, 1, 1, 1);
            calendar.set(Calendar.MILLISECOND, 0);
            Date issueDate = calendar.getTime();

            String issueMockName = String.format("getIssueCreateDates issue #%d", i);
            GHIssue issue = mock(GHIssue.class, issueMockName);

            mockIssues.add(issue);
            expectedDates.add(issueDate);
            issueToDate.put(issueMockName, issueDate);
        }

        when(fakeRepo.getIssues(GHIssueState.CLOSED)).thenReturn(mockIssues);

        List<Date> actualDates;

        try (MockedConstruction<GHIssueWrapper> ignored = mockConstruction(
                GHIssueWrapper.class,
                (wrapperMock, context) -> {
                    GHIssue issueArg = (GHIssue) context.arguments().get(0);
                    assertNotNull(issueArg);

                    String issueName = mockingDetails(issueArg)
                            .getMockCreationSettings()
                            .getMockName()
                            .toString();

                    assertTrue(issueToDate.containsKey(issueName));
                    when(wrapperMock.getCreatedAt()).thenReturn(issueToDate.get(issueName));
                }
        )) {
            actualDates = my.getIssueCreateDates();
        }

        assertNotNull(actualDates);
        assertEquals(DATES, actualDates.size());

        for (int i = 0; i < DATES; i++) {
            assertEquals(expectedDates.get(i), actualDates.get(i));
        }

        verify(fakeRepo, times(1)).getIssues(GHIssueState.CLOSED);
    }

    // -----------------------------
    // Step 1 (1): getMostPopularDay()
    // -----------------------------
    @Test
    void getMostPopularDay_countsCommitDaysCorrectly() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = spy(new MyGithub(gh));

        // Build commits: 3 Mondays, 1 Tuesday
        // 2024-01-01 is Monday
        List<GHCommit> commits = Arrays.asList(
                commitAt(dateUTC(2024, Calendar.JANUARY, 1, 10, 0, 0)), // Mon
                commitAt(dateUTC(2024, Calendar.JANUARY, 1, 12, 0, 0)), // Mon
                commitAt(dateUTC(2024, Calendar.JANUARY, 8, 9, 0, 0)),  // Mon
                commitAt(dateUTC(2024, Calendar.JANUARY, 2, 9, 0, 0))   // Tue
        );

        doReturn(commits).when(my).getCommits();
        assertEquals("Monday", my.getMostPopularDay());
    }

    @Test
    void getMostPopularDay_tieReturnsFirstEncounteredDay() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = spy(new MyGithub(gh));

        // Two Mondays, Two Tuesdays
        List<GHCommit> commits = Arrays.asList(
                commitAt(dateUTC(2024, Calendar.JANUARY, 1, 10, 0, 0)), // Mon
                commitAt(dateUTC(2024, Calendar.JANUARY, 1, 12, 0, 0)), // Mon
                commitAt(dateUTC(2024, Calendar.JANUARY, 2, 9, 0, 0)),  // Tue
                commitAt(dateUTC(2024, Calendar.JANUARY, 2, 11, 0, 0))  // Tue
        );

        doReturn(commits).when(my).getCommits();

        assertEquals("Monday", my.getMostPopularDay());
    }

    @Test
    void getMostPopularDay_throwsWhenCommitDateIsNull() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = spy(new MyGithub(gh));

        GHCommit c = mock(GHCommit.class);
        when(c.getCommitDate()).thenReturn(null);

        doReturn(Collections.singletonList(c)).when(my).getCommits();

        assertThrows(NullPointerException.class, my::getMostPopularDay);

    }

    @Test
    void getAverageTimeBetweenCommitsSeconds_computesAverageGapSorted() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = spy(new MyGithub(gh));

        // Intentionally out of order:
        // t0=00:00, t1=00:10, t2=00:40 => gaps 600s and 1800s => avg 1200s
        Date t0 = dateUTC(2024, Calendar.JANUARY, 1, 0, 0, 0);
        Date t1 = dateUTC(2024, Calendar.JANUARY, 1, 0, 10, 0);
        Date t2 = dateUTC(2024, Calendar.JANUARY, 1, 0, 40, 0);

        List<GHCommit> commits = Arrays.asList(
                commitAt(t2),
                commitAt(t0),
                commitAt(t1)
        );

        doReturn(commits).when(my).getCommits();

        OptionalDouble avg = my.getAverageTimeBetweenCommitsSeconds();
        assertTrue(avg.isPresent());
        assertEquals(1200.0, avg.getAsDouble(), 1e-9);
    }

    @Test
    void getAverageTimeBetweenCommitsSeconds_emptyWhenLessThanTwoCommits() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = spy(new MyGithub(gh));

        doReturn(Collections.singletonList(commitAt(dateUTC(2024, Calendar.JANUARY, 1, 0, 0, 0))))
                .when(my).getCommits();

        assertTrue(my.getAverageTimeBetweenCommitsSeconds().isEmpty());
    }

    // Extra: null commit dates ignored
    @Test
    void getAverageTimeBetweenCommitsSeconds_ignoresNullCommitDates() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = spy(new MyGithub(gh));

        GHCommit c1 = mock(GHCommit.class, "c1");
        when(c1.getCommitDate()).thenReturn(dateUTC(2024, Calendar.JANUARY, 1, 0, 0, 0));

        GHCommit c2 = mock(GHCommit.class, "c2");
        when(c2.getCommitDate()).thenReturn(null); // ignored

        GHCommit c3 = mock(GHCommit.class, "c3");
        when(c3.getCommitDate()).thenReturn(dateUTC(2024, Calendar.JANUARY, 1, 0, 20, 0)); // +1200s

        doReturn(Arrays.asList(c1, c2, c3)).when(my).getCommits();

        OptionalDouble avg = my.getAverageTimeBetweenCommitsSeconds();
        assertTrue(avg.isPresent());
        assertEquals(1200.0, avg.getAsDouble(), 1e-9);
    }

    @Test
    void getAverageTimeBetweenCommitsSeconds_emptyWhenAllCommitDatesNull() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = spy(new MyGithub(gh));

        GHCommit c1 = mock(GHCommit.class);
        when(c1.getCommitDate()).thenReturn(null);
        GHCommit c2 = mock(GHCommit.class);
        when(c2.getCommitDate()).thenReturn(null);

        doReturn(Arrays.asList(c1, c2)).when(my).getCommits();
        assertTrue(my.getAverageTimeBetweenCommitsSeconds().isEmpty());
    }

    // -----------------------------
    // Step 1 (3): getAverageClosedIssueOpenTimeSeconds()
    // -----------------------------
    @Test
    void getAverageClosedIssueOpenTimeSeconds_averagesDurationsForClosedIssues() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = new MyGithub(gh);

        GHRepository repo = mock(GHRepository.class);
        my.myRepos = new HashMap<>();
        my.myRepos.put("r1", repo);

        // Two issues: 1h and 3h => avg 2h = 7200s
        GHIssue i1 = mock(GHIssue.class, "i1");
        GHIssue i2 = mock(GHIssue.class, "i2");
        when(repo.getIssues(GHIssueState.CLOSED)).thenReturn(Arrays.asList(i1, i2));

        Map<String, Date[]> issueTimes = new HashMap<>();
        issueTimes.put("i1", new Date[]{
                dateUTC(2024, Calendar.JANUARY, 1, 0, 0, 0),
                dateUTC(2024, Calendar.JANUARY, 1, 1, 0, 0)
        });
        issueTimes.put("i2", new Date[]{
                dateUTC(2024, Calendar.JANUARY, 1, 0, 0, 0),
                dateUTC(2024, Calendar.JANUARY, 1, 3, 0, 0)
        });

        OptionalDouble avg;
        try (MockedConstruction<GHIssueWrapper> ignored = mockConstruction(
                GHIssueWrapper.class,
                (w, context) -> {
                    GHIssue issueArg = (GHIssue) context.arguments().get(0);
                    String name = mockingDetails(issueArg).getMockCreationSettings().getMockName().toString();
                    Date[] times = issueTimes.get(name);
                    when(w.getCreatedAt()).thenReturn(times[0]);
                    when(w.getClosedAt()).thenReturn(times[1]);
                }
        )) {
            avg = my.getAverageClosedIssueOpenTimeSeconds();
        }

        assertTrue(avg.isPresent());
        assertEquals(7200.0, avg.getAsDouble(), 1e-9);
    }

    @Test
    void getAverageClosedIssueOpenTimeSeconds_emptyWhenNoValidClosedIssues() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = new MyGithub(gh);

        GHRepository repo = mock(GHRepository.class);
        my.myRepos = new HashMap<>();
        my.myRepos.put("r1", repo);

        GHIssue i1 = mock(GHIssue.class, "i1");
        when(repo.getIssues(GHIssueState.CLOSED)).thenReturn(Collections.singletonList(i1));

        OptionalDouble avg;
        try (MockedConstruction<GHIssueWrapper> ignored = mockConstruction(
                GHIssueWrapper.class,
                (w, context) -> {
                    when(w.getCreatedAt()).thenReturn(null);
                    when(w.getClosedAt()).thenReturn(null);
                }
        )) {
            avg = my.getAverageClosedIssueOpenTimeSeconds();
        }

        assertTrue(avg.isEmpty());
    }

    // Extra: issues negative duration ignored
    @Test
    void getAverageClosedIssueOpenTimeSeconds_ignoresNegativeDurations() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = new MyGithub(gh);

        GHRepository repo = mock(GHRepository.class);
        my.myRepos = new HashMap<>();
        my.myRepos.put("r1", repo);

        GHIssue iBad = mock(GHIssue.class, "iBad");
        GHIssue iGood = mock(GHIssue.class, "iGood");
        when(repo.getIssues(GHIssueState.CLOSED)).thenReturn(Arrays.asList(iBad, iGood));

        Map<String, Date[]> issueTimes = new HashMap<>();
        issueTimes.put("iBad", new Date[]{
                dateUTC(2024, Calendar.JANUARY, 1, 3, 0, 0),
                dateUTC(2024, Calendar.JANUARY, 1, 1, 0, 0)
        });
        issueTimes.put("iGood", new Date[]{
                dateUTC(2024, Calendar.JANUARY, 1, 0, 0, 0),
                dateUTC(2024, Calendar.JANUARY, 1, 1, 0, 0)
        });

        OptionalDouble avg;
        try (MockedConstruction<GHIssueWrapper> ignored = mockConstruction(
                GHIssueWrapper.class,
                (w, context) -> {
                    GHIssue issueArg = (GHIssue) context.arguments().get(0);
                    String name = mockingDetails(issueArg).getMockCreationSettings().getMockName().toString();
                    Date[] times = issueTimes.get(name);
                    when(w.getCreatedAt()).thenReturn(times[0]);
                    when(w.getClosedAt()).thenReturn(times[1]);
                }
        )) {
            avg = my.getAverageClosedIssueOpenTimeSeconds();
        }

        assertTrue(avg.isPresent());
        assertEquals(3600.0, avg.getAsDouble(), 1e-9);
    }

    @Test
    void getAverageClosedIssueOpenTimeSeconds_emptyWhenNoRepos() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = new MyGithub(gh);
        my.myRepos = new HashMap<>(); // no repos
        assertTrue(my.getAverageClosedIssueOpenTimeSeconds().isEmpty());
    }

    // -----------------------------
    // Step 1 (4): getAverageClosedPullRequestOpenTimeSeconds()
    // -----------------------------
    @Test
    void getAverageClosedPullRequestOpenTimeSeconds_ignoresOpenPRs_andAveragesClosed() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = new MyGithub(gh);

        GHRepository repo = mock(GHRepository.class);
        my.myRepos = new HashMap<>();
        my.myRepos.put("r1", repo);

        // 2 PRs: one closed after 2h, one still open (closedAt null) -> avg = 2h = 7200s
        GHPullRequest prClosed = mock(GHPullRequest.class, "prClosed");
        GHPullRequest prOpen = mock(GHPullRequest.class, "prOpen");

        @SuppressWarnings("unchecked")
        PagedIterable<GHPullRequest> prs = (PagedIterable<GHPullRequest>) mock(PagedIterable.class);
        @SuppressWarnings("unchecked")
        PagedIterator<GHPullRequest> it = (PagedIterator<GHPullRequest>) mock(PagedIterator.class);

        when(prs.iterator()).thenReturn(it);

        // iterator behavior: closed first, open second
        when(it.hasNext()).thenReturn(true, true, false);
        when(it.next()).thenReturn(prClosed, prOpen);

        when(repo.listPullRequests(GHIssueState.ALL)).thenReturn(prs);

        Map<String, Date[]> prTimes = new HashMap<>();
        prTimes.put("prClosed", new Date[]{
                dateUTC(2024, Calendar.JANUARY, 1, 0, 0, 0),
                dateUTC(2024, Calendar.JANUARY, 1, 2, 0, 0)
        });
        prTimes.put("prOpen", new Date[]{
                dateUTC(2024, Calendar.JANUARY, 1, 0, 0, 0),
                null
        });

        OptionalDouble avg;
        try (MockedConstruction<GHPullRequestWrapper> ignored = mockConstruction(
                GHPullRequestWrapper.class,
                (w, context) -> {
                    GHPullRequest prArg = (GHPullRequest) context.arguments().get(0);
                    String name = mockingDetails(prArg).getMockCreationSettings().getMockName().toString();
                    Date[] times = prTimes.get(name);
                    when(w.getCreatedAt()).thenReturn(times[0]);
                    when(w.getClosedAt()).thenReturn(times[1]);
                }
        )) {
            avg = my.getAverageClosedPullRequestOpenTimeSeconds();
        }

        assertTrue(avg.isPresent());
        assertEquals(7200.0, avg.getAsDouble(), 1e-9);
    }

    // Extra: PRs empty when none closed
    @Test
    void getAverageClosedPullRequestOpenTimeSeconds_emptyWhenNoClosedPRs() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = new MyGithub(gh);

        GHRepository repo = mock(GHRepository.class);
        my.myRepos = new HashMap<>();
        my.myRepos.put("r1", repo);

        GHPullRequest pr1 = mock(GHPullRequest.class, "pr1");
        GHPullRequest pr2 = mock(GHPullRequest.class, "pr2");

        @SuppressWarnings("unchecked")
        PagedIterable<GHPullRequest> prs = (PagedIterable<GHPullRequest>) mock(PagedIterable.class);
        @SuppressWarnings("unchecked")
        PagedIterator<GHPullRequest> it = (PagedIterator<GHPullRequest>) mock(PagedIterator.class);

        when(prs.iterator()).thenReturn(it);
        when(it.hasNext()).thenReturn(true, true, false);
        when(it.next()).thenReturn(pr1, pr2);
        when(repo.listPullRequests(GHIssueState.ALL)).thenReturn(prs);

        Map<String, Date[]> prTimes = new HashMap<>();
        prTimes.put("pr1", new Date[]{dateUTC(2024, Calendar.JANUARY, 1, 0, 0, 0), null});
        prTimes.put("pr2", new Date[]{dateUTC(2024, Calendar.JANUARY, 1, 1, 0, 0), null});

        OptionalDouble avg;
        try (MockedConstruction<GHPullRequestWrapper> ignored = mockConstruction(
                GHPullRequestWrapper.class,
                (w, context) -> {
                    GHPullRequest prArg = (GHPullRequest) context.arguments().get(0);
                    String name = mockingDetails(prArg).getMockCreationSettings().getMockName().toString();
                    Date[] times = prTimes.get(name);
                    when(w.getCreatedAt()).thenReturn(times[0]);
                    when(w.getClosedAt()).thenReturn(times[1]);
                }
        )) {
            avg = my.getAverageClosedPullRequestOpenTimeSeconds();
        }

        assertTrue(avg.isEmpty());
    }

    @Test
    void getAverageClosedPullRequestOpenTimeSeconds_ignoresNegativeDurations() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = new MyGithub(gh);

        GHRepository repo = mock(GHRepository.class);
        my.myRepos = new HashMap<>();
        my.myRepos.put("r1", repo);

        GHPullRequest prBad = mock(GHPullRequest.class, "prBad");
        GHPullRequest prGood = mock(GHPullRequest.class, "prGood");

        @SuppressWarnings("unchecked")
        PagedIterable<GHPullRequest> prs = (PagedIterable<GHPullRequest>) mock(PagedIterable.class);
        @SuppressWarnings("unchecked")
        PagedIterator<GHPullRequest> it = (PagedIterator<GHPullRequest>) mock(PagedIterator.class);

        when(prs.iterator()).thenReturn(it);
        when(it.hasNext()).thenReturn(true, true, false);
        when(it.next()).thenReturn(prBad, prGood);
        when(repo.listPullRequests(GHIssueState.ALL)).thenReturn(prs);

        Map<String, Date[]> prTimes = new HashMap<>();
        prTimes.put("prBad", new Date[]{
                dateUTC(2024, Calendar.JANUARY, 1, 3, 0, 0),
                dateUTC(2024, Calendar.JANUARY, 1, 1, 0, 0)
        });
        prTimes.put("prGood", new Date[]{
                dateUTC(2024, Calendar.JANUARY, 1, 0, 0, 0),
                dateUTC(2024, Calendar.JANUARY, 1, 2, 0, 0)
        });

        OptionalDouble avg;
        try (MockedConstruction<GHPullRequestWrapper> ignored = mockConstruction(
                GHPullRequestWrapper.class,
                (w, context) -> {
                    GHPullRequest prArg = (GHPullRequest) context.arguments().get(0);
                    String name = mockingDetails(prArg).getMockCreationSettings().getMockName().toString();
                    Date[] times = prTimes.get(name);
                    when(w.getCreatedAt()).thenReturn(times[0]);
                    when(w.getClosedAt()).thenReturn(times[1]);
                }
        )) {
            avg = my.getAverageClosedPullRequestOpenTimeSeconds();
        }

        assertTrue(avg.isPresent());
        assertEquals(7200.0, avg.getAsDouble(), 1e-9);
    }

    // -----------------------------
    // Step 1 (5): getAverageBranchesPerRepo()
    // -----------------------------
    @Test
    void getAverageBranchesPerRepo_averagesBranchCountsAcrossRepos() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = new MyGithub(gh);

        GHRepository r1 = mock(GHRepository.class);
        GHRepository r2 = mock(GHRepository.class);

        my.myRepos = new HashMap<>();
        my.myRepos.put("r1", r1);
        my.myRepos.put("r2", r2);

        Map<String, GHBranch> b1 = new HashMap<>();
        b1.put("main", mock(GHBranch.class));
        b1.put("dev", mock(GHBranch.class));

        Map<String, GHBranch> b2 = new HashMap<>();
        b2.put("main", mock(GHBranch.class));
        b2.put("feature", mock(GHBranch.class));
        b2.put("hotfix", mock(GHBranch.class));
        b2.put("release", mock(GHBranch.class));

        when(r1.getBranches()).thenReturn(b1); // 2
        when(r2.getBranches()).thenReturn(b2); // 4

        OptionalDouble avg = my.getAverageBranchesPerRepo();
        assertTrue(avg.isPresent());
        assertEquals(3.0, avg.getAsDouble(), 1e-9);
    }

    // Extra: branches empty/no repos + null branches map
    @Test
    void getAverageBranchesPerRepo_emptyWhenNoRepos() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = new MyGithub(gh);

        my.myRepos = new HashMap<>(); // no repos
        assertTrue(my.getAverageBranchesPerRepo().isEmpty());
    }

    @Test
    void getAverageBranchesPerRepo_treatsNullBranchMapAsZero() throws IOException {
        GitHub gh = mock(GitHub.class);
        MyGithub my = new MyGithub(gh);

        GHRepository r1 = mock(GHRepository.class);
        GHRepository r2 = mock(GHRepository.class);

        my.myRepos = new HashMap<>();
        my.myRepos.put("r1", r1);
        my.myRepos.put("r2", r2);

        when(r1.getBranches()).thenReturn(null); // treated as 0
        Map<String, GHBranch> b2 = new HashMap<>();
        b2.put("main", mock(GHBranch.class));
        b2.put("dev", mock(GHBranch.class));
        when(r2.getBranches()).thenReturn(b2); // 2

        OptionalDouble avg = my.getAverageBranchesPerRepo();
        assertTrue(avg.isPresent());
        assertEquals(1.0, avg.getAsDouble(), 1e-9); // (0 + 2) / 2 repos
    }

    // -----------------------------
    // Step 2: robustness tests (retries)
    // -----------------------------
    @Test
    void getGithubName_retriesTwice_thenSucceedsOnThird() throws IOException {
        GitHub gh = mock(GitHub.class);
        GHMyself me = mock(GHMyself.class);

        when(gh.getMyself())
                .thenThrow(new IOException("net1"))
                .thenThrow(new IOException("net2"))
                .thenReturn(me);

        when(me.getLogin()).thenReturn("Souhardya");

        MyGithub my = new MyGithub(gh);

        assertEquals("Souhardya", my.getGithubName());
        verify(gh, times(3)).getMyself();
        verify(me, times(1)).getLogin();
    }

    @Test
    void getGithubName_retriesThreeTimes_thenReturnsError() throws IOException {
        GitHub gh = mock(GitHub.class);

        when(gh.getMyself())
                .thenThrow(new IOException("net1"))
                .thenThrow(new IOException("net2"))
                .thenThrow(new IOException("net3"));

        MyGithub my = new MyGithub(gh);

        assertEquals("ERROR", my.getGithubName());
        verify(gh, times(3)).getMyself();
    }

    @Test
    void getGithubName_printsErrorMessageWhenAllRetriesFail() throws IOException {
        GitHub gh = mock(GitHub.class);

        when(gh.getMyself())
                .thenThrow(new IOException("net1"))
                .thenThrow(new IOException("net2"))
                .thenThrow(new IOException("net3"));

        MyGithub my = new MyGithub(gh);

        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(err));
        try {
            assertEquals("ERROR", my.getGithubName());
        } finally {
            System.setErr(oldErr);
        }

        String stderr = err.toString();
        assertTrue(stderr.contains("failed after 3 attempts"));
        assertTrue(stderr.contains("Cause: net3"));
    }

    // -----------------------------
    // Coverage helpers: hit getRepos() caching and real getCommits() path
    // -----------------------------
    @Test
    void getRepos_loadsReposFromMyself_andCachesMyself() throws IOException {
        GitHub gh = mock(GitHub.class);
        GHMyself me = mock(GHMyself.class);

        Map<String, GHRepository> reposMap = new HashMap<>();
        reposMap.put("r1", mock(GHRepository.class));
        reposMap.put("r2", mock(GHRepository.class));

        when(gh.getMyself()).thenReturn(me);
        when(me.getRepositories()).thenReturn(reposMap);

        MyGithub my = new MyGithub(gh);

        OptionalDouble avg = my.getAverageBranchesPerRepo();
        assertTrue(avg.isPresent());

        verify(gh, times(1)).getMyself();
        verify(me, times(1)).getRepositories();

        // Second call should not call getMyself again (cached)
        OptionalDouble avg2 = my.getAverageBranchesPerRepo();
        assertTrue(avg2.isPresent());
        verify(gh, times(1)).getMyself();
        verify(me, times(1)).getRepositories();
    }

    @Test
    void getCommits_ignoresRepositoryIsEmptyException() throws IOException {
        GitHub gh = mock(GitHub.class);
        GHMyself me = mock(GHMyself.class);

        GHRepository repo = mock(GHRepository.class);
        when(repo.getName()).thenReturn("emptyRepo");

        Map<String, GHRepository> reposMap = new HashMap<>();
        reposMap.put("emptyRepo", repo);

        when(gh.getMyself()).thenReturn(me);
        when(me.getRepositories()).thenReturn(reposMap);

        GHCommitQueryBuilder qb = mock(GHCommitQueryBuilder.class);
        when(repo.queryCommits()).thenReturn(qb);
        when(qb.author(anyString())).thenReturn(qb);

        Exception cause = new Exception("Repository is empty");
        GHException ghe = new GHException("boom", cause);
        when(qb.list()).thenThrow(ghe);

        MyGithub my = spy(new MyGithub(gh));
        doReturn("Souhardya").when(my).getGithubName();

        Iterable<? extends GHCommit> commits = my.getCommits();
        assertEquals(0, ((Collection<?>) commits).size());
    }

    @Test
    void getCommits_rethrowsNonEmptyRepoException() throws IOException {
        GitHub gh = mock(GitHub.class);
        GHMyself me = mock(GHMyself.class);

        GHRepository repo = mock(GHRepository.class);
        when(repo.getName()).thenReturn("badRepo");

        Map<String, GHRepository> reposMap = new HashMap<>();
        reposMap.put("badRepo", repo);

        when(gh.getMyself()).thenReturn(me);
        when(me.getRepositories()).thenReturn(reposMap);

        GHCommitQueryBuilder qb = mock(GHCommitQueryBuilder.class);
        when(repo.queryCommits()).thenReturn(qb);
        when(qb.author(anyString())).thenReturn(qb);

        Exception cause = new Exception("Some other GH error");
        GHException ghe = new GHException("boom", cause);
        when(qb.list()).thenThrow(ghe);

        MyGithub my = spy(new MyGithub(gh));
        doReturn("Souhardya").when(my).getGithubName();

        assertThrows(GHException.class, my::getCommits);
    }
}
