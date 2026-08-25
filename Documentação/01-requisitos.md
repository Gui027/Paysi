# Paysi — Documento 1: Especificação de Requisitos

**Versão 3.0 · 21 de agosto de 2026 · interno · Substitui a versão 2.0 e incorpora as revisões v2.1 e v3.0**

---

## Escopo deste documento

Define **o que** o sistema deve fazer. Arquitetura e modelo de dados estão no documento 2; ameaças, conformidade e a lista de lançamento no 3; cronograma, orçamento e riscos no 4; como começar no 5; o código dos pontos delicados no 6.

### Status das informações

**Verificado** — tabela pública de taxas do provedor, prazos de liquidação, Resolução Conjunta 16/2025 e 17/2025 (ambas de 28 de novembro de 2025), Resolução BCB 494/2025, PCI DSS 4.0, práticas contratuais de concorrentes. Toda aritmética deste documento foi conferida contra o motor de divisão em 21.945.110 cenários.

**Premissa a confirmar** — todo item marcado `PSP` depende do contrato com o provedor. Todo `JUR` exige advogado especializado em regulação do sistema financeiro. Todo `FIS` exige contador. Nenhum dos três é definitivo antes da confirmação.

---

## 1. Visão do produto

### 1.1 Definição

Plataforma de checkout com divisão automática de pagamento e programa de afiliados, destinada a empresas brasileiras que vendem por assinatura ou pagamento único — tanto software (SaaS) quanto produto digital.

### 1.2 Os dois segmentos

Não são duas plataformas. São dois perfis de configuração sobre o mesmo núcleo: um livro-razão, um motor de divisão, um checkout, um painel.

| Dimensão | Segmento `DIGITAL` | Segmento `SAAS` |
|---|---|---|
| Comprador típico | Pessoa física | Pessoa jurídica |
| Documento coletado | CPF | CNPJ com razão social e endereço; CPF permitido |
| Nota fiscal ao comprador | Não emitida | Emitida em nome do vendedor, obrigatória |
| Meios de pagamento | Pix, cartão | Pix, cartão, boleto |
| Teste gratuito | Cartão obrigatório na entrada | Cartão opcional |
| Afiliados | Uso central | Habilitável, uso secundário |
| Ticket típico | R$ 47 a R$ 997 | R$ 99 a R$ 2.000 |
| Plano comercial usual | Transacional | Escala |

> **Por que isto não dobra o sistema.** Nenhuma das diferenças acima toca o livro-razão, o motor de divisão, o modelo de identidade ou o fluxo de saque — que é onde mora todo o risco. Elas vivem em três lugares: campos do checkout, meios habilitados na oferta e um módulo fiscal isolado atrás de uma porta. O custo real é de 20 dias de desenvolvimento, não de uma segunda plataforma.
>
> O segmento é atributo do **produto**, nunca da conta (ADR-13). Uma mesma conta vende SaaS e produto digital.

### 1.3 Enquadramento

A Paysi opera como facilitadora de pagamentos sobre uma instituição de pagamento autorizada pelo Banco Central. Não custodia recursos em nome próprio, não emite moeda eletrônica e não credencia estabelecimentos junto às bandeiras. `JUR`

Ao criar contas de pagamento nominais para os vendedores dentro do provedor, a Paysi provavelmente se qualifica como **entidade tomadora de serviços de Banking as a Service** sob a Resolução Conjunta nº 16/2025. Três consequências:

1. **Exclusividade por tipo de conta.** A norma restringe a tomadora a um único prestador por modalidade de conta. Manter um segundo provedor ativo como redundância de disponibilidade não é viável — apenas a troca é.
2. **Declaração negativa obrigatória.** A tomadora precisa informar aos clientes que não é instituição autorizada a funcionar pelo Banco Central. Isso é distinto e adicional ao dever de identificar o provedor.
3. **Titularidade da política.** A política e os controles de identificação de clientes, prevenção a fraude e PLD/FT são da instituição prestadora. A Paysi executa tarefas acessórias sob supervisão e com ferramentas dela — e isso precisa estar assim redigido no contrato.

São as pendências PEN-14 e PEN-15. Não invalidam o modelo; mudam a redação do contrato e do material de comunicação. `JUR`

### 1.4 Modelo de receita

| Linha | Descrição | Fase |
|---|---|---|
| Spread transacional | Diferença entre a taxa cobrada do vendedor e o custo do provedor | v1 |
| Mensalidade do plano Escala | Assinatura da própria plataforma, cobrada do vendedor | v1 |
| Antecipação | Margem de 1,25% sobre o custo de antecipação do provedor | v1 |
| Taxa de verificação de conta | Custo do provedor mais margem, uma vez por conta | v1 |
| Emissão fiscal | Repasse do custo do parceiro fiscal mais margem, por nota | v1 |
| Capital próprio em antecipação | Margem integral, exige capital de giro | Fase 3 |

> **A mensalidade não é receita complementar; é a linha que sustenta o custo fixo.** Ver documento 4, §6.5. Receita transacional é volátil e depende do sucesso comercial dos clientes; mensalidade chega no dia 1. Doze clientes no plano Escala cobrem praticamente todo o custo recorrente, independentemente de qualquer volume transacionado.

### 1.5 Fora de escopo permanente

- Hospedagem ou entrega de conteúdo (área de membros)
- Emissão de moeda eletrônica ou custódia em nome próprio
- Credenciamento direto junto a bandeiras
- Iniciação de transação de pagamento via Open Finance (modalidade ITP, exigiria autorização própria)
- Apuração fiscal do vendedor. A Paysi automatiza a emissão da nota em nome dele; não é responsável pela apuração, pela retenção nem pela escrituração `FIS`

---

## 2. Atores

| Ator | Definição | Acesso |
|---|---|---|
| Vendedor | Cadastra produtos e recebe pelas vendas | Painel, modo "vendendo" |
| Afiliado | Divulga produtos de terceiros por comissão | Painel, modo "divulgando" |
| Comprador | Cliente final do vendedor, PF ou PJ | Checkout e e-mails; sem conta |
| Operador Paysi | Equipe interna: risco, suporte, conformidade | Painel administrativo |
| Provedor | Instituição de pagamento autorizada | Integração por API |
| Parceiro fiscal | Emissor técnico de NFS-e em nome do vendedor | Integração por API |

> **Regra fundamental de identidade.** Vendedor e afiliado não são contas diferentes: são modos de uma mesma conta. Uma verificação de identidade, um saldo, uma reserva, uma conta bancária, um saque. O que se separa é a visão.

---

## 3. Requisitos funcionais

Prioridade: **DEVE** (bloqueia o lançamento) · **DEVERIA** (importante, adiável) · **PODE** (desejável).

### 3.1 Conta e identidade

| ID | Requisito | Prior. |
|---|---|---|
| RF-001 | Criar conta com nome, e-mail e senha, escolhendo o modo inicial (vender ou divulgar) | DEVE |
| RF-002 | Autenticar por e-mail e senha, com sessão expirando em 12 horas de inatividade | DEVE |
| RF-003 | Recuperar senha por link de uso único válido por 1 hora, com resposta idêntica exista ou não a conta | DEVE |
| RF-004 | Alternar entre os modos vendendo e divulgando sem novo login | DEVE |
| RF-005 | Aceitar pessoa física e jurídica, com limites distintos (RF-079) | DEVE |
| RF-006 | Encaminhar ao fluxo de verificação de identidade do provedor e receber o resultado por webhook. Disparado apenas na primeira tentativa de publicar um produto, nunca no cadastro | DEVE |
| RF-007 | Bloquear a publicação de checkout enquanto a verificação não for aprovada | DEVE |
| RF-007a | Permitir cadastro, exploração e montagem de produto em rascunho sem disparar verificação nem criação de subconta | DEVE |
| RF-008 | Exibir o estado da verificação item a item, com o que falta e o prazo estimado | DEVERIA |
| RF-009 | Exigir segundo fator na alteração de conta bancária e em saques acima de limite configurável | DEVERIA |
| RF-091 | Cobrar a taxa de verificação (custo do provedor mais margem) uma única vez, no momento da aprovação — nunca no cadastro nem na tentativa recusada | DEVE |
| RF-110 | Declarar, no cadastro, nos termos, no painel e no rodapé do checkout, que a Paysi não é instituição autorizada a funcionar pelo Banco Central, identificando o provedor `JUR` | DEVE |
| RF-117 | Liberar e-mail e documento para novo cadastro após o encerramento da conta, preservando o registro encerrado **novo v3.0** | DEVERIA |

