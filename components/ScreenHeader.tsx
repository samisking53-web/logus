// 화면 맨 위 제목과 짧은 설명
type Props = {
  title: string;
  description?: string;
};

export default function ScreenHeader({ title, description }: Props) {
  return (
    <header className="mb-6">
      <p className="text-sm font-bold tracking-wide text-brand">LOG US</p>
      <h1 className="mt-1 text-2xl font-bold">{title}</h1>
      {description && <p className="mt-2 text-muted">{description}</p>}
    </header>
  );
}
