import { Suspense } from "react";
import { Skeleton } from "../../../../components/ui";
import { ConviteAfiliado } from "./ConviteAfiliado";

export const metadata = { title: "Convite de afiliado" };

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <Suspense fallback={<Skeleton label="Carregando convite" />}><ConviteAfiliado productId={id} /></Suspense>;
}
