package com.lifepulse.mcp;

import java.util.Map;

public record McpRequest(String jsonrpc, String method, Object id, Map<String, Object> params) {
}
