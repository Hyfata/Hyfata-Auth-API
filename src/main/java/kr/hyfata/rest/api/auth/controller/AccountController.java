package kr.hyfata.rest.api.auth.controller;

import kr.hyfata.rest.api.auth.dto.account.*;
import kr.hyfata.rest.api.auth.service.AccountService;
import kr.hyfata.rest.api.common.security.scope.RequireScope;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    /**
     * PUT /api/account/password - 비밀번호 변경
     */
    @PutMapping("/password")
    @RequireScope("account:password")
    public ResponseEntity<?> changePassword(
            Authentication authentication,
            @RequestBody ChangePasswordRequest request) {

        String email = authentication.getName();
        String message = accountService.changePassword(email, request);

        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/account/deactivate - 계정 비활성화
     */
    @PostMapping("/deactivate")
    @RequireScope("account:manage")
    public ResponseEntity<?> deactivateAccount(
            Authentication authentication,
            @RequestBody DeactivateAccountRequest request) {

        String email = authentication.getName();
        String message = accountService.deactivateAccount(email, request);

        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    /**
     * DELETE /api/account - 계정 영구 삭제
     */
    @DeleteMapping
    @RequireScope("account:manage")
    public ResponseEntity<?> deleteAccount(
            Authentication authentication,
            @RequestBody DeleteAccountRequest request) {

        String email = authentication.getName();
        String message = accountService.deleteAccount(email, request);

        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/account/restore/request - 계정 복구 요청 (이메일 발송)
     */
    @PostMapping("/restore/request")
    public ResponseEntity<?> requestAccountRestore(@RequestBody RestoreAccountRequest request) {
        String message = accountService.requestAccountRestore(request.getEmail(), request.getClientId());

        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/account/restore/confirm - 계정 복구 확인 (이메일 링크)
     */
    @GetMapping("/restore/confirm")
    public ResponseEntity<?> confirmAccountRestore(@RequestParam String token) {
        RestoreAccountConfirmRequest request = RestoreAccountConfirmRequest.builder()
                .token(token)
                .build();
        String message = accountService.confirmAccountRestore(request);

        Map<String, String> response = new HashMap<>();
        response.put("message", message);
        return ResponseEntity.ok(response);
    }
}
