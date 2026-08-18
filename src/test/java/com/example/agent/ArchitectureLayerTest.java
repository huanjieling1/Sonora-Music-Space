package com.example.agent;

import com.example.agent.agent.capability.AgentCapabilityAgent;
import com.example.agent.agent.capability.AgentCapabilityDefinition;
import com.example.agent.agent.capability.AgentCapabilityGateway;
import com.example.agent.agent.capability.AgentScopeDecision;
import com.example.agent.agent.capability.AgentScopeResponseAgent;
import com.example.agent.agent.capability.AgentToolAuthorizer;
import com.example.agent.agent.contract.MusicAgentRoute;
import com.example.agent.agent.contract.MusicAgentTurn;
import com.example.agent.agent.contract.MusicAgentWorkflowState;
import com.example.agent.agent.contract.MusicExecutionResult;
import com.example.agent.agent.contract.MusicFollowUpOutcome;
import com.example.agent.agent.contract.MusicIntentDraft;
import com.example.agent.agent.contract.MusicIntentUnderstanding;
import com.example.agent.agent.contract.MusicPreferenceChange;
import com.example.agent.agent.contract.MusicTurnPlan;
import com.example.agent.agent.contract.MusicTaskEvaluation;
import com.example.agent.agent.contract.MusicWorkflowPlan;
import com.example.agent.agent.contract.MusicWorkflowSnapshot;
import com.example.agent.agent.contract.MusicWorkflowTaskSnapshot;
import com.example.agent.agent.contract.MusicWorkflowTaskSpec;
import com.example.agent.agent.contract.ProfileAgentResult;
import com.example.agent.agent.contract.UserTasteContext;
import com.example.agent.agent.conversation.MusicConversationAgentService;
import com.example.agent.agent.execution.MusicExecutionAgent;
import com.example.agent.agent.execution.MusicToolExecutor;
import com.example.agent.agent.feedback.MusicRecommendationFollowUpAgent;
import com.example.agent.agent.intent.LlmMusicFollowUpPlanner;
import com.example.agent.agent.intent.MusicContextualIntentAgent;
import com.example.agent.agent.intent.MusicIntentAgent;
import com.example.agent.agent.profile.DefaultMusicProfileContextReader;
import com.example.agent.agent.profile.LlmMusicProfileNarrator;
import com.example.agent.agent.profile.MusicProfileAgent;
import com.example.agent.agent.profile.MusicRecommendationProfileAgent;
import com.example.agent.agent.response.MusicResponseAgent;
import com.example.agent.agent.response.AgentResponseGuard;
import com.example.agent.controller.AgentController;
import com.example.agent.controller.AuthController;
import com.example.agent.controller.MusicController;
import com.example.agent.controller.MusicPlaylistController;
import com.example.agent.service.AgentChatService;
import com.example.agent.service.CaptchaService;
import com.example.agent.service.ConversationService;
import com.example.agent.service.EmailVerificationService;
import com.example.agent.service.MusicQueryPlanner;
import com.example.agent.service.MusicRecommendationService;
import com.example.agent.service.MusicPlaylistService;
import com.example.agent.service.MusicCatalogProvider;
import com.example.agent.service.RegistrationService;
import com.example.agent.service.UserQueryService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureLayerTest {
    private static final String ENTITY_PACKAGE = "com.example.agent.model.entity";
    private static final String REPOSITORY_PACKAGE = "com.example.agent.repository";

    @Test
    void controllersDoNotDependOnRepositoriesOrEntities() {
        Stream.of(AuthController.class, AgentController.class, MusicController.class, MusicPlaylistController.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .map(Field::getType)
                .map(Class::getPackageName)
                .forEach(packageName -> assertThat(packageName)
                        .doesNotStartWith(REPOSITORY_PACKAGE)
                        .doesNotStartWith(ENTITY_PACKAGE));
    }

    @Test
    void serviceContractsExposeOnlyApplicationAndBusinessModels() {
        Stream.of(RegistrationService.class, UserQueryService.class, ConversationService.class,
                        AgentChatService.class, CaptchaService.class, EmailVerificationService.class,
                        MusicQueryPlanner.class, MusicRecommendationService.class, MusicCatalogProvider.class,
                        MusicPlaylistService.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .flatMap(ArchitectureLayerTest::methodTypes)
                .map(Class::getPackageName)
                .forEach(packageName -> assertThat(packageName)
                        .doesNotStartWith("com.example.agent.controller")
                        .doesNotStartWith("com.example.agent.model.dto")
                        .doesNotStartWith("com.example.agent.model.vo")
                        .doesNotStartWith(REPOSITORY_PACKAGE)
                        .doesNotStartWith(ENTITY_PACKAGE));
    }

    @Test
    void onlyExecutionBoundaryDependsOnMusicTools() {
        Stream.of(MusicIntentAgent.class, MusicProfileAgent.class, MusicRecommendationProfileAgent.class,
                        MusicContextualIntentAgent.class, LlmMusicFollowUpPlanner.class,
                        MusicRecommendationFollowUpAgent.class,
                        AgentCapabilityAgent.class, AgentCapabilityGateway.class,
                        AgentScopeResponseAgent.class, AgentToolAuthorizer.class, AgentResponseGuard.class,
                        DefaultMusicProfileContextReader.class,
                        LlmMusicProfileNarrator.class, MusicResponseAgent.class, MusicConversationAgentService.class)
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .map(Field::getType)
                .map(Class::getPackageName)
                .forEach(packageName -> assertThat(packageName)
                        .doesNotStartWith("com.example.agent.tools")
                        .doesNotStartWith(REPOSITORY_PACKAGE)
                        .doesNotStartWith(ENTITY_PACKAGE));

        assertThat(Arrays.stream(MusicToolExecutor.class.getDeclaredFields())
                .map(Field::getType).map(Class::getName))
                .anyMatch("com.example.agent.tools.MusicAgentTools"::equals);

        assertThat(Arrays.stream(MusicExecutionAgent.class.getDeclaredFields())
                .map(Field::getType).map(Class::getPackageName))
                .allMatch(value -> value.startsWith("com.example.agent.agent.execution"));
    }

    @Test
    void multiAgentContractsRemainFrameworkAndInfrastructureIndependent() {
        Stream.of(MusicAgentRoute.class, MusicAgentTurn.class, MusicAgentWorkflowState.class,
                        MusicExecutionResult.class, MusicIntentDraft.class, MusicIntentUnderstanding.class,
                        MusicTurnPlan.class, MusicPreferenceChange.class,
                        MusicFollowUpOutcome.class, AgentCapabilityDefinition.class, AgentScopeDecision.class,
                        MusicTaskEvaluation.class, MusicWorkflowPlan.class, MusicWorkflowSnapshot.class,
                        MusicWorkflowTaskSnapshot.class, MusicWorkflowTaskSpec.class,
                        ProfileAgentResult.class, UserTasteContext.class,
                        UserTasteContext.Signal.class, UserTasteContext.RankedItem.class)
                .flatMap(type -> Stream.concat(
                        Arrays.stream(type.getDeclaredFields()).flatMap(field -> rawTypes(field.getGenericType())),
                        Arrays.stream(type.getDeclaredMethods()).flatMap(ArchitectureLayerTest::methodTypes)))
                .map(Class::getPackageName)
                .forEach(packageName -> assertThat(packageName)
                        .doesNotStartWith("org.springframework")
                        .doesNotStartWith("dev.langchain4j")
                        .doesNotStartWith("com.example.agent.controller")
                        .doesNotStartWith(REPOSITORY_PACKAGE)
                        .doesNotStartWith(ENTITY_PACKAGE));
    }

    private static Stream<Class<?>> methodTypes(Method method) {
        return Stream.concat(rawTypes(method.getGenericReturnType()),
                Arrays.stream(method.getGenericParameterTypes()).flatMap(ArchitectureLayerTest::rawTypes));
    }

    private static Stream<Class<?>> rawTypes(Type type) {
        if (type instanceof Class<?> value) return Stream.of(value);
        if (type instanceof ParameterizedType value) {
            return Stream.concat(rawTypes(value.getRawType()),
                    Arrays.stream(value.getActualTypeArguments()).flatMap(ArchitectureLayerTest::rawTypes));
        }
        if (type instanceof GenericArrayType value) return rawTypes(value.getGenericComponentType());
        if (type instanceof WildcardType value) {
            return Stream.concat(Arrays.stream(value.getUpperBounds()), Arrays.stream(value.getLowerBounds()))
                    .flatMap(ArchitectureLayerTest::rawTypes);
        }
        if (type instanceof TypeVariable<?> value) {
            return Arrays.stream(value.getBounds()).flatMap(ArchitectureLayerTest::rawTypes);
        }
        return Stream.empty();
    }
}
