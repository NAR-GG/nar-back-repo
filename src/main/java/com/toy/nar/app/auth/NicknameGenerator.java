package com.toy.nar.app.auth;

import com.toy.nar.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
public class NicknameGenerator {

    private static final String[] ADJECTIVES = {
            "전설의", "무적의", "용맹한", "빛나는", "폭풍의", "황금의", "날쌘", "불굴의", "화려한", "강철의"
    };
    private static final String[] NOUNS = {
            "챔피언", "정글러", "서포터", "탑솔러", "미드라이너", "원딜러", "전략가", "승부사", "수호자", "정복자"
    };

    private final MemberRepository memberRepository;

    public String generate() {
        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts; i++) {
            String candidate = buildNickname();
            if (!memberRepository.existsByNickname(candidate)) {
                return candidate;
            }
        }
        // 충돌 시 타임스탬프 suffix로 보장
        return buildNickname() + System.currentTimeMillis() % 10000;
    }

    private String buildNickname() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        String adj = ADJECTIVES[rng.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[rng.nextInt(NOUNS.length)];
        int num = rng.nextInt(1000, 9999);
        return adj + noun + "#" + num;
    }
}
