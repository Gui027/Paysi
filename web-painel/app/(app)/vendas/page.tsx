import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { VendasPage } from "./VendasPage";

export const metadata = { title: "Vendas e Cobranças" };

export default function Page() {
  return (
    <Suspense fallback={<Skeleton label="Carregando vendas" />}>
      <VendasPage />
    </Suspense>
  );
}
