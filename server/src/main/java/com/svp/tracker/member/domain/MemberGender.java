package com.svp.tracker.member.domain;

/** Optional self-identified gender on the member profile. {@code null} in the database means “prefer not to say”. */
public enum MemberGender {
    FEMALE,
    MALE,
    NON_BINARY,
    OTHER
}
