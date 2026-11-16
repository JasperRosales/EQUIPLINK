package src.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public final class DatabaseInitializer {

    private DatabaseInitializer() {
        // Prevent object instantiation.
    }

    private static final List<String> SQL_STATEMENTS = List.of(

            // 1. USERS
            """
                    CREATE TABLE IF NOT EXISTS users (
                        user_id BIGSERIAL PRIMARY KEY,
                        username VARCHAR(60) NOT NULL UNIQUE,
                        email VARCHAR(150) NOT NULL UNIQUE,
                        password_hash TEXT NOT NULL,
                        password_salt TEXT NOT NULL,
                        password_iterations INTEGER NOT NULL
                            CHECK (password_iterations >= 100000),
                        role VARCHAR(20) NOT NULL
                            CHECK (role IN (
                                'BORROWER', 'ICT_STAFF', 'FACILITY_STAFF',
                                'ADMIN', 'DIRECTOR'
                            )),
                        borrower_type VARCHAR(10),
                        full_name VARCHAR(150) NOT NULL,
                        department VARCHAR(120),
                        active BOOLEAN NOT NULL DEFAULT TRUE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        CONSTRAINT borrower_type_rule CHECK (
                            (role = 'BORROWER'
                                AND borrower_type IS NOT NULL
                                AND borrower_type IN ('STUDENT', 'FACULTY'))
                            OR
                            (role <> 'BORROWER' AND borrower_type IS NULL)
                        )
                    )
                    """,

            """
                    CREATE TABLE IF NOT EXISTS password_reset_requests (
                        reset_request_id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL
                            REFERENCES users(user_id) ON DELETE CASCADE,
                        status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED')),
                        requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        reviewed_by BIGINT REFERENCES users(user_id),
                        reviewed_at TIMESTAMP,
                        review_note TEXT
                    )
                    """,

            """
                    CREATE UNIQUE INDEX IF NOT EXISTS idx_password_reset_one_open_request
                    ON password_reset_requests(user_id)
                    WHERE status IN ('PENDING', 'APPROVED')
                    """,

            """
                    CREATE INDEX IF NOT EXISTS idx_password_reset_status_date
                    ON password_reset_requests(status, requested_at)
                    """,

            // 2. EQUIPMENT
            """
                    CREATE TABLE IF NOT EXISTS equipment (
                        equipment_id BIGSERIAL PRIMARY KEY,
                        equipment_code VARCHAR(30) NOT NULL UNIQUE,
                        equipment_name VARCHAR(120) NOT NULL,
                        category VARCHAR(60) NOT NULL,
                        total_quantity INTEGER NOT NULL
                            CHECK (total_quantity >= 0),
                        damaged_quantity INTEGER NOT NULL DEFAULT 0
                            CHECK (damaged_quantity >= 0),
                        lost_quantity INTEGER NOT NULL DEFAULT 0
                            CHECK (lost_quantity >= 0),
                        status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE'
                            CHECK (status IN (
                                'AVAILABLE', 'UNDER_MAINTENANCE', 'RETIRED'
                            )),
                        active BOOLEAN NOT NULL DEFAULT TRUE,
                        custodian_role VARCHAR(20) NOT NULL
                            CHECK (custodian_role IN (
                                'ICT_STAFF', 'FACILITY_STAFF'
                            )),
                        created_by BIGINT REFERENCES users(user_id),
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        CHECK (damaged_quantity + lost_quantity <= total_quantity)
                    )
                    """,

            // 3. BORROW REQUESTS
            """
                    CREATE TABLE IF NOT EXISTS borrow_requests (
                        request_id BIGSERIAL PRIMARY KEY,
                        request_code VARCHAR(30) NOT NULL UNIQUE,
                        borrower_id BIGINT NOT NULL REFERENCES users(user_id),
                        venue VARCHAR(150) NOT NULL,
                        event_date DATE NOT NULL,
                        start_time TIME NOT NULL,
                        end_time TIME NOT NULL,
                        purpose TEXT NOT NULL,
                        participants VARCHAR(255) NOT NULL,
                        person_in_charge VARCHAR(150) NOT NULL,
                        position VARCHAR(100) NOT NULL,

                        status VARCHAR(30) NOT NULL DEFAULT 'PENDING_STAFF'
                            CHECK (status IN (
                                'PENDING_STAFF', 'NEEDS_ACTION',
                                'PENDING_ADMIN', 'PENDING_DIRECTOR',
                                'APPROVED', 'RELEASED', 'COMPLETED',
                                'REJECTED', 'CANCELLED'
                            )),

                        admin_status VARCHAR(20),
                        director_status VARCHAR(20),
                        remarks TEXT,
                        has_issues BOOLEAN NOT NULL DEFAULT FALSE,
                        cancelled_by BIGINT REFERENCES users(user_id),
                        cancelled_at TIMESTAMP,
                        cancellation_reason TEXT,
                        submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        CHECK (end_time > start_time)
                    )
                    """,

            // 3b. REQUEST OCCURRENCES (monthly/multi-schedule)
            """
                    CREATE TABLE IF NOT EXISTS request_occurrences (
                        occurrence_id BIGSERIAL PRIMARY KEY,
                        request_id BIGINT NOT NULL
                            REFERENCES borrow_requests(request_id)
                            ON DELETE CASCADE,
                        event_date DATE NOT NULL,
                        start_time TIME NOT NULL,
                        end_time TIME NOT NULL,
                        venue VARCHAR(150) NOT NULL,
                        release_notified BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CHECK (end_time > start_time)
                    )
                    """,
            // 4. REQUEST GROUPS
            """
                    CREATE TABLE IF NOT EXISTS request_groups (
                        group_id BIGSERIAL PRIMARY KEY,
                        request_id BIGINT NOT NULL
                            REFERENCES borrow_requests(request_id)
                            ON DELETE CASCADE,

                        custodian_role VARCHAR(20) NOT NULL
                            CHECK (custodian_role IN (
                                'ICT_STAFF', 'FACILITY_STAFF'
                            )),

                        status VARCHAR(20) NOT NULL
                            CHECK (status IN (
                                'PENDING', 'ASSIGNED', 'UNABLE',
                                'RELEASED', 'RETURNED', 'DECLINED',
                                'CANCELLED', 'NOT_REQUIRED'
                            )),

                        assigned_by BIGINT REFERENCES users(user_id),
                        assigned_at TIMESTAMP,
                        released_by BIGINT REFERENCES users(user_id),
                        released_at TIMESTAMP,
                        inspected_by BIGINT REFERENCES users(user_id),
                        inspected_at TIMESTAMP,
                        cancelled_by BIGINT REFERENCES users(user_id),
                        cancelled_at TIMESTAMP,
                        staff_remarks TEXT,

                        UNIQUE(request_id, custodian_role)
                    )
                    """,

            // 5. REQUEST ITEMS
            """
                    CREATE TABLE IF NOT EXISTS request_items (
                        request_item_id BIGSERIAL PRIMARY KEY,
                        request_id BIGINT NOT NULL
                            REFERENCES borrow_requests(request_id)
                            ON DELETE CASCADE,

                        group_id BIGINT NOT NULL
                            REFERENCES request_groups(group_id)
                            ON DELETE CASCADE,

                        requested_equipment_id BIGINT NOT NULL
                            REFERENCES equipment(equipment_id),

                        requested_quantity INTEGER NOT NULL
                            CHECK (requested_quantity > 0),

                        assigned_equipment_id BIGINT REFERENCES equipment(equipment_id),

                        assigned_quantity INTEGER NOT NULL DEFAULT 0
                            CHECK (assigned_quantity >= 0),

                        released_quantity INTEGER NOT NULL DEFAULT 0
                            CHECK (released_quantity >= 0),

                        returned_quantity INTEGER NOT NULL DEFAULT 0
                            CHECK (returned_quantity >= 0),

                        damaged_quantity INTEGER NOT NULL DEFAULT 0
                            CHECK (damaged_quantity >= 0),

                        missing_quantity INTEGER NOT NULL DEFAULT 0
                            CHECK (missing_quantity >= 0),

                        condition_before VARCHAR(80),
                        condition_after VARCHAR(80),
                        inspection_result VARCHAR(80),
                        staff_remarks TEXT
                    )
                    """,

            // 6. NOTIFICATIONS
            """
                    CREATE TABLE IF NOT EXISTS notifications (
                        notification_id BIGSERIAL PRIMARY KEY,
                        user_id BIGINT NOT NULL
                            REFERENCES users(user_id) ON DELETE CASCADE,

                        request_id BIGINT
                            REFERENCES borrow_requests(request_id)
                            ON DELETE CASCADE,

                        title VARCHAR(150) NOT NULL,
                        message TEXT NOT NULL,
                        is_read BOOLEAN NOT NULL DEFAULT FALSE,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """,

            // 7. REQUEST HISTORY
            """
                    CREATE TABLE IF NOT EXISTS request_history (
                        history_id BIGSERIAL PRIMARY KEY,

                        request_id BIGINT NOT NULL
                            REFERENCES borrow_requests(request_id)
                            ON DELETE CASCADE,

                        group_id BIGINT
                            REFERENCES request_groups(group_id)
                            ON DELETE CASCADE,

                        actor_id BIGINT REFERENCES users(user_id),
                        event_type VARCHAR(60) NOT NULL,
                        from_status VARCHAR(30),
                        to_status VARCHAR(30),
                        remarks TEXT,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """,

            // MIGRATIONS (run after every table exists)
            "ALTER TABLE request_groups ADD COLUMN IF NOT EXISTS occurrence_id BIGINT REFERENCES request_occurrences(occurrence_id) ON DELETE CASCADE",
            "ALTER TABLE request_items ADD COLUMN IF NOT EXISTS occurrence_id BIGINT REFERENCES request_occurrences(occurrence_id) ON DELETE CASCADE",
            "ALTER TABLE request_items ADD COLUMN IF NOT EXISTS substitute_equipment_id BIGINT REFERENCES equipment(equipment_id)",
            "ALTER TABLE request_items ADD COLUMN IF NOT EXISTS unavailable_remarks TEXT",
            "ALTER TABLE notifications ADD COLUMN IF NOT EXISTS occurrence_id BIGINT REFERENCES request_occurrences(occurrence_id) ON DELETE CASCADE",
            "ALTER TABLE request_groups DROP CONSTRAINT IF EXISTS request_groups_request_id_custodian_role_key",
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_request_groups_occ_role ON request_groups(occurrence_id, custodian_role) WHERE occurrence_id IS NOT NULL",
            "CREATE INDEX IF NOT EXISTS idx_occurrences_request ON request_occurrences(request_id, event_date, start_time)",

            /*
             * LEGACY CLEANUP: the earlier work-in-progress schema tracked schedules in
             * request_schedules and required request_items.schedule_id. The current
             * code uses request_occurrences instead, so carry the old schedules over
             * and then remove the obsolete column. This block is idempotent and is a
             * no-op on databases created with the current schema.
             */
            """
                    DO $$
                    DECLARE
                        legacy_schedules BOOLEAN;
                        legacy_column BOOLEAN;
                    BEGIN
                        SELECT EXISTS (
                            SELECT 1 FROM information_schema.tables
                            WHERE table_schema = 'public'
                              AND table_name = 'request_schedules'
                        ) INTO legacy_schedules;

                        SELECT EXISTS (
                            SELECT 1 FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = 'request_items'
                              AND column_name = 'schedule_id'
                        ) INTO legacy_column;

                        IF legacy_schedules THEN
                            INSERT INTO request_occurrences (
                                request_id, event_date, start_time, end_time, venue
                            )
                            SELECT rs.request_id, rs.event_date, rs.start_time,
                                   rs.end_time, br.venue
                            FROM request_schedules rs
                            JOIN borrow_requests br ON br.request_id = rs.request_id
                            WHERE NOT EXISTS (
                                SELECT 1 FROM request_occurrences ro
                                WHERE ro.request_id = rs.request_id
                                  AND ro.event_date = rs.event_date
                                  AND ro.start_time = rs.start_time
                                  AND ro.end_time = rs.end_time
                            );

                            IF legacy_column THEN
                                UPDATE request_items ri
                                SET occurrence_id = ro.occurrence_id
                                FROM request_schedules rs
                                JOIN request_occurrences ro
                                  ON ro.request_id = rs.request_id
                                 AND ro.event_date = rs.event_date
                                 AND ro.start_time = rs.start_time
                                 AND ro.end_time = rs.end_time
                                WHERE ri.schedule_id = rs.schedule_id
                                  AND ri.occurrence_id IS NULL;

                                UPDATE request_groups rg
                                SET occurrence_id = ro.occurrence_id
                                FROM request_occurrences ro
                                WHERE rg.request_id = ro.request_id
                                  AND rg.occurrence_id IS NULL
                                  AND (
                                      SELECT COUNT(*) FROM request_occurrences x
                                      WHERE x.request_id = rg.request_id
                                  ) = 1
                                  AND NOT EXISTS (
                                      SELECT 1 FROM request_groups other
                                      WHERE other.occurrence_id = ro.occurrence_id
                                        AND other.custodian_role = rg.custodian_role
                                        AND other.group_id <> rg.group_id
                                  );
                            END IF;
                        END IF;

                        IF legacy_column THEN
                            ALTER TABLE request_items
                                DROP COLUMN IF EXISTS schedule_id;
                        END IF;
                    END $$;
                    """,


            // Independent department (ICT / Facility) decisions
            "ALTER TABLE request_groups ADD COLUMN IF NOT EXISTS declined_by BIGINT REFERENCES users(user_id)",
            "ALTER TABLE request_groups ADD COLUMN IF NOT EXISTS declined_at TIMESTAMP",
            "ALTER TABLE request_groups ADD COLUMN IF NOT EXISTS overdue_notified BOOLEAN NOT NULL DEFAULT FALSE",
            "ALTER TABLE request_groups DROP CONSTRAINT IF EXISTS request_groups_status_check",
            """
                    ALTER TABLE request_groups ADD CONSTRAINT request_groups_status_check
                    CHECK (status IN (
                        'PENDING', 'ASSIGNED', 'UNABLE', 'RELEASED', 'RETURNED',
                        'DECLINED', 'CANCELLED', 'NOT_REQUIRED'
                    ))
                    """,
            "ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS overdue_notified BOOLEAN NOT NULL DEFAULT FALSE",

            // Notification details, per-user read and delete state
            "ALTER TABLE notifications ADD COLUMN IF NOT EXISTS department VARCHAR(20)",
            "ALTER TABLE notifications ADD COLUMN IF NOT EXISTS action VARCHAR(40)",
            "ALTER TABLE notifications ADD COLUMN IF NOT EXISTS read_at TIMESTAMP",
            "ALTER TABLE notifications ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE",
            "ALTER TABLE notifications ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP",
            "CREATE INDEX IF NOT EXISTS idx_notifications_user_visible ON notifications(user_id, is_deleted, created_at)",
            "CREATE INDEX IF NOT EXISTS idx_history_latest ON request_history(history_id)",

            // INDEXES
            """
                    CREATE INDEX IF NOT EXISTS idx_requests_borrower
                    ON borrow_requests(borrower_id)
                    """,

            """
                    CREATE INDEX IF NOT EXISTS idx_requests_status
                    ON borrow_requests(status)
                    """,

            """
                    CREATE INDEX IF NOT EXISTS idx_groups_status_role
                    ON request_groups(custodian_role, status)
                    """,

            """
                    CREATE INDEX IF NOT EXISTS idx_notifications_user_read
                    ON notifications(user_id, is_read)
                    """,

            """
                    CREATE INDEX IF NOT EXISTS idx_history_request
                    ON request_history(request_id, created_at)
                    """);

    public static void initializeDatabase() throws SQLException {

        try (Connection connection = Database.getConnection()) {

            connection.setAutoCommit(false);

            try (Statement statement = connection.createStatement()) {

                for (String sql : SQL_STATEMENTS) {
                    statement.execute(sql);
                }

                connection.commit();

                System.out.println(
                        "EquipLink database initialized successfully.");

            } catch (SQLException exception) {

                connection.rollback();

                System.err.println(
                        "Database initialization failed: "
                                + exception.getMessage());

                throw exception;
            }
        }
    }
}
