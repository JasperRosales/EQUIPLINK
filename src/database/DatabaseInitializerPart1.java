package src.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/** Part 1 of DatabaseInitializer, split out to keep files short. */
public abstract class DatabaseInitializerPart1 {

    static final List<String> SQL_STATEMENTS = java.util.stream.Stream.concat(
            DatabaseSchema.FIRST_STATEMENTS.stream(),
            List.<String>of(

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
            "ALTER TABLE request_groups ADD COLUMN IF NOT EXISTS received_by BIGINT REFERENCES users(user_id)",
            "ALTER TABLE request_groups ADD COLUMN IF NOT EXISTS received_at TIMESTAMP",
            "ALTER TABLE request_groups ADD COLUMN IF NOT EXISTS overdue_notified BOOLEAN NOT NULL DEFAULT FALSE",
            "ALTER TABLE request_groups ADD COLUMN IF NOT EXISTS return_notified BOOLEAN NOT NULL DEFAULT FALSE",
            "ALTER TABLE request_groups DROP CONSTRAINT IF EXISTS request_groups_status_check",
            """
                    ALTER TABLE request_groups ADD CONSTRAINT request_groups_status_check
                    CHECK (status IN (
                        'PENDING', 'ASSIGNED', 'UNABLE', 'RELEASED', 'RECEIVED',
                        'RETURNED', 'DECLINED', 'CANCELLED', 'NOT_REQUIRED'
                    ))
                    """,
            "ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS overdue_notified BOOLEAN NOT NULL DEFAULT FALSE",

            // Notification details, per-user read and delete state
            "ALTER TABLE notifications ADD COLUMN IF NOT EXISTS department VARCHAR(20)",
            "ALTER TABLE notifications ADD COLUMN IF NOT EXISTS action VARCHAR(40)",
            "ALTER TABLE notifications ADD COLUMN IF NOT EXISTS read_at TIMESTAMP",
            "ALTER TABLE notifications ADD COLUMN IF NOT EXISTS is_deleted BOOLEAN NOT NULL DEFAULT FALSE",
            "ALTER TABLE notifications ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP",
            "ALTER TABLE notifications ADD COLUMN IF NOT EXISTS read_before_delete BOOLEAN",
            "CREATE INDEX IF NOT EXISTS idx_notifications_user_visible ON notifications(user_id, is_deleted, created_at)",

            // Recoverable request deletion: rows stay in the database until the
            // undo window expires, and the original stage is kept for restores.
            "ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP",
            "ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS deleted_by BIGINT REFERENCES users(user_id)",
            "ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS deletion_reason TEXT",
            "ALTER TABLE borrow_requests ADD COLUMN IF NOT EXISTS status_before_delete VARCHAR(30)",
            "CREATE INDEX IF NOT EXISTS idx_requests_deleted_at ON borrow_requests(deleted_at)",

            // Password recovery: registered email plus elevated reviewer verification
            "ALTER TABLE password_reset_requests ADD COLUMN IF NOT EXISTS registered_email_match BOOLEAN NOT NULL DEFAULT FALSE",
            "ALTER TABLE password_reset_requests ADD COLUMN IF NOT EXISTS identity_verified BOOLEAN NOT NULL DEFAULT FALSE",
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
                    """
            ).stream()).toList();
}
