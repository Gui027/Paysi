package com.paysi.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI paysiOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Paysi API")
                .version("v1")
                .description("API da plataforma Paysi para checkout, pagamentos, recebíveis e afiliados.")
                .contact(new Contact()
                        .name("Paysi")
                        .url("https://github.com/Gui027/Paysi")));
    }
}
