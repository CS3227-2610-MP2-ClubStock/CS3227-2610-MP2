package clubstock.ui.controller;

import java.util.List;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.catalog.CatalogType;
import clubstock.application.catalog.MemberCatalogService;
import clubstock.application.request.MemberRequestService;
import clubstock.application.request.RequestDraft;
import clubstock.application.request.RequestPreview;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Presents the Member request form, current preview, and submission confirmation.
 */
public final class RequestEntryController {
    private static final String CATALOG_LOAD_FAILURE =
            "Offered equipment types could not be loaded. Try refreshing.";
    private static final String REQUEST_FAILURE =
            "Your request could not be completed. Please try again.";

    private final MemberCatalogService catalogService;
    private final MemberRequestService requestService;
    private final NavigationService navigation;

    @FXML
    private Button backButton;
    @FXML
    private Button refreshTypesButton;
    @FXML
    private VBox requestForm;
    @FXML
    private ComboBox<CatalogType> equipmentTypeComboBox;
    @FXML
    private TextField quantityField;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private TextArea detailsTextArea;
    @FXML
    private Label emptyTypesLabel;
    @FXML
    private Label typeLoadErrorLabel;
    @FXML
    private Button reviewButton;
    @FXML
    private VBox previewPanel;
    @FXML
    private Label previewTypeLabel;
    @FXML
    private Label previewQuantityLabel;
    @FXML
    private Label previewDatesLabel;
    @FXML
    private Label previewDetailsLabel;
    @FXML
    private Label previewAvailabilityLabel;
    @FXML
    private Label zeroStockWarningLabel;
    @FXML
    private CheckBox zeroStockConfirmationCheckBox;
    @FXML
    private Button submitButton;
    @FXML
    private Label statusLabel;

    private RequestPreview currentPreview;
    private RequestDraft reviewedDraft;
    private boolean isBusy;

    /**
     * Creates the request-entry controller.
     *
     * @param catalogService Member-authorized offered-type query service.
     * @param requestService Member request preview and submission service.
     * @param navigation Navigation boundary.
     * @throws IllegalArgumentException If any dependency is null.
     */
    public RequestEntryController(MemberCatalogService catalogService,
            MemberRequestService requestService, NavigationService navigation) {
        if (catalogService == null || requestService == null || navigation == null) {
            throw new IllegalArgumentException("Request-entry dependencies cannot be null.");
        }
        this.catalogService = catalogService;
        this.requestService = requestService;
        this.navigation = navigation;
    }

    /**
     * Configures request-type rows and loads the initial Member catalogue.
     */
    @FXML
    private void initialize() {
        equipmentTypeComboBox.setCellFactory(listView -> new EquipmentTypeCell());
        equipmentTypeComboBox.setButtonCell(new EquipmentTypeCell());
        equipmentTypeComboBox.setAccessibleText("Equipment type and current available quantity");
        quantityField.textProperty().addListener((observable, previous, current) -> draftChanged());
        startDatePicker.valueProperty().addListener((observable, previous, current) -> draftChanged());
        endDatePicker.valueProperty().addListener((observable, previous, current) -> draftChanged());
        detailsTextArea.textProperty().addListener((observable, previous, current) -> draftChanged());
        equipmentTypeComboBox.valueProperty().addListener(
                (observable, previous, current) -> draftChanged());
        zeroStockConfirmationCheckBox.selectedProperty().addListener(
                (observable, previous, current) -> updateActionStates());
        hidePreview();
        clearStatus();
        refreshOfferedTypes();
    }

    /**
     * Reloads offered equipment types and their latest available quantities.
     */
    @FXML
    private void refreshTypes() {
        if (!isBusy) {
            refreshOfferedTypes();
        }
    }

    /**
     * Returns to the Member workspace.
     */
    @FXML
    private void goBack() {
        if (!isBusy) {
            navigation.show(Route.MEMBER_HOME);
        }
    }

    /**
     * Validates the form and shows a fresh request summary before submission.
     */
    @FXML
    private void reviewRequest() {
        if (isBusy) {
            return;
        }
        RequestDraft draft = formDraft();
        if (draft == null) {
            return;
        }

        setBusy(true);
        try {
            showPreview(requestService.preview(draft), false);
        } catch (ApplicationException exception) {
            showError(exception.displayMessage());
        } catch (RuntimeException exception) {
            showError(REQUEST_FAILURE);
        } finally {
            setBusy(false);
        }
    }

