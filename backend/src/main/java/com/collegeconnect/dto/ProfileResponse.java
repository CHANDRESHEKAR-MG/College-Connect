package com.collegeconnect.dto;

public record ProfileResponse(
        Long accountId,
        String role,
        String displayName,
        String accountStatus,
        String collegeIdentifier,
        String employeeIdentifier,
        String verifiedMobile) {
}
