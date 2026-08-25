# Paysi — Documento 4: Plano de Projeto

**Versão 4.0 · 21 de agosto de 2026 · interno · Substitui a versão 3.0 e incorpora as revisões v2.1 e v3.0**

---

## 1. Sumário executivo

| Item | Definição |
|---|---|
| Objetivo | Colocar em operação uma plataforma de checkout com divisão de pagamento e afiliados, atendendo empresas brasileiras de SaaS e criadores de produto digital |
| Prazo estimado | **43 semanas** com uma pessoa na Rota A; 36 na Rota B; 27 com duas pessoas |
| Equipe mínima | 1 pessoa em tempo integral com domínio de Java e React |
| Investimento até o lançamento | R$ 28 mil a R$ 120 mil de necessidade de caixa, excluída remuneração |
| Caminho crítico | Comercial e jurídico até a semana 12; técnico a partir dali |
| Maior risco | Prazo real exaurir o caixa antes de a receita começar |
| Já concluído | Motor de divisão, rateio por parcela e repartição de reembolso, validados em 28,6 milhões de cenários; esquema de banco completo, aplicado e coberto por 70 asserções |

> **A decisão de sequenciamento que define o projeto.** As duas dependências capazes de inviabilizar o projeto — parecer jurídico e contrato com provedor — não dependem de código e levam de 2 a 12 semanas. **Todo o trabalho da fase 2 foi desenhado para não depender de nenhuma das duas.**
>
> Construir três meses e só então descobrir que nenhum provedor aceita o segmento significa perder três meses. As três frentes correm em paralelo desde a semana 1.

```
CC-04 · CNPJ                    1 a 3 semanas
CC-01 · Jurídico                2 a 5 semanas · inclui BaaS
CC-02 · Contrato com provedor   4 a 12 semanas
CC-03 · Origem do débito de contestação   definida junto ao contrato
CC-05 · Retenção dentro da subconta (PEN-21)   primeira conversa comercial
CC-06 · Parceiro fiscal         2 a 4 semanas
```

> **CC-05 é novo e sobe de prioridade.** A PEN-21 pergunta se o provedor permite reter e debitar saldo dentro da subconta nominal do vendedor. Se a resposta for não, a cadeia de buckets inteira perde lastro — com o esquema perfeito, testado e inútil. Precisa estar na **primeira** conversa comercial, junto da PEN-04, não na negociação de contrato.

---

## 2. Premissas e restrições

Registro formal do que este plano assume. Se qualquer premissa se mostrar falsa, o plano precisa ser **revisto**, não ajustado na margem.

### 2.1 Premissas

| ID | Premissa | Se for falsa |
|---|---|---|
| PRE-01 | Uma pessoa em tempo integral, com domínio prévio de Java, Spring e React | Prazo dobra em regime de meio período |
| PRE-02 | Ao menos um provedor aceita plataforma de pagamento para SaaS e produto digital, com subcontas por API | Projeto inviável na forma desenhada |
| PRE-03 | O enquadramento como facilitadora dispensa autorização própria | Projeto muda de natureza; exige capital e 12 a 24 meses |
| PRE-04 | A divisão nativa do provedor funciona em Pix, boleto e em cada ciclo de assinatura | Divisão manual é inviável — recairia em conta de passagem, prática vedada |
| PRE-05 | Contestação pode ser debitada do saldo do vendedor | Exposição da plataforma vira permanente; preço e reserva sobem |
| PRE-06 | A antecipação do provedor é cobrada proporcionalmente aos dias | Restam apenas D+32 e D+2; a tabela intermediária desaparece |
| PRE-07 | Clientes aceitam a estrutura de dois planos | Margem cai ou o modelo precisa de terceira faixa |
| PRE-08 | Ticket médio entre R$ 99 e R$ 500 | Abaixo de R$ 100 a taxa fixa de R$ 2,00 pesa demais; revisar precificação |
| PRE-09 | Capital disponível para **11 meses** de operação sem receita | Reduzir escopo para a Rota B ou buscar cliente pagante antes |
| PRE-10 | A taxa de subconta do provedor é cobrada apenas na validação, não na tentativa | Se cobrada em tentativa recusada, o gatilho no primeiro produto perde parte do efeito de filtro |
| PRE-11 | Existe parceiro fiscal que emite NFS-e em nome de terceiro por API, cobrindo os municípios dos primeiros clientes | O segmento SaaS lança sem emissão automatizada; o vendedor emite por fora |
| PRE-12 | O provedor oferece 3DS com repasse de responsabilidade | A resposta ao risco R-03 volta a ser elevar reserva, com custo para o vendedor honesto |
| PRE-13 | A atividade admite regime tributário com carga de até ~16% sobre a receita de serviço | Ponto de equilíbrio sobe; precificação precisa ser revista antes do lançamento |
| PRE-14 | **O provedor permite bloquear e liberar saldo dentro da subconta nominal, por API** *(novo)* | `GUARANTEE`, `PENDING` e `RESERVE` não têm lastro. O razão descreveria um estado que o dinheiro real não tem — e o caminho alternativo é conta de passagem, vedada |
| PRE-15 | **O provedor envia identificador de evento estável e informa o cronograma de recebíveis por parcela** *(novo)* | A deduplicação de entrada recai só na chave natural do razão; RF-041 vira aproximação |

> **PRE-09 subiu de 10 para 11 meses**, acompanhando as 43 semanas. Não é detalhe: é a premissa que dimensiona o caixa, e um cronograma otimista não atrasa apenas a entrega — **ele dimensiona errado o dinheiro**, e é assim que projeto tecnicamente viável morre.
>
> **PRE-14 é a premissa mais perigosa do plano**, porque é a única que, se falsa, invalida trabalho já feito em vez de apenas atrasá-lo.

