import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { FinanceiroPage } from "./FinanceiroPage";

export const metadata = { title: "Financeiro" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando financeiro" />}><FinanceiroPage /></Suspense>;
}
