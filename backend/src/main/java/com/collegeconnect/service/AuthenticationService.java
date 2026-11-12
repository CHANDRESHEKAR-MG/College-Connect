package com.collegeconnect.service;

import com.collegeconnect.dto.AccessTokenResponse;
import com.collegeconnect.dto.LoginRequest;
import com.collegeconnect.security.Role;
import com.collegeconnect.security.UserAccountPrincipal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {

    private static final long ACCESS_TOKEN_LIFETIME_SECONDS = 15 * 60;

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;

    public AuthenticationService(AuthenticationManager authenticationManager, JwtEncoder jwtEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtEncoder = jwtEncoder;
    }

    public AccessTokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.loginIdentifier(),
                        request.password()));
        UserAccountPrincipal principal = (UserAccountPrincipal) authentication.getPrincipal();
        if (principal.getRole() == Role.PARENT) {
            throw new BadCredentialsException("Invalid credentials");
        }

        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(principal.getId().toString())
                .claim("role", principal.getRole().name())
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ACCESS_TOKEN_LIFETIME_SECONDS, ChronoUnit.SECONDS))
                .build();

        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims)).getTokenValue();
        return new AccessTokenResponse(accessToken, "Bearer", ACCESS_TOKEN_LIFETIME_SECONDS);
    }
}
