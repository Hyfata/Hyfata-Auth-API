package kr.hyfata.rest.api.config;

import kr.hyfata.rest.api.auth.entity.Client;
import kr.hyfata.rest.api.auth.entity.ClientType;
import kr.hyfata.rest.api.auth.repository.ClientRepository;
import kr.hyfata.rest.api.common.config.FirstPartyClientProperties;
import kr.hyfata.rest.api.common.config.FirstPartyClientInitializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FirstPartyClientInitializerTest {

    @Mock
    private FirstPartyClientProperties firstPartyClientProperties;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private FirstPartyClientInitializer initializer;

    @Test
    @DisplayName("설정된 First-Party 클라이언트가 DB에 없으면 생성")
    void run_newClient_createsFirstPartyClient() throws Exception {
        // given
        FirstPartyClientProperties.ClientConfig config = new FirstPartyClientProperties.ClientConfig();
        config.setClientId("hyfata-official-web");
        config.setClientSecret("secret");
        config.setName("Hyfata Official Web");
        config.setFrontendUrl("https://hyfata.kr");
        config.setRedirectUris("https://hyfata.kr/oauth/callback");
        config.setDefaultScopes("profile email");
        config.setAllowedScopes("profile email");

        when(firstPartyClientProperties.getClients()).thenReturn(List.of(config));
        when(clientRepository.findByClientId("hyfata-official-web")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed_secret");
        when(clientRepository.save(any(Client.class))).thenAnswer(i -> i.getArgument(0));

        // when
        initializer.run(null);

        // then
        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        Client saved = captor.getValue();

        assertThat(saved.getClientId()).isEqualTo("hyfata-official-web");
        assertThat(saved.getClientType()).isEqualTo(ClientType.FIRST_PARTY);
        assertThat(saved.getName()).isEqualTo("Hyfata Official Web");
    }

    @Test
    @DisplayName("기존 클라이언트가 있으면 First-Party로 업데이트")
    void run_existingClient_updatesToFirstParty() throws Exception {
        // given
        FirstPartyClientProperties.ClientConfig config = new FirstPartyClientProperties.ClientConfig();
        config.setClientId("hyfata-official-web");
        config.setClientSecret("new_secret");
        config.setName("Hyfata Official Web");
        config.setFrontendUrl("https://hyfata.kr");
        config.setRedirectUris("https://hyfata.kr/oauth/callback");
        config.setDefaultScopes("profile email");
        config.setAllowedScopes("profile email");

        Client existing = Client.builder()
                .clientId("hyfata-official-web")
                .clientType(ClientType.THIRD_PARTY)
                .build();

        when(firstPartyClientProperties.getClients()).thenReturn(List.of(config));
        when(clientRepository.findByClientId("hyfata-official-web")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode(any())).thenReturn("hashed_secret");
        when(clientRepository.save(any(Client.class))).thenAnswer(i -> i.getArgument(0));

        // when
        initializer.run(null);

        // then
        assertThat(existing.getClientType()).isEqualTo(ClientType.FIRST_PARTY);
        assertThat(existing.getClientSecret()).isEqualTo("hashed_secret");
        verify(clientRepository).save(existing);
    }

    @Test
    @DisplayName("clientSecret이 없으면 First-Party 클라이언트 생성 스킵")
    void run_missingSecret_skipsClient() throws Exception {
        // given
        FirstPartyClientProperties.ClientConfig config = new FirstPartyClientProperties.ClientConfig();
        config.setClientId("hyfata-official-web");
        config.setClientSecret(null);

        when(firstPartyClientProperties.getClients()).thenReturn(List.of(config));

        // when
        initializer.run(null);

        // then
        verify(clientRepository, never()).save(any());
    }
}
