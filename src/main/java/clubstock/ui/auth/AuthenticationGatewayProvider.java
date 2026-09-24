package clubstock.ui.auth;

import clubstock.application.port.TransactionManager;

/**
 * Service-provider hook used to connect the shell to the shared authentication implementation.
 */
public interface AuthenticationGatewayProvider {
    /**
     * Creates the UI adapter over the shared authentication/session services.
     *
     * @param transactionManager Shared transaction boundary.
     * @return Authentication adapter.
     */
    AuthenticationGateway create(TransactionManager transactionManager);
}
