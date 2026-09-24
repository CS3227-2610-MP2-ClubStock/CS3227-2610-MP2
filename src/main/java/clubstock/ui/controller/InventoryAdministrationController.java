package clubstock.ui.controller;

import clubstock.application.ApplicationException;
import clubstock.application.inventory.EquipmentItemSummary;
import clubstock.application.inventory.EquipmentTypeSummary;
import clubstock.application.inventory.InventoryService;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Presents Exco equipment type and physical-item administration.
 */
public final class InventoryAdministrationController {
    private static final String UNEXPECTED_ERROR =
            "Inventory administration could not be completed. Please try again.";
    private final InventoryService inventoryService;
    private final NavigationService navigation;
    @FXML
    private TableView<EquipmentTypeSummary> typesTable;
    @FXML
    private TableColumn<EquipmentTypeSummary, String> typeIdColumn;
    @FXML
    private TableColumn<EquipmentTypeSummary, String> typeNameColumn;
    @FXML
    private TableColumn<EquipmentTypeSummary, String> typeOfferedColumn;
    @FXML
    private TableColumn<EquipmentTypeSummary, Number> availableQuantityColumn;
    @FXML
    private Button manageTypeButton;
    @FXML
    private Button addItemButton;
    @FXML
    private TableView<EquipmentItemSummary> itemsTable;
    @FXML
    private TableColumn<EquipmentItemSummary, String> itemIdColumn;
    @FXML
    private TableColumn<EquipmentItemSummary, String> itemTypeColumn;
    @FXML
    private TableColumn<EquipmentItemSummary, String> itemConditionColumn;
    @FXML
    private TableColumn<EquipmentItemSummary, String> itemAvailabilityColumn;
    @FXML
    private TableColumn<EquipmentItemSummary, String> itemRetiredColumn;
    @FXML
    private Button manageItemButton;
    @FXML
    private Label statusLabel;
    private EquipmentTypeSummary selectedType;
    private EquipmentItemSummary selectedItem;

    /**
     * Creates the controller.
     *
     * @param inventoryService Exco-authorized inventory operations.
     * @param navigation Navigation boundary.
     */
    public InventoryAdministrationController(InventoryService inventoryService,
            NavigationService navigation) {
        if (inventoryService == null || navigation == null) {
            throw new IllegalArgumentException("Inventory administration dependencies cannot be null.");
        }
        this.inventoryService = inventoryService;
        this.navigation = navigation;
    }

