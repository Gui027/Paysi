import Link from "next/link";
import { hubItems } from "../../../lib/relatorios";

export const metadata = { title: "Relatórios" };

const glyphs: Record<string, React.ReactNode> = {
  "co-producao-recebida": <><circle cx="9" cy="8" r="3.2" /><path d="M3 19c.5-3.6 3-5.5 6-5.5s5.500 1.900 6 5.500" /><circle cx="17" cy="9" r="2.500" /><path d="M16 14c2.700 0 4.500 1.600 5 4.500" /></>,
  produto: <><path d="M3 17 9 11l4 4 8-9" /><path d="M15 6h6v6" /></>,
  abandonadas: <><path d="M3 4h2.500l2 11h10l2-8H7" /><circle cx="9.500" cy="19" r="1.400" /><circle cx="16.500" cy="19" r="1.400" /></>,
  alunos: <><circle cx="12" cy="12" r="9" /><path d="M8.500 14.500c.9 1.200 2 1.800 3.500 1.800s2.600-.6 3.500-1.800" /><path d="M9 9.500h.01M15 9.500h.01" /></>,
  afiliado: <><circle cx="12" cy="7.500" r="3" /><circle cx="5.500" cy="11" r="2.200" /><circle cx="18.500" cy="11" r="2.200" /><path d="M6.500 20c.4-3.400 2.500-5.200 5.500-5.200s5.100 1.800 5.500 5.200" /></>,
  "saldo-receber": <><rect x="3" y="6" width="15" height="11" rx="2" /><path d="M7 20h13a1 1 0 0 0 1-1V10" /><circle cx="10.500" cy="11.500" r="2" /></>,
  "recebiveis-cartao": <><rect x="3" y="5" width="18" height="14" rx="3" /><path d="M3 10h18M7 15h3" /></>,
  "assinaturas-canceladas": <><circle cx="12" cy="12" r="9" /><rect x="9" y="9" width="6" height="6" rx="1" /></>,
  "agente-recuperador": <><path d="M4 5h11a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H9l-4 3v-3H4a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z" /><path d="M20 9.500v6a2 2 0 0 1-2 2h-1" /></>,
};

export default function Page() {
  return <div className="rel">
    <h1>Relatórios</h1>
    <ul className="rel-hub">
      {hubItems.map(item => <li key={item.id}>
        <Link href={`/relatorios/${item.id}`} className="rel-hub-card">
          <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{glyphs[item.id]}</svg>
          <span>{item.label}</span>
        </Link>
      </li>)}
    </ul>
  </div>;
}
