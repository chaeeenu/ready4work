# Kakao Local API

## Purpose (용도)
사용자가 입력한 출발지/도착지 문자열(주소 또는 장소명)을 **위도·경도 좌표**로 변환한다. 이 좌표는 TMAP Transit API 의 입력으로 전달된다. 또한 날씨 API 의 좌표 모드에서 지역명을 구하기 위한 리버스 지오코딩도 이 Bean 을 공유한다.

## Source (소스 링크)
- 공식 문서: https://developers.kakao.com/docs/latest/ko/local/dev-guide
- Base URL: `https://dapi.kakao.com`
- WebClient Bean: `kakaoWebClient` (`WebClientConfig.java:28`)

## Authentication (인증 방식)
- 전달 방식: **HTTP Header** `Authorization: KakaoAK {KAKAO_REST_API_KEY}`
- 키는 `WebClientConfig.java:32` 의 `defaultHeader("Authorization", "KakaoAK " + apiKey)` 로 **Bean 생성 시점에 한 번** 박힌다 → 도메인 서비스(`TransitService`, `WeatherService`) 는 키 문자열을 직접 다루지 않는다.
- 환경변수: `KAKAO_REST_API_KEY`
- 설정 경로: `application.yml` → `kakao.api.key: ${env.KAKAO_REST_API_KEY}` → `WebClientConfig.java:29`

## Internal REST Endpoint (우리 백엔드 → 프론트)
Kakao Local 은 독립된 내부 엔드포인트를 갖지 않고, **`TransitService` 와 `WeatherService` 의 내부 단계**로만 사용된다. 따라서 프론트 관점에서는 `/api/transit/routes` 또는 `/api/weather` 호출 시 투명하게 수행된다.

---

## External API Endpoints (외부 원본 API)

### 1) Address Search
- **Method**: `GET`
- **Path**: `/v2/local/search/address.json`
- **Used in**: `TransitService.java:85-103` (`searchByAddress`)

#### Request Parameters
| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `query` | string | ✔ | 주소 문자열 (예: `서울특별시 중구 세종대로 110`) |

#### Request Example
```
GET https://dapi.kakao.com/v2/local/search/address.json?query=서울특별시+중구+세종대로+110
Authorization: KakaoAK {KAKAO_REST_API_KEY}
```

#### Response Schema (실제로 읽는 필드만)
| 필드 경로 | 타입 | 설명 |
|-----------|------|------|
| `documents` | array | 매칭 결과. 비어있으면 빈 `Mono` 반환 후 keyword 검색으로 fallback |
| `documents[0].x` | string | 경도(lon). `Double.parseDouble` 후 `coord[0]` 에 저장 |
| `documents[0].y` | string | 위도(lat). `coord[1]` 에 저장 |

첫 번째 매칭만 사용한다. 결과가 비면 `Mono.empty()` 를 반환해 호출부가 keyword 검색으로 넘어가게 한다.

---

### 2) Keyword Search (fallback)
- **Method**: `GET`
- **Path**: `/v2/local/search/keyword.json`
- **Used in**: `TransitService.java:106-124` (`searchByKeyword`)

주소로 매칭되지 않는 경우(예: "강남역", "삼성전자 본사")를 위한 fallback. 파라미터·응답 스키마는 address search 와 동일한 구조로 `documents[0].x`, `documents[0].y` 를 쓴다.

#### Request Example
```
GET https://dapi.kakao.com/v2/local/search/keyword.json?query=강남역
Authorization: KakaoAK {KAKAO_REST_API_KEY}
```

호출 흐름: `geocode()` 가 먼저 `searchByAddress` 를 시도하고, 빈 결과면 `.switchIfEmpty(searchByKeyword(query))` 로 넘어간 뒤, 그래도 비면 `"장소를 찾을 수 없습니다"` 예외를 던진다 (`TransitService.java:78-82`).

---

### 3) Coord → Region Info (reverse geocoding)
- **Method**: `GET`
- **Path**: `/v2/local/geo/coord2regioninfo.json`
- **Used in**: `WeatherService.java:156-176` (`reverseGeocode`)

#### Request Parameters
| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `x` | double | ✔ | 경도 (lon) |
| `y` | double | ✔ | 위도 (lat) |

#### Response Schema (실제로 읽는 필드만)
| 필드 경로 | 타입 | 설명 |
|-----------|------|------|
| `documents[]` | array | 여러 행정구역 유형이 함께 반환됨 |
| `documents[].region_type` | string | `"H"` (행정동) 항목만 선택 |
| `documents[].region_2depth_name` | string | 예: `"종로구"` — WeatherResponse 의 도시명으로 사용 |

실패/빈 결과 시 `"알 수 없음"` 으로 fallback (`WeatherService.java:175`).

---

## Internal DTO Mapping
- address/keyword 검색 결과 → `double[]{x, y}` (경도, 위도 순서). 주의: 일반적인 `(lat, lon)` 관례와 **반대** 다. TMAP 호출 시 `startX = coord[0]`, `startY = coord[1]` 로 그대로 전달된다 (`TransitService.java:63-65`).
- reverse geocode 결과 → `WeatherResponse.city` 로 직접 들어감 (별도 DTO 없음).

## Caching & Error Handling
- **좌표 캐시**: `TransitService.java:28` 의 `coordCache`, **TTL 24시간** (`Duration.ofHours(24)`, `TransitService.java:72`). 키는 사용자가 입력한 place name 문자열 그대로.
- **리버스 지오코딩 캐시 없음**: weather 캐시(30분) 안에 결과가 함께 들어가므로 추가 캐시는 없다.
- **에러 처리**:
  - address 검색 실패 → keyword fallback
  - keyword 도 실패 → `RuntimeException` (`TransitService.java:81`) → TransitController 까지 전파 (transit 전용 에러 응답 필요 시 여기서 정규화해야 함)
  - reverse geocode 실패 → `.onErrorReturn("알 수 없음")`

## Notes / Gotchas
- 카카오 응답의 `x`, `y` 는 문자열로 내려온다. 반드시 `Double.parseDouble` 필요.
- `documents` 는 null 체크 필수 (비어있을 수도, 키 자체가 없을 수도 있음).
- 레이트리밋: 카카오 REST API 는 앱당 일일 쿼터가 있다. 좌표 캐시 24h TTL 이 이를 완화한다.
