package com.dev.realtimeembeddingkth.langchain4j.spring.API.Controller;

import com.dev.realtimeembeddingkth.langchain4j.spring.API.Service.EmbeddingGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/generateStreamFromEmbedding")
public class EmbeddingGenerationController {

    private final EmbeddingGenerationService embeddingGenerationService;

    @Autowired
    public EmbeddingGenerationController(EmbeddingGenerationService embeddingGenerationService) {
        this.embeddingGenerationService = embeddingGenerationService;
    }

    @GetMapping("/embeddingStream")
    public String generateStreamWithDocument(
            @RequestParam String message,
            @RequestParam String uuid,
            @RequestParam String modelName,
            @RequestParam int id) throws IOException {
        embeddingGenerationService.generateStreamWithEmbedding(message, modelName, uuid, id);
        return "Streaming";
    }

    //test

    @GetMapping("/embeddingStreamTest")
    public String generateStreamWithDocumentTest() throws IOException {
        embeddingGenerationService.generateStreamWithEmbedding("List the number of participants in cultural schools for the year 2021 next to its municipality (Kommun)", "llama3", "12345", 1);
        return "Streaming";
    }
}
