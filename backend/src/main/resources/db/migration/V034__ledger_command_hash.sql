ALTER TABLE ledger_transactions ADD COLUMN command_hash text;

COMMENT ON COLUMN ledger_transactions.command_hash IS
  'SHA-256 canônico do comando; detecta reutilização da chave natural com conteúdo diferente.';
