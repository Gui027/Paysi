import { ProductForm } from "../ProductForm";

export const metadata = { title: "Novo produto" };

export default function Page() {
  return <ProductForm mode="create" />;
}
