import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { AfiliadosPage } from "./AfiliadosPage";

export const metadata = { title: "Afiliados" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando afiliados" />}><AfiliadosPage /></Suspense>;
}
