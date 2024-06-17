package com.dev.realtimeembeddingkth.langchain4j.spring.API.Service;

import com.dev.realtimeembeddingkth.langchain4j.spring.Generation.Stream.StreamGeneration;
import org.springframework.stereotype.Service;

@Service
public class StreamGenerationService {
    private final StreamGeneration streamGeneration;

    public StreamGenerationService(StreamGeneration streamGeneration) {
        this.streamGeneration = streamGeneration;
    }

    public String generateStream(String question, String modelName, String uuid, int id) {
        return streamGeneration.generateStream(question, modelName, uuid, id);
    }
}
