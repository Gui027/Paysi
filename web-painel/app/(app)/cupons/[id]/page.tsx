import { CouponForm } from "../CouponForm";

export const metadata = { title: "Editar cupom" };

export default async function Page({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <CouponForm couponId={id} />;
}
