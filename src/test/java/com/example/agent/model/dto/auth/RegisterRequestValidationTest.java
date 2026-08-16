package com.example.agent.model.dto.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsCompleteRegistrationData() {
        var request = new RegisterRequest("开发者_01", "USER@example.com", "13812345678",
                "Agent1234", "Agent1234", "ABCDE");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsPureNumericUsernameInvalidPhoneAndWeakPassword() {
        var request = new RegisterRequest("123456", "user@example.com", "12812345678",
                "password", "password", "ABCDE");
        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("username", "phone", "password");
    }
}
