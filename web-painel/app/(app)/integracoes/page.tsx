import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { IntegracoesPage } from "./IntegracoesPage";

export const metadata = { title: "Integrações" };

export default function Page() {
  return (
    <Suspense fallback={<Skeleton label="Carregando integrações" />}>
      <IntegracoesPage />
    </Suspense>
  );
}
