package clubstock.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import clubstock.ui.controller.ExcoHomeController;
import clubstock.ui.controller.ExcoLoginController;
import clubstock.ui.controller.ExcoSetupController;
import clubstock.ui.controller.MemberHomeController;
import clubstock.ui.controller.MemberLoginController;
import clubstock.ui.controller.RoleSelectionController;
import clubstock.ui.navigation.Route;

class FxmlResourceTest {
    private static final String FXML_NAMESPACE = "http://javafx.com/fxml/1";
    private static final String STYLESHEET = "/clubstock/ui/clubstock.css";
    private static final Map<Route, Class<?>> CONTROLLERS = Map.of(
            Route.ROLE_SELECTION, RoleSelectionController.class,
            Route.EXCO_SETUP, ExcoSetupController.class,
            Route.EXCO_LOGIN, ExcoLoginController.class,
            Route.MEMBER_LOGIN, MemberLoginController.class,
            Route.EXCO_HOME, ExcoHomeController.class,
            Route.MEMBER_HOME, MemberHomeController.class);

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
    void sharedStylesheetIncludesVisibleFocusAndTextualErrorStyles() throws IOException {
        URL resource = FxmlResourceTest.class.getResource(STYLESHEET);
        assertNotNull(resource);
        String css;
        try (InputStream stream = resource.openStream()) {
            css = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

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
        URL resource = FxmlResourceTest.class.getResource(Route.MEMBER_LOGIN.resourcePath());
        assertNotNull(resource);
        String fxml;
        try (InputStream stream = resource.openStream()) {
            fxml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(fxml.contains("fx:id=\"memberIdField\""));
        assertTrue(fxml.contains("fx:id=\"passwordField\""));
        assertTrue(fxml.contains("onAction=\"#login\""));
        assertTrue(fxml.contains("onAction=\"#goBack\""));
        assertFalse(fxml.contains("not available in this build yet"));
    }

    @Test
    void memberHomeRouteContainsSafeCatalogueAndRefreshBindings() throws IOException {
        URL resource = FxmlResourceTest.class.getResource(Route.MEMBER_HOME.resourcePath());
        assertNotNull(resource);
        String fxml;
        try (InputStream stream = resource.openStream()) {
            fxml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(fxml.contains("fx:id=\"catalogListView\""));
        assertTrue(fxml.contains("Offered equipment types and available quantities"));
        assertTrue(fxml.contains("No equipment is currently offered"));
        assertTrue(fxml.contains("fx:id=\"errorLabel\""));
        assertTrue(fxml.contains("onAction=\"#refresh\""));
        assertTrue(fxml.contains("onAction=\"#logout\""));
        assertFalse(fxml.contains("Equipment ID"));
        assertFalse(fxml.contains("EquipmentItem"));
        assertFalse(fxml.contains("equipmentId"));
    }

    private static void assertCredentialLabelBindings(Route route, String labelId,
            String fieldId) throws IOException {
        URL resource = FxmlResourceTest.class.getResource(route.resourcePath());
        assertNotNull(resource, route.resourcePath());
        String fxml;
        try (InputStream stream = resource.openStream()) {
            fxml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertFalse(fxml.contains("labelFor="), route.name());
        assertTrue(fxml.contains("fx:id=\"" + labelId + "\""), labelId);
        assertTrue(fxml.contains("fx:id=\"" + fieldId + "\""), fieldId);
    }

    private static void assertStyleClasses(Route route, List<String> expectedClasses)
            throws IOException, ParserConfigurationException, SAXException {
        URL resource = FxmlResourceTest.class.getResource(route.resourcePath());
        assertNotNull(resource, route.resourcePath());
        try (InputStream stream = resource.openStream()) {
            Document document = secureDocumentBuilderFactory()
                    .newDocumentBuilder().parse(stream);
            String classes = document.getDocumentElement().getAttribute("styleClass");
            assertEquals(expectedClasses, List.of(classes.split(",\\s*")), route.name());
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