### 2.2 Restrições

| ID | Restrição | Origem |
|---|---|---|
| RES-01 | Dados de cartão não podem transitar por infraestrutura própria | Manutenção do escopo PCI em SAQ A |
| RES-02 | Cada vendedor precisa de conta nominal no próprio documento | Vedação a conta de passagem |
| RES-03 | Prazo de reembolso nunca inferior a 7 dias | Código de Defesa do Consumidor |
| RES-04 | Identificação do provedor e declaração de não autorização obrigatórias em pontos de contato | Resolução Conjunta 16/2025 |
| RES-05 | Hospedagem em território nacional, com cópia em região distinta a verificar | Latência, dados pessoais e continuidade |
| RES-06 | Antecipação de recebível de boleto permanentemente desabilitada | Custo do provedor torna qualquer margem negativa |
| RES-07 | Marca e comunicação não podem sugerir instituição financeira nem custódia própria | Resolução Conjunta 17/2025 |
| RES-08 | Um único provedor de conta de pagamento por vez | Exclusividade da Resolução Conjunta 16/2025 |
| RES-09 | Emissão fiscal nunca no caminho crítico da confirmação de pagamento | Disponibilidade do checkout (RF-113) |
| RES-10 | **A aplicação não conecta ao banco como dona das tabelas** *(novo)* | Sem isso, o `REVOKE` no razão e na auditoria não protege nada (ADR-15) |

---

## 3. Estrutura analítica do projeto

### 3.1 Fase 1 — Fundação

| ID | Pacote de trabalho | Esforço | Depende de |
|---|---|---|---|
| 1.1 | Constituição da empresa e contabilidade | 1 a 3 sem. | — |
| 1.2 | Registro de marca e domínio | 2 dias | — |
| 1.3 | Consultoria jurídica: enquadramento, incluindo BaaS | 2 a 5 sem. | — |
| 1.4 | Consultoria jurídica: contratos e termos | 3 a 5 sem. | 1.3 |
| 1.5 | Política de conheça seu cliente e encerramento | 3 dias | — |
| 1.6 | Lista de produtos proibidos | 1 dia | — |
| 1.7 | Prospecção comercial de provedores | 4 a 12 sem. | 1.1, 1.5, 1.6 |
| 1.8 | Ambiente de desenvolvimento e integração contínua | 3 dias | — |
| 1.9 | Prospecção e contrato de parceiro fiscal | 2 a 4 sem. | 1.1 |
| 1.10 | Adequação LGPD documental: encarregado, registro, bases legais | 2 sem. | 1.4 |

### 3.2 Fase 2 — Núcleo financeiro · 32 dias restantes

| ID | Pacote de trabalho | Esforço | Depende de |
|---|---|---|---|
| 2.1 | Tipo monetário e percentual em pontos-base — **CONCLUÍDO** | 2 dias | — |
| 2.2 | Motor de divisão, estorno e contestação — **CONCLUÍDO** | 4 dias | 2.1 |
| 2.3 | Varredura de arredondamento no CI, com parcela e reembolso — **CONCLUÍDO** | 1 dia | 2.2 |
| 2.4 | Esquema e migrações — **entregue aplicado e testado**; resta integrar ao Flyway | 2 dias | 1.8 |
| 2.5 | Livro-razão em JDBC: imutabilidade, cadeia de buckets, agendamento, gatilhos de sinal | 8 dias | 2.1, 2.4 |
| 2.6 | Bloqueio de concorrência em saldo, com ordem canônica | 2 dias | 2.5 |
| 2.7 | As oito verificações diárias de integridade | 4 dias | 2.5 |
| 2.8 | Interface de provedor e implementação falsa | 3 dias | 2.2 |
| 2.9 | Máquina de estados de pedido, cobrança e assinatura | 3 dias | 2.4 |
| 2.10 | Idempotência com memorização e espelho durável | 2 dias | 2.4 |
| 2.11 | Saldo devedor: cascata, compensação e baixa | 3 dias | 2.5 |
| 2.12 | Resumo de saldo, consolidação sob bloqueio e reconstrução | 3 dias | 2.5 |
| 2.13 | Inbox de eventos do provedor | 2 dias | 2.8 |

> **O pacote 2.4 caiu de 4 para 2 dias.** `paysi-esquema-v3.0.sql` está escrito, aplicado num PostgreSQL 16.15 e exercitado por 70 asserções. O que resta é dividi-lo nos 30 arquivos de migração e ligar ao Flyway. É o que compensa os 2 dias que os gatilhos novos adicionaram a 2.5 e 2.7 — o total de desenvolvimento não muda.

### 3.3 Fase 3 — Produto · 116 dias

