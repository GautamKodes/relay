package dev.relay.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventSubscriber implements MessageListener {
    private final SimpMessagingTemplate template;
    private final ObjectMapper mapper;

    @Override
    public void onMessage(org.springframework.data.redis.connection.Message message, byte[] pattern) {
        try {
            JsonNode event = mapper.readTree(message.getBody());
            long channelId = event.get("channelId").asLong();
            template.convertAndSend("/topic/channel." + channelId, event);
        } catch (IOException | RuntimeException e) {
            log.warn("Dropped malformed event", e);
        }
    }
}
