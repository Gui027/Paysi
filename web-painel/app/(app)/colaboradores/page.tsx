import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { ColaboradoresPage } from "./ColaboradoresPage";

export const metadata = { title: "Colaboradores" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando colaboradores" />}><ColaboradoresPage /></Suspense>;
}
