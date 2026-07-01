package com.genersoft.iot.vmp.conf.security;

import com.alibaba.fastjson2.JSONObject;
import com.genersoft.iot.vmp.conf.security.dto.JwtUser;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * PLS 签发的 HS512 JWT 校验器。
 * PLS 使用 jjwt 0.9.x 签发，secret 按 Base64 语义解码（忽略不完整的尾部组），
 * 且对密钥长度无强制限制；本类使用 JDK 原生 HmacSHA512 做等价校验，避免引入 jjwt。
 */
@Slf4j
public class PlsTokenVerifier {

    private static final String ALGORITHM = "HmacSHA512";

    /**
     * 校验 PLS token 签名并抽取用户名。
     *
     * @param token  PLS 签发的 JWT
     * @param secret PLS token secret（与 PLS application.yml token.secret 一致）
     * @return 校验通过返回 NORMAL 状态的 JwtUser；失败返回 null
     */
    public static JwtUser verify(String token, String secret) {
        log.debug("[PLS-AUTH] 收到 token, len={}, preview={}", token == null ? 0 : token.length(), maskMiddle(token));
        if (secret == null || secret.isEmpty()) {
            log.debug("[PLS-AUTH] PLS token trust enabled but secret is empty");
            return null;
        }
        log.debug("[PLS-AUTH] 使用 secret, len={}, preview={}, sha256_8={}",
                secret.length(), maskMiddle(secret), hashPrefix(secret.getBytes(StandardCharsets.UTF_8)));
        if (token == null || token.isEmpty()) {
            return null;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            log.debug("[PLS-AUTH] PLS token format invalid, expected 3 parts");
            return null;
        }
        try {
            byte[] key = deriveKey(secret);
            log.debug("[PLS-AUTH] 解出 key, bytes={}, sha256_8={}", key.length, hashPrefix(key));
            String signingInput = parts[0] + "." + parts[1];

            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            byte[] computed = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));

            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            log.debug("[PLS-AUTH] 签名比对, computed_sha256_8={}, token_sha256_8={}",
                    hashPrefix(computed), hashPrefix(signature));
            if (!constantTimeEquals(computed, signature)) {
                log.debug("[PLS-AUTH] PLS token signature mismatch");
                return null;
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JSONObject payload = JSONObject.parseObject(payloadJson);
            if (payload == null) {
                log.debug("[PLS-AUTH] PLS token payload is not valid JSON");
                return null;
            }

            String username = payload.getString("sub");
            if (username == null || username.isEmpty()) {
                username = "pls-admin";
            }
            log.debug("[PLS-AUTH] 验签通过, username={}", username);

            JwtUser jwtUser = new JwtUser();
            jwtUser.setStatus(JwtUser.TokenStatus.NORMAL);
            jwtUser.setUserName(username);
            jwtUser.setPassword("");
            jwtUser.setUserId(-1);
            jwtUser.setRoleId(-1);
            return jwtUser;
        } catch (Exception e) {
            log.debug("[PLS-AUTH] PLS token verify failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 按 jjwt 0.9.x 的 Base64 语义解码 secret：只取完整 4 字符组，忽略不完整尾部。
     */
    private static byte[] deriveKey(String secret) {
        int completeGroups = secret.length() / 4;
        String complete = secret.substring(0, completeGroups * 4);
        return Base64.getDecoder().decode(complete);
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }

    /**
     * 掩码预览：首尾各留 3 位，中间替换成 *，避免明文落盘。
     */
    private static String maskMiddle(String s) {
        if (s == null) {
            return "null";
        }
        int len = s.length();
        if (len <= 6) {
            return "*".repeat(len);
        }
        return s.substring(0, 3) + "*".repeat(len - 6) + s.substring(len - 3);
    }

    /**
     * SHA-256 前 8 位 hex，用于跨系统比对是否为同一份原文，而不暴露原文。
     */
    private static String hashPrefix(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "n/a";
        }
    }
}
