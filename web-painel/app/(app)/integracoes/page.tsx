import { redirect } from "next/navigation";

/** Os webhooks agora ficam em Apps → Webhooks. */
export default function Page() {
  redirect("/apps/webhooks");
}
