import { rmSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import { spawnSync } from "node:child_process";

const panelRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const output = join(panelRoot, ".test-dist");
const tsc = join(panelRoot, "..", "node_modules", "typescript", "bin", "tsc");

const modules = ["produtos", "moeda", "dashboard", "aparencia", "assets"];

rmSync(output, { recursive: true, force: true });

const compile = spawnSync(process.execPath, [
  tsc,
  join(panelRoot, "lib", "api.ts"),
  ...modules.flatMap(name => [join(panelRoot, "lib", `${name}.ts`), join(panelRoot, "lib", `${name}.test.ts`)]),
  "--ignoreConfig",
  "--outDir", output,
  "--module", "commonjs",
  "--target", "es2022",
  "--types", "node",
  "--esModuleInterop",
  "--skipLibCheck",
], { stdio: "inherit" });

if (compile.status !== 0) {
  rmSync(output, { recursive: true, force: true });
  process.exit(compile.status ?? 1);
}

const tests = spawnSync(process.execPath, ["--test", ...modules.map(name => join(output, `${name}.test.js`))], { stdio: "inherit" });
rmSync(output, { recursive: true, force: true });
process.exit(tests.status ?? 1);
