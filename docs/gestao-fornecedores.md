# Gestão de Terceiros e Fornecedores

**Responsável:** Tecnologia e Segurança da Informação
**Revisão do inventário:** trimestral e antes de nova contratação
**Versão:** 1.0 — 06/11/2026

## Processo de contratação e acesso

Antes de contratar ou conceder acesso, o responsável registra finalidade,
dados acessados, criticidade, localização, suboperadores, autenticação,
criptografia, continuidade, prazo de retenção, exclusão, histórico de
incidentes e capacidade de notificação. O acesso só é concedido após aceite do
risco e pelo menor privilégio.

Contratos novos que envolvam dados ou sistemas devem prever confidencialidade,
LGPD, controles de acesso, proteção de credenciais, notificação de incidente
sem demora injustificada, devolução/eliminação de dados, auditoria e uso de
subcontratados somente nas condições previamente autorizadas ou notificadas.

**Condição de conformidade:** política interna não altera contratos já
assinados. Cada fornecedor existente deve ter seus termos/DPA verificados e a
evidência anexada ao inventário antes de afirmar que todas as cláusulas estão
cobertas.

## Inventário inicial

| Fornecedor/categoria | Serviço e acesso | Criticidade | Situação da análise contratual |
|---|---|---|---|
| Asaas | BaaS, pagamentos, subcontas e dados cadastrais/financeiros | Crítica | avaliação BaaS em andamento; guardar contrato e anexos |
| GitHub/GHCR/Actions | código, imagens, CI/CD e secrets de deploy | Alta | termos/DPA devem ser arquivados |
| Provedor KVM/VPS | processamento, banco, arquivos e logs de produção | Crítica | identificar razão social e arquivar contrato/DPA/SLA |
| Google/Gmail | comunicação empresarial e suporte | Média | arquivar termos e controles da conta |
| Provedor DNS/registro | resolução dos domínios públicos | Alta | identificar fornecedor e arquivar termos/SLA |

O inventário não armazena senhas ou chaves. Mudanças de acesso, encerramento e
incidentes são registrados junto à revisão trimestral.
