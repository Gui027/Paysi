import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { Verificacao } from "./Verificacao";

export const metadata = { title: "Verificação de identidade" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando verificação de identidade" />}><Verificacao /></Suspense>;
}
