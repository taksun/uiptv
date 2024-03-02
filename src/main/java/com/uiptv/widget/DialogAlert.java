package com.uiptv.widget;

import com.uiptv.ui.RootApplication;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;

public class DialogAlert {
    public static Alert showDialog(String contents) {
        Alert confirmDialogue = new Alert(Alert.AlertType.CONFIRMATION, contents, ButtonType.YES, ButtonType.NO);
        Button yesButton = (Button) confirmDialogue.getDialogPane().lookupButton(ButtonType.YES);
        yesButton.setDefaultButton(false);
        Button noButton = (Button) confirmDialogue.getDialogPane().lookupButton(ButtonType.NO);
        noButton.setDefaultButton(true);
        confirmDialogue.initModality(Modality.APPLICATION_MODAL);
        confirmDialogue.initOwner(RootApplication.primaryStage);
        confirmDialogue.showAndWait();
        return confirmDialogue;
    }
}