> **RF-117 corrige o defeito D02.** O documento era liberado após encerramento; o e-mail ficava bloqueado para sempre por um `UNIQUE` global. Quem encerra a conta e volta seis meses depois é cliente recuperado, não fraudador — e não há razão para as duas colunas terem regras diferentes.

> **A taxa de verificação nasce como saldo devedor.** RF-091 cobra na aprovação; nesse instante o vendedor nunca vendeu nada e o saldo disponível é zero. Debitar `AVAILABLE` deixaria o bucket negativo — incidente de severidade máxima na primeira conta criada do sistema. A taxa nasce em `DEBT`, é compensada sozinha na saída da garantia da primeira venda (RF-104) e bloqueia saque enquanto durar (RF-103), o que é inofensivo porque não há o que sacar. Lançamento no documento 2, §3.6.
>
> Consequência de produto: **a primeira dívida que a maioria dos vendedores vai ver não é contestação, é a taxa de verificação.** A tela de saldo devedor precisa dizer isso com todas as letras.

### 3.2 Produto e oferta

| ID | Requisito | Prior. |
|---|---|---|
| RF-010 | Cadastrar produto com nome, descrição e tipo de cobrança | DEVE |
| RF-011 | Definir preço em centavos, com valor mínimo de R$ 20,00 | DEVE |
| RF-012 | Definir ciclo da assinatura: mensal, trimestral, semestral ou anual | DEVE |
| RF-013 | Definir período de teste gratuito de 0 a 30 dias | DEVE |
| RF-014 | Definir prazo de reembolso, nunca inferior a 7 dias corridos | DEVE |
| RF-015 | Selecionar por produto quais meios de pagamento são aceitos, entre os habilitados para o segmento | DEVE |
| RF-016 | Definir número máximo de parcelas, limitado a 12 | DEVE |
| RF-017 | Habilitar ou desabilitar afiliação por produto | DEVE |
| RF-018 | Simular a divisão em tempo real durante o cadastro, por meio de pagamento, exibindo a taxa efetiva em percentual — **também sobre o preço com desconto de cupom** | DEVE |
| RF-019 | Manter produto em rascunho, sem checkout público | DEVERIA |
| RF-020 | Gerar link único e permanente de checkout por oferta | DEVE |
| RF-092 | Definir o segmento do produto (`SAAS` ou `DIGITAL`), que determina campos fiscais, meios disponíveis, exigência de nota e política de teste gratuito | DEVE |
| RF-099 | Permitir teste gratuito sem cartão em produto `SAAS`, exigindo meio de pagamento apenas na primeira cobrança | DEVE |
| RF-118 | Tornar imutáveis, após a criação da oferta, o vínculo com o produto, o tipo de cobrança e o segmento; e após a primeira venda paga, também o ciclo e o prazo de garantia. Preço continua editável **novo v3.0** | DEVE |

> **Por que o mínimo é R$ 20,00.** A parcela fixa de R$ 2,00 é indiferente em R$ 197 e devastadora em R$ 5,00: a taxa efetiva chegava a 46%. É material de reclamação pública e de questionamento pelo Art. 51 do CDC, além de indefensável comercialmente.
>
> Taxa efetiva no cartão à vista, plano Transacional, conferida no motor: **R$ 20,00 → 16,0% · R$ 50,00 → 10,0% · R$ 197,00 → 7,0%**. O RF-018 exibe esse número na tela de cadastro, para que nenhum vendedor descubra depois.

> **RF-118 é o que o objeto de preço faz no mercado.** No Stripe você não edita um `Price`: arquiva e cria outro. A razão é que a oferta é copiada para dentro do pedido e do razão, e o dado copiado precisa ser confiável para sempre. Imposto por gatilho, não por disciplina — documento 2, §3.3.

### 3.3 Checkout

| ID | Requisito | Prior. |
|---|---|---|
| RF-021 | Exibir produto, valor, ciclo e condição de renovação antes de qualquer campo do formulário | DEVE |
| RF-022 | Coletar nome, e-mail e CPF do comprador. Nenhum outro campo obrigatório sem justificativa operacional | DEVE |
| RF-023 | Processar Pix com QR Code e código copiável, com expiração e confirmação automática | DEVE |
| RF-024 | Processar cartão com tokenização exclusiva pelo SDK do provedor. Dados de cartão nunca transitam por servidor Paysi | DEVE |
| RF-025 | Exibir explicitamente valor cobrado hoje, valor da próxima cobrança e data | DEVE |
| RF-026 | Registrar aceite dos termos e da política de reembolso com data, hora e hash do conteúdo | DEVE |
| RF-027 | Aplicar cupom de desconto. Comissão e taxa incidem sobre o valor efetivamente pago | DEVERIA |
| RF-028 | Ocultar o campo de cupom atrás de um link | DEVERIA |
| RF-029 | Permitir configurar logo, cor e texto do botão, banner e imagem lateral, a partir de arquivos hospedados pela Paysi, nunca de URL externa | DEVERIA |
| RF-030 | Manter layout e estrutura fixos, sem CSS livre | DEVE |
| RF-031 | Exibir a identificação do provedor em todo ponto de contato `JUR` | DEVE |
| RF-032 | Em recusa de cartão, oferecer Pix como alternativa imediata e permitir nova tentativa em até 24 horas | DEVERIA |
| RF-033 | Oferecer versão embutível por script no site do vendedor | PODE |
| RF-093 | Em produto `SAAS`, ou quando o comprador declarar PJ, coletar CNPJ, razão social, inscrição municipal opcional e endereço completo | DEVE |
| RF-097 | Processar boleto com vencimento configurável de 1 a 15 dias, baixa automática por webhook e expiração registrada | DEVE |
| RF-100 | Submeter a cobrança de cartão à autenticação 3DS quando o provedor oferecer, registrando o resultado como evidência de defesa `PSP` | DEVE |

> **Dois pisos distintos, e é assim em toda plataforma.** O **piso comercial** é a regra de negócio sobre o preço anunciado: R$ 20,00 (RF-011). O **piso técnico de cobrança** é o mínimo que o adquirente aceita: R$ 5,00. O cupom pode descer até o piso técnico e não abaixo.
>
> A validação acontece ao **criar o cupom**, não no checkout: se o cupom levar a oferta mais barata vinculada abaixo de R$ 5,00, recusa com mensagem clara. Erro na cara de quem configurou, não de quem ia pagar. Cupom de 100% fica fora da v1 — exige fluxo de pedido de valor zero, sem cobrança e sem lançamento, e isso é pacote próprio.
>
> **Guarda que a varredura exigiu (v3.0).** Em R$ 5,00 com comissão de 50% no cartão em 12x, o vendedor fica com **16 centavos**. Fecha, é legal e é absurdo. RF-027 passa a exigir aviso de bloqueio na criação do cupom quando a combinação cupom + comissão deixar o vendedor abaixo de 20% do valor pago.

> **Por que 3DS entrou.** A autenticação do portador desloca a responsabilidade pela contestação de fraude para o emissor do cartão. É a alavanca mais barata sobre a exposição de AM-06 e do risco R-03 — mais barata que elevar a reserva de 4% para 8%, que era a única resposta anterior.
>
> Contrapartida honesta: 3DS adiciona atrito e derruba conversão. Por isso a aplicação é **condicional** — obrigatória acima de um valor configurável e em transação com sinal de risco, opcional abaixo disso. O limiar é parâmetro operacional, não constante no código.

### 3.4 Divisão do pagamento

| ID | Requisito | Prior. |
|---|---|---|
| RF-034 | Dividir cada cobrança entre vendedor, afiliado e plataforma no momento da criação da transação, usando o recurso nativo do provedor `PSP` | DEVE |
| RF-035 | Calcular a taxa da plataforma sobre o valor pago, com percentual variável por meio de pagamento e por plano comercial, mais R$ 2,00 fixo | DEVE |
| RF-036 | Calcular a comissão do afiliado sobre o valor pago, com teto de 50% | DEVE |
| RF-037 | Enviar ao provedor apenas as alocações de vendedor e afiliado, como valores fixos em centavos. A plataforma é a recebedora residual e absorve a diferença entre o custo estimado e o real | DEVE |
| RF-038 | Truncar valores de plataforma e afiliado; o vendedor recebe o resto exato | DEVE |
| RF-039 | Bloquear a transação quando o custo do provedor exceder a taxa cobrada, com alerta operacional | DEVE |
| RF-040 | Repetir a divisão em cada ciclo de assinatura | DEVE |
| RF-041 | Em parcelamento, liberar cada parte proporcionalmente à entrada de cada parcela, seguindo o cronograma de recebíveis do provedor. O rateio entre parcelas usa o **método do maior resto**: as primeiras parcelas absorvem o resto da divisão inteira, um centavo cada. É calculado **uma única vez**, na criação dos recebíveis, e gravado — nunca recalculado na liberação | DEVE |

