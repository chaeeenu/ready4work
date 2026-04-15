package com.ready4work.transit.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class ArrivalMessageParserTest {

    @ParameterizedTest
    @CsvSource({
            "'3분후 도착', 3",
            "'5분 후 도착', 5",
            "'12분후 도착', 12",
            "'1분 뒤 도착', 1"
    })
    @DisplayName("N분후/뒤 도착 메시지에서 분 추출")
    void parseMinutes_minutesPattern(String message, int expected) {
        assertThat(ArrivalMessageParser.parseMinutes(message))
                .isEqualTo(OptionalInt.of(expected));
    }

    @Test
    @DisplayName("곧 도착 → 1분")
    void parseMinutes_soonArrival() {
        assertThat(ArrivalMessageParser.parseMinutes("곧 도착")).isEqualTo(OptionalInt.of(1));
    }

    @Test
    @DisplayName("도착 → 1분")
    void parseMinutes_arrival() {
        assertThat(ArrivalMessageParser.parseMinutes("도착")).isEqualTo(OptionalInt.of(1));
    }

    @ParameterizedTest
    @CsvSource({
            "'[2]번째 전역 (대림)', 4",
            "'[3]번째 전역 (신도림)', 6",
            "'[1]번째 전역', 2"
    })
    @DisplayName("[N]번째 전역 → N * 2분 추정")
    void parseMinutes_stationCount(String message, int expected) {
        assertThat(ArrivalMessageParser.parseMinutes(message))
                .isEqualTo(OptionalInt.of(expected));
    }

    @Test
    @DisplayName("전역 출발 → 2분")
    void parseMinutes_previousStationDeparture() {
        assertThat(ArrivalMessageParser.parseMinutes("전역 출발")).isEqualTo(OptionalInt.of(2));
    }

    @Test
    @DisplayName("운행종료 → empty")
    void parseMinutes_serviceEnded() {
        assertThat(ArrivalMessageParser.parseMinutes("운행종료")).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "알 수 없음"})
    @DisplayName("null, 빈 문자열, 파싱 불가 → empty")
    void parseMinutes_unparseable(String message) {
        assertThat(ArrivalMessageParser.parseMinutes(message)).isEmpty();
    }

    @Test
    @DisplayName("지하철 arvlMsg2 실제 형식: '3분 후 (강남)' → 3분")
    void parseMinutes_subwayRealFormat() {
        assertThat(ArrivalMessageParser.parseMinutes("3분 후 (강남)"))
                .isEqualTo(OptionalInt.of(3));
    }
}
