package com.toy.nar.common.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

	/* 400 BAD_REQUEST : 잘못된 요청 */
	INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
	INVALID_COMBINATION_ID(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 조합 ID입니다."),
	INVALID_MATCHUP_REQUEST(HttpStatus.BAD_REQUEST, "동일한 챔피언끼리는 매치업을 조회할 수 없습니다."),
	INVALID_MATCH_ID(HttpStatus.BAD_REQUEST, "유효하지 않은 매치 ID 형식입니다."),
	INVALID_QUIET_HOURS(HttpStatus.BAD_REQUEST, "알림 잠자기 시간이 올바르지 않습니다."),
	MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 매치 정보를 찾을 수 없습니다."),

	/* 404 NOT_FOUND : 리소스를 찾을 수 없음 */
	COMBINATION_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 조건의 조합 데이터를 찾을 수 없습니다."),
	DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "데이터가 존재하지 않습니다."),
	NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "공지사항을 찾을 수 없습니다."),

	/* 커뮤니티 */
	COMMUNITY_LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
	COMMUNITY_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "게시글을 찾을 수 없습니다."),
	COMMUNITY_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),
	COMMUNITY_NOT_AUTHOR(HttpStatus.FORBIDDEN, "본인이 작성한 글만 수정·삭제할 수 있습니다."),
	COMMUNITY_BOARD_FORBIDDEN(HttpStatus.FORBIDDEN, "응원팀 게시판에만 글을 쓸 수 있습니다."),
	FAVORITE_TEAM_CHANGE_COOLDOWN(HttpStatus.FORBIDDEN, "응원팀은 30일에 한 번만 바꿀 수 있습니다."),
	COMMUNITY_WRITE_INTERVAL(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 작성할 수 있습니다."),
	COMMUNITY_REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "신고 대상을 찾을 수 없습니다."),
	COMMUNITY_ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 대상입니다."),
	COMMUNITY_BLOCK_SELF(HttpStatus.BAD_REQUEST, "자기 자신은 차단할 수 없습니다."),
	COMMUNITY_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
	COMMUNITY_POLL_NOT_FOUND(HttpStatus.NOT_FOUND, "투표를 찾을 수 없습니다."),
	COMMUNITY_ALREADY_VOTED(HttpStatus.CONFLICT, "이미 투표했습니다."),

	/* 500 INTERNAL_SERVER_ERROR : 서버 내부 오류 */
	INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
	DATA_INTEGRITY_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "게임 데이터의 무결성이 훼손되었습니다. (팀/선수 정보 누락)");

	private final HttpStatus httpStatus;
	private final String message;
}
