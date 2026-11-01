package dev.relay.web;

import dev.relay.dto.Dtos.MessageEditRequest;
import dev.relay.dto.Dtos.MessageView;
import dev.relay.dto.Dtos.ReactionRequest;
import dev.relay.dto.Dtos.ReactionView;
import dev.relay.security.AuthUser;
import dev.relay.service.MessageService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {
    private final MessageService messageService;

    @GetMapping("/{id}/thread")
    public List<MessageView> thread(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return messageService.thread(id, user.id());
    }

    @PatchMapping("/{id}")
    public MessageView edit(@PathVariable Long id, @AuthenticationPrincipal AuthUser user,
                            @Valid @RequestBody MessageEditRequest request) {
        return messageService.edit(id, user.id(), request.content());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        messageService.delete(id, user.id());
    }

    @PostMapping("/{id}/reactions")
    public List<ReactionView> react(@PathVariable Long id, @AuthenticationPrincipal AuthUser user,
                                    @Valid @RequestBody ReactionRequest request) {
        return messageService.toggleReaction(id, user.id(), request.emoji());
    }
}