| ID | Pacote de trabalho | Esforço | Depende de |
|---|---|---|---|
| 3.1 | Conta, autenticação e recuperação de senha | 4 dias | 2.4 |
| 3.2 | Verificação de identidade via provedor | 3 dias | 1.7, 3.1 |
| 3.3 | Criação de subconta no provedor | 3 dias | 1.7, 3.2 |
| 3.4 | Produto, oferta, segmento e configuração de cobrança | 5 dias | 3.1 |
| 3.5 | Checkout com Pix | 6 dias | 3.3, 3.4 |
| 3.6 | Checkout com cartão e tokenização | 5 dias | 3.5 |
| 3.7 | Divisão nativa integrada ao provedor | 4 dias | 2.2, 3.5 |
| 3.8 | Assinatura com cobrança automática | 5 dias | 3.6, 2.9 |
| 3.9 | Régua de retentativa com notificação | 4 dias | 3.8 |
| 3.10 | Afiliação: vitrine, pedido, aprovação e atribuição | 6 dias | 3.4, 3.7 |
| 3.11 | Saldo, extrato e saque, com guarda de titularidade | 6 dias | 2.5, 2.6, 3.3 |
| 3.12 | Reembolso total e parcial, entidade `refunds`, liberação de garantia | 7 dias | 3.11 |
| 3.13 | Outbox, entrega e segredo por endpoint | 5 dias | 3.7 |
| 3.14 | Painel do vendedor | 8 dias | 3.4, 3.11 |
| 3.15 | Painel do afiliado | 3 dias | 3.10, 3.14 |
| 3.16 | Faixas de limite, rebaixamento e teste de cartão | 4 dias | 3.5 |
| 3.17 | Captura de evidências de venda | 2 dias | 3.5 |
| 3.18 | Painel administrativo com auditoria e ajustes aprovados | 5 dias | 3.14 |
| 3.19 | Conciliação diária contra o provedor | 4 dias | 2.5, 3.7 |
| 3.20 | Boleto: emissão, baixa, expiração e régua | 5 dias | 3.5 |
| 3.21 | Comprador PJ, cadastro fiscal e entidade `buyers` | 3 dias | 3.4 |
| 3.22 | Emissão e cancelamento de NFS-e via parceiro | 8 dias | 1.9, 3.21 |
| 3.23 | Planos comerciais e cobrança da mensalidade | 4 dias | 3.11 |
| 3.24 | 3DS no checkout, com limiar operacional | 3 dias | 3.6 |
| 3.25 | Recebíveis, rateio por parcela e liberação por parcela | 4 dias | 3.7, 2.5 |

### 3.4 Fase 4 — Endurecimento · 25 dias mais piloto

| ID | Pacote de trabalho | Esforço | Depende de |
|---|---|---|---|
| 4.1 | Teste de acesso cruzado entre contas | 3 dias | 3.14 |
| 4.2 | Ensaio completo de conciliação | 3 dias | 3.19 |
| 4.3 | Fluxo de contestação com defesa automática e reversão | 4 dias | 3.17 |
| 4.4 | Índices de risco, agregado da plataforma e suspensão automática | 3 dias | 3.16 |
| 4.5 | Observabilidade e alertas de negócio | 3 dias | 3.19 |
| 4.6 | Teste de carga no checkout | 2 dias | 3.6 |
| 4.7 | Ensaio de restauração de backup | 1 dia | 2.4 |
| 4.8 | Implementação operacional da LGPD: fluxo de titular, simulação de incidente | 3 dias | 1.10, 3.18 |
| 4.9 | Revisão da lista de lançamento, 39 itens bloqueantes | 3 dias | 4.1 a 4.8 |
| 4.10 | Piloto com 3 a 5 clientes reais | 2 semanas | 4.9, 1.4 |

---

## 4. Cronograma

### 4.1 A aritmética, explícita

| Bloco | Dias úteis |
|---|---|
| 1.8 — ambiente e integração contínua | 3 |
| Fase 2 restante | 32 |
| Fase 3 | 116 |
| Fase 4 sem o piloto | 25 |
| **Total de desenvolvimento** | **176** |

Cento e setenta e seis dias úteis são **35,2 semanas sem folga nenhuma** — sem bug, sem suporte, sem reunião comercial, sem feriado, sem doença. Com folga de 15%, que é conservadora para trabalho novo em domínio financeiro, são **40,5 semanas**. Somando as 2 semanas de piloto, **42,5 — planeje 43**.

> **Por que a versão 2.1 dizia 18 semanas.** Dois erros somados.
>
> O primeiro é aritmético: a fase 3 estava declarada como 73 dias; somando os dezenove pacotes listados, o total era 82. Nove dias evaporaram na digitação.
>
> O segundo é de método: mesmo com 82 dias, o total era de 122 dias úteis, ou 24,4 semanas — dentro de uma janela declarada de 18 semanas que ainda precisava conter o piloto. O plano prometia caber 24 semanas de trabalho em 16, o que nenhum sequenciamento resolve, porque com uma pessoa não existe paralelismo no lado técnico.
>
> **Arredonde para cima ao planejar caixa.** 42,5 semanas viram 43, e 43 semanas viram 11 meses de reserva. Meia semana de otimismo por revisão é como o cronograma volta a mentir.

### 4.2 Fases no tempo — Rota A, escopo completo

| Semanas | Frente técnica | Frente comercial e jurídica |
|---|---|---|
| 1 | Ambiente, CI, esqueleto | Contato com provedores (declarando os dois segmentos e a PEN-21 na primeira mensagem), consulta jurídica, CNPJ, INPI |
| 2–8 | Fase 2: razão, buckets, dívida, concorrência, oito verificações, inbox | Jurídico em andamento; sandbox solicitado |
| 9–13 | 3.1 a 3.4: conta, produto, oferta — **não dependem de contrato** | Negociação; parceiro fiscal |
| 14–21 | 3.5 a 3.9: checkout, divisão, assinatura, régua | Contrato assinado (M3) |
| 22–28 | 3.10 a 3.15: afiliação, saldo, saque, reembolso, painéis | Captação do piloto |
| 29–35 | 3.16 a 3.25: risco, admin, conciliação, boleto, fiscal, planos, 3DS, recebíveis | Termos e política finalizados |
| 36–41 | Fase 4: endurecimento, LGPD operacional, lista de verificação | Preparação do piloto |
| 42–43 | Piloto com 3 a 5 clientes | Acompanhamento diário |

