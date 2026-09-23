package clubstock.application.port;

import java.util.List;
import java.util.Optional;

import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;

/**
 * Persists Member accounts and exposes foundational reference checks.
 */
public interface MemberRepository {
    /**
     * Finds a Member by identity.
     *
     * @param memberId Member identity.
     * @return Matching Member, when present.
     */
    Optional<Member> findById(MemberId memberId);
    /**
     * Returns all Members ordered by stable identity.
     *
     * @return All Members.
     */
    List<Member> findAll();
    /**
     * Inserts a Member.
     *
     * @param member Member to insert.
     */
    void insert(Member member);
    /**
     * Updates a Member.
     *
     * @param member Member to update.
     */
    void update(Member member);
}
