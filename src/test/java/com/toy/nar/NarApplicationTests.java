package com.toy.nar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 로컬 dev 환경 전용 풀 컨텍스트 스모크 테스트.
 * application-dev.yml, service-account-key.json 등 gitignore된 로컬 설정이 있어야 기동되므로
 * 클린 체크아웃 기본 빌드에서는 건너뛴다.
 * 실행: ./gradlew test -Dfullcontext.local.enabled=true --tests "com.toy.nar.NarApplicationTests"
 */
@EnabledIfSystemProperty(named = "fullcontext.local.enabled", matches = "true")
@SpringBootTest
class NarApplicationTests {

	@Test
	void contextLoads() {
	}

}
