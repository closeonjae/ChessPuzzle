# PLAN.md

RESEARCH.md의 조사 결과를 바탕으로 확정한 구현 계획. (로그인 방식은 사용자 확인 완료: **RemoteAuthClient 기반 OAuth**)

## 1. 범위

- Wear OS **단독(standalone)** 앱 1개. 폰 컴패니언 앱 없음.
- 갤럭시 워치 8(블루투스 모델)에 ADB로 사이드로드해서 개인적으로 사용.
- 기능: Lichess OAuth 로그인 → 로그인한 계정의 퍼즐 레이팅에 맞는 퍼즐을 받아서 워치에서 풂 → 정답/오답을 Lichess에 보고해 레이팅 갱신 → 다음 퍼즐로 반복.
- 범위 밖(요청받지 않음, 구현하지 않음): 퍼즐 테마 선택 UI, 통계/대시보드 화면, 오프라인 캐시, 다국어, 워치 페이스/타일, 로그아웃 이외의 계정 관리.

## 2. 아키텍처 / 기술 스택

| 항목 | 선택 | 근거(RESEARCH.md 절) |
|---|---|---|
| 언어/UI | Kotlin + Jetpack Compose **for Wear OS** (`androidx.wear.compose:compose-material3`, `compose-foundation`, `compose-navigation`) | 2절 |
| 보조 라이브러리 | Horologist `compose-layout`(원형 화면 레이아웃), `network-awareness`(BT 프록시 연결 상태 감지) | 2절 |
| 네트워크 | OkHttp(또는 Ktor) + kotlinx.serialization, 표준 HTTP — 별도 프록시 설정 불필요 | 1절 |
| 인증 | `androidx.wear.phone.interactions.authentication.RemoteAuthClient` + PKCE(S256) | 3, 4절 |
| 체스 규칙 | `bhlangonijr/chesslib` (JitPack, Apache 2.0) | 8절 |
| 토큰 저장 | `androidx.security.crypto` EncryptedSharedPreferences (Wear OS에서 동작 확인 필요, 안 되면 일반 DataStore로 대체 — 구현 중 실기기 검증) | — |
| 좌표 입력 | `androidx.wear:wear-input:1.0.0` (`RemoteInputIntentHelper`) — 보드 우측 탭 → 워치 시스템 키보드로 SAN 형식(`Nc3` 등) 입력 | RESEARCH.md 10-A절 |
| 최소/타깃 SDK | minSdk 30(Wear OS 3 계열, RemoteAuthClient·Compose 지원 확보), targetSdk 36(Watch 8 실제 OS) | 1절 |
| 배포 | Gradle 빌드 → `adb install` 사이드로드. Play 스토어용 더미 폰 모듈 없음 | 2절 |

**미확정 값(사용자 확인 필요, 낮은 리스크라 기본값으로 진행하고 이견 있으면 변경)**:
- `applicationId` / Wear OS 패키지명: `com.closeonjae.chesspuzzle` (제안값)
- Lichess OAuth `client_id`: 패키지명과 동일하게 `com.closeonjae.chesspuzzle` 사용 (Lichess는 사전 등록이 없고 임의 문자열이면 되므로, redirect_uri에 이미 패키지명이 들어가는 `RemoteAuthClient` 구조와 통일)

## 3. 화면 구성 (상세 값은 DESIGN.md에서 확정)

1. **로그인 화면**: "Sign in with Lichess" 버튼 하나. 로그인 시도 중/실패 상태 표시. (영문 UI)
2. **퍼즐 화면** (메인 화면, 로그인 후 항상 여기로): 화면을 거의 가득 채우는 8×8 체스판(테두리·둥근 모서리 없음) + 상단 "White to move"/"Black to move" + 하단 레이팅 숫자 + 좌측 랭크 번호(8→1) + 우측 좌표 입력 탭(탭하면 시스템 키보드로 `e2e4` 형식 입력, RESEARCH.md 10-A절).
3. **로딩/에러 상태**: 퍼즐 요청 중 스피너, 네트워크 실패 시 상단에 에러 문구 + 하단에 "Retry" 탭 텍스트(빈 상태·로딩·오류를 화면 설계 단계에서 명시적으로 다룸 — DEVELOP.md 디자인 지침).

화면은 2개뿐이며 각 화면은 목적이 하나다(로그인 vs 풀이). 로그인은 앱 최초 실행 시 1회, 이후에는 토큰이 있으면 바로 퍼즐 화면으로 진입(입력 최소화 원칙).

## 4. 데이터 흐름

