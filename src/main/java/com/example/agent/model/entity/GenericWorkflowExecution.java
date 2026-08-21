package com.example.agent.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Durable generic DAG workflow snapshot; profile payloads are never stored here. */
@Entity
@Table(name = "generic_workflow_execution")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GenericWorkflowExecution {
    @Id
    @Column(name = "workflow_id", nullable = false, length = 36)
    private String workflowId;

    @Column(name = "principal_id", nullable = false, length = 128)
    private String principalId;

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    @Column(name = "plan_json", nullable = false, columnDefinition = "LONGTEXT")
    private String planJson;

    @Column(name = "state_json", nullable = false, columnDefinition = "LONGTEXT")
    private String stateJson;

    @Column(name = "resume_context_json", columnDefinition = "LONGTEXT")
    private String resumeContextJson;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public static GenericWorkflowExecution create(String workflowId, String principalId,
                                                   String conversationId, String planJson,
                                                   String stateJson, String status) {
        GenericWorkflowExecution value = new GenericWorkflowExecution();
        value.workflowId = workflowId;
        value.principalId = principalId;
        value.conversationId = conversationId == null ? "" : conversationId;
        value.planJson = planJson;
        value.stateJson = stateJson;
        value.status = status;
        return value;
    }

    public void update(String planJson, String stateJson, String status) {
        this.planJson = planJson;
        this.stateJson = stateJson;
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateResumeContext(String resumeContextJson) {
        this.resumeContextJson = resumeContextJson;
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
