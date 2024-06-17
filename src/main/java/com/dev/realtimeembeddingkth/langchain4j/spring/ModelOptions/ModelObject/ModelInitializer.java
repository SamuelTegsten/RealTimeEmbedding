package com.dev.realtimeembeddingkth.langchain4j.spring.ModelOptions.ModelObject;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import static com.dev.realtimeembeddingkth.langchain4j.spring.ModelOptions.ModelObject.ModelList.displayAllModels;

@Component
public class ModelInitializer {
    @PostConstruct
    public void init() {
        ModelList.populateModels();
        displayAllModels();
    }
}
