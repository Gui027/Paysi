# Processo de Gestão de Vulnerabilidades

**Responsável:** Tecnologia e Segurança da Informação
**Revisão:** trimestral
**Versão:** 1.0 — 06/11/2026

## Identificação

- CodeQL em pushes, pull requests e semanalmente;
- Trivy semanal para dependências, segredos e configuração;
- Dependabot semanal para Maven, npm, GitHub Actions e imagens Docker;
- OWASP ZAP Baseline semanal contra a superfície web pública;
- testes automatizados, revisão de código, alertas de fornecedores e relatos
  privados pelo GitHub Security Advisories.

Resultados permanecem nos históricos do GitHub Actions, Security e issues. Um
achado não é encerrado apenas por ser falso positivo: a justificativa deve ser
registrada.

## Triagem, responsável e SLA

O sócio-administrador responsável por Tecnologia e Segurança da Informação é
o proprietário inicial de todos os achados e pode designar outro responsável
nominal. Cada item registra severidade, componente, evidência, responsável,
prazo e estado.

| Severidade | Contenção | Correção-alvo |
|---|---:|---:|
| Crítica | imediata | 24 horas |
| Alta | 1 dia útil | 7 dias corridos |
| Média | 3 dias úteis | 30 dias |
| Baixa | próxima priorização | 90 dias |

Exceções exigem justificativa, controle compensatório, responsável e nova data.
Críticos e altos não podem ser aceitos indefinidamente.

## Validação

Toda correção passa pelos testes automatizados e por nova execução do scanner
que encontrou o problema. Achados com indício de exploração acionam o Plano de
Resposta a Incidentes, independentemente do prazo normal de correção.

Uma avaliação de invasão mais ampla deve ser realizada anualmente e antes de
mudança material no fluxo financeiro. O ZAP é evidência de DAST recorrente,
mas não substitui avaliação humana independente quando exigida por contrato.
