package com.paybot.telemetry;

/**
 * Trạng thái vòng đời của FastStats Telemetry Subsystem.
 * Tuân thủ Quy Tắc 17: Class đơn nhiệm quản lý trạng thái độc lập.
 */
public enum FastStatsState {
    NOT_STARTED,
    INITIALIZING,
    READY,
    DISABLED,
    SHUTDOWN
}
