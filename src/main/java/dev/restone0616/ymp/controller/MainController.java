package dev.restone0616.ymp.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.restone0616.ymp.Application;
import dev.restone0616.ymp.code.Base64Util;
import dev.restone0616.ymp.comment.CommentItem;
import dev.restone0616.ymp.comment.CommentUtil;
import dev.restone0616.ymp.comment.GithubLoginUtil;
import dev.restone0616.ymp.comment.VerificationResult;
import dev.restone0616.ymp.network.DownloadUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SuppressWarnings({"BusyWait", "ResultOfMethodCallIgnored"})
public class MainController {
    @FXML
    private VBox container;

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private Button loginButton;

    @FXML
    private ImageView loginButtonView;

    @Getter
    private static boolean isLogined = false;

    private static JsonObject meta;

    private static Stage stage;

    public static void setStage(Stage stage) {
        MainController.stage = stage;
    }

    @FXML
    public void initialize() {
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        stage.setOnCloseRequest(event -> Platform.exit());
        loginButton.setOnMouseClicked(event -> {
            if(event.getButton() == MouseButton.PRIMARY)
                onLoginButtonClick();
        });

        LoadingController controller = LoadingController.open(stage);
        Objects.requireNonNull(controller);
        controller.updateProgress("正在初始化...", -1);

        final int[] timer = new int[]{0};
        final CompletableFuture<JsonElement> future = DownloadUtil.downloadMeta(Application.MAIN_META_URL);

        new Thread(() -> {
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
            controller.updateProgress("登录Github账号中...", -1);
            File file = new File(Application.getDataDirectory(), "user.cache");
            if(file.exists()) {
                VerificationResult result = null;
                try (InputStream in = new FileInputStream(file)) {
                    String token = new String(in.readAllBytes());
                    result = GithubLoginUtil.verifyAccessToken(token);
                } catch (IOException ignored) {}
                if (result != null && result.isValid()) {
                    isLogined = true;
                    loginButtonView.setImage(new Image(Objects.requireNonNull(Application.class.getClassLoader().getResourceAsStream("dev/restone0616/ymp/icons/logined.png"))));
                } else {
                    file.delete();
                    PopupController.open(stage, "提示", "登录信息已失效, 请点击右下角按钮重新登录!", "确定", null);
                }
            }
            controller.closeStage();
            try {
                meta = Objects.requireNonNull(future.get()).getAsJsonObject();
                if(meta.isEmpty())
                    throw new RuntimeException();
                Platform.runLater(() -> {
                    JsonArray groups = meta.getAsJsonArray("groups");

                    for (int i = 0; i < groups.size(); i++) {
                        JsonObject group = groups.get(i).getAsJsonObject();
                        String title = group.get("title").getAsString();
                        JsonArray elements = group.get("elements").getAsJsonArray();

                        TilePane buttonPane = new TilePane();
                        buttonPane.setHgap(8);
                        buttonPane.setVgap(8);
                        buttonPane.setPrefColumns(0);

                        for (int j = 0; j < elements.size(); j++) {
                            Button btn = new Button(String.valueOf(j));
                            JsonObject element = elements.get(j).getAsJsonObject();
                            btn.setText(element.get("title").getAsString());
                            btn.setOnAction(e -> onSelectButtonClick(element));
                            buttonPane.getChildren().add(btn);
                        }

                        TitledPane pane = new TitledPane(title, buttonPane);
                        pane.setAnimated(true);
                        pane.setCollapsible(true);
                        pane.setExpanded(false);
                        pane.getStyleClass().add("group-pane");

                        container.getChildren().add(pane);
                    }
                    stage.show();
                });
            } catch (Exception ignored) {
                Platform.runLater(() -> {
                    PopupController.open(controller.getStage(), "提示", "无法获取播放列表!", "确定", null);
                    Platform.exit();
                });
            }
        }).start();
    }

