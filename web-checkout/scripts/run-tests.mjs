import { rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { spawnSync } from "node:child_process";

const checkoutRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const output = join(checkoutRoot, ".test-dist");
const tsc = join(checkoutRoot, "..", "node_modules", "typescript", "bin", "tsc");
const modules = ["documento", "mascaras", "camposComprador", "checkout", "termos", "simulacao"];

rmSync(output, { recursive: true, force: true });

const compile = spawnSync(process.execPath, [
  tsc,
  join(checkoutRoot, "src", "lib", "api.ts"),
  ...modules.flatMap(name => [
    join(checkoutRoot, "src", "lib", `${name}.ts`),
    join(checkoutRoot, "src", "lib", `${name}.test.ts`),
  ]),
  "--ignoreConfig",
  "--outDir", output,
  "--module", "es2022",
  "--target", "es2022",
  "--types", "node,vite/client",
  "--esModuleInterop",
  "--skipLibCheck",
], { stdio: "inherit" });

if (compile.status !== 0) {
  rmSync(output, { recursive: true, force: true });
  process.exit(compile.status ?? 1);
}

const tests = spawnSync(process.execPath, [
  "--test",
  ...modules.map(name => join(output, `${name}.test.js`)),
], { stdio: "inherit" });

rmSync(output, { recursive: true, force: true });
process.exit(tests.status ?? 1);
