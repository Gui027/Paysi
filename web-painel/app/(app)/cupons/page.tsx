import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { CuponsPage } from "./CuponsPage";

export const metadata = { title: "Cupons" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando cupons" />}><CuponsPage /></Suspense>;
}
