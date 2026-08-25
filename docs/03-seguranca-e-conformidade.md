# Paysi — Documento 3: Segurança e Conformidade

**Versão 3.0 · 21 de agosto de 2026 · interno restrito · Substitui a versão 2.0 e incorpora as revisões v2.1 e v3.0**

---

> **Leia antes de usar este documento.** Ele organiza riscos e controles técnicos. **Não substitui parecer jurídico nem auditoria de segurança.** Todo item marcado `JUR` precisa de validação por advogado especializado em regulação do sistema financeiro; todo `FIS`, por contador. A operação não deve iniciar sem essa validação.

---

## 1. Modelo de ameaças

Ameaças ordenadas por perda esperada, não por probabilidade. A coluna de exposição indica **quem paga** quando a ameaça se concretiza.

### 1.1 Fraude do vendedor

| ID | Ameaça | Controle | Exposição |
|---|---|---|---|
| AM-01 | Vender alto, sacar e desaparecer antes das contestações chegarem | Faixas de limite de volume (doc 1 §5.5); reserva de 4% a 10% retida por 90 dias; verificação de identidade obrigatória antes de vender; aprovação manual das primeiras contas | Plataforma |
| AM-02 | Vender produto proibido ou com marca de terceiro | Lista de produtos proibidos publicada; revisão manual acima de limite; suspensão imediata com retenção para apuração | Plataforma |
| AM-03 | Usar a plataforma para lavagem: entrada de recursos por vendas fictícias | Monitoramento de padrão sobre a entidade `buyers`: mesmo comprador repetido, ticket alto sem histórico, saque imediato integral; comunicação ao provedor `JUR` | Plataforma e provedor |
| AM-04 | Vendedor com produto ruim gerando reembolso em massa | Alerta em 8%, revisão em 12%, suspensão em 20%. Necessário porque a plataforma absorve a taxa em cada reembolso | Plataforma |
| AM-21 | Usar a emissão automatizada de NFS-e para dar aparência fiscal a operação simulada | Nota sempre em nome do vendedor, com o CNPJ dele e trilha completa, **imposto por gatilho**; volume de emissão entra no monitoramento de AM-03; recusa de emitir para conta suspensa | Vendedor e plataforma |
| AM-23 | Acumular saldo devedor por contestação e abandonar a conta | Bloqueio de saque com dívida (RF-103); compensação automática em vendas futuras (RF-104); baixa só com aprovação registrada (RF-116); dívida acima de limiar dispara revisão de faixa | Plataforma |

> **AM-01 é o risco número um do negócio.** A reserva cobre operação normal, com contestação em torno de 1%. **Não cobre fraude deliberada.** Um vendedor que fature R$ 500 mil numa semana e desapareça deixa uma reserva de R$ 20 mil contra uma exposição de até R$ 500 mil.
>
> O limite de volume é o único controle que realmente contém esse cenário — e por isso as quatro faixas objetivas substituíram o "liberando por histórico" sem critério. Um vendedor só chega a R$ 500 mil em 30 dias depois de 180 dias de histórico limpo, e nessa altura já existe reserva acumulada de verdade.

### 1.2 Fraude do comprador

| ID | Ameaça | Controle | Exposição |
|---|---|---|---|
| AM-05 | Teste de cartões roubados usando o checkout como validador | Limite de tentativas por IP, por CPF e por impressão de dispositivo; verificação invisível; bloqueio progressivo; alerta ao ultrapassar limiar por oferta | Plataforma |
| AM-06 | Compra com cartão roubado seguida de contestação | **Autenticação 3DS** acima de limiar configurável e em transação com sinal de risco (RF-100); antifraude do provedor; evidências completas; defesa automática | Vendedor |
| AM-07 | Contestação de má-fé após consumir o produto | Registro de acesso ao produto, entrega e abertura do e-mail, aceite dos termos com hash e horário, resultado do 3DS | Vendedor |
| AM-08 | Pix fraudado com bloqueio posterior pelo mecanismo de devolução | Não liberar saldo de Pix antes do prazo de garantia — o que o fluxo de buckets garante estruturalmente; monitorar padrão de devolução por vendedor | Plataforma |
| AM-22 | Criação em massa de contas para abusar de teste gratuito sem cartão | Limite de teste por e-mail, documento e impressão de dispositivo; teste sem cartão apenas em segmento `SAAS`; verificação invisível no início do teste | Vendedor |

> **Por que 3DS entrou como controle principal de AM-06.** A versão anterior listava apenas antifraude do provedor e defesa por evidência. Evidência boa ganha disputa de "produto não entregue"; **não ganha disputa de fraude com cartão não presente**, em que o portador legítimo nega ter feito a compra. Nesse cenário, sem autenticação, a responsabilidade é do estabelecimento — ou seja, do vendedor, e em cascata da plataforma.
>
> Com 3DS bem-sucedido, a responsabilidade se desloca para o emissor. É por isso que a resposta ao risco R-03 deixou de ser apenas "elevar a reserva de 4% para 8% a 15%": elevar reserva transfere o custo para o vendedor honesto; **3DS reduz o custo total**.
>
> Contrapartida honesta: 3DS adiciona atrito e derruba conversão. Por isso a aplicação é condicional, com limiar operacional ajustável. Depende de o provedor oferecer o recurso com repasse de responsabilidade confirmado em contrato — PEN-16. `PSP`

