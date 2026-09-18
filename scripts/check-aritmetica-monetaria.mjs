#!/usr/bin/env node
/**
 * Detector de aritmética monetária no cliente (FE-15.1).
 *
 * Este NÃO é um analisador AST completo — é uma varredura por padrões,
 * proporcional ao estilo pragmático deste repo (ver web-painel/lib/moeda.ts
 * e run-tests.mjs). O objetivo é falhar o CI quando alguém reintroduz
 * cálculo de valores monetários no front-end (multiplicação/divisão de
 * "cents"/"amount"/"price", parseFloat/Number() sobre strings de moeda),
 * já que toda a aritmética de dinheiro deve vir pronta do backend
 * (ver web-checkout/src/lib/simulacao.ts: "os valores exibidos vêm
 * integralmente da API; o cliente não calcula descontos ou taxas").
 *
 * Arquivos sancionados (podem formatar/analisar centavos como inteiros,
 * mas não multiplicar/dividir por fatores de conversão de moeda):
 *   - web-painel/lib/moeda.ts
 *   - qualquer arquivo *.test.ts(x)
 *
 * Uso: node scripts/check-aritmetica-monetaria.mjs
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

// Arquivos que podem lidar com centavos como inteiros (formatação/parse
// sancionados) sem disparar o detector.
const ALLOWLIST = new Set([
  join(repoRoot, "web-painel", "lib", "moeda.ts"),
  // Conversão de centavos <-> string para exibição/entrada de preço na criação
  // de ofertas; não é cálculo de valores (desconto, taxa, total), é parsing e
  // formatação — mesma responsabilidade de web-painel/lib/moeda.ts.
  join(repoRoot, "web-painel", "lib", "ofertas.ts"),
  // Formatação de centavos para exibição no checkout; comentário no próprio
  // arquivo confirma que valores e cálculos sempre vêm da API.
  join(repoRoot, "web-checkout", "src", "lib", "formato.ts"),
]);

// Padrões suspeitos de aritmética monetária feita no cliente.
const SUSPECT_PATTERNS = [
  {
    // multiplicação/divisão por fatores típicos de conversão de moeda
    // (ex.: valor * 1.1, cents / 100 fora de moeda.ts, price * 0.9)
    regex: /\b(price|preco|preço|valor|amount|cents?|centavos|total|subtotal|desconto|discount|fee|taxa)\w*\s*[*/]\s*\d/i,
    message: "multiplicação/divisão de um valor monetário por uma constante numérica",
  },
  {
    regex: /\b(price|preco|preço|valor|amount|cents?|centavos|total|subtotal|desconto|discount|fee|taxa)\w*\s*[-+]\s*\w*(price|preco|preço|valor|amount|cents?|centavos|total|subtotal|desconto|discount|fee|taxa)/i,
    message: "soma/subtração entre dois valores monetários calculada no cliente",
  },
  {
    regex: /\bparseFloat\s*\(/,
    message: "parseFloat() sobre um valor — proibido para dinheiro (use inteiros em centavos vindos da API)",
  },
  {
    regex: /\bNumber\s*\(\s*(price|preco|preço|valor|amount|cents?|centavos|total|subtotal)/i,
    message: "Number() aplicado diretamente a um campo com nome de valor monetário",
  },
];

// Linhas com este marcador são explicitamente permitidas (uso documentado
// e revisado, ex.: exibição de exemplo, teste, comentário).
const IGNORE_MARKER = "aritmetica-ok";

const SKIP_DIR_NAMES = new Set(["node_modules", ".next", "dist", ".test-dist", "build", ".git"]);

/** @param {string} dir */
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

let violations = [];

for (const dir of TARGET_DIRS) {
  let files;
  try {
    files = collectFiles(dir);
  } catch {
    continue;
  }

  for (const file of files) {
    if (file.endsWith(".test.ts") || file.endsWith(".test.tsx")) continue;
    if (ALLOWLIST.has(file)) continue;
    if (file.includes(`${join("node_modules")}`)) continue;

    const content = readFileSync(file, "utf8");
    const lines = content.split(/\r?\n/);

    lines.forEach((line, idx) => {
      if (line.includes(IGNORE_MARKER)) return;
      const trimmed = line.trim();
      if (trimmed.startsWith("//") || trimmed.startsWith("*")) return;

      for (const { regex, message } of SUSPECT_PATTERNS) {
        if (regex.test(line)) {
          violations.push({
            file: relative(repoRoot, file),
            line: idx + 1,
            message,
            text: trimmed,
          });
        }
      }
    });
  }
}

if (violations.length > 0) {
  console.error(`\nDetector de aritmética monetária: ${violations.length} ocorrência(s) suspeita(s).\n`);
  for (const v of violations) {
    console.error(`  ${v.file}:${v.line} — ${v.message}`);
    console.error(`    ${v.text}`);
  }
  console.error(
    "\nToda aritmética de valores monetários deve vir pronta do backend (API)." +
      "\nSe a ocorrência é falso positivo e foi revisada, adicione o comentário" +
      ` "${IGNORE_MARKER}" na mesma linha, ou inclua o arquivo na ALLOWLIST deste script.\n`,
  );
  process.exit(1);
}

console.log("Detector de aritmética monetária: nenhuma ocorrência suspeita encontrada.");
