package com.svp.tracker.auth.service;

public interface SmsSender {

    void sendOtp(String phoneE164, String code);
}