### 1.3 Fraude do afiliado

| ID | Ameaça | Controle | Exposição |
|---|---|---|---|
| AM-09 | Autocompra para gerar comissão | Bloqueio por CPF, CNPJ e e-mail, com gatilho no banco; verificação também de dispositivo e cartão quando disponível | Vendedor |
| AM-10 | Compra por interposta pessoa para gerar comissão e reembolsar depois | Comissão liberada só após o prazo de garantia; monitorar afiliado com índice de reembolso acima da média | Vendedor |
| AM-11 | Sobreposição de cookie sobre tráfego pago do vendedor | Janela reduzida de 90 para 60 dias; registro do clique com origem; relatório ao vendedor de quanto do volume veio por afiliado | Vendedor |

> **AM-11 continua sendo o controle mais fraco deste documento.** Reduzir a janela diminui a exposição, mas não resolve: um afiliado que compra a mesma palavra-chave de marca do vendedor continua capturando o último clique de um comprador que já viria de qualquer forma.
>
> O controle que resolveria é **atribuição com prioridade de canal** — tráfego direto e busca por marca não são sobrescritos por clique de afiliado. Isso exige registrar a origem da sessão, e não apenas o clique, e fica fora do escopo da v1. Enquanto não existir, o relatório ao vendedor é o que permite a ele perceber e encerrar a afiliação. Está registrado aqui para não ser esquecido.

### 1.4 Ataque à aplicação

| ID | Ameaça | Controle | Exposição |
|---|---|---|---|
| AM-12 | Acesso ao dado de outra conta por manipulação de identificador | Verificação de posse em toda consulta; identificadores opacos; teste automatizado que tenta acessar recurso alheio em cada rota; **titularidade de conta bancária imposta no banco** | Plataforma |
| AM-13 | Tomada de conta por vazamento de senha | Argon2id; segundo fator na troca de conta bancária e em saque acima de limite; notificação de acesso novo; sessão expirando | Usuário e plataforma |
| AM-14 | Falsificação de notificação para o sistema do vendedor | HMAC-SHA256 com horário, usando **segredo por endpoint**; rejeição de desvio superior a 5 minutos; documentação de verificação obrigatória | Vendedor |
| AM-15 | Manipulação de valor no cliente antes do envio | Valor sempre recalculado no servidor a partir da oferta; corpo da requisição jamais define preço, comissão ou taxa | Plataforma |
| AM-16 | Cobrança duplicada por reenvio de requisição | Chave de idempotência obrigatória, gravada com `SET NX`, resposta memorizada por 24 horas e espelho durável no banco | Comprador e plataforma |
| AM-17 | Saque duplicado por concorrência | Bloqueio consultivo por conta com espaço de nomes próprio, dentro da transação; saldo calculado dentro do bloqueio; **ordem canônica na aquisição** | Plataforma |
| AM-18 | Abuso interno por operador | Auditoria imutável com autor, horário e motivo obrigatório; segundo fator com credencial própria; segregação entre quem aprova e quem executa, **imposta por `CHECK`** | Plataforma |
| AM-19 | Comprometimento da credencial fiscal do vendedor guardada pela Paysi | Credencial nunca em banco de aplicação: apenas referência a cofre gerenciado; acesso só pelo processo de emissão; auditoria de todo uso; rotação a pedido | Vendedor e plataforma |
| AM-20 | Injeção de recurso externo na página de checkout por configuração de aparência | Imagens hospedadas em domínio da Paysi, nunca carregadas de URL informada pelo usuário (RNF-034); política de segurança de conteúdo restritiva; inventário de scripts | Plataforma e escopo PCI |
| AM-26 | **Reentrega de notificação do provedor creditando o vendedor duas vezes** *(novo)* | Padrão inbox com chave `(provider, provider_event_id)`; chave natural `(type, reference_type, reference_id)` no razão como rede; reprocessamento por estado, não por presença | Plataforma |
| AM-27 | **Saque direcionado a conta bancária de outro titular** *(novo)* | Gatilho no banco exigindo que a conta bancária pertença ao solicitante, esteja verificada e não arquivada; segundo fator na troca de conta bancária | Plataforma |

> **AM-12 e AM-15 são os defeitos mais comuns em plataforma multiempresa.** Toda consulta que recebe um identificador precisa responder "essa conta pode ver isso?" antes de responder "esse registro existe?". E nenhum valor monetário pode vir do cliente — o servidor lê a oferta e recalcula. Estes dois pontos entram na lista de verificação obrigatória de toda revisão de código.

