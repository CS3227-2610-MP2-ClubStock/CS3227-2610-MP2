package clubstock.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import javafx.fxml.FXML;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import clubstock.ui.controller.ExcoActiveLoansController;
import clubstock.ui.controller.ExcoHomeController;
import clubstock.ui.controller.ExcoLoginController;
import clubstock.ui.controller.ExcoReportVerificationController;
import clubstock.ui.controller.ExcoRequestQueueController;
import clubstock.ui.controller.ExcoSetupController;
import clubstock.ui.controller.InventoryAdministrationController;
import clubstock.ui.controller.MemberAdministrationController;
import clubstock.ui.controller.MemberActiveLoansController;
import clubstock.ui.controller.MemberHomeController;
import clubstock.ui.controller.MemberLoginController;
import clubstock.ui.controller.MemberOwnRequestsController;
import clubstock.ui.controller.RequestEntryController;
import clubstock.ui.controller.RoleSelectionController;
import clubstock.ui.navigation.Route;

class FxmlResourceTest {
    private static final String FXML_NAMESPACE = "http://javafx.com/fxml/1";
    private static final String STYLESHEET = "/clubstock/ui/clubstock.css";
    private static final Map<Route, Class<?>> CONTROLLERS = Map.ofEntries(
            Map.entry(Route.ROLE_SELECTION, RoleSelectionController.class),
            Map.entry(Route.EXCO_SETUP, ExcoSetupController.class),
            Map.entry(Route.EXCO_LOGIN, ExcoLoginController.class),
            Map.entry(Route.MEMBER_LOGIN, MemberLoginController.class),
            Map.entry(Route.EXCO_HOME, ExcoHomeController.class),
            Map.entry(Route.MEMBER_ADMINISTRATION, MemberAdministrationController.class),
            Map.entry(Route.INVENTORY_ADMINISTRATION, InventoryAdministrationController.class),
            Map.entry(Route.EXCO_REQUEST_QUEUE, ExcoRequestQueueController.class),
            Map.entry(Route.EXCO_ACTIVE_LOANS, ExcoActiveLoansController.class),
            Map.entry(Route.EXCO_REPORT_VERIFICATION, ExcoReportVerificationController.class),
            Map.entry(Route.MEMBER_HOME, MemberHomeController.class),
            Map.entry(Route.MEMBER_REQUEST_ENTRY, RequestEntryController.class),
            Map.entry(Route.MEMBER_OWN_REQUESTS, MemberOwnRequestsController.class),
            Map.entry(Route.MEMBER_ACTIVE_LOANS, MemberActiveLoansController.class));

    @Test
    void everyRouteHasWellFormedFxmlWithTheRegisteredController()
            throws IOException, ParserConfigurationException, SAXException {
        for (Route route : Route.values()) {
            URL resource = FxmlResourceTest.class.getResource(route.resourcePath());
            assertNotNull(resource, route.resourcePath());
            try (InputStream stream = resource.openStream()) {
                Document document = secureDocumentBuilderFactory()
                        .newDocumentBuilder().parse(stream);
                String controller = document.getDocumentElement()
                        .getAttributeNS(FXML_NAMESPACE, "controller");
                assertEquals(CONTROLLERS.get(route).getName(), controller, route.name());
            }
        }
    }

    @Test
    void sharedStylesheetIncludesVisibleFocusAndRoleScreenStyles() throws IOException {
        String css = resourceText(STYLESHEET);

        assertTrue(css.contains(":focused"));
        assertTrue(css.contains(".error-text"));
        assertTrue(css.contains(".member-shell"));
        assertTrue(css.contains(".exco-shell"));
        assertTrue(css.contains(".catalog-list-view"));
        assertTrue(css.contains(".zero-stock-status"));
    }

    @Test
    void excoCredentialLabelsUseControllerBindings() throws IOException {
        assertCredentialLabelBindings(Route.EXCO_LOGIN, "passwordLabel", "passwordField");
        assertCredentialLabelBindings(Route.EXCO_SETUP, "passwordLabel", "passwordField");
        assertCredentialLabelBindings(Route.EXCO_SETUP, "confirmationLabel", "confirmationField");
    }

    @Test
    void memberLoginRouteContainsBothCredentialsAndActions() throws IOException {
        String fxml = routeText(Route.MEMBER_LOGIN);

        assertTrue(fxml.contains("fx:id=\"memberIdField\""));
        assertTrue(fxml.contains("fx:id=\"passwordField\""));
        assertTrue(fxml.contains("onAction=\"#login\""));
        assertTrue(fxml.contains("onAction=\"#goBack\""));
        assertFalse(fxml.contains("not available in this build yet"));
    }

