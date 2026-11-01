package dev.relay.repo;

import dev.relay.domain.Channel;
import dev.relay.domain.ChannelType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    List<Channel> findByWorkspaceId(Long workspaceId);

    List<Channel> findByWorkspaceIdAndType(Long workspaceId, ChannelType type);

    Optional<Channel> findByWorkspaceIdAndDmKey(Long workspaceId, String dmKey);

    boolean existsByWorkspaceIdAndName(Long workspaceId, String name);
}
