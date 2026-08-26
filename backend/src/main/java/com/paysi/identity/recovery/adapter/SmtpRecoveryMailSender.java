package com.paysi.identity.recovery.adapter;

import com.paysi.identity.recovery.port.RecoveryMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class SmtpRecoveryMailSender implements RecoveryMailSender {
    private final JavaMailSender mailSender;
    private final String webBaseUrl;
    private final String from;

    SmtpRecoveryMailSender(JavaMailSender mailSender,
                           @Value("${paysi.web-base-url:http://localhost:3000}") String webBaseUrl,
                           @Value("${paysi.mail.from:nao-responda@paysi.local}") String from) {
        this.mailSender = mailSender;
        this.webBaseUrl = webBaseUrl;
        this.from = from;
    }

    @Override
    public void sendPasswordReset(String destinationEmail, String rawToken) {
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destinationEmail);
        message.setSubject("Redefinição de senha da Paysi");
        message.setText("Use o link abaixo em até 1 hora. Ele funciona uma única vez:\n\n"
                + webBaseUrl + "/redefinir-senha?token=" + rawToken
                + "\n\nSe você não solicitou a troca, ignore esta mensagem.");
        mailSender.send(message);
    }
}
