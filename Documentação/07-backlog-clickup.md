# Paysi — Backlog executável para o ClickUp

Fonte: documentação v3.0, DDL autoritativo, testes SQL e estado atual do repositório em 25/08/2026.

## Padrão de publicação

- Criar três tarefas-pai na lista `901716271089`: `[BACKLOG] Backend`, `[BACKLOG] Frontend` e `[BACKLOG] Mobile`.
- Publicar os itens abaixo como subtarefas da tarefa-pai correspondente.
- Manter os códigos `BE`, `FE` e `MO` no título para facilitar busca, ordenação e referência em PRs.
- Toda tarefa só pode ser concluída com testes automatizados do caminho feliz, validações e ao menos um caminho de erro.
- Valores monetários são inteiros em centavos; percentuais são inteiros em pontos-base; frontend e mobile nunca calculam taxa, comissão, saldo ou divisão.
- Recurso identificado por ID exige autenticação, autorização e teste de acesso cruzado entre contas.

---

## Backend

### BE-01 — Conta, autenticação, sessão e recuperação de senha

**Objetivo:** implementar o ciclo de identidade do usuário e criar toda conta já vinculada ao plano Transacional.

**Campos e contratos:** cadastro `fullName`, `email`, `password`, `personType (PF|PJ)`, `taxId`, `initialMode (SELLER|AFFILIATE)` e aceite legal `termsHash`; login `email`, `password`; sessão com `accountId`, modo ativo e expiração por 12 horas de inatividade; recuperação com `email`; redefinição com `token`, `newPassword`, `confirmPassword`. Persistir em `accounts`: `id`, `email`, `password_hash`, `full_name`, `person_type`, `tax_id`, `kyc_status`, `provider_account_id`, `payout_delay`, `risk_tier`, `status`, `created_at`. Criar `platform_subscriptions` automaticamente.

**Regras:** Argon2id; e-mail normalizado; CPF/CNPJ válido; resposta de recuperação idêntica para conta existente ou inexistente; token de uso único com validade de 1 hora; conta encerrada libera e-mail/documento sem apagar histórico; alternância de modo não exige novo login.

**Critérios de aceite:** `POST /v1/accounts`, login, logout, renovação/invalidação de sessão, solicitação e conclusão de recuperação funcionam; duplicidade ativa retorna erro estável por campo; conta nasce com plano; sessão expira após 12 horas inativa; testes cobrem enumeração de usuário, senha fraca, token expirado/reutilizado e acesso de conta suspensa.

**Referências:** RF-001 a RF-005, RF-110, RF-117, RF-125, RNF-019, RNF-024.

### BE-02 — KYC, subconta do provedor e MFA

**Objetivo:** controlar verificação de identidade e operações sensíveis sem disparar KYC no cadastro.

**Campos e contratos:** `POST /v1/accounts/me/kyc`; resposta `providerUrl`, `kycStatus`, `requirements[] {code,label,status,reason?,estimatedAt?}`; webhook do provedor com identificador estável; `mfa_credentials` com segredo cifrado, recuperação e timestamps; desafio MFA `code`, `operation`, `challengeId`.

**Regras:** KYC começa somente na primeira tentativa de publicar; rascunho é permitido antes disso; publicação bloqueada até `APPROVED`; criar subconta apenas no fluxo aprovado; cobrar taxa de verificação uma única vez na aprovação, lançando em `DEBT` se não houver saldo; exigir MFA em alteração bancária e saque acima do limite configurado.

**Critérios de aceite:** tentativa de publicar conta pendente devolve ação necessária e link; webhook repetido ou simultâneo produz um efeito; rejeição não cobra taxa; aprovação repetida não duplica cobrança/subconta; painel consegue consultar itens pendentes; testes cobrem assinatura inválida, estados fora de ordem e MFA incorreto/expirado.

**Referências:** RF-006 a RF-009, RF-091, RF-123.

### BE-03 — Produtos, ofertas, meios de pagamento e publicação

**Objetivo:** disponibilizar catálogo completo com regras de imutabilidade e publicação.

**Campos e contratos:** produto `name`, `description`, `segment (SAAS|DIGITAL)`, `chargeType (ONE_TIME|SUBSCRIPTION)`, `affiliationEnabled`; oferta `priceCents`, `cycle (MONTHLY|QUARTERLY|SEMIANNUAL|ANNUAL|null)`, `trialDays (0..30)`, `trialRequiresCard`, `guaranteeDays (>=7)`, `maxInstallments (1..12)`, `boletoDueDays (1..15)`, `boletoAdvanceDays (3..10)`, `paymentMethods[]`, `payoutDelay (D32|D15|D7|D2)`, `status (DRAFT|PUBLISHED|ARCHIVED)` e `slug`. Criar rotas de listar, detalhar, criar, editar, publicar e arquivar.

