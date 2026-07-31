package com.toy.nar.app.riot.dto;

/**
 * 선수 계정 추가 전 검증 결과(저장 없음).
 *
 * <p>{@code accountFound=false}면 Riot ID 오타이거나 존재하지 않는 계정이다.
 * {@code accountFound=true}인데 {@code summonerFound=false}면 계정은 있지만 고른 지역이 틀렸다
 * (예: NA 계정을 KR로 조회). 티어·레벨은 프로 본인 계정인지 판단하는 근거로 쓴다.
 */
public record RiotAccountVerification(
		boolean accountFound,
		boolean summonerFound,
		String riotId,
		String gameName,
		String tagLine,
		String puuid,
		String platform,
		Integer summonerLevel,
		String soloTier,
		String soloRank,
		Integer soloLeaguePoints,
		Integer soloWins,
		Integer soloLosses,
		String opggUrl,
		String message) {
}
