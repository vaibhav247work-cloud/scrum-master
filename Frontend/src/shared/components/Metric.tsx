type MetricProps = {
  label: string;
  value: string;
  meta: string;
  tone: string;
};

export function Metric({ label, value, meta, tone }: MetricProps) {
  return (
    <div className={`metric-card ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{meta}</small>
    </div>
  );
}
