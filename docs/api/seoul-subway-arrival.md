# Seoul Open Data — Subway Realtime Arrival

## Purpose (용도)
역 이름을 입력받아 해당 역의 **지하철 실시간 도착 정보**(다음 열차 메시지, 상/하행, 종착역 등)를 가져와 프론트 대시보드의 Subway/Transit 관련 카드에 노출한다.

## Source (소스 링크)
- 공식 문서(서울 열린데이터광장): http://data.seoul.go.kr/ — "지하철 실시간 도착정보" (`realtimeStationArrival`)
- Base URL: `http://swopenAPI.seoul.go.kr/api/subway`
- WebClient Bean: `seoulWebClient` (`WebClientConfig.java:37`)

## Authentication (인증 방식)
- 전달 방식: **URL Path 의 일부** — 키가 경로에 직접 박힘
- 환경변수: `SEOUL_DATA_API_KEY`
- 설정 경로: `application.yml` → `seoul.api.key: ${env.SEOUL_DATA_API_KEY}` → `SubwayArrivalService.java:30`
- 이 API 는 헤더 인증이 아니므로 `seoulWebClient` 에는 `defaultHeader` 가 없다. 대신 서비스 코드가 `{apiKey}` 를 path variable 로 넣는다 (`SubwayArrivalService.java:50`).

## Internal REST Endpoint (우리 백엔드 → 프론트)
- **Method & Path**: `GET /api/subway/arrivals`
- **Controller**: `SubwayAlertController.java:28`
- **Query Parameters**:

| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `stationName` | string | ✔ | 예: `서울`, `강남`, `서울역`. 끝의 `"역"` 글자는 서비스에서 자동 제거 (`SubwayArrivalService.java:83-90`) |
| `refresh` | boolean | ✖ | `true` 시 해당 역 캐시 제거 후 재조회 |

---

## External API Endpoints (외부 원본 API)

### 실시간 역별 도착 정보
- **Method**: `GET`
- **Path**: `/{apiKey}/json/realtimeStationArrival/0/20/{stationName}`
- **Used in**: `SubwayArrivalService.java:49-52`

#### Path Segments
| 세그먼트 | 설명 |
|----------|------|
| `{apiKey}` | `SEOUL_DATA_API_KEY` — URL 인코딩은 WebClient 가 처리 |
| `json` | 응답 포맷 고정 |
| `realtimeStationArrival` | 서비스 이름 |
| `0` | 시작 인덱스 (START_INDEX) |
| `20` | 끝 인덱스 (END_INDEX) — 최대 20건 |
| `{stationName}` | "역" 접미사 제거된 역명 (예: `서울`) |

#### Request Example
```
GET http://swopenAPI.seoul.go.kr/api/subway/{KEY}/json/realtimeStationArrival/0/20/서울
```

#### Response Schema (실제로 읽는 필드만)
```
realtimeArrivalList[]
  ├─ trainLineNm   → "광운대행 - 청량리방면" 같은 노선 방향 표기
  ├─ arvlMsg2      → "전역출발", "3분 후 도착" 같은 도착 안내 메시지
  ├─ updnLine      → "상행" / "하행"
  ├─ bstatnNm      → 종착역 이름
  └─ arvlCd        → 도착 상태 코드 (0=진입, 1=도착, 2=출발, …)
```

`realtimeArrivalList` 가 비거나 null 이면 빈 `SubwayArrivalResponse` 반환 (`SubwayArrivalService.java:59-62`).

#### Internal DTO Mapping
- `com.ready4work.subway.dto.SubwayArrival(trainLineNm, arvlMsg2, updnLine, bstatnNm, arvlCd)` at `SubwayArrivalService.java:65-71`
- `com.ready4work.subway.dto.SubwayArrivalResponse(stationName, arrivals, timestamp)` at `SubwayArrivalService.java:75`
- `timestamp` 포맷: `yyyy-MM-dd HH:mm:ss` (`TIMESTAMP_FMT`, line 26)

---

## Caching & Error Handling
- **캐시**: `ConcurrentHashMap<String, Mono<SubwayArrivalResponse>>` — key 는 정규화된 역명
- **TTL**: **15초** (`Duration.ofSeconds(15)`, `SubwayArrivalService.java:43`)
- **refresh=true 동작**: `cache.remove(normalized)` 로 해당 역만 무효화 (`SubwayArrivalService.java:39-41`)
- **에러 처리**: `.onErrorResume` 으로 WebClient 예외 발생 시 **빈 도착 목록** 을 반환 (`SubwayArrivalService.java:77-80`). 한 역 조회가 실패해도 대시보드 다른 카드는 영향 없음.

## Notes / Gotchas
- **역명 정규화**: 사용자 입력이 `"서울역"` 이어도 `"서울"` 로 바꿔 호출한다. 실제 API 가 `"서울역"` 을 거부하기 때문. 역명이 "노량진" 처럼 애초에 "역" 으로 안 끝나면 그대로 사용.
- **키가 path 에 노출** 되므로 로깅 시 주의. 현재 코드는 URL 전체를 로그에 찍지 않는다. 이 관례를 유지할 것.
- 15초라는 짧은 TTL 은 "지금 출발해도 되나?" 같은 **실시간성 판단** 을 보장하기 위함이다. 함부로 늘리지 말 것.
- 응답 포맷상 `errorMessage.code` 같은 최상위 에러 필드도 존재하지만 현재는 읽지 않음 — 새 이슈 발생 시 추가 매핑 고려.
