package dev.restone0616.ymp;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.restone0616.ymp.controller.MainController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.Random;

public class Application extends javafx.application.Application {
    public static final String TITLE = "YCommunity - 1.0.0 in Beta";
    public static final String MAIN_META_URL = "https://raw.githubusercontent.com/Restone0616/YCommunityResources/refs/heads/main/meta.json";

    public static final HttpClient httpClient;
    public static final Gson gson = new Gson();
    public static final Random random = new Random();

    static {
            httpClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
    }

    @Override
    public void init() {
        System.setProperty("java.net.useSystemProxies", "true");
    }

    @Override
    public void start(@NotNull Stage stage) throws IOException {
        MainController.setStage(stage);
        FXMLLoader fxmlLoader = new FXMLLoader(Application.class.getResource("main.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 640, 480);
        stage.setTitle(TITLE);
        stage.getIcons().add(new Image(Objects.requireNonNull(Application.class.getResourceAsStream("icon.png"))));
        stage.setScene(scene);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static @NotNull File getTempDirectory() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "YCommunity");
        dir.mkdirs();
        return dir;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static @NotNull File getDataDirectory() {
        File dir = new File(System.getenv("APPDATA"), "YCommunity");
        dir.mkdirs();
        return dir;
    }

    public static @NotNull JsonObject getLang() {
        try {
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(Application.class.getClassLoader().getResourceAsStream("dev/restone0616/ymp/lang.json")))).getAsJsonObject();
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }
}
