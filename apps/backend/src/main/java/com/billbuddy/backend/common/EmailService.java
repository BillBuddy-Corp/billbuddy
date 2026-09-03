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

    public void sendPasswordResetEmail(String to, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Reset your BillBuddy password");
        message.setText(
                "We received a request to reset your BillBuddy password.\n\n" +
                        "Reset it here: " + resetLink + "\n\n" +
                        "If you didn't request this, you can ignore this email."
        );
        mailSender.send(message);
    }

    public void sendVerificationEmail(String to, String verifyLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Verify your BillBuddy email");
        message.setText(
                "Verify your email to finish setting up your BillBuddy account.\n\n" +
                        "Verify here: " + verifyLink + "\n\n" +
                        "If you didn't create this account, you can ignore this email."
        );
        mailSender.send(message);
    }
}
