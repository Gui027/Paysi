import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { SaldoPage } from "./SaldoPage";

export const metadata = { title: "Saldo e extrato" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando saldo" />}><SaldoPage /></Suspense>;
}
