import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { InicioPage } from "./InicioPage";

export const metadata = { title: "Início" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando início" />}><InicioPage /></Suspense>;
}
