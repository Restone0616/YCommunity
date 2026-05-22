package dev.restone0616.ymp.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.restone0616.ymp.Application;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DownloadUtil {
    private DownloadUtil() {}
    private static final String[] DOMAINS = {"p.savenow.to", "p.lbserver.xyz"};
    private static final String API_KEY = "dfcb6d76f2f6a9894gjkege8a4ab232222";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    public static @NotNull CompletableFuture<JsonElement> downloadMeta(@NotNull String link) {
        JsonElement emptyJson = new JsonObject();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(link)).timeout(Duration.ofSeconds(10)).build();
            return Application.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                if(response.body().startsWith("{"))
                                    return Application.gson.fromJson(response.body(), JsonObject.class);
                                else if(response.body().startsWith("["))
                                    return Application.gson.fromJson(response.body(), JsonArray.class);
                                else
                                    return emptyJson;
                            } catch (Exception e) {
                                return emptyJson;
                            }
                        } else {
                            return emptyJson;
                        }
                    })
                    .exceptionally(ex -> emptyJson);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(emptyJson);
        }
    }

    public static @NotNull CompletableFuture<Void> downloadSubtitle(@NotNull String link, @NotNull File file) {
        try {
            Path filePath = file.toPath();
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent))
                Files.createDirectories(parent);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(link))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            return Application.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofFile(filePath))
                    .thenApply(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            return null;
                        } else {
                            throw new RuntimeException(String.valueOf(response.statusCode()));
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public static void downloadVideo(@NotNull String link, @NotNull File file, int resolution, @NotNull BiConsumer<String, Integer> callback) {
        CompletableFuture.runAsync(() -> {
            try {
                callback.accept("preparing-download", -1);
                String activeDomain = getActiveDomain();
                callback.accept("resolving-api", -1);
                String format = String.valueOf(resolution);
                callback.accept("requesting-task", -1);
                String taskId = requestDownloadTask(activeDomain, link, format);
                callback.accept("creating-task", -1);
                String downloadUrl = pollForDownloadUrl(activeDomain, taskId, callback);
                callback.accept("downloading-file", 0);
                downloadFile(Objects.requireNonNull(downloadUrl), file, callback);
                callback.accept("complete", 100);
            } catch(Exception e) {
                callback.accept("network-error", 0);
            }
        });
    }
    private static @NotNull String getActiveDomain() {
        for (String domain : DOMAINS) {
            if (isDomainReachable(domain)) {
                return domain;
            }
        }
        return DOMAINS[0];
    }
    private static boolean isDomainReachable(@NotNull String domain) {
        try {
            String url = "https://" + domain + "/api/health";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(3))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = Application.httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }
    private static @NotNull String requestDownloadTask(@NotNull String domain, @NotNull String link, @NotNull String format)
            throws IOException, InterruptedException {
        String encodedUrl = URLEncoder.encode(link, StandardCharsets.UTF_8);
        String apiUrl = String.format("https://%s/ajax/download.php?copyright=0&format=%s&url=%s&api=%s",
                domain, format, encodedUrl, API_KEY);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://y2mate.yt/en/")
                .GET()
                .build();

        HttpResponse<String> response = Application.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String body = response.body();
        if (response.statusCode() != 200)
            throw new RuntimeException("HTTP error " + response.statusCode() + " while requesting task");
        return Objects.requireNonNull(extractTaskId(body));
    }
    private static @Nullable String pollForDownloadUrl(@NotNull String domain, @NotNull String taskId, @NotNull BiConsumer<String, Integer> callback)
            throws InterruptedException {
        int pollCount = 0;
        int maxPolls = 120;
        while (pollCount++ < maxPolls) {
            String progressUrl = String.format("https://%s/api/progress?id=%s", domain, taskId);
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(progressUrl))
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "https://y2mate.yt/en/")
                        .GET()
                        .build();

                HttpResponse<String> response = Application.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                if (response.statusCode() != 200) {
                    Thread.sleep(1000);
                    continue;
                }
                ProgressInfo info = parseProgress(body);
                if (info.error != null) {
                    callback.accept("download-error", 0);
                    throw new RuntimeException("Server reported error: " + info.error);
                }
                if (info.progressPercent >= 0)
                    callback.accept("server-processing", -1);
                if (info.downloadUrl != null)
                    return info.downloadUrl;
            } catch (IOException ignored) {}
            Thread.sleep(1500);
        }
        return null;
    }
    private static void downloadFile(@NotNull String url, @NotNull File destination, @NotNull BiConsumer<String, Integer> callback)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Referer", "https://y2mate.yt/en/")
                .GET()
                .build();

        HttpResponse<InputStream> response = Application.httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        long downloaded = 0;
        byte[] buffer = new byte[8192];
        int read;

        try (InputStream in = response.body();
             FileOutputStream out = new FileOutputStream(destination)) {
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (contentLength > 0) {
                    int percent = (int) ((downloaded * 100) / contentLength);
                    callback.accept("downloading-file", percent);
                } else {
                    callback.accept("downloading-file", -1);
                }
            }
        }
    }
    private static @Nullable String extractTaskId(@NotNull String json) {
        Pattern pattern = Pattern.compile("\"id\"\\s*:\\s*\"?([a-zA-Z0-9]+)\"?");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    private static @NotNull ProgressInfo parseProgress(@NotNull String json) {
        ProgressInfo info = new ProgressInfo();
        try {
            Pattern progPat = Pattern.compile("\"progress\"\\s*:\\s*(\\d+)");
            Matcher progMat = progPat.matcher(json);
            if (progMat.find()) {
                int prog = Integer.parseInt(progMat.group(1));
                info.progressPercent = Math.min(100, prog * 10);
            }
            Pattern textPat = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"");
            Matcher textMat = textPat.matcher(json);
            if (textMat.find()) {
                String text = textMat.group(1);
                if ("error".equalsIgnoreCase(text)) {
                    info.error = text;
                }
            }

            // download_url 字段
            Pattern urlPat = Pattern.compile("\"download_url\"\\s*:\\s*\"([^\"]+)\"");
            Matcher urlMat = urlPat.matcher(json);
            if (urlMat.find()) {
                info.downloadUrl = urlMat.group(1).replace("\\/", "/");
            }
        } catch (Exception e) {
            info.error = "JSON parse error: " + e.getMessage();
        }
        return info;
    }
    private static class ProgressInfo {
        int progressPercent = -1;
        String downloadUrl = null;
        String error = null;
    }
}
