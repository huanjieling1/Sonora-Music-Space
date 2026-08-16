package com.example.agent.service.impl;

import com.example.agent.config.MusicKnowledgeProperties;
import com.example.agent.model.bo.MusicEntityType;
import com.example.agent.model.bo.MusicUnderstandingBo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class MusicKnowledgeResolver {
    private static final Logger log = LoggerFactory.getLogger(MusicKnowledgeResolver.class);

    private final MusicKnowledgeRepository repository;
    private final List<PublicMusicKnowledgeProvider> publicProviders;
    private final MusicKnowledgeProperties properties;
    private final ExecutorService executor;
    private final ObjectMapper objectMapper;

    MusicKnowledgeResolver(MusicKnowledgeRepository repository,
                           List<PublicMusicKnowledgeProvider> publicProviders,
                           MusicKnowledgeProperties properties,
                           @Qualifier("musicProviderExecutor") ExecutorService executor,
                           ObjectMapper objectMapper) {
        this.repository = repository;
        this.publicProviders = publicProviders;
        this.properties = properties;
        this.executor = executor;
        this.objectMapper = objectMapper;
    }

    public MusicUnderstandingBo resolve(String description) {
        String cleaned = MusicTextNormalizer.cleanRequest(description);
        String normalizedDescription = MusicTextNormalizer.normalize(cleaned);
        Optional<MusicKnowledgeRepository.EntityRow> correction = repository.findCorrection(normalizedDescription);
        if (correction.isPresent()) {
            return localUnderstanding(correction.get(), List.of(cleaned), List.of("user"));
        }

        String candidate = MusicTextNormalizer.entityCandidate(cleaned);
        String normalizedCandidate = MusicTextNormalizer.normalize(candidate);
        Optional<MusicKnowledgeRepository.AliasRow> local = repository.aliases().stream()
                .filter(alias -> matches(normalizedDescription, normalizedCandidate, alias.normalizedAlias()))
                .findFirst();
        if (local.isPresent()) {
            var alias = local.get();
            var row = new MusicKnowledgeRepository.EntityRow(alias.entityId(), alias.canonicalName(),
                    alias.entityType(), alias.relatedTerms(), alias.confidence(), alias.source());
            return localUnderstanding(row, List.of(alias.aliasName()), List.of(alias.source()));
        }

        if (!shouldUsePublicKnowledge(candidate)) {
            return MusicUnderstandingBo.unresolved();
        }
        List<ExternalMusicEntity> external = lookupPublic(candidate, normalizedCandidate);
        return external.stream()
                .max(Comparator.comparingDouble(ExternalMusicEntity::confidence)
                        .thenComparing(entity -> sourcePriority(entity.source())))
                .map(this::externalUnderstanding)
                .orElseGet(MusicUnderstandingBo::unresolved);
    }

    private MusicUnderstandingBo localUnderstanding(MusicKnowledgeRepository.EntityRow row,
                                                    List<String> matchedAliases, List<String> sources) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>(repository.aliasesFor(row.id()));
        aliases.addAll(matchedAliases);
        LinkedHashSet<String> allSources = new LinkedHashSet<>(sources);
        allSources.add(row.source());
        return new MusicUnderstandingBo(row.id(), row.canonicalName(), row.entityType(), List.copyOf(aliases),
                row.confidence(), List.copyOf(allSources), row.relatedTerms(), repository.relationsFor(row.id()),
                repository.rejectedTracks(row.canonicalName()));
    }

    private MusicUnderstandingBo externalUnderstanding(ExternalMusicEntity entity) {
        return new MusicUnderstandingBo(null, entity.canonicalName(), entity.entityType(), entity.aliases(),
                entity.confidence(), List.of(entity.source()), List.of(), List.of(),
                repository.rejectedTracks(entity.canonicalName()));
    }

    private List<ExternalMusicEntity> lookupPublic(String candidate, String cacheKey) {
        List<CompletableFuture<Optional<ExternalMusicEntity>>> futures = publicProviders.stream()
                .filter(PublicMusicKnowledgeProvider::enabled)
                .map(provider -> CompletableFuture.supplyAsync(() -> cachedLookup(provider, candidate, cacheKey), executor)
                        .completeOnTimeout(Optional.empty(), properties.resolvedTimeoutSeconds(), TimeUnit.SECONDS))
                .toList();
        List<ExternalMusicEntity> results = new ArrayList<>();
        futures.forEach(future -> future.join().ifPresent(results::add));
        return results;
    }

    private Optional<ExternalMusicEntity> cachedLookup(PublicMusicKnowledgeProvider provider,
                                                       String candidate, String cacheKey) {
        Optional<MusicKnowledgeRepository.CacheRow> cached = repository.cache(cacheKey, provider.id());
        if (cached.isPresent()) {
            if (!cached.get().successful() || !StringUtils.hasText(cached.get().payload())) {
                return Optional.empty();
            }
            try {
                return Optional.of(objectMapper.readValue(cached.get().payload(), ExternalMusicEntity.class));
            } catch (JsonProcessingException exception) {
                log.warn("Music knowledge cache {} could not be decoded", provider.id());
            }
        }
        long startedAt = System.nanoTime();
        try {
            Optional<ExternalMusicEntity> result = provider.lookup(candidate);
            String payload = result.isPresent() ? objectMapper.writeValueAsString(result.get()) : null;
            int days = result.isPresent() ? properties.resolvedSuccessCacheDays() : properties.resolvedFailureCacheDays();
            repository.saveCache(cacheKey, provider.id(), result.isPresent(), payload, LocalDateTime.now().plusDays(days));
            log.info("Music knowledge provider {} completed in {}ms with match={}", provider.id(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt), result.isPresent());
            return result;
        } catch (RuntimeException | JsonProcessingException exception) {
            repository.saveCache(cacheKey, provider.id(), false, null,
                    LocalDateTime.now().plusDays(properties.resolvedFailureCacheDays()));
            log.warn("Music knowledge provider {} failed in {}ms: {}", provider.id(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
                    exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static boolean matches(String normalizedDescription, String normalizedCandidate, String alias) {
        if (!StringUtils.hasText(alias)) return false;
        if (alias.length() <= 1) return normalizedCandidate.equals(alias);
        return normalizedCandidate.equals(alias) || normalizedDescription.contains(alias);
    }

    private static boolean shouldUsePublicKnowledge(String candidate) {
        if (!StringUtils.hasText(candidate) || candidate.length() > 80) return false;
        String normalized = MusicTextNormalizer.normalize(candidate);
        return normalized.length() >= 2 && !candidate.matches(".*(?:适合|氛围|风格|类型|推荐|写代码|学习|运动|睡眠).*" );
    }

    private static int sourcePriority(String source) {
        return "wikidata".equals(source) ? 2 : 1;
    }
}
