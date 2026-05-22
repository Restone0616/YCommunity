package dev.restone0616.ymp.controller;

import dev.restone0616.ymp.Application;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.net.URL;
import java.util.Objects;

public class LoadingController {
    @FXML
    private BorderPane root;

    @FXML
    private Label loadingMessageLabel;

    @FXML
    private Label percentLabel;

    @FXML
    private ProgressBar progressBar;

    @Setter
    @Getter
    private Stage stage;

    private ImageView currentBackground;

    private static class Delta {
        double x, y;
    }

    @FXML
    public void initialize() {
        final Delta dragDelta = new Delta();
        root.setOnMousePressed(e -> {
            dragDelta.x = stage.getX() - e.getScreenX();
            dragDelta.y = stage.getY() - e.getScreenY();
        });
        root.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() + dragDelta.x);
            stage.setY(e.getScreenY() + dragDelta.y);
        });
    }

    public static @Nullable LoadingController open(@NotNull Stage mainStage) {
        try {
            Stage loadingStage = new Stage();
            loadingStage.setTitle(Application.TITLE);
            loadingStage.getIcons().add(new Image(Objects.requireNonNull(Application.class.getResourceAsStream("icon.png"))));
            loadingStage.initOwner(mainStage);
            loadingStage.initModality(Modality.WINDOW_MODAL);
            loadingStage.initStyle(StageStyle.TRANSPARENT);

            FXMLLoader loader = new FXMLLoader(Application.class.getResource("loading.fxml"));
            Parent root = loader.load();
            LoadingController controller = loader.getController();
            controller.setStage(loadingStage);

            Scene scene = new Scene(root, 720, 320);
            scene.setFill(Color.TRANSPARENT);
            loadingStage.setScene(scene);
            controller.nextBackground(false);
            loadingStage.show();

            return controller;
        } catch (Exception ignored) {
            return null;
        }
    }

    public void updateProgress(@NotNull String message, double progress) {
        Platform.runLater(() -> {
            loadingMessageLabel.setText(message);
            progressBar.setProgress(progress);
            if(progress >= 0)
                percentLabel.setText(String.format("%.0f%%", progress * 100));
        });
    }

    public void updateBackground(@NotNull URL url, boolean animation) {
        new Thread(() -> {
            try {
                Image fxImage = SwingFXUtils.toFXImage(ImageIO.read(url), null);
                Platform.runLater(() -> {
                    if (root == null) return;
                    ImageView newBg = new ImageView(fxImage);
                    newBg.setPreserveRatio(false);
                    newBg.setFitWidth(root.getWidth());
                    newBg.setFitHeight(root.getHeight());
                    newBg.setOpacity(0);
                    root.getChildren().addFirst(newBg);
                    FadeTransition fadeIn = new FadeTransition(Duration.millis(animation ? 500 : 1), newBg);
                    fadeIn.setFromValue(0);
                    fadeIn.setToValue(1);
                    ImageView oldBg = currentBackground;
                    if (oldBg != null) {
                        FadeTransition fadeOut = new FadeTransition(Duration.millis(animation ? 500 : 1), oldBg);
                        fadeOut.setFromValue(oldBg.getOpacity());
                        fadeOut.setToValue(0);
                        fadeOut.setOnFinished(e -> root.getChildren().remove(oldBg));
                        fadeOut.play();
                    }
                    fadeIn.play();
                    currentBackground = newBg;
                });
            } catch (Exception ignored) {}
        }).start();
    }

    public void nextBackground(boolean animation) {
        String backgroundId = "dev/restone0616/ymp/loading-backgrounds/" + Application.random.nextInt(10) + ".png";
        updateBackground(Objects.requireNonNull(getClass().getClassLoader().getResource(backgroundId)), animation);
    }

    public void closeStage() {
        if (stage != null)
            Platform.runLater(stage::close);
    }
}
