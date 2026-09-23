package clubstock.ui.navigation;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ControllerFactoryTest {
    @Test
    void createsOnlyExplicitlyRegisteredControllerTypes() {
        ControllerFactory factory = new ControllerFactory();
        factory.register(TestController.class, TestController::new);

        assertInstanceOf(TestController.class, factory.create(TestController.class));
        assertThrows(IllegalStateException.class, () -> factory.create(String.class));
    }

    @Test
    void rejectsDuplicateRegistrationsAndInvalidSupplierResults() {
        ControllerFactory duplicateFactory = new ControllerFactory();
        duplicateFactory.register(TestController.class, TestController::new);
        assertThrows(IllegalStateException.class,
                () -> duplicateFactory.register(TestController.class, TestController::new));

        ControllerFactory invalidFactory = new ControllerFactory();
        invalidFactory.register(TestController.class, () -> null);
        assertThrows(IllegalStateException.class,
                () -> invalidFactory.create(TestController.class));
    }

    private static final class TestController {
    }
}