```
[앱 시작]
  └ 저장된 access_token 있음? ──아니오──▶ [로그인 화면]
        │                                    │ "로그인" 탭
        │                                    ▼
        │                         RemoteAuthClient.sendAuthorizationRequest(
        │                           authProviderUrl = lichess.org/oauth?...&code_challenge=...,
        │                           clientId = ...)
        │                                    │ (폰 브라우저에서 동의 → 콜백)
        │                                    ▼
        │                         POST lichess.org/api/token (code, code_verifier, redirect_uri)
        │                                    ▼
        │                         access_token 저장 (EncryptedSharedPreferences)
        └────────────────────────────────────┘
                    ▼
        [퍼즐 화면] GET /api/puzzle/batch/mix?nb=1 (Bearer token)
                    ▼
        game.pgn을 initialPly까지 재생(chesslib) → 시작 포지션 렌더링
                    ▼
        사용자가 기물 탭-선택 → 목적지 탭 → chesslib로 합법수 검증
                    ├─ solution[i]와 다른 합법수 ─▶ "오답" 표시, 되돌림
                    └─ solution[i]와 일치 ─▶ 보드 갱신 → (상대 응수 자동 재생) → 다음 사용자 차례
                    ▼ (마지막 수까지 완료)
        POST /api/puzzle/batch/mix (solutions: [{id, win: true, rated: true}])
                    ▼
        응답의 glicko.rating으로 레이팅 갱신 표시 → 다음 퍼즐 요청 반복
```

## 5. 주요 모듈

- `LichessAuthManager` — RemoteAuthClient 래핑, PKCE code_verifier/challenge 생성, 토큰 교환/저장/삭제.
- `LichessApiClient` — `/api/puzzle/batch/{angle}` GET/POST 래핑, 직렬화 모델.
- `PuzzleEngine` — chesslib 래핑: PGN 재생, 합법수 검증, solution 매칭, 상대 응수 자동 재생.
- `PuzzleViewModel` — 위 세 모듈을 조합한 화면 상태(StateFlow) 관리.
- `LoginScreen`, `PuzzleScreen` — Compose UI.
- `MoveInputLauncher` — `RemoteInputIntentHelper` 래핑(좌표 입력 탭 → 시스템 키보드 실행 → 결과 SAN 문자열을 `PuzzleEngine`(chesslib)으로 파싱/형식·합법성 검증).

## 6. 상충관계 (Trade-offs)

- **`/api/puzzle/batch/{angle}` (nb=1) vs `/api/puzzle/next`**: batch 쌍만 레이팅을 실제로 갱신함(RESEARCH.md 6절). `/next`가 더 단순하지만 "레이팅에 맞는 퍼즐"이라는 핵심 요구를 충족 못 해 batch를 선택. 대신 매 퍼즐마다 GET 1회 + 완료 시 POST 1회, 총 2회 네트워크 호출이 필요해짐(단순함보다 정확성 우선).
- **RemoteAuthClient 리스크**: 공식 API지만 에러 코드·필요 권한이 문서화되어 있지 않아 구현 중 실기기 테스트로 메꿔야 함. 대안(개인 토큰 수동 입력)이 더 견고하지만 사용자가 "OAuth 로그인"을 명시적으로 요청했고 이를 확인 후에도 유지하기로 함 — 실기기 검증 실패 시 개인 토큰 방식으로 폴백하는 것을 리스크로 남겨둠.
- **토큰 저장소**: EncryptedSharedPreferences가 Wear OS에서 미지원일 경우 평문 DataStore로 대체 — 개인 1인 사용 기기이므로 보안 요구가 낮아 폴백 허용.
- **로터리 입력 미사용**: 체스판 선택엔 부적합하다는 조사 결과에 따라 구현하지 않음(요청받지 않은 기능 추가 금지).
- **좌표 입력 = 시스템 키보드(`RemoteInputIntentHelper`) vs 커스텀 키패드**: 조사 결과 고정 형식 문자열엔 시스템 키보드의 자동완성/스와이프 타이핑이 오히려 오차를 만들 수 있어 커스텀 키패드가 더 안정적이라는 의견이 있었으나(RESEARCH.md 10-A절), 사용자가 "키보드를 열어서" 입력하는 방식을 명시적으로 요청했으므로 시스템 키보드 경로로 확정. 잘못된 형식 입력은 클라이언트에서 검증 후 재입력을 요청하는 것으로 리스크를 완화.
- **입력 표기법 = SAN(`Nc3`) vs UCI(`e2e4`)**: 퍼즐 API의 `solution`은 UCI 배열이라 UCI 입력이 비교가 더 직접적이지만, 사용자가 SAN 방식을 요청했으므로 SAN으로 확정. `PuzzleEngine`이 SAN 문자열을 chesslib로 현재 국면 기준 합법수로 해석한 뒤, 그 결과 수(출발/도착 칸)를 `solution`의 UCI 항목과 비교하는 한 단계가 추가됨(간단한 변환이라 큰 비용은 아님).

