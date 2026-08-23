# 벤치마크 관측 (로컬 전용)

CSV 적재 성능을 재기 위해 **내 개발 머신에서** 띄우는 Prometheus·Grafana 다.
스크랩 대상은 `host.docker.internal:8080` — `./gradlew bootRun` 으로 띄운 앱 하나다.

> **프로덕션 관측은 `infra/monitoring/` 이다.** 이름이 비슷해 헷갈리기 쉬우니 구분해 둔다.
>
> | | 여기 | `infra/monitoring/` |
> |---|---|---|
> | 대상 | 내 노트북의 `bootRun` 앱 1개 | 맥미니 k3s 파드·DB·춘천 박스 (타깃 5개) |
> | 스크랩 주기 | 5초 (벤치마크 구간을 촘촘히) | 15초 |
> | 구성 | Prometheus, Grafana | + Loki, Pushgateway, mysqld-exporter, Alloy |
> | Grafana | `:3001` (admin/admin) | `:3000` / `grafana.nar.kr` |
> | 성격 | 측정할 때만 띄우는 **일회성 도구** | 서버에 복사해 반영하는 **상시 운영 원본** |

## 1. 애플리케이션 실행

512MB 메모리 환경을 맞춰서 실행합니다.

```bash
SPRING_PROFILES_ACTIVE=dev JAVA_TOOL_OPTIONS="-Xms512m -Xmx512m" ./gradlew bootRun
```

Actuator metric 노출 여부를 확인합니다.

```bash
curl http://localhost:8080/actuator/prometheus
```

## 2. Prometheus / Grafana 실행

```bash
docker compose -f benchmark-observability/docker-compose.yml up -d
```

접속 URL:

- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001
- Grafana 계정: `admin` / `admin`

Grafana에는 `NAR Batch Observability` 대시보드가 자동 등록됩니다.

## 3. 배치 테스트에서 확인할 지표

JPA baseline은 로컬 CSV 기준으로 아래 endpoint를 호출해 실행합니다.

```bash
curl -X POST http://localhost:8080/api/admin/benchmark/ingestion/local-csv/jpa-baseline
```

- `nar_data_ingestion_duration_seconds`: 전체 CSV 적재 시간
- `nar_data_ingestion_chunk_phase_duration_seconds`: chunk 단위 `resolve`, `process`, `write` 단계별 시간
- `nar_data_ingestion_games_total`: 성공, 실패, 스킵, 무효 game 수
- `nar_data_ingestion_write_rows`: 저장된 테이블별 row 수
- `jvm_memory_used_bytes`: JVM heap 사용량
- `jvm_gc_pause_seconds_*`: GC 횟수와 소요 시간
- `process_cpu_usage`: 애플리케이션 CPU 사용률

## 4. 포트폴리오 캡처 포인트

동일 데이터셋을 두 번 실행해 아래 항목을 비교합니다.

1. JPA cascade + IDENTITY 기반 저장
2. JdbcTemplate batch insert 기반 저장
3. 컬럼 수 기반 동적 batch size 적용 저장

캡처에는 JVM heap, GC, CPU, `resolve/process/write` 단계별 소요 시간이 함께 보이도록 두면 좋습니다.
