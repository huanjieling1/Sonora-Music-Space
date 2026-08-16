package com.example.agent.model.bo;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemoryIdTest {
    private static final UUID FIRST_CONVERSATION = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SECOND_CONVERSATION = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void separatesDifferentConversationsForTheSameUser() {
        String first = new ConversationMemoryId(7L, FIRST_CONVERSATION).value();
        String second = new ConversationMemoryId(7L, SECOND_CONVERSATION).value();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void separatesDifferentUsersEvenWithTheSameConversationId() {
        String firstUser = new ConversationMemoryId(7L, FIRST_CONVERSATION).value();
        String secondUser = new ConversationMemoryId(8L, FIRST_CONVERSATION).value();

        assertThat(firstUser).isNotEqualTo(secondUser);
    }

    @Test
    void keepsTheMemoryIdStableForTheSameUserAndConversation() {
        assertThat(new ConversationMemoryId(7L, FIRST_CONVERSATION).value())
                .isEqualTo(new ConversationMemoryId(7L, FIRST_CONVERSATION).value());
    }
}
