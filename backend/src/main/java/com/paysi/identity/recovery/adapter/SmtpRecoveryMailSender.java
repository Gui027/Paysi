package com.paysi.identity.recovery.adapter;

import com.paysi.identity.recovery.port.RecoveryMailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * E-mail de redefinição de senha com a identidade da Paysi: HTML em tabelas com CSS inline (o que
 * Gmail/Outlook realmente respeitam) e o logo embutido na própria mensagem (cid), então aparece
 * mesmo com imagens remotas bloqueadas. Sempre vai junto uma versão em texto puro.
 */
@Component
class SmtpRecoveryMailSender implements RecoveryMailSender {
    private static final String SUBJECT = "Redefinição de senha da Paysi";

    private final JavaMailSender mailSender;
    private final String webBaseUrl;
    private final String from;
    private final String htmlTemplate;

    SmtpRecoveryMailSender(JavaMailSender mailSender,
                           @Value("${paysi.web-base-url:http://localhost:3000}") String webBaseUrl,
                           @Value("${paysi.mail.from:nao-responda@paysi.local}") String from) {
        this.mailSender = mailSender;
        this.webBaseUrl = webBaseUrl;
        this.from = from;
        this.htmlTemplate = load("mail/redefinicao-senha.html");
    }

    @Override
    public void sendPasswordReset(String destinationEmail, String rawToken) {
        String link = webBaseUrl + "/redefinir-senha?token=" + rawToken;
        try {
            MimeMessage message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            helper.setFrom(from, "Paysi");
            helper.setTo(destinationEmail);
            helper.setSubject(SUBJECT);
            helper.setText(plainText(link), htmlTemplate.replace("{{link}}", HtmlUtils.htmlEscape(link)));
            helper.addInline("paysi-logo", new ClassPathResource("mail/paysi-logo-negativo.png"), "image/png");
            mailSender.send(message);
        } catch (MessagingException | IOException error) {
            throw new MailPreparationException("Falha ao montar o e-mail de redefinição de senha", error);
        }
    }

    private static String plainText(String link) {
        return "Recebemos um pedido para trocar a senha da sua conta na Paysi.\n\n"
                + "Use o link abaixo em até 1 hora. Ele funciona uma única vez:\n\n"
                + link
                + "\n\nSe você não solicitou a troca, ignore esta mensagem: sua senha continua a mesma.";
    }

    private static String load(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Template de e-mail ausente: " + path, error);
        }
    }
}
