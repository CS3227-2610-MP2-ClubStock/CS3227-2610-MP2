package clubstock.ui.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AuthenticatedPrincipalTest {
    @Test
    void createsRoleSpecificPrincipals() {
        AuthenticatedPrincipal exco = AuthenticatedPrincipal.exco();
        AuthenticatedPrincipal member = AuthenticatedPrincipal.member("M-001");

        assertEquals(UserRole.EXCO, exco.role());
        assertTrue(exco.optionalMemberId().isEmpty());
        assertEquals(UserRole.MEMBER, member.role());
        assertEquals("M-001", member.optionalMemberId().orElseThrow());
    }

    @Test
    void rejectsRoleIdentityMismatch() {
        assertThrows(IllegalArgumentException.class,
                () -> new AuthenticatedPrincipal(UserRole.MEMBER, null));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthenticatedPrincipal(UserRole.MEMBER, "  "));
        assertThrows(IllegalArgumentException.class,
                () -> new AuthenticatedPrincipal(UserRole.EXCO, "M-001"));
    }
}