> **Invariante do cálculo.** Para toda transação: `soma(alocações) + custo do provedor = valor pago`. Verificada em código antes de qualquer persistência, e agora também como `CHECK` na tabela `charges`. Violação interrompe o processamento e gera alerta.

> **RF-037 usa a mecânica do provedor a favor.** Vendedor e afiliado são calculados só a partir do valor pago, sem nenhuma dependência do custo do provedor:
>
> ```
> vendedor = valor_pago − taxa_cobrada − comissão_afiliado
> afiliado = comissão_afiliado
> ```
>
> Ambos determinísticos, enviados como centavos fixos. A plataforma, sendo a conta principal, fica com o resíduo — que é, por construção, `taxa_cobrada − custo_real`. Nenhuma estimativa entra na divisão; a tabela de custos serve só ao simulador do RF-018.
>
> Consequência aceita: a margem da plataforma flutua em centavos conforme o custo real. É o lugar certo para a variação ficar. **O vendedor sempre paga exatamente a taxa anunciada** — verificado em 21.945.110 cenários.

> **RF-039 nunca dispara com a tabela publicada, e isso é resultado, não suposição.** A menor margem da plataforma em toda a faixa é de **10 centavos** (R$ 5,00 no Pix, plano Escala). O requisito continua existindo para proteger tabelas futuras.

### 3.5 Afiliados

| ID | Requisito | Prior. |
|---|---|---|
| RF-042 | Listar em vitrine pública os produtos com afiliação habilitada | DEVE |
| RF-043 | Permitir que qualquer conta verificada peça afiliação, inclusive quem já vende | DEVE |
| RF-044 | Exibir antes do pedido: comissão proposta, janela de atribuição, prazo de garantia, prazo de liberação, devolução em reembolso e bloqueio de autocompra | DEVE |
| RF-045 | Exigir aprovação do vendedor, que define a comissão daquele afiliado | DEVE |
| RF-046 | Tornar a comissão imutável após a aprovação. Alterar exige encerrar a afiliação e abrir outra | DEVE |
| RF-047 | Definir por afiliação se a comissão vale só na primeira cobrança ou em todos os ciclos | DEVE |
| RF-048 | Atribuir a venda ao último link clicado, com janela de 60 dias | DEVE |
| RF-049 | Bloquear comissão quando CPF, CNPJ ou e-mail do comprador coincidir com o do afiliado | DEVE |
| RF-050 | Liberar a comissão apenas após o prazo de garantia do produto | DEVE |
| RF-051 | Estornar a comissão quando a venda for reembolsada ou contestada | DEVE |
| RF-052 | Exibir projeção de ganho apenas como estimativa baseada em dados reais do produto, nunca como promessa `JUR` | DEVE |
| RF-108 | Manter a comissão recorrente das assinaturas já atribuídas quando a afiliação for encerrada sem justa causa. O encerramento impede novas atribuições, não interrompe as existentes. Encerramento por fraude comprovada interrompe tudo | DEVE |

> **RF-108 fecha um conflito contratual previsível.** Comissão imutável (RF-046) somada à possibilidade de encerrar a afiliação, sem dizer o que acontece com as recorrentes já atribuídas, permitia ao vendedor encerrar no dia seguinte à venda e parar de pagar para sempre. O afiliado teria razão em reclamar.
>
> A janela caiu de 90 para 60 dias: noventa dias é longo para o mercado brasileiro, aumenta a exposição à sobreposição de cookie sobre tráfego pago do próprio vendedor (AM-11) e não tem contrapartida comercial que justifique.

### 3.6 Assinaturas

| ID | Requisito | Prior. |
|---|---|---|
| RF-053 | Cobrar automaticamente no vencimento usando o cartão tokenizado | DEVE |
| RF-054 | Ao falhar a cobrança, marcar a assinatura como inadimplente e reprocessar em D+1, D+3, D+7 e D+14, contados da primeira falha. São quatro retentativas após a tentativa original | DEVE |
| RF-055 | Notificar o comprador a cada tentativa, com link para atualizar o meio de pagamento | DEVE |
| RF-056 | Cancelar a assinatura quando a retentativa de D+14 falhar, sem carência adicional. É a quinta falha no total | DEVE |
| RF-057 | Gerar comissão normalmente em ciclo recuperado pela régua | DEVE |
| RF-058 | Cancelamento tem efeito ao fim do ciclo já pago | DEVE |
| RF-059 | Suspender cobranças e notificar assinantes quando o vendedor for suspenso | DEVE |
| RF-060 | Suportar Pix Automático quando disponível no provedor `PSP` | DEVERIA |
| RF-061 | Mudança de plano vale a partir do ciclo seguinte, sem cálculo proporcional | DEVE |
| RF-098 | Em assinatura por boleto, emitir o boleto do ciclo seguinte com antecedência configurável de 3 a 10 dias, com régua de lembretes em D−3, D0 e D+2, e sem retentativa automática | DEVE |
| RF-119 | A retentativa reaproveita a **mesma cobrança**, incrementando `attempt_count`; nunca cria uma segunda cobrança para o mesmo ciclo **novo v3.0** | DEVE |
| RF-120 | Teste gratuito sem cartão que chega ao fim sem meio de pagamento cadastrado entra em `PAST_DUE` e segue a régua **apenas por notificação**, sem cobrança, cancelando em D+14 **novo v3.0** | DEVE |

> **RF-054 e RF-056 se contradiziam.** "Tentar em D+1, D+3, D+7 e D+14" com "cancelar após a quarta falha" cancelava antes da tentativa de D+14 — a quarta falha acontece em D+7. A régua perdia a última retentativa, que é justamente a que mais recupera. A redação nova conta explicitamente: uma tentativa original mais quatro retentativas, cancelamento depois da quinta falha. `attempt_count` conta tentativas, não falhas, e começa em 1.

> **RF-119 fecha o buraco que o índice único abriu.** `(subscription_id, cycle_number)` é `UNIQUE` — é o que impede a retentativa do processo de 15 minutos de cobrar o mesmo ciclo duas vezes. Mas isso torna obrigatório que a régua **atualize** a cobrança existente. Ler "reprocessa cobranças falhas" como "cria nova cobrança" faz a régua inteira falhar na primeira retentativa, com erro de chave duplicada.

> **RF-120 cobre o estado que RF-099 criou e ninguém descreveu.** Teste sem cartão termina, não há o que cobrar, e nenhum documento dizia em que estado a assinatura fica. A saída não precisa de estado novo: `PAST_DUE` é exatamente "cobrança pendente", e a régua roda por notificação.

#### Ciclo de vida

```
PEDIDO       PENDING ──▶ PAID ──▶ FAILED | EXPIRED
                                  (boleto ou Pix vencido)

COBRANÇA     PENDING ──▶ PAID ──▶ PARTIALLY_REFUNDED ──▶ REFUNDED
                     └─▶ FAILED      └─▶ CHARGEBACK
                     └─▶ EXPIRED

ASSINATURA   TRIAL ──▶ ACTIVE ⇆ PAST_DUE ──▶ CANCELED
                                (D+1, D+3, D+7, D+14)   (5ª falha)
```

> **O estado de reembolso vive na cobrança, não no pedido (D04).** Uma assinatura tem N cobranças e um pedido. Com o acumulador no pedido, reembolsar o terceiro e o quinto ciclo de uma assinatura de R$ 100 acumulava R$ 200 contra um teto de R$ 100 e **o banco recusava um reembolso legítimo**. O estado consolidado do pedido passa a ser a visão `v_order_status`.

### 3.7 Saldo e saque