**Regras:** preço comercial mínimo 2.000 centavos; boleto apenas para SAAS; teste sem cartão apenas SAAS; meios devem ser permitidos pelo segmento; slug permanente por oferta; produto/oferta em rascunho não têm checkout público; `productId`, `segment` e `chargeType` imutáveis após oferta, e `cycle`/`guaranteeDays` imutáveis após primeira cobrança confirmada; preço permanece editável.

**Critérios de aceite:** banco e API recusam combinações inválidas; publicação exige KYC aprovado e configuração fiscal quando aplicável; resposta inclui disponibilidade efetiva calculada no servidor; testes cobrem cada transição e imutabilidade após venda.

**Referências:** RF-010 a RF-020, RF-092, RF-099, RF-111, RF-118.

### BE-04 — Cupons e personalização segura do checkout

**Objetivo:** permitir desconto concorrente e personalização limitada sem ampliar o escopo PCI.

**Campos e contratos:** cupom `code`, `discountType (PERCENT|FIXED)`, `discountBps?`, `discountCents?`, `startsAt?`, `expiresAt?`, `maxRedemptions?`, `maxPerBuyer?`, `offerIds[]`; aparência `logoAssetId?`, `primaryColor`, `buttonText`, `bannerAssetId?`, `sideImageAssetId?`. Endpoint de simulação recebe `offerId`, `method`, `installments`, `couponCode?` e devolve memória completa calculada pelo backend.

**Regras:** resgate usa `UPDATE` condicional na mesma transação do pedido; limite por comprador é conferido depois do bloqueio do cupom; taxas e comissão incidem sobre o pago; uploads hospedados pela Paysi, nunca URL externa; validar MIME, tamanho e dimensões; sem CSS livre.

**Critérios de aceite:** cem resgates simultâneos não excedem limite; cupom inválido/expirado/esgotado retorna código estável; simulação com e sem desconto fecha em centavos; exclusão é lógica; URL externa e arquivo inválido são recusados; testes cobrem concorrência e autorização por vendedor.

**Referências:** RF-018, RF-027 a RF-030, RNF-034.

### BE-05 — Checkout público, comprador, aceite e idempotência

**Objetivo:** expor a oferta e criar pedido sem confiar em preço ou taxa enviados pelo navegador.

**Campos e contratos:** `GET /v1/offers/{slug}/checkout` retorna produto, descrição, segmento, cobrança, preço atual, ciclo, valor de hoje/próxima cobrança/data, meios, parcelas, campos obrigatórios, aparência e textos legais. `POST /v1/checkout/{slug}/orders` recebe `buyer {name,email,personType,taxId,legalName?,municipalReg?,address {zip,street?,number,complement?,district?,city?,state?}}`, `method`, `installments`, `cardToken?`, `coupon?`, `visitorKey?`, `termsHash`; exige `Idempotency-Key`.

**Regras:** telefone não é obrigatório sem justificativa; SAAS ou comprador PJ exige dados fiscais/endereço; criar/reutilizar `buyers` e gravar `buyer_snapshot`; preço, desconto, comissão, plano e taxas são relidos do banco; mesma chave+mesmo corpo devolve mesma resposta por 24h; mesma chave+corpo diferente retorna 409; escopo da chave é a oferta; aceite registra hash e timestamp.

**Critérios de aceite:** pedido não aceita valor do cliente; duas requisições simultâneas idênticas criam um pedido; campos condicionais são informados pela oferta; acesso a slug não publicado retorna 404; testes cobrem PF/PJ, SAAS/DIGITAL, cupom e abuso de idempotência.

**Referências:** RF-021, RF-022, RF-025 a RF-028, RF-093, RNF-015.

### BE-06 — Provedor: Pix, cartão tokenizado, 3DS e boleto

**Objetivo:** implementar a porta `PaymentProvider`, fake local e adaptador real desacoplado.

**Campos e contratos:** entrada normalizada `orderId`, `amountCents`, `method`, `installments`, `cardToken?`, `buyer`, `splitInstructions`; saída `providerChargeId`, `status`, `qrCode?`, `copyPaste?`, `expiresAt?`, `boletoBarcode?`, `boletoPdfUrl?`, `threeDS {required,status,challengeUrl?,eci?}`, `providerFeeCents`, `receivableSchedule[]`. Inbox `provider_events`: `provider`, `provider_event_id`, `event_type`, `payload`, `signature_valid`, `received_at`, `processed_at`, `status`, `error`, `attempt_count`, `next_retry_at`.

