#!/usr/bin/env node
/**
 * Orçamento de bundle do checkout (FE-15.1).
 *
 * Critério de aceite do cartão: "bundle < 180KB" (gzip), incluindo o SDK de
 * pagamento se houver um embutido no bundle. Hoje web-checkout não embute
 * nenhum SDK externo de tokenização — a tokenização é simulada localmente em
 * src/lib/cartao.ts (ver comentário no próprio arquivo) — mas o orçamento é
 * medido sobre TODO o JS+CSS publicado em dist/, então qualquer SDK
 * futuramente adicionado ao bundle entra automaticamente nesta soma.
 *
 * Pré-requisito: rodar `npm run build` em web-checkout antes deste script
 * (ele lê web-checkout/dist).
 *
 * Uso: node scripts/check-bundle-size.mjs
 */

import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, extname } from "node:path";
import { fileURLToPath } from "node:url";
import { gzipSync } from "node:zlib";

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), "..");

function dirname(p) {
  return p.replace(/[\\/][^\\/]*$/, "");
}

const distDir = join(repoRoot, "web-checkout", "dist");
const BUDGET_BYTES = 180 * 1024; // 180KB (binário), conforme critério de aceite do cartão.

// Extensões que contam para o orçamento de "bundle" entregue ao navegador.
// Sourcemaps (.map) não são baixados pelo usuário final e ficam de fora.
const COUNTED_EXTENSIONS = new Set([".js", ".mjs", ".css"]);

function collectFiles(dir) {
  const result = [];
  let entries;
  try {
    entries = readdirSync(dir, { withFileTypes: true });
  } catch {
    return result;
  }
  for (const entry of entries) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) {
      result.push(...collectFiles(full));
    } else if (entry.isFile()) {
      result.push(full);
    }
  }
  return result;
}

let files;
try {
  files = collectFiles(distDir).filter((f) => COUNTED_EXTENSIONS.has(extname(f)));
} catch {
  files = [];
}

if (files.length === 0) {
  console.error(
    `Orçamento de bundle: não encontrei arquivos em ${distDir}.` +
      " Rode `npm run build` em web-checkout antes deste script.",
  );
  process.exit(1);
}

let totalGzip = 0;
const rows = [];

for (const file of files) {
  const raw = readFileSync(file);
  const gzipSize = gzipSync(raw, { level: 9 }).length;
  totalGzip += gzipSize;
  rows.push({ file: file.slice(distDir.length + 1), rawSize: statSync(file).size, gzipSize });
}

rows.sort((a, b) => b.gzipSize - a.gzipSize);

console.log("Orçamento de bundle do checkout (gzip):\n");
for (const row of rows) {
  console.log(`  ${row.file.padEnd(40)} ${(row.gzipSize / 1024).toFixed(2).padStart(8)} KB gzip`);
}
console.log(`\n  TOTAL: ${(totalGzip / 1024).toFixed(2)} KB gzip (orçamento: ${(BUDGET_BYTES / 1024).toFixed(0)} KB)`);

if (totalGzip > BUDGET_BYTES) {
  console.error(
    `\nOrçamento de bundle excedido: ${(totalGzip / 1024).toFixed(2)} KB > ${(BUDGET_BYTES / 1024).toFixed(0)} KB.`,
  );
  process.exit(1);
}

console.log("\nOrçamento de bundle dentro do limite.");
