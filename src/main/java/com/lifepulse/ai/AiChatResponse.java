package com.lifepulse.ai;

import java.util.List;

public record AiChatResponse(String answer, List<String> usedTools, String contextSummary) {
}
