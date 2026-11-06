# Política de Desenvolvimento Seguro de Software

**Responsável:** Tecnologia e Segurança da Informação
**Revisão:** anual e após incidente ou mudança relevante
**Versão:** 1.0 — 06/11/2026

## Regras desde a concepção

Toda funcionalidade deve aplicar segurança e privacidade desde a concepção:

- coletar e armazenar somente os dados necessários;
- definir autenticação, autorização e menor privilégio antes da implementação;
- validar entradas no servidor e codificar saídas conforme o contexto;
- usar consultas parametrizadas e componentes mantidos, sem criptografia própria;
- nunca registrar ou versionar senhas, tokens, chaves, CVV ou cartão completo;
- usar HTTPS, cookies seguros, proteção CSRF e cabeçalhos de segurança;
- proteger operações financeiras com idempotência, trilha de auditoria e
  reconciliação;
- manter produção separada de desenvolvimento e usar dados fictícios nos
  testes;
- prever tratamento seguro de erro, limites de requisição e monitoramento;
- criar testes para requisitos de segurança e para toda correção de
  vulnerabilidade.

Dados de cartão são tratados somente de forma transitória pelo fluxo de
tokenização e não devem ser persistidos pela Paysi. Segredos são fornecidos em
execução por cofre de credenciais e não podem fazer parte de código, imagem ou
artefato.

## Verificação antes da produção

Uma alteração só pode chegar à produção depois que o CI da branch principal
for concluído com sucesso. A verificação bloqueante inclui testes automatizados
de backend e frontend, validações de arquitetura, lint, detecção de PII em logs
e uma varredura Trivy de dependências, segredos e configurações. CodeQL, testes
dinâmicos com OWASP ZAP e revisão automática de dependências complementam o
processo periodicamente.

Achado crítico ou alto impede a publicação até correção, mitigação verificável
ou aceite formal e temporário do risco conforme a política de vulnerabilidades.
Cada correção de segurança deve incluir teste de regressão quando tecnicamente
aplicável.

Como existe atualmente um único mantenedor técnico, a revisão independente por
outra pessoa não é declarada. O controle vigente é a inspeção automática e
rastreável pelas ferramentas do CI/CD. Quando houver mais de um desenvolvedor,
mudanças críticas também exigirão revisão humana por pessoa distinta.
