# Paysi

Base executável da plataforma de checkout, divisão de pagamentos e afiliados, criada a partir da documentação v3.0.

## Links essenciais

- Repositório: https://github.com/Gui027/Paysi
- Swagger UI local: http://localhost:8080/swagger-ui.html
- Contrato OpenAPI em JSON: http://localhost:8080/v3/api-docs
- Saúde do backend: http://localhost:8080/actuator/health

> Os endereços `localhost` funcionam somente com o ambiente local em execução. Ainda não existe uma URL pública de homologação ou produção cadastrada.

## Estrutura

- `backend`: Spring Boot 3, Java 21, Flyway e o núcleo financeiro.
- `web-painel`: painel Next.js para vendedor e afiliado.
- `web-checkout`: checkout público Vite + React, deliberadamente enxuto.
- `shared`: tokens visuais e contratos compartilhados em compilação.
- `infra`: PostgreSQL 16, Redis 7, RabbitMQ e Mailpit para desenvolvimento.
- `docs`: documentação autoritativa, DDL consolidado e testes SQL.

## Primeira execução

Pré-requisitos: JDK 21, Node.js 20.19+ e Docker Compose.

```bash
docker compose -f infra/docker-compose.yml up -d

cd backend
./mvnw spring-boot:run

# em outros terminais, a partir da raiz
npm install
npm run dev:painel
npm run dev:checkout
```

- API/saúde: `http://localhost:8080/actuator/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Painel: `http://localhost:3000`
- Checkout: `http://localhost:5173`
- RabbitMQ: `http://localhost:15672` (`guest` / `guest`)
- Mailpit: `http://localhost:8025`

O Flyway usa o papel proprietário `paysi`; a aplicação usa `paysi_app`. As credenciais incluídas são exclusivamente locais. Copie `.env.example` para `.env` apenas quando precisar sobrescrever os padrões. As 30 migrações do DDL autoritativo foram preservadas e a `V030` complementa os privilégios operacionais que o documento descrevia, mas o SQL consolidado ainda não concedia.

### Como acessar o Swagger

1. Suba a infraestrutura com `docker compose -f infra/docker-compose.yml up -d`.
2. Inicie o backend com `cd backend` e `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`).
3. Abra `http://localhost:8080/swagger-ui.html` no navegador.

O Swagger lista automaticamente os endpoints implementados pelos controladores Spring. O JSON em `/v3/api-docs` é o contrato que deve ser usado para integrações e geração de clientes. Se a página não abrir, confirme primeiro o health check e verifique se a porta `8080` está livre.

## Verificação

```bash
cd backend
./mvnw test

cd ..
npm run lint
npm run build
```

As decisões de produto, pendências jurídicas/PSP e critérios financeiros permanecem nos documentos. Em especial, PEN-10, PEN-21 e PEN-04 devem ser resolvidas antes de operação real.

## Deploy no Portainer

A stack de homologação está em `infra/portainer-stack.yml` e constrói banco, backend, painel e checkout diretamente da branch `main`. Ela requer as variáveis `PAYSI_DB_PASSWORD`, `PAYSI_APP_DB_PASSWORD`, `PAYSI_RABBIT_PASSWORD`, `KYC_WEBHOOK_SECRET`, `PAYMENT_WEBHOOK_SECRET` e `MFA_ENCRYPTION_KEY_BASE64` configuradas no Portainer.

- Painel: `http://<servidor>:3020`
- Checkout: `http://<servidor>:5180`
- API/Swagger: `http://<servidor>:8090/swagger-ui.html`
- Saúde: `http://<servidor>:8090/actuator/health`

PostgreSQL, Redis e RabbitMQ não publicam portas no host. Os dados persistentes usam volumes exclusivos com prefixo `paysi_`.