**Regras:** cartão só usa token do SDK; nenhum PAN/CVV passa pela Paysi; Pix e boleto expiram; boleto vence em 1..15 dias; 3DS vira evidência; evento é persistido na mesma transação de seu efeito; reentrega é idempotente; transições de pedido/cobrança são explícitas.

**Critérios de aceite:** fake cobre aprovado, recusado, pendente, expirado e falha; webhook duplicado simultâneo gera um efeito; assinatura inválida não processa; cartão recusado pode gerar nova tentativa Pix em até 24h; testes de contrato podem ser reaproveitados pelo adaptador real.

**Referências:** RF-023, RF-024, RF-032, RF-097, RF-100, RF-123.

### BE-07 — Divisão financeira, cobrança e recebíveis parcelados

**Objetivo:** integrar o motor existente ao pedido e congelar a memória de cálculo de cada cobrança.

**Campos e contratos:** `charges` deve guardar `order_id`, `subscription_id?`, `cycle_number?`, `provider_charge_id`, `amount_cents`, `plan`, `platform_fee_bps`, `platform_fee_fixed_cents`, `platform_fee_cents`, `affiliate_fee_cents`, `seller_amount_cents`, `provider_fee_cents`, `status`, tentativas e datas. `receivables` guarda parcela, data esperada, valor total, parte do vendedor, parte do afiliado, identificador do provedor e liquidação.

**Regras:** plataforma cobra percentual do plano + 200 centavos; afiliado no máximo 50%; vendedor é residual exato; enviar ao PSP apenas vendedor/afiliado em valores fixos; bloquear quando custo real supera taxa; maior resto rateia parcelas uma vez e persiste; não recalcular na liberação; invariante fecha com custo do provedor.

**Critérios de aceite:** exemplo de 17.700 centavos resulta em vendedor 14.670, afiliado 1.770, plataforma 682 e provedor 578; parcelamento 12x soma exatamente; custo maior bloqueia e alerta; teste de varredura cobre arredondamento e partes não negativas.

**Referências:** RF-034 a RF-041, RNF-011, RNF-012, RNF-040.

### BE-08 — Livro-razão, cinco saldos, liberações e checkpoints

**Objetivo:** transformar fatos confirmados em lançamentos imutáveis e saldo consultável com segurança concorrente.

**Campos e contratos:** implementar `ledger_accounts`, `ledger_transactions`, `ledger_entries`, `ledger_release_schedule`, `ledger_checkpoints`; `GET /v1/balance` retorna `guaranteeCents`, `pendingCents`, `reserveCents`, `availableCents`, `debtCents`, `asOf`; `GET /v1/ledger` por cursor retorna bucket, direção, valor, origem, motivo, referência, disponibilidade e memória.

**Regras:** toda transação soma zero; UPDATE/DELETE proibidos; escrita adquire advisory lock de todas as contas em ordem canônica; nenhum bucket do usuário além de DEBT fica negativo e DEBT nunca positivo; venda entra em GUARANTEE; saída compensa DEBT antes das demais alocações; disponibilidade em `max(recebimento, garantia)`; reserva 4%..10% por 90 dias; checkpoints sob bloqueio e reconstruíveis.

**Critérios de aceite:** cem operações concorrentes não geram saldo negativo; job repetido move uma vez; saldo retorna cinco estados e p95 definido; oito views de integridade acusam defeitos injetados; aplicação não possui privilégio para alterar lançamentos.

**Referências:** RF-062 a RF-070, RF-104, RF-111, RF-121, RNF-004, RNF-013, RNF-014, RNF-032, RNF-038, RNF-039.

### BE-09 — Afiliação, cliques, atribuição e comissões

**Objetivo:** implementar marketplace e ciclo completo de afiliação para qualquer conta verificada.

**Campos e contratos:** marketplace por cursor; pedido de afiliação com `productId`; aprovação com `commissionBps`, `attributionWindowDays` (fixo 60), `recurrence (FIRST_CHARGE|ALL_CYCLES)`; afiliação com estados `PENDING|APPROVED|ENDED|FRAUD_ENDED`; link e clique com `visitorKey`, produto, afiliado, criado e expiração.

**Regras:** vendedor aprova; comissão imutável após aprovação e limitada a 5.000 bps; último clique válido vence; bloquear autocompra por CPF, CNPJ ou e-mail; comissão só libera após garantia; reembolso/contestação estorna; encerramento comum preserva assinaturas atribuídas, fraude interrompe; projeção é estimativa baseada em dados reais.

