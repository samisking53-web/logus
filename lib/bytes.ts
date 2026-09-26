// 글자 수를 byte로 센다 (문자 메시지처럼): 영어·숫자·띄어쓰기·기호는 1byte, 한글 등 나머지는 2byte.
// 예: "맛집 추천!" = 맛(2)+집(2)+ (1)+추(2)+천(2)+!(1) = 10byte

function charBytes(char: string) {
  return (char.codePointAt(0) ?? 0) <= 0x7f ? 1 : 2;
}

export function countBytes(text: string) {
  let total = 0;
  for (const char of text) total += charBytes(char);
  return total;
}

// maxBytes를 넘는 뒷부분을 잘라낸다
export function cutToBytes(text: string, maxBytes: number) {
  let total = 0;
  let result = "";
  for (const char of text) {
    total += charBytes(char);
    if (total > maxBytes) break;
    result += char;
  }
  return result;
}
