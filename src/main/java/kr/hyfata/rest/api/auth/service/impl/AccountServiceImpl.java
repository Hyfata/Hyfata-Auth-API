package kr.hyfata.rest.api.auth.service.impl;

import kr.hyfata.rest.api.auth.dto.account.*;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.auth.entity.UserStatus;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.auth.service.AccountService;
import kr.hyfata.rest.api.auth.service.ClientService;
import kr.hyfata.rest.api.common.service.EmailService;
import kr.hyfata.rest.api.common.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountServiceImpl implements AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final TokenGenerator tokenGenerator;
    private final ClientService clientService;
    private static final String RESTORE_CONFIRM_TEXT = "계정을 삭제합니다";
    private static final int RESTORE_DAYS = 30;
    private static final int RESTORE_TOKEN_EXPIRATION_HOURS = 24;

    @Override
    public String changePassword(String email, ChangePasswordRequest request) {
        User user = findUserByEmail(email);

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다");
        }

        // 새 비밀번호 확인
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("새 비밀번호가 일치하지 않습니다");
        }

        // 비밀번호 변경
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return "비밀번호가 변경되었습니다";
    }

    @Override
    public String deactivateAccount(String email, DeactivateAccountRequest request) {
        User user = findUserByEmail(email);

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다");
        }

        // 계정 비활성화
        user.setStatus(UserStatus.DEACTIVATED);
        user.setDeactivatedAt(LocalDateTime.now());
        user.setDeactivationReason(request.getReason());
        userRepository.save(user);

        return "계정이 비활성화되었습니다";
    }

    @Override
    public String deleteAccount(String email, DeleteAccountRequest request) {
        User user = findUserByEmail(email);

        // 비밀번호 확인
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다");
        }

        // 확인 문구 검증
        if (!RESTORE_CONFIRM_TEXT.equals(request.getConfirmText())) {
            throw new IllegalArgumentException("확인 문구가 일치하지 않습니다");
        }

        // 계정 삭제 (소프트 삭제)
        user.setStatus(UserStatus.DELETED);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);

        return "계정이 삭제되었습니다. 30일 후 완전히 삭제됩니다";
    }

    @Override
    public String requestAccountRestore(String email, String clientId) {
        // 클라이언트 검증
        if (clientService.validateClient(clientId).isEmpty()) {
            throw new IllegalArgumentException("유효하지 않거나 비활성화된 클라이언트입니다");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));

        // 비활성화 상태 확인
        if (user.getStatus() != UserStatus.DEACTIVATED) {
            throw new IllegalArgumentException("비활성화된 계정이 아닙니다");
        }

        // 복구 가능 기간 확인 (30일)
        if (user.getDeactivatedAt() != null &&
                user.getDeactivatedAt().plusDays(RESTORE_DAYS).isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("복구 기한이 만료되었습니다");
        }

        // 복구 토큰 생성 및 저장
        String restoreToken = tokenGenerator.generateAccountRestoreToken();
        user.setRestoreToken(restoreToken);
        user.setRestoreTokenExpiredAt(LocalDateTime.now().plusHours(RESTORE_TOKEN_EXPIRATION_HOURS));
        userRepository.save(user);

        // 복구 이메일 발송
        emailService.sendAccountRestoreEmail(user.getEmail(), restoreToken, clientId);

        return "계정 복구 링크가 이메일로 발송되었습니다";
    }

    @Override
    @Transactional
    public String confirmAccountRestore(RestoreAccountConfirmRequest request) {
        User user = userRepository.findByRestoreToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않거나 만료된 복구 토큰입니다"));

        // 토큰 만료 확인
        if (user.getRestoreTokenExpiredAt() == null ||
                LocalDateTime.now().isAfter(user.getRestoreTokenExpiredAt())) {
            throw new IllegalArgumentException("복구 링크가 만료되었습니다");
        }

        // 비활성화 상태 확인
        if (user.getStatus() != UserStatus.DEACTIVATED) {
            throw new IllegalArgumentException("비활성화된 계정이 아닙니다");
        }

        // 계정 복구
        user.setStatus(UserStatus.ACTIVE);
        user.setDeactivatedAt(null);
        user.setDeactivationReason(null);
        user.setRestoreToken(null);
        user.setRestoreTokenExpiredAt(null);
        userRepository.save(user);

        return "계정이 복구되었습니다. 다시 로그인해주세요";
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다"));
    }
}
