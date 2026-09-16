import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { MeusLinksPage } from "./MeusLinksPage";

export const metadata = { title: "Meus links e comissões" };

export default function Page() {
  return (
    <Suspense fallback={<Skeleton label="Carregando seus links e comissões" />}>
      <MeusLinksPage />
    </Suspense>
  );
}
