package com.collegeconnect.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "supporting_document")
public class SupportingDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "absence_case_id")
    private AbsenceCase absenceCase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_request_id")
    private LeaveRequest leaveRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_account_id", nullable = false)
    private UserAccount uploadedBy;

    @Column(name = "private_storage_key", nullable = false, unique = true, length = 512)
    private String privateStorageKey;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "media_type", nullable = false, length = 120)
    private String mediaType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "scan_status", nullable = false, length = 30)
    private String scanStatus;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected SupportingDocument() {
    }

    public SupportingDocument(
            AbsenceCase absenceCase,
            LeaveRequest leaveRequest,
            UserAccount uploadedBy,
            String privateStorageKey,
            String originalFileName,
            String mediaType,
            long fileSizeBytes,
            String scanStatus,
            Instant uploadedAt) {
        this.absenceCase = absenceCase;
        this.leaveRequest = leaveRequest;
        this.uploadedBy = uploadedBy;
        this.privateStorageKey = privateStorageKey;
        this.originalFileName = originalFileName;
        this.mediaType = mediaType;
        this.fileSizeBytes = fileSizeBytes;
        this.scanStatus = scanStatus;
        this.uploadedAt = uploadedAt;
    }

    public AbsenceCase getAbsenceCase() {
        return absenceCase;
    }

    public LeaveRequest getLeaveRequest() {
        return leaveRequest;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getScanStatus() {
        return scanStatus;
    }

    public String getPrivateStorageKey() {
        return privateStorageKey;
    }

    public UserAccount getUploadedBy() {
        return uploadedBy;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void recordScanResult(String scanStatus) {
        this.scanStatus = scanStatus;
    }

    public void markReplaced() {
        this.scanStatus = "REPLACED";
    }

    public void markDeleted() {
        this.scanStatus = "DELETED";
    }
}
