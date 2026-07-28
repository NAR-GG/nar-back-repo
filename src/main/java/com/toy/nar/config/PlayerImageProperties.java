package com.toy.nar.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 업로드된 선수 이미지를 두는 디렉토리.
 *
 * <p>jar 내부(classpath static)는 읽기 전용이라 런타임 추가가 불가능해서, 업로드분은 컨테이너 밖
 * 호스트 디스크에 둔다(배포 시 {@code -v /srv/nar/images:/app/images}). 그래야 재배포·컨테이너
 * 교체에도 파일이 남는다. 기존 jar 이미지는 그대로 두고 새 파일만 이 디렉토리로 간다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "nar.images")
public class PlayerImageProperties {

	/** 이미지 루트. 하위에 players/ 를 만들어 쓴다. 로컬 기본값은 프로젝트 상대경로. */
	private String dir = "data/images";
}
