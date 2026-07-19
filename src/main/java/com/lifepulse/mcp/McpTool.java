package com.lifepulse.mcp;

import java.util.Map;

public record McpTool(String name, String description, Map<String, Object> inputSchema) {
}
