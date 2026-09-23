import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { PerfilFiscal } from "./PerfilFiscal";

export const metadata = { title: "Perfil fiscal" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando perfil fiscal" />}><PerfilFiscal /></Suspense>;
}
