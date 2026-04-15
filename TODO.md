# TODO.md 

## Overview 
- claude에게 태스크를 추적하게 하기 위한 목적으로 To-do List 작성
- 작업하면서 지속적으로 업데이트 예정

## Rules
- To-do List 작성시 작업한 내용을 한줄요약 타이틀 형태로 작성한다.
- 버그수정내역은 타이틀 앞에 `버그 #n : 타이틀` 형식으로 작성한다.

## To-do List
- [x] 오늘의 날씨 정보 제공
- [x] 오늘의 날씨에 따른 옷차림 추천
- [x] 출근길 경로 탐색
- [x] 출근길 경로 관련 특이사항 정보 제공
- [x] 출근길 경로 지하철 실시간 도착정보 제공
- [x] 출근길 경로 버스 실시간 도착정보 제공
  - [x] Step 1: TransitLeg DTO에 busStopLat, busStopLon 필드 추가
  - [x] Step 2: TransitService에서 승차 정류소 좌표 추출
  - [x] Step 3: 환경 설정 추가 (.env, application.yml)
  - [x] Step 4: WebClientConfig에 busStationWebClient, busArrivalWebClient Bean 추가
  - [x] Step 5: bus 도메인 신규 생성 (DTO, Service, Controller)
  - [x] Step 6: useBusArrivals hook 생성
  - [x] Step 7: TransitCard에 버스 도착정보 통합
  - [x] Step 8: 테스트 코드 작성 (11개 전체 통과)
  - [x] Step 9: 문서 업데이트
- [x] 버그 #1 : 버스 도착정보 XML 파싱 미동작 수정 (resultType=json 미지원 → XmlMapper 전환, 단일 항목 처리)
- [x] 교통 API 재설계: 실시간 도착정보 기반 경로 enrichment
  - [x] Step 1: DTO 확장 (TransitLeg, TransitRoute에 실시간 필드 추가)
  - [x] Step 2: ArrivalMessageParser 유틸리티 생성
  - [x] Step 3: TransitEnrichmentService 생성 (조합 계층)
  - [x] Step 4: TransitController에 enrich 파라미터 추가
  - [x] Step 5: useTransitData 훅 수정 (enrich + 30초 폴링)
  - [x] Step 6: TransitCard 리팩토링 (모든 leg에 실시간 표시)
  - [x] Step 7: CSS 추가
  - [x] Step 8: 테스트 코드 작성 (37개 전체 통과)
- [x] 버그 #2 : 지하철/버스 도착정보 UI 표시 개선 (Mono.zip partial-failure 수정, 방면 텍스트 추가, 두 번째 열차 정보 설정)
  - [x] Step 1: Mono.zip → Flux.flatMap + collectMap 교체 (부분 실패 허용)
  - [x] Step 2: extractDirectionText 메서드 추가 (trainLineNm에서 방면/행 텍스트 파싱)
  - [x] Step 3: enrichSubwayLeg 개선 (방면 텍스트 포함 메시지, arrivalMessage2 설정)
  - [x] Step 4: 테스트 코드 작성 (41개 전체 통과)
- [x] 버그 #3 : TMAP API 일일 한도 초과 방지 (departureTime 세션 고정)
  - [x] formatDepartureTime 헬퍼 함수 모듈 상단으로 분리
  - [x] fetchRoutes에 departureTime 파라미터 추가 (내부 new Date() 제거)
  - [x] useEffect에서 sessionDepartureTime 1회 계산 후 폴링에서 재사용
- [x] TMAP → ODsay 마이그레이션 + departureTime 제거
  - [x] Step 1: application.yml - tmap → odsay 설정 교체
  - [x] Step 2: WebClientConfig - tmapWebClient → odsayWebClient (Filter 자동 주입)
  - [x] Step 3: TransitService - ODsay API 호출 및 파싱 로직으로 교체
  - [x] Step 4: TransitController - departureTime 파라미터 제거
  - [x] Step 5: useTransitData.js - departureTime 관련 코드 전체 제거
  - [x] Step 6: CLAUDE.md 업데이트 (API 표, 환경변수)
  - [x] Step 7: TransitServiceTest 재작성 (ODsay mock, 7개 통과)
- [x] 버그 #4 : 지하철 실시간 도착정보 미표시 수정
  - [x] 빈 startName 방어 처리 (!isBlank 가드 추가), DEBUG→INFO/WARN 로그 레벨 승격
  - [x] SubwayArrival DTO에 subwayId 필드 추가, Seoul API subwayId(1001~1009) 기반 노선 매칭으로 교체
  - [x] subwayId→호선번호 변환 헬퍼 추가 (특수노선 매핑 포함), 테스트 11개→12개
- [ ] 간단한 뉴스 제공
- [ ] 미국증시 관심종목 종가 정보 제공
- [ ] 옵시디언 메모 연동