# Plano de Continuidade e Recuperação de Desastre

**Responsável:** Tecnologia e Segurança da Informação
**Revisão:** trimestral e após incidente/teste
**Versão:** 1.0 — 06/11/2026

## Metas operacionais atuais

| Item | Meta |
|---|---:|
| RPO atual | 24 horas, enquanto o backup diário for o mecanismo disponível |
| RTO em horário comercial | 4 horas |
| RTO fora do horário comercial | 8 horas |
| Backup | diário, com retenção local de 14 dias |
| Teste de restauração | trimestral, em ambiente isolado, com evidência |

O objetivo futuro de RPO de cinco minutos depende de arquivamento contínuo de
WAL/PITR e não é declarado como controle ativo até ser contratado e testado.

## Estratégia

O backup diário contém dump consistente do PostgreSQL, arquivos da aplicação e
checksums. O dump é validado com `pg_restore --list`. A cópia externa usa
restic, que cifra conteúdo e metadados antes do envio, para repositório em
conta/região distinta. O destino deve habilitar versionamento e, quando
disponível, Object Lock/imutabilidade para impedir exclusão pelo operador da
aplicação.

Ativação obrigatória antes de declarar backup externo operacional:

1. contratar/criar bucket em região brasileira distinta do servidor;
2. habilitar versionamento e retenção imutável;
3. criar credencial com permissão apenas para o prefixo de backup;
4. armazenar senha restic e credenciais via `systemd-creds encrypt`;
5. instalar restic e ativar o drop-in
   `infra/systemd/paysi-backup-offsite.conf.example`;
6. executar backup e restauração de teste, registrando IDs, horários e hashes;
7. configurar alerta para falha ou ausência de backup por mais de 26 horas.

## Recuperação

1. declarar incidente e preservar evidências;
2. suspender saques/liberações se houver dúvida sobre integridade financeira;
3. selecionar snapshot anterior ao incidente e validar checksum;
4. restaurar sempre em banco isolado;
5. aplicar verificações de esquema e as oito verificações do razão;
6. comparar contagens e transações com o último checkpoint íntegro;
7. promover somente após aprovação do responsável técnico;
8. monitorar, comunicar impacto e registrar causa raiz.

O procedimento técnico detalhado e a evidência anterior estão em
`docs/runbook-restore-backup.md`.
