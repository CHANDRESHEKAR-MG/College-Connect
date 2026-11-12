package com.collegeconnect.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "leave_request_session",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_leave_request_session",
                columnNames = {"leave_request_id", "session_id"}))
public class LeaveRequestSession extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_request_id", nullable = false)
    private LeaveRequest leaveRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ClassSession session;

    protected LeaveRequestSession() {
    }

    public LeaveRequestSession(LeaveRequest leaveRequest, ClassSession session) {
        this.leaveRequest = leaveRequest;
        this.session = session;
    }

    public LeaveRequest getLeaveRequest() {
        return leaveRequest;
    }

    public ClassSession getSession() {
        return session;
    }
}
