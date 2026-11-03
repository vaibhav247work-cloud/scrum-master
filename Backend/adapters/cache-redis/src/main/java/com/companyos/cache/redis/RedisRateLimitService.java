package com.companyos.cache.redis;

import com.companyos.security.contract.RateLimitDecision;
import com.companyos.security.contract.RateLimitRule;
import com.companyos.security.contract.RateLimitService;
import com.companyos.security.contract.RateLimitUnavailableException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class RedisRateLimitService implements RateLimitService {
    private static final String KEY_PREFIX = "company-os:ratelimit:v1:";
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            for i = 1, #KEYS do
                local current = tonumber(redis.call('GET', KEYS[i]) or '0')
                local limit = tonumber(ARGV[(i - 1) * 2 + 1])
                if current >= limit then
                    return 0
                end
            end
            for i = 1, #KEYS do
                local current = redis.call('INCR', KEYS[i])
                if current == 1 then
                    redis.call('EXPIRE', KEYS[i], ARGV[(i - 1) * 2 + 2])
                end
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisRateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public RateLimitDecision consume(List<RateLimitRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return new RateLimitDecision(true, 0);
        }
        try {
            List<String> keys = rules.stream().map(rule -> KEY_PREFIX + sha256(rule.key())).toList();
            List<String> arguments = new ArrayList<>();
            rules.forEach(rule -> {
                arguments.add(Integer.toString(rule.limit()));
                arguments.add(Long.toString(seconds(rule.window())));
            });
            Long result = redis.execute(CONSUME_SCRIPT, keys, arguments.toArray());
            if (result == null) throw new IllegalStateException("Rate-limit script returned no result");
            if (result == 1L) return new RateLimitDecision(true, 0);
            long retryAfter = rules.stream()
                    .map(rule -> redis.getExpire(KEY_PREFIX + sha256(rule.key())))
                    .filter(value -> value != null && value > 0)
                    .min(Long::compareTo)
                    .orElse(seconds(rules.getFirst().window()));
            return new RateLimitDecision(false, retryAfter);
        } catch (RateLimitUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RateLimitUnavailableException(exception);
        }
    }

    private static long seconds(Duration duration) {
        return Math.max(1, duration.toSeconds());
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
