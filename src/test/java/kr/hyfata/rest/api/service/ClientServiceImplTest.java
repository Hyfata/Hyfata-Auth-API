package kr.hyfata.rest.api.service;

import kr.hyfata.rest.api.auth.dto.ClientRegistrationRequest;
import kr.hyfata.rest.api.auth.dto.ClientResponse;
import kr.hyfata.rest.api.auth.entity.Client;
import kr.hyfata.rest.api.auth.entity.ClientType;
import kr.hyfata.rest.api.auth.entity.User;
import kr.hyfata.rest.api.auth.repository.ClientRepository;
import kr.hyfata.rest.api.auth.repository.UserRepository;
import kr.hyfata.rest.api.auth.service.impl.ClientServiceImpl;
import kr.hyfata.rest.api.common.util.TokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceImplTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenGenerator tokenGenerator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ClientServiceImpl clientService;

    private ClientRegistrationRequest request;

    @BeforeEach
    void setUp() {
        request = ClientRegistrationRequest.builder()
                .name("Test Client")
                .frontendUrl("https://test.com")
                .redirectUris(List.of("https://test.com/callback"))
                .defaultScopes("profile email account:manage")
                .allowedScopes("profile email account:manage 2fa:manage")
                .build();

        when(tokenGenerator.generatePasswordResetToken()).thenReturn("plain_secret");
        when(passwordEncoder.encode(any())).thenReturn("hashed_secret");
        when(clientRepository.existsByClientId(any())).thenReturn(false);
        when(clientRepository.save(any(Client.class))).thenAnswer(i -> {
            Client c = i.getArgument(0);
            c.setId(1L);
            return c;
        });
    }

    @Test
    @DisplayName("관리자가 클라이언트 등록 시 요청한 scope 그대로 적용")
    void registerClient_admin_allowsCustomScopes() {
        // given
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // when
        ClientResponse response = clientService.registerClient(request, adminAuth);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getDefaultScopes()).isEqualTo("profile email account:manage");
        assertThat(response.getAllowedScopes()).isEqualTo("profile email account:manage 2fa:manage");
    }

    @Test
    @DisplayName("비관리자가 클라이언트 등록 시 scope가 profile email로 강제 제한")
    void registerClient_nonAdmin_forcesDefaultScopes() {
        // given
        Authentication userAuth = new UsernamePasswordAuthenticationToken(
                "user@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        // when
        ClientResponse response = clientService.registerClient(request, userAuth);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getDefaultScopes()).isEqualTo("profile email");
        assertThat(response.getAllowedScopes()).isEqualTo("profile email");
    }

    @Test
    @DisplayName("익명 사용자가 클라이언트 등록 시도 시 scope가 profile email로 강제 제한")
    void registerClient_anonymous_forcesDefaultScopes() {
        // when
        ClientResponse response = clientService.registerClient(request, null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getDefaultScopes()).isEqualTo("profile email");
        assertThat(response.getAllowedScopes()).isEqualTo("profile email");
    }

    @Test
    @DisplayName("관리자가 scope를 지정하지 않으면 기본값 적용")
    void registerClient_adminWithoutScopes_usesDefault() {
        // given
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        request.setDefaultScopes(null);
        request.setAllowedScopes(null);

        // when
        ClientResponse response = clientService.registerClient(request, adminAuth);

        // then
        assertThat(response.getDefaultScopes()).isEqualTo("profile email");
        assertThat(response.getAllowedScopes()).isEqualTo("profile email");
    }

    @Test
    @DisplayName("API로 등록된 클라이언트는 항상 THIRD_PARTY 타입")
    void registerClient_alwaysThirdPartyType() {
        // given
        Authentication adminAuth = new UsernamePasswordAuthenticationToken(
                "admin@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        // when
        ClientResponse response = clientService.registerClient(request, adminAuth);

        // then
        assertThat(response.getClientType()).isEqualTo(ClientType.THIRD_PARTY);
    }
}
