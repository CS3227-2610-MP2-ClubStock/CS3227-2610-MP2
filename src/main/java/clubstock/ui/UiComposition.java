package clubstock.ui;

import clubstock.application.catalog.MemberCatalogService;
import clubstock.application.inventory.InventoryService;
import clubstock.application.member.MemberAccountService;
import clubstock.ui.auth.AuthenticationGateway;
import clubstock.ui.auth.ExcoAuthenticationAction;
import clubstock.ui.auth.LogoutAction;
import clubstock.ui.auth.MemberAuthenticationAction;
import clubstock.ui.auth.RoleSelectionAction;
import clubstock.ui.controller.ExcoHomeController;
import clubstock.ui.controller.ExcoLoginController;
import clubstock.ui.controller.ExcoSetupController;
import clubstock.ui.controller.InventoryAdministrationController;
import clubstock.ui.controller.MemberAdministrationController;
import clubstock.ui.controller.MemberHomeController;
import clubstock.ui.controller.MemberLoginController;
import clubstock.ui.controller.RoleSelectionController;
import clubstock.ui.navigation.ControllerFactory;
import clubstock.ui.navigation.FxmlViewLoader;
import clubstock.ui.navigation.JavaFxErrorPresenter;
import clubstock.ui.navigation.JavaFxNavigator;
import javafx.stage.Stage;

/**
 * Wires the JavaFX navigation and controller graph.
 */
public final class UiComposition {
    private UiComposition() {
    }

    /**
     * Creates the primary navigator and registers all current controllers.
     *
     * @param stage Primary stage.
     * @param authentication Authentication/session boundary.
     * @param memberAccountService Exco Member-account administration service.
     * @param inventoryService Exco inventory administration service.
     * @param memberCatalogService Context-owned Member catalogue query.
     * @return Configured navigator.
     */
    public static JavaFxNavigator createNavigator(Stage stage,
            AuthenticationGateway authentication, MemberAccountService memberAccountService,
            InventoryService inventoryService, MemberCatalogService memberCatalogService) {
        if (stage == null || authentication == null || memberAccountService == null
                || inventoryService == null || memberCatalogService == null) {
            throw new IllegalArgumentException("UI composition dependencies cannot be null.");
        }

        ControllerFactory controllerFactory = new ControllerFactory();
        FxmlViewLoader viewLoader = new FxmlViewLoader(controllerFactory);
        JavaFxNavigator navigator = new JavaFxNavigator(stage, authentication, viewLoader,
                new JavaFxErrorPresenter(stage));
        ExcoAuthenticationAction excoAction =
                new ExcoAuthenticationAction(authentication, navigator);
        LogoutAction logoutAction = new LogoutAction(authentication, navigator);

        controllerFactory.register(RoleSelectionController.class,
                () -> new RoleSelectionController(
                        new RoleSelectionAction(authentication, navigator)));
        controllerFactory.register(ExcoSetupController.class,
                () -> new ExcoSetupController(excoAction, navigator));
        controllerFactory.register(ExcoLoginController.class,
                () -> new ExcoLoginController(excoAction, navigator));
        MemberAuthenticationAction memberAction =
                new MemberAuthenticationAction(authentication, navigator);
        controllerFactory.register(MemberLoginController.class,
                () -> new MemberLoginController(memberAction, navigator));
        controllerFactory.register(ExcoHomeController.class,
                () -> new ExcoHomeController(logoutAction, navigator));
        controllerFactory.register(MemberAdministrationController.class,
                () -> new MemberAdministrationController(memberAccountService, navigator));
        controllerFactory.register(InventoryAdministrationController.class,
                () -> new InventoryAdministrationController(inventoryService, navigator));
        controllerFactory.register(MemberHomeController.class,
                () -> new MemberHomeController(logoutAction, memberCatalogService));
        return navigator;
    }
}
