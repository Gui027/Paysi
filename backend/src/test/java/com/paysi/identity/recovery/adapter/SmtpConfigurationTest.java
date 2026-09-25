package com.paysi.identity.recovery.adapter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;

/** Garante que as variáveis MAIL_* do stack chegam ao JavaMailSender (auth, TLS e timeouts). */
class SmtpConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class));

    @Test
    void realProviderSettingsReachTheMailSender() {
        runner.withPropertyValues(
                "spring.mail.host=smtp.example.com", "spring.mail.port=587",
                "spring.mail.username=apikey", "spring.mail.password=segredo",
                "spring.mail.properties.mail.smtp.auth=true",
                "spring.mail.properties.mail.smtp.starttls.enable=true",
                "spring.mail.properties.mail.smtp.starttls.required=true",
                "spring.mail.properties.mail.smtp.ssl.protocols=TLSv1.2",
                "spring.mail.properties.mail.smtp.connectiontimeout=5000",
                "spring.mail.properties.mail.smtp.timeout=8000").run(context -> {
            var sender = (JavaMailSenderImpl) context.getBean(org.springframework.mail.javamail.JavaMailSender.class);
            assertThat(sender.getHost()).isEqualTo("smtp.example.com");
            assertThat(sender.getPort()).isEqualTo(587);
            assertThat(sender.getUsername()).isEqualTo("apikey");
            assertThat(sender.getJavaMailProperties())
                    .containsEntry("mail.smtp.auth", "true")
                    .containsEntry("mail.smtp.starttls.enable", "true")
                    .containsEntry("mail.smtp.starttls.required", "true")
                    .containsEntry("mail.smtp.ssl.protocols", "TLSv1.2")
                    .containsEntry("mail.smtp.connectiontimeout", "5000")
                    .containsEntry("mail.smtp.timeout", "8000");
        });
    }
}
