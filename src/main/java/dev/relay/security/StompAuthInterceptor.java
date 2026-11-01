package dev.relay.security;

import dev.relay.service.ChannelService;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {
    private static final Pattern CHANNEL_TOPIC = Pattern.compile("^/topic/channel\\.(\\d+)$");

    private final JwtService jwtService;
    private final ChannelService channelService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new MessagingException("Missing token");
            }
            AuthUser user = jwtService.parse(header.substring(7)).orElseThrow(() -> new MessagingException("Invalid token"));
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            if (!(accessor.getUser() instanceof Authentication auth) || !(auth.getPrincipal() instanceof AuthUser user)) {
                throw new MessagingException("Unauthenticated");
            }
            String destination = accessor.getDestination();
            Matcher matcher = destination == null ? null : CHANNEL_TOPIC.matcher(destination);
            if (matcher == null || !matcher.matches()) {
                throw new MessagingException("Unsupported destination");
            }
            if (!channelService.isMember(Long.parseLong(matcher.group(1)), user.id())) {
                throw new MessagingException("Forbidden");
            }
        }
        return message;
    }
}