| ID | Requisito | Prior. |
|---|---|---|
| RF-062 | Manter cinco estados de saldo por conta: em garantia, pendente, reserva, disponível e devedor | DEVE |
| RF-063 | Registrar todo movimento em livro-razão de partidas dobradas, imutável | DEVE |
| RF-064 | Calcular saldo sempre por soma de lançamentos, nunca por campo mutável | DEVE |
| RF-065 | Somar venda própria e comissão de afiliado no mesmo saldo, com origem identificada por lançamento | DEVE |
| RF-066 | Reter reserva de segurança de 4% a 10% conforme o prazo escolhido, liberando em 90 dias | DEVE |
| RF-067 | Permitir escolher prazo de recebimento entre D+32, D+15, D+7 e D+2, com custo e reserva correspondentes | DEVERIA |
| RF-068 | Sacar por Pix para conta do mesmo CPF ou CNPJ, com mínimo de R$ 2,00. A titularidade é verificada **no banco**, no cadastro da conta bancária e de novo no pedido de saque | DEVE |
| RF-069 | Exibir extrato com memória de cálculo: cada lançamento indica origem, motivo e transação de referência | DEVE |
| RF-070 | Impedir que em garantia, pendente, reserva e disponível fiquem negativos. O estado devedor é o único que pode ser negativo, e nunca positivo | DEVE |
| RF-103 | Bloquear saque enquanto houver saldo devedor, exibindo o valor e a origem | DEVE |
| RF-104 | Compensar o saldo devedor automaticamente **na saída da garantia** de vendas futuras, antes de qualquer outra alocação daquele valor. Nunca no momento da venda | DEVE |
| RF-111 | Disponibilizar o saldo em `max(prazo de recebimento, prazo de garantia)`, exibindo a data efetiva no cadastro da oferta e no extrato | DEVE |
| RF-121 | Recusar no banco, no momento da escrita, qualquer lançamento que deixe bucket de usuário negativo ou o devedor positivo **novo v3.0** | DEVE |

> **RF-111 corrige o defeito mais caro da versão 1.1.** Combinar recebimento em D+2 com garantia de até 30 dias permitia sacar no dia 2 dinheiro que o comprador pediria de volta no dia 6, sem de onde debitar. Duas mudanças resolvem, e as duas são necessárias: a venda credita `GUARANTEE`, não `PENDING`; e a disponibilidade é `max(recebimento, garantia)`.
>
> Isso torna D+2 menos atraente do que a versão anterior sugeria. É o preço de a promessa ser verdadeira.

> **RF-104 contradizia o documento 2, e o documento 2 estava certo.** "Antes de qualquer outra alocação" fazia quem lesse o documento 1 implementar a compensação na venda. Compensar na venda usa dinheiro que ainda pode ser reembolsado pelo comprador; se o reembolso viesse depois, seria preciso desfazer a quitação — e o razão é imutável. Compensando na saída da garantia, só entra na quitação dinheiro que já passou do prazo de arrependimento.

> **RF-121 é a diferença entre detectar e impedir.** RNF-032 mandava verificar por consulta diária. Consulta diária descobre amanhã que o saldo ficou negativo ontem — e o saque já saiu. O gatilho de restrição deferido recusa no `COMMIT`, e por ser deferido não se importa com a ordem dos lançamentos dentro da transação. As verificações diárias continuam existindo como rede, para o caso de alguém escrever no banco por fora da aplicação.

### 3.8 Reembolso, contestação e risco

| ID | Requisito | Prior. |
|---|---|---|
| RF-071 | Reembolsar automaticamente dentro do prazo de garantia, sem depender do vendedor | DEVE |
| RF-072 | No reembolso, debitar do vendedor e do afiliado apenas o que receberam. A plataforma absorve a própria taxa e a do provedor | DEVE |
| RF-073 | Na contestação, debitar do vendedor, com a reserva como primeira fonte | DEVE |
| RF-074 | Registrar em toda venda: IP, horário, impressão do dispositivo, aceite dos termos, entrega e abertura do e-mail, acessos ao produto, contatos com suporte e resultado do 3DS | DEVE |
| RF-075 | Montar automaticamente o pacote de defesa da contestação a partir dessas evidências | DEVERIA |
| RF-076 | Alertar em 0,5% de contestação por vendedor, suspender vendas em 1,0% e bloquear saldo em 1,5% | DEVE |
| RF-077 | Alertar em 8% de reembolso, revisar em 12% e suspender em 20% | DEVE |
| RF-078 | Antes de qualquer bloqueio, notificar com motivo específico e memória de cálculo, e abrir prazo de contestação `JUR` | DEVE |
| RF-079 | Limitar vendedor novo a R$ 20.000 em 30 dias (PJ) ou R$ 5.000 (PF), liberando por faixas conforme RF-107 | DEVE |
| RF-080 | Manter e aplicar lista de produtos proibidos, com suspensão imediata | DEVE |
| RF-081 | Bloquear teste de cartão: limite de tentativas por IP, por CPF e por impressão de dispositivo | DEVE |
| RF-105 | Permitir reembolso parcial, debitando vendedor e afiliado proporcionalmente. Cada reembolso é registrado individualmente; a **cobrança** mantém o total devolvido. Assume `REFUNDED` só quando o acumulado igualar o valor pago; antes disso, `PARTIALLY_REFUNDED`. O evento `payment.refunded` é emitido apenas no total; o parcial emite `payment.partially_refunded`, que **não** instrui bloqueio de acesso | DEVERIA |
| RF-106 | Monitorar o índice de contestação agregado da plataforma, com alerta em 0,4% e congelamento de aprovação de contas novas em 0,7% | DEVE |
| RF-107 | Liberar limite de volume por faixas objetivas de histórico, sem decisão discricionária | DEVE |
| RF-122 | Reembolso solicitado **após** o prazo de garantia segue a cascata `GUARANTEE → PENDING → AVAILABLE → DEBT`, sem tocar a reserva **novo v3.0** | DEVE |

> **A repartição do reembolso é por truncagem cumulativa, e a regra óbvia não funciona.** A primeira formulação truncava as partes fatia a fatia e deixava a plataforma absorver o resíduo por ser recebedora residual. Parece certa e passa em dezenas de milhares de cenários regulares.
>
> Ela quebra em fatias sucessivas próximas do total. Venda de R$ 100,00, dez reembolsos de R$ 9,99 — reproduzido no motor:
>
> | | Vendedor | Afiliado | Plataforma | Provedor |
> |---|---|---|---|---|
> | Alocação da venda | 8201 | 1000 | 451 | 348 |
> | Devolvido nas 10 fatias | 8190 | 990 | **470** | 340 |
> | Resta para a fatia final | 11 | 10 | **−19** | 8 |
>
> A fatia final exigiria **creditar** 19 centavos negativos à plataforma. Cada fatia isolada soma zero e passa em todas as verificações; só a última quebra.
>
> **A regra que sobrevive** trunca sobre o **total acumulado reembolsado**, não sobre a fatia, com o vendedor como residual — a mesma política do RF-038 na venda:
>
> ```
> afiliado_acum   = trunc(A × C ÷ pago)      C = total já reembolsado, incluindo esta fatia
> plataforma_acum = trunc(P × C ÷ pago)
> provedor_acum   = trunc(V × C ÷ pago)
> vendedor_acum   = C − afiliado_acum − plataforma_acum − provedor_acum
> parte desta fatia = acumulado atual − acumulado anterior
> ```
>
> A deriva não acumula, fecha por construção em `C = pago` e o vendedor nunca fica negativo. Verificada em 4.275.150 cenários.
>
> **Guardas:** reembolso parcial mínimo de R$ 1,00, para que a fatia tenha o que repartir entre quatro partes; e alerta operacional se a receita acumulada da plataforma naquela cobrança não cobrir a parte que lhe cabe, espelhando RF-039.

> **RF-122 cobre o caso que ninguém tinha escrito.** RF-071 fala do reembolso dentro da garantia, quando o dinheiro está em `GUARANTEE`. Reembolso voluntário depois disso encontra o dinheiro em `PENDING`, em `AVAILABLE` ou já sacado. A reserva fica de fora deliberadamente: ela existe para contestação, que é risco imposto, não para devolução que o vendedor escolheu fazer.

> **RF-106 protege o ativo que ninguém estava olhando.** Os limiares do RF-076 são por vendedor e estão corretos. Mas quem descredencia a Paysi não é a bandeira olhando um vendedor — é o provedor olhando o número consolidado. Um único vendedor de volume alto pode levar o agregado acima do aceitável antes de estourar o próprio limiar. Os limiares agregados são deliberadamente mais apertados, porque o dano é existencial e não recuperável.

### 3.9 Notificações ao sistema do vendedor

