package clubstock.ui.controller;

import clubstock.application.loan.LoanQueryService;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

/** Presents the Exco active-Loan screen. */
public final class ExcoActiveLoansController {
    private final LoanQueryService service; private final NavigationService navigation;
    @FXML private Label statusLabel;
    public ExcoActiveLoansController(LoanQueryService service, NavigationService navigation) {
        this.service = service; this.navigation = navigation;
    }
    @FXML private void refresh() { statusLabel.setText("Active Loans: " + service.listActiveForExco().size()); }
    @FXML private void goBack() { navigation.show(Route.EXCO_HOME); }
    @FXML private void initialize() { refresh(); }
}
