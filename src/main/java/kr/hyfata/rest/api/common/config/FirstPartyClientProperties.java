package kr.hyfata.rest.api.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * First-Party OAuth 클라이언트 설정
 * <p>
 * application.properties 또는 환경 변수로 공식 클라이언트 정보를 주입받습니다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.first-party")
public class FirstPartyClientProperties {

    private List<ClientConfig> clients = new ArrayList<>();

    @Getter
    @Setter
    public static class ClientConfig {
        private String clientId;
        private String clientSecret;
        private String name;
        private String description;
        private String frontendUrl;
        private String redirectUris;
        private String defaultScopes;
        private String allowedScopes;
        private Integer maxTokensPerUser = 5;
        private Boolean enabled = true;
    }
}
