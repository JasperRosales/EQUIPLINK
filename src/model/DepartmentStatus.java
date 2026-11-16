package src.model;

import java.time.LocalDateTime;

/**
 * One department's (ICT or Facility) processing record for a request — a row of
 * {@code request_groups}. Each department keeps its own decision, remarks, and
 * release/return timestamps so one department never overwrites the other.
 */
public class DepartmentStatus {

    private final long groupId;
    private final String custodianRole;
    private final String status;
    private final Long occurrenceId;
    private final LocalDateTime scheduleStart;
    private final LocalDateTime scheduleEnd;
    private final String remarks;
    private final String decidedByName;
    private final LocalDateTime decidedAt;
    private final String releasedByName;
    private final LocalDateTime releasedAt;
    private final String inspectedByName;
    private final LocalDateTime inspectedAt;

    public DepartmentStatus(
            long groupId,
            String custodianRole,
            String status,
            Long occurrenceId,
            LocalDateTime scheduleStart,
            LocalDateTime scheduleEnd,
            String remarks,
            String decidedByName,
            LocalDateTime decidedAt,
            String releasedByName,
            LocalDateTime releasedAt,
            String inspectedByName,
            LocalDateTime inspectedAt) {
        this.groupId = groupId;
        this.custodianRole = custodianRole;
        this.status = status;
        this.occurrenceId = occurrenceId;
        this.scheduleStart = scheduleStart;
        this.scheduleEnd = scheduleEnd;
        this.remarks = remarks;
        this.decidedByName = decidedByName;
        this.decidedAt = decidedAt;
        this.releasedByName = releasedByName;
        this.releasedAt = releasedAt;
        this.inspectedByName = inspectedByName;
        this.inspectedAt = inspectedAt;
    }

    public long getGroupId() { return groupId; }
    public String getCustodianRole() { return custodianRole; }
    public String getStatus() { return status; }
    public Long getOccurrenceId() { return occurrenceId; }
    public LocalDateTime getScheduleStart() { return scheduleStart; }
    public LocalDateTime getScheduleEnd() { return scheduleEnd; }
    public String getRemarks() { return remarks; }
    public String getDecidedByName() { return decidedByName; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public String getReleasedByName() { return releasedByName; }
    public LocalDateTime getReleasedAt() { return releasedAt; }
    public String getInspectedByName() { return inspectedByName; }
    public LocalDateTime getInspectedAt() { return inspectedAt; }

    /** True when this department is still undecided after its schedule started. */
    public boolean isNotAssignedOnTime(LocalDateTime now) {
        return "PENDING".equals(status) && scheduleStart != null && now.isAfter(scheduleStart);
    }

    /** True when approved equipment was never released before the schedule ended. */
    public boolean isNotReleasedOnTime(LocalDateTime now) {
        return "ASSIGNED".equals(status) && scheduleEnd != null && now.isAfter(scheduleEnd);
    }
}
