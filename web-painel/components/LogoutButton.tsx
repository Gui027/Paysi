"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { logout } from "../lib/sessao";

export function LogoutButton() {
  const router = useRouter();
  const [pending, setPending] = useState(false);

  async function leave() {
    if (pending) return;
    setPending(true);
    try {
      await logout();
    } finally {
      router.replace("/entrar");
      router.refresh();
    }
  }

  return <button className="sidebar-logout" type="button" onClick={leave} disabled={pending}>{pending ? "Saindo…" : "Sair"}</button>;
}
