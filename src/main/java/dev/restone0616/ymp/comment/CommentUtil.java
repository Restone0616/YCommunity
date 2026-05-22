package dev.restone0616.ymp.comment;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.restone0616.ymp.Application;
import dev.restone0616.ymp.controller.MainController;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.*;

import java.io.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommentUtil {
    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final Pattern ISSUE_URL_PATTERN =
            Pattern.compile("https?://github\\.com/([^/]+)/([^/]+)/issues/(\\d+).*");

    private CommentUtil() {}
    public static @NotNull CompletableFuture<List<CommentItem>> getRawComments(@NotNull String issueUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                IssueIdentifier id = parseIssueUrl(issueUrl);
                String apiUrl = String.format("%s/repos/%s/%s/issues/%d/comments",
                        GITHUB_API_BASE, id.owner, id.repo, id.issueNumber);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .header("Accept", "application/vnd.github.v3+json")
                        .GET()
                        .build();

                HttpResponse<String> response = Application.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200)
                    throw new IOException("Failed to get comments: HTTP " + response.statusCode() + " " + response.body());

                Gson gson = new Gson();
                Type commentListType = new TypeToken<List<CommentItem>>() {}.getType();
                return gson.fromJson(response.body(), commentListType);
            } catch (Exception ignored) {
                return List.of();
            }
        });
    }

    public static @NotNull CompletableFuture<GHIssueComment> sendRawComment(@NotNull String issueUrl, @NotNull String message) {
        CompletableFuture<GHIssueComment> future = new CompletableFuture<>();
        if(!MainController.isLogined())
            return CompletableFuture.completedFuture(null);
        new Thread(() -> {
            File file = new File(Application.getDataDirectory(), "user.cache");
            String token = null;
            try (InputStream in = new FileInputStream(file)) {
                token = new String(in.readAllBytes());
            } catch (IOException ignored) {}
            if(token == null || token.isEmpty())
                future.complete(null);
            try {
                GitHub github = new GitHubBuilder().withOAuthToken(token).build();

                Pattern pattern = Pattern.compile("https?://(?:www\\.)?github\\.com/([^/]+)/([^/]+)/issues/(\\d+)");
                Matcher matcher = pattern.matcher(issueUrl);
                if (matcher.find()) {
                    String repoName = matcher.group(1) + "/" + matcher.group(2);
                    int issueNumber = Integer.parseInt(matcher.group(3));

                    GHRepository repo = github.getRepository(repoName);
                    GHIssue issue = repo.getIssue(issueNumber);

                    GHIssueComment comment = issue.comment(message);
                    future.complete(comment);
                } else
                    throw new GHFileNotFoundException("Repository not exists");
            } catch (Exception ignored) {
                future.complete(null);
            }
        }).start();
        return future;
    }

    private static @NotNull IssueIdentifier parseIssueUrl(@NotNull String issueUrl) {
        Matcher matcher = ISSUE_URL_PATTERN.matcher(issueUrl);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid GitHub issue URL: " + issueUrl);
        }
        return new IssueIdentifier(matcher.group(1), matcher.group(2), Integer.parseInt(matcher.group(3)));
    }

    private static class IssueIdentifier {
        final String owner;
        final String repo;
        final int issueNumber;

        IssueIdentifier(String owner, String repo, int issueNumber) {
            this.owner = owner;
            this.repo = repo;
            this.issueNumber = issueNumber;
        }
    }
}
