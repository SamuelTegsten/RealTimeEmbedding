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

    public void generateStreamWithEmbedding(String question, String uuid, int id) throws IOException {
        String model = "phi3:instruct";
        embeddingGeneration.generateEmbedding(question, model, uuid, id);
    }
}
