package com.example.agent;

import com.example.agent.model.entity.AppUser;
import com.example.agent.repository.AppUserRepository;
import com.example.agent.repository.EmailVerificationCodeRepository;
import com.example.agent.service.CaptchaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;
    @Autowired EmailVerificationCodeRepository codes;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired CaptchaService captchaService;

    private AppUser user;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM music_knowledge_feedback");
        codes.deleteAll();
        users.deleteAll();
        user = users.saveAndFlush(AppUser.register("测试用户_01", "login@example.com", "13812345678",
                passwordEncoder.encode("Agent1234")));
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM music_knowledge_feedback");
        codes.deleteAll();
        users.deleteAll();
    }

    @Test
    void storesOnlyHashAndTimestamps() {
        assertThat(user.getPassword()).startsWith("sha256$10000$").doesNotContain("Agent1234");
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
    }

    @Test
    void logsInWithUsernameEmailOrPhone() throws Exception {
        for (String account : new String[]{"测试用户_01", "LOGIN@example.com", "13812345678"}) {
            mockMvc.perform(post("/api/auth/login").with(csrf())
                            .contentType("application/json")
                            .content("{\"account\":\"" + account + "\",\"password\":\"Agent1234\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.username").value("测试用户_01"));
        }
    }

    @Test
    void rememberMeCookieRestoresAuthenticationWithoutOriginalSession() throws Exception {
        var loginResult = mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "account": "login@example.com",
                                  "password": "Agent1234",
                                  "rememberMe": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("SONORA_REMEMBER"))
                .andExpect(cookie().httpOnly("SONORA_REMEMBER", true))
                .andExpect(cookie().maxAge("SONORA_REMEMBER", 30 * 24 * 60 * 60))
                .andReturn();

        var rememberCookie = loginResult.getResponse().getCookie("SONORA_REMEMBER");
        assertThat(rememberCookie).isNotNull();
        assertThat(rememberCookie.getValue()).doesNotContain("Agent1234");

        mockMvc.perform(get("/api/auth/me").cookie(rememberCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("测试用户_01"));
    }

    @Test
    void loginWithoutRememberMeDoesNotCreatePersistentCredential() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "account": "login@example.com",
                                  "password": "Agent1234",
                                  "rememberMe": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("SONORA_REMEMBER", 0));
    }

    @Test
    void registersCompleteUserThroughApiWithoutEmailVerification() throws Exception {
        users.deleteAll();
        MockHttpSession session = new MockHttpSession();
        captchaService.create(session);
        Object captchaState = session.getAttribute("REGISTER_IMAGE_CAPTCHA");
        String captcha = (String) ReflectionTestUtils.getField(captchaState, "code");

        mockMvc.perform(post("/api/auth/register").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "新用户_01",
                                  "email": "NEW@example.com",
                                  "phone": "13912345678",
                                  "password": "Secure123",
                                  "confirmPassword": "Secure123",
                                  "imageCaptcha": "%s"
                                }
                                """.formatted(captcha)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("new@example.com"));

        AppUser stored = users.findByEmailAndDeletedFalse("new@example.com").orElseThrow();
        assertThat(stored.getUsername()).isEqualTo("新用户_01");
        assertThat(stored.getEmail()).isEqualTo("new@example.com");
        assertThat(stored.getPhone()).isEqualTo("13912345678");
        assertThat(stored.getPassword()).startsWith("sha256$10000$").doesNotContain("Secure123");
        assertThat(stored.getCreatedAt()).isNotNull();
        assertThat(stored.getUpdatedAt()).isNotNull();
        assertThat(stored.isDeleted()).isFalse();
        assertThat(codes.count()).isZero();
        assertThat(session.getAttribute("REGISTER_IMAGE_CAPTCHA")).isNull();
    }

    @Test
    void registrationRequiresValidImageCaptcha() throws Exception {
        users.deleteAll();

        mockMvc.perform(post("/api/auth/register").with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "图形验证用户",
                                  "email": "captcha@example.com",
                                  "phone": "13712345678",
                                  "password": "Secure123",
                                  "confirmPassword": "Secure123",
                                  "imageCaptcha": "ABCDE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("图形验证码已失效，请刷新后重试"));

        assertThat(users.existsByEmail("captcha@example.com")).isFalse();
    }

    @Test
    void deletedUserCannotLogIn() throws Exception {
        user.markDeleted();
        users.saveAndFlush(user);
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"account\":\"login@example.com\",\"password\":\"Agent1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void protectsAgentApi() throws Exception {
        mockMvc.perform(post("/api/agent/chat").with(csrf())
                        .contentType("application/json").content("{\"message\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectsMusicApiAndExposesProviderStatusAfterLogin() throws Exception {
        mockMvc.perform(get("/api/music/status"))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"account\":\"测试用户_01\",\"password\":\"Agent1234\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/music/status").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready").value(false))
                .andExpect(jsonPath("$.data.providers.length()").value(4));
    }

    @Test
    void exposesCsrfTokenForVueClient() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"));
    }

    @Test
    void rejectsFeedbackThatDoesNotReferenceAnOwnedExposure() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"account\":\"测试用户_01\",\"password\":\"Agent1234\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/music/feedback").session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {
                                  "searchId": "11111111-1111-1111-1111-111111111111",
                                  "conversationId": "22222222-2222-2222-8222-222222222222",
                                  "action": "NOT_RELEVANT",
                                  "description": "我是说无畏契约的",
                                  "trackId": "qq:test",
                                  "resolvedEntityName": "VALORANT"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));

        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM music_knowledge_feedback
                 WHERE track_id = 'qq:test' AND resolved_entity_name = 'VALORANT'
                """, Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void allowsConfiguredVueOriginWithCredentials() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));

        mockMvc.perform(options("/api/auth/login")
                        .header("Origin", "http://127.0.0.1:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void mysqlSchemaContainsUserComments() {
        String tableComment = jdbcTemplate.queryForObject("""
                SELECT TABLE_COMMENT FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_user'
                """, String.class);
        String passwordComment = jdbcTemplate.queryForObject("""
                SELECT COLUMN_COMMENT FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'app_user' AND COLUMN_NAME = 'password'
                """, String.class);
        assertThat(tableComment).contains("用户信息表");
        assertThat(passwordComment).contains("SHA-256").contains("不保存明文");
    }
}
