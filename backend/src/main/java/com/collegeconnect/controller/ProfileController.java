package com.collegeconnect.controller;

import com.collegeconnect.dto.ProfileResponse;
import com.collegeconnect.security.Role;
import com.collegeconnect.service.ProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/admin/me")
    public ResponseEntity<ProfileResponse> adminProfile(Authentication authentication) {
        return ResponseEntity.ok(profileService.getCurrentProfile(currentUserId(authentication), Role.ADMIN));
    }

    @GetMapping("/professor/me")
    public ResponseEntity<ProfileResponse> professorProfile(Authentication authentication) {
        return ResponseEntity.ok(profileService.getCurrentProfile(currentUserId(authentication), Role.PROFESSOR));
    }

    @GetMapping("/student/me")
    public ResponseEntity<ProfileResponse> studentProfile(Authentication authentication) {
        return ResponseEntity.ok(profileService.getCurrentProfile(currentUserId(authentication), Role.STUDENT));
    }

    @GetMapping("/parent/me")
    public ResponseEntity<ProfileResponse> parentProfile(Authentication authentication) {
        return ResponseEntity.ok(profileService.getCurrentProfile(currentUserId(authentication), Role.PARENT));
    }

    private Long currentUserId(Authentication authentication) {
        return Long.valueOf(authentication.getName());
    }
}
