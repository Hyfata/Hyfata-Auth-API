package kr.hyfata.rest.api.auth.service;

import kr.hyfata.rest.api.auth.dto.account.*;
import kr.hyfata.rest.api.auth.entity.User;

public interface AccountService {

    /**
     * 비밀번호 변경
     * @param email 사용자 이메일
     * @param request 비밀번호 변경 요청
     * @return 성공 메시지
     */
    String changePassword(String email, ChangePasswordRequest request);

    /**
     * 계정 비활성화
     * @param email 사용자 이메일
     * @param request 비활성화 요청
     * @return 성공 메시지
     */
    String deactivateAccount(String email, DeactivateAccountRequest request);

    /**
     * 계정 영구 삭제
     * @param email 사용자 이메일
     * @param request 삭제 요청
     * @return 성공 메시지
     */
    String deleteAccount(String email, DeleteAccountRequest request);

    /**
     * 계정 복구 요청 (이메일 발송)
     * @param email 사용자 이메일
     * @param clientId 클라이언트 ID
     * @return 요청 완료 메시지
     */
    String requestAccountRestore(String email, String clientId);

    /**
     * 계정 복구 확인 (토큰 검증 후 활성화)
     * @param request 복구 확인 요청
     * @return 복구 완료 메시지
     */
    String confirmAccountRestore(RestoreAccountConfirmRequest request);

    /**
     * 사용자 조회
     * @param email 사용자 이메일
     * @return 사용자 객체
     */
    User findUserByEmail(String email);
}
