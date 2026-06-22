# OAuth 2.0 Scopes 설계 문서

> **Status**: Design Draft  
> **Goal**: 타사(Third-Party) 클라이언트와 공식(Official) 클라이언트의 권한을 Scope 단위로 분리하여, 민감한 사용자 정보 변경은 오직 공식 사이트에서만 가능하도록 한다.

---

## 1. 배경 및 문제 정의

현재 시스템은 **모든 OAuth 클라이언트를 동등하게 취급**합니다. 타사 앱이 발급받은 JWT로도 다음 API를 호출할 수 있습니다.

- `POST /api/account/password` — 비밀번호 변경
- `POST /api/auth/enable-2fa` / `disable-2fa` — 2FA 토글
- `POST /api/account/deactivate` — 계정 비활성화
- `DELETE /api/account` — 계정 탈퇴

이는 보안상 큰 문제입니다. 사용자가 타사 앱에 로그인했을 때, 그 앱이 사용자의 비밀번호를 바꾸거나 2FA를 끌 수 있으면 안 됩니다.

### 현재 JWT 구조의 한계

현재 Access Token 클레임:
```json
{
  "sub": "user@example.com",
  "jti": "...",
  "iat": 1234567890,
  "exp": 1234568790
}
```

**토큰이 어떤 클라이언트(client_id)에서 발급되었는지, 어떤 권한(scope)을 가지는지 전혀 알 수 없습니다.**

---

## 2. 설계 목표

1. **OAuth 표준 준수**: RFC 6749의 Scope 개념을 따른다.
2. **역할 기반 접근 제어**: 공식 사이트는 전체 권한, 타사 앱은 제한된 권한만 부여.
3. **하위 호환성 유지**: 기존 클라이언트 앱은 점진적으로 마이그레이션할 수 있도록 단계적 도입.
4. **Flutter 연동 친화적**: 모바일 앱에서도 Scope 요청/처리가 간단해야 함.

---

## 3. Scope 정의

### 3.1 기본 Scope (OpenID Connect 스타일)

| Scope | 설명 | 타사 앱 기본 | 공식 사이트 |
|-------|------|-------------|------------|
| `openid` | 사용자 식별자(sub) 접근 | ✅ | ✅ |
| `profile` | 기본 프로필 조회 (username, displayName) | ✅ | ✅ |
| `email` | 이메일 주소 및 인증 상태 접근 | ✅ | ✅ |

### 3.2 민감 Scope (공식 사이트 전용 권장)

| Scope | 설명 | 타사 앱 | 공식 사이트 |
|-------|------|---------|------------|
| `profile:write` | 프로필 수정 (username, 이름 등) | ❌ | ✅ |
| `account:password` | 비밀번호 변경 | ❌ | ✅ |
| `account:manage` | 계정 비활성화/탈퇴 | ❌ | ✅ |
| `2fa:manage` | 2FA 활성화/비활성화 | ❌ | ✅ |
| `sessions:manage` | 세션 목록 조회 및 원격 로그아웃 | ❌ | ✅ |

### 3.3 Scope 상속 및 포함 관계

```
profile        → profile:read (implicit)
profile:write  → profile:read 포함 (write 권한이 있으면 read도 가능)
account:manage → account:password 포함 (계정 관리자는 비밀번호 변경 가능)
```

---

## 4. 아키텍처 변경 설계

### 4.1 개념도 (변경 후)

```
┌─────────────┐     ┌─────────────────────────────┐     ┌─────────────┐
│   Client    │────▶│  /oauth/authorize           │────▶│  User       │
│ Application │     │  ?scope=profile:read+email  │     │ (로그인)    │
└─────────────┘     └─────────────────────────────┘     └──────┬──────┘
                                                               │
                          ┌────────────────────────────────────┘
                          ▼
              ┌───────────────────────┐
              │  Authorization Code   │  ← scope 저장
              └───────────┬───────────┘
                          │
              ┌───────────▼───────────┐
              │  POST /oauth/token    │
              │  scope + client_id    │
              └───────────┬───────────┘
                          ▼
              ┌───────────────────────┐
              │  JWT Access Token     │
              │  {                    │
              │    "sub": "...",      │
              │    "scope": "profile  │
              │            :read      │
              │            email",    │
              │    "client_id":       │
              │            "..."      │
              │  }                    │
              └───────────┬───────────┘
                          ▼
              ┌───────────────────────┐
              │  API Gateway/Filter   │
              │  scope 검증           │
              └───────────┬───────────┘
                          ▼
              ┌───────────────────────┐
              │  /api/account/password│  ← account:password 필요
              │  403 Forbidden        │     (타사 앱은 거부)
              └───────────────────────┘
```

