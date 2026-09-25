package com.paysi.identity.recovery.adapter;

import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpRecoveryMailSenderTest {
    private final List<MimeMessage> sent = new ArrayList<>();
    private final JavaMailSenderImpl mailSender = new JavaMailSenderImpl() {
        @Override
        public void send(MimeMessage message) {
            try {
                message.saveChanges();
            } catch (jakarta.mail.MessagingException error) {
                throw new IllegalStateException(error);
            }
            sent.add(message);
        }
    };
    private final SmtpRecoveryMailSender sender =
            new SmtpRecoveryMailSender(mailSender, "https://app.paysi.com.br", "nao-responda@paysi.com.br");

    @Test
    void sendsBrandedHtmlWithPlainTextAlternativeAndEmbeddedLogo() throws Exception {
        sender.sendPasswordReset("ana@example.com", "tok-123_abc");

        assertThat(sent).hasSize(1);
        var message = sent.get(0);
        assertThat(message.getSubject()).isEqualTo("Redefinição de senha da Paysi");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("ana@example.com");
        assertThat(message.getFrom()[0].toString()).contains("Paysi").contains("nao-responda@paysi.com.br");

        var parts = new ArrayList<String>();
        var inlineImages = new ArrayList<String>();
        collect(message, parts, inlineImages);
        String link = "https://app.paysi.com.br/redefinir-senha?token=tok-123_abc";
        assertThat(parts).anySatisfy(text -> assertThat(text).contains("<html").contains("Criar nova senha")
                .contains("cid:paysi-logo").contains("#1D6BD8").contains(link));
        assertThat(parts).anySatisfy(text -> assertThat(text).doesNotContain("<html").contains(link));
        assertThat(inlineImages).containsExactly("<paysi-logo>");
        assertThat(parts).noneSatisfy(text -> assertThat(text).contains("{{link}}"));
    }

    @Test
    void escapesTheLinkInsideHtml() throws Exception {
        sender.sendPasswordReset("ana@example.com", "a\"><script>x</script>");

        var parts = new ArrayList<String>();
        collect(sent.get(0), parts, new ArrayList<>());
        var html = parts.stream().filter(text -> text.contains("<html")).findFirst().orElseThrow();
        assertThat(html).doesNotContain("<script>x</script>").contains("&lt;script&gt;");
    }

    private static void collect(Part part, List<String> texts, List<String> inline) throws Exception {
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++) collect(multipart.getBodyPart(i), texts, inline);
        } else if (part.isMimeType("text/*")) {
            texts.add((String) part.getContent());
        } else if (part.getHeader("Content-ID") != null) {
            inline.add(part.getHeader("Content-ID")[0]);
        }
    }
}
