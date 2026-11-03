type ActivityProps = {
  title: string;
  meta: string;
  tone: string;
};

export function Activity({ title, meta, tone }: ActivityProps) {
  return (
    <div className="activity-row">
      <span className={`activity-dot ${tone}`} />
      <div>
        <strong>{title}</strong>
        <span>{meta}</span>
      </div>
    </div>
  );
}
