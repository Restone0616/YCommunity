package dev.restone0616.ymp.controller;

import com.google.gson.JsonObject;
import dev.restone0616.ymp.Application;
import dev.restone0616.ymp.code.Base64Util;
import dev.restone0616.ymp.comment.CommentUtil;
import dev.restone0616.ymp.comment.DanmakuItem;
import dev.restone0616.ymp.subtitle.SubtitleItem;
import dev.restone0616.ymp.subtitle.SubtitleUtil;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class PlayerController {
    private static final double DANMAKU_ROW_HEIGHT = 30;
    private static final double DANMAKU_DURATION = 10.0;

    @FXML
    private BorderPane root;
    @FXML
    private StackPane videoPane;
    @FXML
    private VBox toolbar;
    @FXML
    private Button playPauseButton;
    @FXML
    private Button subtitleButton;
    @FXML
    private Button danmakuButton;
    @FXML
    private Button fullscreenButton;
    @FXML
    private Slider progressSlider;
    @FXML
    private MediaView mediaView;
    @FXML
    private Label subtitleLabel;
    @FXML
    private Pane danmakuPane;

    private List<DanmakuItem> danmakuList = new ArrayList<>();
    private int currentDanmakuIndex = 0;
    private final Label[] danmakuRowNodes = new Label[10];

    private MediaPlayer mediaPlayer;

    private List<SubtitleItem> subtitles = new ArrayList<>();
    private int currentSubtitleIndex = -1;

    private Stage stage;
    private Stage primaryStage;
    private File subtitlesFile;
    private String danmakuLink;

    private long lastMouseMoved;
    private boolean isPlaying = true;
    private boolean isSubtitleOn = false;
    private boolean isDanmakuOn = false;
    private boolean isFullScreen = false;
    private boolean isDragging = false;

    @FXML
    private ImageView playIconView;
    @FXML
    private ImageView fullscreenIconView;
    @FXML
    private ImageView subtitleIconView;
    @FXML
    private ImageView danmakuIconView;
    @FXML
    private StackPane danmakuInputPane;
    @FXML
    private TextField danmakuTextField;
    @FXML
    private Button sendDanmakuButton;

    private Timeline toolbarTimeline;

    @FXML
    public void initialize() {
        Rectangle clipRect = new Rectangle();
        clipRect.widthProperty().bind(videoPane.widthProperty());
        clipRect.heightProperty().bind(videoPane.heightProperty());
        videoPane.setClip(clipRect);

        toolbar.setViewOrder(-1);
        subtitleLabel.setViewOrder(-2);

        playPauseButton.setOnAction(e -> handlePlayPause());
        subtitleButton.setOnAction(e -> handleSubtitleToggle());
        danmakuButton.setOnAction(e -> handleDanmakuToggle());
        fullscreenButton.setOnAction(e -> handleFullscreen());
        sendDanmakuButton.setOnAction(e -> sendDanmaku());
        danmakuTextField.setOnAction(e -> sendDanmaku());

        progressSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (isDragging && mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(newVal.doubleValue()));
            }
        });

        progressSlider.setOnMousePressed(e -> isDragging = true);
        progressSlider.setOnMouseReleased(e -> {
            isDragging = false;
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
            }
        });

        danmakuPane.prefWidthProperty().bind(videoPane.widthProperty());
        danmakuPane.prefHeightProperty().bind(videoPane.heightProperty());
        Arrays.fill(danmakuRowNodes, null);

        root.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE && isFullScreen) {
                handleFullscreen();
                event.consume();
            }
        });
        root.setFocusTraversable(true);
        root.requestFocus();
    }

    public static void open(@NotNull File video, @Nullable File subtitles, @Nullable File danmaku, @Nullable String danmakuLink, @NotNull Stage mainStage) {
        Platform.runLater(() -> {
            FXMLLoader loader = new FXMLLoader(Application.class.getResource("player.fxml"));
            Parent root;
            try {
                root = loader.load();
            } catch (IOException ignored) {
                return;
            }
            PlayerController controller = loader.getController();

            Stage playerStage = new Stage();
            playerStage.setScene(new Scene(root, 1280, 720));
            playerStage.setTitle(Application.TITLE);
            playerStage.getIcons().add(new Image(Objects.requireNonNull(Application.class.getResourceAsStream("icon.png"))));

            controller.initData(playerStage, mainStage, video, subtitles, danmaku, danmakuLink);

            mainStage.hide();
            playerStage.show();
        });
    }

    private void initData(@NotNull Stage playerStage, @NotNull Stage primaryStage, @NotNull File videoFile, @Nullable File subtitlesFile, @Nullable File danmakuFile, @Nullable String danmakuLink) {
        this.stage = playerStage;
        this.primaryStage = primaryStage;
        this.subtitlesFile = subtitlesFile;
        this.danmakuLink = danmakuLink;

        stage.setResizable(false);

        Media media = new Media(videoFile.toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        mediaView.setMediaPlayer(mediaPlayer);
        mediaView.setPreserveRatio(true);

        mediaPlayer.setAutoPlay(true);
        mediaPlayer.setOnEndOfMedia(() -> isPlaying = false);

        mediaPlayer.setOnReady(() -> {
            double totalSeconds = mediaPlayer.getTotalDuration().toSeconds();
            progressSlider.setMax(totalSeconds);
        });

        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (!isDragging && mediaPlayer.getTotalDuration() != null)
                progressSlider.setValue(newTime.toSeconds());
            if (isDanmakuOn && currentDanmakuIndex < danmakuList.size()) {
                long currentMs = (long) newTime.toMillis();
                while (currentDanmakuIndex < danmakuList.size() && danmakuList.get(currentDanmakuIndex).getTimestamp() <= currentMs) {
                    DanmakuItem item = danmakuList.get(currentDanmakuIndex);
                    Platform.runLater(() -> launchDanmaku(item.getContent()));
                    currentDanmakuIndex++;
                }
            }
        });

        if (subtitlesFile != null)
            subtitles = SubtitleUtil.parseSRT(subtitlesFile);

        subtitleLabel.translateYProperty().bind(
                Bindings.createDoubleBinding(
                        () -> toolbar.getTranslateY() - toolbar.getHeight(),
                        toolbar.translateYProperty(),
                        toolbar.heightProperty()
                )
        );
        mediaPlayer.currentTimeProperty().addListener((obs, old, newTime) -> {
            if (!isSubtitleOn || subtitles.isEmpty()) {
                Platform.runLater(() -> subtitleLabel.setText(""));
                return;
            }
            long currentMs = (long) newTime.toMillis();
            if (currentSubtitleIndex >= 0 && currentSubtitleIndex < subtitles.size()) {
                SubtitleItem curr = subtitles.get(currentSubtitleIndex);
                if (currentMs >= curr.startMs && currentMs <= curr.endMs) {
                    return;
                }
            }
            for (int i = 0; i < subtitles.size(); i++) {
                SubtitleItem item = subtitles.get(i);
                if (currentMs >= item.startMs && currentMs <= item.endMs) {
                    final int idx = i;
                    Platform.runLater(() -> {
                        currentSubtitleIndex = idx;
                        subtitleLabel.setText(item.text);
                        subtitleLabel.setOpacity(1);
                    });
                    return;
                }
            }
            Platform.runLater(() -> {
                currentSubtitleIndex = -1;
                subtitleLabel.setText("");
                subtitleLabel.setOpacity(0);
            });
        });

        if (danmakuFile != null) {
            try {
                String jsonContent = Files.readString(danmakuFile.toPath());
                DanmakuItem[] items = Application.gson.fromJson(jsonContent, DanmakuItem[].class);
                danmakuList = Arrays.asList(items);
                danmakuList.sort(Comparator.comparingLong(DanmakuItem::getTimestamp));
                currentDanmakuIndex = 0;
            } catch (IOException ignored) {}
        } else {
            danmakuList.clear();
            currentDanmakuIndex = 0;
        }

        if (danmakuLink == null || (!MainController.isLogined())) {
            danmakuTextField.setDisable(true);
            sendDanmakuButton.setDisable(true);
        }

        stage.setOnShown(e -> {
            toolbar.setTranslateY(toolbar.getHeight());
            root.setOnMouseEntered(event -> animateToolbar(true));
            root.setOnMouseExited(event -> animateToolbar(false));
        });

        stage.setOnHidden(e -> {
            dispose();
            primaryStage.show();
        });
    }

    private void handlePlayPause() {
        if (mediaPlayer == null) return;
        if (isPlaying) {
            mediaPlayer.pause();
            try {
                playIconView.setImage(new Image(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("dev/restone0616/ymp/icons/play.png"))));
            } catch (Exception ignored) {}
        } else {
            mediaPlayer.play();
            try {
                playIconView.setImage(new Image(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("dev/restone0616/ymp/icons/pause.png"))));
            } catch (Exception ignored) {}
        }
        isPlaying = !isPlaying;
    }

    private void handleSubtitleToggle() {
        isSubtitleOn = !isSubtitleOn;
        try {
            subtitleIconView.setImage(new Image(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(isSubtitleOn ? "dev/restone0616/ymp/icons/subtitle_on.png" : "dev/restone0616/ymp/icons/subtitle_off.png"))));
        } catch (Exception ignored) {}

        subtitleLabel.setVisible(isSubtitleOn);
        if (!isSubtitleOn) {
            subtitleLabel.setText("");
            currentSubtitleIndex = -1;
        }
    }

    private void handleDanmakuToggle() {
        isDanmakuOn = !isDanmakuOn;
        try {
            danmakuIconView.setImage(new Image(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(isDanmakuOn ? "dev/restone0616/ymp/icons/danmaku_on.png" : "dev/restone0616/ymp/icons/danmaku_off.png"))));
        } catch (Exception ignored) {}

        if (!isDanmakuOn) {
            Platform.runLater(() -> {
                danmakuPane.getChildren().clear();
                Arrays.fill(danmakuRowNodes, null);
            });
        }
    }

    private void handleFullscreen() {
        isFullScreen = !isFullScreen;
        try {
            fullscreenIconView.setImage(new Image(Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(isFullScreen ? "dev/restone0616/ymp/icons/fullscreen_on.png" : "dev/restone0616/ymp/icons/fullscreen_off.png"))));
        } catch (Exception ignored) {}

        stage.setFullScreen(isFullScreen);
        if (isFullScreen) {
            root.setPadding(new Insets(0, 0, 0, 0));
            root.setOnMouseEntered(event -> {});
            root.setOnMouseExited(event -> {});
            root.setOnMouseMoved(event -> {
                animateToolbar(true);
                lastMouseMoved = System.currentTimeMillis();
                new Thread(() -> {
                    long lastMouseMovedCache = lastMouseMoved;
                    int timer = 5;
                    while (timer > 0) {
                        timer --;
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {}
                        if(lastMouseMoved > lastMouseMovedCache)
                            return;
                    }
                    Platform.runLater(() -> animateToolbar(false));
                }).start();
            });

            mediaView.fitWidthProperty().bind(stage.getScene().widthProperty());
            mediaView.fitHeightProperty().bind(stage.getScene().heightProperty());
            videoPane.setClip(null);
        } else {
            root.setPadding(new Insets(25, 25, 0, 25));
            root.setOnMouseMoved(event -> {});
            root.setOnMouseEntered(event -> animateToolbar(true));
            root.setOnMouseExited(event -> animateToolbar(false));
            mediaView.fitWidthProperty().unbind();
            mediaView.fitHeightProperty().unbind();
            mediaView.setFitWidth(1230);
            mediaView.setFitHeight(680);
            Rectangle clipRect = new Rectangle();
            clipRect.widthProperty().bind(videoPane.widthProperty());
            clipRect.heightProperty().bind(videoPane.heightProperty());
            videoPane.setClip(clipRect);
        }
    }

    private void animateToolbar(boolean show) {
        if (toolbarTimeline != null) toolbarTimeline.stop();
        double currentOpacity = toolbar.getOpacity();
        double currentTranslateY = toolbar.getTranslateY();
        double targetOpacity = show ? 1.0 : 0.0;
        double targetTranslateY = show ? 0 : toolbar.getHeight();
        Duration duration = Duration.millis(200);
        toolbarTimeline = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(toolbar.opacityProperty(), currentOpacity),
                        new KeyValue(toolbar.translateYProperty(), currentTranslateY)
                ),
                new KeyFrame(duration,
                        new KeyValue(toolbar.opacityProperty(), targetOpacity),
                        new KeyValue(toolbar.translateYProperty(), targetTranslateY)
                )
        );
        toolbarTimeline.play();
    }

    private void launchDanmaku(@NotNull String text) {
        if (danmakuPane == null) return;
        int row = -1;
        for (int i = 0; i < 10; i++) {
            Label node = danmakuRowNodes[i];
            if (node == null) {
                row = i;
                break;
            } else {
                if (node.getBoundsInParent().getMaxX() <= 0) {
                    danmakuPane.getChildren().remove(node);
                    danmakuRowNodes[i] = null;
                    row = i;
                    break;
                }
            }
        }
        if (row == -1)
            return;

        Label label = new Label(text);
        label.getStyleClass().add("danmaku-label");
        label.setLayoutY(row * DANMAKU_ROW_HEIGHT);
        label.setLayoutX(0);
        danmakuPane.getChildren().add(label);

        label.applyCss();
        label.layout();

        double paneWidth = danmakuPane.getWidth();
        double labelWidth = label.prefWidth(-1);
        if (labelWidth <= 0) {
            labelWidth = label.getLayoutBounds().getWidth();
        }

        label.setTranslateX(paneWidth);

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(DANMAKU_DURATION),
                        new KeyValue(label.translateXProperty(), -labelWidth))
        );
        int finalRow = row;
        timeline.setOnFinished(e -> {
            danmakuPane.getChildren().remove(label);
            if (danmakuRowNodes[finalRow] == label) {
                danmakuRowNodes[finalRow] = null;
            }
        });
        timeline.play();
        danmakuRowNodes[row] = label;
    }
    private void sendDanmaku() {
        String text = danmakuTextField.getText().trim();
        if (text.isEmpty()) return;
        danmakuTextField.clear();
        int timestamp = (int) mediaPlayer.getCurrentTime().toMillis();
        JsonObject danmakuObj = new JsonObject();
        danmakuObj.addProperty("timestamp", timestamp);
        danmakuObj.addProperty("content", text);
        CommentUtil.sendRawComment(danmakuLink, Base64Util.encode(Application.gson.toJson(danmakuObj)));
        launchDanmaku(text);
    }

    private void dispose() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
    }
}
