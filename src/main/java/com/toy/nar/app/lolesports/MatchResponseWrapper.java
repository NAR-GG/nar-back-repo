package com.toy.nar.app.lolesports;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class MatchResponseWrapper {
	private List<MatchResultDto> matches; // 경기 데이터 리스트
	private String nextPageToken;         // 다음 페이지를 불러올 암호키 (older)
	private String newerPageToken;        // 더 먼 미래 페이지 토큰 (newer). 없으면 null
}