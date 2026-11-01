package dev.relay.dto;

import dev.relay.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class Dtos {
    private Dtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 80) String displayName,
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record UserView(Long id, String displayName) {
        public static UserView of(User user) {
            return new UserView(user.getId(), user.getDisplayName());
        }
    }

    public record AuthResponse(String token, UserView user) {
    }

    public record WorkspaceCreateRequest(@NotBlank @Size(max = 80) String name) {
    }

    public record JoinRequest(@NotBlank String inviteCode) {
    }

    public record WorkspaceView(Long id, String name, String inviteCode, String role) {
    }

    public record ChannelCreateRequest(@NotBlank @Size(max = 80) String name, boolean privateChannel) {
    }

    public record UserRef(@NotNull Long userId) {
    }

    public record ChannelView(Long id, Long workspaceId, String name, String type, long unread, Long dmUserId) {
    }

    public record MessageCreateRequest(@NotBlank @Size(max = 4000) String content, Long parentId) {
    }

    public record MessageEditRequest(@NotBlank @Size(max = 4000) String content) {
    }

    public record ReactionRequest(@NotBlank @Size(max = 16) String emoji) {
    }

    public record ReactionView(String emoji, long count, List<Long> userIds) {
    }

    public record ReactionUpdate(Long messageId, List<ReactionView> reactions) {
    }

    public record MessageView(
            Long id,
            Long channelId,
            Long parentId,
            UserView author,
            String content,
            Instant createdAt,
            Instant editedAt,
            boolean deleted,
            long replyCount,
            List<ReactionView> reactions) {
    }

    public record MessagePage(List<MessageView> items, Long nextCursor) {
    }

    public record TypingView(Long userId, String displayName) {
    }

    public record ChatEvent(String type, Long channelId, Object data) {
    }
}
