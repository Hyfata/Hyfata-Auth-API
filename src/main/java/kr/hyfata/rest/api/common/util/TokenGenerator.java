package kr.hyfata.rest.api.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

@Component
@Slf4j
public class TokenGenerator {

    private static final String NUMERIC_CHARS = "0123456789";
    private final SecureRandom random = new SecureRandom();

    /**
     * 2FA 코드 생성 (6자리 숫자)
     */
    public String generate2FACode() {
        return generateCode();
    }

    /**
     * 랜덤 토큰 생성 (UUID 기반)
     */
    public String generateRandomToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * 비밀번호 재설정 토큰 생성 (복잡한 토큰)
     */
    public String generatePasswordResetToken() {
        return generateRandomToken() + "-" + System.currentTimeMillis();
    }

    /**
     * 이메일 검증 토큰 생성
     */
    public String generateEmailVerificationToken() {
        return generateRandomToken();
    }

    /**
     * 계정 복구 토큰 생성
     */
    public String generateAccountRestoreToken() {
        return generateRandomToken();
    }

    /**
     * 지정된 길이의 코드 생성
     */
    private String generateCode() {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(TokenGenerator.NUMERIC_CHARS.charAt(random.nextInt(TokenGenerator.NUMERIC_CHARS.length())));
        }
        return code.toString();
    }
}