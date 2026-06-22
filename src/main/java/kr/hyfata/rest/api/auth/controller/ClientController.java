package kr.hyfata.rest.api.auth.controller;

import kr.hyfata.rest.api.auth.dto.ClientRegistrationRequest;
import kr.hyfata.rest.api.auth.dto.ClientResponse;
import kr.hyfata.rest.api.auth.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuth 클라이언트 관리 API
 * 여러 웹사이트/애플리케이션이 이 API를 통해 인증을 제공받도록 등록
 */
@RestController
@RequestMapping("/api/clients")
@RequiredArgsConstructor
@Slf4j
public class ClientController {

    private final ClientService clientService;

    /**
     * 새로운 Third-Party 클라이언트 등록 (OAuth 앱 생성과 유사)
     * POST /api/clients/register
     *
     * 이 API로는 Third-Party 클라이언트만 등록할 수 있습니다.
     * First-Party(공식) 클라이언트는 application.properties 설정을 통해 관리됩니다.
     *
     * 요청 예시:
     * {
     *   "name": "My Web App",
     *   "description": "My web application",
     *   "frontendUrl": "https://myapp.com",
     *   "redirectUris": ["https://myapp.com/callback", "https://myapp.com/auth"],
     *   "maxTokensPerUser": 5,
     *   "defaultScopes": "profile email",
     *   "allowedScopes": "profile email profile:write account:password account:manage 2fa:manage sessions:manage"
     * }
     *
     * 응답:
     * {
     *   "id": 1,
     *   "clientId": "client_1697...",
     *   "clientSecret": "...",
     *   "name": "My Web App",
     *   ...
     * }
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerClient(
            @RequestBody ClientRegistrationRequest request,
            Authentication authentication) {
        try {
            // 필수 필드 검증
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("Client name is required");
            }
            if (request.getFrontendUrl() == null || request.getFrontendUrl().trim().isEmpty()) {
                throw new IllegalArgumentException("Frontend URL is required");
            }
            if (request.getRedirectUris() == null || request.getRedirectUris().isEmpty()) {
                throw new IllegalArgumentException("At least one redirect URI is required");
            }

            ClientResponse clientResponse = clientService.registerClient(request, authentication);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Client registered successfully");
            response.put("client", clientResponse);

            log.info("Client registered: {}", clientResponse.getClientId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IllegalArgumentException e) {
            log.warn("Client registration validation error: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
        } catch (Exception e) {
            log.error("Client registration error: {}", e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to register client: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 클라이언트 정보 조회
     * GET /api/clients/{clientId}
     */
    @GetMapping("/{clientId}")
    public ResponseEntity<Map<String, Object>> getClient(@PathVariable String clientId) {
        try {
            var clientOpt = clientService.getClient(clientId);

            if (clientOpt.isEmpty()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "Client not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("client", clientOpt.get());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error retrieving client {}: {}", clientId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to retrieve client");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * 클라이언트 존재 여부 확인
     * GET /api/clients/exists/{clientId}
     */
    @GetMapping("/exists/{clientId}")
    public ResponseEntity<Map<String, Object>> clientExists(@PathVariable String clientId) {
        try {
            boolean exists = clientService.existsClient(clientId);

            Map<String, Object> response = new HashMap<>();
            response.put("exists", exists);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error checking client existence {}: {}", clientId, e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to check client");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }
}
