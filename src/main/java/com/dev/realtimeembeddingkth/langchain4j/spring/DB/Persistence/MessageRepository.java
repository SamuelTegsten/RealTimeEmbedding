package com.dev.realtimeembeddingkth.langchain4j.spring.DB.Persistence;

import com.dev.realtimeembeddingkth.langchain4j.spring.DB.Entity.Message;
import org.springframework.data.repository.CrudRepository;

public interface MessageRepository extends CrudRepository<Message, Long> {

}
