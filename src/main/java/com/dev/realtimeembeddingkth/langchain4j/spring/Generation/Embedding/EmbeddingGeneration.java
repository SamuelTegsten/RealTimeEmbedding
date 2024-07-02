package com.dev.realtimeembeddingkth.langchain4j.spring.Generation.Embedding;

import com.dev.realtimeembeddingkth.langchain4j.spring.API.Service.SseService;
import com.dev.realtimeembeddingkth.langchain4j.spring.ModelOptions.ModelObject.Model;
import com.dev.realtimeembeddingkth.langchain4j.spring.ModelOptions.ModelObject.ModelList;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;

import dev.langchain4j.data.segment.TextSegment;
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

import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static com.dev.realtimeembeddingkth.langchain4j.spring.Config.OllamaServerConfig.OllamaServerCheck.baseUrl;
import static com.dev.realtimeembeddingkth.langchain4j.spring.Config.OllamaServerConfig.OllamaServerCheck.checkOllamaServerAndInitializeModel;
import static com.dev.realtimeembeddingkth.langchain4j.spring.Generation.Embedding.InitializeNeo4j.initializeNeo4j;
import static com.dev.realtimeembeddingkth.langchain4j.spring.Generation.InitilizeOllamaStreamModel.InitializeStreamByModel.initializeModel;
import static com.dev.realtimeembeddingkth.langchain4j.spring.ModelMemory.InitModelMemory.initModelMemory;

@Component
public class EmbeddingGeneration {
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    /**
     * Interface defining a general stream assistant for chat operations.
     * @SystemMessage defines the Agent Prompt from the system Admin
     * @UserMessage is the provided message + possible input that the system admin want the user to always say
     * @MemoryId is the specific id from each conversation that the multi-user-chat-memory.db saves (default 10 messages)
     */

    interface GeneralStreamAssistant{
        @SystemMessage({"Show the content in a numbered list: 1 , 2 , 3... etc.",
        "Show all content from { Entry ... }"})
        TokenStream chat(@MemoryId int memoryId, @UserMessage String userMessage);
    }

    /**
     * @param sseService the SSE (Server-Sent Events) service used for sending events to the frontend
     * @param objectMapper the ObjectMapper used for JSON processing the tokens
     */

    @Autowired
    public EmbeddingGeneration(SseService sseService, ObjectMapper objectMapper) {
        this.sseService = sseService;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates an embedding based on the provided question, model name, and user ID.
     *
     * @param question the question to be processed
     * @param modelName the name of the model to be used
     * @param uuid the unique user ID
     * @param id the specific conversation ID
     */

    public void generateEmbedding(String question, String modelName, String uuid, int id){

        /**
         * findModelByName gets the model Object from the @param modelName
         * The checkOllamaServerAndInitializeModel downloads the Ollama model if it does not exist in the docker container
         * and if the container does not exist then it is downloaded from the Ollama repository
         */

        Model modelObject = ModelList.findModelByName(modelName);
        checkOllamaServerAndInitializeModel(modelObject);

        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

        //BgeSmallEnV15QuantizedEmbeddingModel() is the only one as of 2024-06-18 that works with the embeddingstore implementation
        EmbeddingModel embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        //EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        /**
         * The ContentRetriever aggregates the embedding store, embedding model, so they can interact.
         * minScore 0.6 --> default value (most accurate)
         * maxResults 25 --> Trial and error value for most accurate response
         */


        EmbeddingStore<TextSegment> embeddingStore = initializeNeo4j();

        ContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .minScore(0.6)
                .maxResults(25)
                .build();

        //Is only used for RetrievalAugmentor
        ChatLanguageModel model = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(Duration.ofMinutes(2))
                .maxRetries(3)
                .build();

        //Is only used for RetrievalAugmentor
        QueryTransformer queryTransformer = new ExpandingQueryTransformer(model);

        //Currently worse than the contentRetriever on its own. Might be able to produce better responses with better ExpandingQueryTransformer.
        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .contentRetriever(contentRetriever)
                .build();

        //Aggregates the contentRetriever and LLM.
        GeneralStreamAssistant assistant = AiServices.builder(GeneralStreamAssistant.class)
                .streamingChatLanguageModel(initializeModel(modelObject))
                //.chatMemoryProvider(initModelMemory())
                //.retrievalAugmentor(retrievalAugmentor)
                .contentRetriever(contentRetriever)
                .build();

        Embedding queryEmbedding = embeddingModel.embed(question).content();
        EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                .maxResults(25)
                .minScore(0.6)
                .queryEmbedding(queryEmbedding)
                .build();

        var relevant = embeddingStore.search(embeddingSearchRequest);
        String text = String.valueOf(relevant.matches());
        int startIndex = text.indexOf("{ Entry: 0");
        int endIndex = text.indexOf("metadata = {}", startIndex + 1);
        String extractedText = text.substring(startIndex + 1, endIndex);

        System.out.println(extractedText);

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
                sseService.sendEvent(uuid, extractedText, objectMapper);
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
