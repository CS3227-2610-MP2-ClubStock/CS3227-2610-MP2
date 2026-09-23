package clubstock.ui.navigation;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Creates dependency-injected FXML controllers from explicit registrations.
 */
public final class ControllerFactory {
    private final Map<Class<?>, Supplier<?>> registrations = new HashMap<>();

    /**
     * Creates an empty controller registry.
     */
    public ControllerFactory() {
    }

    /**
     * Registers one controller supplier.
     *
     * @param controllerType FXML controller type.
     * @param supplier Supplier of a new controller.
     * @param <T> Controller type.
     */
    public <T> void register(Class<T> controllerType, Supplier<? extends T> supplier) {
        if (controllerType == null || supplier == null) {
            throw new IllegalArgumentException("Controller registration cannot be null.");
        }
        if (registrations.putIfAbsent(controllerType, supplier) != null) {
            throw new IllegalStateException(
                    "Controller is already registered: " + controllerType.getName());
        }
    }

    /**
     * Creates a registered controller.
     *
     * @param controllerType Requested type.
     * @return New controller.
     */
    public Object create(Class<?> controllerType) {
        if (controllerType == null) {
            throw new IllegalArgumentException("Controller type cannot be null.");
        }
        Supplier<?> supplier = registrations.get(controllerType);
        if (supplier == null) {
            throw new IllegalStateException(
                    "No controller is registered for " + controllerType.getName());
        }
        Object controller = supplier.get();
        if (controller == null || !controllerType.isInstance(controller)) {
            throw new IllegalStateException(
                    "Controller supplier returned an invalid instance for "
                            + controllerType.getName());
        }
        return controller;
    }
}
