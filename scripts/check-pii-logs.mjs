#!/usr/bin/env node
/**
 * Guarda de PII/segredos em logs e analytics (FE-15.1).
 *
 * Varredura por padrões (não AST) que falha o CI quando o código de
 * web-painel/web-checkout:
 *   1) usa console.log/debug/info/warn/error fora de código de teste — hoje
 *      o front-end não tem nenhum, e este script trava a introdução de
 *      novos sem revisão explícita (aprovados via IGNORE_MARKER);
 *   2) parece registrar campos sensíveis (email, cpf/cnpj, número de
 *      cartão, cvv, senha/token) em uma chamada de log/analytics.
 *
 * Uso: node scripts/check-pii-logs.mjs
 */

import { readFileSync, readdirSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), "..");

function dirname(p) {
  return p.replace(/[\\/][^\\/]*$/, "");
}

const TARGET_DIRS = [
  join(repoRoot, "web-painel", "app"),
  join(repoRoot, "web-painel", "lib"),
  join(repoRoot, "web-painel", "components"),
  join(repoRoot, "web-checkout", "src"),
];

const SKIP_DIR_NAMES = new Set(["node_modules", ".next", "dist", ".test-dist", "build", ".git"]);

function collectFiles(dir) {
  const result = [];
  let entries;
  try {
    entries = readdirSync(dir, { withFileTypes: true });
  } catch {
    return result;
  }
  for (const entry of entries) {
    if (SKIP_DIR_NAMES.has(entry.name)) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      result.push(...collectFiles(full));
    } else if (entry.isFile() && (full.endsWith(".ts") || full.endsWith(".tsx"))) {
      result.push(full);
    }
  }
  return result;
}

const IGNORE_MARKER = "log-ok";

const LOG_CALL = /\b(console\.(log|debug|info|warn|error)|analytics\.(track|identify|page)|window\.gtag|window\.dataLayer\.push)\s*\(/;
const SENSITIVE_FIELD = /\b(email|e-mail|cpf|cnpj|documento|cardNumber|numeroCartao|cvv|cvc|senha|password|token|cardToken|secret|authorization)\b/i;

let violations = [];

for (const dir of TARGET_DIRS) {
  for (const file of collectFiles(dir)) {
    if (file.endsWith(".test.ts") || file.endsWith(".test.tsx")) continue;

    const content = readFileSync(file, "utf8");
    const lines = content.split(/\r?\n/);

    lines.forEach((line, idx) => {
      if (line.includes(IGNORE_MARKER)) return;
      const trimmed = line.trim();
      if (trimmed.startsWith("//") || trimmed.startsWith("*")) return;

      if (LOG_CALL.test(line)) {
        const sensitive = SENSITIVE_FIELD.test(line);
        violations.push({
          file: relative(repoRoot, file),
          line: idx + 1,
          text: trimmed,
          severity: sensitive ? "critico" : "revisar",
        });
      }
    });
  }
}

if (violations.length > 0) {
  const criticos = violations.filter((v) => v.severity === "critico");
  console.error(`\nGuarda de PII/logs: ${violations.length} chamada(s) de log/analytics encontrada(s).\n`);
  for (const v of violations) {
    console.error(`  [${v.severity}] ${v.file}:${v.line}`);
    console.error(`    ${v.text}`);
  }
  if (criticos.length > 0) {
    console.error(
      `\n${criticos.length} ocorrência(s) parecem registrar campo(s) sensível(is) (email/cpf/cartão/senha/token).` +
        " Remova o dado sensível do log ou redija-o antes de logar.",
    );
  } else {
    console.error(
      "\nNenhum campo sensível óbvio nos logs encontrados, mas este repo não deve ter" +
        " console.log/analytics no front-end sem revisão explícita.",
    );
  }
  console.error(
    `Se a chamada foi revisada e é segura (não expõe PII), adicione o comentário "${IGNORE_MARKER}" na mesma linha.\n`,
  );
  process.exit(1);
}

console.log("Guarda de PII/logs: nenhuma chamada de log/analytics suspeita encontrada.");
