package clubstock.domain.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import clubstock.domain.account.MemberId;
import clubstock.domain.equipment.EquipmentTypeId;

class LoanRequestTest {
    private static final Instant REQUESTED_AT = Instant.parse("2026-01-15T08:30:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(REQUESTED_AT, ZoneOffset.UTC);
    private static final MemberId MEMBER_ID = new MemberId("member-1");
    private static final EquipmentTypeId EQUIPMENT_TYPE_ID = new EquipmentTypeId("type-1");

    @Test
    void submit_validRequest_startsPendingWithTimestampAndDetails() {
        LoanRequestId loanRequestId = new LoanRequestId("request-1");

        LoanRequest request = LoanRequest.submit(loanRequestId, MEMBER_ID, EQUIPMENT_TYPE_ID, 3,
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 3),
                "  Training session  ", FIXED_CLOCK);

        assertSame(loanRequestId, request.loanRequestId());
        assertSame(MEMBER_ID, request.memberId());
        assertSame(EQUIPMENT_TYPE_ID, request.equipmentTypeId());
        assertEquals(3, request.requestedQuantity());
        assertEquals(LocalDate.of(2026, 2, 1), request.requestedStartDate());
        assertEquals(LocalDate.of(2026, 2, 3), request.requestedEndDate());
        assertEquals("Training session", request.details().orElseThrow());
        assertEquals(REQUESTED_AT, request.requestedAt());
        assertEquals(LoanRequestStatus.PENDING, request.status());
        assertTrue(request.approvedQuantity().isEmpty());
    }

    @Test
    void submit_pastAndSameDayDates_areAccepted() {
        LoanRequest pastRequest = submitRequest(LocalDate.of(2025, 12, 1),
                LocalDate.of(2025, 12, 2), "details");
        LoanRequest sameDayRequest = submitRequest(LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 1), "details");