### 4.3 As três rotas

| Rota | Escopo | Prazo | Quando faz sentido |
|---|---|---|---|
| A | Completo, uma pessoa | **43 semanas** | Caixa para 11 meses e nenhuma pressa de mercado |
| B | v1 reduzida, uma pessoa | **36 semanas** | Caixa apertado ou necessidade de validar preço antes |
| C | Completo, duas pessoas a partir da semana 8 | **27 semanas** | Existe capital ou sócio técnico |

**O que a Rota B adia para uma v1.1, totalizando 27 dias:**

| Pacote | Dias | Impacto de adiar |
|---|---|---|
| 3.22 Emissão de NFS-e | 8 | O vendedor SaaS emite a nota por fora. Atrito real, não bloqueio |
| 3.20 Boleto | 5 | Pix cobre a maior parte do B2B hoje |
| 3.23 Planos comerciais | 4 | Lança só com o Transacional; perde o argumento de preço para SaaS |
| 3.24 3DS | 3 | Exposição a contestação de fraude fica maior no piloto, que tem volume limitado |
| 3.25 Recebíveis por parcela | 4 | Lança sem parcelamento; só à vista e assinatura |
| 3.15 Painel do afiliado | 3 | Afiliado usa a visão de vendedor, mais crua |

Rota B: 176 − 27 = 149 dias → 29,8 semanas → 34,3 com folga → **36 semanas** com o piloto.

Rota C, com a aritmética à mostra: semanas 1 a 7 com uma pessoa consomem 35 dias; restam 141 dias-pessoa. Com folga de 15% (162) divididos por duas pessoas (81) e 10% de sobrecarga de coordenação (89 dias), são 17,8 semanas — **7 + 17,8 + 2 = 27 semanas**. A sobrecarga de coordenação é estimativa; duas pessoas não entregam o dobro.

> **A Rota B não abandona o segmento SaaS.** Adiar a emissão fiscal não é adiar o SaaS. Coleta de CNPJ, razão social e endereço, teste sem cartão e segmento no produto continuam na v1 — são baratos e, se ficarem para depois, exigem migração de dados e retrabalho de checkout.
>
> O que se adia é a **automação** da nota. Um SaaS pequeno que já emite as próprias notas continua emitindo. O que se perde é o argumento de venda, não a capacidade de atender.
>
> Essa é exatamente a fronteira certa para cortar: adia-se o que é módulo isolado atrás de porta e mantém-se tudo que toca modelo de dados e telas.
>
> **Nada do núcleo financeiro é adiável na Rota B.** As correções de razão, agendamento, inbox, gatilhos de sinal e verificações são todas fase 2 — é o que precisa estar certo antes de existir a primeira venda real, e não há versão reduzida disso.

### 4.4 Marcos

| Marco | Semana (Rota A) | Critério de conclusão |
|---|---|---|
| M1 | 3 | CNPJ ativo, três provedores contatados, consulta jurídica iniciada, sandbox solicitado, PEN-21 e PEN-04 perguntadas por escrito |
| M2 | 8 | Venda simulada de ponta a ponta com divisão, cadeia completa de buckets, reserva, reembolso total e parcial, contestação, cascata de dívida e as oito verificações — **sem provedor real** |
| M3 | 13 | Contrato assinado e primeira transação real em ambiente de testes |
| M4 | 26 | Fluxo completo: cadastro, checkout, assinatura, afiliado, saldo e saque |
| M5 | 41 | Lista de verificação aprovada nos 39 itens bloqueantes |
| M6 | 43 | Piloto concluído com pelo menos três clientes reais transacionando |

> **M3 continua governando, mas menos do que antes.** Os pacotes 3.2, 3.3, 3.6, 3.7 e 3.24 dependem do contrato. Com o cronograma real, existe mais trabalho independente de contrato do que a versão anterior supunha — a fase 2 e os pacotes 3.1, 3.4, 3.21 somam quase 45 dias que não tocam o provedor.
>
> Se o contrato atrasar além da semana 13, a reordenação recomendada é antecipar 3.14 (painel do vendedor), 3.21 (comprador PJ) e 3.23 (planos), que só dependem do banco de dados.

---

## 5. Recursos e papéis

| Papel | Alocação | Responsabilidade |
|---|---|---|
| Desenvolvedor integral | 100%, 43 semanas | Backend, frontend, infraestrutura, testes |
| Contador | Pontual + mensal | Abertura, enquadramento fiscal, regime tributário, obrigações |
| Advogado regulatório | Pontual | Enquadramento, BaaS, contratos, termos, política de privacidade |
| Encarregado de dados | Acumulável no início | Pedidos de titular, registro de tratamento, comunicação de incidente |
| Responsável comercial | ~20%, semanas 1 a 21 | Negociação com provedores e parceiro fiscal, captação do piloto |
| Responsável por conformidade | Acumulável no início | Política de KYC, monitoramento, decisão de suspensão |

> **Segregação obrigatória a partir da operação.** Quem aprova um saque manual não pode ser quem o executa. Enquanto a equipe for de uma pessoa, **todo saque precisa ser automático, sem intervenção humana possível**. O caminho manual não deve existir no código — não é disciplina, é ausência de código para violar.
>
> O mesmo raciocínio se aplica à baixa de saldo devedor incobrável e a qualquer ajuste manual do razão: é decisão que reduz ativo da plataforma. Com uma pessoa só, ela precisa de limite de valor automático, acima do qual fica pendente até haver segunda assinatura. **Isso agora é `CHECK` na tabela `ledger_adjustments`**, não instrução de processo.

