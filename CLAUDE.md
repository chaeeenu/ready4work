# CLAUDE.md

## Project Overview
여유롭게 출근준비를 도와주는 올인원 체크앱.  
위치/목적지/시간 기반으로 날씨, 옷차림 추천,대중교통 경로, 지하철 실시간 도착·장애 알림을 한 대시보드에서 제공한다.   
내가 실제로 행하는 모습을 기반으로 필요한 기능들을 추가해나가는 방식으로 개발한다.

## Critical Rules (절대 규칙)
- **모든 개발작업 이후에는 반드시 테스트코드 작성**
- **플랜설계 이후 작업전 TODO.md 작성 및 작업완료 후 TODO.md 업데이트**
- **민감정보 커밋 금지**: `.env`, `credentials.json`, `*.pem`, `*.key`, `application-local.yml` 커밋 금지.
- **API 키 하드코딩/로깅 금지**: `.env` → `DotenvConfig` → `${env.*}` 경로만 사용. 키는 소스·로그·번들 어디에도 직접 노출 금지. 로깅은 boolean 존재 여부(`"API key present: true/false"`)만.
- **프론트엔드 → 외부 API 직접 호출 금지**: 모든 외부 API 호출은 백엔드 WebClient 경유. 프론트는 `/api` 내부 엔드포인트만 호출.
- **외부 API 실패 시 fallback 필수**: `.onErrorResume` / `.onErrorReturn` 으로 빈 결과/기본값 반환. 한 API 장애가 대시보드 전체를 무너뜨리면 안 됨.
- **민감 응답 필드 노출 금지**: 외부 API 응답은 반드시 `dto/` 내부 DTO 로 매핑 후 전달.

## Architecture (아키텍처)

```
ready4work/
├── build.gradle                  
├── .env                          
├── docs/api/                     
├── frontend/                     
│   ├── vite.config.js            
│   └── src/
│       ├── components/
│       │   ├── cards/            # WeatherCard, TransitCard, AssetCard, CalendarCard, NewsCard, ObsidianCard, CardShell
│       │   ├── Dashboard/        # Dashboard, CardSlot
│       │   ├── settings/         # SettingsPanel, CardToggleList, CardReorderList
│       │   └── shared/           # Header, AlertBanner
│       ├── hooks/                # useWeatherData, useTransitData, useSubwayArrivals, useSubwayAlerts, useCardConfig, useLocalStorage, useMockData
│       ├── context/              # DashboardContext (Context API 기반 상태관리)
│       ├── data/                 # cardRegistry, mock 데이터
│       └── utils/                # cn, time
└── src/main/java/com/ready4work/
    ├── config/                   # WebClientConfig (5개 Bean), DotenvConfig
    ├── weather/                  # Controller / Service / ClothingAdvisor / dto
    ├── transit/                  # Controller / Service / dto
    └── subway/                   # ArrivalService / AlertService / Controller / dto
```

### 레이어 규칙
- **도메인 분리**: `weather`, `transit`, `subway` 각자 Controller/Service/dto 보유. 도메인 간 직접 의존 금지 (공통은 `config/`).
- **DTO**: 외부 응답은 `Map<String,Object>` 로 받아 서비스에서 내부 DTO(record)로 매핑. `dto/` 밖에 외부 응답 구조 노출 금지.
- **WebClient**: `config/WebClientConfig.java` 에서만 생성. 서비스는 `@Qualifier` 주입만 사용.
- **캐싱**: `ConcurrentHashMap<String, Mono<X>> + Mono.cache(Duration)` 패턴. TTL — Weather 30분, Transit 경로 5분·좌표 24h, Subway Arrival 15초, Alert 1분.
- **Frontend 상태**: `DashboardContext` (Context API) 단일 원천. Redux/Zustand 도입 금지. 새 카드는 `data/cardRegistry.js` 에 등록.

## Tech Stack (기술스택)

### Backend
- Spring Boot **4.0.5**, Java **25**
- Spring WebFlux (비동기 WebClient) + Spring Web MVC (Controller)
- Spring Data JPA + H2, Lombok, `dotenv-java:3.0.0`

### Frontend
- React **19.2**, Vite **8.0**
- **JavaScript (JSX)** — TypeScript 아님
- **CSS Modules** (`*.module.css`) — Tailwind 아님
- 상태관리: React Context API (`DashboardContext`)

### External APIs
| 용도 | 제공자 | 상세 문서 |
|------|--------|-----------|
| 현재/시간별 날씨 | OpenWeatherMap | `docs/api/openweathermap.md` |
| 주소 → 좌표, 리버스 지오코딩 | Kakao Local | `docs/api/kakao-local.md` |
| 대중교통 길찾기 | TMAP Transit | `docs/api/tmap-transit.md` |
| 지하철 실시간 도착 | 서울 열린데이터광장 | `docs/api/seoul-subway-arrival.md` |
| 지하철 장애/공지 | 공공데이터포털 (B553766) | `docs/api/datagokr-subway-alert.md` |

## Internal REST Endpoints

| Method | Path | 담당 Service | 캐시 TTL |
|--------|------|--------------|----------|
| GET | `/api/weather?lat&lon` 또는 `?city` | `WeatherService` | 30분 |
| GET | `/api/transit/routes?origin&destination&departureTime&refresh` | `TransitService` | 5분 |
| GET | `/api/subway/arrivals?stationName&refresh` | `SubwayArrivalService` | 15초 |
| GET | `/api/alerts/subway?refresh` | `SubwayAlertService` | 1분 |

## Environment Variables

`.env` → `DotenvConfig` → `application.yml` 의 `${env.*}` 로 참조.

- `WEATHER_API_KEY` — OpenWeatherMap
- `KAKAO_REST_API_KEY` — Kakao REST
- `TMAP_APP_KEY` — TMAP
- `SEOUL_DATA_API_KEY` — 서울 열린데이터광장
- `DATA_GO_KR_SERVICE_KEY` — 공공데이터포털 (URL-encoded 상태로 입력)
