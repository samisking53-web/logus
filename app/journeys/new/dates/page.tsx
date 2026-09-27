// S03 여행 기간 달력
import type { Metadata } from "next";
import { DateRangePicker } from "@/components/journey/date-range-picker";

export const metadata: Metadata = {
  title: "여행 기간 선택",
};

export default function JourneyDatesPage() {
  return <DateRangePicker />;
}