---

## 5. 데이터 모델 변경

### 5.1 `clients` 테이블

```sql
-- 기존
ALTER TABLE clients ADD COLUMN IF NOT EXISTS default_scopes VARCHAR(500);
-- 예: "profile:read email" (클라이언트 등록 시 기본 scope)

ALTER TABLE clients ADD COLUMN IF NOT EXISTS allowed_scopes VARCHAR(500);
-- 예: "profile:read email profile:write account:password ..."
-- 이 클라이언트가 요청할 수 있는 최대 scope 범위
```

### 5.2 `authorization_codes` 테이블

```sql
-- Authorization Code 발급 시 사용자가 승인한 scope 저장
ALTER TABLE authorization_codes ADD COLUMN IF NOT EXISTS scopes VARCHAR(500);
```

### 5.3 `user_sessions` 테이블

```sql
-- 세션별 발급된 scope 기록 (토큰 갱신 시 동일 scope 유지)
ALTER TABLE user_sessions ADD COLUMN IF NOT EXISTS scopes VARCHAR(500);
```

---

## 6. 엔티티 변경

### 6.1 `Client.java`

```java
@Column(length = 500)
private String defaultScopes;   // 클라이언트 기본 scope (등록 시 설정)

@Column(length = 500)
private String allowedScopes;   // 클라이언트가 요청할 수 있는 최대 scope

// 헬퍼 메서드
public Set<String> getAllowedScopesSet() {
    return allowedScopes != null 
        ? Set.of(allowedScopes.split(" ")) 
        : Set.of("profile:read", "email");
}
```

### 6.2 `AuthorizationCode.java`

```java
@Column(length = 500)
private String scopes;
```

### 6.3 `UserSession.java`

```java
@Column(length = 500)
private String scopes;
```

---

## 7. OAuth 흐름 변경

### 7.1 Step 1: `/oauth/authorize`

**요청 파라미터에 `scope` 추가:**

```http
GET /oauth/authorize?client_id=client_001
  &redirect_uri=https://myapp.com/callback
  &response_type=code
  &state=abc123
  &scope=profile:read+email
```

| Parameter | Required | Description |
|-----------|----------|-------------|
| `scope` | X | 요청 scope (공백 또는 `+`로 구분). 미입력 시 클라이언트의 `defaultScopes` 사용 |

**클라이언트 검증 로직 추가:**
1. `client_id`의 `allowedScopes` 확인
2. 요청된 `scope`가 `allowedScopes`에 포함되는지 검증
3. 포함되지 않는 scope가 있으면 `invalid_scope` 에러 반환

**Authorization Code 저장 시 `scopes` 필드에 사용자가 승인한 scope 저장:**
- 현재는 로그인만 하면 자동 승인되므로, scope 요청 시에도 동일하게 처리
- 향후 **Consent 화면** ("이 앱이 ~에 접근하려 합니다. 동의하시겠습니까?") 도입 시, 사용자가 체크한 scope만 저장

### 7.2 Step 2: `/oauth/token`

**Access Token 발급 시:**
1. `AuthorizationCode.scopes`에서 scope 정보를 읽음
2. JWT 클레임에 `"scope": "profile:read email"` 추가
3. `UserSession` 생성 시 `scopes` 필드 저장 (토큰 갱신 시 동일 scope 유지)
4. 응답의 `scope` 필드에 발급된 scope 반환 (이미 구현됨)

