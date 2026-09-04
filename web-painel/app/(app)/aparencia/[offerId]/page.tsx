import { AparenciaForm } from "./AparenciaForm";

export const metadata = { title: "Aparência do checkout" };

export default async function Page({ params }: { params: Promise<{ offerId: string }> }) {
  const { offerId } = await params;
  return <AparenciaForm offerId={offerId} />;
}
