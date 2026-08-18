package com.example.agent;

import com.example.agent.config.AgentProperties;
import com.example.agent.config.MusicCatalogProperties;
import com.example.agent.config.MusicKnowledgeProperties;
import com.example.agent.config.MultiAgentProperties;
import com.example.agent.config.MusicPersonalizationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({AgentProperties.class, MusicCatalogProperties.class, MusicKnowledgeProperties.class,
        MusicPersonalizationProperties.class, MultiAgentProperties.class})
public class AgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