| ID | Requisito | Prior. |
|---|---|---|
| RF-082 | Emitir eventos de pagamento aprovado, recusado, expirado, estornado total e estornado parcial; assinatura criada, renovada, inadimplente e cancelada; contestação aberta; nota fiscal emitida | DEVE |
| RF-083 | Assinar cada envio com HMAC-SHA256 e cabeçalho de horário, rejeitável por replay | DEVE |
| RF-084 | Reenviar em 1min, 5min, 30min, 2h e 12h em caso de falha | DEVE |
| RF-085 | Garantir entrega ao menos uma vez, com identificador estável para deduplicação no destino | DEVE |
| RF-086 | Exibir histórico de envios com código de resposta e permitir reenvio manual | DEVERIA |
| RF-109 | Manter segredo de assinatura **por endpoint de destino**, gerado pela plataforma, exibido uma única vez e rotacionável pelo vendedor com janela de sobreposição de 24 horas | DEVE |
| RF-123 | Registrar todo evento recebido **do provedor** em tabela de entrada antes de processá-lo, na mesma transação do efeito, tratando reentrega como duplicata sem efeito colateral **novo v3.0** | DEVE |

> **O segredo de webhook era global.** Uma variável de ambiente `WEBHOOK_SIGNING_SECRET` única para toda a plataforma significa que um vazamento compromete a integridade de todos os destinos de uma vez, e que rotacionar exige quebrar a integração de todo mundo no mesmo instante — o que na prática significa nunca rotacionar. Custa uma coluna cifrada e meia hora de código na primeira semana; depois de cem integrações, custa uma migração coordenada com cem empresas.

> **RF-123 é a contrapartida que faltava.** O ADR-07 cuida da saída; o ADR-08 cuida da API. **Nada tratava a entrada.** O provedor também entrega ao menos uma vez, e a segunda entrega de `payment.confirmed` escrevia uma segunda transação `SALE`: cinco lançamentos, soma zero, passa em todas as verificações, **credita o vendedor duas vezes**. Só a conciliação pega, dias depois.
>
> A correção é em duas camadas: tabela de entrada com chave `(provider, provider_event_id)`, e chave natural `(type, reference_type, reference_id)` em `ledger_transactions` como rede de segurança. Testado: a segunda tentativa de gravar `SALE` para a mesma cobrança é recusada pelo banco.

### 3.10 Nota fiscal e obrigações fiscais

| ID | Requisito | Prior. |
|---|---|---|
| RF-090 | Emitir nota fiscal de serviço da taxa cobrada pela plataforma, contra o vendedor `FIS` | DEVE |
| RF-094 | Emitir NFS-e da venda em nome do vendedor, por meio de parceiro fiscal, quando o produto exigir. O vendedor é o prestador; a Paysi é apenas o meio técnico `FIS` | DEVE |
| RF-095 | Coletar do vendedor os dados de emissão (município, inscrição, item de serviço, alíquota, credencial) e validar a emissão em ambiente de teste antes de habilitar o produto | DEVE |
| RF-096 | Disponibilizar o link da nota ao comprador por e-mail e no comprovante, e ao vendedor no detalhe da venda, com reenvio manual | DEVE |
| RF-112 | Solicitar o cancelamento da nota quando a venda for reembolsada, dentro do prazo do município, registrando a falha para tratamento manual quando o prazo já tiver expirado | DEVE |
| RF-113 | Nunca bloquear a confirmação do pagamento por falha de emissão fiscal. A nota é assíncrona, com fila própria de retentativa e alerta ao vendedor | DEVE |
| RF-124 | Recusar, no banco, emissão de NFS-e cujo emissor não seja o vendedor do produto daquela cobrança **novo v3.0** | DEVE |

> **RF-113 é decisão de engenharia, não conveniência.** Emissão de NFS-e depende de prefeitura, e prefeitura cai. Se a emissão estiver no caminho crítico da confirmação, a disponibilidade do checkout passa a ser limitada pela disponibilidade do **município menos confiável entre todos os vendedores**.

> **RF-124 fecha o defeito D11.** `issuer_id` era um uuid livre apontando para qualquer conta — a nota podia sair em nome do afiliado. Não é distinção semântica: ela define quem responde por erro de alíquota, quem recolhe o ISS e quem é autuado (PEN-19).

### 3.11 Cobrança da própria plataforma

| ID | Requisito | Prior. |
|---|---|---|
| RF-101 | Definir o plano comercial por conta (`TRANSACIONAL` ou `ESCALA`), aplicando a tabela correspondente a toda cobrança criada a partir da mudança | DEVE |
| RF-102 | Cobrar a mensalidade do Escala debitando o saldo disponível no primeiro dia do ciclo, com cartão cadastrado como alternativa e rebaixamento automático após 10 dias de inadimplência | DEVE |
| RF-114 | Registrar a mudança de plano com autor, data e tabela vigente, sem efeito retroativo sobre cobranças já criadas | DEVE |
| RF-125 | Toda conta nasce com plano comercial definido; não existe conta sem plano **novo v3.0** | DEVE |

> **O plano vive em um lugar só, e a cobrança congela o que aplicou.** `accounts.plan` saiu; a fonte única do plano corrente é `platform_subscriptions`. A cobrança guarda a memória de cálculo congelada — plano, percentual, parcela fixa, taxa, comissão e parte do vendedor. É o que o Stripe faz gravando `application_fee_amount` na cobrança: ninguém recalcula taxa histórica a partir da tabela vigente hoje.
>
> **RF-125 existe porque eleger uma fonte única sem garantir que ela exista troca um problema por outro.** Sete de oito contas de teste ficaram sem plano, e conta sem plano é cobrança sem tabela de preço.

> **Sem saldo suficiente, não há lançamento nenhum.** A ordem do RF-102 é: debitar `AVAILABLE` se cobrir o valor inteiro; senão cobrar o cartão; senão marcar `PAST_DUE` e rebaixar em 10 dias. Débito parcial não existe — deixaria a mensalidade num estado meio pago que nenhuma tela sabe exibir.

### 3.12 Operação interna

| ID | Requisito | Prior. |
|---|---|---|
| RF-087 | Painel administrativo com busca de conta, venda e assinatura | DEVE |
| RF-088 | Suspender conta, bloquear saldo e encerrar relacionamento, com motivo obrigatório e registro de quem executou | DEVE |
| RF-089 | Conciliar diariamente o livro-razão contra o extrato do provedor, com relatório de divergências | DEVE |
| RF-115 | Registrar e responder pedido de titular sob a LGPD com prazo, autor e evidência `JUR` | DEVE |
| RF-116 | Baixar saldo devedor incobrável para `SYS_CHARGEBACK_LOSS`, com aprovação registrada e motivo obrigatório | DEVE |
| RF-126 | Toda correção manual do razão passa por registro de ajuste com pedido, aprovação e motivo, com segregação entre quem pede e quem aprova imposta no banco **novo v3.0** | DEVE |

> **Quem pede não aprova.** Abaixo de um limiar de valor, a aprovação automática dispensa a segunda assinatura — que é o arranjo possível enquanto a equipe for de uma pessoa. Acima dele, fica pendente até haver segunda assinatura. A regra é `CHECK` na tabela, não instrução na tela.

---

## 4. Requisitos não funcionais

### 4.1 Desempenho

| ID | Requisito | Meta |
|---|---|---|
| RNF-001 | Tempo até o checkout ficar interativo, em 4G brasileiro | p95 abaixo de 1,5 s |
| RNF-002 | Resposta da API na criação de cobrança | p95 abaixo de 800 ms |
| RNF-003 | Peso do bundle inicial do checkout, **incluindo o SDK do provedor** | Abaixo de 180 KB comprimido |
| RNF-004 | Consulta de saldo por soma de lançamentos | p95 abaixo de 200 ms com 5 milhões de lançamentos |
| RNF-005 | Régua de cobrança de assinaturas | 10.000 cobranças por hora |

> **RNF-003 mede a coisa certa agora.** O orçamento anterior media só o código da Paysi e ignorava o SDK do provedor, que é carregado na mesma página, não é opcional e não está sob nosso controle. Medir o pedaço otimizável e ignorar o que o usuário efetivamente baixa produz um relatório bonito e um checkout lento. Se o SDK sozinho passar de 90 KB, trocar React por Preact no checkout deixa de ser preferência e vira aritmética.

### 4.2 Disponibilidade e continuidade

| ID | Requisito | Meta |
|---|---|---|
| RNF-006 | Disponibilidade do checkout | 99,5% mensal |
| RNF-007 | Disponibilidade do painel | 99,0% mensal |
| RNF-008 | Perda máxima de dados aceitável (RPO) | 5 minutos |
| RNF-009 | Tempo máximo de recuperação (RTO), horário comercial estendido | 1 hora |
| RNF-009a | Tempo máximo de recuperação fora do horário comercial | 4 horas |
| RNF-010 | Restauração de backup testada | Trimestral, com evidência |

