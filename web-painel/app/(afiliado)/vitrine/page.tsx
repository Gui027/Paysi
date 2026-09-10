import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { VitrinePage } from "./VitrinePage";

export const metadata = { title: "Vitrine de afiliação" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando vitrine" />}><VitrinePage /></Suspense>;
}
