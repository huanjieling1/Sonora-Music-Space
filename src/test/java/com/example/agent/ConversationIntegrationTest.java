package com.example.agent;

import com.example.agent.constant.enums.ChatMessageRole;
import com.example.agent.exception.AppException;
import com.example.agent.model.bo.ConversationMemoryId;
import com.example.agent.model.entity.AgentChatMessage;
import com.example.agent.model.entity.AppUser;
import com.example.agent.repository.AgentChatMessageRepository;
import com.example.agent.repository.AgentConversationRepository;
import com.example.agent.repository.AppUserRepository;
import com.example.agent.repository.EmailVerificationCodeRepository;
import com.example.agent.service.impl.ConversationStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ConversationIntegrationTest {
    @Autowired ConversationStore store;
    @Autowired AgentConversationRepository conversations;
    @Autowired AgentChatMessageRepository messages;
    @Autowired AppUserRepository users;
    @Autowired EmailVerificationCodeRepository codes;
    @Autowired PasswordEncoder passwordEncoder;

    private AppUser firstUser;
    private AppUser secondUser;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        firstUser = users.saveAndFlush(AppUser.register("会话用户_01", "session1@example.com", "13612345678",
                passwordEncoder.encode("Agent1234")));
        secondUser = users.saveAndFlush(AppUser.register("会话用户_02", "session2@example.com", "13512345678",
                passwordEncoder.encode("Agent1234")));
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    @Test
    void isolatesConversationListsAndHistoryByUser() {
        var firstConversation = store.create(firstUser.getId());
        var secondConversation = store.create(secondUser.getId());
        store.saveExchange(firstUser.getId(), firstConversation.getConversationId(),
                "设计一个 Router Agent", "可以先定义路由契约。");
        store.saveExchange(secondUser.getId(), secondConversation.getConversationId(),
                "设计一个 Worker Agent", "可以先定义工作任务。");

        assertThat(store.list(firstUser.getId()))
                .extracting(conversation -> conversation.getConversationId())
                .containsExactly(firstConversation.getConversationId());
        assertThat(store.list(secondUser.getId()))
                .extracting(conversation -> conversation.getConversationId())
                .containsExactly(secondConversation.getConversationId());
        assertThatThrownBy(() -> store.history(secondUser.getId(), firstConversation.getConversationId()))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void persistsOrderedMessagesAndRestoresOnlyOwnedMemory() {
        var conversation = store.create(firstUser.getId());
        store.saveExchange(firstUser.getId(), conversation.getConversationId(),
                "这是一个用于生成侧栏标题的较长问题", "这是 Agent 的回答");

        var history = store.history(firstUser.getId(), conversation.getConversationId());
        assertThat(history).extracting(AgentChatMessage::getRole)
                .containsExactly(ChatMessageRole.USER, ChatMessageRole.ASSISTANT);
        assertThat(store.list(firstUser.getId()).get(0).getTitle()).isEqualTo("这是一个用于生成侧栏标题的较长问题");

        var memory = store.loadMemory(
                new ConversationMemoryId(firstUser.getId(), conversation.getConversationId()), 20);
        assertThat(memory).hasSize(2);
        assertThat(memory.get(0)).isInstanceOf(UserMessage.class);
        assertThat(memory.get(1)).isInstanceOf(AiMessage.class);
    }

    @Test
    void softDeletesOnlyOwnedConversationAndHidesItsHistory() {
        var firstConversation = store.create(firstUser.getId());
        store.saveExchange(firstUser.getId(), firstConversation.getConversationId(), "播放雨爱", "正在播放");

        assertThatThrownBy(() -> store.delete(secondUser.getId(), firstConversation.getConversationId()))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));

        store.delete(firstUser.getId(), firstConversation.getConversationId());

        assertThat(store.list(firstUser.getId())).isEmpty();
        assertThatThrownBy(() -> store.history(firstUser.getId(), firstConversation.getConversationId()))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(messages.findAllByConversationIdOrderByIdAsc(firstConversation.getId())).hasSize(2);
        assertThat(conversations.findById(firstConversation.getId()).orElseThrow().isDeleted()).isTrue();
    }

    private void cleanDatabase() {
        messages.deleteAll();
        conversations.deleteAll();
        codes.deleteAll();
        users.deleteAll();
    }
}
