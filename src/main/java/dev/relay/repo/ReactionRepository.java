package dev.relay.repo;

import dev.relay.domain.Reaction;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {
    List<Reaction> findByMessageIdInOrderByIdAsc(Collection<Long> messageIds);

    List<Reaction> findByMessageIdOrderByIdAsc(Long messageId);

    Optional<Reaction> findByMessageIdAndUserIdAndEmoji(Long messageId, Long userId, String emoji);

    void deleteByMessageId(Long messageId);
}
