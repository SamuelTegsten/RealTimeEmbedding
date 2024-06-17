package com.dev.realtimeembeddingkth.langchain4j.spring.Generation.Embedding;

import dev.langchain4j.data.segment.TextSegment;

import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingStore;
import org.neo4j.driver.*;


public class InitializeNeo4j {
    protected static EmbeddingStore<TextSegment> initializeNeo4j() {
        EmbeddingStore<TextSegment> embeddingStore = Neo4jEmbeddingStore.builder()
                .driver(GraphDatabase.driver("bolt://localhost:7687", AuthTokens.basic("neo4j", "Password123")))
                .databaseName("neo4j")
                .dimension(384)
                .build();
        return embeddingStore;
    }
}