**Critérios de aceite:** conta pode vender e divulgar sem saldo separado; atribuição concorrente é determinística; autocompra não gera comissão; marketplace só lista itens publicados e afiliáveis; testes cobrem encerramentos, recorrência e expurgo após 60 dias.

**Referências:** RF-042 a RF-052, RF-108.

### BE-10 — Assinaturas, teste grátis, cobrança recorrente e régua

**Objetivo:** implementar ciclo de assinatura sem duplicar cobrança.

**Campos e contratos:** assinatura `order_id`, `offer_id`, `buyer_id`, `status (TRIALING|ACTIVE|PAST_DUE|CANCELED)`, `cycle`, `current_cycle`, `current_period_start/end`, `trial_end?`, `next_charge_at?`, `cancel_at_period_end`, token/referência do meio; mudança de plano com próxima vigência; endpoints listar, detalhar, cancelar e atualizar meio.

**Regras:** cobrança automática repete split; unicidade `(subscription_id, cycle_number)`; falha reutiliza a mesma cobrança e incrementa tentativa em D+1/D+3/D+7/D+14; quinta falha cancela; teste sem cartão notifica sem tentar cobrar e cancela em D+14; cancelamento vale no fim do ciclo; boleto é emitido 3..10 dias antes e lembrado D-3/D0/D+2; suspensão do vendedor suspende cobranças.

**Critérios de aceite:** job executado por duas instâncias não duplica ciclo; régua usa datas da primeira falha; ciclo recuperado gera comissão; mudança vale no ciclo seguinte sem pró-rata; notificações e estados são observáveis em testes com relógio controlado.

**Referências:** RF-053 a RF-061, RF-098, RF-119, RF-120, RNF-005.

### BE-11 — Conta bancária, saque, titularidade e MFA

**Objetivo:** permitir saque Pix somente para conta verificada do mesmo titular.

**Campos e contratos:** conta bancária `holderType`, `holderTaxId`, `holderName`, `bankCode`, `branch`, `accountNumber`, `accountDigit`, `accountType`, `pixKeyType`, `pixKey`, `providerBankAccountId`; saque `amountCents`, `bankAccountId`, `Idempotency-Key`, `mfaChallengeId?`; resposta com saldo antes/depois, status e identificador.

**Regras:** verificar titularidade no cadastro e novamente no saque; banco impõe `payout.account_id == bank_account.account_id`; mínimo 200 centavos; bloquear com DEBT; saldo disponível não pode ficar negativo; MFA acima do limite; arquivo de comprovante e falhas são rastreáveis.

**Critérios de aceite:** tentativa conta A→banco B falha no banco; duas solicitações idênticas movimentam uma vez; banco não verificado/arquivado é recusado; erro informa origem do débito; testes cobrem corrida de saldo e expiração de MFA.

**Referências:** RF-009, RF-068, RF-103, AM-12.

### BE-12 — Reembolsos, contestações, evidências e risco

**Objetivo:** tratar reversões e risco com memória de cálculo auditável.

**Campos e contratos:** `POST /v1/charges/{id}/refunds` recebe `amountCents?`, `reason`, `Idempotency-Key`; cada refund persiste valor, partes `sellerCents`, `affiliateCents`, `platformCents`, `providerCents`, status e IDs. Evidência armazena IP, user agent, device fingerprint, termos/hash/horário, 3DS, entrega, abertura de e-mail, acesso e suporte. Disputa guarda charge, estado, prazo, tarifa, pacote e resultado.

**Regras:** dentro da garantia é automático; parcial usa truncagem cumulativa gravada, e cobrança só vira REFUNDED no total; depois da garantia debita GUARANTEE→PENDING→AVAILABLE→DEBT sem tocar RESERVE; vendedor/afiliado devolvem apenas o recebido; reserva é primeira fonte de chargeback; limiares de risco de vendedor e plataforma seguem RF-076/077/106; bloqueio exige notificação e contestação.

**Critérios de aceite:** múltiplos parciais nunca excedem pago e somam exatamente; eventos parcial/total são distintos; pacote de defesa é reproduzível; índices geram alerta/suspensão nos limiares; testes cobrem disputa ganha/perdida e concorrência.

**Referências:** RF-071 a RF-081, RF-105 a RF-107, RF-122.

### BE-13 — Outbox, webhooks do vendedor e notificações

**Objetivo:** entregar eventos ao menos uma vez com segurança e histórico operacional.

