import { Suspense } from "react";
import { Skeleton } from "../../../../components/ui";
import { ProdutoDetalhe } from "./ProdutoDetalhe";

export const metadata = { title: "Editar produto" };

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <Suspense fallback={<Skeleton label="Carregando produto" />}><ProdutoDetalhe productId={id} /></Suspense>;
}
