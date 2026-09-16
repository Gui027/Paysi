import { Suspense } from "react";
import { Skeleton } from "../../../../components/ui";
import { OrderDetailPage } from "./OrderDetailPage";

export const metadata = { title: "Detalhe da Venda" };

export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <Suspense fallback={<Skeleton label="Carregando detalhes da venda" />}>
      <OrderDetailPage orderId={id} />
    </Suspense>
  );
}
