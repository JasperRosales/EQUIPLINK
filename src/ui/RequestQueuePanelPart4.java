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

/** Part 4 of RequestQueuePanel, split out to keep files short. */
public abstract class RequestQueuePanelPart4 extends RequestQueuePanelPart3 {

    void deleteAsAdmin(BorrowRequest request, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        long requestId = request.getRequestId();
        String label = requestLabel(request);
        int answer = UiSupport.showConfirmDialog(this,
                "Delete request " + label + "?\n"
                        + "It will be removed from all request lists and kept for audit. "
                        + "You can undo for " + UndoPolicy.RECOVERY_LABEL + ".",
                "Delete request", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            UiSupport.endAction(source);
            return;
        }
        String reason = UiSupport.showInputDialog(this,
                "Reason for deleting request " + label + ":",
                "Delete request", JOptionPane.QUESTION_MESSAGE);
        if (reason == null || reason.isBlank()) {
            UiSupport.endAction(source);
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                requestController.deleteRequestAsAdmin(actor, requestId, reason);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadRequests();
                    offerAdminUndo(label, requestId);
                } catch (Exception exception) {
                    UiSupport.showMessageDialog(RequestQueuePanelPart4.this,
                            messageOf(exception), "Unable to delete request",
                            JOptionPane.ERROR_MESSAGE);
                } finally {
                    UiSupport.endAction(source);
                }
            }
        }.execute();
    }

    void offerAdminUndo(String label, long requestId) {
        int choice = UiSupport.showUndoDialog(this,
                label + " was deleted.\nUndo is available for " + UndoPolicy.RECOVERY_LABEL + ".",
                "Request deleted");
        if (choice != 0) {
            return;
        }
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                requestController.restoreRequestAsAdmin(actor, requestId);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadRequests();
                    UiSupport.showMessageDialog(RequestQueuePanelPart4.this,
                            label + " was restored with its original status.",
                            "Request restored", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    UiSupport.showMessageDialog(RequestQueuePanelPart4.this,
                            messageOf(exception), "Unable to restore request",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    void declineRequest(BorrowRequest request, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        String remarks = UiSupport.showInputDialog(
                this, "Reason for declining your department's equipment:",
                "Decline " + RequestStatus.departmentLabel(actor.getRole()) + " equipment",
                JOptionPane.WARNING_MESSAGE);
        if (remarks == null) {
            UiSupport.endAction(source);
            return;
        }
        if (remarks.isBlank()) {
            UiSupport.endAction(source);
            UiSupport.showMessageDialog(
                    this, "Enter a reason for declining.",
                    "Reason required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runAction("Unable to decline request", () ->
                requestController.declineStaffRequest(actor, request.getRequestId(), remarks),
                source);
    }

    void assignRequest(BorrowRequest request, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        List<src.model.ItemInspection> assignments = ItemInspectionDialog.show(
                this, request, actor.getRole(), ItemInspectionDialog.Kind.ASSIGN);
        if (assignments == null) {
            UiSupport.endAction(source);
            return;
        }
        runAction("Unable to assign equipment", () ->
                requestController.assignStaffRequest(
                        actor, request.getRequestId(), assignments), source);
    }

    void reviewRequest(
            BorrowRequest request, boolean approve, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        String prompt = approve
                ? "Optional remarks:"
                : "Reason for declining this request:";
        String remarks = UiSupport.showInputDialog(
                this, prompt, approve ? "Approve request" : "Decline request",
                JOptionPane.QUESTION_MESSAGE);
        if (remarks == null) {
            UiSupport.endAction(source);
            return;
        }
        if (!approve && remarks.isBlank()) {
            UiSupport.endAction(source);
            UiSupport.showMessageDialog(
                    this, "Enter a reason before declining.",
                    "Reason required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runAction("Unable to review request", () -> {
            if (mode == Mode.ADMIN_REVIEW) {
                requestController.reviewByAdmin(
                        actor, request.getRequestId(), approve, remarks);
            } else {
                requestController.reviewByDirector(
                        actor, request.getRequestId(), approve, remarks);
            }
        }, source);
    }

    void updateTransaction(
            BorrowRequest request, boolean release, javax.swing.AbstractButton source) {
        if (!UiSupport.beginAction(source)) {
            return;
        }
        List<src.model.ItemInspection> inspections = ItemInspectionDialog.show(
                this, request, actor.getRole(),
                release ? ItemInspectionDialog.Kind.RELEASE : ItemInspectionDialog.Kind.RETURN);
        if (inspections == null) {
            UiSupport.endAction(source);
            return;
        }
        runAction(
                release ? "Unable to release equipment" : "Unable to record return",
                () -> {
                    if (release) {
                        requestController.releaseStaffAssignment(
                                actor, request.getRequestId(), inspections);
                    } else {
                        requestController.recordStaffReturn(
                                actor, request.getRequestId(), inspections);
                    }
                }, source);
    }

    void runAction(
            String errorTitle, ThrowingAction action, javax.swing.AbstractButton source) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                action.run();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    loadRequests();
                } catch (Exception exception) {
                    UiSupport.showMessageDialog(
                            RequestQueuePanelPart4.this, messageOf(exception),
                            errorTitle, JOptionPane.ERROR_MESSAGE);
                } finally {
                    UiSupport.endAction(source);
                }
            }
        }.execute();
    }
}
