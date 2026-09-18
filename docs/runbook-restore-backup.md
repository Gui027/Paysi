# Runbook — Backup e Restore

**BE-15.1 · checklist #32 do documento 3 · RNF-008 (RPO 5 min), RNF-009/009a (RTO 1h/4h), RNF-010 (restauração trimestral com evidência)**

Este runbook cobre o procedimento operacional de restauração de backup do Postgres
e registra a evidência de um ciclo de restauração executado de ponta a ponta
durante o desenvolvimento do BE-15.1.

## 1. Estratégia de continuidade (documento 3, §5.3)

| Item | Definição |
|---|---|
| RPO (perda máxima aceitável) | 5 minutos — arquivamento contínuo de WAL do provedor gerenciado |
| RTO em horário comercial estendido | 1 hora |
| RTO fora do horário comercial | 4 horas |
| Teste de restauração | Trimestral, com evidência registrada (este documento é o modelo do registro) |
| Cópia geográfica | Região distinta dentro do território nacional (ADR-10) |

Em produção, o RPO de 5 minutos depende do arquivamento contínuo de WAL do
provedor gerenciado de Postgres (point-in-time recovery), não de `pg_dump`
periódico. `pg_dump` é o mecanismo verificado neste runbook porque é o que
pode ser executado e evidenciado neste ambiente de desenvolvimento; em
produção o procedimento de restauração real usa o PITR do provedor, e o
runbook do provedor específico deve ser anexado a este documento quando a
infraestrutura de produção for definida (pendência `JUR`/`FIS`-adjacente:
depende da nuvem escolhida, ADR-10).

## 2. Procedimento de restauração

1. **Interromper liberações automáticas de saldo e saques** (documento 3, §5.2,
   passo 1) — mesmo procedimento de divergência financeira, porque uma
   restauração em andamento é uma janela onde o estado do razão não é confiável.
2. Identificar o backup mais recente anterior ao incidente (arquivamento
   contínuo ou o último `pg_dump` agendado).
3. Restaurar em um banco **separado** do de produção — nunca sobrescrever o
   banco vivo diretamente. Validar antes de promover.
4. Rodar as 70 asserções de esquema (`docs/paysi-testes-v3.0.sql`) contra o
   banco restaurado.
5. Rodar as oito verificações de integridade (`LedgerIntegrityMonitor`) contra
   o banco restaurado — devem vir vazias, ou a causa raiz do incidente ainda
   não foi endereçada.
6. Comparar contagem de linhas das tabelas centrais (`accounts`, `orders`,
   `charges`, `ledger_entries`) contra o último checkpoint monitorado antes do
   incidente.
7. Promover o banco restaurado só depois da validação acima.
8. Reconstruir `ledger_checkpoints` e `ledger_release_schedule` das contas
   afetadas (documento 3, §5.2, passos 7 e 8) — restaurar o razão não
   reconstrói sozinho as estruturas derivadas.
9. Registrar a causa raiz e o teste que passará a detectá-la.

## 3. Evidência de execução — 2026-09-18

Ciclo completo de backup/restore executado nesta sessão contra um Postgres 16
efêmero (`postgres:16-alpine` via Docker), com o esquema completo aplicado
pelas 53 migrações de `backend/src/main/resources/db/migration/` (mesmo
procedimento do job `database` do CI).

```
=== 1. Seed evidence rows (accounts) ===
INSERT 0 2

=== 2. Row count BEFORE backup ===
accounts count before dump: 2

=== 3. pg_dump (custom format) ===
-rw-r--r-- 1 evald 197609 180226 Sep 18 07:35 paysi_backup_evidence.dump

=== 4. Simulate disaster: drop and recreate database ===
DROP DATABASE
CREATE DATABASE

=== 5. Confirm database is empty after drop/recreate ===
     0

=== 6. Restore from dump ===
pg_restore: creating ACL "public.TABLE products"
...
pg_restore: creating DEFAULT ACL "public.DEFAULT PRIVILEGES FOR TABLES"

=== 7. Row count AFTER restore ===
accounts count after restore: 2
tables after restore: 60

=== 8. Verify the two seeded accounts are present by natural key ===
           email            |   full_name
----------------------------+----------------
 restore-demo-1@paysi.local | Demo Restore 1
 restore-demo-2@paysi.local | Demo Restore 2
(2 rows)

BEFORE=2 AFTER=2
RESTORE_EVIDENCE: PASS - row counts match before and after restore
```

Interpretação:

- 60 de 60 tabelas do esquema foram recriadas pela restauração — nenhuma
  perdida.
- As duas linhas de evidência semeadas antes do `pg_dump` (`accounts` com
  e-mail `restore-demo-1@paysi.local` e `restore-demo-2@paysi.local`)
  sobreviveram ao ciclo completo de queda + restauração, identificadas pela
  chave natural (`email`), não só por contagem — o que descarta o caso onde a
  contagem bate por coincidência com dado diferente.
- O comando usado foi `pg_dump -Fc` (formato customizado, comprimido) seguido
  de `pg_restore`, que é o par recomendado pela documentação do Postgres para
  bancos deste porte — evita o `psql -f dump.sql` de texto puro, mais lento
  para restaurar e sem paralelismo.

### O que este teste comprova e o que não comprova

Comprova: o par `pg_dump`/`pg_restore` preserva schema e dados através de uma
queda simulada de banco, e o procedimento é executável por qualquer operador
com acesso ao Postgres — não depende de ferramenta proprietária de um provedor
específico.

Não comprova: o RPO de 5 minutos de produção (que depende de PITR contínuo do
provedor gerenciado, não testável neste ambiente) nem o RTO de 1h/4h fim-a-fim
incluindo provisionamento de infraestrutura nova — este teste mediu apenas a
etapa de restauração de dados em um banco já provisionado.

## 4. Gap documentado

O teste trimestral de restauração em produção (item deste runbook, seção 1)
precisa ser agendado como rotina operacional recorrente assim que a
infraestrutura de produção estiver definida (ADR-10 pendente: qual nuvem, qual
região secundária). Este runbook é o modelo de evidência a ser preenchido a
cada execução trimestral — não substitui a execução recorrente.
