package com.dev.realtimeembeddingkth.langchain4j.spring.API.Service;

import com.dev.realtimeembeddingkth.langchain4j.spring.Generation.Embedding.EmbedNeo4jDB;
import com.dev.realtimeembeddingkth.langchain4j.spring.Generation.Embedding.EmbeddingGeneration;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class EmbeddingGenerationService {
    private final EmbeddingGeneration embeddingGeneration;
    private final EmbedNeo4jDB embeddingNeo4jDB;

    public EmbeddingGenerationService(EmbeddingGeneration embeddingGeneration, EmbedNeo4jDB embeddingNeo4jDB) {
        this.embeddingGeneration = embeddingGeneration;
        this.embeddingNeo4jDB = embeddingNeo4jDB;
    }

    public void generateStreamWithEmbedding(String question, String modelName, String uuid, int id) throws Exception {
        embeddingGeneration.generateEmbedding(question, modelName, uuid, id);
    }

    public void executeQuery(String query, String NLPQuestion) {
        embeddingNeo4jDB.executeQuery(query, NLPQuestion);
    }
}
