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

}
