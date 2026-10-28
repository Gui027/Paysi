# Taxa de criação de subconta: como evitar pagá-la

Documento de decisão. Compara dois modelos para que a Paysi **não arque com a taxa que o Asaas cobra pela criação de subconta**, com e sem custódia de dinheiro de terceiros.

## 1. O problema

- O Asaas debita da conta mestre da Paysi uma taxa a cada subconta white label criada.
- Em teste, a criação de um vendedor gerou um boleto de **R$ 25,80** ("saldo negativo em sua conta Asaas"), porque a conta mestre estava sem saldo.
- A `PLATFORM_FEE` cobrada do vendedor é de **R$ 12,00** (ver `02-arquitetura-e-dados.md`). Se o custo real for maior, cada vendedor aprovado dá prejuízo.
- Hoje a subconta é criada na **primeira tentativa de publicar** uma oferta (início do KYC), antes de existir qualquer venda. Quem cria conta, tenta publicar e desiste ainda assim custa dinheiro.

**Passo zero, nos dois modelos:** abrir o extrato do Asaas no dia do teste e anotar o valor exato de cada débito. Sem isso não há como definir o desconto da taxa. Esta é a pendência PEN-08 de `01-requisitos.md`.

## 2. Estado atual (referência)

1. Vendedor cria conta e produto (rascunho).
2. Vendedor tenta publicar. O KYC começa e a subconta é criada no Asaas. **A taxa é cobrada da Paysi aqui.**
3. KYC aprovado. A Paysi lança a `PLATFORM_FEE` de R$ 12 ao vendedor, uma vez, de forma idempotente. Se ele não tiver saldo, vira `DEBT`.
4. Oferta publicada. Vendas usam split para a subconta.

Problema: o custo ocorre no passo 2, e a cobrança ao vendedor só no passo 3. Nada garante que o vendedor venderá.

---

## 3. Modelo A: SEM custódia (recomendado)

A subconta nasce quando o vendedor **ativa o checkout**, e só então. O dinheiro nunca passa pela conta mestre.

### Passo a passo

1. **Cadastro.** O vendedor cria a conta. Custo para a Paysi: zero.
2. **Rascunho.** O vendedor cria produtos e ofertas em rascunho. Custo: zero.
3. **Ativar checkout.** Quando o vendedor pede para publicar/ativar a venda, a Paysi exibe o aviso: "para vender, é preciso verificar sua identidade e será cobrada uma taxa única de R$ X, descontada do seu primeiro saque".
4. **Início do KYC e criação da subconta.** Só nesse momento a Paysi chama o Asaas. **A taxa do Asaas é cobrada da Paysi aqui.**
5. **KYC aprovado.** A oferta é publicada. O checkout é liberado.
6. **Vendas.** Cada cobrança leva o `walletId` da subconta e o split vai direto para ela. A mestre recebe só a comissão da Paysi.
7. **Saque.** O vendedor só saca com saldo mínimo de R$ 100. **No primeiro saque a Paysi desconta a taxa de verificação** (`PLATFORM_FEE`), recuperando o que adiantou ao Asaas.
8. **KYC reprovado.** Pelo desenho atual, a rejeição não cobra taxa do vendedor. A Paysi absorve o custo, se o Asaas cobrar na tentativa. Ver risco R2.

### O que muda no código

- Mover o gate de KYC de "publicar oferta" (`OfferPublicationService`, linha 49) para "ativar checkout". Em termos de produto, pode ser o mesmo botão com um aviso mais claro, ou um passo novo.
- Ajustar a cobrança da `PLATFORM_FEE` para ocorrer **no primeiro saque** (hoje ocorre na aprovação do KYC). Manter a chave idempotente do ledger.
- Garantir que um saque abaixo de R$ 100 seja recusado, e que o desconto da taxa não deixe o saldo negativo.

### Quanto a Paysi paga

