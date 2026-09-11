import { Suspense } from "react";
import { Skeleton } from "../../../../components/ui";
import { SaquePage } from "./SaquePage";

export const metadata = { title: "Solicitar saque" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando saque" />}><SaquePage /></Suspense>;
}