> **As metas anteriores eram incompatíveis com uma pessoa.** 99,9% mensal são 43 minutos de indisponibilidade por mês, com recuperação em 1 hora a qualquer hora do dia. Isso pressupõe plantão. Prometer 99,9% com equipe de uma pessoa é prometer o que não se pode entregar — e, uma vez escrito no contrato do cliente, vira inadimplemento contratual em vez de incidente. As metas sobem quando houver plantão de verdade, não antes.
>
> E se o provedor cair, a Paysi não processa. Com um único provedor — e a Resolução Conjunta 16/2025 restringe a tomadora a um prestador por tipo de conta — a disponibilidade real está limitada à dele. **Não prometa ao cliente número melhor que o do contrato do PSP.** `PSP`

### 4.3 Integridade financeira

| ID | Requisito |
|---|---|
| RNF-011 | Valores monetários como inteiro em centavos. Ponto flutuante proibido em todo o sistema |
| RNF-012 | Percentuais em pontos-base inteiros |
| RNF-013 | Lançamentos do razão não podem ser alterados nem removidos. Correção por lançamento inverso |
| RNF-014 | Toda transação contábil soma zero entre débitos e créditos |
| RNF-015 | Toda escrita que movimenta dinheiro exige chave de idempotência, com resultado memorizado por 24 horas |
| RNF-016 | Divergência de conciliação superior a R$ 0,01 gera alerta operacional no mesmo dia |
| RNF-032 | Nenhum estado de saldo além do devedor pode ficar negativo, **recusado na escrita** e verificado por consulta diária independente da aplicação |
| RNF-038 | Toda conta de sistema declara o próprio saldo normal, verificado diariamente contra o sinal efetivo **novo v3.0** |
| RNF-039 | Toda escrita no razão adquire o bloqueio consultivo da conta, **inclusive quando não valida saldo**; bloqueio de mais de uma conta é adquirido em ordem canônica da chave de bloqueio **novo v3.0** |
| RNF-040 | O rateio por parcela e a repartição de reembolso são calculados uma única vez e gravados, nunca recalculados na liberação **novo v3.0** |

### 4.4 Segurança

| ID | Requisito |
|---|---|
| RNF-017 | Escopo PCI DSS restrito a SAQ A. Nenhum dado de cartão trafega ou é armazenado em infraestrutura Paysi |
| RNF-018 | Toda comunicação em TLS 1.2 ou superior, com HSTS |
| RNF-019 | Senhas com Argon2id; segredos em cofre gerenciado, nunca em repositório |
| RNF-020 | Isolamento entre contas verificado em toda consulta. Nenhum recurso acessível por identificador direto sem checagem de posse |
| RNF-021 | Registro de auditoria imutável para toda ação administrativa, com autor, horário e motivo |
| RNF-022 | Segundo fator obrigatório para operadores internos, com credencial própria — não a de usuário |
| RNF-033 | Política de segurança de conteúdo restritiva no checkout, com inventário autorizado de scripts e verificação de integridade |
| RNF-034 | Imagens e logos configurados pelo vendedor hospedados em domínio próprio da Paysi, nunca carregados de URL informada pelo usuário |
| RNF-041 | Chave de API verificada por HMAC-SHA256 com pepper no cofre, buscada por prefixo, comparada em tempo constante. Argon2id fica onde deve: senha de usuário **novo v3.0** |
| RNF-042 | A aplicação que atende requisição **não é dona das tabelas**. O papel dono migra; o papel de aplicação opera **novo v3.0** |

> **RNF-041 não é preferência de algoritmo.** Argon2id é salgado por linha: não permite procurar a chave a partir do segredo apresentado, e verificar linha a linha custa 50 a 200 ms — contra os 800 ms p95 inteiros do RNF-002.

> **RNF-042 é o que faz o `REVOKE` valer alguma coisa.** Dono de tabela ignora `GRANT` e `REVOKE`. Com a aplicação conectando como dona, a proteção contra `UPDATE` no razão nunca existiu — sobrava só o gatilho. Dois papéis, dois trabalhos.

### 4.5 Dados pessoais e retenção

| ID | Requisito | Prazo |
|---|---|---|
| RNF-023 | Evidências de venda para defesa de contestação | 18 meses |
| RNF-024 | Registros fiscais e contábeis `FIS` | 5 anos |
| RNF-025 | Registros de acesso a aplicação `JUR` | 6 meses, mínimo do Marco Civil |
| RNF-026 | Dados de comprador sem vínculo ativo | Anonimização após o fim da retenção fiscal |
| RNF-027 | Atendimento a pedido de titular sob a LGPD `JUR` | 15 dias |
| RNF-035 | Comunicação de incidente à ANPD e aos titulares afetados `JUR` | Conforme prazo regulatório vigente, contado da ciência |
| RNF-036 | Encarregado pelo tratamento designado, com identidade e canal publicados `JUR` | Antes da primeira venda |
| RNF-037 | Registro das operações de tratamento mantido e revisado a cada seis meses `JUR` | Contínuo |

### 4.6 Observabilidade

| ID | Requisito |
|---|---|
| RNF-028 | Identificador de correlação propagado do checkout até a chamada ao provedor |
| RNF-029 | Métricas de negócio expostas: aprovação, recuperação de assinatura, contestação e reembolso por vendedor e agregado |
| RNF-030 | Alerta automático quando a taxa de aprovação cair mais de 5 pontos em 1 hora |
| RNF-031 | Registros nunca contêm dado de cartão, senha, token de sessão ou documento completo |
| RNF-043 | As oito verificações de integridade rodam diariamente com alerta; resultado não vazio em qualquer uma é incidente de severidade máxima **novo v3.0** |

---

## 5. Regras de negócio consolidadas

### 5.1 Planos e precificação

Preço fechado: o vendedor vê um único número que já inclui o custo do provedor. A margem da plataforma é o spread.

**Plano Transacional — R$ 0 por mês**

| Meio | Custo do provedor | Taxa cobrada | Margem em R$ 197 |
|---|---|---|---|
| Pix | R$ 1,99 | 3,99% + R$ 2,00 | R$ 7,87 |
| Boleto | R$ 1,99 | 3,99% + R$ 2,00 | R$ 7,87 |
| Cartão à vista | 2,99% + R$ 0,49 | 5,99% + R$ 2,00 | R$ 7,42 |
| Cartão 2–6x | 3,49% + R$ 0,49 | 6,49% + R$ 2,00 | R$ 7,42 |
| Cartão 7–12x | 3,99% + R$ 0,49 | 6,99% + R$ 2,00 | R$ 7,42 |

**Plano Escala — R$ 297 por mês**

| Meio | Custo do provedor | Taxa cobrada | Margem em R$ 197 |
|---|---|---|---|
| Pix | R$ 1,99 | 1,99% + R$ 2,00 | R$ 3,93 |
| Boleto | R$ 1,99 | 1,99% + R$ 2,00 | R$ 3,93 |
| Cartão à vista | 2,99% + R$ 0,49 | 3,99% + R$ 2,00 | R$ 3,48 |
| Cartão 2–6x | 3,49% + R$ 0,49 | 4,49% + R$ 2,00 | R$ 3,48 |
| Cartão 7–12x | 3,99% + R$ 0,49 | 4,99% + R$ 2,00 | R$ 3,48 |

Todas as margens acima foram recalculadas pelo motor de divisão, não digitadas.

> **O ponto de indiferença é publicável, e isso é o argumento de venda.** No cartão à vista, o vendedor paga R$ 13,80 por venda de R$ 197 no Transacional e R$ 9,86 no Escala. A diferença é de R$ 3,94 por transação, contra mensalidade de R$ 297.
>
> **Acima de 76 transações por mês, o Escala é mais barato** — em ticket de R$ 197, cerca de R$ 15 mil de volume mensal.
>
> Publicar essa conta na página de preço, com calculadora, resolve boa parte do risco R-01. O vendedor de SaaS que compara com um provedor direto a 3,5% enxerga 3,99% mais mensalidade — diferença defensável, ao contrário de 5,99%. E o criador de produto digital de volume baixo continua sem pagar mensalidade nenhuma.

> **Risco comercial que permanece aberto.** O Escala reduz o R-01, não elimina. O que justifica o preço continua sendo o que o cliente **não** obtém contratando o provedor direto: divisão com afiliados pronta, subconta sem contrato próprio, emissão fiscal automatizada e onboarding imediato. Validar com clientes reais antes de escalar.

### 5.2 Taxa de verificação de conta

