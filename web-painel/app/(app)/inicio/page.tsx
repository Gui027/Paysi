import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { InicioPage } from "./InicioPage";

export const metadata = { title: "Dashboard" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando dashboard" />}><InicioPage /></Suspense>;
}
