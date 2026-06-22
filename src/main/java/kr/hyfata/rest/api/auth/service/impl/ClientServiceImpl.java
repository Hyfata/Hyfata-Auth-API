package kr.hyfata.rest.api.auth.service.impl;

import kr.hyfata.rest.api.auth.dto.ClientRegistrationRequest;
import kr.hyfata.rest.api.auth.dto.ClientResponse;
import kr.hyfata.rest.api.auth.entity.Client;
import kr.hyfata.rest.api.auth.entity.ClientType;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.auth.repository.ClientRepository;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.auth.service.ClientService;
import kr.hyfata.rest.api.common.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final TokenGenerator tokenGenerator;
    private final PasswordEncoder passwordEncoder;

    @Override
    public ClientResponse registerClient(ClientRegistrationRequest request, Authentication authentication) {
        // 관리자 여부 확인
        boolean isAdmin = authentication != null && authentication.getAuthorities()
                .contains(new SimpleGrantedAuthority("ROLE_ADMIN"));

        // clientId와 clientSecret 생성
        String clientId = generateUniqueClientId();
        String clientSecret = tokenGenerator.generatePasswordResetToken();  // 긴 난수 토큰 사용
        String hashedClientSecret = passwordEncoder.encode(clientSecret);  // BCrypt로 해싱

        // redirectUris를 쉼표로 구분된 문자열로 변환
        String redirectUrisStr = String.join(",", request.getRedirectUris());

        // scope 설정: 비관리자는 profile email로 강제 제한
        String defaultScopes;
        String allowedScopes;
        if (isAdmin) {
            defaultScopes = (request.getDefaultScopes() != null && !request.getDefaultScopes().isBlank())
                    ? request.getDefaultScopes()
                    : "profile email";
            allowedScopes = (request.getAllowedScopes() != null && !request.getAllowedScopes().isBlank())
                    ? request.getAllowedScopes()
                    : defaultScopes;
        } else {
            defaultScopes = "profile email";
            allowedScopes = "profile email";
            if (request.getDefaultScopes() != null || request.getAllowedScopes() != null) {
                log.warn("Non-admin user attempted to set custom scopes. Forced to 'profile email'. user={}",
                        authentication != null ? authentication.getName() : "anonymous");
            }
        }

        Client client = Client.builder()
                .clientId(clientId)
                .clientSecret(hashedClientSecret)  // 해싱된 값 저장
                .name(request.getName())
                .description(request.getDescription())
                .frontendUrl(request.getFrontendUrl())
                .redirectUris(redirectUrisStr)
                .enabled(true)
                .maxTokensPerUser(request.getMaxTokensPerUser() != null ? request.getMaxTokensPerUser() : 5)
                .defaultScopes(defaultScopes)
                .allowedScopes(allowedScopes)
                .clientType(ClientType.THIRD_PARTY)  // API로 생성되는 클라이언트는 모두 Third-Party
                .build();

        // 소유자 설정 (optional)
        if (request.getOwnerId() != null) {
            User owner = userRepository.findById(request.getOwnerId())
                    .orElseThrow(() -> new IllegalArgumentException("Owner user not found with id: " + request.getOwnerId()));
            client.setOwner(owner);
        }

        Client savedClient = clientRepository.save(client);
        log.info("Client registered: {} ({}) by user={} (admin={})", request.getName(), clientId,
                authentication != null ? authentication.getName() : "anonymous", isAdmin);

        // 생성 시에만 평문 clientSecret을 응답에 포함
        return mapToResponseWithSecret(savedClient, clientSecret);
    }

    @Override
    public Optional<ClientResponse> getClient(String clientId) {
        return clientRepository.findByClientId(clientId)
                .map(this::mapToResponse);
    }

    @Override
    public Optional<Client> validateClient(String clientId) {
        Optional<Client> client = clientRepository.findByClientId(clientId);

        if (client.isEmpty()) {
            log.warn("Client not found: {}", clientId);
            return Optional.empty();
        }

        if (!client.get().getEnabled()) {
            log.warn("Client is disabled: {}", clientId);
            return Optional.empty();
        }

        return client;
    }

    @Override
    public boolean existsClient(String clientId) {
        return clientRepository.existsByClientId(clientId);
    }

    /**
     * 고유한 clientId 생성
     */
    private String generateUniqueClientId() {
        String clientId;
        do {
            clientId = "client_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 10000);
        } while (clientRepository.existsByClientId(clientId));

        return clientId;
    }

    /**
     * Client 엔티티를 ClientResponse DTO로 변환 (clientSecret 제외)
     */
    private ClientResponse mapToResponse(Client client) {
        List<String> redirectUris = List.of(client.getRedirectUris().split(","));

        return ClientResponse.builder()
                .id(client.getId())
                .clientId(client.getClientId())
                .name(client.getName())
                .description(client.getDescription())
                .frontendUrl(client.getFrontendUrl())
                .redirectUris(redirectUris)
                .enabled(client.getEnabled())
                .maxTokensPerUser(client.getMaxTokensPerUser())
                .defaultScopes(client.getDefaultScopes())
                .allowedScopes(client.getAllowedScopes())
                .clientType(client.getClientType())
                .ownerId(client.getOwner() != null ? client.getOwner().getId() : null)
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt())
                .build();
    }

    /**
     * Client 엔티티를 ClientResponse DTO로 변환 (생성 시에만 평문 clientSecret 포함)
     */
    private ClientResponse mapToResponseWithSecret(Client client, String plainClientSecret) {
        List<String> redirectUris = List.of(client.getRedirectUris().split(","));

        return ClientResponse.builder()
                .id(client.getId())
                .clientId(client.getClientId())
                .clientSecret(plainClientSecret)  // 생성 시에만 평문 반환
                .name(client.getName())
                .description(client.getDescription())
                .frontendUrl(client.getFrontendUrl())
                .redirectUris(redirectUris)
                .enabled(client.getEnabled())
                .maxTokensPerUser(client.getMaxTokensPerUser())
                .defaultScopes(client.getDefaultScopes())
                .allowedScopes(client.getAllowedScopes())
                .clientType(client.getClientType())
                .ownerId(client.getOwner() != null ? client.getOwner().getId() : null)
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt())
                .build();
    }
}