**Campos e contratos:** endpoint de destino `url`, `subscribedEvents[]`, `enabled`; segredo gerado por endpoint, exibido uma vez; rotação com segredo atual/anterior e expiração; delivery com evento, tentativa, código HTTP, erro, timestamps e `nextRetryAt`; notificações com canal, destinatário, template, payload e status.

**Regras:** outbox é gravado na mesma transação do fato; publicação usa `FOR UPDATE SKIP LOCKED`; HMAC-SHA256 sobre `timestamp.body`; headers `X-Paysi-Signature` e `X-Paysi-Event-Id`; retentativas 1m/5m/30m/2h/12h; sobreposição de segredo por 24h; reenvio manual preserva ID do evento e cria nova tentativa.

**Critérios de aceite:** queda após commit não perde evento; duas instâncias não fazem duplicação interna; histórico mostra código/resposta; segredo nunca reaparece; rotação aceita ambos por 24h; SSRF e URL insegura são recusadas.

**Referências:** RF-082 a RF-086, RF-109, RNF-028.

### BE-14 — Fiscal, planos, administração, LGPD e conciliação

**Objetivo:** cobrir funções operacionais obrigatórias sem misturar fatos financeiros.

**Campos e contratos:** perfil fiscal `municipalityCode`, `municipalRegistration`, `serviceItem`, `taxBps`, `credentialRef`, `validatedAt`; invoice por cobrança com emissor, tomador, estado, número, link, tentativas/cancelamento; plano `TRANSACIONAL|ESCALA`, tabela e vigência; admin busca conta/venda/assinatura, suspende/bloqueia/encerra com `reason`; ajuste informa conta, bucket, direção, valor, referência, motivo, solicitante/aprovador; LGPD registra tipo, titular, prazo, responsável, evidência e estado.

**Regras:** nota de venda é emitida pelo vendedor; banco recusa emissor divergente; emissão assíncrona não bloqueia pagamento; reembolso solicita cancelamento; mensalidade Escala debita disponível, tenta cartão e rebaixa após 10 dias; mudança de plano não retroage; auditoria append-only; quem solicita ajuste não aprova; conciliação diária alerta diferença acima de 1 centavo.

**Critérios de aceite:** emissor fake e falhas/reprocessamento cobertos; pagamento confirma mesmo com fiscal fora; ações admin exigem motivo e MFA próprio; ajuste fecha em partidas dobradas; pedido LGPD tem SLA/evidência; relatório de conciliação aponta divergência injetada.

**Referências:** RF-087 a RF-090, RF-094 a RF-096, RF-101, RF-102, RF-112 a RF-116, RF-124, RF-126.

### BE-15 — Segurança, observabilidade, desempenho e CI de lançamento

**Objetivo:** tornar os requisitos não funcionais verificáveis e bloqueantes no pipeline.

**Campos e contratos:** erro padrão `code`, `message`, `field?`, `correlationId`; logs estruturados com correlação e IDs não sensíveis; métricas de checkout, PSP, jobs, filas, conciliação e integridade; health/readiness; alertas; rate limit por chave, IP, CPF e dispositivo; runbooks e evidência de backup/restore.

**Regras:** TLS 1.2+, HSTS, CSP estrita e inventário de scripts; nunca logar senha, token, PAN/CVV ou documento completo; cursor em paginação; 429 com `Retry-After`; scheduler com ShedLock/advisory lock; aplicação com usuário de banco restrito; CI executa Java, ArchUnit, 70 testes SQL, frontend, análise estática e carga contratual.

**Critérios de aceite:** checkout p95 <1,5s em 4G e API de cobrança p95 <800ms; bundle inicial <180KB gzip incluindo SDK; saldo p95 <200ms com 5 milhões de lançamentos; teste cruzado bloqueia IDOR; restauração comprovada; oito checagens e alertas chegam ao canal operacional.

**Referências:** RNF-001 a RNF-040 e checklist de lançamento.

---

## Frontend

### FE-01 — Fundação do painel: design system, shell, sessão e cliente HTTP

**Objetivo:** transformar o esqueleto Next.js em base reutilizável do painel.

**Campos/componentes:** `Botao`, `Campo`, `Select`, `Checkbox`, `Radio`, `Cartao`, `Tabela`, `Etiqueta`, `Interruptor`, `Abas`, `Dialog`, `Toast`, `Skeleton`, `EmptyState`; shell com `BarraLateral`, `Cabecalho`, `TrocaDePainel`; cliente HTTP com token, correlação, refresh/logout, erro por campo e paginação por cursor.

