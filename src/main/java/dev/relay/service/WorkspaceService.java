package dev.relay.service;

import dev.relay.domain.Channel;
import dev.relay.domain.ChannelType;
import dev.relay.domain.Workspace;
import dev.relay.domain.WorkspaceMember;
import dev.relay.domain.WorkspaceRole;
import dev.relay.dto.Dtos.UserView;
import dev.relay.dto.Dtos.WorkspaceView;
import dev.relay.repo.UserRepository;
import dev.relay.repo.WorkspaceMemberRepository;
import dev.relay.repo.WorkspaceRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final UserRepository users;
    private final ChannelService channelService;

    @Transactional
    public WorkspaceView create(Long userId, String name) {
        Workspace workspace = new Workspace();
        workspace.setName(name.trim());
        workspace.setInviteCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        workspace.setOwnerId(userId);
        workspaces.save(workspace);
        addMember(workspace.getId(), userId, WorkspaceRole.OWNER);
        Channel general = channelService.createChannel(workspace.getId(), "general", ChannelType.PUBLIC, null, userId);
        channelService.addMember(general.getId(), userId);
        return view(workspace, WorkspaceRole.OWNER);
    }

    @Transactional
    public WorkspaceView join(Long userId, String inviteCode) {
        Workspace workspace = workspaces.findByInviteCode(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("That invite code is not valid"));
        WorkspaceMember member = members.findByWorkspaceIdAndUserId(workspace.getId(), userId).orElse(null);
        if (member == null) {
            member = addMember(workspace.getId(), userId, WorkspaceRole.MEMBER);
            channelService.joinPublicChannels(workspace.getId(), userId);
        }
        return view(workspace, member.getRole());
    }

    @Transactional(readOnly = true)
    public List<WorkspaceView> mine(Long userId) {
        List<WorkspaceMember> memberships = members.findByUserId(userId);
        Map<Long, Workspace> byId = workspaces
                .findAllById(memberships.stream().map(WorkspaceMember::getWorkspaceId).toList())
                .stream()
                .collect(Collectors.toMap(Workspace::getId, Function.identity()));
        return memberships.stream()
                .filter(member -> byId.containsKey(member.getWorkspaceId()))
                .map(member -> view(byId.get(member.getWorkspaceId()), member.getRole()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UserView> people(Long workspaceId, Long userId) {
        channelService.requireWorkspaceMember(workspaceId, userId);
        List<Long> ids = members.findByWorkspaceId(workspaceId).stream().map(WorkspaceMember::getUserId).toList();
        return users.findAllById(ids).stream()
                .map(UserView::of)
                .sorted(Comparator.comparing(UserView::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private WorkspaceMember addMember(Long workspaceId, Long userId, WorkspaceRole role) {
        WorkspaceMember member = new WorkspaceMember();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(role);
        return members.save(member);
    }

    private WorkspaceView view(Workspace workspace, WorkspaceRole role) {
        return new WorkspaceView(workspace.getId(), workspace.getName(), workspace.getInviteCode(), role.name());
    }
}