**Refresh Token 갱신 시:**
1. 기존 세션의 `scopes`를 그대로 유지
2. 새 Access Token에 동일한 scope 클레임 포함
- 사용자는 refresh를 통해 scope를 **늘리거나 줄일 수 없음**

---

## 8. JWT 구조 변경

### 8.1 Access Token 클레임 (변경 후)

```json
{
  "sub": "user@example.com",
  "jti": "a1b2c3d4...",
  "client_id": "client_001",
  "scope": "profile:read email",
  "iat": 1714819200,
  "exp": 1714820100
}
```

### 8.2 `JwtUtil` 변경 포인트

```java
// 토큰 생성 시 scope 추가
public TokenResult generateAccessTokenWithJti(User user, String clientId, Set<String> scopes) {
    String jti = UUID.randomUUID().toString();
    String scopeStr = String.join(" ", scopes);
    
    String token = Jwts.builder()
        .subject(user.getEmail())
        .claim("jti", jti)
        .claim("client_id", clientId)
        .claim("scope", scopeStr)
        // ... 기존 클레임
        .signWith(key, SignatureAlgorithm.HS512)
        .compact();
    
    return new TokenResult(token, jti);
}

// 토큰에서 scope 추출
public Set<String> extractScopes(String token) {
    String scopeStr = extractClaim(token, claims -> claims.get("scope", String.class));
    return scopeStr != null ? new HashSet<>(Arrays.asList(scopeStr.split(" "))) : Set.of();
}

// 토큰에서 client_id 추출
public String extractClientId(String token) {
    return extractClaim(token, claims -> claims.get("client_id", String.class));
}
```

---

## 9. API 접근 제어 메커니즘

### 9.1 방법 1: `@RequireScope` 커스텀 어노테이션 (추천)

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireScope {
    String[] value();      // 필요한 scope (OR 조건)
    String[] all() default {};  // 필요한 scope (AND 조건, optional)
}
```

**사용 예시:**

```java
@RestController
@RequestMapping("/api/account")
public class AccountController {

    @PutMapping("/password")
    @RequireScope("account:password")
    public ResponseEntity<?> changePassword(...) { ... }

    @PostMapping("/deactivate")
    @RequireScope("account:manage")
    public ResponseEntity<?> deactivateAccount(...) { ... }
}

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/enable-2fa")
    @RequireScope("2fa:manage")
    public ResponseEntity<?> enableTwoFactor(...) { ... }

    @PostMapping("/disable-2fa")
    @RequireScope("2fa:manage")
    public ResponseEntity<?> disableTwoFactor(...) { ... }
}
```

**Aspect/Interceptor 구현:**

```java
@Component
@Aspect
public class ScopeAuthorizationAspect {

    @Autowired
    private JwtUtil jwtUtil;

    @Around("@annotation(requireScope)")
    public Object checkScope(ProceedingJoinPoint joinPoint, RequireScope requireScope) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String jwt = extractJwtFromRequest(request);
        
        Set<String> tokenScopes = jwtUtil.extractScopes(jwt);
        Set<String> requiredScopes = Set.of(requireScope.value());
        
        // 포함 관계 체크 (profile:write 가 있으면 profile:read 도 암시적 허용)
        boolean hasScope = requiredScopes.stream().anyMatch(req -> 
            tokenScopes.contains(req) || hasImplicitScope(tokenScopes, req)
        );
        
        if (!hasScope) {
            throw new AccessDeniedException("Insufficient scope. Required: " + requiredScopes);
        }
        
