CREATE TABLE IF NOT EXISTS exco_account (
    singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
    password_hash TEXT
);

CREATE TABLE IF NOT EXISTS members (
    member_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    is_active INTEGER NOT NULL CHECK (is_active IN (0, 1)),
    removed_at TEXT,
    CHECK ((is_active = 1 AND removed_at IS NULL)
        OR (is_active = 0 AND removed_at IS NOT NULL))
);

CREATE TABLE IF NOT EXISTS equipment_types (
    equipment_type_id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    comparison_key TEXT NOT NULL UNIQUE,
    is_offered INTEGER NOT NULL CHECK (is_offered IN (0, 1))
);

CREATE TABLE IF NOT EXISTS equipment_items (
    equipment_id TEXT PRIMARY KEY,
    equipment_type_id TEXT NOT NULL REFERENCES equipment_types(equipment_type_id),
    condition TEXT NOT NULL CHECK (condition IN ('GOOD', 'DAMAGED', 'LOST')),
    availability TEXT NOT NULL CHECK (availability IN ('AVAILABLE', 'ON_LOAN', 'UNAVAILABLE')),
    verification_pending INTEGER NOT NULL CHECK (verification_pending IN (0, 1)),
    is_retired INTEGER NOT NULL CHECK (is_retired IN (0, 1)),
    retired_at TEXT,
    CHECK (condition <> 'LOST' OR availability = 'UNAVAILABLE'),
    CHECK (verification_pending = 0 OR (availability = 'UNAVAILABLE' AND is_retired = 0)),
    CHECK ((is_retired = 1 AND retired_at IS NOT NULL)
        OR (is_retired = 0 AND retired_at IS NULL)),
    CHECK (is_retired = 0 OR (availability = 'UNAVAILABLE' AND verification_pending = 0))
);

CREATE TABLE IF NOT EXISTS loan_requests (
    loan_request_id TEXT PRIMARY KEY,
    member_id TEXT NOT NULL REFERENCES members(member_id),
    equipment_type_id TEXT NOT NULL REFERENCES equipment_types(equipment_type_id),
    requested_quantity INTEGER NOT NULL CHECK (requested_quantity > 0),
    requested_start_date TEXT NOT NULL,
    requested_end_date TEXT NOT NULL,
    details TEXT,
    requested_at TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    approved_quantity INTEGER,
    CHECK ((status = 'APPROVED' AND approved_quantity IS NOT NULL
            AND approved_quantity BETWEEN 1 AND requested_quantity)
        OR (status <> 'APPROVED' AND approved_quantity IS NULL)),
    UNIQUE (loan_request_id, member_id)
);

CREATE TABLE IF NOT EXISTS loans (
    loan_id TEXT PRIMARY KEY,
    loan_request_id TEXT NOT NULL,
    member_id TEXT NOT NULL,
    equipment_id TEXT NOT NULL REFERENCES equipment_items(equipment_id),
    started_at TEXT NOT NULL,
    end_date TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ON_LOAN', 'RETURN_PENDING', 'LOST_PENDING', 'COMPLETED')),
    reported_return_condition TEXT CHECK (reported_return_condition IN ('GOOD', 'DAMAGED')),
    FOREIGN KEY (loan_request_id, member_id)
        REFERENCES loan_requests(loan_request_id, member_id),
    CHECK (status IN ('ON_LOAN', 'LOST_PENDING') AND reported_return_condition IS NULL
        OR status = 'RETURN_PENDING' AND reported_return_condition IS NOT NULL
        OR status = 'COMPLETED')
);

CREATE TABLE IF NOT EXISTS damage_reports (
    loan_id TEXT PRIMARY KEY REFERENCES loans(loan_id),
    storage_key TEXT NOT NULL,
    image_format TEXT NOT NULL CHECK (image_format IN ('JPEG', 'PNG')),
    size_bytes INTEGER NOT NULL CHECK (size_bytes BETWEEN 1 AND 5242880),
    description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS loss_reports (
    loan_id TEXT PRIMARY KEY REFERENCES loans(loan_id),
    description TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_equipment_items_type_availability
    ON equipment_items(equipment_type_id, availability, is_retired);
CREATE INDEX IF NOT EXISTS idx_loan_requests_status_time
    ON loan_requests(status, requested_at, loan_request_id);
CREATE INDEX IF NOT EXISTS idx_loan_requests_member ON loan_requests(member_id);
CREATE INDEX IF NOT EXISTS idx_loan_requests_type ON loan_requests(equipment_type_id);
CREATE INDEX IF NOT EXISTS idx_loans_status ON loans(status);
CREATE INDEX IF NOT EXISTS idx_loans_member ON loans(member_id);
CREATE INDEX IF NOT EXISTS idx_loans_equipment ON loans(equipment_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_one_unresolved_loan_per_equipment
    ON loans(equipment_id)
    WHERE status IN ('ON_LOAN', 'RETURN_PENDING', 'LOST_PENDING');

CREATE TRIGGER IF NOT EXISTS prevent_damage_and_loss_report
BEFORE INSERT ON damage_reports
WHEN EXISTS (SELECT 1 FROM loss_reports WHERE loan_id = NEW.loan_id)
BEGIN
    SELECT RAISE(ABORT, 'loan already has a loss report');
END;

CREATE TRIGGER IF NOT EXISTS prevent_loss_and_damage_report
BEFORE INSERT ON loss_reports
WHEN EXISTS (SELECT 1 FROM damage_reports WHERE loan_id = NEW.loan_id)
BEGIN
    SELECT RAISE(ABORT, 'loan already has a damage report');
END;

CREATE TRIGGER IF NOT EXISTS validate_damage_report_branch
BEFORE INSERT ON damage_reports
WHEN NOT EXISTS (
    SELECT 1 FROM loans
    WHERE loan_id = NEW.loan_id
      AND status IN ('RETURN_PENDING', 'COMPLETED')
      AND reported_return_condition = 'DAMAGED'
)
BEGIN
    SELECT RAISE(ABORT, 'damage report does not match loan branch');
END;

CREATE TRIGGER IF NOT EXISTS validate_loss_report_branch
BEFORE INSERT ON loss_reports
WHEN NOT EXISTS (
    SELECT 1 FROM loans
    WHERE loan_id = NEW.loan_id
      AND status IN ('LOST_PENDING', 'COMPLETED')
      AND (status = 'LOST_PENDING' OR reported_return_condition IS NULL)
)
BEGIN
    SELECT RAISE(ABORT, 'loss report does not match loan branch');
END;
