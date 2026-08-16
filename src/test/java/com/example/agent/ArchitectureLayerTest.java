package com.example.agent;

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
import java.lang.reflect.Method;
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

    private static Stream<Class<?>> methodTypes(Method method) {
        return Stream.concat(Stream.of(method.getReturnType()), Arrays.stream(method.getParameterTypes()));
    }
}