    @Test
    void memberHomeRouteContainsSafeCatalogueAndRefreshBindings()
            throws IOException, NoSuchMethodException {
        String fxml = routeText(Route.MEMBER_HOME);

        assertTrue(fxml.contains("fx:id=\"catalogListView\""));
        assertTrue(fxml.contains("Offered equipment types and available quantities"));
        assertTrue(fxml.contains("No equipment is currently offered"));
        assertTrue(fxml.contains("fx:id=\"errorLabel\""));
        assertTrue(fxml.contains("onAction=\"#refresh\""));
        assertTrue(fxml.contains("onAction=\"#logout\""));
        assertTrue(fxml.contains("onAction=\"#openRequestEntry\""));
        assertTrue(fxml.contains("onAction=\"#openOwnRequests\""));
        assertTrue(fxml.contains("text=\"_My loans\""));
        assertTrue(fxml.contains("onAction=\"#openMyLoans\""));
        assertTrue(MemberHomeController.class.getDeclaredMethod("openMyLoans")
                .isAnnotationPresent(FXML.class));
        assertFalse(fxml.contains("Equipment ID"));
        assertFalse(fxml.contains("EquipmentItem"));
        assertFalse(fxml.contains("equipmentId"));
    }

    @Test
    void memberRequestRoutesContainTheirMemberSafeActions() throws IOException {
        String requestEntry = routeText(Route.MEMBER_REQUEST_ENTRY);
        assertTrue(requestEntry.contains("fx:id=\"equipmentTypeComboBox\""));
        assertTrue(requestEntry.contains("onAction=\"#reviewRequest\""));
        assertTrue(requestEntry.contains("onAction=\"#submitRequest\""));
        assertTrue(requestEntry.contains("onAction=\"#goBack\""));
        assertFalse(requestEntry.contains("Equipment ID"));
        assertFalse(requestEntry.contains("equipmentId"));

        String ownRequests = routeText(Route.MEMBER_OWN_REQUESTS);
        assertTrue(ownRequests.contains("fx:id=\"requestsTable\""));
        assertTrue(ownRequests.contains("fx:id=\"approvedQuantityColumn\""));
        assertTrue(ownRequests.contains("onAction=\"#cancelSelectedRequest\""));
        assertTrue(ownRequests.contains("onAction=\"#refreshRequests\""));
        assertFalse(ownRequests.contains("Equipment ID"));
        assertFalse(ownRequests.contains("equipmentId"));
    }

    @Test
    void memberAdministrationRouteContainsSafeAccountControls() throws IOException {
        String fxml = routeText(Route.MEMBER_ADMINISTRATION);

        assertTrue(fxml.contains("fx:id=\"membersTable\""));
        assertTrue(fxml.contains("fx:id=\"editMemberButton\""));
        assertTrue(fxml.contains("onAction=\"#openCreateDialog\""));
        assertTrue(fxml.contains("onAction=\"#openEditDialog\""));
        assertTrue(fxml.indexOf("onAction=\"#openCreateDialog\"")
                < fxml.indexOf("fx:id=\"membersTable\""));
        assertTrue(fxml.contains("<ScrollPane fitToWidth=\"true\""));
        assertTrue(fxml.contains("hbarPolicy=\"NEVER\""));
        assertFalse(fxml.contains("newMemberIdField"));
        assertFalse(fxml.contains("createMemberPane"));
        assertFalse(fxml.contains("passwordHash"));
    }

    @Test
    void inventoryAdministrationRouteContainsExcoInventoryControls() throws IOException {
        String fxml = routeText(Route.INVENTORY_ADMINISTRATION);

        assertTrue(fxml.contains("fx:id=\"typesTable\""));
        assertTrue(fxml.contains("fx:id=\"itemsTable\""));
        assertTrue(fxml.contains("onAction=\"#openCreateTypeDialog\""));
        assertTrue(fxml.contains("onAction=\"#openManageTypeDialog\""));
        assertTrue(fxml.contains("onAction=\"#openAddItemDialog\""));
        assertTrue(fxml.contains("onAction=\"#openManageItemDialog\""));
        assertTrue(fxml.contains("<ScrollPane fitToWidth=\"true\""));
        assertFalse(fxml.contains("labelFor="));
        assertFalse(fxml.contains("Member catalogue"));
    }

    @Test
    void excoRequestQueueRouteContainsPendingQueueControls() throws IOException {
        String fxml = routeText(Route.EXCO_REQUEST_QUEUE);

        assertTrue(fxml.contains("fx:id=\"requestsTable\""));
        assertTrue(fxml.contains("fx:id=\"requestIdColumn\""));
        assertTrue(fxml.contains("fx:id=\"availableQuantityColumn\""));
        assertTrue(fxml.contains("fx:id=\"detailsColumn\""));
        assertTrue(fxml.contains("fx:id=\"emptyQueueLabel\""));
        assertTrue(fxml.contains("fx:id=\"approveRequestButton\""));
        assertTrue(fxml.contains("onAction=\"#approveSelectedRequest\""));
        assertTrue(fxml.contains("onAction=\"#rejectSelectedRequest\""));
        assertTrue(fxml.contains("onAction=\"#refreshRequests\""));
        assertTrue(fxml.contains("<ScrollPane fitToWidth=\"true\""));
        assertFalse(fxml.contains("Equipment ID"));
        assertFalse(fxml.contains("labelFor="));
    }

