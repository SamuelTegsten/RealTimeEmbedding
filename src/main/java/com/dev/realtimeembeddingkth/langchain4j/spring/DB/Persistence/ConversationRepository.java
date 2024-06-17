package com.dev.realtimeembeddingkth.langchain4j.spring.DB.Persistence;

import com.dev.realtimeembeddingkth.langchain4j.spring.DB.Entity.Conversation;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface ConversationRepository extends CrudRepository<Conversation, Long> {
    boolean existsByOwnerId(UUID ownerId);
    List<Conversation> findAllByOwnerId(UUID ownerId);
}

