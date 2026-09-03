package com.toy.nar.app.mobile.match.dto;

/**
 * 종료 경기의 와드 수 폴백 — 배치 CSV(game_player_stat)에서 진영·포지션으로 붙인다.
 *
 * <p>라이브 스냅샷의 wards 컬럼은 V87(2026-09-03) 이후 수집분에만 있어 그 전 경기는 null 이다.
 * CSV 는 종료 후 적재되므로 reconcile 된 경기라면 여기서 채울 수 있다.
 * {@code side} 는 game_participants.side('Blue'/'Red'), {@code position} 은 top/jng/mid/bot/sup.
 */
public record LiveWardRow(String side, String position, Integer wardsPlaced, Integer wardsKilled) {
}
