# OAuth 2.0 Scope API 가이드

> 이 문서는 Hyfata API의 OAuth 2.0 Scope 체계를 사용하는 **클아이언트 개발자**를 위한 실무 가이드입니다.
> 설계 문서는 [OAUTH_SCOPES_DESIGN.md](OAUTH_SCOPES_DESIGN.md)를 참고하세요.

---

## 목차

1. [개요](#개요)
2. [Scope 목록](#scope-목록)
3. [Scope → API 매핑](#scope--api-매핑)
4. [클아이언트 등록 및 Scope 설정](#클아이언트-등록-및-scope-설정)
5. [OAuth 인증 요청 시 Scope 사용](#oauth-인증-요청-시-scope-사용)
6. [Scope 부족 시 에러 처리](#scope-부족-시-에러-처리)
7. [Flutter 연동 예시](#flutter-연동-예시)
8. [보안 모범 사례](#보안-모범-사례)

---

## 개요

Hyfata API는 **OAuth 2.0 Scope**를 기반으로 클라이언트 앱의 권한을 제어합니다.

- **공식 클라이언트** (Hyfata가 직접 운영): 전체 권한 보유
- **타사(Third-Party) 클라이언트** (개발자가 등록): 제한된 권한만 부여

Access Token의 JWT payload에 `scope` 클레임이 포함되어 있으며, 민감 API 호출 시 이를 검증합니다.

```json
{
  "sub": "user@example.com",
  "client_id": "client_001",
  "scope": "profile email",
  "jti": "...",
  "iat": 1714819200,
  "exp": 1714820100
}
```

---

## Scope 목록

### 기본 Scope (모든 클라이언트 기본 제공)

| Scope | 설명 | 타사 앱 | 공식 앱 |
|-------|------|---------|---------|
| `profile` | 기본 프로필 조회 (username, displayName) | ✅ | ✅ |
| `email` | 이메일 주소 및 인증 상태 접근 | ✅ | ✅ |

### 민감 Scope (공식 사이트 / 관리자 등록 클라이언트 전용)

| Scope | 설명 | 필요 API |
|-------|------|----------|
| `profile:write` | 프로필 수정 (username, 이름 등) | `PUT /api/account/profile` |
| `account:password` | 비밀번호 변경 | `PUT /api/account/password` |
| `account:manage` | 계정 비활성화/탈퇴 | `POST /api/account/deactivate`, `DELETE /api/account` |
| `2fa:manage` | 2FA 활성화/비활성화 | `POST /api/auth/enable-2fa`, `POST /api/auth/disable-2fa` |
| `sessions:manage` | 세션 목록 조회 및 원격 로그아웃 | `GET /api/sessions`, `DELETE /api/sessions/{id}`, etc. |

### Scope 상속 관계

```
profile:write  → profile (implicit)
account:manage → account:password (implicit)
```

`profile:write`가 있으면 `profile` 조회도 가능합니다.  
`account:manage`가 있으면 `account:password` 변경도 가능합니다.

---

## Scope → API 매핑

### 인증 불필요 (Public)

| API | 설명 |
|-----|------|
| `POST /api/auth/register` | 회원가입 |
| `POST /api/auth/login` (Deprecated) | 로그인 |
| `POST /api/auth/refresh` (Deprecated) | 토큰 갱신 |
| `GET /api/auth/verify-email` | 이메일 인증 |
| `POST /api/auth/request-password-reset` | 비밀번호 재설정 요청 |
| `POST /api/auth/reset-password` | 비밀번호 재설정 |
| `POST /api/account/restore/request` | 계정 복구 요청 (이메일 발송) |
| `GET /api/account/restore/confirm` | 계정 복구 확인 (이메일 링크) |

### `profile` + `email` (기본 Scope)

| API | 설명 |
|-----|------|
| `GET /api/users/me` | 내 정보 조회 (JWT email 기반) |
| `POST /api/auth/logout` | 로그아웃 |
| `POST /oauth/logout` | OAuth 로그아웃 |
| `POST /oauth/token` (refresh_token) | 토큰 갱신 |

### `account:password` 필요

| API | 설명 |
|-----|------|
| `PUT /api/account/password` | 비밀번호 변경 |

### `account:manage` 필요

| API | 설명 |
|-----|------|
| `POST /api/account/deactivate` | 계정 비활성화 |
| `DELETE /api/account` | 계정 영구 삭제 |

### `2fa:manage` 필요

| API | 설명 |
|-----|------|
| `POST /api/auth/enable-2fa` | 2FA 활성화 |
| `POST /api/auth/disable-2fa` | 2FA 비활성화 |

### `sessions:manage` 필요

| API | 설명 |
|-----|------|
| `GET /api/sessions` | 활성 세션 목록 조회 |
| `DELETE /api/sessions/{sessionId}` | 특정 세션 무효화 |
| `POST /api/sessions/revoke-others` | 다른 세션 모두 무효화 |
| `POST /api/sessions/revoke-all` | 모든 세션 무효화 |

---

## 클라이언트 등록 및 Scope 설정

### 개발자 포털에서 등록

`POST /api/clients/register` (Authentication Required)

**First-Party(공식) 클라이언트**:
- `/api/clients/register` API로는 등록할 수 없습니다.
- `application.properties` (또는 환경 변수)로만 등록/관리되며, 애플리케이션 시작 시 DB에 자동 시드됩니다.
- 예: 공식 웹사이트, 공식 모바일 앱 등

**Third-Party(타사) 클라이언트**:
- `/api/clients/register` API를 통해 등록할 수 있습니다.
- **일반 개발자** (`ROLE_USER`):
  - `defaultScopes`와 `allowedScopes`는 무조건 **`profile email`**로 고정됩니다.
  - 요청에 다른 값을 담아도 서버가 강제로 덮어씁니다.
- **관리자** (`ROLE_ADMIN`):
  - `defaultScopes`와 `allowedScopes`를 자유롭게 지정할 수 있습니다.
  - 단, 생성되는 클라이언트는 `clientType: THIRD_PARTY`로 강제 설정됩니다.

### 등록 요청 예시 (타사 개발자)

```json
{
  "name": "My Third-Party App",
  "frontendUrl": "https://myapp.com",
  "redirectUris": ["https://myapp.com/callback"]
}
```

**응답 (자동으로 scope가 제한됨):**

```json
{
  "message": "Client registered successfully",
  "client": {
    "clientId": "client_001",
    "clientSecret": "secret_xyz",
    "defaultScopes": "profile email",
    "allowedScopes": "profile email",
    "...": "..."
  }
}
```

---

## OAuth 인증 요청 시 Scope 사용

### `/oauth/authorize`에 `scope` 파라미터 추가

| Parameter | Required | Description |
|-----------|----------|-------------|
| `scope` | X | 공백 또는 `+`로 구분. 미입력 시 클라이언트의 `defaultScopes` 사용 |

**공식 앱 예시:**

```http
GET /oauth/authorize?client_id=client_official_001
  &redirect_uri=com.hyfata.app://callback
  &response_type=code
  &state=abc123
  &code_challenge=E9Mro...
  &code_challenge_method=S256
  &scope=profile+email+profile:write+account:password+account:manage+2fa:manage+sessions:manage
```

**타사 앱 예시:**

```http
GET /oauth/authorize?client_id=client_third_001
  &redirect_uri=https://myapp.com/callback
  &response_type=code
  &state=abc123
  &code_challenge=E9Mro...
  &code_challenge_method=S256
  &scope=profile+email
```

### Scope 검증 로직

1. 클라이언트의 `allowedScopes`를 확인
2. 요청된 `scope`가 `allowedScopes`에 포함되는지 검증
3. 포함되지 않는 scope가 있으면 `invalid_scope` 에러 반환

```json
{
  "error": "invalid_scope",
  "error_description": "Requested scope 'account:manage' exceeds client's allowed scopes"
}
```

---

## Scope 부족 시 에러 처리

### 403 Forbidden

Access Token에 필요한 scope가 없으면 다음 응답을 받습니다:

```json
{
  "error": "Insufficient scope. Required one of: [account:password]"
}
```

### 클라이언트 대응 방법

1. **API 호출 전 scope 확인**: JWT의 `scope` 클레임을 파싱하여 해당 기능을 숨기거나 비활성화
2. **403 수신 시 안내**: "해당 기능은 공식 앱에서만 사용할 수 있습니다" 메시지 표시
3. **재인증 유도**: 더 높은 권한이 필요하면 사용자를 공식 사이트로 안내

### Flutter 예시

```dart
// JWT payload에서 scope 추출
List<String> extractScopes(String token) {
  final parts = token.split('.');
  if (parts.length != 3) return [];
  
  final payload = jsonDecode(
    utf8.decode(base64Url.decode(base64Url.normalize(parts[1])))
  );
  
  final scopeStr = payload['scope'] as String?;
  return scopeStr?.split(' ') ?? [];
}

// 기능 노출 여부 결정
bool canChangePassword(List<String> scopes) {
  return scopes.contains('account:password') || 
         scopes.contains('account:manage');
}
```

---

## Flutter 연동 예시

### scope를 포함한 로그인 URL 생성

```dart
Future<void> startOAuthLogin({String? scope}) async {
  _codeVerifier = PkceUtil.generateCodeVerifier();
  final codeChallenge = PkceUtil.generateCodeChallenge(_codeVerifier!);

  // 공식 앱: 전체 scope 요청
  final requestedScope = scope ?? 'profile email';

  final loginUrl = Uri(
    scheme: 'https',
    host: 'api.hyfata.com',
    path: '/oauth/authorize',
    queryParameters: {
      'client_id': _clientId,
      'response_type': 'code',
      'redirect_uri': _redirectUri,
      'code_challenge': codeChallenge,
      'code_challenge_method': 'S256',
      'state': _generateState(),
      'scope': requestedScope,  // ← 추가
    },
  ).toString();

  await launchUrl(
    Uri.parse(loginUrl),
    mode: LaunchMode.externalApplication,
  );
}
```

### 403 에러 처리 (Dio Interceptor)

```dart
class ScopeInterceptor extends Interceptor {
  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    if (err.response?.statusCode == 403) {
      final data = err.response?.data;
      if (data is Map && data['error']?.toString().contains('Insufficient scope') == true) {
        // 해당 기능은 공식 앱에서만 사용 가능
        _showScopeInsufficientDialog();
        return handler.reject(
          DioException(
            requestOptions: err.requestOptions,
            error: 'SCOPE_INSUFFICIENT',
          ),
        );
      }
    }
    return handler.next(err);
  }
}
```

---

## 보안 모범 사례

1. **최소 권한 원칙**: 클라이언트는 반드시 필요한 scope만 요청
2. **Scope 하드코딩 금지**: 서버 응답의 `scope` 필드를 확인하여 클라이언트의 실제 권한 파악
3. **민감 기능 UI 제어**: `account:password`, `2fa:manage` 등의 기능은 scope 확인 후 노출
4. **타사 앱 경고**: 사용자가 타사 앱 로그인 시 "이 앱은 제한된 권한으로 접근합니다" 안내
5. **Token Blacklist**: 민감 API 호출 전 Access Token의 JTI가 블랙리스트에 없는지 확인 (서버 처리)

---

## 관련 문서

- [OAUTH_SCOPES_DESIGN.md](OAUTH_SCOPES_DESIGN.md) — Scope 설계 문서
- [AUTH_API.md](AUTH_API.md) — 전체 API 레퍼런스
- [FLUTTER_OAUTH_GUIDE.md](FLUTTER_OAUTH_GUIDE.md) — Flutter OAuth 구현 가이드
