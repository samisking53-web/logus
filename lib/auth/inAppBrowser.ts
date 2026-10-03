// 카카오톡·인스타그램 같은 앱 안의 브라우저(인앱 브라우저)에서는 구글 로그인이 막힌다(구글 정책).
// 감지해서 '브라우저로 열기' 안내를 보여 준다(CLAUDE.md "알려진 함정").
export type InAppBrowser = { name: string; isAndroid: boolean } | null;

const IN_APP_PATTERNS: [RegExp, string][] = [
  [/KAKAOTALK/i, "카카오톡"],
  [/Instagram/i, "인스타그램"],
  [/FBAN|FBAV|FB_IAB/i, "페이스북"],
  [/Line\//i, "라인"],
  [/NAVER\(inapp/i, "네이버"],
  [/DaumApps/i, "다음"],
  [/; wv\)/i, "앱"], // 그 밖의 안드로이드 웹뷰
];

export function detectInAppBrowser(userAgent: string): InAppBrowser {
  const match = IN_APP_PATTERNS.find(([pattern]) => pattern.test(userAgent));
  return match ? { name: match[1], isAndroid: /Android/i.test(userAgent) } : null;
}

/** 안드로이드에서 지금 주소를 크롬으로 여는 링크 */
export function chromeIntentUrl(href: string): string {
  const url = new URL(href);
  return `intent://${url.host}${url.pathname}${url.search}#Intent;scheme=https;package=com.android.chrome;end`;
}
