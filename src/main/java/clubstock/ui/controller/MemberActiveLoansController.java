package clubstock.ui.controller;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import clubstock.application.ApplicationException;
import clubstock.application.loan.LoanQueryService;
import clubstock.application.loan.MemberActiveLoan;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

/**
 * Presents the authenticated Member's unresolved individual Loans.
 */
public final class MemberActiveLoansController {
    /** Safe message shown when active Loans cannot be loaded. */
    private static final String LOAD_FAILURE_TEXT =
            "Your active Loans could not be loaded. Try refreshing.";
    /** Local date-time format for the loan start instant. */
    private static final DateTimeFormatter STARTED_AT_FORMAT =
            DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm").withZone(ZoneId.systemDefault());

    private final LoanQueryService loanQueryService;
    private final NavigationService navigation;

    @FXML
    private TableView<MemberActiveLoan> loansTable;
    @FXML
    private TableColumn<MemberActiveLoan, String> equipmentTypeColumn;
    @FXML
    private TableColumn<MemberActiveLoan, String> equipmentIdColumn;
    @FXML
    private TableColumn<MemberActiveLoan, String> statusColumn;
    @FXML
    private TableColumn<MemberActiveLoan, String> startedAtColumn;
    @FXML
    private TableColumn<MemberActiveLoan, String> endDateColumn;
    @FXML
    private TableColumn<MemberActiveLoan, String> overdueColumn;
    @FXML
    private Label emptyStateLabel;
    @FXML
    private Label errorLabel;

    /**
     * Creates the Member active-Loans controller.
     *
     * @param loanQueryService Shared role-authorized Loan query service.
     * @param navigation Navigation boundary.
     * @throws IllegalArgumentException If either dependency is null.
     */
    public MemberActiveLoansController(LoanQueryService loanQueryService,
            NavigationService navigation) {
        if (loanQueryService == null || navigation == null) {
            throw new IllegalArgumentException("Member active-Loans dependencies cannot be null.");
        }
        this.loanQueryService = loanQueryService;
        this.navigation = navigation;
    }

    /**
     * Configures Loan columns and loads the current Member's initial snapshot.
     */
    @FXML
    private void initialize() {
        equipmentTypeColumn.setCellValueFactory(loan ->
                new ReadOnlyStringWrapper(loan.getValue().equipmentTypeName()));
        equipmentIdColumn.setCellValueFactory(loan ->
                new ReadOnlyStringWrapper(loan.getValue().equipmentId()));
        statusColumn.setCellValueFactory(loan ->
                new ReadOnlyStringWrapper(loan.getValue().status().name()));
        startedAtColumn.setCellValueFactory(loan -> new ReadOnlyStringWrapper(
                formatStartedAt(loan.getValue().startedAt())));
        endDateColumn.setCellValueFactory(loan ->
                new ReadOnlyStringWrapper(loan.getValue().endDate().toString()));
        overdueColumn.setCellValueFactory(loan -> new ReadOnlyStringWrapper(
                loan.getValue().overdue() ? "Overdue" : "—"));
        clearResults();
        loadLoans();
    }

    /**
     * Reloads the current authenticated Member's Loan snapshot.
     */
    @FXML
    private void refresh() {
        loadLoans();
    }

    /**
     * Returns to the existing Member workspace.
     */
    @FXML
    private void goBack() {
        navigation.show(Route.MEMBER_HOME);
    }

    /**
     * Replaces displayed rows with a fresh service snapshot and clears stale rows on failure.
     */
    private void loadLoans() {
        clearResults();
        try {
            List<MemberActiveLoan> snapshot = loanQueryService.listActiveForMember();
            loansTable.getItems().setAll(snapshot);

            boolean isEmpty = snapshot.isEmpty();
            emptyStateLabel.setVisible(isEmpty);
            emptyStateLabel.setManaged(isEmpty);
            loansTable.setVisible(!isEmpty);
            loansTable.setManaged(!isEmpty);
        } catch (ApplicationException exception) {
            showLoadFailure(exception.displayMessage());
        } catch (RuntimeException exception) {
            showLoadFailure(LOAD_FAILURE_TEXT);
        }
    }

    /**
     * Clears rows and hides both result and error states before refreshing.
     */
    private void clearResults() {
        loansTable.getItems().clear();
        loansTable.setVisible(false);
        loansTable.setManaged(false);
        emptyStateLabel.setVisible(false);
        emptyStateLabel.setManaged(false);
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    /**
     * Shows a safe load failure after clearing all previous rows.
     *
     * @param message Safe message displayed to the Member.
     */
    private void showLoadFailure(String message) {
        loansTable.getItems().clear();
        loansTable.setVisible(false);
        loansTable.setManaged(false);
        emptyStateLabel.setVisible(false);
        emptyStateLabel.setManaged(false);
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    /**
     * Formats the loan start instant in the local timezone.
     *
     * @param startedAt Loan start instant.
     * @return Readable local date and time.
     */
    private static String formatStartedAt(Instant startedAt) {
        return STARTED_AT_FORMAT.format(startedAt);
    }
}
