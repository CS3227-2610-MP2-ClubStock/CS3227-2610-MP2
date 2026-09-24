package clubstock.fixture;

import java.nio.file.Path;

import clubstock.ApplicationContext;
import clubstock.application.auth.Pbkdf2PasswordHasher;
import clubstock.application.port.UnitOfWork;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.account.PasswordHash;

/**
 * Creates an isolated Member account for exercising the development catalogue.
 */
public final class CatalogDemoSeeder {
    private static final Path DEMO_DATA_DIRECTORY = Path.of("build", "clubstock-demo");
    private static final MemberId DEMO_MEMBER_ID = new MemberId("demo-member");
    private static final String DEMO_PASSWORD = "demo-password";

    private CatalogDemoSeeder() {
    }

    /**
     * Seeds the disposable catalogue database without printing account credentials.
     *
     * @param arguments Command-line arguments, which are ignored.
     */
    public static void main(String[] arguments) {
        ApplicationContext context = ApplicationContext.create(DEMO_DATA_DIRECTORY);
        seed(context);
        System.out.println("The temporary catalogue Member account is ready.");
    }

    /**
     * Inserts the demo Member only when its fixed identity is absent.
     *
     * @param context Application context that owns the isolated database.
     * @return True if the Member was inserted; false if it already existed.
     */
    static boolean seed(ApplicationContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Application context cannot be null.");
        }

        return context.transactionManager().write(unitOfWork -> insertIfAbsent(unitOfWork));
    }

    /**
     * Creates the active demo Member inside the caller's write transaction when it is absent.
     *
     * @param unitOfWork Active database unit of work.
     * @return True if the Member was inserted; false if it already existed.
     */
    private static boolean insertIfAbsent(UnitOfWork unitOfWork) {
        if (unitOfWork.members().findById(DEMO_MEMBER_ID).isPresent()) {
            return false;
        }

        PasswordHash passwordHash = new Pbkdf2PasswordHasher().hash(DEMO_PASSWORD.toCharArray());
        Member member = Member.create(DEMO_MEMBER_ID, "Demo Member", passwordHash);
        unitOfWork.members().insert(member);
        return true;
    }
}
