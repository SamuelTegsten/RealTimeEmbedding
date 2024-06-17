package com.dev.realtimeembeddingkth.langchain4j.spring.Generation.Stream;

import com.dev.realtimeembeddingkth.langchain4j.spring.API.Service.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dev.realtimeembeddingkth.langchain4j.spring.ModelOptions.ModelObject.ModelList;
import com.dev.realtimeembeddingkth.langchain4j.spring.ModelOptions.ModelObject.Model;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

import static com.dev.realtimeembeddingkth.langchain4j.spring.Config.OllamaServerConfig.OllamaServerCheck.checkOllamaServerAndInitializeModel;
import static com.dev.realtimeembeddingkth.langchain4j.spring.Generation.InitilizeOllamaStreamModel.InitializeStreamByModel.initializeModel;
import static com.dev.realtimeembeddingkth.langchain4j.spring.ModelMemory.InitModelMemory.initModelMemory;

@Component
public class StreamGeneration {

    private final ObjectMapper objectMapper;
    private final SseService sseService;
    private GeneralStreamAssistant assistant;

    interface GeneralStreamAssistant {
        TokenStream chat(@MemoryId int memoryId, @UserMessage String userMessage);
    }

    @Autowired
    public StreamGeneration(ObjectMapper objectMapper, SseService sseService) {
        this.objectMapper = objectMapper;
        this.sseService = sseService;
    }

    public String generateStream(String question, String modelName, String uuid, int id) {
        Model modelObject = ModelList.findModelByName(modelName);
        checkOllamaServerAndInitializeModel(modelObject);

        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        assistant = AiServices.builder(GeneralStreamAssistant.class)
                .streamingChatLanguageModel(initializeModel(modelObject))
                .chatMemoryProvider(initModelMemory())
                .build();

        TokenStream tokenStream = assistant.chat(id, question);

        tokenStream.onNext(token -> {
            try {
                System.out.print(token);
                sseService.sendEvent(uuid, token, objectMapper);
            } catch (Exception e) {
                futureResponse.completeExceptionally(e);
            }
        }).onComplete(response -> {
            try {
                sseService.sendEvent(uuid, "#FC9123CFAA1953123#", objectMapper);
                sseService.completeEmitter(uuid);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            futureResponse.complete(response);
        }).onError(throwable -> {
            sseService.completeEmitter(uuid);
        }).start();

        futureResponse.join();
        return "Running";
    }
}
