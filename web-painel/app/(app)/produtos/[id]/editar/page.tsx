import { ProductForm } from "../../ProductForm";

export const metadata = { title: "Editar produto" };

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <ProductForm mode="edit" productId={id} />;
}
