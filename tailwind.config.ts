import type { Config } from "tailwindcss";
import tailwindcssAnimate from "tailwindcss-animate";

// app/globals.css의 색 변수(R G B 숫자)를 Tailwind 색으로 바꾼다. bg-brand/50처럼 투명도도 붙일 수 있다.
const color = (name: string) => `rgb(var(--${name}) / <alpha-value>)`;

export default {
  darkMode: ["class"],
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      // 색 이름과 값은 app/globals.css의 팔레트에서 가져온다. 새 색이 필요하면 거기에 먼저 추가한다.
      colors: {
        background: color("background"),
        foreground: color("foreground"),
        card: {
          DEFAULT: color("card"),
          foreground: color("card-foreground"),
        },
        popover: {
          DEFAULT: color("popover"),
          foreground: color("popover-foreground"),
        },
        // 템플릿 버튼이 쓰는 primary는 브랜드 프라이머리와 같은 색이다
        primary: {
          DEFAULT: color("brand"),
          foreground: color("brand-foreground"),
        },
        secondary: {
          DEFAULT: color("secondary"),
          foreground: color("secondary-foreground"),
        },
        muted: {
          DEFAULT: color("muted"),
          foreground: color("muted-foreground"),
        },
        weak: color("weak-foreground"),
        accent: {
          DEFAULT: color("accent"),
          foreground: color("accent-foreground"),
        },
        destructive: {
          DEFAULT: color("destructive"),
          foreground: color("destructive-foreground"),
        },
        success: color("success"),
        warning: color("warning"),
        border: color("border"),
        input: color("input"),
        ring: color("ring"),
        brand: {
          DEFAULT: color("brand"),
          pressed: color("brand-pressed"),
          strong: color("brand-strong"),
          soft: color("brand-soft"),
          line: color("brand-line"),
          foreground: color("brand-foreground"),
        },
        reward: {
          DEFAULT: color("reward"),
          strong: color("reward-strong"),
          soft: color("reward-soft"),
        },
        member: {
          "1": color("member-1"),
          "2": color("member-2"),
          "3": color("member-3"),
          "4": color("member-4"),
        },
      },
      borderRadius: {
        lg: "var(--radius)",
        md: "calc(var(--radius) - 2px)",
        sm: "calc(var(--radius) - 4px)",
      },
    },
  },
  plugins: [tailwindcssAnimate],
} satisfies Config;