| Regra | Definição |
|---|---|
| Gatilho de cobrança | Verificação aprovada — nunca na tentativa nem no cadastro |
| Momento da criação da subconta | Primeira tentativa de publicar um produto (RF-006) |
| Escopo | Por conta, não por papel — quem acumula vendedor e afiliado paga uma vez só |
| Se a verificação for recusada | Sem cobrança, conforme prática do provedor |
| Forma de cobrança | Lançamento em `DEBT`, compensado na saída da garantia da primeira venda |

### 5.3 Exemplo completo de divisão

Venda de R$ 100,00 no cartão à vista, plano Transacional, afiliado de 10%:

| Item | Valor |
|---|---|
| Valor pago pelo comprador | R$ 100,00 |
| Custo real do provedor (2,99% + R$ 0,49) | R$ 3,48 |
| Taxa cobrada do vendedor (5,99% + R$ 2,00) | R$ 7,99 |
| Afiliado (10% do valor pago) | R$ 10,00 |
| Vendedor (residual: 100,00 − 7,99 − 10,00) | R$ 82,01 |
| Plataforma (residual no provedor: 7,99 − 3,48) | R$ 4,51 |

Conferência: `82,01 + 10,00 + 4,51 + 3,48 = 100,00`. O vendedor sente exatamente R$ 7,99, que é a taxa anunciada.

Enviado ao provedor na instrução de divisão: **apenas vendedor R$ 82,01 e afiliado R$ 10,00**, em centavos fixos. O restante fica na conta principal, de onde o provedor deduz o próprio custo.

### 5.4 Prazos, garantia e reserva

| Prazo escolhido | Custo do provedor | Cobrado do usuário | Margem | Reserva |
|---|---|---|---|---|
| D+32 (padrão) | — | 0% | — | 4% |
| D+15 | ~0,71% | ~1,96% | 1,25% | 6% |
| D+7 | ~1,04% | ~2,29% | 1,25% | 8% |
| D+2 | ~1,25% | ~2,50% | 1,25% | 10% |

> **A regra que governa tudo nesta tabela:**
>
> ```
> disponibilidade = data do pagamento + max(prazo de recebimento, prazo de garantia)
> ```
>
> A tabela acima descreve o prazo de **antecipação junto ao provedor**, que é uma coisa. O prazo de **arrependimento do comprador**, garantido pelo Art. 49 do CDC e configurado por oferta, é outra. O saldo só fica disponível quando as duas condições estiverem satisfeitas.
>
> | Recebimento | Garantia | Disponível em |
> |---|---|---|
> | D+32 | 7 dias | D+32 |
> | D+7 | 7 dias | D+7 |
> | D+2 | 7 dias | D+7 |
> | D+2 | 30 dias | D+30 |
> | D+15 | 30 dias | D+30 |
>
> **Ninguém calcula esse `max`: ele emerge.** As duas datas são contadas do mesmo marco — o pagamento — e cada processo só move o que já venceu. O dinheiro chega em `AVAILABLE` na data mais tardia das duas por consequência da cadeia, não por cálculo. Nenhum código chama `max()`.
>
> A reserva de segurança é adicional e independente: incide sobre o valor que efetivamente entra em `PENDING` e sai em 90 dias contados da venda.

> **Premissa a validar.** A tabela assume antecipação proporcional aos dias antecipados. Se o provedor cobrar mês cheio, as faixas D+15 e D+7 perdem sentido e resta apenas D+2. Antecipação de recebível de boleto fica permanentemente desabilitada: custo a partir de 5,79% ao mês torna qualquer margem inviável. `PSP`

### 5.5 Liberação de limite por histórico

| Faixa | Requisito para entrar | Limite 30 dias (PJ) | Limite 30 dias (PF) |
|---|---|---|---|
| 0 — Inicial | Verificação aprovada | R$ 20.000 | R$ 5.000 |
| 1 | 30 dias corridos, R$ 10 mil transacionados, contestação < 0,5%, reembolso < 8% | R$ 60.000 | R$ 15.000 |
| 2 | 90 dias corridos, R$ 60 mil transacionados, mesmos índices | R$ 200.000 | R$ 50.000 |
| 3 | 180 dias corridos, R$ 250 mil transacionados, mesmos índices, nenhuma suspensão no período | Sem limite automático | R$ 150.000 |

Rebaixamento é imediato e automático quando qualquer índice ultrapassa o limiar da faixa, com notificação e memória de cálculo (RF-078).

> **As faixas substituíram um "liberando por histórico" sem critério**, que era decisão discricionária disfarçada de regra — exatamente o que o Art. 51 do CDC pune. E é o único controle que realmente contém o cenário AM-01: um vendedor só chega a R$ 500 mil em 30 dias depois de 180 dias de histórico limpo, e nessa altura já existe reserva acumulada de verdade.

### 5.6 Produtos proibidos

Suspensão imediata e retenção de saldo para apuração:

- Apostas, jogos de azar e loterias não autorizadas
- Conteúdo adulto
- Produtos com marca de terceiro sem autorização
- Pirâmides, marketing multinível e esquemas de recrutamento
- Promessa de renda ou ganho garantido
- Produtos de saúde com promessa de cura
- Produtos financeiros ou de investimento sem registro na CVM
- Criptoativos e serviços correlatos
- Armas, munições e correlatos

---

## 6. Pendências bloqueantes

| ID | Pergunta | Responsável |
|---|---|---|
| PEN-01 | O provedor aceita plataforma de pagamento para SaaS e produto digital com subcontas criadas por API? Qual código de atividade é atribuído? | Comercial / PSP |
| PEN-02 | A divisão se repete automaticamente em cada ciclo de assinatura? | PSP |
| PEN-03 | A divisão funciona em Pix, boleto e Pix Automático? | PSP |
| PEN-04 | Na contestação, de qual saldo o provedor debita? É configurável para sair do vendedor? | PSP |
| PEN-05 | A taxa do provedor é devolvida em caso de reembolso? | PSP |
| PEN-06 | A antecipação é cobrada proporcional aos dias ou é taxa cheia? | PSP |
| PEN-07 | Qual o período de avaliação inicial e quais os limites durante ele? | PSP |
| PEN-08 | Qual o custo de divisão, de criação e de manutenção de subconta? A taxa incide em subconta criada mas não validada? | PSP |
| PEN-09 | Qual o formato mínimo de identificação exigido pela Resolução Conjunta 16/2025? | PSP / Jurídico |
| PEN-10 | O enquadramento como facilitadora sobre IP autorizada está correto e dispensa autorização própria? | Jurídico |
| PEN-11 | As cláusulas de retenção de saldo e suspensão resistem ao Art. 51 do CDC? | Jurídico |
| PEN-12 | A projeção de ganho na vitrine tem risco de ser lida como oferta de investimento? | Jurídico |
| PEN-13 | Qual a base legal LGPD para cada dado coletado do comprador? | Jurídico |
| PEN-14 | A Paysi é entidade tomadora de BaaS sob a Resolução Conjunta 16/2025? Em caso positivo, a exclusividade impede um segundo provedor de conta de pagamento? | Jurídico |
| PEN-15 | Qual a redação exigida da declaração de que a Paysi não é instituição autorizada, e em quais superfícies? | Jurídico / PSP |
| PEN-16 | O provedor oferece 3DS? É configurável por transação, com repasse de responsabilidade confirmado em contrato? | PSP |
| PEN-17 | A atividade admite Simples Nacional, ou obriga Lucro Presumido? Qual CNAE e qual alíquota de ISS no município da sede? | Contador |
| PEN-18 | Como a transição para CBS e IBS afeta a carga sobre a receita de serviço no horizonte do plano? | Contador |
| PEN-19 | A Paysi pode emitir NFS-e em nome do vendedor como terceiro autorizado? Qual instrumento formaliza — procuração, credencial municipal ou certificado do próprio vendedor? | Contador / Jurídico |
| PEN-20 | Há obrigação acessória de informação de transações (DIMP, DECRED ou equivalente) que recaia sobre a Paysi? | Contador |
| PEN-21 | O provedor permite **bloquear e liberar saldo dentro da subconta nominal do vendedor** sob comando da Paysi, por API? Se não, como se implementa garantia, pendente e reserva sem operar conta de passagem? E como a Paysi debita a mensalidade e a taxa de verificação? | Comercial / PSP / Jurídico |
| PEN-22 | O provedor envia identificador de evento estável e reenvia o mesmo identificador em retentativa? Qual a janela de reenvio e a garantia de ordem? | PSP |
| PEN-23 | Na contestação ganha em defesa, o provedor devolve a tarifa da adquirente ou ela é retida de qualquer forma? | PSP |
| PEN-24 | O provedor suporta **reembolso parcial** e múltiplos reembolsos parciais sobre a mesma cobrança, com identificador próprio por reembolso? **novo v3.0** | PSP |
| PEN-25 | O provedor informa o **cronograma de recebíveis** por parcela, com data esperada e identificador estável por parcela? **novo v3.0** | PSP |

