package clubstock.ui.controller;

import java.util.List;

import clubstock.application.catalog.CatalogType;
import clubstock.application.catalog.MemberCatalogService;
import clubstock.ui.auth.LogoutAction;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Loads and presents the authenticated Member's current equipment catalogue.
 */
public final class MemberHomeController {
    private static final String LOAD_FAILURE_TEXT =
            "The equipment catalogue could not be loaded. Try refreshing.";

    private final LogoutAction logoutAction;
    private final MemberCatalogService catalogService;
    private final NavigationService navigation;

    @FXML
    private Label emptyStateLabel;
    @FXML
    private Label errorLabel;
    @FXML
    private ListView<CatalogType> catalogListView;

    /**
     * Creates the Member catalogue controller.
     *
     * @param logoutAction Shared logout action.
     * @param catalogService Member-authorized catalogue query service.
     * @param navigation Navigation boundary.
     * @throws IllegalArgumentException If either dependency is null.
     */
    public MemberHomeController(LogoutAction logoutAction, MemberCatalogService catalogService,
            NavigationService navigation) {
        if (logoutAction == null || catalogService == null || navigation == null) {
            throw new IllegalArgumentException("Member home dependencies cannot be null.");
        }
        this.logoutAction = logoutAction;
        this.catalogService = catalogService;
        this.navigation = navigation;
    }

    /**
     * Configures safe catalogue rows and loads the initial snapshot.
     */
    @FXML
    private void initialize() {
        catalogListView.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(CatalogType catalogType, boolean isEmpty) {
                super.updateItem(catalogType, isEmpty);
                if (isEmpty || catalogType == null) {
                    setText(null);
                    setGraphic(null);
                    setAccessibleText(null);
                    return;
                }

                String quantityText = formatAvailableQuantity(catalogType.availableQuantity());
                Label nameLabel = new Label(catalogType.name());
                nameLabel.getStyleClass().add("catalog-type-name");
                Label quantityLabel = new Label(quantityText);
                quantityLabel.getStyleClass().add(catalogType.availableQuantity() == 0
                        ? "zero-stock-status" : "available-status");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(12, nameLabel, spacer, quantityLabel);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setMaxWidth(Double.MAX_VALUE);
                row.getStyleClass().add("catalog-row");
                setText(null);
                setGraphic(row);
                setAccessibleText(catalogType.name() + ", " + quantityText);
            }
        });
        loadCatalog();
    }

    /**
     * Reloads the current Member catalogue snapshot.
     */
    @FXML
    private void refresh() {
        loadCatalog();
    }

    /**
     * Returns to role selection after clearing the current session.
     */
    @FXML
    private void logout() {
        logoutAction.execute();
    }

    /** Opens the request-entry screen for the authenticated Member. */
    @FXML
    private void openRequestEntry() {
        navigation.show(Route.MEMBER_REQUEST_ENTRY);
    }

    /** Opens the authenticated Member's request history. */
    @FXML
    private void openOwnRequests() {
        navigation.show(Route.MEMBER_OWN_REQUESTS);
    }

    /**
     * Replaces displayed rows after a successful query and clears them on failure.
     */
    private void loadCatalog() {
        try {
            List<CatalogType> snapshot = catalogService.listOfferedTypes();
            catalogListView.getItems().setAll(snapshot);

            boolean isEmpty = snapshot.isEmpty();
            emptyStateLabel.setVisible(isEmpty);
            emptyStateLabel.setManaged(isEmpty);
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
            errorLabel.setText("");
            catalogListView.setVisible(!isEmpty);
            catalogListView.setManaged(!isEmpty);
        } catch (RuntimeException exception) {
            catalogListView.getItems().clear();
            catalogListView.setVisible(false);
            catalogListView.setManaged(false);
            emptyStateLabel.setVisible(false);
            emptyStateLabel.setManaged(false);
            errorLabel.setText(LOAD_FAILURE_TEXT);
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        }
    }

    /**
     * Formats the textual quantity shown beside an equipment type.
     *
     * @param availableQuantity Current number available for allocation.
     * @return Accessible quantity status.
     */
    static String formatAvailableQuantity(int availableQuantity) {
        return availableQuantity + " available";
    }
}
