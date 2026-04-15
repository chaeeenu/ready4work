package com.ready4work.transit.util;

import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 실시간 도착 메시지에서 예상 대기시간(분)을 추출한다.
 *
 * 버스 (서울 BIS arrmsg1/arrmsg2):
 *   "3분후 도착", "5분 후 도착", "곧 도착", "운행종료"
 *
 * 지하철 (서울 열린데이터 arvlMsg2):
 *   "3분 후 (강남)", "[2]번째 전역 (대림)", "전역 출발", "곧 도착", "운행종료"
 */
public final class ArrivalMessageParser {

    // "3분후", "5분 후", "12분후" 등
    private static final Pattern MINUTES_PATTERN = Pattern.compile("(\\d+)\\s*분\\s*(?:후|뒤)");

    // "[2]번째 전역" → 2정거장 전
    private static final Pattern STATION_COUNT_PATTERN = Pattern.compile("\\[(\\d+)]\\s*번째\\s*전");

    // "N번째 전역"에서 1정거장 ≈ 2분으로 추정
    private static final int MINUTES_PER_STATION = 2;

    private ArrivalMessageParser() {}

    /**
     * 도착 메시지를 파싱하여 예상 대기시간(분)을 반환한다.
     * 파싱 불가능하면 OptionalInt.empty() 반환.
     */
    public static OptionalInt parseMinutes(String message) {
        if (message == null || message.isBlank()) {
            return OptionalInt.empty();
        }

        String trimmed = message.trim();

        // 운행종료
        if (trimmed.contains("운행종료")) {
            return OptionalInt.empty();
        }

        // "곧 도착" or "도착"
        if (trimmed.contains("곧 도착") || trimmed.equals("도착")) {
            return OptionalInt.of(1);
        }

        // "N분후 도착", "N분 후"
        Matcher minMatcher = MINUTES_PATTERN.matcher(trimmed);
        if (minMatcher.find()) {
            return OptionalInt.of(Integer.parseInt(minMatcher.group(1)));
        }

        // "[N]번째 전역" → N * 2분 추정
        Matcher stationMatcher = STATION_COUNT_PATTERN.matcher(trimmed);
        if (stationMatcher.find()) {
            int stations = Integer.parseInt(stationMatcher.group(1));
            return OptionalInt.of(stations * MINUTES_PER_STATION);
        }

        // "전역 출발" (1정거장 전)
        if (trimmed.contains("전역 출발") || trimmed.contains("전역출발")) {
            return OptionalInt.of(MINUTES_PER_STATION);
        }

        return OptionalInt.empty();
    }
}
