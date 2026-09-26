package clubstock.ui.controller;

import clubstock.application.ApplicationException;
import clubstock.application.loan.ExcoActiveLoan;
import clubstock.application.loan.LoanQueryService;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.collections.FXCollections;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;

/** Presents the Exco active-Loan screen. */
public final class ExcoActiveLoansController {
    private static final String UNEXPECTED_ERROR =
            "Active Loans could not be refreshed. Please try again.";
    private final LoanQueryService service;
    private final NavigationService navigation;
    @FXML
    private TableView<ExcoActiveLoan> loansTable;
    @FXML
    private TableColumn<ExcoActiveLoan, String> memberColumn;
    @FXML
    private TableColumn<ExcoActiveLoan, String> typeColumn;
    @FXML
    private TableColumn<ExcoActiveLoan, String> equipmentIdColumn;
    @FXML
    private TableColumn<ExcoActiveLoan, Object> statusColumn;
    @FXML
    private TableColumn<ExcoActiveLoan, Object> startedColumn;
    @FXML
    private TableColumn<ExcoActiveLoan, Object> endColumn;
    @FXML
    private TableColumn<ExcoActiveLoan, String> overdueColumn;
    @FXML
    private Label statusLabel;
    public ExcoActiveLoansController(LoanQueryService service, NavigationService navigation) {
        if (service == null || navigation == null) {
            throw new IllegalArgumentException("Active Loan dependencies cannot be null.");
        }
        this.service = service;
        this.navigation = navigation;
    }

    @FXML
    private void refresh() {
        try {
            var loans = service.listActiveForExco();
            loansTable.setItems(FXCollections.observableArrayList(loans));
            statusLabel.getStyleClass().remove("error-text");
            statusLabel.setText(loans.isEmpty() ? "No active Loans." : "");
        } catch (ApplicationException exception) {
            showLoadFailure(exception.displayMessage());
        } catch (RuntimeException exception) {
            showLoadFailure(UNEXPECTED_ERROR);
        }
    }

    private void showLoadFailure(String message) {
        loansTable.getItems().clear();
        if (!statusLabel.getStyleClass().contains("error-text")) {
            statusLabel.getStyleClass().add("error-text");
        }
        statusLabel.setText(message);
    }

    @FXML
    private void goBack() {
        navigation.show(Route.EXCO_HOME);
    }

    @FXML
    private void initialize() {
        memberColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().memberName()));
        typeColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().equipmentTypeName()));
        equipmentIdColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().equipmentId()));
        statusColumn.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().status()));
        startedColumn.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().startedAt()));
        endColumn.setCellValueFactory(value -> new ReadOnlyObjectWrapper<>(value.getValue().endDate()));
        overdueColumn.setCellValueFactory(value -> new ReadOnlyStringWrapper(value.getValue().overdue() ? "Overdue" : ""));
        refresh();
    }
}
