package clubstock.application.catalog;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import clubstock.application.ApplicationErrorCode;
import clubstock.application.ApplicationException;
import clubstock.application.auth.AuthenticationService;
import clubstock.application.auth.PasswordHasher;
import clubstock.application.auth.SessionManager;
import clubstock.application.inventory.AvailabilityPolicy;
import clubstock.application.port.UnitOfWork;
import clubstock.domain.account.Member;
import clubstock.domain.account.MemberId;
import clubstock.domain.account.PasswordHash;
import clubstock.domain.equipment.EquipmentType;
import clubstock.domain.equipment.EquipmentTypeId;
import clubstock.domain.equipment.EquipmentTypeName;
import clubstock.infrastructure.sqlite.SqliteDatabase;

class MemberCatalogServiceTest {
    private static final MemberId MEMBER_ID = new MemberId("catalog-member");
    private static final String TEST_PASSWORD = "fixture-password";
    private static final PasswordHash TEST_HASH = new PasswordHash("catalog-test-hash");

    @TempDir
    Path temporaryDirectory;

    @Test
    void listOfferedTypes_filtersSortsAndReturnsImmutableSnapshot() {
        EquipmentType helmet = offeredType("type-z-helmet", "Helmet");
        EquipmentType balls = offeredType("type-a-balls", "Balls");
        EquipmentType pads = offeredType("type-b-pads", "Pads");
        EquipmentType hidden = EquipmentType.create(new EquipmentTypeId("type-hidden"),
                new EquipmentTypeName("Archived Equipment"));
        Fixture fixture = createFixture(temporaryDirectory,
                List.of(helmet, balls, pads, hidden),
                Map.of("type-z-helmet", 1, "type-a-balls", 0, "type-b-pads", 2,
                        "type-hidden", 10));
        fixture.authentication().authenticateMember(MEMBER_ID.value(),
                TEST_PASSWORD.toCharArray());

        List<CatalogType> snapshot = fixture.service().listOfferedTypes();

        assertEquals(List.of("type-a-balls", "type-z-helmet", "type-b-pads"),
                snapshot.stream().map(type -> type.equipmentTypeId().value()).toList());
        assertEquals(List.of("Balls", "Helmet", "Pads"),
                snapshot.stream().map(CatalogType::name).toList());
        assertEquals(List.of(0, 1, 2),
                snapshot.stream().map(CatalogType::availableQuantity).toList());
        assertEquals(List.of(new EquipmentTypeId("type-a-balls"),
                        new EquipmentTypeId("type-z-helmet"), new EquipmentTypeId("type-b-pads")),
                fixture.availabilityPolicy().calls().stream()
                        .map(AvailabilityCall::equipmentTypeId).toList());
        assertSame(fixture.availabilityPolicy().calls().get(0).unitOfWork(),
                fixture.availabilityPolicy().calls().get(2).unitOfWork());
        assertFalse(fixture.availabilityPolicy().calls().stream()
                .anyMatch(call -> call.equipmentTypeId().equals(hidden.equipmentTypeId())));
        assertThrows(UnsupportedOperationException.class, snapshot::clear);

        fixture.database().write(unitOfWork -> {
            EquipmentType renamedHelmet = unitOfWork.equipmentTypes()
                    .findById(helmet.equipmentTypeId()).orElseThrow();
            renamedHelmet.rename(new EquipmentTypeName("Changed Helmet"));
            unitOfWork.equipmentTypes().update(renamedHelmet);
            return null;
        });
        fixture.availabilityPolicy().setAvailableQuantity(helmet.equipmentTypeId(), 4);
        List<CatalogType> refreshedSnapshot = fixture.service().listOfferedTypes();

        assertEquals("Helmet", snapshot.get(1).name());
        assertEquals(1, snapshot.get(1).availableQuantity());
        assertEquals("Changed Helmet", refreshedSnapshot.get(1).name());
        assertEquals(4, refreshedSnapshot.get(1).availableQuantity());
        assertNotSame(fixture.availabilityPolicy().calls().get(0).unitOfWork(),
                fixture.availabilityPolicy().calls().get(3).unitOfWork());
    }

    @Test
    void listOfferedTypes_returnsImmutableEmptyListWhenNoTypeIsOffered() {
        EquipmentType hidden = EquipmentType.create(new EquipmentTypeId("type-hidden"),
                new EquipmentTypeName("Archived Equipment"));
        Fixture fixture = createFixture(temporaryDirectory, List.of(hidden), Map.of());
        fixture.authentication().authenticateMember(MEMBER_ID.value(),
                TEST_PASSWORD.toCharArray());

        List<CatalogType> snapshot = fixture.service().listOfferedTypes();

        assertTrue(snapshot.isEmpty());
        assertTrue(fixture.availabilityPolicy().calls().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.add(new CatalogType(new EquipmentTypeId("other"), "Other", 0)));
    }

    @Test
    void listOfferedTypes_requiresMemberRoleAndAnActiveSession() {
        Fixture fixture = createFixture(temporaryDirectory, List.of(), Map.of());

        ApplicationException signedOut = assertThrows(ApplicationException.class,
                fixture.service()::listOfferedTypes);
        assertEquals(ApplicationErrorCode.AUTHORIZATION_DENIED, signedOut.errorCode());

        fixture.authentication().completeExcoSetup("fixture-password".toCharArray(),
                "fixture-password".toCharArray());
        ApplicationException exco = assertThrows(ApplicationException.class,
                fixture.service()::listOfferedTypes);
        assertEquals(ApplicationErrorCode.AUTHORIZATION_DENIED, exco.errorCode());
        assertTrue(fixture.availabilityPolicy().calls().isEmpty());
    }

