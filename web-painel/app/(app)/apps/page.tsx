import Link from "next/link";

export const metadata = { title: "Apps" };

/** Integrações disponíveis na Paysi: por enquanto, Webhooks e API. */
export default function Page() {
  return <div className="rel">
    <h1>Apps</h1>
    <ul className="apps-grid">
      <li><Link href="/apps/webhooks" className="apps-card">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.700" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><circle cx="12" cy="6" r="2.500" /><circle cx="6" cy="17" r="2.500" /><circle cx="18" cy="17" r="2.500" /><path d="M12 8.500 8 15M9 17h6M13.500 8.500 16.500 14.500" /></svg>
        <span>Webhooks</span></Link></li>
      <li><Link href="/apps/api" className="apps-card">
        <svg width="40" height="40" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><rect x="2" y="3" width="20" height="18" rx="4" fill="currentColor" /><path d="m7 9 3 3-3 3M12 16h5" fill="none" stroke="#fff" strokeWidth="1.800" strokeLinecap="round" strokeLinejoin="round" /></svg>
        <span>API</span></Link></li>
    </ul>
  </div>;
}
