import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { AssinaturasPage } from "./AssinaturasPage";

export const metadata = { title: "Assinaturas" };

export default function Page() {
  return (
    <Suspense fallback={<Skeleton label="Carregando assinaturas" />}>
      <AssinaturasPage />
    </Suspense>
  );
}
