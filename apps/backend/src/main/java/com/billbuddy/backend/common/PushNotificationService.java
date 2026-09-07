package com.billbuddy.backend.common;

// Swappable, same pattern as SmsService: a real provider (Firebase Cloud Messaging) is a new
// implementation class and a config change, not a rewrite of NotificationService.
public interface PushNotificationService {

    void send(String fcmToken, String title, String body);
}
