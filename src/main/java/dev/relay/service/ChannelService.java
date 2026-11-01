package dev.relay.service;

import dev.relay.domain.Channel;
import dev.relay.domain.ChannelMember;
import dev.relay.domain.ChannelType;
import dev.relay.dto.Dtos.ChannelCreateRequest;
import dev.relay.dto.Dtos.ChannelView;
import dev.relay.repo.ChannelMemberRepository;
import dev.relay.repo.ChannelRepository;
import dev.relay.repo.MessageRepository;
import dev.relay.repo.WorkspaceMemberRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChannelService {
    private final ChannelRepository channels;
    private final ChannelMemberRepository channelMembers;
    private final WorkspaceMemberRepository workspaceMembers;
    private final MessageRepository messages;

    public boolean isMember(Long channelId, Long userId) {
        return channelMembers.existsByChannelIdAndUserId(channelId, userId);
    }

    public Channel requireMember(Long channelId, Long userId) {
        Channel channel = channels.findById(channelId).orElseThrow(() -> ApiException.notFound("Channel not found"));
        if (!isMember(channelId, userId)) {
            throw ApiException.forbidden("You are not a member of this channel");
        }
        return channel;
    }

    public void requireWorkspaceMember(Long workspaceId, Long userId) {
        if (!workspaceMembers.existsByWorkspaceIdAndUserId(workspaceId, userId)) {
            throw ApiException.forbidden("You are not a member of this workspace");
        }
    }

    @Transactional(readOnly = true)
    public List<ChannelView> list(Long workspaceId, Long userId) {
        requireWorkspaceMember(workspaceId, userId);
        Map<Long, ChannelMember> mine = channelMembers.findByUserId(userId).stream()
                .collect(Collectors.toMap(ChannelMember::getChannelId, Function.identity()));
        return channels.findByWorkspaceId(workspaceId).stream()
                .filter(channel -> mine.containsKey(channel.getId()))
                .sorted(Comparator.comparing(Channel::getName))
                .map(channel -> view(channel, userId, mine.get(channel.getId())))
                .toList();
    }

    @Transactional
    public ChannelView create(Long workspaceId, Long userId, ChannelCreateRequest request) {
        requireWorkspaceMember(workspaceId, userId);
        String name = request.name().trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (name.isEmpty()) {
            throw ApiException.badRequest("Choose a channel name with letters or numbers");
        }
        if (channels.existsByWorkspaceIdAndName(workspaceId, name)) {
            throw ApiException.conflict("A channel with that name already exists");
        }
        ChannelType type = request.privateChannel() ? ChannelType.PRIVATE : ChannelType.PUBLIC;
        Channel channel = createChannel(workspaceId, name, type, null, userId);
        if (type == ChannelType.PUBLIC) {
            workspaceMembers.findByWorkspaceId(workspaceId).forEach(member -> addMember(channel.getId(), member.getUserId()));
        } else {
            addMember(channel.getId(), userId);
        }
        return view(channel, userId, channelMembers.findByChannelIdAndUserId(channel.getId(), userId).orElseThrow());
    }

    @Transactional
    public ChannelView openDm(Long workspaceId, Long userId, Long otherId) {
        requireWorkspaceMember(workspaceId, userId);
        requireWorkspaceMember(workspaceId, otherId);
        String key = Math.min(userId, otherId) + "-" + Math.max(userId, otherId);
        Channel channel = channels.findByWorkspaceIdAndDmKey(workspaceId, key).orElseGet(() -> {
            Channel created = createChannel(workspaceId, "dm-" + key, ChannelType.DM, key, userId);
            addMember(created.getId(), userId);
            addMember(created.getId(), otherId);
            return created;
        });
        return view(channel, userId, channelMembers.findByChannelIdAndUserId(channel.getId(), userId).orElseThrow());
    }

    @Transactional
    public void invite(Long channelId, Long userId, Long targetId) {
        Channel channel = requireMember(channelId, userId);
        if (channel.getType() == ChannelType.DM) {
            throw ApiException.badRequest("Direct messages cannot have extra members");
        }
        requireWorkspaceMember(channel.getWorkspaceId(), targetId);
        addMember(channelId, targetId);
    }

    @Transactional
    public void markRead(Long channelId, Long userId) {
        requireMember(channelId, userId);
        ChannelMember member = channelMembers.findByChannelIdAndUserId(channelId, userId).orElseThrow();
        member.setLastReadMessageId(messages.maxId(channelId));
    }

    @Transactional
    public Channel createChannel(Long workspaceId, String name, ChannelType type, String dmKey, Long createdBy) {
        Channel channel = new Channel();
        channel.setWorkspaceId(workspaceId);
        channel.setName(name);
        channel.setType(type);
        channel.setDmKey(dmKey);
        channel.setCreatedBy(createdBy);
        return channels.save(channel);
    }

    @Transactional
    public void addMember(Long channelId, Long userId) {
        if (channelMembers.existsByChannelIdAndUserId(channelId, userId)) {
            return;
        }
        ChannelMember member = new ChannelMember();
        member.setChannelId(channelId);
        member.setUserId(userId);
        member.setLastReadMessageId(messages.maxId(channelId));
        channelMembers.save(member);
    }

    @Transactional
    public void joinPublicChannels(Long workspaceId, Long userId) {
        channels.findByWorkspaceIdAndType(workspaceId, ChannelType.PUBLIC)
                .forEach(channel -> addMember(channel.getId(), userId));
    }

    @Transactional(readOnly = true)
    public List<Long> channelIdsFor(Long workspaceId, Long userId) {
        Set<Long> mine = channelMembers.findByUserId(userId).stream()
                .map(ChannelMember::getChannelId)
                .collect(Collectors.toSet());
        return channels.findByWorkspaceId(workspaceId).stream()
                .map(Channel::getId)
                .filter(mine::contains)
                .toList();
    }

    private ChannelView view(Channel channel, Long userId, ChannelMember member) {
        long unread = messages.countByChannelIdAndIdGreaterThanAndUserIdNotAndDeletedFalse(
                channel.getId(), member.getLastReadMessageId(), userId);
        Long dmUserId = null;
        if (channel.getType() == ChannelType.DM) {
            String[] parts = channel.getDmKey().split("-");
            long first = Long.parseLong(parts[0]);
            long second = Long.parseLong(parts[1]);
            dmUserId = userId == first ? second : first;
        }
        return new ChannelView(channel.getId(), channel.getWorkspaceId(), channel.getName(),
                channel.getType().name(), unread, dmUserId);
    }
}
