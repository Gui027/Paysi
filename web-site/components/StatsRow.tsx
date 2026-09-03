import { IconChartUp, IconZap } from "./icons";
import { StaggerGroup, StaggerItem } from "./Reveal";

const stats = [
  {
    icon: <IconChartUp size={20} />,
    value: "até 50%",
    label: "Ajudamos nossos clientes a aumentar a taxa de conversão do checkout.",
  },
  {
    icon: <IconZap size={20} />,
    value: "+52%",
    label: "Seu checkout com a Paysi é muito mais rápido que um checkout tradicional.",
  },
];

export function StatsRow() {
  return (
    <div className="site-container">
      <StaggerGroup className="stats-row">
        {stats.map((stat) => (
          <StaggerItem key={stat.value} className="stat-card">
            <div>
              <div className="value">{stat.value}</div>
              <p className="label">{stat.label}</p>
            </div>
            <span className="icon-badge">{stat.icon}</span>
          </StaggerItem>
        ))}
      </StaggerGroup>
    </div>
  );
}
