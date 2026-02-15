package ca.ualberta.cs.cmput402.ghdow;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.kohsuke.github.GHIssue;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.mockito.MockedConstruction;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class) class MyGithubTest {
    @Test
    void getIssueCreateDates() throws IOException {
        // We don't have a login token for github :(
        String token = "I am a fake token";
        MyGithub my = new MyGithub(token);
        assertNotNull(my);

        // We made this field protected instead of private so we can inject our mock
        // directly
        my.gitHub = mock(GitHub.class);

        // Set up a fake repository
        String fakeRepoName = "fakeRepo";
        GHRepository fakeRepo = mock(GHRepository.class);

        // Put our fake repository in a list of fake repositories
        // We made this field protected instead of private so we can inject our mock
        // directly, but we could have mocked GHMyself/GHPerson instead
        my.myRepos = new HashMap<>();
        my.myRepos.put(fakeRepoName, fakeRepo);

        // Generate some mock issues with mock dates for our mock repository
        final int DATES = 30;

        ArrayList<GHIssue> mockIssues = new ArrayList<>();
        ArrayList<Date> expectedDates = new ArrayList<>();
        HashMap<String, Date> issueToDate = new HashMap<>();
        Calendar calendar = Calendar.getInstance();
        for (int i = 1; i < DATES+1; i++) {
            calendar.set(100, Calendar.JANUARY, i, 1, 1, 1);
            Date issueDate = calendar.getTime();

            // Give this mock GHIssue a unique Mockito "name"
            // This has nothing to do with github, you can
            // give any mockito object a name
            String issueMockName = String.format("getIssueCreateDates issue #%d", i);
            GHIssue issue = mock(GHIssue.class, issueMockName);

            expectedDates.add(issueDate);
            mockIssues.add(issue);

            // Note that we DO NOT try to
            // when(issue.getCreatedAt())
            // because that's what causes the Mockito/github-api bug ...
            // instead we'll just save what we would have wanted to do
            // in a hashmap and then apply it later to GHIssueWrapper
            // which does not have the bug because it doesn't use
            // github-api 's WithBridgeMethods

            issueToDate.put(issueMockName, issueDate);
        }

        // Supply the mock repo with a list of mock issues to return
        when(fakeRepo.getIssues(GHIssueState.CLOSED)).thenReturn(mockIssues);

        List<Date> actualDates;

        // Inside the try block, Mockito will intercept GHIssueWrapper's constructor
        // and have it construct mock GHIssueWrappers instead
        // We have to use a try-with-resources, or it will get stuck like this and probably
        // ruin our other tests.
        try (MockedConstruction<GHIssueWrapper> ignored = mockConstruction(
                GHIssueWrapper.class,
                (mock, context) -> {
                    // Figure out which GHIssue the mock GHIssueWrapper 's
                    // constructor was called with
                    GHIssue issue = (GHIssue) context.arguments().get(0);
                    assertNotNull(issue);

                    // Ask mockito what name we gave the mock issue
                    String issueName = mockingDetails(issue)
                            .getMockCreationSettings()
                            .getMockName()
                            .toString();

                    // Make sure GHIssueWrapper was constructed with one of our mock
                    // GHIssue objects
                    assertTrue(issueToDate.containsKey(issueName));

                    // Get the date associated with the mock GHIssue object
                    // This is where we work around the Mockito/github-api bug!
                    Date date = issueToDate.get(issueName);
                    assertNotNull(date);
                    // Apply the date to the mock GHIssueWrapper
                    when(mock.getCreatedAt()).thenReturn(date);
                }
        )) {
            // This is the only line actually inside the try block
            actualDates = my.getIssueCreateDates();
        }

        // Check that we got our fake dates out
        assertEquals(expectedDates.size(), DATES);
        assertEquals(actualDates.size(), DATES);

        for (int i = 1; i < DATES; i++) {
            assertEquals(expectedDates.get(i), actualDates.get(i));
            System.out.println(expectedDates.get(i));
        }
    }
}

