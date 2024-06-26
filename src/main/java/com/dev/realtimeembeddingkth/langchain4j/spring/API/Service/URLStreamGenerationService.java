package com.dev.realtimeembeddingkth.langchain4j.spring.API.Service;

import com.dev.realtimeembeddingkth.langchain4j.spring.Generation.URL.URLToStreamGeneration;
import org.springframework.stereotype.Service;

@Service
public class URLStreamGenerationService {
    private final URLToStreamGeneration urlToStreamGeneration;

    public URLStreamGenerationService(URLToStreamGeneration urlToStreamGeneration) {
        this.urlToStreamGeneration = urlToStreamGeneration;
    }

    public String generateStreamWithURL(String question, String UrlPath, String modelName, String uuid, int id) {
        return urlToStreamGeneration.generateStreamWithURL(question, UrlPath, modelName, uuid, id);
    }
}
