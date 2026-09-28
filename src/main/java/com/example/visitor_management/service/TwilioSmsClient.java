package com.example.visitor_management.service;

import com.example.visitor_management.dto.SmsResult;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Component
public class TwilioSmsClient {
    private final SmsSettings settings;
    private final HttpClient http;

    @Autowired
    public TwilioSmsClient(SmsSettings settings) {
        this(settings, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }
    // Package-visible constructor lets tests supply a fake HTTP client.
    TwilioSmsClient(SmsSettings settings, HttpClient http) {
        this.settings = settings; this.http = http;
    }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    public SmsResult send(String phone, String text) {
        String credentials = Base64.getEncoder().encodeToString(
            (settings.accountSid() + ":" + settings.authToken()).getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(
            "https://api.twilio.com/2010-04-01/Accounts/" + settings.accountSid() + "/Messages.json"))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Basic " + credentials)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("To=" + encode(phone) +
                "&From=" + encode(settings.fromNumber()) + "&Body=" + encode(text))).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201)
                return new SmsResult("SUBMITTED", "SMS accepted by the provider for " + phone +
                    ". Phone delivery is not yet confirmed.");
            if (response.statusCode() >= 500)
                return unknown();
            return new SmsResult("FAILED", "SMS provider rejected the request. Check the sender, account balance, " +
                "recipient permissions and provider logs. Your visitor pass is still valid.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return unknown();
        } catch (java.io.IOException e) {
            return unknown();
        }
    }
    private SmsResult unknown() {
        return new SmsResult("UNKNOWN", "SMS delivery status is unknown after a connection problem. " +
            "Check provider logs before resending; the first message may still arrive. Your pass is still valid.");
    }
}
