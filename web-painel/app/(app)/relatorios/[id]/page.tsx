import { Suspense } from "react";
import { Skeleton } from "../../../../components/ui";
import { RelatorioPage } from "../RelatorioPage";

export const metadata = { title: "Relatórios" };

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <Suspense fallback={<Skeleton label="Carregando relatório" />}><RelatorioPage routeId={id} /></Suspense>;
}
