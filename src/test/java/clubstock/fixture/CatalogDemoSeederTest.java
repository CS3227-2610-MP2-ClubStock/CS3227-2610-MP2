package clubstock.fixture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.ApplicationContext;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;

class CatalogDemoSeederTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void seed_createsUsableAccount_andRepeatLeavesItUnchanged() {
        ApplicationContext context = ApplicationContext.create(temporaryDirectory);

        assertTrue(CatalogDemoSeeder.seed(context));
        Member firstSeed = readDemoMember(context);
        assertTrue(firstSeed.isActive());
        assertEquals("Demo Member", firstSeed.name());
        assertNotNull(firstSeed.passwordHash());

        context.authentication().authenticateMember("demo-member", "demo-password".toCharArray());
        assertTrue(context.authentication().currentPrincipal().isPresent());
        context.authentication().logout();

        assertFalse(CatalogDemoSeeder.seed(context));
        Member repeatedSeed = readDemoMember(context);
        assertEquals(firstSeed.name(), repeatedSeed.name());
        assertEquals(firstSeed.passwordHash(), repeatedSeed.passwordHash());
        assertTrue(repeatedSeed.isActive());
    }

    /**
     * Returns the demo account from the temporary database.
     *
     * @param context Isolated application context.
     * @return Persisted demo Member.
     */
    private Member readDemoMember(ApplicationContext context) {
        return context.transactionManager().read(unitOfWork -> unitOfWork.members()
                .findById(new MemberId("demo-member")).orElseThrow());
    }
}
