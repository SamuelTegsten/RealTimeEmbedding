package com.dev.realtimeembeddingkth.langchain4j.spring.Config.ModelConfig.OllamaConfig;

import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
class LanguageModelProperties {

    String baseUrl;
    String modelName;
    Double temperature;
    Integer topK;
    Double topP;
    Double repeatPenalty;
    Integer seed;
    Integer numPredict;
    List<String> stop;
    String format;
    Duration timeout;
    Integer maxRetries;
}