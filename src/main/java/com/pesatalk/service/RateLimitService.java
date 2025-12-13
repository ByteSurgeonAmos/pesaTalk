package com.pesatalk.service;

import com.pesatalk.exception.RateLimitException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private final Map<String, Bucket> localBuckets = new ConcurrentHashMap<>();
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${rate-limit.burst-capacity:10}")
    private int burstCapacity;

    @Value("${rate-limit.transaction.per-user-per-day:50}")
    private int transactionsPerDay;

    public RateLimitService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void checkMessageRateLimit(String whatsAppId) {
        String key = "rate:msg:" + whatsAppId;
        Bucket bucket = localBuckets.computeIfAbsent(key, this::createMessageBucket);

        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for user: {}", whatsAppId);
            throw new RateLimitException(60);
        }
    }

    public void checkTransactionRateLimit(String whatsAppId) {
        String key = "rate:txn:" + whatsAppId;
        Bucket bucket = localBuckets.computeIfAbsent(key, this::createTransactionBucket);

        if (!bucket.tryConsume(1)) {
            log.warn("Transaction rate limit exceeded for user: {}", whatsAppId);
            throw new RateLimitException(3600); // 1 hour
        }
    }

    public boolean isWithinDailyTransactionLimit(String userId, int currentCount) {
        return currentCount < transactionsPerDay;
    }

    public void recordTransaction(String userId) {
        String key = "daily:txn:" + userId;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count != null && count == 1) {
            // First transaction of the day - set expiry to end of day
            redisTemplate.expire(key, Duration.ofHours(24));
        }
    }

    public int getDailyTransactionCount(String userId) {
        String key = "daily:txn:" + userId;
        Object count = redisTemplate.opsForValue().get(key);
        return count != null ? Integer.parseInt(count.toString()) : 0;
    }

    private Bucket createMessageBucket(String key) {
        Bandwidth limit = Bandwidth.classic(
            requestsPerMinute,
            Refill.greedy(requestsPerMinute, Duration.ofMinutes(1))
        );

        Bandwidth burst = Bandwidth.classic(
            burstCapacity,
            Refill.intervally(burstCapacity, Duration.ofSeconds(10))
        );

        return Bucket.builder()
            .addLimit(limit)
            .addLimit(burst)
            .build();
    }

    private Bucket createTransactionBucket(String key) {
        // Allow 5 transactions per minute, max 50 per day
        Bandwidth perMinute = Bandwidth.classic(
            5,
            Refill.greedy(5, Duration.ofMinutes(1))
        );

        Bandwidth perDay = Bandwidth.classic(
            transactionsPerDay,
            Refill.intervally(transactionsPerDay, Duration.ofDays(1))
        );

        return Bucket.builder()
            .addLimit(perMinute)
            .addLimit(perDay)
            .build();
    }

    public void clearBuckets() {
        localBuckets.clear();
    }

    public RateLimitStatus getStatus(String whatsAppId) {
        String msgKey = "rate:msg:" + whatsAppId;
        String txnKey = "rate:txn:" + whatsAppId;

        Bucket msgBucket = localBuckets.get(msgKey);
        Bucket txnBucket = localBuckets.get(txnKey);

        return new RateLimitStatus(
            msgBucket != null ? msgBucket.getAvailableTokens() : requestsPerMinute,
            txnBucket != null ? txnBucket.getAvailableTokens() : transactionsPerDay,
            getDailyTransactionCount(whatsAppId)
        );
    }

    public record RateLimitStatus(
        long availableMessageTokens,
        long availableTransactionTokens,
        int dailyTransactionCount
    ) {}
}
