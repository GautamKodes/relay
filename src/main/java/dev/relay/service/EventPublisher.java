package dev.relay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.relay.dto.Dtos.ChatEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class EventPublisher {
    public static final String TOPIC = "relay.events";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;

    public void publish(String type, Long channelId, Object data) {
        String payload;
        try {
            payload = mapper.writeValueAsString(new ChatEvent(type, channelId, data));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        Runnable send = () -> redis.convertAndSend(TOPIC, payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
