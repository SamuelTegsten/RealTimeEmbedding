
package com.dev.realtimeembeddingkth.langchain4j.spring.API.Service;

import com.dev.realtimeembeddingkth.langchain4j.spring.Generation.Document.DocumentToStreamGeneration;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class DocumentStreamGenerationService {
    private final DocumentToStreamGeneration documentToStreamGeneration;

    public DocumentStreamGenerationService(DocumentToStreamGeneration documentToStreamGeneration) {
        this.documentToStreamGeneration = documentToStreamGeneration;
    }

    public void generateStreamWithDocument(String question, MultipartFile document, String modelName,String uuid, int id) throws IOException {
        documentToStreamGeneration.generateStreamWithDocument(question, document, modelName, uuid, id);
    }
}