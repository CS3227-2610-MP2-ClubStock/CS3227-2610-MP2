package clubstock.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import clubstock.ui.auth.ExcoAuthenticationAction;
import clubstock.ui.auth.UnavailableAuthenticationGateway;
import clubstock.ui.controller.ExcoHomeController;
import clubstock.ui.controller.ExcoLoginController;
import clubstock.ui.controller.ExcoSetupController;
import clubstock.ui.controller.MemberHomeController;
import clubstock.ui.controller.MemberLoginController;
import clubstock.ui.controller.RoleSelectionController;
import clubstock.ui.navigation.NavigationService;
import clubstock.ui.navigation.Route;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

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

    @BeforeAll
    static void startJavaFx() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException exception) {
            // JavaFX was already started by another test class.
        }
    }

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
    }

    @Test
    void excoCredentialLabelsAreBoundAfterFxmlInjection() throws Exception {
        assertCredentialLabelBinding(Route.EXCO_LOGIN, "passwordLabel", "passwordField");
        assertCredentialLabelBinding(Route.EXCO_SETUP, "passwordLabel", "passwordField");
        assertCredentialLabelBinding(Route.EXCO_SETUP, "confirmationLabel", "confirmationField");
    }

    private static void assertCredentialLabelBinding(Route route, String labelId,
            String fieldId) throws Exception {
        runOnJavaFxThread(() -> {
            URL resource = FxmlResourceTest.class.getResource(route.resourcePath());
            assertNotNull(resource, route.resourcePath());
            FXMLLoader loader = new FXMLLoader(resource);
            loader.setControllerFactory(FxmlResourceTest::createController);
            loader.load();
            Label label = (Label) loader.getNamespace().get(labelId);
            PasswordField field = (PasswordField) loader.getNamespace().get(fieldId);
            assertNotNull(label, labelId);
            assertNotNull(field, fieldId);
            assertSame(field, label.getLabelFor(), labelId);
        });
    }

    private static Object createController(Class<?> controllerType) {
        NavigationService navigation = route -> {
        };
        ExcoAuthenticationAction action = new ExcoAuthenticationAction(
                new UnavailableAuthenticationGateway(), navigation);
        if (controllerType == ExcoLoginController.class) {
            return new ExcoLoginController(action, navigation);
        }
        if (controllerType == ExcoSetupController.class) {
            return new ExcoSetupController(action, navigation);
        }
        throw new IllegalArgumentException(controllerType.getName());
    }

    private static void runOnJavaFxThread(CheckedRunnable action) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                completed.countDown();
            }
        });
        assertTrue(completed.await(10, TimeUnit.SECONDS), "JavaFX operation timed out");
        Throwable throwable = failure.get();
        if (throwable instanceof Exception exception) {
            throw exception;
        }
        if (throwable != null) {
            throw new AssertionError(throwable);
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
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
