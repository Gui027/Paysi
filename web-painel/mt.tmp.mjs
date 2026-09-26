import fs from "fs";
const rep = (file, a, b) => { let s = fs.readFileSync(file, "utf8").replace(/\r\n/g, "\n"); if (!s.includes(a)) throw new Error(file + ": não achei " + a.slice(0, 70)); fs.writeFileSync(file, s.replace(a, () => b)); };
const p = "app/(app)/saldo/FinanceiroPage.tsx";
rep(p, `    <SaqueDialog open={saqueOpen} overview={overview} onClose={() => setSaqueOpen(false)} onDone={afterPayout} />
    <CnpjDialog open={cnpjOpen} overview={overview} onClose={() => setCnpjOpen(false)} onNeedMfa={() => { setCnpjOpen(false); setMfaSetupOpen(true); }} onDone={afterCompany} />
    <MfaSetupDialog open={mfaSetupOpen} onCancel={() => setMfaSetupOpen(false)} onEnabled={() => { setMfaSetupOpen(false); setNotice("Verificação em duas etapas ativada."); void load(); }} />`,
`    {/* Janelas só existem enquanto estão abertas: fechadas, não deixam campos escondidos na página. */}
    {saqueOpen && <SaqueDialog open overview={overview} onClose={() => setSaqueOpen(false)} onDone={afterPayout} />}
    {cnpjOpen && <CnpjDialog open overview={overview} onClose={() => setCnpjOpen(false)} onNeedMfa={() => { setCnpjOpen(false); setMfaSetupOpen(true); }} onDone={afterCompany} />}
    {mfaSetupOpen && <MfaSetupDialog open onCancel={() => setMfaSetupOpen(false)} onEnabled={() => { setMfaSetupOpen(false); setNotice("Verificação em duas etapas ativada."); void load(); }} />}`);
const t = "e2e/financeiro.spec.ts";
rep(t, "getByText(/saque mínimo é de R\$ 2,00/)", "getByText(/saque mínimo é de R\$\s2,00/)");
rep(t, "getByText(/Pix:.*3,99%.*R\$ 2,00 por venda aprovada/)", "getByText(/Pix:.*3,99%.*R\$\s2,00 por venda aprovada/)");
