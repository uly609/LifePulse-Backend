package com.lifepulse.ai;

import com.lifepulse.common.Result;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/ai")
public class AiAssistantController {
    private final AiAssistantService aiAssistantService;

    public AiAssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
    }

    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return Result.success(aiAssistantService.chat(request.question()));
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter stream(@RequestParam String question) {
        SseEmitter emitter = new SseEmitter(60_000L);
        Thread.startVirtualThread(() -> {
            try {
                AiChatResponse response = aiAssistantService.chat(question);
                emitter.send(SseEmitter.event().name("meta").data(response.contextSummary()));
                for (String chunk : response.answer().split("(?<=。|！|？|\\n)")) {
                    if (!chunk.isBlank()) {
                        emitter.send(SseEmitter.event().name("message").data(chunk));
                        Thread.sleep(80);
                    }
                }
                emitter.send(SseEmitter.event().name("done").data("DONE"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("message").data("AI助手异常：" + e.getMessage()));
                } catch (IOException ignored) {
                }
                emitter.complete();
            }
        });
        return emitter;
    }
}
