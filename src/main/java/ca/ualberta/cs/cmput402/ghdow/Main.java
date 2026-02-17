package ca.ualberta.cs.cmput402.ghdow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

public class Main {
    static String getOAuthToken() throws IOException {
        Path tokenFile = Paths.get(
                System.getProperty("user.home"),
                "githubOAuthToken.txt"
        );
        String token = Files.readString(tokenFile);
        return token.strip();
    }


    public static void main(String[] args) throws IOException {
        String token = getOAuthToken();

        GitHub gh = new GitHubBuilder().withOAuthToken(token).build();
        MyGithub my = new MyGithub(gh);

        System.out.println("Logged in as " + my.getGithubName());
        System.out.println("Most often commits on: " + my.getMostPopularDay());
    }

}