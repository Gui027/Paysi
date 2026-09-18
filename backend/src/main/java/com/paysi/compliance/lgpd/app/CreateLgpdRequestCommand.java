package com.paysi.compliance.lgpd.app;

public record CreateLgpdRequestCommand(String subjectKind, String subjectRef, String kind) {
}
