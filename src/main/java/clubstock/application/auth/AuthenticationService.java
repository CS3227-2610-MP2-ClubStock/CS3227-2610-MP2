package clubstock.application.auth;

import java.util.Arrays;
import java.util.Optional;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.port.TransactionManager;
import clubstock.domain.account.ExcoAccount;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.account.PasswordHash;

/**
 * Authenticates Exco and Member accounts and establishes the current session.
 */
public final class AuthenticationService {
    private static final String AUTHENTICATION_FAILURE_MESSAGE = "Credentials were not accepted.";
    private final TransactionManager transactionManager;
    private final PasswordHasher passwordHasher;
    private final SessionManager sessionManager;

    /**
     * Creates the authentication service.
     *
     * @param transactionManager Account transaction boundary.
     * @param passwordHasher Password hashing and verification service.
     * @param sessionManager Shared in-memory session manager.
     */
    public AuthenticationService(TransactionManager transactionManager,
            PasswordHasher passwordHasher, SessionManager sessionManager) {
        if (transactionManager == null || passwordHasher == null || sessionManager == null) {
            throw new IllegalArgumentException("Authentication dependencies cannot be null.");
        }
        this.transactionManager = transactionManager;
        this.passwordHasher = passwordHasher;
        this.sessionManager = sessionManager;
    }

    /**
     * Returns whether the singleton Exco account requires initial password setup.
     *
     * @return True when Exco has no stored password hash.
     */
    public boolean requiresExcoSetup() {
        return transactionManager.read(unitOfWork ->
                unitOfWork.excoAccounts().get().requiresPasswordSetup());
    }

    /**
     * Completes one-time Exco password setup and starts the Exco session.
     *
     * @param password New password characters.
     * @param confirmation Password confirmation characters.
     */
    public void completeExcoSetup(char[] password, char[] confirmation) {
        try {
            requireCredentialArrays(password, confirmation);
            requireSignedOut();
            if (!Arrays.equals(password, confirmation)) {
                throw new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED,
                        "Password confirmation does not match.", null);
            }

            PasswordHash passwordHash = passwordHasher.hash(password);
            transactionManager.write(unitOfWork -> {
                ExcoAccount account = unitOfWork.excoAccounts().get();
                if (!account.requiresPasswordSetup()) {
                    throw new ApplicationException(ApplicationErrorCode.CONFLICT,
                            "Exco password setup has already been completed.", null);
                }
                account.completeInitialPasswordSetup(passwordHash);
                unitOfWork.excoAccounts().save(account);
                return null;
            });
            sessionManager.establish(Principal.exco());
        } finally {
            clear(password);
            clear(confirmation);
        }
    }

    /**
     * Authenticates Exco and starts the Exco session.
     *
     * @param password Supplied password characters.
     */
    public void authenticateExco(char[] password) {
        requireCredentialArrays(password);
        try {
            requireSignedOut();
            Optional<PasswordHash> passwordHash = transactionManager.read(unitOfWork ->
                    unitOfWork.excoAccounts().get().passwordHash());
            if (passwordHash.isEmpty()
                    || !passwordHasher.matches(password, passwordHash.orElseThrow())) {
                throw authenticationFailure();
            }
            sessionManager.establish(Principal.exco());
        } finally {
            clear(password);
        }
    }

    /**
     * Authenticates an active Member account and starts its session.
     *
     * @param memberId Supplied Member identity.
     * @param password Supplied password characters.
     */
    public void authenticateMember(String memberId, char[] password) {
        requireCredentialArrays(password);
        try {
            requireSignedOut();
            MemberId validatedId;
            try {
                validatedId = new MemberId(memberId);
            } catch (IllegalArgumentException exception) {
                throw authenticationFailure();
            }

            Optional<Member> member = transactionManager.read(unitOfWork ->
                    unitOfWork.members().findById(validatedId));
            if (member.isEmpty() || !member.orElseThrow().isActive()
                    || !passwordHasher.matches(password, member.orElseThrow().passwordHash())) {
                throw authenticationFailure();
            }
            sessionManager.establish(Principal.member(validatedId));
        } finally {
            clear(password);
        }
    }

    /**
     * Returns the current authenticated principal.
     *
     * @return Current principal, if present.
     */
    public Optional<Principal> currentPrincipal() {
        return sessionManager.currentPrincipal();
    }

    /**
     * Clears the current authenticated session.
     */
    public void logout() {
        sessionManager.logout();
    }

    /**
     * Rejects authentication while another session is active.
     */
    private void requireSignedOut() {
        if (sessionManager.currentPrincipal().isPresent()) {
            throw new ApplicationException(ApplicationErrorCode.CONFLICT,
                    "Log out before starting another session.", null);
        }
    }

    /**
     * Creates the same safe failure for every rejected credential attempt.
     *
     * @return Generic authentication failure.
     */
    private static ApplicationException authenticationFailure() {
        return new ApplicationException(ApplicationErrorCode.AUTHENTICATION_FAILED,
                AUTHENTICATION_FAILURE_MESSAGE, null);
    }

    /**
     * Validates that every supplied credential array is present.
     *
     * @param credentials Credential arrays.
     */
    private static void requireCredentialArrays(char[]... credentials) {
        for (char[] credential : credentials) {
            if (credential == null) {
                throw new IllegalArgumentException("Credential arrays cannot be null.");
            }
        }
    }

    /**
     * Clears a credential array when present.
     *
     * @param credential Credential characters.
     */
    private static void clear(char[] credential) {
        if (credential != null) {
            Arrays.fill(credential, '\0');
        }
    }
}