    /**
     * Submits the reviewed request after any required zero-stock confirmation.
     */
    @FXML
    private void submitRequest() {
        if (isBusy || currentPreview == null || reviewedDraft == null) {
            return;
        }

        boolean isConfirmedZeroStock = currentPreview.availableQuantity() == 0
                && zeroStockConfirmationCheckBox.isSelected();
        setBusy(true);
        try {
            requestService.submit(reviewedDraft, isConfirmedZeroStock);
            clearForm();
            refreshOfferedTypes();
            showSuccess("Your request was submitted for Exco review.");
        } catch (ApplicationException exception) {
            if (exception.errorCode() == ApplicationErrorCode.ZERO_STOCK_CONFIRMATION_REQUIRED) {
                refreshPreviewAfterStockChange();
            } else {
                showError(exception.displayMessage());
            }
        } catch (RuntimeException exception) {
            showError(REQUEST_FAILURE);
        } finally {
            setBusy(false);
        }
    }

    /**
     * Loads current offered types while preserving a still-offered selection and form draft.
     */
    private void refreshOfferedTypes() {
        CatalogType previouslySelected = equipmentTypeComboBox.getValue();
        boolean hadPreview = currentPreview != null;
        try {
            List<CatalogType> offeredTypes = catalogService.listOfferedTypes();
            equipmentTypeComboBox.getItems().setAll(offeredTypes);
            CatalogType restoredSelection = offeredTypes.stream()
                    .filter(type -> previouslySelected != null
                            && type.equipmentTypeId().equals(previouslySelected.equipmentTypeId()))
                    .findFirst()
                    .orElse(null);
            equipmentTypeComboBox.setValue(restoredSelection);
            boolean isEmpty = offeredTypes.isEmpty();
            emptyTypesLabel.setVisible(isEmpty);
            emptyTypesLabel.setManaged(isEmpty);
            typeLoadErrorLabel.setVisible(false);
            typeLoadErrorLabel.setManaged(false);
            updateActionStates();

            if (previouslySelected != null && restoredSelection == null) {
                hidePreview();
                showError("That equipment type is no longer offered. Select another type.");
            } else if (hadPreview) {
                hidePreview();
                showStatus("Equipment availability was refreshed. Review your request again.");
            } else if (isEmpty) {
                clearStatus();
            }
        } catch (ApplicationException exception) {
            showTypeLoadError(exception.displayMessage());
        } catch (RuntimeException exception) {
            showTypeLoadError(CATALOG_LOAD_FAILURE);
        }
    }

    /**
     * Reads required form values and rejects quantities that are not positive base-10 integers.
     *
     * @return Form draft, or {@code null} when inline validation has failed.
     */
    private RequestDraft formDraft() {
        CatalogType selectedType = equipmentTypeComboBox.getValue();
        if (selectedType == null) {
            showError("Select an offered equipment type.");
            return null;
        }
        String quantityText = quantityField.getText();
        if (quantityText == null || !quantityText.matches("[0-9]+")) {
            showError("Enter a positive whole-number quantity.");
            return null;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(quantityText);
        } catch (NumberFormatException exception) {
            showError("Enter a positive whole-number quantity within the supported range.");
            return null;
        }
        if (quantity <= 0) {
            showError("Enter a quantity greater than zero.");
            return null;
        }
        if (startDatePicker.getValue() == null || endDatePicker.getValue() == null) {
            showError("Select both a requested start date and end date.");
            return null;
        }
        return new RequestDraft(selectedType.equipmentTypeId(), quantity,
                startDatePicker.getValue(), endDatePicker.getValue(), detailsTextArea.getText());
    }

    /**
     * Displays the complete draft and requires a separate confirmation before submission.
     *
     * @param preview Current service preview.
     * @param isUpdated Whether availability changed during a prior submission attempt.
     */
    private void showPreview(RequestPreview preview, boolean isUpdated) {
        currentPreview = preview;
        reviewedDraft = preview.draft();
        previewTypeLabel.setText(preview.equipmentTypeName());
        previewQuantityLabel.setText(Integer.toString(reviewedDraft.quantity()));
        previewDatesLabel.setText(reviewedDraft.startDate() + " to " + reviewedDraft.endDate());
        String details = reviewedDraft.details();
        previewDetailsLabel.setText(details == null ? "None" : details);
        previewAvailabilityLabel.setText(preview.availableQuantity() + " available now");

        boolean isZeroStock = preview.availableQuantity() == 0;
        zeroStockWarningLabel.setVisible(isZeroStock);
        zeroStockWarningLabel.setManaged(isZeroStock);
        zeroStockConfirmationCheckBox.setSelected(false);
        zeroStockConfirmationCheckBox.setVisible(isZeroStock);
        zeroStockConfirmationCheckBox.setManaged(isZeroStock);
        submitButton.setText(isZeroStock ? "Confirm zero-stock request" : "Confirm and submit");
        previewPanel.setVisible(true);
        previewPanel.setManaged(true);
        if (isUpdated) {
            showStatus("Availability changed before submission. Review the updated summary and confirm again.");
        } else if (isZeroStock) {
            showStatus("Confirm the zero-stock warning before submitting this request.");
        } else {
            showStatus("Review this summary, then confirm submission.");
        }
        updateActionStates();
    }

