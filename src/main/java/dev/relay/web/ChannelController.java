package dev.relay.web;

import dev.relay.dto.Dtos.MessageCreateRequest;
import dev.relay.dto.Dtos.MessagePage;
import dev.relay.dto.Dtos.MessageView;
import dev.relay.dto.Dtos.UserRef;
import dev.relay.security.AuthUser;
import dev.relay.service.ChannelService;
import dev.relay.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channels")
@RequiredArgsConstructor
public class ChannelController {
    private final ChannelService channelService;
    private final MessageService messageService;

    @GetMapping("/{id}/messages")
    public MessagePage history(@PathVariable Long id, @RequestParam(required = false) Long before,
                               @RequestParam(defaultValue = "50") int limit, @AuthenticationPrincipal AuthUser user) {
        return messageService.history(id, user.id(), before, limit);
    }

    @PostMapping("/{id}/messages")
    public MessageView post(@PathVariable Long id, @AuthenticationPrincipal AuthUser user,
                            @Valid @RequestBody MessageCreateRequest request) {
        return messageService.post(id, user.id(), request);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        channelService.markRead(id, user.id());
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void invite(@PathVariable Long id, @AuthenticationPrincipal AuthUser user,
                       @Valid @RequestBody UserRef request) {
        channelService.invite(id, user.id(), request.userId());
    }
}
