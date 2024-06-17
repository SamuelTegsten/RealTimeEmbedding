package com.dev.realtimeembeddingkth.langchain4j.spring.API.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SseService {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(String uuid) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(uuid, emitter);

        emitter.onCompletion(() -> emitters.remove(uuid));
        emitter.onTimeout(() -> emitters.remove(uuid));
        emitter.onError(e -> emitters.remove(uuid));

        return emitter;
    }

    public SseEmitter getEmitter(String uuid) {
        return emitters.get(uuid);
    }

    public void removeEmitter(String uuid) {
        emitters.remove(uuid);
    }

    public void sendEvent(String uuid, Object dataObject, ObjectMapper objectMapper) throws Exception {
        SseEmitter emitter = emitters.get(uuid);
        if (emitter != null) {
            try {
                String jsonData = objectMapper.writeValueAsString(dataObject);
                emitter.send(SseEmitter.event().name("message").data(jsonData));
            } catch (Exception e) {
                System.out.println("Failed to send message to UUID: " + uuid);
                emitters.remove(uuid);
                emitter.complete();
            }
        }
    }

    public void completeEmitter(String uuid) {
        SseEmitter emitter = emitters.get(uuid);
        if (emitter != null) {
            emitter.complete();
        }
    }
}