## 7. 테스트 전략

- `PuzzleEngine`(체스 로직)은 Android 기기 없이 순수 JVM 유닛 테스트로 검증 가능(`./gradlew test`): PGN+initialPly 재생 결과, 합법수 판정, solution 매칭, 오답 처리, 상대 응수 자동 재생 로직에 대해 테스트 작성 후 통과시킨다(DEVELOP.md 목표지향 원칙).
- `LichessApiClient`의 직렬화(요청/응답 JSON 파싱)도 실제 API 예시 응답으로 유닛 테스트.
- 로그인 플로우·UI는 실기기(워치) 수동 검증(개인 단일 기기 앱이라 자동화 UI 테스트는 범위 밖).

## 8. 체크리스트

- [x] Gradle 멀티모듈 프로젝트 스캐폴딩 (`:core` 순수 JVM + `:app` Wear OS Compose, 버전 카탈로그, standalone manifest, gradle wrapper)
- [x] `LichessApiClient` 모델/직렬화 + 유닛 테스트 (`./gradlew :core:test` 통과)
- [x] `PuzzleEngine`(chesslib 연동) + 유닛 테스트 (`./gradlew :core:test` 통과 — Fool's Mate/Ruy Lopez 실제 대국으로 검증)
- [x] `LichessAuthManager`(RemoteAuthClient + PKCE + 토큰 저장, DataStore)
- [x] `MoveInputLauncher`(RemoteInputIntentHelper — SAN 유효성 검증은 chesslib의 `doMove(String)`가 그대로 담당)
- [x] DESIGN.md 작성 (색/타이포/간격/컴포넌트, 빈·로딩·에러 상태, 접근성)
- [x] `LoginScreen` Compose 구현
- [x] `PuzzleScreen` Compose 구현(체스판 렌더링 + 탭 입력) — `./gradlew :app:assembleDebug` 성공, 로컬 Android SDK/Gradle로 실제 컴파일 검증
- [ ] 실기기(워치8) 사이드로드 후 로그인 흐름 검증 — **이 환경엔 실제 워치가 없어 미수행**. 특히 `RemoteAuthClient`의 에러 코드 체계는 문서화되어 있지 않아(RESEARCH.md 4절) 실기기에서만 확인 가능.
- [ ] 실기기 퍼즐 풀이 전체 루프 검증(정답/오답/레이팅 갱신)
- [x] Git 커밋 + main 브랜치 push

### 구현 중 확정된 사항 (계획 대비 변경/구체화)

- Gradle/AGP/Kotlin/각 라이브러리 버전은 추측 대신 Google Maven·Maven Central·JitPack에서 실제 조회해 확정(2026-08 시점 최신 안정 버전 — `gradle/libs.versions.toml` 참고). **AGP 9부터 `org.jetbrains.kotlin.android` 플러그인이 AGP에 내장되어 별도 적용이 오류**라는 점 등, 최근 툴체인 변경 사항을 빌드 실패로 실제 확인하며 반영.
- `androidx.wear.phone.interactions.authentication`(RemoteAuthClient 등)은 `androidx.wear:wear-input`이 아니라 별도 아티팩트 **`androidx.wear:wear-phone-interactions`**에 있음 — RESEARCH.md 4절 조사 당시 놓쳤던 부분으로, 실제 컴파일 오류로 발견해 추가.
- `PuzzleEngine`에서 좌표(탭) 기반 이동은 `board.doMove(Move)`가 아니라 `board.doMove(Move, fullValidation = true)`를 써야 함 — 2-인자 버전은 유닛 테스트로 실제로 상대 기물을 규칙 위반으로 움직이는 사례를 잡아냄.
- `PuzzleBatchSolveRequest` 직렬화 시 `encodeDefaults = true` 필요 — 기본값(`rated = true`)이 요청 JSON에서 생략되는 걸 유닛 테스트가 잡아냄.
- **PuzzleScreen 시각 요소 중 일부는 이번 1차 구현에서 의도적으로 보류**(실기기 없이는 시각 검증이 불가능한 항목 위주): 기물 글리프의 외곽선(대비용 스트로크), 방금 둔 수 칸 강조 애니메이션. 상태 텍스트(정답/오답/레이팅)는 모두 구현됨 — 기능은 완전하고 일부 장식만 남은 상태.