    @Test
    void listOfferedTypes_rejectsInactiveMemberWithExistingSession() {
        Fixture fixture = createFixture(temporaryDirectory, List.of(), Map.of());
        fixture.authentication().authenticateMember(MEMBER_ID.value(),
                TEST_PASSWORD.toCharArray());
        fixture.database().write(unitOfWork -> {
            Member member = unitOfWork.members().findById(MEMBER_ID).orElseThrow();
            member.deactivate(Instant.parse("2026-09-24T00:00:00Z"));
            unitOfWork.members().update(member);
            return null;
        });

        ApplicationException failure = assertThrows(ApplicationException.class,
                fixture.service()::listOfferedTypes);

        assertEquals(ApplicationErrorCode.AUTHORIZATION_DENIED, failure.errorCode());
        assertTrue(fixture.availabilityPolicy().calls().isEmpty());
    }

    @Test
    void listOfferedTypes_rejectsMissingMemberWithExistingSession() throws SQLException {
        Fixture fixture = createFixture(temporaryDirectory, List.of(), Map.of());
        fixture.authentication().authenticateMember(MEMBER_ID.value(),
                TEST_PASSWORD.toCharArray());
        try (Connection connection = DriverManager.getConnection(
                "jdbc:sqlite:" + fixture.databasePath());
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM members WHERE member_id = ?")) {
            statement.setString(1, MEMBER_ID.value());
            assertEquals(1, statement.executeUpdate());
        }

        ApplicationException failure = assertThrows(ApplicationException.class,
                fixture.service()::listOfferedTypes);

        assertEquals(ApplicationErrorCode.AUTHORIZATION_DENIED, failure.errorCode());
        assertTrue(fixture.availabilityPolicy().calls().isEmpty());
    }

    @Test
    void catalogType_exposesOnlyMemberSafeValuesAndValidatesItsState() {
        CatalogType catalogType = new CatalogType(new EquipmentTypeId("type-1"), "Helmet", 0);
        RecordComponent[] components = CatalogType.class.getRecordComponents();

        assertEquals(List.of("equipmentTypeId", "name", "availableQuantity"),
                Arrays.stream(components).map(RecordComponent::getName).toList());
        assertArrayEquals(new Class<?>[] {EquipmentTypeId.class, String.class, int.class},
                Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new));
        assertEquals(0, catalogType.availableQuantity());
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogType(null, "Helmet", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogType(new EquipmentTypeId("type-1"), null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogType(new EquipmentTypeId("type-1"), "Helmet", -1));
    }

    private Fixture createFixture(Path directory, List<EquipmentType> equipmentTypes,
            Map<String, Integer> availableQuantities) {
        Path databasePath = directory.resolve("clubstock.db");
        SqliteDatabase database = new SqliteDatabase(databasePath);
        database.initialize();
        PasswordHasher passwordHasher = new TestPasswordHasher();
        SessionManager sessionManager = new SessionManager();
        RecordingAvailabilityPolicy availabilityPolicy =
                new RecordingAvailabilityPolicy(availableQuantities);
        database.write(unitOfWork -> {
            unitOfWork.members().insert(Member.create(MEMBER_ID, "Catalogue Member", TEST_HASH));
            equipmentTypes.forEach(unitOfWork.equipmentTypes()::insert);
            return null;
        });
        AuthenticationService authentication = new AuthenticationService(database,
                passwordHasher, sessionManager);
        MemberCatalogService service = new MemberCatalogService(database, sessionManager,
                availabilityPolicy);
        return new Fixture(databasePath, database, authentication, availabilityPolicy, service);
    }

    private static EquipmentType offeredType(String id, String name) {
        EquipmentType type = EquipmentType.create(new EquipmentTypeId(id),
                new EquipmentTypeName(name));
        type.offer();
        return type;
    }

    private record Fixture(Path databasePath, SqliteDatabase database,
            AuthenticationService authentication, RecordingAvailabilityPolicy availabilityPolicy,
            MemberCatalogService service) {
    }

    private record AvailabilityCall(UnitOfWork unitOfWork, EquipmentTypeId equipmentTypeId) {
    }

    private static final class RecordingAvailabilityPolicy implements AvailabilityPolicy {
        private final Map<String, Integer> availableQuantities;
        private final List<AvailabilityCall> calls = new ArrayList<>();

        private RecordingAvailabilityPolicy(Map<String, Integer> availableQuantities) {
            this.availableQuantities = new HashMap<>(availableQuantities);
        }

        @Override
        public int countAvailable(UnitOfWork unitOfWork, EquipmentTypeId equipmentTypeId) {
            calls.add(new AvailabilityCall(unitOfWork, equipmentTypeId));
            Integer availableQuantity = availableQuantities.get(equipmentTypeId.value());
            if (availableQuantity == null) {
                throw new AssertionError("No test quantity configured for offered equipment type.");
            }
            return availableQuantity;
        }

        private List<AvailabilityCall> calls() {
            return List.copyOf(calls);
        }

        private void setAvailableQuantity(EquipmentTypeId equipmentTypeId,
                int availableQuantity) {
            availableQuantities.put(equipmentTypeId.value(), availableQuantity);
        }
    }

    private static final class TestPasswordHasher implements PasswordHasher {
        @Override
        public PasswordHash hash(char[] password) {
            Arrays.fill(password, '\0');
            return TEST_HASH;
        }

        @Override
        public boolean matches(char[] password, PasswordHash passwordHash) {
            char[] expectedPassword = TEST_PASSWORD.toCharArray();
            try {
                return TEST_HASH.equals(passwordHash)
                        && Arrays.equals(password, expectedPassword);
            } finally {
                Arrays.fill(password, '\0');
                Arrays.fill(expectedPassword, '\0');
            }
        }
    }
}
