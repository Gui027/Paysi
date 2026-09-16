import { Suspense } from "react";
import { Skeleton } from "../../../../components/ui";
import { SubscriptionDetailPage } from "./SubscriptionDetailPage";

export const metadata = { title: "Detalhe da Assinatura" };

export default async function Page({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <Suspense fallback={<Skeleton label="Carregando detalhes da assinatura" />}>
      <SubscriptionDetailPage subscriptionId={id} />
    </Suspense>
  );
}