    public void onSelectButtonClick(@NotNull JsonObject element) {
        String videoLink = element.get("video").getAsString();
        String subtitlesLink = element.get("subtitles").getAsString();
        String commentsLink = element.get("comments").getAsString();

        LoadingController controller = LoadingController.open(stage);
        Objects.requireNonNull(controller);

        final int[] status = new int[]{0};
        final int[] timer = new int[]{0};

        DownloadUtil.downloadVideo(videoLink, new File(Application.getTempDirectory(), "video.cache"), 1080, (message, progress) -> {
            status[0] = switch (message) {
                case "complete" -> 1;
                case "network-error", "download-error" -> -1;
                case null, default -> 0;
            };
            controller.updateProgress(Application.getLang().get(message).getAsString(), progress / 100.0);
        });

        new Thread(() -> {
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

            } while (status[0] == 0);
            if (status[0] == 1) {
                controller.updateProgress(Application.getLang().get("downloading-subtitles").getAsString(), -1);
                if (!subtitlesLink.isEmpty()) {
                    try {
                        DownloadUtil.downloadSubtitle(subtitlesLink, new File(Application.getTempDirectory(), "subtitle.cache")).get();
                    } catch (Exception ignored) {
                    }
                }
                if(!commentsLink.isEmpty()) {
                    try {
                        List<CommentItem> comments = CommentUtil.getRawComments(commentsLink).get(5000, TimeUnit.MILLISECONDS);
                        JsonArray danmakuJson = new JsonArray();
                        for(CommentItem comment : comments) {
                            String raw = Base64Util.decode(comment.getBody());
                            try {
                                JsonObject danmakuObj = JsonParser.parseString(raw).getAsJsonObject();
                                if(danmakuObj.has("timestamp") && danmakuObj.has("content"))
                                    danmakuJson.add(danmakuObj);
                            } catch(Exception ignored) {}
                        }
                        Files.writeString(new File(Application.getTempDirectory(), "danmaku.cache").toPath(), Application.gson.toJson(danmakuJson));
                    } catch (ExecutionException | InterruptedException | TimeoutException | IOException ignored) {}
                }
                controller.updateProgress(Application.getLang().get("complete").getAsString(), 1);
                try {
                    Thread.sleep(1000);
                } catch (Exception ignored) {
                }
                controller.closeStage();
                PlayerController.open(
                        new File(Application.getTempDirectory(), "video.cache"),
                        subtitlesLink.isEmpty() ? null : new File(Application.getTempDirectory(), "subtitle.cache"),
                        commentsLink.isEmpty() ? null : new File(Application.getTempDirectory(), "danmaku.cache"),
                        commentsLink.isEmpty() ? null : commentsLink,
                        stage
                );
            } else
                PopupController.open(stage, "提示", "预加载视频失败, 请检查网络!", "确定", null);
        }).start();
    }

    public void onLoginButtonClick() {
        if(isLogined) {
            boolean confirm = PopupController.open(stage, "提示", "是否退出Github账号登录?", "确定", "取消");
            if(confirm) {
                File file = new File(Application.getDataDirectory(), "user.cache");
                file.delete();
                loginButtonView.setImage(new Image(Objects.requireNonNull(Application.class.getClassLoader().getResourceAsStream("dev/restone0616/ymp/icons/login.png"))));
                isLogined = false;
            }
        } else
            GithubLoginUtil.loginGithub(stage).thenAccept(info -> {
                if(info == null) {
                    Platform.runLater(() -> PopupController.open(stage, "提示", "登录失败, 请检查网络后重试!", "确定", null));
                    return;
                }
                File file = new File(Application.getDataDirectory(), "user.cache");
                try(FileWriter fw = new FileWriter(file)) {
                    fw.write(info.getValue());
                } catch (IOException ignored) {}
                isLogined = true;
                loginButtonView.setImage(new Image(Objects.requireNonNull(Application.class.getClassLoader().getResourceAsStream("dev/restone0616/ymp/icons/logined.png"))));
            });
    }
}
