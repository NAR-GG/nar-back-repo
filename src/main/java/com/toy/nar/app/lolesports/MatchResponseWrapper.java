package com.toy.nar.app.lolesports;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class MatchResponseWrapper {
	private List<MatchResultDto> matches; // 경기 데이터 리스트
	private String nextPageToken;         // 다음 페이지를 불러올 암호키 (older)
}