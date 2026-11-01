package dev.relay.service;

import dev.relay.domain.Message;
import dev.relay.domain.Reaction;
import dev.relay.domain.User;
import dev.relay.dto.Dtos.MessageCreateRequest;
import dev.relay.dto.Dtos.MessagePage;
import dev.relay.dto.Dtos.MessageView;
import dev.relay.dto.Dtos.ReactionUpdate;
import dev.relay.dto.Dtos.ReactionView;
import dev.relay.dto.Dtos.TypingView;
import dev.relay.dto.Dtos.UserView;
import dev.relay.repo.MessageRepository;
import dev.relay.repo.ReactionRepository;
import dev.relay.repo.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {
    private final MessageRepository messages;
    private final ReactionRepository reactions;
    private final UserRepository users;
    private final ChannelService channelService;
    private final EventPublisher events;

    @Transactional
    public MessageView post(Long channelId, Long userId, MessageCreateRequest request) {
        channelService.requireMember(channelId, userId);
        if (request.parentId() != null) {
            Message parent = messages.findById(request.parentId())
                    .orElseThrow(() -> ApiException.notFound("Thread not found"));
            if (!parent.getChannelId().equals(channelId) || parent.getParentId() != null || parent.isDeleted()) {
                throw ApiException.badRequest("You cannot reply to that message");
            }
        }
        Message message = new Message();
        message.setChannelId(channelId);
        message.setUserId(userId);
        message.setParentId(request.parentId());
        message.setContent(request.content().trim());
        messages.save(message);
        MessageView view = toViews(List.of(message)).get(0);
        events.publish("message.created", channelId, view);
        return view;
    }

    @Transactional(readOnly = true)
    public MessagePage history(Long channelId, Long userId, Long before, int limit) {
        channelService.requireMember(channelId, userId);
        int size = Math.max(1, Math.min(limit, 100));
        Pageable page = PageRequest.of(0, size + 1);
        List<Message> found = before == null
                ? messages.findByChannelIdAndParentIdIsNullOrderByIdDesc(channelId, page)
                : messages.findByChannelIdAndParentIdIsNullAndIdLessThanOrderByIdDesc(channelId, before, page);
        boolean more = found.size() > size;
        List<Message> window = new ArrayList<>(more ? found.subList(0, size) : found);
        Long nextCursor = more ? window.get(window.size() - 1).getId() : null;
        Collections.reverse(window);
        return new MessagePage(toViews(window), nextCursor);
    }

    @Transactional(readOnly = true)
    public List<MessageView> thread(Long parentId, Long userId) {
        Message parent = messages.findById(parentId).orElseThrow(() -> ApiException.notFound("Thread not found"));
        channelService.requireMember(parent.getChannelId(), userId);
        List<Message> all = new ArrayList<>();
        all.add(parent);
        all.addAll(messages.findByParentIdOrderByIdAsc(parentId));
        return toViews(all);
    }

    @Transactional
    public MessageView edit(Long messageId, Long userId, String content) {
        Message message = owned(messageId, userId);
        message.setContent(content.trim());
        message.setEditedAt(Instant.now());
        MessageView view = toViews(List.of(message)).get(0);
        events.publish("message.updated", message.getChannelId(), view);
        return view;
    }

    @Transactional
    public void delete(Long messageId, Long userId) {
        Message message = owned(messageId, userId);
        message.setDeleted(true);
        message.setContent("");
        reactions.deleteByMessageId(messageId);
        events.publish("message.deleted", message.getChannelId(), toViews(List.of(message)).get(0));
    }

    @Transactional
    public List<ReactionView> toggleReaction(Long messageId, Long userId, String emoji) {
        Message message = messages.findById(messageId).orElseThrow(() -> ApiException.notFound("Message not found"));
        if (message.isDeleted()) {
            throw ApiException.badRequest("This message was deleted");
        }
        channelService.requireMember(message.getChannelId(), userId);
        Optional<Reaction> existing = reactions.findByMessageIdAndUserIdAndEmoji(messageId, userId, emoji);
        if (existing.isPresent()) {
            reactions.delete(existing.get());
        } else {
            Reaction reaction = new Reaction();
            reaction.setMessageId(messageId);
            reaction.setUserId(userId);
            reaction.setEmoji(emoji);
            reactions.save(reaction);
        }
        List<ReactionView> views = reactionViews(reactions.findByMessageIdOrderByIdAsc(messageId));
        events.publish("reaction.updated", message.getChannelId(), new ReactionUpdate(messageId, views));
        return views;
    }

    public void typing(Long channelId, Long userId) {
        channelService.requireMember(channelId, userId);
        users.findById(userId).ifPresent(user ->
                events.publish("typing", channelId, new TypingView(user.getId(), user.getDisplayName())));
    }

    @Transactional(readOnly = true)
    public List<MessageView> search(Long workspaceId, Long userId, String query) {
        channelService.requireWorkspaceMember(workspaceId, userId);
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<Long> channelIds = channelService.channelIdsFor(workspaceId, userId);
        if (channelIds.isEmpty()) {
            return List.of();
        }
        return toViews(messages.search(channelIds, query.trim()));
    }

    private Message owned(Long messageId, Long userId) {
        Message message = messages.findById(messageId).orElseThrow(() -> ApiException.notFound("Message not found"));
        if (!message.getUserId().equals(userId) || message.isDeleted()) {
            throw ApiException.forbidden("You cannot change this message");
        }
        return message;
    }

    private List<MessageView> toViews(List<Message> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(Message::getId).toList();
        Map<Long, User> authors = users.findAllById(list.stream().map(Message::getUserId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Long> replies = new HashMap<>();
        for (Object[] row : messages.countReplies(ids)) {
            replies.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, List<Reaction>> reacted = reactions.findByMessageIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(Reaction::getMessageId));
        return list.stream()
                .map(message -> new MessageView(
                        message.getId(),
                        message.getChannelId(),
                        message.getParentId(),
                        UserView.of(authors.get(message.getUserId())),
                        message.getContent(),
                        message.getCreatedAt(),
                        message.getEditedAt(),
                        message.isDeleted(),
                        replies.getOrDefault(message.getId(), 0L),
                        reactionViews(reacted.getOrDefault(message.getId(), List.of()))))
                .toList();
    }

    private List<ReactionView> reactionViews(List<Reaction> list) {
        Map<String, List<Long>> byEmoji = new LinkedHashMap<>();
        for (Reaction reaction : list) {
            byEmoji.computeIfAbsent(reaction.getEmoji(), key -> new ArrayList<>()).add(reaction.getUserId());
        }
        return byEmoji.entrySet().stream()
                .map(entry -> new ReactionView(entry.getKey(), entry.getValue().size(), entry.getValue()))
                .toList();
    }
}
