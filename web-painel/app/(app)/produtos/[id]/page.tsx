import { ProdutoDetalhe } from "./ProdutoDetalhe";

export const metadata = { title: "Detalhe do produto" };

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ProdutoDetalhe productId={id} />;
}
