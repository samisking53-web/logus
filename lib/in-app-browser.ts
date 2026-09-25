// 카카오톡·인스타그램 같은 앱 안의 브라우저(인앱 브라우저)인지 확인한다.
// 구글은 인앱 브라우저에서 로그인을 막기 때문에, 이때는 다른 브라우저로 열도록 안내한다.
const IN_APP_BROWSER_PATTERNS = [
  /KAKAOTALK/i, // 카카오톡
  /Instagram/i, // 인스타그램
  /FBAN|FBAV/i, // 페이스북
  /NAVER\(inapp/i, // 네이버 앱
  /\bLine\//i, // 라인
  /everytimeApp/i, // 에브리타임
  /DaumApps/i, // 다음 앱
];

export function isInAppBrowser(userAgent: string) {
  return IN_APP_BROWSER_PATTERNS.some((pattern) => pattern.test(userAgent));
}

export function isKakaoTalkBrowser(userAgent: string) {
  return /KAKAOTALK/i.test(userAgent);
}
