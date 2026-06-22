package kr.hyfata.rest.api.auth.entity;

/**
 * OAuth 클라이언트 유형
 * <p>
 * FIRST_PARTY: Hyfata가 직접 운영하는 공식 클라이언트 (설정 파일로만 등록)
 * THIRD_PARTY: 외부 개발자가 API를 통해 등록하는 클라이언트
 */
public enum ClientType {
    FIRST_PARTY,
    THIRD_PARTY
}
