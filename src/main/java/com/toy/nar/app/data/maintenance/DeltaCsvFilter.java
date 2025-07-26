package com.toy.nar.app.data.maintenance;

import com.opencsv.bean.CsvToBeanFilter;
import org.springframework.util.StringUtils;

public class DeltaCsvFilter implements CsvToBeanFilter {

	private final String lastProcessedGameId;
	private boolean deltaProcessingStarted;

	public DeltaCsvFilter(String lastProcessedGameId) {
		// 마지막 처리 ID가 없으면(최초 동기화) 처음부터 처리
		if (!StringUtils.hasText(lastProcessedGameId)) {
			this.lastProcessedGameId = null;
			this.deltaProcessingStarted = true;
		} else {
			this.lastProcessedGameId = lastProcessedGameId;
			this.deltaProcessingStarted = false;
		}
	}

	@Override
	public boolean allowLine(String[] line) {
		// gameid(첫번째 컬럼)가 비어있으면 무시
		if (line == null || line.length == 0 || !StringUtils.hasText(line[0])) {
			return false;
		}

		// 델타 처리가 이미 시작되었다면 모든 라인을 허용
		if (deltaProcessingStarted) {
			return true;
		}

		// 현재 라인의 gameId가 마지막 처리 ID와 일치하면, "다음" 라인부터 처리하도록 플래그를 설정
		// 이 라인 자체는 포함하지 않음 (return false)
		if (line[0].equals(lastProcessedGameId)) {
			deltaProcessingStarted = true;
		}

		return false; // 델타 처리가 시작되기 전의 모든 라인은 무시
	}
}
