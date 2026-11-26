package com.gym.springapp.exceptions;

public class MemberDeactivatedException extends RuntimeException {

    private final String errorCode;
    private final Long memberId;
    private final String memberName;

    public MemberDeactivatedException(String message) {
        this("MEMBER_DEACTIVATED", null, null, message);
    }

    public MemberDeactivatedException(String errorCode, Long memberId, String memberName, String message) {
        super(message);
        this.errorCode = errorCode != null ? errorCode : "MEMBER_DEACTIVATED";
        this.memberId = memberId;
        this.memberName = memberName;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getMemberName() {
        return memberName;
    }
}