> **AM-27 é AM-12 no caminho onde ela custa mais caro, e existia (D09).** Nada ligava o saque à titularidade da conta bancária de destino: um saque da conta A para a conta bancária de B era aceito pelo banco. Não era falha de tela, era ausência de restrição — e é o tipo de coisa que uma revisão de código encontra tarde, porque o campo se chama `bank_account_id` e parece óbvio que ele seja da conta certa.

> **AM-14 era mais grave do que a versão anterior registrava.** O guia previa uma variável de ambiente `WEBHOOK_SIGNING_SECRET`, no singular, para toda a plataforma. Com segredo único: um vazamento compromete as notificações de todos os vendedores ao mesmo tempo; rotacionar exige quebrar a integração de todo mundo no mesmo instante, o que na prática significa nunca rotacionar; e um incidente com um único integrador contamina a base inteira.
>
> Custa uma coluna cifrada e meia hora de código na primeira semana. Depois de cem integrações, custa uma migração coordenada com cem empresas.

### 1.5 Ameaças ao meio boleto

| ID | Ameaça | Controle | Exposição |
|---|---|---|---|
| AM-24 | Adulteração da linha digitável entre a emissão e o pagamento | Boleto sempre registrado junto ao provedor; conciliação por identificador do provedor, nunca por valor; nenhum boleto gerado fora da API do provedor | Comprador |
| AM-25 | Pagamento após o vencimento, com produto já entregue | Acesso liberado somente por `payment.approved`, nunca por emissão; expiração explícita com `payment.expired` | Vendedor |

Boleto não admite contestação por bandeira, o que reduz a exposição de AM-06 — é um dos motivos de ele fazer sentido para o segmento SaaS. Em contrapartida, o prazo de compensação torna a experiência de assinatura pior, e por isso a régua é de lembretes em vez de retentativa automática.

---

## 2. Escopo PCI DSS

### 2.1 Estratégia

O objetivo é permanecer em **SAQ A**, o questionário mais simples, destinado a quem terceiriza integralmente o processamento de cartão.

```
Navegador ──▶ Componente do provedor ──▶ Provedor ──▶ Servidor Paysi
digita o     recebe o número            gera um      recebe apenas
cartão       diretamente                token        o token
```

**Regra que nunca se quebra: o número do cartão nunca toca o lado direito da fronteira.**

| Condição | Situação |
|---|---|
| Dados de cartão não são armazenados | Atendido — apenas token do provedor |
| Dados de cartão não são processados | Atendido — campos são componente do provedor |
| Dados de cartão não transitam pelos servidores | Atendido — envio direto do navegador ao provedor |
| Página de pagamento entregue por terceiro certificado | Atendido pelo componente embutido |

> **O que quebra o SAQ A.** Qualquer uma destas decisões move o projeto para SAQ D ou nível 1, com auditoria por avaliador qualificado e custo anual na casa de centenas de milhares de reais:
>
> - Criar campos próprios de cartão, ainda que só enviem ao provedor
> - Guardar o número do cartão para rotear entre adquirentes
> - Registrar em log qualquer parte do número além dos últimos quatro dígitos
> - Trafegar o número por uma rota da API, mesmo que sem persistir
>
> Se o roteamento entre adquirentes virar necessidade, a resposta é **cofre como serviço de terceiro**, nunca cofre próprio.

### 2.2 Requisitos que se aplicam mesmo em SAQ A

Desde 31 de março de 2025, requisitos antes classificados como futuros passaram a ser obrigatórios em toda auditoria PCI DSS 4.0.

| Requisito | Aplicação |
|---|---|
| Segundo fator | Obrigatório para todo acesso administrativo, com credencial própria de operador |
| Inventário de scripts | A página de pagamento mantém inventário autorizado e verifica integridade |
| Política de segurança de conteúdo | Restringir origens permitidas na página de checkout |
| Gestão de vulnerabilidade | Varredura periódica e correção com prazo definido por severidade |

> **A personalização do checkout toca este escopo.** O RF-029 permite ao vendedor configurar logo, banner e imagem lateral. Se essas imagens forem carregadas de URL informada pelo vendedor, **cada vendedor passa a poder inserir uma origem externa na página de pagamento** — o que conflita diretamente com a política de segurança de conteúdo e com o inventário de scripts, e cria a ameaça AM-20.
>
> A correção é barata e definitiva: upload para armazenamento da Paysi, servido do domínio da Paysi. Nenhuma exceção, nem "só para clientes grandes".

---

## 3. Proteção de dados pessoais

### 3.1 Inventário

