package kr.hyfata.rest.api.auth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(length = 100)
    private String firstName;

    @Column(length = 100)
    private String lastName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean accountNonLocked = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean credentialsNonExpired = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean accountNonExpired = true;

    // 2FA 관련 필드
    @Column(nullable = false)
    @Builder.Default
    private Boolean twoFactorEnabled = false;

    @Column(unique = true, length = 20)
    private String twoFactorCode;

    @Column
    private LocalDateTime twoFactorCodeExpiredAt;

    // 비밀번호 재설정 관련 필드
    @Column(unique = true)
    private String resetPasswordToken;

    @Column
    private LocalDateTime resetPasswordTokenExpiredAt;

    // 이메일 검증
    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(unique = true)
    private String emailVerificationToken;

    // 계정 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String role = "ROLE_USER";  // ROLE_USER, ROLE_ADMIN

    // 비활성화 관련
    private LocalDateTime deactivatedAt;

    @Column(columnDefinition = "TEXT")
    private String deactivationReason;

    // 삭제 관련
    private LocalDateTime deletedAt;

    // 계정 복구 관련
    @Column(unique = true)
    private String restoreToken;

    @Column
    private LocalDateTime restoreTokenExpiredAt;

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

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    /**
     * Spring Security의 UserDetails.getUsername() 구현
     * JWT 토큰이 email을 subject로 사용하므로 email을 반환
     */
    @Override
    public String getUsername() {
        return email;
    }

    /**
     * 실제 username 필드 접근용 메서드
     */
    public String getDisplayName() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled && status == UserStatus.ACTIVE;
    }
}