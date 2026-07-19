package com.lifepulse.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifepulse.common.BusinessException;
import com.lifepulse.config.LifePulseProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final LifePulseProperties properties;
    private final ObjectMapper objectMapper;

    public JwtService(LifePulseProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String createToken(Long userId, String username, String role) {
        try {
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", String.valueOf(userId));
            payload.put("username", username);
            payload.put("role", role);
            payload.put("exp", Instant.now().getEpochSecond() + properties.getJwt().getTtlSeconds());
            String headerPart = encodeJson(header);
            String payloadPart = encodeJson(payload);
            String body = headerPart + "." + payloadPart;
            return body + "." + sign(body);
        } catch (Exception e) {
            throw new IllegalStateException("JWT生成失败", e);
        }
    }

    public Long parseUserId(String token) {
        return parseClaims(token).userId();
    }

    public JwtClaims parseClaims(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new BusinessException("Token格式错误");
            }
            String body = parts[0] + "." + parts[1];
            if (!sign(body).equals(parts[2])) {
                throw new BusinessException("Token签名无效");
            }
            Map<String, Object> payload = objectMapper.readValue(URL_DECODER.decode(parts[1]),
                    new TypeReference<>() {
                    });
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > exp) {
                throw new BusinessException("Token已过期");
            }
            return new JwtClaims(
                    Long.valueOf(String.valueOf(payload.get("sub"))),
                    String.valueOf(payload.get("username")),
                    String.valueOf(payload.get("role"))
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Token解析失败");
        }
    }

    private String encodeJson(Object value) throws Exception {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return URL_ENCODER.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
