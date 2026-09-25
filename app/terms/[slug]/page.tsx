// 약관 보기 (스토리보드 번호 없음: 가입 1단계에서 › 를 누르면 나오는 화면)
// 약관 본문은 아직 없다. 확정되면 이 화면에 본문을 넣고 lib/terms.ts의 TERMS_VERSION을 올린다
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { BackButton } from "@/components/back-button";
import { TERMS_DOCUMENTS, TERMS_VERSION, type TermsSlug } from "@/lib/terms";

export function generateStaticParams() {
  return Object.keys(TERMS_DOCUMENTS).map((slug) => ({ slug }));
}

export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params;
  return { title: TERMS_DOCUMENTS[slug as TermsSlug]?.title ?? "약관" };
}

export default async function TermsPage({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const doc = TERMS_DOCUMENTS[slug as TermsSlug];
  if (!doc) notFound();

  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[430px] flex-col break-keep px-5 pb-[calc(env(safe-area-inset-bottom)+1rem)] pt-[env(safe-area-inset-top)]">
      <header className="flex h-14 items-center">
        <BackButton fallbackHref="/signup/terms" />
      </header>
      <h1 className="mt-4 text-2xl font-bold">{doc.title}</h1>
      <p className="mt-2 text-sm text-muted-foreground">버전 {TERMS_VERSION} (초안)</p>
      <div className="mt-6 rounded-2xl border bg-card p-5 text-sm leading-relaxed">
        약관 본문을 준비하고 있어요. 내용이 확정되면 이 자리에 들어갑니다.
      </div>
    </main>
  );
}
