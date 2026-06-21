# 아키텍처

* [레이어 구조](layered-structure.md) - api / app / domain / config / common 의 역할과 의존 방향
* [영속성](persistence.md) - JPA/Hibernate, open-in-view:false, Flyway, Elasticsearch
* [캐싱](caching.md) - Caffeine 2-tier (TTL 1h / LRU 무만료)
* [스케줄링](scheduling.md) - 5-thread pool과 주요 정기 작업
* [컨벤션](conventions.md) - 생성자 주입, DTO projection, 예외 처리, 배치 insert