| Dado | Titular | Finalidade | Retenção |
|---|---|---|---|
| Nome, e-mail, CPF ou CNPJ | Vendedor / afiliado | Identificação, prevenção à lavagem, obrigação regulatória | 5 anos após encerramento `JUR` |
| Documento e prova de vida | Vendedor / afiliado | Verificação de identidade | Não armazenado pela Paysi — fica no provedor |
| Conta bancária | Vendedor / afiliado | Execução do saque | 5 anos `JUR` |
| Credencial fiscal municipal | Vendedor | Emissão de nota em nome dele | Enquanto durar a relação; em cofre, nunca em banco |
| Nome, e-mail, CPF | Comprador PF | Execução do contrato e defesa de contestação | 18 meses após a última cobrança |
| Razão social, CNPJ, endereço, inscrição | Comprador PJ | Emissão de nota fiscal e obrigação fiscal | 5 anos `FIS` |
| IP, dispositivo, horário | Comprador | Prevenção a fraude e prova de entrega | 18 meses |
| Token de cartão | Comprador | Cobrança recorrente | Até o fim da assinatura, depois revogado no provedor |
| Registros de acesso à aplicação | Todos | Segurança e obrigação legal | 6 meses `JUR` |

> **Conflito conhecido entre exclusão e retenção.** Um comprador pode pedir exclusão dos dados; a Paysi tem obrigação de guardar registro fiscal e evidência de transação. A saída é **anonimização parcial**: preserva-se o registro contábil e o identificador da transação, removendo nome, e-mail e documento quando a retenção legal expirar.
>
> A entidade `buyers` torna isso executável: a anonimização acontece em **um** registro, com carimbo `anonymized_at`, e o retrato imutável em `orders.buyer_snapshot` só é preservado enquanto durar a obrigação legal. Antes, com os dados copiados em cada pedido, não havia como garantir que todas as cópias tinham sido tratadas.

### 3.2 Controles técnicos

| Controle | Aplicação |
|---|---|
| Mascaramento em registros | Documento, e-mail e token nunca aparecem completos em log ou rastreamento |
| Criptografia em repouso | Banco com criptografia de disco; conta bancária, chave Pix, segredo de webhook e credencial fiscal cifrados em coluna |
| Criptografia em trânsito | TLS 1.2 ou superior em toda comunicação, incluindo entre serviços |
| Acesso de operador | Menor privilégio, segundo fator, auditoria com motivo obrigatório |
| Separação de papéis de banco | A aplicação que atende requisição não é dona das tabelas (RNF-042) |
| Ambiente de homologação | Nunca recebe dado real de produção. Massa sintética |
| Exportação de dados | Registrada em auditoria com autor e finalidade |

> **O sufixo `_enc` não é estética.** Ele impede que alguém devolva a coluna num JSON sem notar — o mesmo papel que `_cents` cumpre contra somar reais com centavos.

### 3.3 Governança de dados pessoais

Inventário sozinho não atende à LGPD nem sobrevive a uma auditoria do provedor.

| Obrigação | Implementação | Quando |
|---|---|---|
| Encarregado pelo tratamento | Pessoa designada por ato formal, com nome e canal publicados no site e nos termos `JUR` | Antes da primeira venda |
| Registro das operações de tratamento | Documento vivo, derivado do inventário, revisado semestralmente | Antes da primeira venda |
| Base legal por dado | Mapeamento explícito: execução de contrato, obrigação legal, legítimo interesse ou consentimento. PEN-13 `JUR` | Antes da primeira venda |
| Contrato de tratamento com operadores | Provedor de pagamento, parceiro fiscal, e-mail transacional, observabilidade e nuvem. Cada um é operador e precisa de instrumento `JUR` | Junto com cada contrato |
| Relatório de impacto | Justificado pelo volume de dado financeiro e pelo uso de impressão de dispositivo `JUR` | Antes do piloto |
| Atendimento a pedido de titular | Fluxo com prazo, responsável e evidência, na tabela `lgpd_requests` | Antes da primeira venda |
| Comunicação de incidente | Procedimento da seção 5.4, com prazo regulatório contado da ciência `JUR` | Antes da primeira venda |
| Base legal do rastreamento de afiliado | A impressão de dispositivo em `visitor_key` serve a finalidade **comercial**, não apenas antifraude. Precisa de base legal própria e de aviso `JUR` | Antes do lançamento |

> **O rastreamento de afiliado é o ponto mais frágil desta seção.** A `visitor_key` combina cookie e impressão de dispositivo, e sobrevive 60 dias. Enquadrar dispositivo e IP como "prevenção a fraude" é verdade para o checkout — mas **a atribuição de comissão é finalidade comercial, não de segurança**.
>
> Duas finalidades diferentes sobre o mesmo dado exigem duas bases legais. E a página que grava o clique precisa de aviso de cookies com escolha real, o que hoje não está previsto em nenhum documento nem em nenhuma das 40 telas desenhadas. `JUR`

---

## 4. Conformidade regulatória

### 4.1 Enquadramento

A Paysi opera como facilitadora sobre instituição de pagamento autorizada. Isso a mantém fora da regulação direta do Banco Central desde que respeite três limites.

