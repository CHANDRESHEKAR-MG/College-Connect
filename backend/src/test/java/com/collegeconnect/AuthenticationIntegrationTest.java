package com.collegeconnect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.collegeconnect.dto.AccessTokenResponse;
import com.collegeconnect.entity.UserAccount;
import com.collegeconnect.repository.UserAccountRepository;
import com.collegeconnect.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthenticationIntegrationTest.RoleProbeConfiguration.class)
class AuthenticationIntegrationTest extends IntegrationTestSupport {

    private static final String TEST_PASSWORD = "collegeconnect-test-password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void successfulLoginReturnsFifteenMinuteJwtWithOnlyApprovedClaims() throws Exception {
        UserAccount account = createAccount(Role.ADMIN, "ACTIVE");
        AccessTokenResponse response = login(account.getLoginIdentifier(), TEST_PASSWORD);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900);

        Jwt jwt = jwtDecoder.decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(account.getId().toString());
        assertThat(jwt.getClaimAsString("role")).isEqualTo(Role.ADMIN.name());
        assertThat(jwt.getClaims()).containsOnlyKeys("sub", "role", "jti", "iat", "exp");

        mockMvc.perform(get("/api/admin/probe")
                        .header("Authorization", "Bearer " + response.accessToken()))
                .andExpect(status().isOk());
    }

    @Test
    void invalidPasswordReturnsUnauthorized() throws Exception {
        UserAccount account = createAccount(Role.STUDENT, "ACTIVE");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(account.getLoginIdentifier(), "incorrect-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void inactiveAccountCannotLogin() throws Exception {
        UserAccount account = createAccount(Role.PROFESSOR, "INACTIVE");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(account.getLoginIdentifier(), TEST_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void parentPasswordLoginIsDeferred() throws Exception {
        UserAccount account = createAccount(Role.PARENT, "ACTIVE");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(account.getLoginIdentifier(), TEST_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void invalidJwtIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/probe")
                        .header("Authorization", "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredJwtIsRejected() throws Exception {
        Instant now = Instant.now();
        String expiredToken = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .subject("42")
                        .claim("role", Role.ADMIN.name())
                        .id(UUID.randomUUID().toString())
                        .issuedAt(now.minusSeconds(1200))
                        .expiresAt(now.minusSeconds(600))
                        .build())).getTokenValue();

        mockMvc.perform(get("/api/admin/probe")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eachRoleCanAccessOnlyItsRoleGuardedRoutes() throws Exception {
        for (Role role : Role.values()) {
            String token = tokenForRole(role);

            for (Role endpointRole : Role.values()) {
                var result = mockMvc.perform(get("/api/" + rolePath(endpointRole) + "/probe")
                                .header("Authorization", "Bearer " + token))
                        .andReturn();
                int expectedStatus = role == endpointRole ? 200 : 403;
                assertThat(result.getResponse().getStatus())
                        .as("%s token accessing %s endpoint", role, endpointRole)
                        .isEqualTo(expectedStatus);
            }
        }
    }

    @Test
    void adminCanAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/probe")
                        .header("Authorization", "Bearer " + tokenForRole(Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void professorCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/probe")
                        .header("Authorization", "Bearer " + tokenForRole(Role.PROFESSOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotAccessParentEndpoint() throws Exception {
        mockMvc.perform(get("/api/parent/probe")
                        .header("Authorization", "Bearer " + tokenForRole(Role.STUDENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void parentCannotAccessProfessorEndpoint() throws Exception {
        mockMvc.perform(get("/api/professor/probe")
                        .header("Authorization", "Bearer " + tokenForRole(Role.PARENT)))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserCannotAccessUnconfiguredEndpoint() throws Exception {
        mockMvc.perform(get("/api/unconfigured/probe")
                        .header("Authorization", "Bearer " + tokenForRole(Role.ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotAccessProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/probe"))
                .andExpect(status().isUnauthorized());
    }

    private String tokenForRole(Role role) {
        Instant issuedAt = Instant.now();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                JwtClaimsSet.builder()
                        .subject(UUID.randomUUID().toString())
                        .claim("role", role.name())
                        .id(UUID.randomUUID().toString())
                        .issuedAt(issuedAt)
                        .expiresAt(issuedAt.plusSeconds(900))
                        .build())).getTokenValue();
    }

    private UserAccount createAccount(Role role, String accountStatus) {
        String loginIdentifier = role.name().toLowerCase() + "-" + UUID.randomUUID();
        return userAccountRepository.saveAndFlush(new UserAccount(
                loginIdentifier,
                passwordEncoder.encode(TEST_PASSWORD),
                role.name(),
                accountStatus));
    }

    private AccessTokenResponse login(String loginIdentifier, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(loginIdentifier, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AccessTokenResponse.class);
    }

    private String loginJson(String loginIdentifier, String password) throws Exception {
        return objectMapper.writeValueAsString(new LoginPayload(loginIdentifier, password));
    }

    private String rolePath(Role role) {
        return switch (role) {
            case ADMIN -> "admin";
            case PROFESSOR -> "professor";
            case STUDENT -> "student";
            case PARENT -> "parent";
        };
    }

    private record LoginPayload(String loginIdentifier, String password) {
    }

    @TestConfiguration
    static class RoleProbeConfiguration {

        @Bean
        RoleProbeController roleProbeController() {
            return new RoleProbeController();
        }
    }

    @RestController
    static class RoleProbeController {

        @GetMapping({
                "/api/admin/probe",
                "/api/professor/probe",
                "/api/student/probe",
                "/api/parent/probe",
                "/api/unconfigured/probe"
        })
        String probe() {
            return "ok";
        }
    }
}
