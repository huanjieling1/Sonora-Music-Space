package com.example.agent.model.dto.agent;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsConversationIdAndMessage() {
        var request = new ChatRequest(UUID.randomUUID(), "请设计一个 Router Agent");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void requiresConversationId() {
        var request = new ChatRequest(null, "hello");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("conversationId");
    }
}
