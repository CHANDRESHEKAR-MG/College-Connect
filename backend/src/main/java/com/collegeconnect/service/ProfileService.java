package com.collegeconnect.service;

import com.collegeconnect.dto.ProfileResponse;
import com.collegeconnect.entity.AdminProfile;
import com.collegeconnect.entity.ParentProfile;
import com.collegeconnect.entity.ProfessorProfile;
import com.collegeconnect.entity.StudentProfile;
import com.collegeconnect.entity.UserAccount;
import com.collegeconnect.repository.AdminProfileRepository;
import com.collegeconnect.repository.ParentProfileRepository;
import com.collegeconnect.repository.ProfessorProfileRepository;
import com.collegeconnect.repository.StudentProfileRepository;
import com.collegeconnect.repository.UserAccountRepository;
import com.collegeconnect.security.Role;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {

    private final UserAccountRepository userAccountRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final ProfessorProfileRepository professorProfileRepository;
    private final AdminProfileRepository adminProfileRepository;
    private final ParentProfileRepository parentProfileRepository;

    public ProfileService(
            UserAccountRepository userAccountRepository,
            StudentProfileRepository studentProfileRepository,
            ProfessorProfileRepository professorProfileRepository,
            AdminProfileRepository adminProfileRepository,
            ParentProfileRepository parentProfileRepository) {
        this.userAccountRepository = userAccountRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.professorProfileRepository = professorProfileRepository;
        this.adminProfileRepository = adminProfileRepository;
        this.parentProfileRepository = parentProfileRepository;
    }

    public ProfileResponse getCurrentProfile(Long accountId, Role role) {
        UserAccount account = userAccountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Role accountRole = Role.fromDatabaseValue(account.getRole());
        if (accountRole != role) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        return switch (role) {
            case ADMIN -> adminProfileRepository.findByAccount_Id(accountId)
                    .map(profile -> new ProfileResponse(
                            accountId,
                            accountRole.name(),
                            profile.getDisplayName(),
                            account.getStatus(),
                            null,
                            null,
                            null))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin profile not found"));
            case PROFESSOR -> professorProfileRepository.findByAccount_Id(accountId)
                    .map(profile -> new ProfileResponse(
                            accountId,
                            accountRole.name(),
                            profile.getDisplayName(),
                            account.getStatus(),
                            null,
                            profile.getEmployeeIdentifier(),
                            null))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Professor profile not found"));
            case STUDENT -> studentProfileRepository.findByAccount_Id(accountId)
                    .map(profile -> new ProfileResponse(
                            accountId,
                            accountRole.name(),
                            profile.getDisplayName(),
                            account.getStatus(),
                            profile.getCollegeIdentifier(),
                            null,
                            null))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student profile not found"));
            case PARENT -> parentProfileRepository.findByAccount_Id(accountId)
                    .map(profile -> new ProfileResponse(
                            accountId,
                            accountRole.name(),
                            profile.getDisplayName(),
                            account.getStatus(),
                            null,
                            null,
                            profile.getVerifiedMobile()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parent profile not found"));
        };
    }
}
