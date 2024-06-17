package com.dev.realtimeembeddingkth.langchain4j.spring.Generation.URL;

import com.dev.realtimeembeddingkth.langchain4j.spring.API.Service.SseService;
import com.dev.realtimeembeddingkth.langchain4j.spring.ModelOptions.ModelObject.Model;
import com.dev.realtimeembeddingkth.langchain4j.spring.ModelOptions.ModelObject.ModelList;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.UrlDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.retriever.EmbeddingStoreRetriever;
import dev.langchain4j.retriever.Retriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.dev.realtimeembeddingkth.langchain4j.spring.Config.OllamaServerConfig.OllamaServerCheck.checkOllamaServerAndInitializeModel;
import static com.dev.realtimeembeddingkth.langchain4j.spring.Generation.InitilizeOllamaStreamModel.InitializeStreamByModel.initializeModel;
import static com.dev.realtimeembeddingkth.langchain4j.spring.ModelMemory.InitModelMemory.initModelMemory;

@Component
public class URLToStreamGeneration {
    private GeneralStreamAssistant assistant;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    interface GeneralStreamAssistant{
        TokenStream chat(@MemoryId int memoryId, @UserMessage String userMessage);
    }

    @Autowired
    public URLToStreamGeneration(ObjectMapper objectMapper, SseService sseService) {
        this.objectMapper = objectMapper;
        this.sseService = sseService;
    }

    public String generateStreamWithURL(String question, String UrlPath, String modelName, String uuid, int id) {
        Model modelObject = ModelList.findModelByName(modelName);
        checkOllamaServerAndInitializeModel(modelObject);
        initializeStreamWithUrl(UrlPath, modelObject);

        CompletableFuture<Response<AiMessage>> futureResponse = new CompletableFuture<>();

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

    private void initializeStreamWithUrl(String UrlPath, Model model) {
        DocumentParser documentParser = new TextDocumentParser();
        Document document = UrlDocumentLoader.load(UrlPath, documentParser);

        assistant = AiServices.builder(GeneralStreamAssistant.class)
                .streamingChatLanguageModel(initializeModel(model))
                .retriever(retriever(document))
                .chatMemoryProvider(initModelMemory())
                .build();

        System.out.println("Assistant with URL is loaded");
    }

    private Retriever<TextSegment> retriever(Document document){
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        DocumentSplitter splitter = DocumentSplitters.recursive(300, 0);
        List<TextSegment> segments = splitter.split(document);

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);

        return EmbeddingStoreRetriever.from(embeddingStore, embeddingModel, 1, 0.6);
    }


}
