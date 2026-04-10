# External API Reference

이 프로젝트가 사용하는 외부 API 의 **단일 진실원(single source of truth)**.  
각 문서는 공식 스펙의 복사본이 아니라 **우리가 실제로 호출하는 엔드포인트·파라미터·응답 필드** 만 기록한다. 외부 스펙이 바뀌거나 새 필드를 쓰기 시작할 때마다 갱신한다.

## 공통 원칙

- 모든 외부 API 는 **백엔드 WebClient** 를 통해서만 호출한다. 프론트엔드는 `/api/*` 내부 엔드포인트만 본다.
- 각 API 의 키는 `.env` → `DotenvConfig` → `application.yml` 의 `${env.*}` 경로로만 주입된다. 소스·테스트·로그에 키 문자열을 노출하지 않는다.
- 외부 호출 실패 시 서비스 레이어에서 `onErrorResume` 으로 빈 결과/기본값을 반환한다 (예: `SubwayAlertService.java:111`, `WeatherService.java:175`). 한 API 장애가 대시보드 전체를 무너뜨리지 않도록 한다.

## API 목록

| 용도 | 제공자 | 문서 | WebClient Bean |
|------|--------|------|----------------|
| 현재/시간별 날씨, 리버스 지오코딩 | OpenWeatherMap / Kakao Local | [openweathermap.md](./openweathermap.md) | `weatherWebClient`, `kakaoWebClient` |
| 주소/키워드 → 좌표 | Kakao Local | [kakao-local.md](./kakao-local.md) | `kakaoWebClient` |
| 대중교통 길찾기 | TMAP Transit | [tmap-transit.md](./tmap-transit.md) | `tmapWebClient` |
| 지하철 실시간 도착 | 서울 열린데이터광장 | [seoul-subway-arrival.md](./seoul-subway-arrival.md) | `seoulWebClient` |
| 지하철 장애/공지 알림 | 공공데이터포털 (B553766) | [datagokr-subway-alert.md](./datagokr-subway-alert.md) | `dataGoKrWebClient` |

WebClient Bean 은 모두 `com.ready4work.config.WebClientConfig` 에서 정의한다. 도메인 서비스는 `@Qualifier` 로 주입받아 사용하며, 키가 필요한 Bean 은 `defaultHeader` 로 키를 박아두기 때문에 서비스 코드에서 키를 직접 볼 수 없다.

## 문서 포맷

모든 API md 는 다음 섹션을 이 순서로 유지한다 (Claude Code 가 일관되게 파싱할 수 있도록):

1. Purpose
2. Source
3. Authentication
4. Internal REST Endpoint
5. External API Endpoints (요청/응답/Internal DTO Mapping)
6. Caching & Error Handling
7. Notes / Gotchas
