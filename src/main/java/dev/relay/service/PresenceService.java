package dev.relay.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PresenceService {
    private static final Duration TTL = Duration.ofSeconds(45);

    private final StringRedisTemplate redis;

    public void heartbeat(Long userId) {
        redis.opsForValue().set(key(userId), "1", TTL);
    }

    public Set<Long> online(Collection<Long> userIds) {
        Set<Long> result = new HashSet<>();
        if (userIds.isEmpty()) {
            return result;
        }
        List<Long> ids = new ArrayList<>(userIds);
        List<String> values = redis.opsForValue().multiGet(ids.stream().map(this::key).toList());
        if (values == null) {
            return result;
        }
        for (int i = 0; i < ids.size(); i++) {
            if (values.get(i) != null) {
                result.add(ids.get(i));
            }
        }
        return result;
    }

    private String key(Long userId) {
        return "presence:" + userId;
    }
}
