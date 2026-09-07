package com.billbuddy.backend.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// Placeholder until a real provider (Firebase Cloud Messaging) is wired in -- logs the push
// instead of sending it, so the notification flow is fully testable locally without any Firebase
// project or cost. Not safe for production as-is: nothing is actually delivered to a device.
@Slf4j
@Service
public class LoggingPushNotificationService implements PushNotificationService {

    @Override
    public void send(String fcmToken, String title, String body) {
        log.info("Push notification (no provider configured, logging instead): fcmToken={}, title={}, body={}", fcmToken, title, body);
    }
}
