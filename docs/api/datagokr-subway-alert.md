# data.go.kr — Subway Alert / Notice (B553766)

## Purpose (용도)
서울교통공사가 공공데이터포털을 통해 제공하는 **지하철 공지·장애·지연 정보**를 가져와, 대시보드 상단 `AlertBanner` 에 표시한다. 오늘 해당되지 않는 공지는 필터링해서 제외한다.

## Source (소스 링크)
- 공식 문서(공공데이터포털): https://www.data.go.kr/data/... (서비스 ID `B553766`, 오퍼레이션 `getNtceList`)
- Base URL: `https://apis.data.go.kr/B553766/ntce`
- WebClient Bean: `dataGoKrWebClient` (`WebClientConfig.java:44`)

## Authentication (인증 방식)
- 전달 방식: **Query Parameter** `serviceKey` (공공데이터포털 공통)
- 환경변수: `DATA_GO_KR_SERVICE_KEY`
- 설정 경로: `application.yml` → `datagokr.api.key: ${env.DATA_GO_KR_SERVICE_KEY}` → `SubwayAlertService.java:33`
- `dataGoKrWebClient` 에는 `defaultHeader` 가 없다 — 키가 쿼리파라미터이기 때문.

### 중요: URI 직접 구성
공공데이터포털 서비스키는 이미 URL-encoded 형태로 배포되는 경우가 많아, WebClient 의 자동 인코딩을 거치면 **이중 인코딩** 이 발생해 401/서비스 오류가 난다. 이를 피하기 위해 `SubwayAlertService.java:51-55` 는 **쿼리스트링을 직접 문자열로 조립**하고 `URI.create(fullUrl)` 로 전달한다 (`.uri(URI.create(fullUrl))`).

새 파라미터를 추가할 때 `UriBuilder` 로 바꾸지 말 것. 바꾸려면 서비스키가 이중 인코딩되지 않는지 먼저 확인해야 한다.

## Internal REST Endpoint (우리 백엔드 → 프론트)
- **Method & Path**: `GET /api/alerts/subway`
- **Controller**: `SubwayAlertController.java:22`
- **Query Parameters**:

| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `refresh` | boolean | ✖ | `true` 시 전체 alert 캐시 삭제 후 재조회 (`SubwayAlertService.java:41-43`) |

---

## External API Endpoints (외부 원본 API)

### 공지 목록 조회 (getNtceList)
- **Method**: `GET`
- **Full URL**: `https://apis.data.go.kr/B553766/ntce/getNtceList`
- **Used in**: `SubwayAlertService.java:50-57`

#### Request Parameters
| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `serviceKey` | string | ✔ | `DATA_GO_KR_SERVICE_KEY` (이미 URL-encoded 상태로 전달) |
| `pageNo` | int | ✔ | 고정 `1` |
| `numOfRows` | int | ✔ | 고정 `1` — 현재는 "가장 최신 공지 한 건" 만 가져온다 |
| `dataType` | string | ✔ | 고정 `json` |

#### Request Example
```
GET https://apis.data.go.kr/B553766/ntce/getNtceList?serviceKey={KEY}&pageNo=1&numOfRows=1&dataType=json
```

#### Response Schema (실제로 읽는 필드만)
공공데이터포털 공통 envelope 가 있고, 그 안에 우리가 쓰는 필드가 들어있다:

```
response
 └─ body
     └─ items            (List 형태일 수도, Map{"item": List} 형태일 수도 있음)
         └─ item[]
             ├─ noftTtl         → 공지 제목
             ├─ noftCn          → 공지 내용(본문)
             ├─ noftOcrnDt      → 공지 발생 시각
             ├─ lineNmLst       → 영향 노선 목록 (예: "1호선,2호선")
             ├─ stnSctnCdLst    → 영향 역 구간 코드 목록
             ├─ xcseSitnBgngDt  → 장애 상황 시작 일시
             ├─ xcseSitnEndDt   → 장애 상황 종료 일시 (없으면 시작일과 동일 처리)
             └─ nonstopYn       → 무정차 통과 여부 Y/N
```

**items 언패킹 분기** (`SubwayAlertService.java:73-86`): 일부 응답에서 `items` 가 빈 배열일 때 `List` 가 아닌 빈 문자열 `""` 또는 객체로 내려올 수 있어서, `List`/`Map`/기타 세 분기로 안전하게 처리한다. 이 분기를 제거하지 말 것.

**"오늘 기준 활성" 필터** (`SubwayAlertService.java:103-129`):
- `xcseSitnBgngDt` 가 null/blank/"null" 이면 **일단 포함**
- 시작·종료일을 `yyyyMMdd` 로 파싱해서 `!today.isBefore(start) && !today.isAfter(end)` 인 것만 유지
- 파싱 실패 시에도 포함 (보수적으로 노출 — 장애 공지 누락이 더 위험하다는 판단)

#### Internal DTO Mapping
- `com.ready4work.subway.dto.SubwayAlert(noftTtl, noftCn, noftOcrnDt, lineNmLst, stnSctnCdLst, xcseSitnBgngDt, xcseSitnEndDt, nonstopYn)` at `SubwayAlertService.java:100`
- `com.ready4work.subway.dto.SubwayAlertResponse(alerts, timestamp)` at `SubwayAlertService.java:109`

---

## Caching & Error Handling
- **캐시**: 단일 키 `"alerts"` 의 `ConcurrentHashMap` (목록 전체를 한 덩어리로 캐싱)
- **TTL**: **1분** (`Duration.ofMinutes(1)`, `SubwayAlertService.java:45`)
- **refresh=true 동작**: `cache.clear()` (`SubwayAlertService.java:42`)
- **에러 처리**: `.onErrorResume` 으로 WebClient 예외 발생 시 빈 alerts 배열 반환 (`SubwayAlertService.java:111-114`). `AlertBanner` 가 조용히 사라지는 동작이 의도된 것이다.

## Notes / Gotchas
- **이중 인코딩 금지**: 위 Authentication 섹션 참조. `URI.create(fullUrl)` 패턴을 유지할 것.
- **필드 중복 getOrDefault**: `item.getOrDefault("noftTtl", item.get("ntftTtl"))` 처럼 오타 가능한 대체 키를 같이 조회하는 방어 코드가 있다 (`SubwayAlertService.java:89`). 공공 API 가 실제로 이런 스펠링 변동을 보인 이력이 있어 유지한다.
- **현재 numOfRows=1**: 여러 공지를 동시에 보여주려면 여기를 늘리면 되지만, 프론트의 `AlertBanner` 가 현재 한 건만 보여주는 UI 라 함께 변경해야 한다.
- **severity 분류**: `classifySeverity()` (`SubwayAlertService.java:139-145`) 에 `DANGER_KEYWORDS = {"운행중지","사고","중단"}` 이 정의되어 있지만 현재 호출되지 않는다. 프론트에서 danger/warning 구분이 필요해지면 활성화할 지점.
