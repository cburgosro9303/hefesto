package org.iumotionlabs.hefesto.desktop.execution;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.iumotionlabs.hefesto.desktop.i18n.I18nService;

public class TaskManagerView extends VBox {

    private final TaskManagerViewModel viewModel = TaskManagerViewModel.getInstance();

    public TaskManagerView() {
        getStyleClass().add("task-manager-view");
        setSpacing(10);
        setPadding(new Insets(15));

        var title = new Label(I18nService.getInstance().t("execution.task.manager"));
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        var taskList = new ListView<ManagedTask>();
        taskList.setItems(viewModel.getTasks());
        taskList.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(ManagedTask task, boolean empty) {
                super.updateItem(task, empty);
                if (empty || task == null) {
                    setGraphic(null);
                    return;
                }

                var nameLabel = new Label(task.getName());
                nameLabel.setStyle("-fx-font-weight: bold;");

                var statusLabel = new Label();
                task.statusProperty().addListener((_, _, status) -> statusLabel.setText(status.name()));
                statusLabel.setText(task.statusProperty().get().name());

                var progressBar = new ProgressBar();
                progressBar.progressProperty().bind(task.progressProperty());
                progressBar.setPrefWidth(150);

                var cancelBtn = new Button(I18nService.getInstance().t("common.cancel"));
                cancelBtn.setOnAction(_ -> task.cancel());
                cancelBtn.disableProperty().bind(
                    task.statusProperty().isNotEqualTo(ManagedTask.Status.RUNNING)
                );

                var row = new HBox(10, nameLabel, statusLabel, progressBar, cancelBtn);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                setGraphic(row);
            }
        });

        VBox.setVgrow(taskList, Priority.ALWAYS);
        getChildren().addAll(title, taskList);
    }
}