**Critérios de aceite:** estados loading/vazio/erro/sucesso documentados; sessão protegida no servidor quando aplicável; navegação por teclado, foco visível e mensagens anunciadas; layout responsivo; nenhum componente faz aritmética monetária; tokens visuais existentes são reutilizados.

### FE-02 — Cadastro, login e recuperação de senha

**Campos:** cadastro `nome completo`, `e-mail`, `senha`, `confirmar senha`, `tipo PF/PJ`, `CPF/CNPJ`, `modo inicial vender/divulgar`, aceite dos termos; login `e-mail`, `senha`, lembrar dispositivo opcional; recuperação `e-mail`; redefinição `nova senha`, `confirmar senha`.

**Critérios de aceite:** validação inline e erro de API por campo; máscara não altera valor enviado; recuperação não revela existência da conta; senha pode ser exibida/ocultada; sucesso redireciona ao modo escolhido; textos legais exibem declaração regulatória configurada.

### FE-03 — Verificação de identidade e perfil da conta

**Campos/telas:** estado geral KYC, checklist `item`, `status`, `motivo`, `prazo`; CTA iniciar/continuar no provedor; dados da conta em leitura; prazo de recebimento `D+32/D+15/D+7/D+2`; estado da conta e plano.

**Critérios de aceite:** KYC não abre no cadastro; primeira publicação pendente direciona para verificação; retorno do provedor atualiza por polling seguro; rejeição mostra próximos passos; taxa de verificação e possível origem do saldo devedor são explicadas antes da ação.

### FE-04 — Dashboard do vendedor e troca de modo

**Campos/blocos:** vendas hoje/período, quantidade, saldo nos cinco buckets, próximos recebimentos, assinaturas ativas/inadimplentes, alertas KYC/fiscal/risco/dívida, últimas vendas e ações rápidas.

**Critérios de aceite:** período e estados refletem resposta do backend; moeda apenas formatada; vazio orienta cadastrar produto; erro parcial não derruba a página; alternar vendedor/afiliado preserva sessão e rota válida.

### FE-05 — Produtos e ofertas: lista, cadastro, edição e publicação

**Campos:** `nome`, `descrição`, `segmento`, `tipo de cobrança`, `preço`, `ciclo`, `teste grátis em dias`, `exigir cartão no teste`, `garantia`, `meios`, `parcelas`, `vencimento/antecedência do boleto`, `prazo de recebimento`, `afiliações`, status e slug. Exibir simulação por método/cupom recebida do backend.

**Critérios de aceite:** campos condicionais seguem segmento/cobrança; limites são visíveis; rascunho salva sem KYC; publicar inicia KYC quando necessário; campos imutáveis ficam bloqueados com explicação; copiar/abrir link funciona; nenhuma taxa é calculada no browser.

### FE-06 — Cupons e aparência do checkout

**Campos:** código, tipo/valor do desconto, início/fim, limite total, limite por comprador, ofertas; upload de logo/banner/imagem lateral, seletor de cor e texto do botão; prévia desktop/mobile.

**Critérios de aceite:** formulário impede combinações inválidas; upload mostra progresso/erro e aceita somente formatos definidos; não existe campo URL/CSS; prévia usa dados retornados pela API; cupom arquivado não some do histórico.

### FE-07 — Vendas, detalhe, reembolso e nota fiscal

**Campos/telas:** filtros por período/status/método/produto; tabela com comprador mascarado, valor, método, estado e data; detalhe com pedido, cobranças, split, recebíveis, afiliação, aceite/3DS, histórico de eventos e nota. Reembolso recebe cobrança, valor total/parcial, motivo, confirmação e idempotência.

**Critérios de aceite:** assinatura com várias cobranças obriga selecionar cobrança; valor máximo vem do servidor; confirmação distingue parcial/total e impacto; nota permite abrir/reemitir; dados sensíveis são mascarados; paginação é cursor.

### FE-08 — Assinaturas e atualização do meio de pagamento

**Campos:** busca/filtros, comprador, produto, ciclo, estado, período atual, próxima cobrança, tentativas, cancelamento agendado; detalhe com cobranças; cancelar ao fim do ciclo; link seguro para atualizar meio.

**Critérios de aceite:** estados TRIALING/ACTIVE/PAST_DUE/CANCELED têm texto claro; régua mostra D+1/D+3/D+7/D+14; teste sem cartão não promete cobrança; ação de cancelamento explica vigência; nenhuma informação de cartão aparece.

### FE-09 — Afiliados do vendedor

**Campos:** produto, solicitante, comissão proposta/aprovada, janela, recorrência, status e datas; ações aprovar, rejeitar, encerrar e encerrar por fraude com motivo.

