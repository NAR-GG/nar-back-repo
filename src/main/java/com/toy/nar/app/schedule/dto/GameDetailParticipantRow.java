package com.toy.nar.app.schedule.dto;

public record GameDetailParticipantRow(
        Long gameId,
        Integer gameNumber,
        Integer gameLengthSeconds,
        String side,
        String position,
        Boolean isWin,
        String teamName,
        String playerName,
        String championNameEn) {
}
