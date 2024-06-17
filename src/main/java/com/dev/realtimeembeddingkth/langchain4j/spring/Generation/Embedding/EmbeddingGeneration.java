package com.dev.realtimeembeddingkth.langchain4j.spring.Generation.Embedding;

import com.dev.realtimeembeddingkth.langchain4j.spring.API.Service.SseService;
import com.dev.realtimeembeddingkth.langchain4j.spring.ModelOptions.ModelObject.Model;
import com.dev.realtimeembeddingkth.langchain4j.spring.ModelOptions.ModelObject.ModelList;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;

import dev.langchain4j.model.embedding.bge.small.en.v15.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.transformer.ExpandingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static com.dev.realtimeembeddingkth.langchain4j.spring.Config.OllamaServerConfig.OllamaServerCheck.baseUrl;
import static com.dev.realtimeembeddingkth.langchain4j.spring.Config.OllamaServerConfig.OllamaServerCheck.checkOllamaServerAndInitializeModel;
import static com.dev.realtimeembeddingkth.langchain4j.spring.Generation.Embedding.InitializeNeo4j.initializeNeo4j;
import static com.dev.realtimeembeddingkth.langchain4j.spring.Generation.InitilizeOllamaStreamModel.InitializeStreamByModel.initializeModel;

@Component
public class EmbeddingGeneration {
    private final SseService sseService;
    private final ObjectMapper objectMapper;
    interface GeneralStreamAssistant{
        @SystemMessage("Show the content in a numbered list: 1 , 2 , 3... etc. " +
        "Include all relevant information found")
        TokenStream chat(@MemoryId int memoryId, @UserMessage String userMessage);
    }

    @Autowired
    public EmbeddingGeneration(SseService sseService, ObjectMapper objectMapper) {
        this.sseService = sseService;
        this.objectMapper = objectMapper;
    }

    public void generateEmbedding(String question, String modelName, String uuid, int id){

        Model modelObject = ModelList.findModelByName(modelName);
        checkOllamaServerAndInitializeModel(modelObject);

        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        //EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(initializeNeo4j())
                .embeddingModel(embeddingModel)
                .minScore(0.6)
                .maxResults(25)
                .build();

        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofMinutes(2))
                .maxRetries(3)
                .build();

        QueryTransformer queryTransformer = new ExpandingQueryTransformer(model);

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .contentRetriever(contentRetriever)
                .build();

        GeneralStreamAssistant assistant = AiServices.builder(GeneralStreamAssistant.class)
                .streamingChatLanguageModel(initializeModel(modelObject))
                //.chatMemoryProvider(initModelMemory())
                //.retrievalAugmentor(retrievalAugmentor)
                .contentRetriever(contentRetriever)
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
    }
}
