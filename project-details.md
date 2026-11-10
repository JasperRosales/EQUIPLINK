# EquipLink Business Process

## 1. Overview

The EquipLink Business Process describes the overall process of managing campus equipment borrowing requests, from user registration and login to request submission, staff processing, administrative approval, equipment release, return, and transaction completion.

The system involves five user roles:

- **Borrower (Student/Faculty)** – Submits equipment borrowing requests and returns borrowed equipment.
- **ICT Staff** – Processes requests involving ICT equipment.
- **Facility Staff** – Processes requests involving facility equipment.
- **Facility Admin / System Admin** – Reviews and approves or rejects borrowing requests.
- **Campus Director** – Provides the final approval or rejection.

## 2. Business Process Description

### 2.1 User Registration and Login

Users access EquipLink through the login interface. New users can register an account, while existing users can log in using their credentials. The system validates the login information and identifies the user's role.

### 2.2 Equipment Viewing and Request Submission

Borrowers can view available campus equipment and submit borrowing requests by providing the necessary information, including:

- Venue
- Event date
- Start and end time
- Purpose of borrowing
- Number of participants
- Person in charge
- Requested equipment and quantities

The system saves the submitted request and notifies the appropriate staff.

### 2.3 Staff Processing

ICT Staff and Facility Staff process equipment requests according to their assigned responsibilities.

- ICT Staff handles ICT-related equipment.
- Facility Staff handles facility-related equipment.

They check equipment availability and assign the available quantities. If equipment cannot be provided, the staff marks the item as unable to fulfill.

Once all required staff processing is completed, the request proceeds to administrative review unless further action is needed from the borrower.

### 2.4 Administrative Review

The administrator reviews the processed borrowing request and evaluates the information and staff assignments.

The administrator can either:

- **Approve** – Forward the request to the Campus Director for final approval.
- **Reject** – Reject the request and notify the borrower.

### 2.5 Final Approval

The Campus Director reviews the request forwarded by the administrator.

The Campus Director can approve or reject the request. Once approved, the system updates the request status and notifies the borrower.

### 2.6 Equipment Release

After final approval, the responsible staff releases the assigned equipment to the borrower.

Before releasing the equipment, staff records its initial condition to serve as a reference during the return inspection.

### 2.7 Equipment Usage and Return

The borrower uses the equipment for the approved activity and returns it after use.

The responsible staff inspects the returned equipment and records its condition after borrowing.

### 2.8 Damage and Missing Equipment Assessment

During the return inspection, staff determines whether there are damaged or missing items.

If there are issues, the staff records the corresponding damaged or missing quantities. Otherwise, the equipment can proceed to the normal availability update.

### 2.9 Transaction Completion

After the return inspection, EquipLink updates the equipment records, marks the borrowing request as completed, and saves the transaction history for future reference.

## 3. Business Process by User Role

| User Role | Responsibilities |
|---|---|
| Borrower (Student/Faculty) | Register, log in, view available equipment, submit borrowing requests, and return equipment. |
| ICT Staff | Process ICT equipment requests, assign equipment, release equipment, and inspect returned ICT equipment. |
| Facility Staff | Process facility equipment requests, assign equipment, release equipment, and inspect returned facility equipment. |
| Facility Admin / System Admin | Review processed requests and approve or reject borrowing requests. |
| Campus Director | Provide the final approval or rejection of borrowing requests. |

## 4. Expected System Outcomes

Through this business process, EquipLink aims to:

1. Organize campus equipment borrowing requests in a centralized system.
2. Reduce manual paperwork and delays in processing requests.
3. Provide clear responsibilities for ICT Staff and Facility Staff.
4. Establish a structured administrative and final approval process.
5. Monitor equipment availability and assignments.
6. Record equipment conditions before and after borrowing.
7. Track damaged and missing equipment.
8. Maintain transaction history for monitoring and reporting.

---

**Project Name:** EquipLink – Campus Equipment Borrowing and Management System

**Document:** Business Process

**Purpose:** To describe the proposed workflow for equipment borrowing, processing, approval, release, return, and transaction monitoring.
