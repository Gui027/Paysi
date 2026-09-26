import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { VitrinePage } from "./VitrinePage";

export const metadata = { title: "Marketplace" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando marketplace" />}><VitrinePage /></Suspense>;
}
