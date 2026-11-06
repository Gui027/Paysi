# Plano de Resposta a Incidentes de Segurança

**Responsável primário:** sócio-administrador de Tecnologia e Segurança da Informação
**Substituto:** representante legal formalmente designado
**Versão:** 1.0 — 06/11/2026
**Teste de mesa:** semestral

## Classificação e prazos internos

| Severidade | Exemplo | Início da resposta | Correção/mitigação-alvo |
|---|---|---:|---:|
| Crítica | vazamento, fraude ativa, chave exposta, integridade financeira | imediata | 24 horas |
| Alta | indisponibilidade de checkout, vulnerabilidade explorável | até 1 hora | 7 dias ou antes |
| Média | degradação sem perda financeira | 1 dia útil | 30 dias |
| Baixa | baixo impacto, sem exploração | 3 dias úteis | 90 dias |

## Fluxo obrigatório

1. Registrar data/hora da ciência, origem do alerta, sistemas e responsável.
2. Preservar evidências antes de corrigir: logs, IDs, versões e linha do tempo.
3. Conter: revogar credenciais, bloquear acesso, isolar componente ou suspender
   a função afetada sem apagar evidências.
4. Avaliar alcance: dados, titulares, valores, fornecedores e integrações.
5. Erradicar a causa, aplicar correção e validar integridade.
6. Recuperar gradualmente, monitorando recorrência.
7. Produzir análise de causa raiz e criar teste/controle preventivo.
8. Registrar encerramento, decisões, comunicações e pendências.

## Comunicação

- **Asaas:** incidentes que possam afetar a integração BaaS, credenciais,
  pagamentos, subcontas ou dados compartilhados são comunicados sem demora ao
  canal de Segurança BaaS informado no processo de avaliação e, se necessário,
  ao atendimento oficial. A comunicação inicial contém horário, escopo
  conhecido, contenção e ponto de contato; complementações seguem a apuração.
- **ANPD e titulares:** incidente confirmado com dados pessoais que possa
  acarretar risco ou dano relevante é comunicado pelo representante legal ou
  encarregado via SEI!ANPD e diretamente aos titulares em até três dias úteis
  da ciência, salvo prazo específico aplicável. Informações incompletas são
  complementadas no prazo regulatório.
- **Clientes e parceiros:** são avisados pelo e-mail cadastrado e pelo canal
  operacional adequado quando houver impacto, ação necessária ou risco.

Nenhuma comunicação externa contém segredo, dado desnecessário ou hipótese
apresentada como fato. Assessoria jurídica deve revisar comunicações
regulatórias quando o tempo permitir, sem atrasar o prazo legal.

## Registro e teste

Registros de incidentes são mantidos por no mínimo cinco anos. A cada semestre
é realizado exercício de mesa, registrando cenário, participantes, decisões,
tempo de resposta, falhas e responsáveis pelas ações corretivas.