**Critérios de aceite:** aprovação exige comissão 0..50%; comissão aprovada fica somente leitura; encerramento comum e fraude exibem impactos diferentes; lista tem filtros e histórico; autovínculo rejeitado pela API aparece com mensagem específica.

### FE-10 — Saldo, extrato, conta bancária e saque

**Campos:** cartões `garantia`, `pendente`, `reserva`, `disponível`, `devedor`; extrato com origem, motivo, referência, bucket, valor e disponibilidade; conta bancária `titular`, `CPF/CNPJ`, `banco`, `agência`, `conta/dígito`, `tipo`, `tipo/chave Pix`; saque `valor`, conta e MFA quando solicitado.

**Critérios de aceite:** DEBT é explicado por linha, inclusive taxa de KYC; saldo negativo bloqueia saque com origem; titularidade exige confirmação; valor mínimo e disponível vêm da API; idempotência impede duplo clique; alteração bancária exige MFA.

### FE-11 — Plano, integrações, webhooks e notificações

**Campos:** plano atual, tabela vigente, mensalidade, próxima cobrança e troca futura; chave de API com nome/escopos/último uso/revogação; webhook com URL, eventos, estado, segredo único, rotação e histórico de entregas; preferências por tipo/canal.

**Critérios de aceite:** economia/projeção vem do servidor; segredo só aparece após criação/rotação com confirmação de cópia; histórico mostra tentativa/HTTP/erro e permite reenvio; chaves são mascaradas; troca de plano informa vigência sem pró-rata.

### FE-12 — Modo afiliado: vitrine, pedidos, links e comissões

**Campos/telas:** vitrine com busca/filtros, produto, vendedor, preço, comissão estimada, garantia, atribuição e recorrência; detalhe/solicitação; meus links com copiar e métricas; comissões com bucket, origem, previsão e estornos.

**Critérios de aceite:** apenas conta verificada solicita; termos da afiliação aparecem antes; estimativa é rotulada e servida pela API; link contém identificador opaco; encerramento explica manutenção de recorrências; moeda só é formatada.

### FE-13 — Checkout público: oferta, comprador, fiscal, cupom e aceite

**Campos:** resumo antes do formulário; comprador `nome`, `e-mail`, `PF/PJ`, `CPF/CNPJ`; condicional PJ/SAAS `razão social`, `inscrição municipal opcional`, `CEP`, `logradouro`, `número`, `complemento`, `bairro`, `cidade`, `UF`; cupom atrás de link; aceite de termos/reembolso; valor hoje, próxima cobrança e data.

**Critérios de aceite:** telefone não é obrigatório; campos vêm do contrato público; CPF/CNPJ e CEP têm validação acessível; resumo atualiza somente após resposta de simulação; termos geram hash versionado; marca/provedor e declaração legal aparecem no rodapé; sem scripts/URLs do vendedor.

### FE-14 — Checkout público: cartão/3DS, Pix, boleto e estados finais

**Campos/estados:** seletor de meio/parcelas; container do SDK de cartão sem acesso a PAN/CVV; desafio 3DS; Pix com QR, copia-e-cola, expiração e polling; boleto com linha, PDF e vencimento; aprovado, recusado, pendente e expirado; alternativa Pix após recusa.

**Critérios de aceite:** somente token do provedor vai à API; chave de idempotência persiste por tentativa; duplo clique não duplica; polling para em estado final; QR/código/boleto são copiáveis e acessíveis; recusa permite Pix por 24h; nenhum dado sensível aparece em log/analytics.

### FE-15 — Qualidade web: acessibilidade, responsividade, segurança e desempenho

**Escopo:** testes E2E dos fluxos críticos, auditoria WCAG, CSP/headers, inventário de scripts, mascaramento de PII, estados offline/rede lenta, Core Web Vitals e orçamento de bundle.

**Critérios de aceite:** checkout interativo p95 <1,5s em perfil 4G; bundle inicial <180KB gzip incluindo SDK; painel e checkout navegáveis por teclado e leitor; zero erro crítico de acessibilidade; E2E cobre compra Pix/cartão/boleto, assinatura, reembolso, saque e afiliação; nenhuma aritmética monetária detectada no frontend.

---

## Mobile

> Não existe aplicação mobile no repositório atual e o arquivo citado como `docs/paysi-mobile.html` não está presente. Este backlog assume aplicativo React Native para vendedor e afiliado; checkout continua web responsivo.

### MO-01 — Fundação do aplicativo, navegação, sessão e atualização segura

