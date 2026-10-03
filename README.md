# LOG US (로그어스)

여정(여행·행사) 단위로 친구·연인·가족이 사진·영상·글을 함께 기록하고, 추억 지도와 DAY RECAP 영상으로 다시 보는 **안드로이드 앱**입니다. 대학 캡스톤 프로젝트이고 백엔드는 Firebase(`logus-80f21`)입니다.

## 폴더 구조
| 폴더·파일 | 내용 |
|---|---|
| `android/` | 안드로이드 앱(Kotlin + Jetpack Compose). Android Studio 로 **이 폴더**를 연다 → [android/README.md](android/README.md) |
| `functions/` | Cloud Functions(서버 함수, TypeScript) |
| `firestore.rules`, `storage.rules`, `firestore.indexes.json` | Firebase 보안 규칙·색인 |
| `tests/rules/` | 보안 규칙·서버 함수 테스트(`npm run test:rules`) |
| `docs/login-setup.txt` | 구글 로그인·Firebase 콘솔 설정 순서 |
| `docs-storyboard-v4.pdf` | 화면 스토리보드 |
| `CLAUDE.md` | 팀 개발 규칙(데이터 모델·보안 규칙·화면 번호) |

## 자주 쓰는 명령
```bash
# 보안 규칙 테스트 (처음 한 번 npm install, npm --prefix functions install)
npm run test:rules

# 서버 함수 빌드
npm --prefix functions run build

# 배포 (사람이 승인한 뒤 로컬에서, 대상은 --only 로)
firebase deploy --only firestore:rules,storage
```
