import { Suspense } from "react";
import { Skeleton } from "../../../../components/ui";
import { ApiPage } from "./ApiPage";

export const metadata = { title: "API" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando API Keys" />}><ApiPage /></Suspense>;
}
