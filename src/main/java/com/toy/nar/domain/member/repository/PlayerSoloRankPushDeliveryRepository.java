package com.toy.nar.domain.member.repository;

import com.toy.nar.domain.member.entity.PlayerSoloRankPushDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 솔랭 푸시 발송 기록.
 *
 * <p>예약·마감은 전부 {@link PlayerSoloRankPushDeliveryRepositoryCustom} 의 벌크 연산으로만 한다.
 * 구독자 단위 단건 메서드는 두지 않는다 — 예약 판정 규칙(신규·FAILED·PENDING 5분 초과)이 두 곳에
 * 갈라져 어긋나면 중복 발송이나 누락이 되고, 실제로 그 판정을 잘못 구현해 중복 발송이 난 적이 있다.</p>
 */
public interface PlayerSoloRankPushDeliveryRepository
		extends JpaRepository<PlayerSoloRankPushDelivery, Long>, PlayerSoloRankPushDeliveryRepositoryCustom {
}
