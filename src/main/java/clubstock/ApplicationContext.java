package clubstock;

import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.AuthenticationService;
import clubstock.application.auth.Pbkdf2PasswordHasher;
import clubstock.application.auth.SessionManager;
import clubstock.application.catalog.MemberCatalogService;
import clubstock.application.inventory.AvailabilityPolicy;
import clubstock.application.inventory.EquipmentItemAvailabilityPolicy;
import clubstock.application.inventory.InventoryService;
import clubstock.application.loan.LoanQueryService;
import clubstock.application.member.MemberAccountService;
import clubstock.application.port.DamageEvidenceStore;
import clubstock.application.port.TransactionManager;
import clubstock.application.request.ApprovalService;
import clubstock.application.request.ExcoRequestService;
import clubstock.application.request.MemberRequestService;
import clubstock.application.verification.VerificationService;
import clubstock.infrastructure.file.FileDamageEvidenceStore;
import clubstock.infrastructure.id.UuidIdGenerator;
import clubstock.infrastructure.sqlite.SqliteDatabase;
import clubstock.ui.auth.AuthenticationGateway;
import clubstock.ui.auth.AuthenticationGatewayAdapter;

/**
 * Holds dependencies constructed once for one ClubStock process.
 */
public final class ApplicationContext {
    private static final String DATA_DIRECTORY_PROPERTY = "clubstock.dataDir";
    private static final String DATABASE_FILENAME = "clubstock.db";
    private static final String DAMAGE_EVIDENCE_DIRECTORY = "damage-evidence";
    private final Path dataDirectory;
    private final Clock clock;
    private final ZoneId zoneId;
    private final TransactionManager transactionManager;
    private final SessionManager sessionManager;
    private final AuthenticationGateway authentication;
    private final MemberAccountService memberAccountService;
    private final AvailabilityPolicy availabilityPolicy;
    private final InventoryService inventoryService;
    private final MemberCatalogService memberCatalogService;
    private final ExcoRequestService excoRequestService;
    private final MemberRequestService memberRequestService;
    private final ApprovalService approvalService;
    private final LoanQueryService loanQueryService;
    private final VerificationService verificationService;
    private final DamageEvidenceStore damageEvidenceStore;

    private ApplicationContext(Path dataDirectory, Clock clock, ZoneId zoneId,
            TransactionManager transactionManager, SessionManager sessionManager,
            AuthenticationGateway authentication, MemberAccountService memberAccountService,
            AvailabilityPolicy availabilityPolicy, InventoryService inventoryService,
            MemberCatalogService memberCatalogService, ExcoRequestService excoRequestService,
            MemberRequestService memberRequestService, ApprovalService approvalService,
            LoanQueryService loanQueryService,
            VerificationService verificationService, DamageEvidenceStore damageEvidenceStore) {
        this.dataDirectory = dataDirectory;
        this.clock = clock;
        this.zoneId = zoneId;
        this.transactionManager = transactionManager;
        this.sessionManager = sessionManager;
        this.authentication = authentication;
        this.memberAccountService = memberAccountService;
        this.availabilityPolicy = availabilityPolicy;
        this.inventoryService = inventoryService;
        this.memberCatalogService = memberCatalogService;
        this.excoRequestService = excoRequestService;
        this.memberRequestService = memberRequestService;
        this.approvalService = approvalService;
        this.loanQueryService = loanQueryService;
        this.verificationService = verificationService;
        this.damageEvidenceStore = damageEvidenceStore;
    }

    /**
     * Creates the production context and initializes persistent storage.
     *
     * @return Fully initialized application context.
     */
    public static ApplicationContext createProduction() {
        return create(resolveDataDirectory());
    }

