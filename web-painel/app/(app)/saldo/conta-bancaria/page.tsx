import { Suspense } from "react";
import { Skeleton } from "../../../../components/ui";
import { BankAccountPage } from "./BankAccountPage";

export const metadata = { title: "Conta bancária" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando conta bancária" />}><BankAccountPage /></Suspense>;
}
