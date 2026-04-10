# TMAP Transit API

## Purpose (용도)
출발지·도착지 좌표와 출발 시각을 입력받아 **대중교통(지하철·버스·도보) 경로** 를 최대 5개 반환받고, 그중 "최단 시간" 과 "최소 환승" 두 개를 골라 프론트 Transit 카드에 보여준다.

## Source (소스 링크)
- 공식 문서: https://skopenapi.readme.io/reference/%EB%8C%80%EC%A4%91%EA%B5%90%ED%86%B5-%EA%B2%BD%EB%A1%9C%EC%95%88%EB%82%B4
- Base URL: `https://apis.openapi.sk.com`
- WebClient Bean: `tmapWebClient` (`WebClientConfig.java:19`)

## Authentication (인증 방식)
- 전달 방식: **HTTP Header** `appKey: {TMAP_APP_KEY}`
- 키는 `WebClientConfig.java:23` 의 `defaultHeader("appKey", appKey)` 로 Bean 생성 시 박힌다.
- 환경변수: `TMAP_APP_KEY`
- 설정 경로: `application.yml` → `tmap.api.key: ${env.TMAP_APP_KEY}` → `WebClientConfig.java:20`

## Internal REST Endpoint (우리 백엔드 → 프론트)
- **Method & Path**: `GET /api/transit/routes`
- **Controller**: `TransitController.java:16`
- **Query Parameters**:

| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `origin` | string | ✔ | 출발지. 주소 또는 장소명 — Kakao Local 로 좌표 변환 |
| `destination` | string | ✔ | 도착지. 동일 |
| `departureTime` | string | ✖ | `yyyyMMddHHmm`. 생략 시 현재 시각 |
| `refresh` | boolean | ✖ | `true` 시 해당 캐시 키 제거 후 재조회 (`TransitService.java:47-50`) |

---

## External API Endpoints (외부 원본 API)

### 대중교통 경로 탐색
- **Method**: `POST`
- **Path**: `/transit/routes`
- **Used in**: `TransitService.java:139-145` (`searchPath`)

#### Request Headers
```
appKey: {TMAP_APP_KEY}
Content-Type: application/json
```

#### Request Body (JSON)
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `startX` | string | ✔ | 출발지 경도 (lon) |
| `startY` | string | ✔ | 출발지 위도 (lat) |
| `endX` | string | ✔ | 도착지 경도 |
| `endY` | string | ✔ | 도착지 위도 |
| `format` | string | ✔ | 고정값 `json` |
| `count` | int | ✔ | 최대 경로 개수. 현재 `5` |
| `searchDtime` | string | ✔ | 출발 시각 `yyyyMMddHHmm` |

좌표는 `LinkedHashMap` 에 `String.valueOf` 로 담아 보낸다 (`TransitService.java:130-137`) — 순서 보존이 필요한 건 아니지만 디버깅 로그 가독성을 위해 `LinkedHashMap` 을 쓴다.

#### Request Example
```json
POST https://apis.openapi.sk.com/transit/routes
appKey: {TMAP_APP_KEY}

{
  "startX": "126.9779",
  "startY": "37.5665",
  "endX":   "127.0276",
  "endY":   "37.4979",
  "format": "json",
  "count":  5,
  "searchDtime": "202604101830"
}
```

#### Response Schema (실제로 읽는 필드만)
TMAP 응답은 깊이가 깊다. 우리가 읽는 경로:

```
metaData.plan.itineraries[]
  ├─ totalTime        (초)        → /60 한 분값
  ├─ totalWalkTime    (초)        → /60 한 분값 = walkTime
  ├─ transferCount    (int)
  ├─ fare.regular.totalFare (int)
  └─ legs[]
       ├─ mode                    (WALK | SUBWAY | BUS 등. toLowerCase 사용)
       ├─ sectionTime             (초)
       ├─ route                   (예: "수도권2호선", "간선버스 146")
       ├─ routeColor              (hex, # 없을 수 있음 → "#"+replaceFirst)
       ├─ start.name / end.name   (구간 시작/종점명)
       └─ passStopList.stations[] (정차 역/정류장. size()-1 = stationCount)
```

**매핑 규칙** (`TransitService.java:192-253`):
- `metaData` 또는 `plan` 이 없으면 빈 `TransitRouteResponse` 반환 후 경고 로그
- `walk` 모드에서 `sectionTime == 0` 인 구간은 무시
- `route` 가 null 이면 `"지하철"` / `"버스"` 기본값
- `routeColor` 가 비면 `"#888888"`
- 같은 한 `itinerary` 에 대해 `lineNames` 를 `→` 로 join 한 문자열이 `TransitRoute.summary`

**두 경로 선별** (`TransitService.java:171-187`):
1. `fastest`: `totalTime` 최소
2. `leastTransfer`: `transferCount` 최소 → 동점 시 `totalTime` 최소
3. 두 경로가 동일 객체면 1개만, 다르면 2개 반환

#### Internal DTO Mapping
- `com.ready4work.transit.dto.TransitRouteResponse(origin, destination, List<TransitRoute>)`
- `com.ready4work.transit.dto.TransitRoute(totalTime, transferCount, totalCost, walkTime, summary, List<TransitLeg>)`
- `com.ready4work.transit.dto.TransitLeg(type, lineName, lineColor, startName, endName, stationCount, sectionTime)`
  - `type` ∈ `{"subway", "bus", "walk"}`

---

## Caching & Error Handling
- **좌표 캐시**: place name → `double[]` (lon, lat). TTL **24시간** (`TransitService.java:72`)
- **경로 캐시**: key = `origin|destination|departureTime`. TTL **5분** (`TransitService.java:53`)
  - `departureTime` 은 분 단위라 같은 분 요청은 캐시 히트
- **refresh=true 동작**: 경로 캐시만 제거, 좌표 캐시는 유지 (`TransitService.java:49`)
- **에러 처리**: `searchPath` 응답의 `metaData`/`plan` 누락 시 빈 itinerary 배열 반환 (경고 로그만). WebClient 레벨 에러는 현재 명시적 `onErrorResume` 이 없으므로 호출자까지 전파된다 — 새 경로 API 작업 시에는 다른 서비스들과 맞춰 fallback 을 추가하는 것을 고려.

## Notes / Gotchas
- **좌표 순서 주의**: TMAP 의 `startX/endX` 는 **경도(lon)**, `startY/endY` 는 **위도(lat)**. Kakao 도 같은 `x=lon, y=lat` 규칙이라 그대로 전달 가능하다 (`TransitService.java:63-65`).
- **단위**: TMAP 의 `totalTime`, `sectionTime`, `totalWalkTime` 은 **초** 단위다. 우리는 모두 `/60` 으로 분 변환 후 DTO 에 저장한다.
- **요금**: `fare.regular.totalFare` 가 없으면 `0`. 현재 청소년·청년 할인 요금은 읽지 않는다.
- TMAP 는 앱키 기반 일일 호출 한도가 있다. 경로 캐시 5분, 좌표 캐시 24h 로 방어.
