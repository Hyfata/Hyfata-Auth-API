package kr.hyfata.rest.api.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * OAuth 클라이언트 엔티티
 * 여러 웹사이트/애플리케이션이 이 API를 통해 인증을 제공받도록 함
 */
@Entity
@Table(name = "clients")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String clientId;

    @Column(nullable = false, length = 255)
    private String clientSecret;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 255)
    private String frontendUrl;

    @Column(nullable = false)
    private String redirectUris;  // JSON 형식 또는 쉼표로 구분된 URI들

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ClientType clientType = ClientType.THIRD_PARTY;

    // 접근 통제
    @Column(nullable = false)
    @Builder.Default
    private Integer maxTokensPerUser = 5;  // 사용자당 최대 토큰 수

    @Column(length = 500)
    private String defaultScopes;   // 클라이언트 기본 scope (등록 시 설정)

    @Column(length = 500)
    private String allowedScopes;   // 클라이언트가 요청할 수 있는 최대 scope

    // 소유자 연관관계
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = true)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private User owner;

    // 메타데이터
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 허용된 scope 목록 반환
     */
    public java.util.Set<String> getAllowedScopesSet() {
        return allowedScopes != null
                ? java.util.Set.of(allowedScopes.split(" "))
                : java.util.Set.of("profile", "email");
    }

    /**
     * 기본 scope 목록 반환
     */
    public java.util.Set<String> getDefaultScopesSet() {
        return defaultScopes != null
                ? java.util.Set.of(defaultScopes.split(" "))
                : java.util.Set.of("profile", "email");
    }
}
