package com.ready4work.weather;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class ClothingAdvisor {

    private static final Set<String> RAIN_CONDITIONS = Set.of("Rain", "Drizzle", "Thunderstorm");
    private static final Set<String> SNOW_CONDITIONS = Set.of("Snow");

    public String recommend(double temp, int high, int low, String mainCondition) {
        List<String> tips = new ArrayList<>();

        if (temp >= 28) {
            tips.add("반팔, 반바지. 자외선 차단제 꼭 바르세요");
        } else if (temp >= 23) {
            tips.add("얇은 셔츠나 반팔이면 충분합니다");
        } else if (temp >= 20) {
            tips.add("긴팔 셔츠가 적당합니다");
        } else if (temp >= 17) {
            tips.add("가벼운 자켓이나 가디건을 챙기세요");
        } else if (temp >= 12) {
            tips.add("자켓이나 니트를 입으세요");
        } else if (temp >= 9) {
            tips.add("코트나 두꺼운 외투가 필요합니다");
        } else if (temp >= 5) {
            tips.add("패딩이나 코트에 목도리를 챙기세요");
        } else {
            tips.add("한파 주의! 패딩+목도리+장갑 필수입니다");
        }

        if (RAIN_CONDITIONS.contains(mainCondition)) {
            tips.add("우산 챙기세요 🌂");
        }
        if (SNOW_CONDITIONS.contains(mainCondition)) {
            tips.add("눈 소식이 있어요, 우산 챙기세요 🌂");
        }
        if (high - low >= 10) {
            tips.add("일교차가 큽니다");
        }

        return String.join(". ", tips) + ".";
    }
}
