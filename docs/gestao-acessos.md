# Gestão de Acessos

**Responsável:** Tecnologia e Segurança da Informação
**Revisão:** trimestral e em toda admissão, mudança de função ou desligamento
**Versão:** 1.0 — 06/11/2026

## Concessão, alteração e retirada

Todo acesso deve ter finalidade, sistema, perfil e responsável identificados.
O sócio-administrador aprova a concessão conforme menor privilégio. Contas não
podem ser compartilhadas. Mudança de função provoca nova avaliação e saída da
empresa ou do fornecedor provoca bloqueio imediato e revogação de sessões,
tokens e chaves relacionadas.

Contas críticas — GitHub, servidor, Asaas, e-mail corporativo, DNS e consoles de
nuvem — devem usar MFA sempre que o fornecedor oferecer o recurso. Credenciais
de serviço não são usadas para login humano e seguem a política de gestão de
segredos.

## Revisão trimestral

O responsável exporta ou consulta a lista de usuários e permissões de cada
sistema crítico, compara com a necessidade atual e registra data, sistema,
contas encontradas, perfil, decisão e ação realizada. Acesso sem justificativa
é revogado no mesmo ciclo; acesso privilegiado desnecessário é reduzido.

Como a empresa possui atualmente um único mantenedor técnico, não se afirma
segregação humana de funções. Alterações de produção continuam sujeitas a CI,
histórico do Git e trilha de deploy. A contratação de outro operador exigirá
perfis separados e aprovação por pessoa distinta para mudanças críticas.