    @Test
    void excoActiveLoansRouteContainsRequiredLoanFields() throws IOException {
        String fxml = routeText(Route.EXCO_ACTIVE_LOANS);
        assertTrue(fxml.contains("fx:id=\"loansTable\""));
        assertTrue(fxml.contains("fx:id=\"memberColumn\""));
        assertTrue(fxml.contains("fx:id=\"equipmentIdColumn\""));
        assertTrue(fxml.contains("fx:id=\"overdueColumn\""));
        assertTrue(fxml.contains("onAction=\"#refresh\""));
    }

    @Test
    void memberActiveLoansRouteContainsRequiredLoanFieldsAndSafeActions() throws IOException,
            NoSuchMethodException {
        String fxml = routeText(Route.MEMBER_ACTIVE_LOANS);

        assertTrue(fxml.contains("fx:id=\"loansTable\""));
        assertTrue(fxml.contains("fx:id=\"equipmentTypeColumn\""));
        assertTrue(fxml.contains("fx:id=\"equipmentIdColumn\""));
        assertTrue(fxml.contains("fx:id=\"statusColumn\""));
        assertTrue(fxml.contains("fx:id=\"startedAtColumn\""));
        assertTrue(fxml.contains("fx:id=\"endDateColumn\""));
        assertTrue(fxml.contains("fx:id=\"overdueColumn\""));
        assertTrue(fxml.contains("fx:id=\"emptyStateLabel\""));
        assertTrue(fxml.contains("fx:id=\"errorLabel\""));
        assertTrue(fxml.contains("onAction=\"#refresh\""));
        assertTrue(fxml.contains("onAction=\"#goBack\""));
        assertFalse(fxml.contains("cancelSelectedLoan"));
        assertFalse(fxml.contains("reportLost"));
        assertFalse(fxml.contains("reportDamage"));
        assertTrue(MemberActiveLoansController.class.getDeclaredMethod("initialize")
                .isAnnotationPresent(FXML.class));
        assertTrue(MemberActiveLoansController.class.getDeclaredMethod("refresh")
                .isAnnotationPresent(FXML.class));
        assertTrue(MemberActiveLoansController.class.getDeclaredMethod("goBack")
                .isAnnotationPresent(FXML.class));
    }

    @Test
    void excoReportVerificationRouteContainsEvidenceAndResolutionControls() throws IOException {
        String fxml = routeText(Route.EXCO_REPORT_VERIFICATION);

        assertTrue(fxml.contains("fx:id=\"reportsTable\""));
        assertTrue(fxml.contains("fx:id=\"conditionColumn\""));
        assertTrue(fxml.contains("fx:id=\"imageAvailableColumn\""));
        assertTrue(fxml.contains("fx:id=\"evidenceColumn\""));
        assertTrue(fxml.contains("onAction=\"#verifyGood\""));
        assertTrue(fxml.contains("onAction=\"#verifyDamagedAvailable\""));
        assertTrue(fxml.contains("onAction=\"#verifyDamagedUnavailable\""));
        assertTrue(fxml.contains("onAction=\"#confirmLost\""));
        assertTrue(fxml.contains("fx:id=\"viewImageButton\""));
        assertTrue(fxml.contains("onAction=\"#viewDamageImage\""));
        assertTrue(fxml.contains("onAction=\"#refresh\""));
        assertTrue(fxml.contains("fx:id=\"statusLabel\""));
    }

    private static void assertCredentialLabelBindings(Route route, String labelId,
            String fieldId) throws IOException {
        String fxml = routeText(route);

        assertFalse(fxml.contains("labelFor="), route.name());
        assertTrue(fxml.contains("fx:id=\"" + labelId + "\""), labelId);
        assertTrue(fxml.contains("fx:id=\"" + fieldId + "\""), fieldId);
    }

    private static String routeText(Route route) throws IOException {
        return resourceText(route.resourcePath());
    }

    private static String resourceText(String resourcePath) throws IOException {
        URL resource = FxmlResourceTest.class.getResource(resourcePath);
        assertNotNull(resource, resourcePath);
        try (InputStream stream = resource.openStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static DocumentBuilderFactory secureDocumentBuilderFactory()
            throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory;
    }
}
