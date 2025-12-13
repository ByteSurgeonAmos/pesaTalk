package com.pesatalk.service;

import com.pesatalk.service.intent.ConversationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConversationStateService {

    private static final Logger log = LoggerFactory.getLogger(ConversationStateService.class);
    private static final String CONVERSATION_KEY_PREFIX = "conversation:";
    private static final Duration CONVERSATION_TTL = Duration.ofMinutes(30);

    private final RedisTemplate<String, Object> redisTemplate;

    public ConversationStateService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public ConversationContext getOrCreate(String whatsAppId, UUID userId) {
        String key = buildKey(whatsAppId);

        ConversationContext context = (ConversationContext) redisTemplate.opsForValue().get(key);

        if (context == null || context.isExpired()) {
            context = new ConversationContext(userId);
            save(whatsAppId, context);
            log.debug("Created new conversation context for user: {}", whatsAppId);
        } else {
            context.touch();
            save(whatsAppId, context);
        }

        return context;
    }

    public Optional<ConversationContext> get(String whatsAppId) {
        String key = buildKey(whatsAppId);
        ConversationContext context = (ConversationContext) redisTemplate.opsForValue().get(key);

        if (context != null && !context.isExpired()) {
            return Optional.of(context);
        }

        return Optional.empty();
    }

    public void save(String whatsAppId, ConversationContext context) {
        String key = buildKey(whatsAppId);
        redisTemplate.opsForValue().set(key, context, CONVERSATION_TTL);
    }

    public void clear(String whatsAppId) {
        String key = buildKey(whatsAppId);
        redisTemplate.delete(key);
        log.debug("Cleared conversation context for: {}", whatsAppId);
    }

    public void reset(String whatsAppId) {
        get(whatsAppId).ifPresent(context -> {
            context.reset();
            save(whatsAppId, context);
            log.debug("Reset conversation context for: {}", whatsAppId);
        });
    }

    private String buildKey(String whatsAppId) {
        return CONVERSATION_KEY_PREFIX + whatsAppId;
    }
}
