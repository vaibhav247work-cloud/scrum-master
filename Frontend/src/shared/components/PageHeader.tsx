type PageHeaderProps = {
  eyebrow: string;
  title: string;
  description: string;
  action?: string;
  onAction?: () => void;
};

export function PageHeader({ eyebrow, title, description, action, onAction }: PageHeaderProps) {
  return (
    <div className="page-header">
      <div>
        <div className="eyebrow">{eyebrow}</div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action && <button className="primary-button" onClick={onAction}>＋ {action}</button>}
    </div>
  );
}