        return joinPoint.proceed();
    }
    
    private boolean hasImplicitScope(Set<String> tokenScopes, String required) {
        // profile:write → profile:read 암시적 포함
        if (required.equals("profile:read") && tokenScopes.contains("profile:write")) return true;
        // account:manage → account:password 암시적 포함
        if (required.equals("account:password") && tokenScopes.contains("account:manage")) return true;
        return false;
    }
}
```

### 9.2 방법 2: Spring Security `@PreAuthorize` (대안)

```java
@PreAuthorize("hasAuthority('SCOPE_account:password')")
@PutMapping("/password")
public ResponseEntity<?> changePassword(...) { ... }
```

- **장점**: Spring Security 표준, 추가 코드 거의 없음
- **단점**: JWT의 scope를 Spring Security `GrantedAuthority`로 변환해야 함. `JwtAuthenticationFilter`에서 `authentication.getAuthorities()`에 scope를 넣어야 함.

**현재 코드베이스에서는 방법 1(`@RequireScope` AOP)이 덜 침습적이고 명확합니다.**

---

## 10. 공식 클라이언트 vs 타사 클라이언트 구분

### 10.1 `Client` 등록 시 scope 설정

**공식(First-Party) 클라이언트**는 `application.properties` 또는 환경 변수로 관리되며, 애플리케이션 시작 시 `FirstPartyClientInitializer`가 DB에 시드합니다.

```properties
app.first-party.clients[0].client-id=${OFFICIAL_WEB_CLIENT_ID:hyfata-official-web}
app.first-party.clients[0].client-secret=${OFFICIAL_WEB_CLIENT_SECRET}
app.first-party.clients[0].name=${OFFICIAL_WEB_CLIENT_NAME:Hyfata Official Web}
app.first-party.clients[0].frontend-url=${OFFICIAL_WEB_FRONTEND_URL:https://hyfata.kr}
app.first-party.clients[0].redirect-uris=${OFFICIAL_WEB_REDIRECT_URIS:https://hyfata.kr/oauth/callback}
app.first-party.clients[0].default-scopes=${OFFICIAL_WEB_DEFAULT_SCOPES:profile email profile:write account:password account:manage 2fa:manage sessions:manage}
app.first-party.clients[0].allowed-scopes=${OFFICIAL_WEB_ALLOWED_SCOPES:profile email profile:write account:password account:manage 2fa:manage sessions:manage}
```

**타사(Third-Party) 클라이언트**는 `POST /api/clients/register` API를 통해 등록되며, `clientType`은 `THIRD_PARTY`로 강제 설정됩니다.

```java
// 타사 클라이언트 등록 예시
Client thirdPartyClient = Client.builder()
    .name("Third Party App")
    .clientId("client_third_001")
    // ...
    .clientType(ClientType.THIRD_PARTY)
    .defaultScopes("profile:read email")
    .allowedScopes("profile:read email friends:read chat:read chat:write notifications:read")
    .build();
```

### 10.2 클라이언트 scope 요청 제한

`/oauth/authorize`에서 타사 클라이언트가 `account:manage`를 요청하면:

```json
{
  "error": "invalid_scope",
  "error_description": "Requested scope 'account:manage' exceeds client's allowed scopes"
}
```

---

## 11. Flutter 클라이언트 연동 가이드

### 11.1 공식 앱의 Authorization 요청

```dart
// 공식 앱: 전체 scope 요청
final scope = 'profile:read email profile:write account:password account:manage 2fa:manage sessions:manage';

final authUrl = Uri.parse('https://api.hyfata.kr/oauth/authorize')
  .replace(queryParameters: {
    'client_id': 'client_official_001',
    'redirect_uri': 'com.hyfata.app://callback',
    'response_type': 'code',
    'state': state,
    'code_challenge': codeChallenge,
    'code_challenge_method': 'S256',
    'scope': scope,  // ← 추가
  });
```

### 11.2 타사 앱의 Authorization 요청

```dart
// 타사 앱: 제한된 scope만 요청
final scope = 'profile:read email';

final authUrl = Uri.parse('https://api.hyfata.kr/oauth/authorize')
  .replace(queryParameters: {
    'client_id': 'client_third_001',
    // ...
    'scope': scope,
  });
```

### 11.3 Scope 부족 시 처리

API 호출 시 `403 Forbidden` + `"Insufficient scope"` 응답을 받으면, 사용자에게 "해당 기능은 공식 앱에서만 사용할 수 있습니다" 안내.

---

## 12. 구현 단계별 로드맵

### Phase 1: 데이터 모델 ✅
- [x] `Client` 엔티티에 `defaultScopes`, `allowedScopes` 추가
- [x] `AuthorizationCode` 엔티티에 `scopes` 추가
- [x] `UserSession` 엔티티에 `scopes` 추가
- [x] DB 마이그레이션 파일 작성 (`V6__add_client_type.sql` 추가, scope 필드는 JPA `ddl-auto=update`로 적용)
- [x] `JwtUtil`에 `client_id`, `scope` 클레임 생성/추출 메서드 추가

### Phase 2: OAuth 흐름 통합 ✅
- [x] `/oauth/authorize`에 `scope` 파라미터 파싱 및 검증
- [x] 클라이언트의 `allowedScopes` 초과 요청 시 `invalid_scope` 에러 반환
- [x] `/oauth/token`에서 JWT에 `client_id`, `scope` 클레임 포함
- [x] `UserSession` 생성 시 `scopes` 필드 저장
- [x] Refresh Token 갱신 시 기존 `scopes` 유지

### Phase 3: API 접근 제어 ✅
- [x] `@RequireScope` 어노테이션 및 AOP 인터셉터 구현
- [x] 민감 API에 어노테이션 적용:
  - `/api/account/**` → `account:password`, `account:manage`
  - `/api/auth/enable-2fa`, `/disable-2fa` → `2fa:manage`
  - `/api/sessions/**` → `sessions:manage`
- [x] `security.sensitive-endpoints`에 위 경로 추가 (토큰 블랙리스트 연동)

### Phase 4: 클라이언트 관리 및 가이드 업데이트 ✅
- [x] First-Party / Third-Party 클라이언트 구분 (`ClientType` 추가)
- [x] First-Party 클라이언트를 `application.properties` 설정으로 시드 (`FirstPartyClientInitializer`)
- [x] `/api/clients/register` API는 Third-Party 클라이언트만 생성 가능하도록 제한
- [x] Flutter 공식 앱에 `scope` 파라미터 추가
- [x] `docs/auth/FLUTTER_OAUTH_GUIDE.md` 업데이트

### Phase 5: Consent 화면 (선택, 향후)
- [ ] `/oauth/authorize`에서 타사 클라이언트 로그인 시 scope 동의 화면 제공
- [ ] 사용자가 scope를 선택적으로 체크/해제할 수 있도록 UI 제공
- [ ] `AuthorizationCode.scopes`에 사용자가 동의한 scope만 저장

---

## 13. 운영 마이그레이션 참고사항

이미 운영 중인 DB가 있고, 기존 `clients` 레코드에 `defaultScopes` / `allowedScopes`가 비어 있거나 `profile:read email` 등 예전 형식으로 되어 있다면, 다음 SQL을 참고해 수동으로 보정하세요.

```sql
-- 타사 클라이언트를 최소 권한으로 설정
UPDATE clients
SET default_scopes = 'profile email',
    allowed_scopes = 'profile email'
WHERE client_type = 'THIRD_PARTY'
  AND (default_scopes IS NULL OR default_scopes = '');

-- 공식 클라이언트는 설정 파일(FirstPartyClientInitializer)로 시드되므로
-- 별도 수동 보정은 일반적으로 필요 없습니다.
```

---

## 13. 보안 고려사항

### 13.1 Scope 탈취 방지
- Access Token은 짧게 (15분). 탈취되어도 위험 최소화.
- Refresh Token 갱신 시 `scope` 변경 불가. 탈취된 refresh로 권한 상승 불가.

### 13.2 하위 호환성
- **기존 클라이언트 앱**은 `scope` 파라미터를 보내지 않음 → `defaultScopes`를 자동 적용.
- 마이그레이션 기간 동안 기존 클라이언트의 `defaultScopes`를 관대하게 설정(예: `profile:read email`)하여 서비스 중단 없이 점진적으로 제한.

### 13.3 Token Blacklist 연동
- 민감 scope를 요구하는 엔드포인트는 반드시 `sensitive-endpoints`에 등록하여, revoked token으로의 접근을 차단.

---

## 14. 관련 문서

- [AUTH_API.md](AUTH_API.md) — API 스펙
- [FLUTTER_OAUTH_GUIDE.md](FLUTTER_OAUTH_GUIDE.md) — Flutter 연동 가이드
- RFC 6749 Section 3.3 — Access Token Scope
