import { Suspense } from "react";
import { Skeleton } from "../../../../../components/ui";
import { LogsPage } from "./LogsPage";

export const metadata = { title: "Logs do webhook" };

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <Suspense fallback={<Skeleton label="Carregando logs" />}><LogsPage webhookId={id} /></Suspense>;
}
