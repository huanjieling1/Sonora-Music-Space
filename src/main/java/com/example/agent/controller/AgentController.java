package com.example.agent.controller;

import com.example.agent.model.ao.ChatAo;
import com.example.agent.model.dto.agent.ChatRequest;
import com.example.agent.model.vo.agent.ChatVo;
import com.example.agent.model.vo.agent.ConversationVo;
import com.example.agent.model.vo.agent.MessageVo;
import com.example.agent.model.vo.common.ApiResponse;
import com.example.agent.security.AppUserPrincipal;
import com.example.agent.service.ConversationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent")
public class AgentController {
    private final ConversationService conversationService;

    public AgentController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping("/conversations")
    public ApiResponse<ConversationVo> createConversation(
            @AuthenticationPrincipal AppUserPrincipal user) {
        return ApiResponse.ok("会话已创建", ConversationVo.from(conversationService.create(user.id())));
    }

    @GetMapping("/conversations")
    public ApiResponse<List<ConversationVo>> conversations(
            @AuthenticationPrincipal AppUserPrincipal user) {
        List<ConversationVo> result = conversationService.list(user.id()).stream()
                .map(ConversationVo::from)
                .toList();
        return ApiResponse.ok("获取成功", result);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ApiResponse<List<MessageVo>> messages(@PathVariable UUID conversationId,
                                                 @AuthenticationPrincipal AppUserPrincipal user) {
        List<MessageVo> result = conversationService.history(user.id(), conversationId).stream()
                .map(MessageVo::from)
                .toList();
        return ApiResponse.ok("获取成功", result);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ApiResponse<Map<String, Boolean>> deleteConversation(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal AppUserPrincipal user) {
        conversationService.delete(user.id(), conversationId);
        return ApiResponse.ok("对话已删除", Map.of("deleted", true));
    }

    @PostMapping("/chat")
    public ApiResponse<ChatVo> chat(@Valid @RequestBody ChatRequest request,
                                    @AuthenticationPrincipal AppUserPrincipal user) {
        var command = new ChatAo(user.id(), request.conversationId(), request.message().trim());
        return ApiResponse.ok("回复成功", ChatVo.from(conversationService.chat(command)));
    }
}