---

## 6. Orçamento estimado

> **Natureza destes números.** São estimativas de mercado, não cotações. Variam por região, por prestador e por complexidade. Devem ser substituídas por orçamento real antes de qualquer compromisso financeiro. Remuneração da equipe não está incluída.

### 6.1 Custo único até o lançamento

| Item | Mínimo | Máximo | Momento |
|---|---|---|---|
| Abertura de empresa | R$ 1.000 | R$ 2.500 | Semana 1 |
| Parecer jurídico de enquadramento, incluindo BaaS | R$ 6.000 | R$ 30.000 | Semanas 1 a 5 |
| Contratos, termos e política de privacidade | R$ 3.000 | R$ 12.000 | Semanas 5 a 10 |
| Adequação LGPD documental | R$ 3.000 | R$ 10.000 | Semanas 8 a 12 |
| Registro de marca no INPI, duas classes | R$ 700 | R$ 3.000 | Semana 2 |
| Domínios | R$ 100 | R$ 400 | Semana 2 |
| Identidade visual e ajustes de design | R$ 0 | R$ 8.000 | Semanas 2 a 6 |
| Certificado digital e-CNPJ | R$ 250 | R$ 600 | Semana 4 |
| Implantação do parceiro fiscal | R$ 0 | R$ 2.000 | Semanas 13 a 17 |
| Teste de intrusão externo | R$ 15.000 | R$ 45.000 | Semana 40 |
| **Subtotal único** | **R$ 29.050** | **R$ 113.500** | |

O teste de intrusão é o item mais caro e o mais adiável: pode ficar para depois do piloto, quando houver volume. Sem ele, o subtotal cai para R$ 14.050 a R$ 68.500.

### 6.2 Custo recorrente mensal

| Item | Mínimo | Máximo | Observação |
|---|---|---|---|
| Servidores de aplicação | R$ 400 | R$ 900 | Duas instâncias pequenas |
| Banco de dados gerenciado | R$ 300 | R$ 1.100 | Com réplica a partir da fase 2 |
| Cache | R$ 100 | R$ 300 | Idempotência e limites |
| Fila | R$ 50 | R$ 250 | Notificações e tarefas |
| Hospedagem do frontend e CDN | R$ 0 | R$ 400 | Faixa gratuita cobre o início |
| Armazenamento e backup | R$ 50 | R$ 250 | Arquivamento contínuo |
| Observabilidade | R$ 0 | R$ 600 | Faixa gratuita cobre o início |
| E-mail transacional | R$ 50 | R$ 400 | Escala com o volume |
| Cofre de segredos | R$ 0 | R$ 200 | Credencial fiscal e chaves |
| Parceiro fiscal, base | R$ 100 | R$ 600 | Mais custo por nota emitida |
| Contabilidade | R$ 300 | R$ 900 | |
| **Subtotal mensal** | **R$ 1.350** | **R$ 5.900** | |

### 6.3 Custo variável por transação

| Item | Custo | Quando entra |
|---|---|---|
| Taxa do provedor | Ver documento 1, §5.1 | Desde a primeira venda; já embutida no preço |
| Emissão de NFS-e | R$ 0,10 a R$ 0,40 por nota | Segmento SaaS; repassado com margem |
| Autenticação 3DS | R$ 0,05 a R$ 0,25 por tentativa `PSP` | Acima do limiar configurado |
| Notificação por WhatsApp | ~R$ 0,55 por mensagem | Régua de retentativa; ~R$ 130 por mil assinantes ao mês |
| Antifraude dedicado | R$ 0,15 a R$ 0,50 | Fase 4, quando o do provedor não bastar |
| Emissão de nota da taxa da plataforma | ~R$ 0,49 | Sobre a taxa de serviço da plataforma |
| **Tarifa da adquirente em contestação** | ~R$ 30 por disputa | Absorvida pelo vendedor se perdida, **pela plataforma se ganha** (PEN-23) |

### 6.4 Necessidade de caixa

| Cenário | Cálculo | Total |
|---|---|---|
| Enxuto, Rota B, sem teste de intrusão | R$ 14.050 único + 9 × R$ 1.350 | R$ 26.200 |
| Realista, Rota A, sem teste de intrusão | R$ 30.000 único + 11 × R$ 2.900 | R$ 61.900 |
| Conservador, Rota A, com intrusão e design | R$ 75.000 único + 11 × R$ 4.500 | R$ 124.500 |

Todos os cenários excluem remuneração e assumem que a receita só começa a cobrir o custo recorrente depois do piloto.

### 6.5 Tributos sobre a receita

> **O ponto de equilíbrio da versão 2.1 ignorava imposto.** Ele dividia o custo recorrente pela margem bruta sobre o volume transacionado, sem nenhum tributo na conta. Numa operação em que a receita **é** a taxa de serviço, a carga incide inteira sobre essa taxa — que é justamente a margem.

A base de cálculo é a **receita de serviço da Paysi** — a taxa cobrada do vendedor, a mensalidade e a margem de antecipação. Não é o volume transacionado, que é dinheiro de terceiro passando pelo provedor.

| Regime | Composição aproximada sobre a receita de serviço |
|---|---|
| Lucro Presumido | IRPJ 4,8% + CSLL 2,88% + PIS 0,65% + COFINS 3,0% + ISS 2% a 5% = **13,3% a 16,3%** |
| Simples Nacional, se admitido | 6% a 15,5% na faixa inicial, conforme anexo e fator R |

