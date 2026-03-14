package com.toy.nar.app.player;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PlayerProfileDto {
    private String gameName; // 활동명 (Faker, Peyz 등)
    private String realName; // 본명 (이상혁, 김수환 등)
    private String birthDate; // 생년월일 (YYYY-MM-DD)
    private Integer age; // 나이
    private String role; // 포지션 (ADC, Mid 등)
    private List<GameAccountDto> gameAccounts; // 게임 계정 목록 (티어 정보 포함)

    @Data
    @Builder
    public static class GameAccountDto {
        private String region; // 서버 지역 (예: "KR")
        private String riotId; // Riot ID (예: "Peyz #KR11")
        private String tier; // 티어 (예: "Challenger 1,200LP")
    }
}
