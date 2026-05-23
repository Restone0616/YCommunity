package dev.restone0616.ymp.comment;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.restone0616.ymp.Application;
import dev.restone0616.ymp.controller.LoadingController;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@SuppressWarnings({"BusyWait", "ResultOfMethodCallIgnored", "deprecation"})
public class GithubLoginUtil {
    private static final int PORT = 38125;
    private static final String CLIENT_ID = "Ov23lij1GXwCVzo1saJS";
    private static final String CLIENT_SECRET = "543a01ffe4251df14c738a2e2fd9c6646f19721b";
    private static final String CALLBACK_URL = "http://localhost:" + PORT + "/callback";
    private static final String AUTHORIZATION_URL = "https://github.com/login/oauth/authorize";
    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private GithubLoginUtil() {}

    public static @NotNull CompletableFuture<Map.Entry<GHMyself, String>> loginGithub(@NotNull Stage mainStage) {
        LoadingController controller = LoadingController.open(mainStage);
        Objects.requireNonNull(controller);
        controller.updateProgress("正在登录至Github...", -1);

        CompletableFuture<Map.Entry<GHMyself, String>> future = new CompletableFuture<>();
        final int[] timer = new int[]{0};
        Thread mThread = new Thread(() -> {
            do {
                try {
                    Thread.sleep(500);
                    timer[0]++;
                } catch (Exception ignored) {
                }

                if (timer[0] >= 10) {
                    timer[0] = 0;
                    controller.nextBackground(true);
                }

            } while (!future.isDone());
            controller.closeStage();
        });
        mThread.setDaemon(true);
        mThread.start();

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.setExecutor(Executors.newFixedThreadPool(8));

            future.whenComplete((user, e) -> {
                mThread.interrupt();
                new Thread(() -> server.stop(1000)).start();
            });
            controller.getStage().setOnCloseRequest(event -> future.complete(null));

            server.createContext("/", exchange -> {
                String response = loadHtmlResponse("login-home-page.html");
                sendResponse(exchange, 200, response, "text/html");
            });
            server.createContext("/login", exchange -> {
                String authUrl = buildAuthorizationUrl();

                exchange.getResponseHeaders().set("Location", authUrl);
                exchange.sendResponseHeaders(302, -1);
            });
            server.createContext("/callback", exchange -> {
                try {
                    String query = exchange.getRequestURI().getQuery();
                    Map<String, String> params = parseQueryString(query);
                    String code = params.get("code");
                    if (code == null || code.isEmpty()) {
                        String error = params.get("error");
                        String response = loadHtmlResponse("error-page.html")
                                .replace("error_message", error);
                        sendResponse(exchange, 400, response, "text/html");
                        return;
                    }
                    String accessToken = exchangeCodeForToken(code);
                    if (accessToken != null) {
                        GHMyself myself = getUserInfo(accessToken);
                        Objects.requireNonNull(myself);
                        future.complete(Map.entry(myself, accessToken));
                        String response = loadHtmlResponse("login-success-page.html")
                                .replace("%login%", myself.getLogin() == null ? "Undefined" : myself.getLogin())
                                .replace("%name%", myself.getName() == null ? "Undefined" : myself.getName())
                                .replace("%email%", myself.getEmail() == null ? "Undefined" : myself.getEmail())
                                .replace("%avatar_url%", myself.getAvatarUrl() == null ? "Undefined" : myself.getAvatarUrl());
                        sendResponse(exchange, 200, response, "text/html");
                    } else {
                        future.complete(null);
                        String response = loadHtmlResponse("error-page.html")
                                .replace("error_message", "Fail to get access token");
                        sendResponse(exchange, 500, response, "text/html");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    future.complete(null);
                    String response = loadHtmlResponse("error-page.html")
                            .replace("error_message", "Internal Server Error");
                    sendResponse(exchange, 500, response, "text/html");
                }
            });
            server.createContext("/background.png", exchange -> {
                try (InputStream in = Application.class.getClassLoader().getResourceAsStream(
                        "dev/restone0616/ymp/web/background.png")) {
                    if (in == null) {
                        sendResponse(exchange, 404, "Not Found", "text/plain");
                        return;
                    }
                    byte[] imgBytes = in.readAllBytes();
                    exchange.getResponseHeaders().set("Content-Type", "image/png");
                    exchange.sendResponseHeaders(200, imgBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(imgBytes);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, 500, "Internal Server Error", "text/plain");
                }
            });
            server.start();
            Desktop.getDesktop().browse(URI.create("http://localhost:38125"));
            return future;
        } catch (Exception e) {
            e.printStackTrace();
            return CompletableFuture.completedFuture(null);
        }
    }

    public static @NotNull VerificationResult verifyAccessToken(@NotNull String accessToken) {
        try {
            GHMyself myself = getUserInfo(accessToken);
            if (myself != null) {
                return new VerificationResult(true, myself, null);
            } else {
                return new VerificationResult(false, null, "Failed to get user info with provided token");
            }
        } catch (IOException e) {
            return new VerificationResult(false, null, "Error verifying token: " + e.getMessage());
        }
    }

    private static @NotNull String buildAuthorizationUrl() {
        Map<String, String> params = new HashMap<>();
        params.put("client_id", CLIENT_ID);
        params.put("redirect_uri", CALLBACK_URL);
        params.put("scope", "public_repo");
        params.put("state", Long.toHexString(Double.doubleToLongBits(Math.random())));
        StringBuilder urlBuilder = new StringBuilder(AUTHORIZATION_URL);
        urlBuilder.append("?");

        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                urlBuilder.append("&");
            }
            urlBuilder.append(entry.getKey())
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }

        return urlBuilder.toString();
    }
    private static void sendResponse(@NotNull HttpExchange exchange, int statusCode, @NotNull String response, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    private static @Nullable String exchangeCodeForToken(@NotNull String code) throws IOException {
        String postData = buildTokenRequestParams(code);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(postData, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = Application.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject jsonResponse = Application.gson.fromJson(response.body(), JsonObject.class);
                return jsonResponse.get("access_token").getAsString();
            } else {
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }
    private static @NotNull String buildTokenRequestParams(@NotNull String code) {
        Map<String, String> params = new HashMap<>();
        params.put("client_id", CLIENT_ID);
        params.put("client_secret", CLIENT_SECRET);
        params.put("code", code);
        params.put("redirect_uri", CALLBACK_URL);

        StringBuilder postData = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                postData.append("&");
            }
            postData.append(entry.getKey())
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }

        return postData.toString();
    }
    private static @Nullable GHMyself getUserInfo(@NotNull String accessToken) throws IOException {
        GitHub gitHub = new GitHubBuilder().withOAuthToken(accessToken).build();
        return gitHub.getMyself();
    }
    private static @NotNull Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return params;
    }
    private static @NotNull String loadHtmlResponse(@NotNull String name) {
        try(InputStream stream = Objects.requireNonNull(GithubLoginUtil.class.getClassLoader().getResourceAsStream("dev/restone0616/ymp/web/" + name))) {
            byte[] bytes = new byte[stream.available()];
            stream.read(bytes);
            return new String(bytes);
        } catch (IOException ignored) {
            return "";
        }
    }
}
