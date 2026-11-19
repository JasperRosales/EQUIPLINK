package src.ui;

import src.controller.BorrowRequestController;
import src.model.BorrowRequest;
import src.model.DepartmentStatus;
import src.model.EquipmentRequestItem;
import src.model.RequestStatus;
import src.model.User;
import src.util.UndoPolicy;
import src.ui.components.PrimaryButton;
import src.ui.components.SurfacePanel;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/** Part 2 of RequestQueuePanel, split out to keep files short. */
public abstract class RequestQueuePanelPart2 extends RequestQueuePanelPart1 {

    static RequestStatus.Category[] statusCategoriesFor(Mode mode) {
        return switch (mode) {
            case STAFF_QUEUE -> new RequestStatus.Category[] {
                    RequestStatus.Category.ALL, RequestStatus.Category.PENDING,
                    RequestStatus.Category.APPROVED, RequestStatus.Category.PARTIALLY_RELEASED,
                    RequestStatus.Category.COMPLETED, RequestStatus.Category.OVERDUE,
                    RequestStatus.Category.NEEDS_ACTION, RequestStatus.Category.DECLINED,
                    RequestStatus.Category.CANCELLED
            };
            case SCHEDULE_ASSIGNMENT -> new RequestStatus.Category[] {
                    RequestStatus.Category.ALL, RequestStatus.Category.PENDING,
                    RequestStatus.Category.APPROVED, RequestStatus.Category.PARTIALLY_RELEASED,
                    RequestStatus.Category.OVERDUE, RequestStatus.Category.NEEDS_ACTION,
                    RequestStatus.Category.DECLINED, RequestStatus.Category.CANCELLED
            };
            case TRANSACTION_HISTORY -> new RequestStatus.Category[] {
                    RequestStatus.Category.ALL, RequestStatus.Category.PENDING,
                    RequestStatus.Category.APPROVED, RequestStatus.Category.PARTIALLY_APPROVED,
                    RequestStatus.Category.FULLY_APPROVED,
                    RequestStatus.Category.PARTIALLY_RELEASED,
                    RequestStatus.Category.FULLY_RELEASED,
                    RequestStatus.Category.COMPLETED, RequestStatus.Category.DECLINED,
                    RequestStatus.Category.OVERDUE, RequestStatus.Category.NEEDS_ACTION,
                    RequestStatus.Category.ADMIN_REVIEW, RequestStatus.Category.DIRECTOR_REVIEW,
                    RequestStatus.Category.FINAL_APPROVED, RequestStatus.Category.CANCELLED
            };
            case ADMIN_REVIEW -> new RequestStatus.Category[] {
                    RequestStatus.Category.ALL, RequestStatus.Category.ADMIN_REVIEW,
                    RequestStatus.Category.PENDING, RequestStatus.Category.APPROVED,
                    RequestStatus.Category.DECLINED, RequestStatus.Category.NEEDS_ACTION,
                    RequestStatus.Category.OVERDUE, RequestStatus.Category.CANCELLED
            };
            case DIRECTOR_REVIEW -> new RequestStatus.Category[] {
                    RequestStatus.Category.ALL, RequestStatus.Category.DIRECTOR_REVIEW,
                    RequestStatus.Category.FINAL_APPROVED, RequestStatus.Category.PENDING,
                    RequestStatus.Category.DECLINED, RequestStatus.Category.OVERDUE,
                    RequestStatus.Category.CANCELLED
            };
            case ALL_REQUESTS -> new RequestStatus.Category[] {
                    RequestStatus.Category.ALL, RequestStatus.Category.PENDING,
                    RequestStatus.Category.APPROVED, RequestStatus.Category.PARTIALLY_RELEASED,
                    RequestStatus.Category.COMPLETED, RequestStatus.Category.DECLINED,
                    RequestStatus.Category.OVERDUE, RequestStatus.Category.NEEDS_ACTION,
                    RequestStatus.Category.CANCELLED, RequestStatus.Category.ADMIN_REVIEW,
                    RequestStatus.Category.DIRECTOR_REVIEW, RequestStatus.Category.FINAL_APPROVED
            };
        };
    }

    static boolean statusCategoriesContains(Mode mode, RequestStatus.Category category) {
        for (RequestStatus.Category candidate : statusCategoriesFor(mode)) {
            if (candidate == category) return true;
        }
        return false;
    }

    String titleForMode() {
        switch (mode) {
            case STAFF_QUEUE:
                return "Requests";
            case ADMIN_REVIEW:
                return "Borrowing requests";
            case DIRECTOR_REVIEW:
                return "Request approval";
            case SCHEDULE_ASSIGNMENT:
                return "Schedule & assignment";
            case TRANSACTION_HISTORY:
                return "Transaction history";
            case ALL_REQUESTS:
            default:
                return "All requests";
        }
    }

    String subtitleForMode() {
        switch (mode) {
            case STAFF_QUEUE:
                return "Requests with your department's equipment. Approve or decline independently of other departments.";
            case ADMIN_REVIEW:
                return "Review staff-assigned requests before they go to the campus director.";
            case DIRECTOR_REVIEW:
                return "Final approval for requests ready for campus director review.";
            case SCHEDULE_ASSIGNMENT:
                return "Release your department's approved equipment and inspect returns, independently of other departments.";
            case TRANSACTION_HISTORY:
                return "Pending, approved, declined, overdue, and completed request records.";
            case ALL_REQUESTS:
            default:
                return scopeRole == null
                        ? "Monitor every request and each department's decision."
                        : "Every request containing your department's equipment.";
        }
    }

    String emptyMessage() {
        switch (mode) {
            case STAFF_QUEUE:
                return "No requests are waiting for your equipment group.";
            case ADMIN_REVIEW:
            case DIRECTOR_REVIEW:
                return "There are no requests waiting for approval.";
            case SCHEDULE_ASSIGNMENT:
                return "Nothing to release or receive. Requests appear here after final approval.";
            case TRANSACTION_HISTORY:
                return "No transaction records match the current filters.";
            case ALL_REQUESTS:
            default:
                return "No request records have been submitted yet.";
        }
    }

    String messageOf(Exception exception) {
        Throwable cause = exception instanceof ExecutionException && exception.getCause() != null
                ? exception.getCause() : exception;
        return cause.getMessage();
    }

    @FunctionalInterface
    interface ThrowingAction {
        void run() throws Exception;
    }
}