    /**
     * Creates an isolated application context with real persistence and authentication services.
     *
     * @param dataDirectory Disposable or otherwise controlled data directory.
     * @return Fully initialized application context.
     */
    public static ApplicationContext create(Path dataDirectory) {
        if (dataDirectory == null) {
            throw new IllegalArgumentException("Application data directory cannot be null.");
        }

        Path normalizedDirectory = dataDirectory.toAbsolutePath().normalize();
        SqliteDatabase database = initializeDatabase(normalizedDirectory);
        SessionManager sessionManager = new SessionManager();
        Pbkdf2PasswordHasher passwordHasher = new Pbkdf2PasswordHasher();
        AuthenticationService authenticationService = new AuthenticationService(database,
                passwordHasher, sessionManager);
        AuthenticationGateway authentication = new AuthenticationGatewayAdapter(
                authenticationService);
        Clock clock = Clock.systemDefaultZone();
        AvailabilityPolicy availabilityPolicy = new EquipmentItemAvailabilityPolicy();
        MemberAccountService memberAccountService = new MemberAccountService(database, sessionManager,
                passwordHasher, clock);
        InventoryService inventoryService = new InventoryService(database, sessionManager,
                new UuidIdGenerator(), availabilityPolicy, clock);
        MemberCatalogService memberCatalogService = new MemberCatalogService(database,
                sessionManager, availabilityPolicy);
        MemberRequestService memberRequestService = new MemberRequestService(database,
                sessionManager, availabilityPolicy, new UuidIdGenerator(), clock);
        ExcoRequestService excoRequestService = new ExcoRequestService(database, sessionManager,
                availabilityPolicy);
        ApprovalService approvalService = new ApprovalService(database, sessionManager,
                availabilityPolicy, new UuidIdGenerator(), clock);
        LoanQueryService loanQueryService = new LoanQueryService(database, sessionManager, clock,
                ZoneId.systemDefault());
        DamageEvidenceStore damageEvidenceStore = new FileDamageEvidenceStore(
                normalizedDirectory.resolve(DAMAGE_EVIDENCE_DIRECTORY));
        VerificationService verificationService = new VerificationService(database, sessionManager,
                damageEvidenceStore);
        return new ApplicationContext(normalizedDirectory, clock, ZoneId.systemDefault(), database,
                sessionManager, authentication, memberAccountService, availabilityPolicy,
                inventoryService, memberCatalogService, excoRequestService, memberRequestService,
                approvalService, loanQueryService,
                verificationService, damageEvidenceStore);
    }

    /**
     * Returns the managed data directory.
     *
     * @return Absolute normalized directory.
     */
    public Path dataDirectory() {
        return dataDirectory;
    }

    /**
     * Returns the process clock captured during composition.
     *
     * @return Process clock.
     */
    public Clock clock() {
        return clock;
    }

    /**
     * Returns the process time zone captured during composition.
     *
     * @return Process time zone.
     */
    public ZoneId zoneId() {
        return zoneId;
    }

    /**
     * Returns the authentication integration boundary.
     *
     * @return Authentication boundary.
     */
    public AuthenticationGateway authentication() {
        return authentication;
    }

    /**
     * Returns the transaction manager shared by application services.
     *
     * @return Shared transaction manager.
     */
    public TransactionManager transactionManager() {
        return transactionManager;
    }

    /**
     * Returns the session manager shared by application services and authentication.
     *
     * @return Shared in-memory session manager.
     */
    public SessionManager sessionManager() {
        return sessionManager;
    }

    /**
     * Returns the Exco Member-account administration service.
     *
     * @return Shared Member-account administration service.
     */
    public MemberAccountService memberAccountService() {
        return memberAccountService;
    }

    /**
     * Returns the shared transactional availability policy for cross-role catalogue use.
     *
     * @return Shared availability policy.
     */
    public AvailabilityPolicy availabilityPolicy() {
        return availabilityPolicy;
    }

    /**
     * Returns the Exco inventory administration service.
     *
     * @return Shared inventory administration service.
     */
    public InventoryService inventoryService() {
        return inventoryService;
    }

    /**
     * Returns the Member catalogue query service.
     *
     * @return Context-owned catalogue service.
     */
    public MemberCatalogService memberCatalogService() {
        return memberCatalogService;
    }

    /**
     * Returns the Exco pending-request query and manual-rejection service.
     *
     * @return Context-owned Exco request service.
     */
    public ExcoRequestService excoRequestService() {
        return excoRequestService;
    }

    /**
     * Returns the Member request submission, query, and cancellation service.
     *
     * @return Context-owned Member request service.
     */
    public MemberRequestService memberRequestService() {
        return memberRequestService;
    }

    /** Returns the Exco request-approval and item-allocation service. */
    public ApprovalService approvalService() {
        return approvalService;
    }

    public LoanQueryService loanQueryService() { return loanQueryService; }
    public VerificationService verificationService() { return verificationService; }

    /** Returns the context-owned managed damage-evidence store. */
    public DamageEvidenceStore damageEvidenceStore() { return damageEvidenceStore; }

    private static Path resolveDataDirectory() {
        String configuredDirectory = System.getProperty(DATA_DIRECTORY_PROPERTY);
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            return Path.of(configuredDirectory).toAbsolutePath().normalize();
        }

        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            throw new ApplicationException(ApplicationErrorCode.PERSISTENCE_FAILURE,
                    "ClubStock storage could not be located.", null);
        }
        return Path.of(userHome, ".clubstock").toAbsolutePath().normalize();
    }

    private static SqliteDatabase initializeDatabase(Path dataDirectory) {
        SqliteDatabase database = new SqliteDatabase(dataDirectory.resolve(DATABASE_FILENAME));
        database.initialize();
        return database;
    }
}