> **Não iniciar antes de resolver.**
>
> **PEN-10** define se o projeto é legal na forma desenhada. É a única pendência que justifica parar tudo.
>
> **PEN-21** pode invalidar a cadeia de buckets inteira. A divisão nativa manda o dinheiro para a subconta do vendedor no instante da transação; `GUARANTEE`, `PENDING` e `RESERVE` só são reais se o provedor oferecer bloqueio de saldo sob comando da Paysi. Se o vendedor tiver acesso direto à subconta, ele saca em D+2 o que o razão diz estar em garantia por 30 dias — e o razão vira ficção contábil, com o RF-111 corrigido no papel e furado na prática. O caminho alternativo está fechado: reter em conta da Paysi é conta de passagem, vedada.
>
> **PEN-04** pode inviabilizar o modelo econômico: se a contestação for sempre debitada da plataforma, a exposição residual deixa de ser exceção e vira regra.
>
> **PEN-24 e PEN-25** não param o projeto, mas param dois requisitos: sem reembolso parcial no provedor, RF-105 é só contabilidade interna; sem cronograma de parcelas, RF-041 vira aproximação.

---

## 7. Rastreabilidade das correções

Registro do que estava errado, para que a revisão seja auditável e ninguém reintroduza um defeito já corrigido.

### 7.1 Correções da versão 2.0 (sobre a 1.1)

| # | Defeito | Correção |
|---|---|---|
| D-01 | RF-062 previa "em garantia", mas nenhum fluxo do razão o creditava | Venda passa a creditar `GUARANTEE` |
| D-02 | D+2 com garantia de 30 dias permitia sacar dinheiro reembolsável | Disponibilidade em `max(recebimento, garantia)` |
| D-03 | RF-070 era impossível: a cascata exigia saldo negativo | Estado devedor, com compensação e baixa |
| D-04 | RF-037 exigia custo real e divisão na criação — mutuamente exclusivos | Só vendedor e afiliado na divisão; plataforma residual |
| D-05 | RF-054 e RF-056 se contradiziam sobre quando cancelar | 1 tentativa + 4 retentativas |
| D-06 | Ticket mínimo de R$ 5,00 gerava taxa efetiva de 46% | Mínimo de R$ 20,00 com taxa efetiva exibida |
| D-07 | RF-090 tratava emissão de nota como desejável | Reclassificado para obrigatório |
| D-08 | RNF-006 prometia 99,9% com equipe de uma pessoa | Metas rebaixadas para o sustentável |
| D-09 | RNF-003 media o bundle excluindo o SDK do provedor | Passa a medir o total transferido |
| D-10 | Segredo de webhook único para toda a plataforma | Segredo por endpoint, rotacionável |
| D-11 | RF-079 dizia "liberando por histórico" sem definir critério | Faixas objetivas e automáticas |
| D-12 | Encerrar afiliação interrompia comissão recorrente já atribuída | RF-108, com exceção para fraude |
| D-13 | Índice de contestação medido só por vendedor | Índice agregado com limiar próprio |
| D-14 | Restrição a antecipação de boleto sem boleto no escopo | Boleto passa a ser meio suportado |
| D-15 | 3DS ausente de todos os documentos | RF-100, com aplicação condicional |
| D-16 | Resolução Conjunta 16/2025 lida apenas como dever de identificação | Exclusividade, declaração negativa e titularidade |
| D-17 | Nenhum tratamento formal de LGPD além do inventário | Encarregado, registro, prazo de incidente |
| D-18 | Nenhum tratamento de tributos sobre a receita | Pendências criadas; equilíbrio refeito |

### 7.2 Correções da versão 2.1

| # | Defeito | Correção |
|---|---|---|
| A1 | Verificação de bucket negativo incluía contas de sistema | Saldo normal declarado; verificação nº 4 |
| A2 | Cupom derrubava `paid_cents` abaixo do `CHECK` | Piso comercial e piso técnico separados |
| A3 | Gatilho de desnormalização prometido e nunca escrito | Três gatilhos reais (RF-118) |
| A4 | `admin_users` sem tabela de segundo fator | `admin_mfa_credentials` |
| B1 | Sem idempotência no webhook **de entrada** | Padrão inbox + chave natural (RF-123) |
| B2 | `ledger_checkpoints` + `bigserial` perdiam lançamento | Consolidação sob o mesmo bloqueio; verificação nº 5 |
| B3 | Doc 5 gravava o razão na criação do pedido | Razão só no fato gerador confirmado |
| B4 | Bloqueio consultivo sem ordem canônica | Ordem total, num método único (RNF-039) |
| B5 | `redeemed_count` mutável sem controle de concorrência | `UPDATE` condicional + trilha de resgate |
| B6 | Rateio por parcela sem regra nem cobertura | Maior resto, gravado uma vez (RF-041) |
| B7 | Tarifa da adquirente creditada em `SYS_CLEARING` | Conta `SYS_ACQUIRER_FEE` |
| B9 | Ciclo de assinatura sem unicidade | Índice único `(subscription_id, cycle_number)` |
| B10 | `ADJUSTMENT` e `DEBT_WRITEOFF` sem nada por trás | `ledger_adjustments` com segregação (RF-126) |
| C1 | RF-104 e doc 2 discordavam sobre quando compensar | Compensação na saída da garantia |
| C2 | Rótulo do diagrama invertia a regra do `max` | O `max` emerge; nenhum código o calcula |
| C3 | Sem estado de reembolso parcial | Entidade `refunds` (RF-105) |
| C4 | Plano comercial em duas tabelas | Fonte única + memória congelada na cobrança |
| D1–D4 | Antecedência de boleto, hash de chave de API, titular de conta bancária, boleto em `DIGITAL` | Coluna, HMAC, gatilhos |
| E1 | Faltava a pendência de retenção dentro da subconta | PEN-21 |
| G1 | Nada marcava lançamento já liberado | `ledger_release_schedule` |

### 7.3 Correções da versão 3.0

| # | Defeito | Correção | Onde |
|---|---|---|---|
| D01 | `REVOKE` sobre papel inexistente; aplicação como dona das tabelas | `V000__roles.sql`; RNF-042 | doc 2 §3.1 |
| D02 | E-mail bloqueado para sempre após encerramento | RF-117 | RF-117 |
| D03 | Gatilho de imutabilidade lia estado que saiu do pedido | Passa a olhar cobrança confirmada | doc 2 §3.3 |
| D04 | Acumulador de reembolso no pedido quebrava assinatura multi-ciclo | Reembolso na cobrança; `v_order_status` | RF-105, §3.6 |
| D05 | Parcela aceitava parte maior que ela mesma | `CHECK` + verificação nº 6 | RF-041 |
| D06 | `release_at` aceito em débito | `CHECK` de direção | doc 2 §3.6 |
| D07 | Agendamento dependia de a aplicação lembrar | Gatilho popula; verificação nº 8 | doc 2 §3.6 |
| D08 | Não negatividade só no dia seguinte | Gatilho de restrição deferido | RF-121 |
| D09 | Saque para conta bancária de outro titular era aceito | Gatilho de titularidade | RF-068 |
| D10 | `disputes` duplicava `refunds` | Disputa é só contestação | RF-105 |
| D11 | NFS-e em nome de qualquer conta | Gatilho amarra ao vendedor | RF-124 |
| D12 | Conta sem plano comercial | Plano padrão nasce com a conta | RF-125 |
| D13 | Acumulador de reembolso sem amarração | Verificação nº 7 | RF-105 |
| D14 | Régua ambígua sobre criar ou atualizar cobrança | RF-119 | RF-119 |
| D15 | Fim de teste sem cartão sem estado definido | RF-120 | RF-120 |
| D16 | Reembolso fora da garantia sem cascata | RF-122 | RF-122 |

---

*Documento de trabalho. Taxas do provedor referem-se à tabela pública de cliente final e devem ser renegociadas em condição de parceiro. Referências regulatórias exigem validação jurídica especializada antes da operação; referências fiscais exigem validação de contador. Este documento não constitui parecer jurídico nem fiscal.*
