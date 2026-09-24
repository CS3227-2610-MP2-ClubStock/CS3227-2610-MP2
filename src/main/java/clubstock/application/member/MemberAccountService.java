package clubstock.application.member;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.PasswordHasher;
import clubstock.application.auth.SessionManager;
import clubstock.application.port.TransactionManager;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.account.PasswordHash;

/**
 * Performs Exco-authorized administration of Member accounts.
 */
public final class MemberAccountService {
    private final TransactionManager transactionManager;
    private final SessionManager sessionManager;
    private final PasswordHasher passwordHasher;
    private final Clock clock;

    /**
     * Creates the Member administration service.
     *
     * @param transactionManager Shared transaction boundary.
     * @param sessionManager Authenticated principal source.
     * @param passwordHasher Password hashing boundary.
     * @param clock Clock used to record deactivation time.
     */
    public MemberAccountService(TransactionManager transactionManager, SessionManager sessionManager,
            PasswordHasher passwordHasher, Clock clock) {
        if (transactionManager == null || sessionManager == null || passwordHasher == null
                || clock == null) {
            throw new IllegalArgumentException("Member administration dependencies cannot be null.");
        }
        this.transactionManager = transactionManager;
        this.sessionManager = sessionManager;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
    }

    /**
     * Lists every Member account using safe, non-credential summaries.
     *
     * @return Member summaries in repository identity order.
     */
    public List<MemberSummary> listMembers() {
        sessionManager.requireExco();
        return transactionManager.read(unitOfWork -> unitOfWork.members().findAll().stream()
                .map(member -> new MemberSummary(member.memberId().value(), member.name(),
                        member.isActive()))
                .toList());
    }

    /**
     * Creates one active Member account.
     *
     * @param memberId Proposed immutable Member ID.
     * @param name Proposed Member display name.
     * @param password Plaintext initial password, cleared before this method returns.
     */
    public void createMember(String memberId, String name, char[] password) {
        try {
            sessionManager.requireExco();
            MemberId validatedId = validatedMemberId(memberId);
            PasswordHash passwordHash = passwordHasher.hash(requirePassword(password));
            transactionManager.write(unitOfWork -> {
                if (unitOfWork.members().findById(validatedId).isPresent()) {
                    throw conflict("That Member ID is already reserved.");
                }
                Member member;
                try {
                    member = Member.create(validatedId, name, passwordHash);
                } catch (IllegalArgumentException exception) {
                    throw validation(exception.getMessage(), exception);
                }
                unitOfWork.members().insert(member);
                return null;
            });
        } finally {
            clear(password);
        }
    }

    /**
     * Changes a Member's display name without changing identity or credentials.
     *
     * @param memberId Existing Member ID.
     * @param name Replacement display name.
     */
    public void renameMember(String memberId, String name) {
        sessionManager.requireExco();
        MemberId validatedId = validatedMemberId(memberId);
        transactionManager.write(unitOfWork -> {
            Member member = requireMember(unitOfWork.members().findById(validatedId), validatedId);
            try {
                member.updateName(name);
            } catch (IllegalArgumentException exception) {
                throw validation(exception.getMessage(), exception);
            }
            unitOfWork.members().update(member);
            return null;
        });
    }

    /**
     * Replaces a Member password without exposing its credential hash.
     *
     * @param memberId Existing Member ID.
     * @param password Replacement plaintext password, cleared before this method returns.
     */
    public void replacePassword(String memberId, char[] password) {
        try {
            sessionManager.requireExco();
            MemberId validatedId = validatedMemberId(memberId);
            PasswordHash passwordHash = passwordHasher.hash(requirePassword(password));
            transactionManager.write(unitOfWork -> {
                Member member = requireMember(unitOfWork.members().findById(validatedId), validatedId);
                member.replacePasswordHash(passwordHash);
                unitOfWork.members().update(member);
                return null;
            });
        } finally {
            clear(password);
        }
    }

    /**
     * Soft-deactivates a Member when no unresolved record references that Member.
     *
     * @param memberId Existing Member ID.
     */
    public void deactivateMember(String memberId) {
        sessionManager.requireExco();
        MemberId validatedId = validatedMemberId(memberId);
        transactionManager.write(unitOfWork -> {
            Member member = requireMember(unitOfWork.members().findById(validatedId), validatedId);
            if (unitOfWork.loanRequests().existsPendingByMember(validatedId)
                    || unitOfWork.loans().existsUnresolvedByMember(validatedId)) {
                throw conflict("This Member has unresolved requests or loans and cannot be removed.");
            }
            try {
                member.deactivate(clock.instant());
            } catch (IllegalStateException exception) {
                throw conflict("This Member account is already inactive.");
            }
            unitOfWork.members().update(member);
            return null;
        });
    }

    private static MemberId validatedMemberId(String memberId) {
        try {
            return new MemberId(memberId);
        } catch (IllegalArgumentException exception) {
            throw validation(exception.getMessage(), exception);
        }
    }

    private static char[] requirePassword(char[] password) {
        if (password == null) {
            throw validation("Password cannot be blank.", null);
        }
        return password;
    }

    private static Member requireMember(java.util.Optional<Member> member, MemberId memberId) {
        return member.orElseThrow(() -> new ApplicationException(ApplicationErrorCode.NOT_FOUND,
                "The selected Member account no longer exists.", null));
    }

    private static ApplicationException validation(String message, Throwable cause) {
        String safeMessage = message == null || message.isBlank()
                ? "Member account details are invalid." : message;
        return new ApplicationException(ApplicationErrorCode.VALIDATION_FAILED, safeMessage, cause);
    }

    private static ApplicationException conflict(String message) {
        return new ApplicationException(ApplicationErrorCode.CONFLICT, message, null);
    }

    private static void clear(char[] password) {
        if (password != null) {
            Arrays.fill(password, '\0');
        }
    }
}