    /**
     * Re-previews a request when stock reaches zero between review and submission.
     */
    private void refreshPreviewAfterStockChange() {
        try {
            RequestPreview refreshedPreview = requestService.preview(reviewedDraft);
            showPreview(refreshedPreview, true);
        } catch (ApplicationException exception) {
            hidePreview();
            showError(exception.displayMessage());
        } catch (RuntimeException exception) {
            hidePreview();
            showError(REQUEST_FAILURE);
        }
    }

    /**
     * Clears the form after a successful submission so the same request cannot be repeated
     * accidentally.
     */
    private void clearForm() {
        quantityField.clear();
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        detailsTextArea.clear();
        equipmentTypeComboBox.getSelectionModel().clearSelection();
        hidePreview();
    }

    /**
     * Invalidates a previously reviewed summary when form input changes.
     */
    private void draftChanged() {
        if (!isBusy) {
            hidePreview();
            clearStatus();
            updateActionStates();
        }
    }

    /**
     * Hides the preview and forgets its confirmation state.
     */
    private void hidePreview() {
        currentPreview = null;
        reviewedDraft = null;
        previewPanel.setVisible(false);
        previewPanel.setManaged(false);
        zeroStockConfirmationCheckBox.setSelected(false);
        zeroStockConfirmationCheckBox.setVisible(false);
        zeroStockConfirmationCheckBox.setManaged(false);
        updateActionStates();
    }

    /**
     * Disables duplicate actions and form edits while a synchronous service operation runs.
     *
     * @param isOperationInProgress Whether an operation is active.
     */
    private void setBusy(boolean isOperationInProgress) {
        isBusy = isOperationInProgress;
        updateActionStates();
    }

    /**
     * Updates action availability from current loading, busy, and confirmation state.
     */
    private void updateActionStates() {
        boolean hasOfferedTypes = !equipmentTypeComboBox.getItems().isEmpty();
        requestForm.setDisable(isBusy);
        equipmentTypeComboBox.setDisable(isBusy || !hasOfferedTypes);
        quantityField.setDisable(isBusy || !hasOfferedTypes);
        startDatePicker.setDisable(isBusy || !hasOfferedTypes);
        endDatePicker.setDisable(isBusy || !hasOfferedTypes);
        detailsTextArea.setDisable(isBusy || !hasOfferedTypes);
        backButton.setDisable(isBusy);
        refreshTypesButton.setDisable(isBusy);
        reviewButton.setDisable(isBusy || !hasOfferedTypes);
        previewPanel.setDisable(isBusy);
        boolean requiresZeroConfirmation = currentPreview != null
                && currentPreview.availableQuantity() == 0;
        submitButton.setDisable(isBusy || currentPreview == null
                || (requiresZeroConfirmation && !zeroStockConfirmationCheckBox.isSelected()));
    }

    /**
     * Shows a safe failure when offered types cannot be loaded.
     *
     * @param message Safe user-facing message.
     */
    private void showTypeLoadError(String message) {
        typeLoadErrorLabel.setText(message);
        typeLoadErrorLabel.setVisible(true);
        typeLoadErrorLabel.setManaged(true);
        updateActionStates();
        showError(message);
    }

    /**
     * Shows an error message using the shared inline status label.
     *
     * @param message Safe failure message.
     */
    private void showError(String message) {
        setStatusStyle("error-text");
        statusLabel.setText(message);
    }

    /**
     * Shows a successful submission message.
     *
     * @param message Success message.
     */
    private void showSuccess(String message) {
        setStatusStyle("success-text");
        statusLabel.setText(message);
    }

    /**
     * Shows neutral workflow guidance.
     *
     * @param message Guidance for the current screen state.
     */
    private void showStatus(String message) {
        setStatusStyle("supporting-text");
        statusLabel.setText(message);
    }

    /**
     * Clears the status message and restores neutral text styling.
     */
    private void clearStatus() {
        statusLabel.setText("");
        setStatusStyle("supporting-text");
    }

    /**
     * Replaces the status label style with one semantic state class.
     *
     * @param styleClass Status style class to apply.
     */
    private void setStatusStyle(String styleClass) {
        statusLabel.getStyleClass().removeAll("error-text", "success-text", "supporting-text");
        statusLabel.getStyleClass().add(styleClass);
    }

    /**
     * Formats a Member-safe type row without exposing physical equipment identities.
     *
     * @param type Offered equipment category.
     * @return Type name and currently available quantity.
     */
    private static String formatType(CatalogType type) {
        return type.name() + " — " + type.availableQuantity() + " available";
    }

    private static final class EquipmentTypeCell extends ListCell<CatalogType> {
        @Override
        protected void updateItem(CatalogType type, boolean isEmpty) {
            super.updateItem(type, isEmpty);
            if (isEmpty || type == null) {
                setText(null);
                setAccessibleText(null);
                return;
            }
            String text = formatType(type);
            setText(text);
            setAccessibleText(text);
        }
    }
}
