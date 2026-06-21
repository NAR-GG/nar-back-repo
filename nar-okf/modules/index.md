# 모듈

`app/` 아래 도메인 모듈들. 각 모듈은 비즈니스 로직·외부 클라이언트·DTO를 담는다. → [레이어 구조](/architecture/layered-structure.md)

> 핵심 도메인 8개는 개념 파일이 있다(현재 stub, 점진 풍부화). 나머지는 코드 위치만 표기하고 추후 개념 파일을 추가한다.

# 핵심 도메인

* [lolesports](lolesports.md) - LoL Esports API 동기화 (일정·경기·순위)
* [analysis](analysis.md) - 팀/선수 분석 쿼리
* [monitor](monitor.md) - Riot API 폴링 기반 실시간 라이브 경기 모니터
* [schedule](schedule.md) - 경기 일정 관리·알림
* [search](search.md) - Elasticsearch 색인·전문 검색
* [youtube](youtube.md) - YouTube 중계 탐색·메타데이터 동기화
* [riot](riot.md) - Riot API 연동 (선수 랭크·계정)
* [participant](participant.md) - 선수·팀·챔피언 관리

# 기타 모듈 (코드 위치)

| 모듈 | 위치 | 역할(요약) |
|------|------|-----------|
| auth | `app/auth/` | 인증·소셜 로그인·프로필(닉네임·Cloudinary 이미지) |
| kakao | `app/kakao/` | 카카오 연동 |
| mobile | `app/mobile/` | 모바일([warding](/overview.md)) 전용 서비스·DTO (match·push·schedule·rating·device) |
| player | `app/player/` | 선수 솔로랭크 모니터·푸시 |
| record | `app/record/` | 경기 기록 |
| data | `app/data/` | 데이터 동기화·유지보수 (Google Drive CSV import, 검증, 정리) |
| category | `app/category/` | 분류 |
| community | `app/community/` | 커뮤니티 |