| Limite | Consequência de violar |
|---|---|
| Não custodiar recursos em nome próprio | Passaria a exercer atividade de instituição de pagamento sem autorização |
| Não operar conta única de passagem | Prática vedada; a instituição é obrigada a encerrar a conta |
| Não iniciar transação via Open Finance | Caracterizaria modalidade que exige autorização própria |

> **Consequência prática do primeiro limite.** Cada vendedor precisa ter conta de pagamento nominal em seu próprio CPF ou CNPJ dentro do provedor. Se em qualquer momento o desenho levar o dinheiro a passar por uma conta em nome da Paysi antes de chegar ao vendedor, **o enquadramento muda inteiramente**.
>
> Isto vale também para a mensalidade do plano Escala e para a taxa de verificação: são cobranças da Paysi contra o vendedor, liquidadas por débito de saldo ou cartão, e nunca justificam reter recurso de terceiro em conta própria.
>
> **E é exatamente aqui que mora a PEN-21.** Todo o modelo de buckets pressupõe que a Paysi consiga reter e debitar saldo **dentro da subconta nominal do vendedor**. Se o provedor não oferecer esse comando por API, `GUARANTEE`, `PENDING` e `RESERVE` não têm lastro — e o caminho alternativo está fechado por este mesmo limite.

### 4.2 Banking as a Service — Resolução Conjunta nº 16/2025

A norma, de 28 de novembro de 2025, disciplina a prestação de serviços de infraestrutura bancária e de pagamento por instituição autorizada a uma entidade tomadora não autorizada. O prazo de adequação do setor é 31 de dezembro de 2026.

Ao criar contas de pagamento nominais para os vendedores, a Paysi provavelmente se qualifica como entidade tomadora — é a PEN-14. Três consequências:

1. **Exclusividade por tipo de conta.** A norma restringe a tomadora a um único prestador por modalidade de conta. Isso não impede trocar de provedor; impede manter dois ativos em paralelo. O risco R-14 precisou ser reescrito por causa disso, e o limite foi registrado no ADR-09.
2. **Declaração negativa.** A tomadora precisa informar aos clientes que **não é instituição autorizada a funcionar pelo Banco Central**. Isso é distinto e adicional ao dever de identificar o provedor, e a conclusão anterior de que no checkout bastaria "rodapé discreto" pode não valer. É o RF-110 e a PEN-15.
3. **Titularidade da política.** A política e os controles de identificação de clientes, prevenção a fraude e PLD/FT são responsabilidade da instituição prestadora. A tomadora executa tarefas acessórias, sob supervisão e com ferramentas dela.

> **O terceiro ponto é o mais delicado**, porque a seção 4.5 e boa parte do plano descrevem a Paysi tomando decisões próprias de suspensão, monitoramento e encerramento. Isso continua sendo bom para o negócio — mas precisa estar redigido no contrato como **execução delegada**, não como política autônoma. `JUR`

**Superfícies de identificação exigidas**

| Superfície | Identificação do provedor | Declaração de que a Paysi não é autorizada |
|---|---|---|
| Cadastro e verificação | Sim — o usuário abre conta na instituição | Sim |
| Contratos e termos | Sim | Sim |
| Painel do vendedor | Sim | Sim |
| Comprovantes | Sim | Sim |
| Página de checkout vista pelo comprador | Sim | A confirmar — PEN-15 `JUR` |

Como a Paysi nasce depois da regra, já nasce adequada. O formato mínimo aceito precisa ser confirmado com o provedor e com o advogado. `JUR`

### 4.3 Nomenclatura — Resolução Conjunta nº 17/2025

Da mesma data, veda o uso de termo que sugira, literal ou por semelhança morfológica ou fonética, atividade ou modalidade para a qual a instituição não tenha autorização específica. Alcança nome empresarial, nome fantasia, marca, domínio de internet e a apresentação ao público.

A marca escolhida não contém termo restrito. Dois pontos:

- A norma alcança também a **relação contratual**: é vedado à instituição autorizada contratar parceira cuja nomenclatura a caracterize como instituição autorizada. Ou seja, o provedor tem obrigação própria de recusar a Paysi se a comunicação sugerir o contrário. Isso deixa de ser só risco nosso e passa a ser **critério de aprovação do parceiro comercial**.
- A comunicação precisa deixar explícita a real natureza da atuação — o que se sobrepõe e reforça a declaração negativa.

> **Consequência operacional:** nenhum material pode dizer "sua conta na Paysi", "seu saldo conosco" ou "a Paysi guarda o seu dinheiro". O saldo é do vendedor, na instituição, e a Paysi apenas o exibe e o movimenta a pedido. `JUR`

### 4.4 Autorização de prestadores — Resolução BCB nº 494/2025

Exige que prestadores de serviços de pagamento que atuavam sem autorização passem a solicitá-la, com janela específica de protocolo para emissores de moeda eletrônica, emissores de instrumentos pós-pagos e credenciadores.

