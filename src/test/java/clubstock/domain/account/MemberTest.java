package clubstock.domain.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void create_validAccount_trimsNameAndPreservesIdentity() {
        MemberId memberId = new MemberId("member-1");
        PasswordHash passwordHash = new PasswordHash("hash-1");

        Member member = Member.create(memberId, "  Member One  ", passwordHash);

        assertSame(memberId, member.memberId());
        assertEquals("Member One", member.name());
        assertSame(passwordHash, member.passwordHash());
    }

    @Test
    void create_unicodePaddedName_stripsSurroundingWhitespace() {
        Member member = Member.create(new MemberId("member-1"), "\u2003Member One\u2003",
                new PasswordHash("hash-1"));

        assertEquals("Member One", member.name());
    }

    @Test
    void create_invalidValues_rejectBeforeCreation() {
        MemberId memberId = new MemberId("member-1");
        PasswordHash passwordHash = new PasswordHash("hash-1");

        assertThrows(IllegalArgumentException.class,
                () -> Member.create(memberId, " ", passwordHash));
        assertThrows(IllegalArgumentException.class,
                () -> Member.create(null, "Member One", passwordHash));
        assertThrows(IllegalArgumentException.class,
                () -> Member.create(memberId, "Member One", null));
    }

    @Test
    void updateName_validName_changesOnlyName() {
        MemberId memberId = new MemberId("member-1");
        PasswordHash passwordHash = new PasswordHash("hash-1");
        Member member = Member.create(memberId, "Member One", passwordHash);

        member.updateName("  Updated Member  ");

        assertEquals("Updated Member", member.name());
        assertSame(memberId, member.memberId());
        assertSame(passwordHash, member.passwordHash());
    }

    @Test
    void updateName_invalidName_preservesExistingAccount() {
        Member member = Member.create(new MemberId("member-1"), "Member One",
                new PasswordHash("hash-1"));

        assertThrows(IllegalArgumentException.class, () -> member.updateName("  "));
        assertEquals("Member One", member.name());
    }

    @Test
    void replacePasswordHash_validHash_changesOnlyCredential() {
        MemberId memberId = new MemberId("member-1");
        PasswordHash originalHash = new PasswordHash("hash-1");
        PasswordHash replacementHash = new PasswordHash("hash-2");
        Member member = Member.create(memberId, "Member One", originalHash);

        member.replacePasswordHash(replacementHash);

        assertSame(replacementHash, member.passwordHash());
        assertSame(memberId, member.memberId());
        assertEquals("Member One", member.name());
    }

    @Test
    void replacePasswordHash_nullHash_preservesExistingAccount() {
        PasswordHash originalHash = new PasswordHash("hash-1");
        Member member = Member.create(new MemberId("member-1"), "Member One", originalHash);

        assertThrows(IllegalArgumentException.class, () -> member.replacePasswordHash(null));
        assertSame(originalHash, member.passwordHash());
    }

    @Test
    void restore_inactiveAccount_preservesRemovalState() {
        Instant removedAt = Instant.parse("2026-09-22T10:15:30Z");

        Member member = Member.restore(new MemberId("member-1"), "Member One",
                new PasswordHash("hash-1"), false, removedAt);

        assertEquals(false, member.isActive());
        assertEquals(removedAt, member.removedAt().orElseThrow());
    }

    @Test
    void deactivate_activeAccount_recordsInstantAndRejectsRepeat() {
        Member member = Member.create(new MemberId("member-1"), "Member One",
                new PasswordHash("hash-1"));
        Instant removedAt = Instant.parse("2026-09-22T10:15:30Z");

        member.deactivate(removedAt);

        assertThrows(IllegalStateException.class, () -> member.deactivate(removedAt.plusSeconds(1)));
        assertEquals(removedAt, member.removedAt().orElseThrow());
    }

    @Test
    void equality_sameIdentityDifferentDetails_comparesByMemberId() {
        MemberId memberId = new MemberId("member-1");
        Member first = Member.create(memberId, "First Name", new PasswordHash("hash-1"));
        Member second = Member.create(new MemberId("member-1"), "Second Name",
                new PasswordHash("hash-2"));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void equality_differentIdentity_doesNotCompareEqual() {
        Member first = Member.create(new MemberId("member-1"), "Member One",
                new PasswordHash("hash-1"));
        Member second = Member.create(new MemberId("member-2"), "Member One",
                new PasswordHash("hash-1"));

        assertNotEquals(first, second);
    }
}
