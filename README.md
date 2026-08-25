# Paysi

Base executável da plataforma de checkout, divisão de pagamentos e afiliados, criada a partir da documentação v3.0.

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
- Painel: `http://localhost:3000`
- Checkout: `http://localhost:5173`
- RabbitMQ: `http://localhost:15672` (`guest` / `guest`)
- Mailpit: `http://localhost:8025`

O Flyway usa o papel proprietário `paysi`; a aplicação usa `paysi_app`. As credenciais incluídas são exclusivamente locais. Copie `.env.example` para `.env` apenas quando precisar sobrescrever os padrões. As 30 migrações do DDL autoritativo foram preservadas e a `V030` complementa os privilégios operacionais que o documento descrevia, mas o SQL consolidado ainda não concedia.

## Verificação

```bash
cd backend
./mvnw test

cd ..
npm run lint
npm run build
```

As decisões de produto, pendências jurídicas/PSP e critérios financeiros permanecem nos documentos. Em especial, PEN-10, PEN-21 e PEN-04 devem ser resolvidas antes de operação real.
