import { PersonType } from "../lib/checkout";
import { ChaveCampo, rotuloCampo } from "../lib/camposComprador";
import { formatarDocumento } from "../lib/documento";
import { formatarCep } from "../lib/mascaras";
import { Campo } from "./Campo";

const FULL_WIDTH: ReadonlySet<ChaveCampo> = new Set(["name", "email", "legalName", "address.street"]);

export function CamposComprador({ campos, values, errors, personType, onChange }: {
  campos: ChaveCampo[];
  values: Record<string, string>;
  errors: Record<string, string | undefined>;
  personType: PersonType;
  onChange: (key: ChaveCampo, value: string) => void;
}) {
  return (
    <div className="field-grid">
      {campos.map(key => (
        <Campo
          key={key}
          id={key}
          label={rotuloCampo(key, personType)}
          full={FULL_WIDTH.has(key)}
          error={errors[key]}
          value={values[key] ?? ""}
          maxLength={key === "address.state" ? 2 : undefined}
          inputMode={key === "taxId" || key === "address.zipCode" ? "numeric" : undefined}
          autoComplete={key === "name" ? "name" : key === "email" ? "email" : "off"}
          onChange={event => onChange(key, transformar(key, event.target.value, personType))}
        />
      ))}
    </div>
  );
}

function transformar(key: ChaveCampo, value: string, personType: PersonType): string {
  if (key === "taxId") return formatarDocumento(value, personType);
  if (key === "address.zipCode") return formatarCep(value);
  if (key === "address.state") return value.toUpperCase();
  return value;
}
