package dev.restone0616.ymp.controller;

import dev.restone0616.ymp.Application;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.Objects;
import java.util.ResourceBundle;

public class PopupController implements Initializable {
    @FXML
    private HBox titleBar;
    @FXML
    private Label titleLabel;
    @FXML
    private Label messageLabel;
    @FXML
    private Button minBtn;
    @FXML
    private Button closeBtn;
    @FXML
    private Button firstButton;
    @FXML
    private Button secondButton;

    @Setter
    private Stage stage;
    private double xOffset = 0;
    private double yOffset = 0;
    private boolean selectedFirstButton = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        closeBtn.setOnAction(e -> {
            if (stage != null) stage.close();
        });
        minBtn.setOnAction(e -> {
            if (stage != null) stage.setIconified(true);
        });
        firstButton.setOnAction(e -> {
            selectedFirstButton = true;
            if (stage != null) stage.close();
        });
        secondButton.setOnAction(e -> {
            selectedFirstButton = false;
            if (stage != null) stage.close();
        });
        titleBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        titleBar.setOnMouseDragged(event -> {
            if (stage != null) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });
    }

    public void setTitleAndMessage(String title, String message) {
        if (titleLabel != null) titleLabel.setText(title);
        if (messageLabel != null) messageLabel.setText(message);
    }

    public static boolean open(@NotNull Window owner, @NotNull String title, @NotNull String message, @NotNull String firstButtonText, @Nullable String secondButtonText) {
        try {
            FXMLLoader loader = new FXMLLoader(Application.class.getResource("popup.fxml"));
            Parent root = loader.load();

            PopupController controller = loader.getController();
            controller.setText(firstButtonText, secondButtonText);
            Stage popupStage = new Stage();
            popupStage.getIcons().add(new Image(Objects.requireNonNull(Application.class.getResourceAsStream("icon.png"))));
            controller.setStage(popupStage);

            controller.setTitleAndMessage(title, message);

            Scene scene = new Scene(root);
            scene.setFill(Color.TRANSPARENT);
            popupStage.setScene(scene);
            popupStage.initStyle(StageStyle.TRANSPARENT);

            popupStage.initOwner(owner);
            popupStage.initModality(Modality.WINDOW_MODAL);

            popupStage.showAndWait();
            return controller.selectedFirstButton;
        } catch (Exception ignored) {
            return false;
        }
    }

    public void setText(@NotNull String b1, @Nullable String b2) {
        firstButton.setText(b1);
        if(b2 == null)
            secondButton.setVisible(false);
        else
            secondButton.setText(b2);
    }
}