> **Duas perguntas para o contador, antes de publicar qualquer preço.**
>
> **A atividade admite Simples Nacional?** Atividades do sistema financeiro são vedadas. Facilitação de pagamento por plataforma de tecnologia normalmente não é, mas depende do CNAE atribuído — e o CNAE também é objeto da PEN-01 com o provedor. A diferença entre 6% e 16,3% sobre a receita não é ajuste de margem: **é o dobro do ponto de equilíbrio.** É a PEN-17.
>
> **Como a transição para CBS e IBS afeta isso no horizonte do plano?** A reforma está em fase de transição e a carga sobre serviços tende a se comportar de outra forma no regime não cumulativo. Um plano de 11 meses até o lançamento e mais 12 de operação atravessa essa janela. É a PEN-18. `FIS`

#### Ponto de equilíbrio operacional, com a margem real

A versão 3.0 assumia "margem média de 3,6% sobre o volume no mix dos dois planos". As margens reais, sobre o volume, calculadas pelo motor:

| Meio | Transacional | Escala |
|---|---|---|
| Pix | 4,00% | 1,99% |
| Cartão à vista | 3,77% | 1,77% |

> **O plano Escala compensa acima de 76 transações por mês. Ou seja, o volume migra para o plano de margem menor por construção** — é o desenho funcionando, não um desvio. Um vendedor de volume alto que continue no Transacional está pagando caro e vai embora.

Com 80% do volume vindo de contas Escala e mix meio a meio entre Pix e cartão, a margem ponderada por volume fica perto de **2,3%**, não 3,6%. Líquida de 15% de tributo, **1,95%**.

| Objetivo | Com 3,6% (premissa antiga) | Com 2,3% (real) |
|---|---|---|
| Cobrir R$ 2.900 de custo recorrente | ~R$ 95.000 | **~R$ 149.000** |
| Cobrir custo e remuneração de R$ 12.000 | ~R$ 487.000 | **~R$ 764.000** |

> **A mensalidade muda a natureza deste risco, e por isso ela deixa de ser argumento comercial complementar e passa a ser a linha que sustenta o custo fixo.**
>
> Doze clientes no plano Escala são R$ 3.564 por mês. Líquidos de 15% de tributo, R$ 3.029 — que cobrem o custo recorrente da faixa realista **independentemente de qualquer volume transacionado**.
>
> Isso muda a pergunta da captação de "quanto volume preciso" para "quantos clientes preciso", que é mais fácil de responder, de prever e de vender. O spread transacional financia crescimento; a mensalidade financia sobrevivência.
>
> É o argumento decisivo para **manter o pacote 3.23 na v1 mesmo na Rota B**, ainda que custe 4 dias.

---

## 7. Registro de riscos

Probabilidade e impacto de 1 a 5. Exposição é o produto. Todo risco tem responsável, gatilho observável e resposta definida antes de acontecer.

| ID | Risco | P | I | E | Gatilho observável | Resposta |
|---|---|---|---|---|---|---|
| R-20 | **O provedor não permite retenção dentro da subconta, e a cadeia de buckets perde lastro** *(novo)* | 3 | 5 | **15** | Resposta à PEN-21 | Reavaliar o modelo de garantia: liberar só o que o provedor consegue reter; renegociar prazo de repasse; em último caso, revisar a promessa de reembolso automático |
| R-18 | Prazo real de 43 semanas exaure o caixa antes da receita | 4 | 5 | **20** | Fase 2 terminando depois da semana 9 | Migrar para a Rota B; buscar cliente pagante antecipado; reduzir custo recorrente à faixa mínima |
| R-02 | Divergência de conciliação não detectada | 3 | 5 | 15 | Qualquer das oito verificações com resultado não vazio | Congelar liberações e saques; corrigir por lançamento inverso aprovado; reconstruir resumo e agendamento; incidente crítico |
| R-01 | Preço inviável para o segmento | 3 | 4 | 12 | Dois dos três primeiros prospectos recusam citando preço | Publicar a calculadora do ponto de indiferença; ajustar mensalidade; reduzir margem |
| R-03 | Contestação sempre debitada da plataforma | 3 | 4 | 12 | Resposta do provedor à PEN-04 | Habilitar 3DS antes de escalar; elevar reserva; revisar preço; considerar outro provedor |
| R-04 | Fraude de vendedor superando a reserva | 3 | 4 | 12 | Vendedor novo com pico de volume e saque imediato integral | Faixas de limite automáticas; suspensão; retenção para apuração |
| R-05 | Atraso do contrato além da semana 13 | 4 | 3 | 12 | Nenhuma proposta formal até a semana 9 | Reordenar fase 3 para painéis, comprador PJ e planos; ampliar prospecção para cinco provedores |
| R-07 | Equipe de uma pessoa como ponto único | 3 | 4 | 12 | Ausência prolongada por qualquer motivo | Documentação e testes atualizados; credenciais em cofre com acesso de emergência |
| R-15 | Enquadramento como tomadora de BaaS impõe obrigações não previstas | 3 | 4 | 12 | Parecer da PEN-14 ou exigência do provedor | Redigir contrato como execução delegada; ajustar comunicação; abandonar a ideia de segundo provedor simultâneo |
| R-06 | Concorrência estabelecida | 4 | 3 | 12 | Perda de prospecto por funcionalidade ausente | Reforçar recuperação de assinatura e emissão fiscal; manter foco nos dois segmentos |
| R-17 | Carga tributária acima do previsto por vedação ao Simples | 3 | 4 | 12 | Resposta do contador à PEN-17 | Revisar precificação antes de publicar; reavaliar mensalidade; considerar sede em município de ISS menor |
| R-08 | Nenhum provedor aceita o segmento | 2 | 5 | 10 | Três recusas por política de risco | Reposicionar para nicho de menor risco; reavaliar viabilidade |
| R-09 | Enquadramento exigir autorização própria | 2 | 5 | 10 | Parecer jurídico contrário | Interromper desenvolvimento de custódia; reavaliar modelo de gateway puro |
| R-10 | Escopo PCI ampliado por decisão técnica | 2 | 5 | 10 | Proposta de armazenar cartão, criar campos próprios ou carregar imagem de URL externa | Recusar na revisão de código; usar cofre de terceiro se houver necessidade real |
| R-19 | Dois segmentos dobram a superfície de suporte com uma pessoa | 3 | 3 | 9 | Tempo de suporte passando de 20% da semana | Congelar entrada de novos clientes; automatizar a dúvida mais frequente; priorizar um segmento |
| R-11 | Questionamento judicial de retenção | 3 | 3 | 9 | Primeira reclamação formal sobre bloqueio | Memória de cálculo já visível e obrigatória na gravação; faixas objetivas já aplicadas; revisar redação com advogado |
| R-16 | Emissão fiscal falha de forma persistente em município relevante | 3 | 3 | 9 | Fila fiscal com falhas acima de 5% por mais de 24 h | Emissão manual pelo vendedor; escalar com o parceiro; avaliar segundo parceiro |
| R-14 | Indisponibilidade prolongada do provedor | 2 | 4 | 8 | Interrupção acima de 2 horas | Aviso no checkout com retomada por link. **Segundo provedor simultâneo não é resposta viável** — RES-08 |
| R-21 | **Provedor sem identificador de evento estável ou sem cronograma de parcelas** *(novo)* | 2 | 3 | 6 | Resposta à PEN-22 e PEN-25 | Deduplicar pela chave natural do razão; derivar cronograma da tabela de prazos e conciliar diariamente |
| R-12 | Antecipação cobrada como mês cheio | 3 | 2 | 6 | Resposta do provedor à PEN-06 | Publicar apenas D+32 e D+2; remover faixas intermediárias |
| R-13 | Ticket médio abaixo do previsto | 3 | 2 | 6 | Média do piloto abaixo de R$ 100 | Revisar a parcela fixa de R$ 2,00; considerar piso por transação |

