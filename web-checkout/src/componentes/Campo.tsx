import { InputHTMLAttributes } from "react";

export function Campo({ id, label, full, error, hideLabel = false, ...props }: InputHTMLAttributes<HTMLInputElement> & {
  id: string;
  label: string;
  full?: boolean;
  error?: string;
  hideLabel?: boolean;
}) {
  return (
    <label className={full ? "full" : undefined} htmlFor={id}>
      <span className={hideLabel ? "sr-only" : undefined}>{label}</span>
      <input id={id} aria-invalid={Boolean(error)} aria-describedby={error ? `${id}-erro` : undefined} {...props} />
      {error && <small className="field-error" id={`${id}-erro`}>{error}</small>}
    </label>
  );
}