- Custo do Asaas só para quem pediu para ativar o checkout, não para todo cadastro.
- Recuperado do primeiro saque, **se** o vendedor sacar. Se ele ativar, vender pouco e nunca sacar, a Paysi fica com o custo (ver R1).

### Riscos

| # | Risco | Mitigação |
|---|---|---|
| R1 | Vendedor ativa o checkout e nunca saca (ou nunca vende): a Paysi absorve a taxa | Cobrar parte na ativação, ou exigir a primeira venda antes de liberar o KYC (ver Variante A2) |
| R2 | KYC reprovado: o Asaas pode ter cobrado a subconta e a Paysi não recupera | Confirmar com o Asaas se a taxa incide em subconta reprovada (PRE-10) |
| R3 | O vendedor ainda enfrenta o KYC antes da primeira venda, o que reduz a conversão | Aviso claro e fluxo de documentos simples |

### Variante A2: subconta só após a primeira venda, sem custódia

Para evitar custo com quem nunca vendeu, a primeira venda precisa de uma subconta, senão o dinheiro cai na mestre (Modelo B). A forma sem custódia é:

- Permitir que o vendedor **apenas teste** o checkout com a oferta em modo de pré-visualização (já existe pré-visualização por UUID, commit `a3db951`), sem venda real.
- Para vender de verdade, é preciso estar com a subconta ativa, como no Modelo A.

Resumo: sem custódia, não há como ter venda real antes da subconta. O filtro possível é o aviso e a pré-visualização.

---

## 4. Modelo B: COM custódia (Paysi guarda o dinheiro por um período)

A subconta só é criada depois da **primeira venda paga**. Até lá o dinheiro fica na conta mestre.

### Passo a passo

1. **Cadastro e rascunho.** Custo zero.
2. **Publicar sem KYC.** O gate de publicação é removido. O vendedor publica a oferta e o checkout funciona.
3. **Venda.** A cobrança é criada **sem `walletId`**. O valor entra inteiro na **conta mestre** do Asaas.
4. **Ledger.** A Paysi registra o valor líquido do vendedor como **saldo pendente (bloqueado)**, separado do saldo da própria Paysi.
5. **Gatilho do KYC.** A primeira venda paga dispara o aviso: "você tem R$ X a receber; envie seus documentos para liberar".
6. **Criação da subconta.** Só agora a Paysi chama o Asaas. **A taxa do Asaas é cobrada da Paysi aqui, e só para quem vendeu.**
7. **KYC aprovado.** A Paysi lança a `PLATFORM_FEE` ao vendedor e faz a **transferência interna da mestre para a subconta** do saldo retido, menos a taxa. A operação é idempotente.
8. **Próximas vendas.** Já com `walletId`, split direto, sem passar pela mestre.
9. **Saque.** Mínimo de R$ 100.
10. **KYC reprovado ou não concluído.** A Paysi estorna os compradores ou devolve ao vendedor por outro meio, dentro de um prazo definido.

### O que muda no código

- Remover o bloqueio de publicação por KYC e permitir cobrança sem `walletId` (já é possível: `JdbcWalletLookup.walletId` devolve `Optional`).
- Novo estado no ledger: saldo retido por vendedor, sem subconta.
- Gatilho "primeira venda paga" que inicia o KYC.
- Rotina idempotente de repasse mestre para subconta após a aprovação.
- Limites da janela: valor máximo retido por vendedor e prazo máximo; estorno automático ao estourar.

### Quanto a Paysi paga

- Taxa do Asaas só para quem vendeu pelo menos uma vez. É a melhor eficiência de custo.

### Riscos (custódia)