### 7.1 Riscos que exigem decisão antes do código

> **R-09** — sem parecer favorável, o desenvolvimento de custódia e saldo não deve avançar. É a única situação que justifica parar o projeto inteiro.
>
> **R-20** — é novo e é o único que pode **invalidar trabalho já feito**. Todo o razão pressupõe retenção dentro da subconta. A pergunta custa um e-mail e precisa sair na semana 1.
>
> **R-03** — a resposta muda o modelo econômico. Precisa estar clara antes de publicar preço a qualquer cliente.
>
> **R-18** — é o de maior exposição do registro. Não bloqueia o código, mas exige uma decisão de rota **na semana 1, não na semana 20**. Escolher a Rota A sem ter caixa para 11 meses é escolher parar no meio.

---

## 8. Critérios de aceite

### 8.1 Definição de concluído — geral

- Código revisado por outra pessoa ou, na ausência dela, por revisão formal registrada
- Testes automatizados escritos e passando na integração contínua
- Migração de banco reversível e testada
- Nenhum dado sensível em registro de log
- Documentação atualizada quando a decisão diverge do que está nos documentos 1 a 3

### 8.2 Adicional para pacotes financeiros

| Critério | Verificação |
|---|---|
| Cobertura de ramos em 100% | Relatório de cobertura anexado ao pacote |
| Invariante de soma verificada | Varredura de ticket, comissão e plano passando |
| Truncamento coberto | Varredura de parcela e de reembolso parcial passando, com ausência de parte negativa |
| Não negatividade de bucket | Gatilho ativo e teste de recusa no `COMMIT` |
| Teste de concorrência | Execução simultânea sem saldo negativo nem duplicidade |
| Idempotência comprovada | Requisição repetida e simultânea devolvem o mesmo resultado |
| Nenhum ponto flutuante | Verificação estática rejeitando `double` e `float` em pacote financeiro |

### 8.3 Critérios por marco

| Marco | Aceite |
|---|---|
| M2 | Simulação completa de venda, cadeia de buckets, reserva, reembolso total e parcial, contestação com cascata de dívida e reversão, com provedor falso. **As oito verificações passando, e cada uma testada com defeito injetado.** Nenhuma alocação negativa na varredura completa |
| M3 | Transação real em ambiente de testes do provedor, com divisão de três partes confirmada no extrato deles e batendo com o livro-razão local |
| M4 | Um vendedor consegue: criar conta, verificar identidade, cadastrar produto nos dois segmentos, receber uma venda com afiliado, ver o saldo correto e sacar. Sem intervenção manual |
| M5 | Os 39 itens bloqueantes aprovados com evidência registrada |
| M6 | Três clientes reais transacionando por duas semanas, com conciliação diária sem divergência |

---

## 9. Governança

| Ritual | Frequência | Conteúdo |
|---|---|---|
| Revisão de andamento | Semanal | Pacotes concluídos, bloqueios, alteração de estimativa |
| Revisão de riscos | Quinzenal | Reavaliar probabilidade e impacto; verificar gatilhos |
| Revisão de pendências | Semanal até M3 | Situação das 25 pendências |
| Revisão de marco | Por marco | Aceite formal contra os critérios da seção 8 |
| Conciliação | Diária a partir de M3 | Divergência é tratada no mesmo dia |
| Revisão de rota | Semanas 8, 16 e 28 | Confrontar dias consumidos contra os 176 estimados e decidir sobre corte de escopo |

