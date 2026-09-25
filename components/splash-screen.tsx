// 앱 시작화면 (스토리보드 1쪽 '앱 시작화면')
// 앱을 열면 LOG EARTH 화면을 1.25초 보여준 뒤 0.4초에 걸쳐 사라지고, 그 아래의 홈이 보인다.
// 같은 창에서 새로고침하거나 탭을 옮길 때는 다시 보여주지 않는다.
// 보여주는 시간과 사라지는 효과는 app/globals.css의 .splash-screen에 있다.

// 시작화면을 봤다는 표시를 sessionStorage에 이 이름으로 남긴다.
// sessionStorage는 앱(브라우저 탭)을 닫으면 지워지므로, 앱을 새로 열면 시작화면이 다시 나온다.
const SEEN_KEY = "logus-splash-seen";

// 화면을 그리기 전에 실행되는 짧은 스크립트.
// 이 창에서 이미 봤으면 <html>에 splash-seen 클래스를 붙여 시작화면을 처음부터 숨기고,
// 처음이면 봤다고 표시만 해 둔다. (저장소를 못 쓰는 브라우저에서는 매번 보여준다)
const checkSeenScript = `try{if(sessionStorage.getItem("${SEEN_KEY}")){document.documentElement.classList.add("splash-seen")}else{sessionStorage.setItem("${SEEN_KEY}","1")}}catch(e){}`;

// app/layout.tsx의 <body> 맨 앞에 둔다.
// 시작화면보다 먼저 실행되어야 새로고침할 때 시작화면이 깜빡이지 않는다.
export function SplashScreenScript() {
  return <script dangerouslySetInnerHTML={{ __html: checkSeenScript }} />;
}

// app/(tabs)/layout.tsx에 둔다. 하단 탭 화면(홈/탐색/마이로그)을 처음 열 때 화면 전체를 덮는다.
export function SplashScreen() {
  return (
    <div
      // 꾸미는 화면이라 화면 읽기 프로그램(스크린 리더)은 건너뛰게 한다
      aria-hidden="true"
      className="splash-screen fixed inset-0 z-50 flex items-center justify-center bg-brand-soft"
    >
      <p className="text-4xl font-extrabold tracking-tight text-brand">
        LOG EARTH
      </p>
    </div>
  );
}
