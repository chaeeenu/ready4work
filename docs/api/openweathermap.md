# OpenWeatherMap API

## Purpose (용도)
사용자 위치 또는 도시명을 기준으로 **현재 날씨**와 **오늘의 3시간 간격 예보**를 가져와, 대시보드의 Weather 카드와 옷차림 추천에 사용한다.

## Source (소스 링크)
- 공식 문서: https://openweathermap.org/api
- Base URL: `https://api.openweathermap.org`
- WebClient Bean: `weatherWebClient` (`WebClientConfig.java:12`)

## Authentication (인증 방식)
- 전달 방식: **Query Parameter** `appid`
- 환경변수: `WEATHER_API_KEY`
- 설정 경로: `application.yml` → `weather.api.key: ${env.WEATHER_API_KEY}` → `WeatherService.java:33` 의 `@Value("${weather.api.key}")`
- `weatherWebClient` 는 `defaultHeader` 를 쓰지 않으며, 키는 각 요청의 쿼리 파라미터로 붙는다.

## Internal REST Endpoint (우리 백엔드 → 프론트)
- **Method & Path**: `GET /api/weather`
- **Controller**: `WeatherController.java:16`
- **Query Parameters**:

| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `lat` | double | △ | `lon` 과 함께 제공되면 좌표 기반 조회 |
| `lon` | double | △ | `lat` 과 함께 제공되면 좌표 기반 조회 |
| `city` | string | ✖ | 좌표가 없을 때 fallback. 기본값 `Seoul` |

좌표가 제공되면 `getWeatherByCoords(lat, lon)` 이, 아니면 `getWeather(city)` 가 호출된다 (`WeatherController.java:21-24`).

---

## External API Endpoints (외부 원본 API)

### 1) Current Weather
- **Method**: `GET`
- **Path**: `/data/2.5/weather`
- **Used in**: `WeatherService.java:101-109` (city), `WeatherService.java:127-136` (coords)

#### Request Parameters
| 이름 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `q` | string | △ | 도시명 (city 모드에서만) |
| `lat` | double | △ | 위도 (coords 모드에서만) |
| `lon` | double | △ | 경도 (coords 모드에서만) |
| `appid` | string | ✔ | `WEATHER_API_KEY` |
| `units` | string | ✔ | 항상 `metric` (섭씨) |
| `lang` | string | ✔ | 항상 `kr` |

#### Request Example
```
GET https://api.openweathermap.org/data/2.5/weather?lat=37.57&lon=126.98&appid={KEY}&units=metric&lang=kr
```

#### Response Schema (실제로 읽는 필드만)
| 필드 경로 | 타입 | 설명 |
|-----------|------|------|
| `main.temp` | number | 현재 기온(°C) — 반올림해서 `CurrentWeather.temp` |
| `main.humidity` | int | 습도 % |
| `weather[0].main` | string | 예: `Clear`, `Rain` — `CONDITION_KR_MAP` 로 한국어 변환 |
| `weather[0].description` | string | 상세 설명 (매핑 실패 시 fallback) |
| `weather[0].icon` | string | 예: `01d` — `ICON_EMOJI_MAP` 으로 이모지 변환 |
| `wind.speed` | number | m/s |

#### Internal DTO Mapping
- → `com.ready4work.weather.dto.CurrentWeather` at `WeatherService.java:197-200`

---

### 2) 5-day / 3-hour Forecast
- **Method**: `GET`
- **Path**: `/data/2.5/forecast`
- **Used in**: `WeatherService.java:111-119` (city), `WeatherService.java:138-147` (coords)

#### Request Parameters
위 current weather 와 동일 (`q` 또는 `lat`+`lon`, `appid`, `units=metric`, `lang=kr`).

#### Request Example
```
GET https://api.openweathermap.org/data/2.5/forecast?q=Seoul&appid={KEY}&units=metric&lang=kr
```

#### Response Schema (실제로 읽는 필드만)
| 필드 경로 | 타입 | 설명 |
|-----------|------|------|
| `list[].dt` | long | UNIX epoch (초) — KST 로 변환해 "오늘" 여부 필터링 |
| `list[].main.temp` | number | 해당 시각 기온 — 오늘 최고/최저 계산 |
| `list[].weather[0].icon` | string | 통근 시간대(`COMMUTE_HOURS = {7,9,12,15,18,21}`)에만 이모지 매핑 |

forecast 의 오늘 데이터가 비어있으면 현재 기온으로 high/low 를 대체한다 (`WeatherService.java:236-240`).

#### Internal DTO Mapping
- → `com.ready4work.weather.dto.HourlyForecast` (배열) at `WeatherService.java:228-232`
- 최종 응답은 `WeatherResponse(city, current, high, low, hourly, clothing, alerts=[])` at `WeatherService.java:244`
- `clothing` 은 `ClothingAdvisor.recommend(temp, high, low, mainCondition)` 결과

---

### 3) (Kakao) Reverse Geocode for Region Name
좌표 모드일 때만, 지역명(예: "종로구")을 얻기 위해 카카오 Local 의 `coord2regioninfo` 를 같이 호출한다.
- **Base**: `kakaoWebClient` (Kakao API — 별도 문서 `kakao-local.md` 참조)
- **Path**: `GET /v2/local/geo/coord2regioninfo.json?x={lon}&y={lat}`
- **Used in**: `WeatherService.java:156-176`
- **Response 필드**: `documents[].region_type == "H"` 인 항목의 `region_2depth_name`
- 실패 시 `"알 수 없음"` 으로 fallback (`WeatherService.java:175`)

---

## Caching & Error Handling
- **캐시 위치**: `WeatherService.java:27` 의 `ConcurrentHashMap<String, Mono<WeatherResponse>>`
- **TTL**: **30분** — `Mono.cache(Duration.ofMinutes(30))` at `WeatherService.java:89, 95`
- **캐시 키**:
  - 좌표 모드: `String.format("coord:%.2f,%.2f", lat, lon)` → 소수점 2자리(약 1km) 단위 grouping
  - 도시 모드: `city.toLowerCase()`
- **에러 처리**: current + forecast 는 `Mono.zip` 으로 묶이며 하나라도 실패하면 전체 실패. 리버스 지오코딩만 `.onErrorReturn("알 수 없음")` 로 격리되어 있다.

## Notes / Gotchas
- OpenWeatherMap 무료 플랜은 분당/일일 호출 제한이 있다. 30분 캐시가 이를 방어한다.
- forecast 의 `list` 는 3시간 간격이라 `COMMUTE_HOURS` 중 특정 시각이 실제로 포함되지 않을 수 있다. 이 경우 해당 시각은 표시되지 않는다.
- `@SuppressWarnings("unchecked")` 가 붙은 이유: 응답을 `Map.class` raw 로 받아 수동 캐스팅하기 때문. 새 필드를 추가할 때는 DTO 로 매핑하는 편이 안전하다.
