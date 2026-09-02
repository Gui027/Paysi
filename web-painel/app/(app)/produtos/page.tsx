import { Suspense } from "react";
import { Skeleton } from "../../../components/ui";
import { ProdutosPage } from "./ProdutosPage";

export const metadata = { title: "Produtos" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando produtos" />}><ProdutosPage /></Suspense>;
}