    /**
     * Configures inventory tables and reads their initial shared state.
     */
    @FXML
    private void initialize() {
        typeIdColumn.setCellValueFactory(type ->
                new ReadOnlyStringWrapper(type.getValue().equipmentTypeId()));
        typeNameColumn.setCellValueFactory(type -> new ReadOnlyStringWrapper(type.getValue().name()));
        typeOfferedColumn.setCellValueFactory(type -> new ReadOnlyStringWrapper(
                type.getValue().offered() ? "Offered" : "Unoffered"));
        availableQuantityColumn.setCellValueFactory(type ->
                new ReadOnlyIntegerWrapper(type.getValue().availableQuantity()));
        itemIdColumn.setCellValueFactory(item ->
                new ReadOnlyStringWrapper(item.getValue().equipmentId()));
        itemTypeColumn.setCellValueFactory(item ->
                new ReadOnlyStringWrapper(item.getValue().equipmentTypeName()));
        itemConditionColumn.setCellValueFactory(item ->
                new ReadOnlyStringWrapper(item.getValue().condition()));
        itemAvailabilityColumn.setCellValueFactory(item ->
                new ReadOnlyStringWrapper(item.getValue().availability()));
        itemRetiredColumn.setCellValueFactory(item -> new ReadOnlyStringWrapper(
                item.getValue().retired() ? "Retired" : "Active"));
        typesTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> selectType(current));
        itemsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> selectItem(current));
        manageTypeButton.setDisable(true);
        addItemButton.setDisable(true);
        manageItemButton.setDisable(true);
        refreshInventory();
    }

    /**
     * Opens the create-equipment-type dialog.
     */
    @FXML
    private void openCreateTypeDialog() {
        Dialog<ButtonType> dialog = newDialog("Create equipment type");
        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Hockey stick");
        Label feedback = newFeedbackLabel();
        Button createButton = primaryButton("Create type");
        createButton.setOnAction(event -> {
            if (execute(() -> inventoryService.createType(nameField.getText()),
                    "Equipment type created as unoffered.", feedback)) {
                dialog.close();
            }
        });
        VBox content = formContent();
        content.getChildren().addAll(labelFor("Equipment type name", nameField), nameField,
                createButton, feedback);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    /**
     * Opens actions for the selected equipment type.
     */
    @FXML
    private void openManageTypeDialog() {
        EquipmentTypeSummary type = requireSelectedType();
        if (type == null) {
            return;
        }
        Dialog<ButtonType> dialog = newDialog("Manage equipment type");
        Label idLabel = new Label(type.equipmentTypeId());
        idLabel.getStyleClass().add("selected-member-id");
        TextField nameField = new TextField(type.name());
        Label feedback = newFeedbackLabel();
        Button renameButton = secondaryButton("Save name");
        renameButton.setOnAction(event -> {
            if (execute(() -> inventoryService.renameType(type.equipmentTypeId(),
                    nameField.getText()), "Equipment type name updated.", feedback)) {
                dialog.close();
            }
        });
        Button offerButton = secondaryButton(type.offered() ? "Unoffer type" : "Offer type");
        offerButton.setOnAction(event -> {
            boolean completed = type.offered()
                    ? execute(() -> inventoryService.unofferType(type.equipmentTypeId()),
                            "Equipment type is no longer offered to Members.", feedback)
                    : execute(() -> inventoryService.offerType(type.equipmentTypeId()),
                            "Equipment type is now offered to Members.", feedback);
            if (completed) {
                dialog.close();
            }
        });
        CheckBox deleteConfirmation = new CheckBox(
                "I understand that this permanently removes the unreferenced type.");
        deleteConfirmation.setWrapText(true);
        Button deleteButton = dangerButton("Delete type");
        deleteButton.setOnAction(event -> {
            if (!deleteConfirmation.isSelected()) {
                showDialogError(feedback, "Select the confirmation checkbox before deleting.");
                return;
            }
            if (execute(() -> inventoryService.deleteType(type.equipmentTypeId()),
                    "Equipment type deleted.", feedback)) {
                dialog.close();
            }
        });
        VBox content = formContent();
        content.getChildren().addAll(new Label("Equipment type ID"), idLabel,
                labelFor("Equipment type name", nameField), nameField, renameButton, offerButton,
                deleteConfirmation, deleteButton, feedback);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    /**
     * Opens the add-item dialog for the selected equipment type.
     */
    @FXML
    private void openAddItemDialog() {
        EquipmentTypeSummary type = requireSelectedType();
        if (type == null) {
            return;
        }
        Dialog<ButtonType> dialog = newDialog("Add equipment item");
        Label typeLabel = new Label(type.name() + " (" + type.equipmentTypeId() + ")");
        typeLabel.setWrapText(true);
        TextField itemIdField = new TextField();
        itemIdField.setPromptText("Physical Equipment ID");
        Label feedback = newFeedbackLabel();
        Button addButton = primaryButton("Add item");
        addButton.setOnAction(event -> {
            if (execute(() -> inventoryService.addItem(itemIdField.getText(),
                    type.equipmentTypeId()), "Equipment item added as GOOD and UNAVAILABLE.",
                    feedback)) {
                dialog.close();
            }
        });
        VBox content = formContent();
        content.getChildren().addAll(new Label("Equipment type"), typeLabel,
                labelFor("Equipment ID", itemIdField), itemIdField, addButton, feedback);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    /**
     * Opens release and retirement actions for the selected physical item.
     */
    @FXML
    private void openManageItemDialog() {
        EquipmentItemSummary item = requireSelectedItem();
        if (item == null) {
            return;
        }
        Dialog<ButtonType> dialog = newDialog("Manage equipment item");
        Label details = new Label(item.equipmentId() + " — " + item.equipmentTypeName() + "\n"
                + "Condition: " + item.condition() + "\nAvailability: " + item.availability()
                + "\nState: " + (item.retired() ? "Retired" : "Active"));
        details.setWrapText(true);
        Label feedback = newFeedbackLabel();
        Button releaseButton = secondaryButton("Release for allocation");
        releaseButton.setDisable(item.retired());
        releaseButton.setOnAction(event -> {
            if (execute(() -> inventoryService.releaseItem(item.equipmentId()),
                    "Equipment item released for allocation.", feedback)) {
                dialog.close();
            }
        });
        CheckBox retirementConfirmation = new CheckBox(
                "I understand this retires the item from active inventory.");
        retirementConfirmation.setWrapText(true);
        Button retireButton = dangerButton("Retire item");
        retireButton.setDisable(item.retired());
        retireButton.setOnAction(event -> {
            if (!retirementConfirmation.isSelected()) {
                showDialogError(feedback, "Select the confirmation checkbox before retiring.");
                return;
            }
            if (execute(() -> inventoryService.retireItem(item.equipmentId()),
                    "Equipment item retired.", feedback)) {
                dialog.close();
            }
        });
        VBox content = formContent();
        content.getChildren().addAll(details, releaseButton, retirementConfirmation, retireButton,
                feedback);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    /**
     * Refreshes the type and item lists from the shared inventory state.
     */
    @FXML
    private void refreshInventory() {
        try {
            typesTable.setItems(FXCollections.observableArrayList(inventoryService.listTypes()));
            itemsTable.setItems(FXCollections.observableArrayList(inventoryService.listItems()));
            selectedType = null;
            selectedItem = null;
            manageTypeButton.setDisable(true);
            addItemButton.setDisable(true);
            manageItemButton.setDisable(true);
            clearStatus();
        } catch (ApplicationException exception) {
            showError(exception.displayMessage());
        } catch (RuntimeException exception) {
            showError(UNEXPECTED_ERROR);
        }
    }

    /**
     * Returns to the Exco workspace.
     */
    @FXML
    private void goBack() {
        navigation.show(Route.EXCO_HOME);
    }

    private boolean execute(Runnable operation, String successMessage, Label dialogFeedback) {
        clearStatus();
        try {
            operation.run();
            refreshInventory();
            showSuccess(successMessage);
            return true;
        } catch (ApplicationException exception) {
            showDialogError(dialogFeedback, exception.displayMessage());
            showError(exception.displayMessage());
            return false;
        } catch (RuntimeException exception) {
            showDialogError(dialogFeedback, UNEXPECTED_ERROR);
            showError(UNEXPECTED_ERROR);
            return false;
        }
    }

    private EquipmentTypeSummary requireSelectedType() {
        if (selectedType == null) {
            showError("Select an equipment type first.");
        }
        return selectedType;
    }

    private EquipmentItemSummary requireSelectedItem() {
        if (selectedItem == null) {
            showError("Select an equipment item first.");
        }
        return selectedItem;
    }

    private void selectType(EquipmentTypeSummary type) {
        selectedType = type;
        manageTypeButton.setDisable(type == null);
        addItemButton.setDisable(type == null);
    }

    private void selectItem(EquipmentItemSummary item) {
        selectedItem = item;
        manageItemButton.setDisable(item == null);
    }

    private static Dialog<ButtonType> newDialog(String title) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setMinWidth(460);
        dialog.setResizable(true);
        return dialog;
    }

    private static VBox formContent() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(4));
        content.getStyleClass().add("account-form-card");
        return content;
    }

    private static Label labelFor(String text, Node field) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        label.setLabelFor(field);
        return label;
    }

    private static Button primaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        return button;
    }

    private static Button secondaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        return button;
    }

    private static Button dangerButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("danger-button");
        return button;
    }

    private static Label newFeedbackLabel() {
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.getStyleClass().add("error-text");
        return feedback;
    }

    private static void showDialogError(Label feedback, String message) {
        feedback.getStyleClass().remove("success-text");
        if (!feedback.getStyleClass().contains("error-text")) {
            feedback.getStyleClass().add("error-text");
        }
        feedback.setText(message);
    }

    private void showError(String message) {
        statusLabel.getStyleClass().remove("success-text");
        if (!statusLabel.getStyleClass().contains("error-text")) {
            statusLabel.getStyleClass().add("error-text");
        }
        statusLabel.setText(message);
    }

    private void showSuccess(String message) {
        statusLabel.getStyleClass().remove("error-text");
        if (!statusLabel.getStyleClass().contains("success-text")) {
            statusLabel.getStyleClass().add("success-text");
        }
        statusLabel.setText(message);
    }

    private void clearStatus() {
        statusLabel.setText("");
        statusLabel.getStyleClass().remove("success-text");
        if (!statusLabel.getStyleClass().contains("error-text")) {
            statusLabel.getStyleClass().add("error-text");
        }
    }
}
