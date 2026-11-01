package dev.relay.web;

import dev.relay.dto.Dtos.ChannelCreateRequest;
import dev.relay.dto.Dtos.ChannelView;
import dev.relay.dto.Dtos.JoinRequest;
import dev.relay.dto.Dtos.MessageView;
import dev.relay.dto.Dtos.UserRef;
import dev.relay.dto.Dtos.UserView;
import dev.relay.dto.Dtos.WorkspaceCreateRequest;
import dev.relay.dto.Dtos.WorkspaceView;
import dev.relay.security.AuthUser;
import dev.relay.service.ChannelService;
import dev.relay.service.MessageService;
import dev.relay.service.PresenceService;
import dev.relay.service.WorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {
    private final WorkspaceService workspaceService;
    private final ChannelService channelService;
    private final MessageService messageService;
    private final PresenceService presenceService;

    @GetMapping
    public List<WorkspaceView> mine(@AuthenticationPrincipal AuthUser user) {
        return workspaceService.mine(user.id());
    }

    @PostMapping
    public WorkspaceView create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody WorkspaceCreateRequest request) {
        return workspaceService.create(user.id(), request.name());
    }

    @PostMapping("/join")
    public WorkspaceView join(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody JoinRequest request) {
        return workspaceService.join(user.id(), request.inviteCode());
    }

    @GetMapping("/{id}/people")
    public List<UserView> people(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return workspaceService.people(id, user.id());
    }

    @GetMapping("/{id}/channels")
    public List<ChannelView> channels(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return channelService.list(id, user.id());
    }

    @PostMapping("/{id}/channels")
    public ChannelView createChannel(@PathVariable Long id, @AuthenticationPrincipal AuthUser user,
                                     @Valid @RequestBody ChannelCreateRequest request) {
        return channelService.create(id, user.id(), request);
    }

    @PostMapping("/{id}/dms")
    public ChannelView openDm(@PathVariable Long id, @AuthenticationPrincipal AuthUser user,
                              @Valid @RequestBody UserRef request) {
        return channelService.openDm(id, user.id(), request.userId());
    }

    @GetMapping("/{id}/presence")
    public Set<Long> presence(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        List<Long> ids = workspaceService.people(id, user.id()).stream().map(UserView::id).toList();
        return presenceService.online(ids);
    }

    @GetMapping("/{id}/search")
    public List<MessageView> search(@PathVariable Long id, @RequestParam String q, @AuthenticationPrincipal AuthUser user) {
        return messageService.search(id, user.id(), q);
    }
}
