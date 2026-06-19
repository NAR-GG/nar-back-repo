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

    /** 랜덤 이름과 태그 조합. {@code (name, tag)}는 중복되지 않음을 보장한다. */
    public record GeneratedNickname(String name, String tag) {
    }

    public GeneratedNickname generate() {
        int maxAttempts = 10;
        for (int i = 0; i < maxAttempts; i++) {
            GeneratedNickname candidate = build();
            if (!memberRepository.existsByNameAndTag(candidate.name(), candidate.tag())) {
                return candidate;
            }
        }
        // 충돌 시 타임스탬프 suffix로 보장 (tag 컬럼 길이 10 이내)
        GeneratedNickname candidate = build();
        return new GeneratedNickname(candidate.name(), candidate.tag() + System.currentTimeMillis() % 1000);
    }

    private GeneratedNickname build() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        String adj = ADJECTIVES[rng.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[rng.nextInt(NOUNS.length)];
        int num = rng.nextInt(1000, 9999);
        return new GeneratedNickname(adj + noun, String.valueOf(num));
    }
}
