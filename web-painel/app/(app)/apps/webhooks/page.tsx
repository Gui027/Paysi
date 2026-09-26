import { Suspense } from "react";
import { Skeleton } from "../../../../components/ui";
import { WebhooksPage } from "./WebhooksPage";

export const metadata = { title: "Webhooks" };

export default function Page() {
  return <Suspense fallback={<Skeleton label="Carregando webhooks" />}><WebhooksPage /></Suspense>;
}