| # | Risco | Mitigação |
|---|---|---|
| C1 | **Conta de passagem.** Dinheiro de terceiros fica na mestre. Contraria o desenho do PEN-21 | Validar com o jurídico e com o contrato do Asaas antes de implementar |
| C2 | Enquadramento regulatório (arranjo ou instituição de pagamento) | Parecer jurídico. Limitar valor e prazo da retenção |
| C3 | Bloqueio da conta mestre, dívida ou processo contra a Paysi atinge o dinheiro retido | Ledger rigoroso e conta mestre sem saldo de risco além do retido |
| C4 | Estornos e chargebacks antes da subconta ficam por conta da Paysi | Reserva sobre o saldo retido; prazo de contestação |
| C5 | KYC reprovado exige devolver dinheiro sem subconta | Política de estorno ao comprador definida nos termos de uso |
| C6 | Split de afiliados não existe sem subconta do vendedor e do afiliado | Retenção também da comissão, ou exigir subconta do afiliado antes |

---

## 5. Comparação

| | Modelo A: sem custódia | Modelo B: com custódia |
|---|---|---|
| Taxa do Asaas paga por | Quem ativou o checkout | Só quem vendeu |
| Dinheiro de terceiros na mestre | Nunca | Sim, até a aprovação do KYC |
| Risco jurídico e regulatório | Baixo | Alto, precisa de parecer |
| KYC antes da primeira venda | Sim | Não |
| Conversão do vendedor | Menor | Maior |
| Mudança no código | Média | Grande |
| Alinhado ao PEN-21 | Sim | Não |

## 6. Recomendação

1. **Hoje:** conferir o extrato do Asaas e ajustar a `PLATFORM_FEE` para cobrir o custo real, mais margem.
2. **Implementar o Modelo A** movendo o KYC e a criação da subconta para a ativação do checkout, e cobrando a taxa no primeiro saque (mín. R$ 100).
3. **Considerar o Modelo B só se** (a) o jurídico e o Asaas aprovarem custódia temporária e (b) a quantidade de cadastros que nunca vendem justificar a complexidade.

## 7. Perguntas para o Asaas e o jurídico

1. Qual o valor exato da taxa de criação de subconta white label, e há mensalidade ou taxa de manutenção?
2. A taxa incide em subconta criada mas reprovada no KYC?
3. Existe isenção, pacote ou desconto por volume de subcontas?
4. É permitido manter na conta mestre valores de vendedores ainda sem subconta? Por quanto tempo?
5. A API permite transferência da conta mestre para a subconta? Tem custo?
6. A Paysi pode bloquear/liberar saldo dentro da subconta nominal do vendedor por API (PEN-21)?
7. (Jurídico) Qual o enquadramento regulatório da retenção temporária de valores de terceiros?

## 8. Pendências

- [ ] Anotar os débitos do extrato do Asaas referentes ao teste.
- [ ] Responder as perguntas da seção 7.
- [ ] Decidir entre Modelo A e Modelo B.
- [ ] Ajustar `PLATFORM_FEE` ao custo real.
- [ ] Planejar a implementação do modelo escolhido.

## 9. Atualização (28/10/2026): regra do Asaas sobre tarifas em nome do parceiro

Na análise do checklist BaaS, o Asaas informou que o parceiro **não pode cobrar tarifa relativa aos serviços financeiros em nome próprio** (pergunta 8). Remuneração por **split/overprice** e mensalidade de software não entram nessa regra.

Consequências para este documento:

- A **taxa de verificação de R$ 12** foi removida do código (`KycService` e `KycWebhookService`). A Paysi não cobra mais nada pela verificação ou pela abertura da subconta.
- A **taxa de ativação paga antes** (seção 6 do PDF) **não deve ser adotada** como tarifa de abertura de conta. Só poderia existir como taxa de uso do software, e apenas se a Eliana (Asaas) confirmar por escrito que é aceitável.
- A decisão de **não custodiar** valores de terceiros está tomada: o fluxo continua com a subconta criada no início do KYC. O custo da subconta passa a ser coberto pela comissão por venda (split).
- Não há tarifa de saque (`PAYOUT_FEE_CENTS = 0`) nem de antecipação (não existe no código).
