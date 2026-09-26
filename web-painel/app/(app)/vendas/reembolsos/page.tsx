import { Suspense } from "react";
import { Skeleton } from "../../../../components/ui";
import { ReembolsosPage } from "./ReembolsosPage";

export const metadata = { title: "Reembolsos" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando reembolsos" />}><ReembolsosPage /></Suspense>;
}