**Escopo/campos:** criar workspace mobile, ambientes, navegação pública/autenticada, abas por modo, cliente API tipado, armazenamento seguro de token, renovação/logout, deep links, analytics sem PII, tema/tokens e componentes base.

**Critérios de aceite:** iOS/Android compilam em CI; token fica em Keychain/Keystore; sessão expirada volta ao login; deep link autenticado retoma destino; erros/loading/vazio/offline padronizados; nenhum cálculo monetário no app.

### MO-02 — Acesso: criar conta, entrar e recuperar senha

**Campos:** os mesmos de FE-02, com teclado/autofill corretos, máscara visual, aceite legal e abertura segura dos termos.

**Critérios de aceite:** paridade de validações com API; recuperação não enumera conta; senha não vai a logs/analytics; acessibilidade e retorno de teclado testados; modo inicial define a primeira aba.

### MO-03 — Início, troca de modo e notificações push

**Campos/blocos:** resumo do vendedor ou afiliado, cinco saldos, vendas/comissões, alertas e ações; seletor vender/divulgar; preferências e registro/revogação do token push.

**Critérios de aceite:** mudança de modo não reloga; valores são formatados; push abre recurso autorizado; conteúdo sensível é ocultado na tela bloqueada; revogar sessão remove token do dispositivo.

### MO-04 — Verificação de identidade e conta

**Campos:** checklist KYC, status/prazo/motivo, CTA abrir fluxo do provedor, retorno por deep link, perfil, tipo/documento mascarado, prazo de recebimento e plano.

**Critérios de aceite:** KYC só inicia após ação de publicação; retorno atualiza estado; rejeição orienta correção; taxa e possível DEBT são explicados; webview/browser não expõe token indevido.

### MO-05 — Produtos e ofertas

**Campos:** lista/filtros/status; cadastro/edição com todos os campos de FE-05; simulação servida pelo backend; publicação, arquivamento e compartilhamento do link.

**Critérios de aceite:** condicionais SAAS/DIGITAL e assinatura/avulso funcionam; rascunho offline não é prometido sem sincronização; campos imutáveis são explicados; KYC intercepta publicação; link abre checkout web.

### MO-06 — Vendas, cobrança, reembolso e fiscal

**Campos:** lista/filtros, detalhe do pedido e cobranças, comprador mascarado, split, recebíveis, status fiscal e reembolso total/parcial com motivo/confirmação.

**Critérios de aceite:** usuário escolhe cobrança da assinatura; valor máximo e impacto vêm da API; idempotência evita repetição; nota abre em navegador seguro; atualização por pull-to-refresh/push não duplica itens.

### MO-07 — Assinaturas

**Campos:** lista, estados, período, próxima cobrança, tentativas, histórico e cancelamento no fim do ciclo.

**Critérios de aceite:** régua e teste sem cartão têm mensagens distintas; cancelamento exige confirmação e mostra vigência; detalhes não exibem cartão; deep link de inadimplência abre assinatura correta.

### MO-08 — Saldo, extrato, banco e saque com biometria/MFA

**Campos:** cinco buckets, extrato por cursor e memória; todos os campos bancários de FE-10; saque com valor, destino, confirmação biométrica local e MFA do servidor quando exigido.

**Critérios de aceite:** DEBT e origem bloqueiam saque; titularidade é confirmada; biometria não substitui regra/MFA do servidor; duplo toque movimenta uma vez; comprovante pode ser compartilhado sem PII excessiva.

### MO-09 — Afiliados: solicitações, vitrine, links e comissões

**Campos:** gestão do vendedor e experiência do afiliado descritas em FE-09/FE-12; busca, filtros, aprovação/comissão, termos, copiar/compartilhar link, métricas e extrato de comissão.

**Critérios de aceite:** ações respeitam modo e autorização; comissão aprovada é imutável; fraude exige motivo; estimativa é rotulada; compartilhamento usa link opaco e não vaza identificadores internos.

### MO-10 — Segurança, acessibilidade, observabilidade e publicação

**Escopo:** pinning apenas se política operacional suportar rotação, detecção de root/jailbreak como sinal, redaction de logs/crash, proteção de screenshots em telas sensíveis, permissões mínimas, testes E2E, leitor de tela, tamanhos dinâmicos, build assinado e rollout.

**Critérios de aceite:** nenhum segredo/PII em crash/analytics; cobertura E2E dos fluxos críticos; VoiceOver/TalkBack e contraste aprovados; app atende política das lojas; ambiente e versão aparecem no diagnóstico; kill switch e rollback documentados.

