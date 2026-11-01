package dev.relay.web;

import dev.relay.dto.Dtos.MessageCreateRequest;
import dev.relay.security.AuthUser;
import dev.relay.service.MessageService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatSocketController {
    private final MessageService messageService;

    @MessageMapping("/channels/{channelId}/messages")
    public void send(@DestinationVariable Long channelId, @Payload @Valid MessageCreateRequest request, Principal principal) {
        messageService.post(channelId, userId(principal), request);
    }

    @MessageMapping("/channels/{channelId}/typing")
    public void typing(@DestinationVariable Long channelId, Principal principal) {
        messageService.typing(channelId, userId(principal));
    }

    private Long userId(Principal principal) {
        return ((AuthUser) ((Authentication) principal).getPrincipal()).id();
    }
}
