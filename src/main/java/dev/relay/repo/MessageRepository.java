package dev.relay.repo;

import dev.relay.domain.Message;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByChannelIdAndParentIdIsNullOrderByIdDesc(Long channelId, Pageable pageable);

    List<Message> findByChannelIdAndParentIdIsNullAndIdLessThanOrderByIdDesc(Long channelId, Long before, Pageable pageable);

    List<Message> findByParentIdOrderByIdAsc(Long parentId);

    long countByChannelIdAndIdGreaterThanAndUserIdNotAndDeletedFalse(Long channelId, Long afterId, Long userId);

    @Query("select coalesce(max(m.id), 0L) from Message m where m.channelId = :channelId")
    long maxId(@Param("channelId") Long channelId);

    @Query("select m.parentId, count(m) from Message m where m.parentId in :ids and m.deleted = false group by m.parentId")
    List<Object[]> countReplies(@Param("ids") Collection<Long> ids);

    @Query(value = "select * from messages m where m.deleted = false and m.channel_id in (:channelIds) "
            + "and to_tsvector('english', m.content) @@ plainto_tsquery('english', :q) order by m.id desc limit 30",
            nativeQuery = true)
    List<Message> search(@Param("channelIds") Collection<Long> channelIds, @Param("q") String q);
}