Não deve alcançar a Paysi no enquadramento como facilitadora sobre instituição autorizada. Está registrada aqui porque é exatamente o tipo de norma que a consulta da PEN-10 precisa cobrir — e porque, **se o parecer concluir que o modelo se aproxima de subcredenciamento, ela passa a ser diretamente relevante.** `JUR`

### 4.5 Direito do consumidor

| Obrigação | Implementação |
|---|---|
| Arrependimento em 7 dias | Prazo mínimo de garantia por produto, não configurável abaixo disso |
| Informação clara antes da contratação | Valor cobrado hoje, valor futuro e data da renovação exibidos antes do formulário |
| Responsabilidade solidária | A Paysi aparece na fatura. Reclamação do comprador chega à plataforma, não só ao vendedor |
| Vedação a cláusula abusiva | Retenção e suspensão precisam de motivo concreto, notificação prévia e canal de contestação |

> **Risco jurídico concreto — retenção de saldo.** Existem decisões judiciais entendendo que bloqueio automático de saldo sem justificativa concreta configura falha na prestação do serviço, especialmente quando o risco é presumido e não comprovado. Concorrentes acumulam reclamações públicas invocando o Art. 51 do CDC e enriquecimento sem causa.
>
> **Cláusula contratual não basta. O que protege é o processo:** memória de cálculo visível, notificação com motivo específico antes do bloqueio e canal de contestação com prazo definido.
>
> Isso está reforçado em dois lugares que não são tela: as **faixas objetivas** substituíram a decisão discricionária disfarçada de critério, e `risk_events.reason` é `NOT NULL` em todo alerta, rebaixamento e suspensão — **a memória de cálculo passa a ser condição de gravação**, não item de interface. `JUR`

### 4.6 Prevenção à lavagem de dinheiro

A obrigação regulatória formal recai sobre o provedor, e a Resolução Conjunta 16/2025 reforça que a política é dele. A Paysi, como quem traz o cliente, precisa sustentar o processo — e é isso que o provedor auditará antes de assinar contrato.

| Controle | Implementação |
|---|---|
| Conheça seu cliente | Verificação obrigatória antes de habilitar recebimento |
| Triagem de pessoa exposta e de listas restritivas | Consulta na aprovação e reprocessamento periódico. Confirmar com o provedor se é fornecida por ele `JUR` |
| Monitoramento contínuo | Alertas de padrão atípico: comprador repetido, ticket destoante, saque imediato integral, volume anômalo de emissão fiscal |
| Política de encerramento | Documento escrito com hipóteses de suspensão e encerramento |
| Retenção de registro | Trilha completa da transação preservada pelo prazo legal |
| Pessoa responsável | Responsável interno designado pela conformidade |

---

## 5. Continuidade e resposta a incidente

### 5.1 Classificação

| Nível | Definição | Exemplos | Resposta |
|---|---|---|---|
| Crítico | Dinheiro incorreto ou dado vazado | Transação do razão não soma zero; bucket negativo fora de `DEBT`; acesso a dado de outra conta; saldo divergente; qualquer das oito verificações não vazia | Imediata, 24 horas |
| Alto | Serviço essencial indisponível | Checkout fora do ar; cobrança de assinatura parada | 1 hora, horário comercial estendido; 4 horas fora dele |
| Médio | Função degradada | Notificação atrasada; emissão fiscal em fila; relatório indisponível | 1 dia útil |
| Baixo | Defeito sem impacto financeiro | Erro visual, texto incorreto | Próxima entrega |

### 5.2 Procedimento para divergência financeira

1. Interromper liberações automáticas de saldo e saques
2. Isolar a transação divergente pelo identificador
3. Reconstruir o esperado a partir do pedido e da resposta do provedor
4. **Registrar o incidente antes de corrigir**
5. Corrigir por lançamento inverso, nunca alterando o registro original, e sempre por `ledger_adjustments` com aprovação registrada
6. Reprocessar as oito verificações de integridade
7. Reconstruir `ledger_checkpoints` das contas afetadas a partir do razão
8. Reconstruir `ledger_release_schedule` das contas afetadas, e conferir a verificação nº 8
9. Comunicar os usuários afetados se houve impacto em saldo
10. Registrar a causa raiz e o teste que passará a detectá-la

> **Por que a ordem importa.** Corrigir antes de registrar destrói a evidência de como o erro aconteceu. Num sistema financeiro, saber que houve erro é obrigação; saber por quê é o que impede a repetição.
>
> Os passos 7 e 8 existem por causa das estruturas derivadas. **Toda estrutura derivada precisa ser reconstruída depois de uma correção** — senão o conserto do razão convive com um resumo errado ou um agendamento órfão, e o sintoma volta sem causa aparente.
>
> E há uma armadilha específica no passo 7: reconstruir o resumo **conserta o número e apaga a pista**. Se a causa raiz não estiver registrada antes (passo 4), o mesmo defeito volta na semana seguinte parecendo novo.

### 5.3 Continuidade

