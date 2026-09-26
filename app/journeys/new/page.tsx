// S02 새 여정 만들기
import type { Metadata } from "next";
import { NewJourneyForm } from "@/components/journey/new-journey-form";

export const metadata: Metadata = {
  title: "새 여정 만들기",
};

export default function NewJourneyPage() {
  return <NewJourneyForm />;
}