> **A revisão de rota é o controle mais importante desta seção.** Um cronograma de 43 semanas erra. A pergunta não é se vai errar, é **quando isso será percebido**.
>
> Três pontos de conferência obrigatórios, com uma métrica só: dias de desenvolvimento consumidos contra dias estimados dos pacotes concluídos. Se na semana 16 o consumo estiver 25% acima do previsto, a decisão de migrar para a Rota B se toma **ali** — com 27 semanas de caixa pela frente — e não na semana 34, com quatro.

---

## 10. Plano de lançamento

### 10.1 Piloto

| Item | Definição |
|---|---|
| Participantes | 3 a 5 empresas, com ao menos uma de cada segmento |
| Duração | 2 semanas de transação real |
| Limite de volume | R$ 20 mil por participante |
| Acompanhamento | Conciliação diária e contato direto com cada participante |
| Critério de sucesso | Zero divergência de conciliação, aprovação acima de 80%, nenhum incidente crítico |
| Critério de recuo | Qualquer divergência financeira não explicada interrompe o piloto |

### 10.2 Abertura gradual

| Etapa | Gatilho | Ação |
|---|---|---|
| Piloto fechado | M5 aprovado | Convite direto, sem cadastro aberto |
| Lista de espera | M6 aprovado | Cadastro aberto com aprovação manual de cada conta |
| Cadastro aberto | 50 contas ativas sem incidente | Aprovação automática mantendo a faixa 0 de limites |
| Limites ampliados | Faixas do documento 1, §5.5 | Liberação automática por histórico de cada vendedor |

> **Por que não abrir cadastro de uma vez.** Cadastro aberto no primeiro dia é o convite mais eficiente para o cenário AM-01: vender alto, sacar e desaparecer. A aprovação manual das primeiras contas custa tempo e evita a perda que a reserva não cobriria.

---

## 11. Operação após o lançamento

| Rotina | Frequência | Responsável |
|---|---|---|
| Conferir conciliação e as oito verificações de integridade | Diária | Conformidade |
| Revisar alertas de risco por vendedor e o índice agregado | Diária | Conformidade |
| Revisar a fila de emissão fiscal e a fila de eventos do provedor | Diária | Suporte |
| Responder contestações no prazo | Conforme abertura | Suporte |
| Aprovar contas novas | Diária, na fase inicial | Conformidade |
| Revisar pedidos de titular em aberto | Semanal | Encarregado |
| Revisar índices de aprovação e recuperação | Semanal | Produto |
| Ensaio de restauração de backup | Trimestral | Engenharia |
| Revisar registro de operações de tratamento | Semestral | Encarregado |
| Revisar tabela de preço do provedor e do parceiro fiscal | Semestral | Comercial |

---

## 12. Situação atual

| Pacote | Situação | Observação |
|---|---|---|
| 2.1 | Concluído | Tipo monetário em centavos, percentual em pontos-base |
| 2.2 | Concluído | Divisão, truncamento, residual no vendedor, estorno e contestação |
| 2.3 | Concluído | 28,6 milhões de cenários nas três varreduras, executados |
| 2.4 | Concluído em SQL | Esquema aplicado em PostgreSQL 16.15, 70 asserções passando. Resta dividir em migrações Flyway |
| Documentação | Concluída, revisão 3.0 | Requisitos, arquitetura, segurança, plano, guia e referência de código |
| Design | Concluído, com lacunas | 27 telas de sistema e 13 de aplicativo. Faltam comprador PJ, nota fiscal, boleto, saldo devedor e plano comercial |
| 1.1 a 1.10 | Não iniciado | **Caminho crítico. Deve começar imediatamente** |
| 2.5 em diante | Não iniciado | Próximo bloco de desenvolvimento |

### 12.1 Ações desta semana

1. **Decidir a rota entre A, B e C**, confrontando com o caixa disponível. É a decisão que mais afeta tudo o mais — e ela usa 43 semanas, não 40
2. Enviar contato comercial aos três provedores, declarando os dois segmentos na primeira frase e perguntando **PEN-21 e PEN-04 por escrito, nessa mesma mensagem**
3. Solicitar o ambiente de testes, que em geral independe do comercial
4. Marcar a consulta jurídica sobre enquadramento, incluindo explicitamente BaaS — PEN-10 e PEN-14
5. Marcar conversa com o contador sobre regime tributário e CNAE — PEN-17 e PEN-18
6. Abrir o CNPJ, se ainda não existir
7. Consultar o INPI e registrar o domínio
8. Redigir a política de conheça seu cliente e a lista de produtos proibidos
9. Prospectar parceiro fiscal, verificando cobertura municipal e emissão em nome de terceiro
10. Iniciar o pacote 2.4: dividir o esquema entregue nas migrações Flyway e ligar ao CI, com `paysi-testes-v3.0.sql` rodando a cada envio

> **O item 10 mudou de natureza.** Antes era "escrever o esquema"; agora é "integrar o esquema pronto". O ganho real não são os dois dias: é que a suíte de testes entra no CI na primeira semana, e a partir dali **toda alteração de esquema é conferida contra as 70 asserções antes de virar migração**.

---

*Documento de planejamento. Estimativas de esforço assumem uma pessoa em tempo integral com domínio prévio da stack. Valores financeiros são estimativas de mercado e não constituem cotação — devem ser substituídos por orçamento real antes de qualquer compromisso. Estimativas tributárias não constituem consultoria fiscal. O caminho crítico é comercial e jurídico até a semana 13; a partir dali, é técnico.*