| Item | Definição |
|---|---|
| Perda máxima aceitável | 5 minutos, por arquivamento contínuo do banco |
| Tempo de recuperação | 1 hora em horário comercial estendido; 4 horas fora dele |
| Teste de restauração | Trimestral, com evidência registrada |
| Cópia geográfica | Região distinta dentro do território nacional — **verificar disponibilidade antes de escolher a nuvem** (ADR-10) |
| Indisponibilidade do provedor | Checkout exibe aviso e oferece retomada por link; nenhuma cobrança é perdida silenciosamente |
| Indisponibilidade do parceiro fiscal | Nota entra em fila e é emitida depois. Nunca bloqueia o pagamento (RF-113) |

### 5.4 Incidente com dado pessoal

Procedimento distinto do de divergência financeira, porque **o relógio regulatório começa a correr na ciência**, não na conclusão da investigação.

| Passo | Ação |
|---|---|
| 1 | Registrar data e hora da ciência. É o marco de todos os prazos |
| 2 | Conter: revogar credencial, encerrar sessão, isolar o componente |
| 3 | Determinar categorias de dado, número aproximado de titulares e risco a eles |
| 4 | Comunicar à ANPD dentro do prazo regulatório vigente, ainda que com informação parcial, complementando depois `JUR` |
| 5 | Comunicar os titulares afetados quando houver risco relevante |
| 6 | Comunicar o provedor — exigência contratual provável, além de dever de BaaS |
| 7 | Registrar causa raiz, medida corretiva e prazo |

> **Vinte e quatro horas é um bom prazo técnico e não diz nada sobre a obrigação de comunicar**, que tem prazo próprio, autoridade própria e formulário próprio. Descobrir isso durante o incidente é a pior hora possível. O procedimento precisa estar escrito, com o encarregado designado e o canal da ANPD identificado, **antes da primeira venda** — não depois do primeiro problema. `JUR`

---

## 6. Política de exposição e cascata

### 6.1 Quem absorve cada perda

| Evento | Vendedor | Afiliado | Plataforma |
|---|---|---|---|
| Reembolso dentro da garantia | Devolve o que recebeu | Devolve a comissão | Absorve a própria taxa e a do provedor |
| Reembolso parcial | Devolve a parte proporcional acumulada, como residual exato | Devolve a parte truncada | Absorve a própria taxa e a do provedor, proporcionalmente |
| Contestação perdida | Valor devolvido menos a parte do afiliado, mais a tarifa da adquirente | Devolve a comissão | Nada, enquanto houver de onde debitar |
| Contestação ganha na defesa | Nada | Nada | **Absorve a tarifa da adquirente**, que não volta (PEN-23) |
| Contestação sem saldo nem dívida recuperável | Dívida registrada | Dívida registrada | Perda, após baixa aprovada |
| Taxa de verificação em conta sem saldo | Dívida registrada, compensada na 1ª venda | — | Nada |

> **A linha de contestação ganha mudou na v2.1.** Ela dizia "absorve o custo da defesa, se houver". Com a tarifa da adquirente indo para conta própria, ficou explícito **quanto** é esse custo: os R$ 30,00 por disputa, que a adquirente retém mesmo quando a defesa vence. A prática de mercado varia, e por isso a PEN-23 precisa ser respondida antes de o número entrar em qualquer projeção.

### 6.2 A cascata, em ordem

**Contestação, para o vendedor:** `RESERVE → AVAILABLE → DEBT`
**Reembolso dentro da garantia:** `GUARANTEE`
**Reembolso depois da garantia:** `GUARANTEE → PENDING → AVAILABLE → DEBT` — sem tocar a reserva (RF-122)
**Para o afiliado:** a mesma cascata sem `RESERVE`, já que ele não constitui reserva
**Restituição de contestação ganha:** a **ordem inversa** — `DEBT → AVAILABLE → RESERVE`

> **A reserva fica de fora do reembolso deliberadamente.** Ela existe para cobrir contestação, que é risco imposto ao vendedor, não devolução que ele escolheu fazer. Usar a reserva para reembolso voluntário reduz a proteção contra o risco que ela foi constituída para cobrir.

> **A restituição segue a ordem inversa por um motivo prático.** Restituir na ordem direta deixaria o vendedor com reserva cheia e dívida em aberto — bloqueado para saque, por um dinheiro que já voltou.

O bucket `DEBT` não é perda: é crédito da plataforma contra o vendedor, compensado automaticamente na saída da garantia de vendas futuras (RF-104), com saque bloqueado enquanto durar (RF-103). `SYS_CHARGEBACK_LOSS` só é tocada quando a dívida é reconhecida como incobrável, por decisão registrada e aprovada (RF-116), com segregação imposta no banco.

> **Esta política depende inteiramente da PEN-04.** Tudo acima pressupõe que o provedor permita debitar a contestação do saldo do vendedor. Se a resposta for que a contestação sai sempre da conta da plataforma, sem opção de configuração, a exposição residual deixa de ser exceção e vira regra — e o preço, a reserva e possivelmente a escolha do provedor mudam.
>
> E **depende igualmente da PEN-21**: sem comando de bloqueio dentro da subconta, não há de onde debitar nada.

