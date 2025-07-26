package com.toy.nar.domain.sync;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SyncStatus {

	/**
	 * 동기화 컨텍스트 (예: "GOOGLE_DRIVE_CSV")
	 */
	@Id
	private String context;

	/**
	 * 마지막으로 성공적으로 처리된 게임의 고유 ID
	 */
	private String lastProcessedId;
}
