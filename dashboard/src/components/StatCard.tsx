import { ReactNode } from "react";

interface StatCardProps {
  label: string;
  value: ReactNode;
  accent?: "default" | "warm" | "cool";
  detail?: ReactNode;
}

export function StatCard({
  label,
  value,
  accent = "default",
  detail
}: StatCardProps) {
  return (
    <article className={`stat-card accent-${accent}`}>
      <p className="stat-label">{label}</p>
      <p className="stat-value">{value}</p>
      {detail ? <p className="stat-detail">{detail}</p> : null}
    </article>
  );
}