---

## 7. Lista de verificação antes do lançamento

### Bloqueantes

| # | Item |
|---|---|
| 1 | Parecer jurídico confirmando o enquadramento como facilitadora |
| 2 | Parecer jurídico sobre enquadramento como entidade tomadora de BaaS e suas consequências |
| 3 | Contrato assinado com o provedor, com **origem do débito de contestação** definida por escrito |
| 4 | Resposta escrita do provedor sobre **retenção e débito dentro da subconta** (PEN-21) |
| 5 | Termos de uso, contrato do vendedor e política de privacidade revisados por advogado |
| 6 | Política de conheça seu cliente e de encerramento formalizada |
| 7 | Lista de produtos proibidos publicada e aplicada no cadastro |
| 8 | Encarregado de dados designado, publicado e com canal ativo |
| 9 | Registro de operações de tratamento e base legal por dado concluídos |
| 10 | Contrato de tratamento assinado com provedor, parceiro fiscal e demais operadores |
| 11 | Procedimento de comunicação de incidente à ANPD escrito e testado em simulação |
| 12 | **As oito verificações de integridade** rodando diariamente, com alerta, e cada uma testada com defeito injetado |
| 13 | Conciliação diária contra o provedor, com ensaio já executado em homologação |
| 14 | Teste automatizado de acesso cruzado entre contas em todas as rotas |
| 15 | Varredura de arredondamento no CI, cobrindo faixa **a partir de R$ 5,00**, comissão e plano |
| 16 | Varredura de rateio por parcela e de reembolso parcial, verificando soma exata **e ausência de parte negativa** |
| 17 | Idempotência verificada em todos os endpoints que movimentam dinheiro, inclusive em concorrência |
| 18 | Bloqueio de concorrência testado com saques simultâneos |
| 19 | **Ordem canônica de bloqueio** testada com transações cruzadas em duas contas |
| 20 | **Gatilho de sinal de bucket** ativo, testado nos dois sentidos |
| 21 | **Titularidade da conta bancária** verificada no banco no pedido de saque |
| 22 | Cascata de saldo devedor testada de ponta a ponta, incluindo compensação e baixa |
| 23 | Regra `max(recebimento, garantia)` testada com D+2 e garantia de 30 dias |
| 24 | **Reprocessamento de evento do provedor** testado com entrega repetida e simultânea |
| 25 | **Agendamento de liberação** testado: processo rodado duas vezes move o dinheiro uma vez |
| 26 | **Consolidação do resumo sob concorrência** testada com escrita não confirmada |
| 27 | Segundo fator ativo para operadores internos, com credencial própria |
| 28 | Faixas de limite de vendedor configuradas e testadas, com rebaixamento automático |
| 29 | Limites de teste de cartão configurados |
| 30 | Auditoria imutável ativa para ação administrativa |
| 31 | **Segregação de função em ajuste e baixa** testada: quem pede não aprova |
| 32 | Backup restaurado com sucesso em ensaio, com região de destino definida |
| 33 | Identificação do provedor e declaração negativa presentes em todas as superfícies exigidas |
| 34 | Segredo de webhook por endpoint, com rotação testada |
| 35 | Política de segurança de conteúdo ativa no checkout, com inventário de scripts |
| 36 | Imagens de personalização servidas de domínio próprio |
| 37 | Emissão fiscal validada em ambiente de teste para ao menos um município real, **com emissor amarrado ao vendedor** |
| 38 | Registros verificados quanto a ausência de dado sensível |
| 39 | **Aplicação conectando com papel que não é dono das tabelas**, com `REVOKE` efetivo comprovado |

### Recomendados

| # | Item |
|---|---|
| 40 | 3DS habilitado e limiar operacional definido |
| 41 | Teste de intrusão externo |
| 42 | Seguro de responsabilidade civil e cibernética avaliado |

> **O que mudou na contagem.** A v1.1 tinha 18 bloqueantes; a v2.0, 29; a v2.1, 33. Agora são **39 bloqueantes e 3 recomendados**.
>
> Os itens novos não são zelo excessivo. Nove vieram de obrigações legais que não estavam registradas; os demais vieram de defeitos **reproduzidos em banco**, cada um com o teste que o detecta. Um item de lista que não corresponde a um teste executável é intenção, não controle — e por isso os itens 12, 16, 19, 20, 21, 24, 25, 26 e 31 já têm asserção correspondente em `paysi-testes-v3.0.sql`.
>
> O marco M5 do documento 4 acompanha: 39 itens.

---

*Documento interno restrito. Organiza riscos e controles técnicos; não constitui parecer jurídico, fiscal nem auditoria de segurança. Itens marcados exigem validação por profissional habilitado antes do início da operação. Referências normativas foram verificadas em agosto de 2026 e podem ter sido alteradas; confirmar vigência antes de decidir.*
