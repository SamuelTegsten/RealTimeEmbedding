package com.dev.realtimeembeddingkth.langchain4j.spring.API.Service;

import com.dev.realtimeembeddingkth.langchain4j.spring.Generation.Embedding.EmbeddingGeneration;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class EmbeddingGenerationService {
    private final EmbeddingGeneration embeddingGeneration;

    public EmbeddingGenerationService(EmbeddingGeneration embeddingGeneration) {
        this.embeddingGeneration = embeddingGeneration;
    }

    public void generateStreamWithEmbedding(String question, String modelName, String uuid, int id) throws IOException {
        embeddingGeneration.generateEmbedding(question, modelName, uuid, id);
    }
}
