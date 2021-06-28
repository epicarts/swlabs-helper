package io.seoul.helper.domain.team;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TeamStatus {
    WAITING("STATUS_WAITING", "선택 대기중"),
    READY("STATUS_READY", "인원 모집중"),
    RUNNING("STATUS_RUNNING", "스터디 진행중"),
    END("STATUS_END", "종료");

    private final String key;
    private final String name;
}
