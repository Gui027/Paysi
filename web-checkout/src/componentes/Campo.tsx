import { InputHTMLAttributes } from "react";

export function Campo({ id, label, full, error, ...props }: InputHTMLAttributes<HTMLInputElement> & {
  id: string;
  label: string;
  full?: boolean;
  error?: string;
}) {
  return (
    <label className={full ? "full" : undefined} htmlFor={id}>
      {label}
      <input id={id} aria-invalid={Boolean(error)} aria-describedby={error ? `${id}-erro` : undefined} {...props} />
      {error && <small className="field-error" id={`${id}-erro`}>{error}</small>}
    </label>
  );
}