        assertEquals(LocalDate.of(2025, 12, 1), pastRequest.requestedStartDate());
        assertEquals(LocalDate.of(2026, 2, 1), sameDayRequest.requestedEndDate());
    }

    @Test
    void submit_blankDetails_normalizesToAbsence() {
        LoanRequest nullDetailsRequest = submitRequest(LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 1), null);
        LoanRequest blankDetailsRequest = submitRequest(LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 1), " \t ");

        assertTrue(nullDetailsRequest.details().isEmpty());
        assertTrue(blankDetailsRequest.details().isEmpty());
    }

    @Test
    void submit_invalidValues_rejectBeforeCreation() {
        LocalDate startDate = LocalDate.of(2026, 2, 2);
        LocalDate endDate = LocalDate.of(2026, 2, 1);

        assertThrows(IllegalArgumentException.class,
                () -> LoanRequest.submit(null, MEMBER_ID, EQUIPMENT_TYPE_ID, 1, startDate,
                        startDate, null, FIXED_CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> LoanRequest.submit(new LoanRequestId("request-1"), null, EQUIPMENT_TYPE_ID,
                        1, startDate, startDate, null, FIXED_CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> LoanRequest.submit(new LoanRequestId("request-1"), MEMBER_ID, null, 1,
                        startDate, startDate, null, FIXED_CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> LoanRequest.submit(new LoanRequestId("request-1"), MEMBER_ID,
                        EQUIPMENT_TYPE_ID, 0, startDate, startDate, null, FIXED_CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> LoanRequest.submit(new LoanRequestId("request-1"), MEMBER_ID,
                        EQUIPMENT_TYPE_ID, -1, startDate, startDate, null, FIXED_CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> LoanRequest.submit(new LoanRequestId("request-1"), MEMBER_ID,
                        EQUIPMENT_TYPE_ID, 1, startDate, endDate, null, FIXED_CLOCK));
        assertThrows(IllegalArgumentException.class,
                () -> LoanRequest.submit(new LoanRequestId("request-1"), MEMBER_ID,
                        EQUIPMENT_TYPE_ID, 1, startDate, startDate, null, null));
    }

    @Test
    void approve_fullAndPartialQuantity_recordsExactQuantityAndBecomesTerminal() {
        LoanRequest fullRequest = submitRequest(LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 3), null);
        LoanRequest partialRequest = submitRequest(new LoanRequestId("request-2"),
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 3), null);

        fullRequest.approve(3);
        partialRequest.approve(1);

        assertEquals(LoanRequestStatus.APPROVED, fullRequest.status());
        assertEquals(3, fullRequest.approvedQuantity().orElseThrow());
        assertEquals(LoanRequestStatus.APPROVED, partialRequest.status());
        assertEquals(1, partialRequest.approvedQuantity().orElseThrow());
    }

    @Test
    void approve_invalidQuantity_preservesPendingRequest() {
        LoanRequest request = submitRequest(LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 3), "details");

        assertThrows(IllegalArgumentException.class, () -> request.approve(0));
        assertThrows(IllegalArgumentException.class, () -> request.approve(-1));
        assertThrows(IllegalArgumentException.class, () -> request.approve(4));

        assertEquals(LoanRequestStatus.PENDING, request.status());
        assertTrue(request.approvedQuantity().isEmpty());
        assertEquals("details", request.details().orElseThrow());
    }

    @Test
    void cancel_owner_cancelsPendingRequest() {
        LoanRequest request = submitRequest(LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 3), null);

        request.cancelBy(new MemberId("member-1"));

        assertEquals(LoanRequestStatus.CANCELLED, request.status());
        assertTrue(request.approvedQuantity().isEmpty());
    }

    @Test
    void cancel_nonOwner_preservesPendingRequest() {
        LoanRequest request = submitRequest(LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 3), null);

        assertThrows(IllegalArgumentException.class,
                () -> request.cancelBy(new MemberId("member-2")));

        assertEquals(LoanRequestStatus.PENDING, request.status());
    }

    @Test
    void reject_pendingRequest_becomesRejected() {
        LoanRequest request = submitRequest(LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 3), null);

        request.reject();

        assertEquals(LoanRequestStatus.REJECTED, request.status());
        assertTrue(request.approvedQuantity().isEmpty());
    }

    @Test
    void terminalRequest_rejectsFurtherTransitionsWithoutMutation() {
        LoanRequest approvedRequest = submitRequest(new LoanRequestId("request-1"), "approved");
        LoanRequest rejectedRequest = submitRequest(new LoanRequestId("request-2"), "rejected");
        LoanRequest cancelledRequest = submitRequest(new LoanRequestId("request-3"), "cancelled");
        approvedRequest.approve(2);
        rejectedRequest.reject();
        cancelledRequest.cancelBy(MEMBER_ID);

        assertTerminalTransitionsFailWithoutMutation(approvedRequest, LoanRequestStatus.APPROVED,
                2, "approved");
        assertTerminalTransitionsFailWithoutMutation(rejectedRequest, LoanRequestStatus.REJECTED,
                null, "rejected");
        assertTerminalTransitionsFailWithoutMutation(cancelledRequest,
                LoanRequestStatus.CANCELLED, null, "cancelled");
    }

    @Test
    void equality_sameIdentityDifferentDetails_comparesByLoanRequestId() {
        LoanRequest first = submitRequest(new LoanRequestId("request-1"), "First");
        LoanRequest second = submitRequest(new LoanRequestId("request-1"), "Second");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void equality_differentIdentity_doesNotCompareEqual() {
        LoanRequest first = submitRequest(new LoanRequestId("request-1"), "Details");
        LoanRequest second = submitRequest(new LoanRequestId("request-2"), "Details");

        assertNotEquals(first, second);
    }

    private static LoanRequest submitRequest(LocalDate startDate, LocalDate endDate,
            String details) {
        return submitRequest(new LoanRequestId("request-1"), startDate, endDate, details);
    }

    private static LoanRequest submitRequest(LoanRequestId loanRequestId, String details) {
        return submitRequest(loanRequestId, LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 3), details);
    }

    private static LoanRequest submitRequest(LoanRequestId loanRequestId, LocalDate startDate,
            LocalDate endDate, String details) {
        return LoanRequest.submit(loanRequestId, MEMBER_ID, EQUIPMENT_TYPE_ID, 3, startDate,
                endDate, details, FIXED_CLOCK);
    }

    private static void assertTerminalTransitionsFailWithoutMutation(LoanRequest request,
            LoanRequestStatus status, Integer approvedQuantity, String details) {
        assertThrows(IllegalStateException.class, () -> request.approve(1));
        assertThrows(IllegalStateException.class, request::reject);
        assertThrows(IllegalStateException.class, () -> request.cancelBy(MEMBER_ID));

        assertEquals(status, request.status());
        if (approvedQuantity == null) {
            assertFalse(request.approvedQuantity().isPresent());
        } else {
            assertEquals(approvedQuantity, request.approvedQuantity().orElseThrow());
        }
        assertEquals(details, request.details().orElseThrow());
    }
}
