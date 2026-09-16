import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { PlanoPage } from "./PlanoPage";

export const metadata = { title: "Plano comercial" };

export default function Page() {
  return (
    <Suspense fallback={<Skeleton label="Carregando plano comercial" />}>
      <PlanoPage />
    </Suspense>
  );
}
