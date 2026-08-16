package com.example.agent.tools;

import com.example.agent.model.bo.AgentActionBo;
import com.example.agent.model.bo.ConversationMemoryId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Keeps authenticated request context out of model-controlled tool arguments. */
@Component
public class AgentActionContext {
    private final ThreadLocal<State> current = new ThreadLocal<>();

    public void begin(ConversationMemoryId memoryId) {
        current.set(new State(memoryId, new ArrayList<>()));
    }

    public ConversationMemoryId memoryId() {
        State state = requireState();
        return state.memoryId();
    }

    public void add(AgentActionBo action) {
        requireState().actions().add(action);
    }

    public List<AgentActionBo> actions() {
        return List.copyOf(requireState().actions());
    }

    public void clear() {
        current.remove();
    }

    private State requireState() {
        State state = current.get();
        if (state == null) {
            throw new IllegalStateException("Agent 工具缺少当前会话上下文");
        }
        return state;
    }

    private record State(ConversationMemoryId memoryId, List<AgentActionBo> actions) {
    }
}
