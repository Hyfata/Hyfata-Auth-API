package kr.hyfata.rest.api.common.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import kr.hyfata.rest.api.auth.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 이메일 서비스
 * <p>
 * 비동기로 HTML 이메일을 발송하여 API 응답을 지연시키지 않습니다.
 * 메일 발송 실패는 로그에만 기록되며, 비즈니스 로직에는 영향을 주지 않습니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@hyfata.com}")
    private String fromEmail;

    @Value("${app.backend.url:http://localhost:8080}")
    private String backendUrl;

    @Value("${spring.mail.enabled:true}")
    private boolean mailEnabled;

    /**
     * 2FA 코드 이메일 발송 (비동기)
     */
    @Async
    public void sendTwoFactorEmail(String to, String code, String clientId) {
        try {
            if (!mailEnabled) {
                log.warn("메일 발송이 비활성화되어 있습니다. 2FA 메일을 건 넘깁니다: {}", to);
                return;
            }

            String subject = "Hyfata 인증 코드";
            String html = buildTwoFactorHtml(code);
            sendHtmlEmail(to, subject, html);
            log.info("2FA 이메일 발송 성공: {} (client: {})", to, clientId);
        } catch (Exception e) {
            log.error("2FA 이메일 발송 실패 {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * 비밀번호 재설정 이메일 발송 (비동기)
     */
    @Async
    public void sendPasswordResetEmail(String to, String resetToken, String clientId) {
        try {
            if (!mailEnabled) {
                log.warn("메일 발송이 비활성화되어 있습니다. 비밀번호 재설정 메일을 건 넘깁니다: {}", to);
                return;
            }

            String resetLink = backendUrl + "/reset-password?token=" + resetToken;
            String subject = "Hyfata 비밀번호 재설정";
            String html = buildPasswordResetHtml(resetLink);
            sendHtmlEmail(to, subject, html);
            log.info("비밀번호 재설정 이메일 발송 성공: {} (client: {})", to, clientId);
        } catch (Exception e) {
            log.error("비밀번호 재설정 이메일 발송 실패 {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * 회원가입 이메일 인증 메일 발송 (비동기)
     */
    @Async
    public void sendEmailVerificationEmail(String to, String verificationToken, String clientId) {
        try {
            if (!mailEnabled) {
                log.warn("메일 발송이 비활성화되어 있습니다. 이메일 인증 메일을 건 넘깁니다: {}", to);
                return;
            }

            String verificationLink = backendUrl + "/verify-email?token=" + verificationToken;
            String subject = "Hyfata 이메일 인증";
            String html = buildEmailVerificationHtml(verificationLink);
            sendHtmlEmail(to, subject, html);
            log.info("이메일 인증 메일 발송 성공: {} (client: {})", to, clientId);
        } catch (Exception e) {
            log.error("이메일 인증 메일 발송 실패 {}: {}", to, e.getMessage(), e);
        }
    }

    /**
     * 계정 복구 이메일 발송 (비동기)
     */
    @Async
    public void sendAccountRestoreEmail(String to, String restoreToken, String clientId) {
        try {
            if (!mailEnabled) {
                log.warn("메일 발송이 비활성화되어 있습니다. 계정 복구 메일을 건 넘깁니다: {}", to);
                return;
            }

            String restoreLink = backendUrl + "/api/account/restore/confirm?token=" + restoreToken;
            String subject = "Hyfata 계정 복구";
            String html = buildAccountRestoreHtml(restoreLink);
            sendHtmlEmail(to, subject, html);
            log.info("계정 복구 이메일 발송 성공: {} (client: {})", to, clientId);
        } catch (Exception e) {
            log.error("계정 복구 이메일 발송 실패 {}: {}", to, e.getMessage(), e);
        }
    }

    private void sendHtmlEmail(String to, String subject, String html) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);
        mailSender.send(message);
    }

    private String buildTwoFactorHtml(String code) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'></head><body style='font-family:sans-serif;background:#f5f5f5;padding:40px 0;'>"
                + "<div style='max-width:480px;margin:0 auto;background:#fff;border-radius:8px;padding:32px;box-shadow:0 4px 12px rgba(0,0,0,0.1);'>"
                + "<h2 style='color:#001F29;margin-bottom:16px;'>Hyfata 인증 코드</h2>"
                + "<p style='color:#555;line-height:1.6;'>아래 인증 코드를 입력해 주세요.</p>"
                + "<div style='background:#f0f9ff;border:1px solid #00bcd4;border-radius:6px;padding:16px;text-align:center;margin:24px 0;'>"
                + "<span style='font-size:28px;font-weight:700;color:#00bcd4;letter-spacing:4px;'>" + code + "</span>"
                + "</div>"
                + "<p style='color:#888;font-size:13px;'>이 코드는 10분 후에 만료됩니다.</p>"
                + "</div></body></html>";
    }

    private String buildPasswordResetHtml(String resetLink) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'></head><body style='font-family:sans-serif;background:#f5f5f5;padding:40px 0;'>"
                + "<div style='max-width:480px;margin:0 auto;background:#fff;border-radius:8px;padding:32px;box-shadow:0 4px 12px rgba(0,0,0,0.1);'>"
                + "<h2 style='color:#001F29;margin-bottom:16px;'>비밀번호 재설정</h2>"
                + "<p style='color:#555;line-height:1.6;'>비밀번호 재설정을 요청하셨습니다. 아래 버튼을 클릭하여 새 비밀번호를 설정하세요.</p>"
                + "<div style='text-align:center;margin:28px 0;'>"
                + "<a href='" + resetLink + "' style='display:inline-block;background:#00bcd4;color:#fff;text-decoration:none;padding:12px 28px;border-radius:4px;font-weight:600;'>비밀번호 재설정</a>"
                + "</div>"
                + "<p style='color:#888;font-size:13px;'>이 링크는 1시간 후에 만료됩니다.<br>요청하신 적이 없다면 이 메일을 무시해 주세요.</p>"
                + "<p style='color:#aaa;font-size:12px;margin-top:16px;word-break:break-all;'>링크가 안 되면 여기로 접속하세요:<br>" + resetLink + "</p>"
                + "</div></body></html>";
    }

    private String buildEmailVerificationHtml(String verificationLink) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'></head><body style='font-family:sans-serif;background:#f5f5f5;padding:40px 0;'>"
                + "<div style='max-width:480px;margin:0 auto;background:#fff;border-radius:8px;padding:32px;box-shadow:0 4px 12px rgba(0,0,0,0.1);'>"
                + "<h2 style='color:#001F29;margin-bottom:16px;'>이메일 인증</h2>"
                + "<p style='color:#555;line-height:1.6;'>Hyfata 계정을 생성해 주셔서 감사합니다. 아래 버튼을 클릭하여 이메일 인증을 완료하세요.</p>"
                + "<div style='text-align:center;margin:28px 0;'>"
                + "<a href='" + verificationLink + "' style='display:inline-block;background:#00bcd4;color:#fff;text-decoration:none;padding:12px 28px;border-radius:4px;font-weight:600;'>이메일 인증하기</a>"
                + "</div>"
                + "<p style='color:#888;font-size:13px;'>이 링크는 24시간 후에 만료됩니다.</p>"
                + "<p style='color:#aaa;font-size:12px;margin-top:16px;word-break:break-all;'>링크가 안 되면 여기로 접속하세요:<br>" + verificationLink + "</p>"
                + "</div></body></html>";
    }

    private String buildAccountRestoreHtml(String restoreLink) {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='UTF-8'></head><body style='font-family:sans-serif;background:#f5f5f5;padding:40px 0;'>"
                + "<div style='max-width:480px;margin:0 auto;background:#fff;border-radius:8px;padding:32px;box-shadow:0 4px 12px rgba(0,0,0,0.1);'>"
                + "<h2 style='color:#001F29;margin-bottom:16px;'>계정 복구</h2>"
                + "<p style='color:#555;line-height:1.6;'>Hyfata 계정 복구를 요청하셨습니다. 아래 버튼을 클릭하면 계정이 다시 활성화됩니다.</p>"
                + "<div style='text-align:center;margin:28px 0;'>"
                + "<a href='" + restoreLink + "' style='display:inline-block;background:#00bcd4;color:#fff;text-decoration:none;padding:12px 28px;border-radius:4px;font-weight:600;'>계정 복구하기</a>"
                + "</div>"
                + "<p style='color:#888;font-size:13px;'>이 링크는 24시간 후에 만료됩니다.<br>요청하신 적이 없다면 이 메일을 무시해 주세요.</p>"
                + "<p style='color:#aaa;font-size:12px;margin-top:16px;word-break:break-all;'>링크가 안 되면 여기로 접속하세요:<br>" + restoreLink + "</p>"
                + "</div></body></html>";
    }
}
