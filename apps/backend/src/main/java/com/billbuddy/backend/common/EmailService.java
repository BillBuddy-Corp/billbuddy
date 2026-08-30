package com.billbuddy.backend.common;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendInviteEmail(String to, String groupName, String joinLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("You're invited to join \"" + groupName + "\" on BillBuddy");
        message.setText(
                "You've been invited to join \"" + groupName + "\" on BillBuddy.\n\n" +
                        "Join here: " + joinLink + "\n\n" +
                        "If you weren't expecting this, you can ignore this email."
        );
        mailSender.send(message);
    }
}
