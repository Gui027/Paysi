package com.paysi.collaborator.adapter;

import com.paysi.collaborator.port.CollaboratorMailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class SmtpCollaboratorMailSender implements CollaboratorMailSender {
    private final JavaMailSender mailSender;
    private final String webBaseUrl;
    private final String from;

    SmtpCollaboratorMailSender(JavaMailSender mailSender,
                               @Value("${paysi.web-base-url:http://localhost:3000}") String webBaseUrl,
                               @Value("${paysi.mail.from:nao-responda@paysi.local}") String from) {
        this.mailSender = mailSender;
        this.webBaseUrl = webBaseUrl;
        this.from = from;
    }

    @Override
    public void sendInvite(String destinationEmail, String ownerName) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(destinationEmail);
        message.setSubject(ownerName + " convidou você para colaborar na Paysi");
        message.setText(ownerName + " convidou você para ser colaborador(a) da conta dele(a) na Paysi.\n\n"
                + "Acesse " + webBaseUrl + " com este e-mail para continuar.\n\n"
                + "Se você não esperava este convite, ignore esta mensagem.");
        mailSender.send(message);
    }
}
