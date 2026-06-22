package kr.hyfata.rest.api.common.config;

import kr.hyfata.rest.api.auth.entity.Client;
import kr.hyfata.rest.api.auth.entity.ClientType;
import kr.hyfata.rest.api.auth.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * First-Party OAuth 클라이언트 초기화
 * <p>
 * 애플리케이션 시작 시 설정 파일에 정의된 공식 클라이언트를 DB에 시드하거나 동기화합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FirstPartyClientInitializer implements ApplicationRunner {

    private final FirstPartyClientProperties firstPartyClientProperties;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var clients = firstPartyClientProperties.getClients();

        if (clients == null || clients.isEmpty()) {
            log.debug("No first-party clients configured");
            return;
        }

        for (FirstPartyClientProperties.ClientConfig config : clients) {
            if (!StringUtils.hasText(config.getClientId())) {
                log.warn("Skipping first-party client with empty clientId");
                continue;
            }

            if (!StringUtils.hasText(config.getClientSecret())) {
                log.warn("Skipping first-party client '{}': clientSecret is required", config.getClientId());
                continue;
            }

            Client client = clientRepository.findByClientId(config.getClientId())
                    .map(existing -> updateExistingClient(existing, config))
                    .orElseGet(() -> createNewClient(config));

            clientRepository.save(client);
            log.info("First-party client synchronized: {} (type={})", client.getClientId(), client.getClientType());
        }
    }

    private Client createNewClient(FirstPartyClientProperties.ClientConfig config) {
        return Client.builder()
                .clientId(config.getClientId())
                .clientSecret(passwordEncoder.encode(config.getClientSecret()))
                .name(config.getName())
                .description(config.getDescription())
                .frontendUrl(config.getFrontendUrl())
                .redirectUris(config.getRedirectUris())
                .enabled(config.getEnabled())
                .maxTokensPerUser(config.getMaxTokensPerUser())
                .defaultScopes(config.getDefaultScopes())
                .allowedScopes(config.getAllowedScopes())
                .clientType(ClientType.FIRST_PARTY)
                .build();
    }

    private Client updateExistingClient(Client client, FirstPartyClientProperties.ClientConfig config) {
        client.setName(config.getName());
        client.setDescription(config.getDescription());
        client.setFrontendUrl(config.getFrontendUrl());
        client.setRedirectUris(config.getRedirectUris());
        client.setEnabled(config.getEnabled());
        client.setMaxTokensPerUser(config.getMaxTokensPerUser());
        client.setDefaultScopes(config.getDefaultScopes());
        client.setAllowedScopes(config.getAllowedScopes());
        client.setClientType(ClientType.FIRST_PARTY);
        client.setUpdatedAt(LocalDateTime.now());

        // clientSecret이 설정된 경우에만 재해싱
        if (StringUtils.hasText(config.getClientSecret())) {
            client.setClientSecret(passwordEncoder.encode(config.getClientSecret()));
        }

        return client;
    }
}
