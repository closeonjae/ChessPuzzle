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

- **`/api/puzzle/batch/{angle}` (nb=1) vs `/api/puzzle/next`**: batch 쌍만 레이팅을 실제로 갱신함(RESEARCH.md 6절). `/next`가 더 단순하지만 "레이팅에 맞는 퍼즐"이라는 핵심 요구를 충족 못 해 batch를 선택. 최초 퍼즐 로딩은 여전히 GET 1회가 필요하지만, 그 이후 "정답 완료 → 다음 퍼즐"은 POST 한 번(`nb=1`)으로 끝난다 — 정답 보고와 다음 퍼즐 배치 요청을 한 응답에 묶어 받을 수 있다는 걸 뒤늦게 활용했다(아래 사용자 리포트 항목 참고). 원래는 POST(`nb=0`)와 별도 GET을 매번 두 번 부르는 구조였다.
  > **버그 수정(사용자 리포트 — "퍼즐 풀고 난 이후에 다시 똑같은 퍼즐을 풀도록 롤백되는 문제")**: 다음 퍼즐을 백그라운드로 미리 불러오도록 바꾸면서(퍼즐 완료 화면을 탭하기 전까지 유지 — 별도 항목), 정답 보고 POST와 다음 퍼즐 GET을 **동시에** 쐈다 — 그런데 GET이 서버가 이 정답을 실제로 기록하기 **전에** 도착하면, 서버 입장에선 그 퍼즐이 아직 안 풀린 걸로 보여 방금 푼 바로 그 퍼즐을 다시 돌려줬다. `PuzzleRepository.reportSolved()`의 POST를 `nextBatchCount=0` → `1`로 바꿔, 정답 보고 응답 자체에 다음 퍼즐이 함께 오도록 고쳤다 — 서버가 이 정답을 이미 기록한 뒤에만 나올 수 있는 응답이라 경쟁 상태 자체가 구조적으로 사라지고, 네트워크 왕복도 2회 → 1회로 줄어 로딩도 빨라졌다(별도 사용자 리포트 — "다음 퍼즐 불러오는 과정에서 로딩이 너무 길어"). 다음 퍼즐이 응답에 안 실려 오면(디버그 프리뷰 픽스처, 또는 정답 보고 자체가 실패한 경우) 기존처럼 별도 GET으로 폴백한다.
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
- [x] **Wear OS 에뮬레이터 검증** (Wear OS 6 / API 36, `wearos_large_round`, 로컬에 새로 구성) — 실제로 설치·실행해 스크린샷/`adb input tap`으로 확인:
  - 로그인 화면 렌더링 확인, "Sign in with Lichess" 탭 → 페어링된 폰이 없어 `RemoteAuthClient`가 실패 → "Sign-in failed. Tap to retry." 에러 상태가 의도대로 표시됨을 확인(정상 동작 — 이 실패 자체가 예상된 제약, RESEARCH.md 4절).
  - 디버그 전용 `DebugPuzzlePreviewActivity`(로그인 없이 캔 데이터로 퍼즐 화면을 미리보기 위한 debug 소스셋 전용 진입점, release에는 포함 안 됨)로 퍼즐 화면 전체 루프를 검증: 탭-선택→이동, 오답("Try again" 빨간색), 정답 연속 진행(상대 응수 자동 재생), 마지막 수 완료 시 "Correct" + 레이팅 칩 갱신(`1646 (+14)`)까지 전부 스크린샷으로 확인됨.
  - **실기기 스크린샷으로 실제 버그 1건 발견 후 수정**: 상단 턴 텍스트가 초안 20sp로는 원형 화면 상단 안전 영역을 넘어 보드와 겹침 → 13sp + 폭 78% 제한 + 말줄임으로 수정(DESIGN.md 3절에 기록).
- [ ] **실물 워치 8 사이드로드 검증** — 에뮬레이터로 대부분 검증했지만, 진짜 페어링된 폰을 통한 `RemoteAuthClient` 로그인 완주(에뮬레이터는 페어링 폰이 없어 이 부분만은 재현 불가)와 실제 Lichess 계정으로 배치 API 호출까지는 실물 기기 필요.
- [x] Git 커밋 + main 브랜치 push

### 구현 중 확정된 사항 (계획 대비 변경/구체화)

- Gradle/AGP/Kotlin/각 라이브러리 버전은 추측 대신 Google Maven·Maven Central·JitPack에서 실제 조회해 확정(2026-08 시점 최신 안정 버전 — `gradle/libs.versions.toml` 참고). **AGP 9부터 `org.jetbrains.kotlin.android` 플러그인이 AGP에 내장되어 별도 적용이 오류**라는 점 등, 최근 툴체인 변경 사항을 빌드 실패로 실제 확인하며 반영.
- `androidx.wear.phone.interactions.authentication`(RemoteAuthClient 등)은 `androidx.wear:wear-input`이 아니라 별도 아티팩트 **`androidx.wear:wear-phone-interactions`**에 있음 — RESEARCH.md 4절 조사 당시 놓쳤던 부분으로, 실제 컴파일 오류로 발견해 추가.
- `PuzzleEngine`에서 좌표(탭) 기반 이동은 `board.doMove(Move)`가 아니라 `board.doMove(Move, fullValidation = true)`를 써야 함 — 2-인자 버전은 유닛 테스트로 실제로 상대 기물을 규칙 위반으로 움직이는 사례를 잡아냄.
- `PuzzleBatchSolveRequest` 직렬화 시 `encodeDefaults = true` 필요 — 기본값(`rated = true`)이 요청 JSON에서 생략되는 걸 유닛 테스트가 잡아냄.
- **PuzzleScreen 시각 요소 중 일부는 이번 1차 구현에서 의도적으로 보류**(실기기 없이는 시각 검증이 불가능한 항목 위주): 기물 글리프의 외곽선(대비용 스트로크), 방금 둔 수 칸 강조 애니메이션. 상태 텍스트(정답/오답/레이팅)는 모두 구현됨 — 기능은 완전하고 일부 장식만 남은 상태.

---

# 9. 오프닝 학습 기능 (2차 기능)

RESEARCH.md **11절**의 조사 결과를 바탕으로 확정한 계획. 사용자 요청: *"어떤 수를 두면 어떤 오프닝인지 알 수 있게, 다음 수는 뭐가 있는지 등을 알고 싶어."*

## 9.1 범위

**사용자 확인 완료 (4개 분기 모두 확정)**

| 항목 | 확정 |
|---|---|
| 데이터 소스 | **`GET explorer.lichess.org/lichess`** + 로그인 계정 레이팅대 필터(`ratings`, `speeds`) |
| 진입 방식 | **로그인 후 모드 선택 화면**("Puzzles" / "Openings" 2버튼) |
| 1차 범위 | **자유 탐색만** |
| 후보 수 표시 | **보드 마커 + 우측 탭으로 여는 목록 오버레이** |

**범위 밖(이번에 구현하지 않음)**: 오프닝 퀴즈/드릴 모드, 즐겨찾기(레퍼토리) 저장, 마스터 DB(`/masters`) 토글, `topGames`/`recentGames`(실제 대국 목록) 표시, `history`(월별 추이), 엔진 평가, 오프라인 오프닝 이름 폴백(RESEARCH 11-D의 번들 TSV), 보드 방향 뒤집기 버튼.

## 9.2 화면 구성 (상세 값은 DESIGN.md 확정 후)

1. **모드 선택 화면** *(신규)* — 로그인 직후 진입. 세로로 버튼 2개(`Puzzles` / `Openings`), 기존 로그인 화면 버튼 양식(높이 52dp, 반경 26dp, 안전영역 70% 폭) 그대로 재사용.
2. **퍼즐 화면** *(기존, 기능 변경 없음)*
3. **오프닝 탐색 화면** *(신규)* — 퍼즐 화면의 골격(보드 70.7% 중앙, 상단 텍스트, 하단 칩, 좌·우 반달 탭)을 그대로 재사용해 조작 위치를 통일한다(DEVELOP.md "같은 기능은 모든 화면에서 같은 자리").

| 자리 | 퍼즐 화면(기존) | 오프닝 화면(신규) |
|---|---|---|
| 상단 텍스트 | `White to move` | **오프닝 이름** (`opening.name`). 없으면(`null`) `Out of book` |
| 보드 | 퍼즐 풀이 | **자유 착수(양쪽 모두)**. 인기 상위 후보 수의 **도착 칸에 반투명 마커** |
| 하단 칩 | 레이팅 | **`ECO · 대국 수` + 승/무/패 비율 막대** (예: `B90 · 1.2M`) |
| 좌측 탭(힌트 자리) | 💡 힌트 | **↩ 한 수 되돌리기** (되돌릴 수 없으면 비활성 표시) |
| 우측 탭(키보드 자리) | ⌨ SAN 입력 | **≡ 후보 수 목록 오버레이 열기** |

- **후보 수 목록 오버레이**: 보드 위에 덮는 전체화면 `ScalingLazyColumn`. 한 항목 = `SAN` · `인기 %` · `승/무/패 막대`, 그 수로 **오프닝 이름이 새로 갈리는 경우**(`moves[].opening != null`)에만 보조 줄로 그 이름을 회색 작은 글씨로 표시. 항목 탭 = 그 수를 두고 오버레이 닫기.
  - Galaxy Watch 8(비-Classic)에는 **회전 베젤이 없다**(RESEARCH 1절) → 터치 스크롤이 기본. 회전형 사이드 버튼 스크롤은 `Modifier.rotaryScrollable`로 부가 지원만 한다.
- **뒤로 가기**: 오프닝/퍼즐 화면 → 모드 선택 화면은 Wear 관례대로 **가장자리 스와이프(swipe-to-dismiss)**. 보드 탭과의 충돌은 보드가 화면 중앙 70.7%에만 있어 가장자리 판정과 겹치지 않는다.

## 9.3 데이터 흐름

```
[로그인 완료]
      ▼
[모드 선택 화면] ──"Puzzles"──▶ (기존 퍼즐 화면, 변경 없음)
      │
   "Openings"
      ▼
GET /api/account (Bearer)  → perfs.rapid → blitz → classical 순으로 첫 레이팅 채택
      │                       (모두 없으면 1600) → 인접 rating 밴드 2개 산출
      │                       예: 1712 → ratings=1600,1800
      ▼
[오프닝 화면] 시작 위치 렌더링, play = [] (빈 수순)
      ▼
GET https://explorer.lichess.org/lichess
      ?variant=standard&play={UCI 쉼표 목록}&speeds=blitz,rapid,classical
      &ratings={밴드}&moves=12&topGames=0&recentGames=0     (Bearer 첨부)
      ▼
응답 → 상단 이름(opening) / 하단 칩(ECO·합계·W-D-B) / 보드 마커·목록(moves[])
      ▼
사용자가 보드 칸 2탭 또는 목록 항목 탭 → chesslib로 합법수 검증 → 보드 즉시 갱신
      ▼ (play에 UCI 한 칸 append)
캐시에 있으면 즉시 표시 / 없으면 위 GET 재요청 (직전 요청은 취소)
      ▼
좌측 탭 = play 마지막 원소 pop + board.undoMove() → 캐시 히트라 네트워크 0회
```

**요청 규율(RESEARCH 11-C)**: Lichess는 **동시 요청 금지**, 429 시 1분 대기. 따라서
- 진행 중인 explorer 요청은 새 수를 둘 때 **반드시 취소**(ViewModel의 단일 `Job` 보관).
- `play` 문자열을 키로 한 **LRU 인메모리 캐시(64개)** — 되돌리기/재방문은 네트워크 0회.
- **선반영(optimistic) 렌더링**: 착수 시 보드는 즉시 갱신하고, 이름·칩·마커는 로딩 상태로 두었다가 응답이 오면 채운다(BT 프록시 왕복 수백 ms, RESEARCH 1절).
- 429/네트워크 실패: 보드 조작은 계속 가능하게 두고 상단에 `Connection lost` + 우측 탭 자리에 재시도 — 퍼즐 화면의 기존 실패 표기 규칙과 동일하게 맞춘다.

## 9.4 주요 모듈

| 모듈 | 위치 | 내용 |
|---|---|---|
| `OpeningExplorerModels` | `:core` `core/lichess/` | `ExplorerResponse`(`opening`, `white/draws/black`, `moves[]`) 직렬화 모델. `topGames`/`recentGames`/`history`는 요청하지 않으므로 모델에 넣지 않는다(`ignoreUnknownKeys` 로 무시) |
| `OpeningExplorerClient` | `:core` `core/lichess/` | 호스트만 다른 `LichessApiClient`의 쌍둥이(OkHttp + kotlinx.serialization). `explorer.lichess.org` 고정, `play` 쿼리 조립 |
| `LichessApiClient.fetchAccount()` | `:core` (기존 파일에 추가) | `GET /api/account` — `perfs.{rapid,blitz,classical}.rating`만 파싱하는 최소 모델 |
| `OpeningLine` | `:core` `core/opening/` | chesslib `Board` + UCI 수순 리스트. `push(from,to)` / `pop()` / `uciPlay(): String` / `legalDestinations(from)`. **새 의존성 없음**(RESEARCH 11-E) |
| `OpeningRepository` | `:app` `data/` | 토큰 주입 + LRU 캐시 + 레이팅 밴드 산출 |
| `OpeningViewModel` / `OpeningScreen` | `:app` `opening/` | 상태(StateFlow) + Compose UI |
| `ModeScreen` | `:app` `mode/` | 모드 선택 화면 |
| `Board` 파라미터화 | `:app` `puzzle/PuzzleScreen.kt` → `ui/board/Board.kt` | 현재 `PuzzleUiState`에 직접 묶여 있어 그대로는 재사용 불가. **기물 배치 + 하이라이트 집합 + 마커 집합 + 탭 콜백**만 받도록 떼어낸다 |

## 9.5 상충 관계 (Trade-offs)

- **`Board` 컴포저블 이동/파라미터화 = 기존 코드 수정**: CLAUDE.md 3절("멀쩡한 코드는 리팩터링하지 마라")과 정면으로 부딪히는 유일한 항목이다. 대안은 오프닝 화면용 보드를 **복제**하는 것인데, 1039줄짜리 `PuzzleScreen.kt`의 보드 렌더링(기물 PNG 추출·좌표 매핑·애니메이션·반달 탭 모양)이 통째로 두 벌이 되어 이후 모든 시각 수정이 두 곳에 필요해진다. **재사용 쪽을 택하되, 파라미터화는 오프닝 화면이 실제로 요구하는 것(기물 배치·하이라이트·마커·탭 콜백)까지만** 하고 그 외 퍼즐 전용 로직·스타일은 손대지 않는다. 리팩터링 전후로 기존 테스트가 동일하게 통과하는 것을 완료 조건으로 둔다(DEVELOP.md 4절).
- **`/lichess`(내 레이팅대) vs `/masters`**: 마스터 DB가 정석적으로 깨끗하지만, 실제로 내가 만날 수를 배우는 목적에는 내 레이팅대 통계가 맞다는 판단으로 `/lichess` 확정(사용자 선택). 대신 하위 레이팅대에는 정석이 아닌 수가 상위에 올라올 수 있다 — 이건 "실제로 이렇게 둔다"는 정보로서 의도된 결과로 받아들인다. `/masters` 토글은 범위 밖으로 남긴다.
- **레이팅 밴드 산출을 위한 `GET /api/account` 1회 추가 호출**: 고정 밴드(예: 항상 `1600,1800`)로 하면 호출이 없어 더 단순하지만 "내 레이팅대"라는 선택의 의미가 사라진다. 오프닝 화면 진입 시 **한 번만** 호출하고 결과를 프로세스 수명 동안 보관하는 절충으로 간다. 퍼즐 레이팅은 대국 레이팅이 아니므로 기존 응답에서 재활용할 수 없다.
- **밴드 2개 선택 규칙**: 내 레이팅이 속한 밴드 + 바로 위 밴드. 한 밴드만 쓰면 낮은 레이팅대에서 표본이 얇아 상위 후보 수가 흔들리고, 3개 이상이면 "내 수준"이라는 필터의 의미가 옅어진다.
- **마커는 상위 5개만**: API는 `moves=12`로 받아 목록 오버레이엔 전부 보여주되, 보드 위 마커는 **인기 상위 5개**로 제한한다. 480×480 화면의 한 칸이 약 42dp라 그 이상은 판독이 어렵다. 잘린 나머지는 오버레이에서 확인 가능하므로 정보 손실이 없다.
- **수를 둘 때마다 네트워크 1회**: 후보 수들을 미리 프리페치하면 체감은 빨라지지만 한 수마다 요청이 5~12배로 늘어 동시 요청 금지 정책과 정면 충돌한다. 프리페치하지 않고 **캐시 + 선반영 렌더링**으로 대응한다.
- **보드 방향 고정(백 시점)**: 자유 탐색이라 양쪽을 다 두게 되는데, 매 수마다 보드를 뒤집으면 퍼즐 화면에서 이미 잡았던 버그(DESIGN.md 4절 `solverSide`)와 같은 종류의 혼란이 생긴다. 뒤집기 버튼은 요청받지 않았으므로 만들지 않고 **백 시점 고정**으로 간다.
- **네비게이션: `SwipeToDismissBox`(이미 있는 `wear-compose-foundation`) vs `wear-compose:compose-navigation` 추가**: 화면이 3개뿐이고 그래프가 선형(모드 선택 → 하나)이라, 의존성을 새로 넣지 않고 `MainActivity`의 상태 하나 + `SwipeToDismissBox`로 처리한다. 화면이 더 늘어나면 그때 네비게이션 라이브러리로 옮긴다.
- **모드 선택 화면을 매번 거친다**: 마지막 선택을 기억해 바로 그 화면으로 들어가면 퍼즐만 푸는 날엔 탭 1회가 줄지만, 다른 모드로 가는 길이 숨겨진다. 사용자가 "모드 선택 화면"을 명시적으로 택했으므로 **매 실행 시 표시**로 확정한다.

## 9.6 테스트 전략

- `OpeningExplorerModels` 직렬화 — **공식 스펙의 예시 JSON**(`examples/openingExplorer-lichess.json.yaml`, RESEARCH 11-B에 전재)을 그대로 픽스처로 써서 `opening`, `moves[].opening`(`null`인 경우 포함), 집계 숫자 파싱을 검증.
- `OpeningExplorerClient`의 쿼리 조립 — `play` 누적 문자열, `ratings` 밴드 직렬화가 스펙 형식(쉼표 구분)과 일치하는지.
- `OpeningLine` — `push`/`pop` 후 보드 상태와 `uciPlay()` 문자열이 왕복 일치하는지, 불법 좌표쌍이 거부되는지(퍼즐에서 실제로 물렸던 `legalMoves()` 검증 이슈를 동일하게 적용).
- 레이팅 → 밴드 매핑 — 경계값(999/1000/2499/2500/미보유)을 표 기반으로 검증.
- `Board` 파라미터화 — 기존 `:core` 테스트 전량 및 퍼즐 화면 동작이 리팩터링 전후 동일해야 한다.
- 화면은 기존과 동일하게 **Wear OS 에뮬레이터(API 36, wearos_large_round) 스크린샷**으로 검증하고, 네트워크가 필요한 부분은 debug 소스셋의 캔 응답 픽스처로 먼저 확인한다(`DebugPuzzlePreviewActivity`와 같은 방식).

## 9.7 체크리스트

- [x] RESEARCH.md 11절 작성 (Opening Explorer API·오프닝 데이터셋 조사) — **완료**
- [x] PLAN.md 9절 작성 (본 문서) — **완료**
- [x] DESIGN.md 9절 작성 (모드 선택 화면, 오프닝 화면, 마커/막대/오버레이 색·크기, 빈·로딩·오류 상태) + HTML 목업 `mocks/opening-screens.html` — 실제 렌더링으로 원 밖 잘림 2건 발견·수정
- [x] `OpeningExplorerModels` + 직렬화 유닛 테스트 (공식 스펙 예시 JSON 그대로 픽스처로 사용)
- [x] `OpeningExplorerClient` + 쿼리 조립 유닛 테스트
- [x] `LichessApiClient.fetchAccount()` + 레이팅 밴드 매핑 유닛 테스트 (경계값 포함)
- [x] `OpeningLine`(chesslib) + 유닛 테스트 (`./gradlew :core:test` 48개 통과)
- [x] `Board` 컴포저블 파라미터화 → `ui/board/`로 분리 (아래 "구현 중 확정된 사항" 참고)
- [x] `OpeningRepository`(캐시·요청 취소·밴드)
- [x] `OpeningViewModel` + `OpeningScreen`(보드 배지, 좌 undo 탭, 우 목록 탭)
- [x] 후보 수 목록 오버레이(`ScalingLazyColumn`)
- [x] `ModeScreen` + `MainActivity` 분기 + `BackHandler` 뒤로가기
- [x] **Wear OS 에뮬레이터 검증** (Wear OS 6 / API 36, `wearos_large_round`) — 디버그 전용 `DebugOpeningPreviewActivity`(캔 응답 픽스처)로 실제 설치·실행해 스크린샷/`adb input tap`으로 확인:
  - 모드 선택 화면 렌더링, "Openings" 탭 → 오프닝 화면 진입
  - 시작 위치: `Out of book` + `48.8M` 칩 + 상위 5수 배지(①e4 ②d4 ③Nf3 ④c4 ⑤g3) + 되돌리기 탭 비활성
  - 배지 ①(e4) 탭 → 착수, `King's Pawn Game` / `B00 · 23.7M`, 마지막 수 노란 강조, 새 배지 5개
  - 우측 탭 → 후보 목록(`c5 29% / Sicilian Defense`, `e5 25%`, `e6 12% / French Defense`) → 행 탭으로 착수
  - 되돌리기 탭 → 직전 국면 복귀(캐시 히트, 요청 0회)
  - 기물 탭 → 선택 + 합법수 점, 이때 배지는 감춰짐
  - 사전에 없는 수 → `Out of book` + `No games` + 목록 탭 비활성
  - 뒤로가기 2단계: 목록만 닫힘 → 모드 선택 화면 복귀
- [ ] Git 커밋 + main push

### 오프닝 기능 구현 중 확정된 사항 (계획 대비 변경/구체화)

- **`Board` 파라미터화의 실제 범위**: 계획대로 통째로 파라미터화하지 않고, 두 화면이 실제로 공유하는 것만 `ui/board/`로 옮겼다 — `BoardSquare`(한 칸), `HalfMoonShape`/`HintTabShape`(반달 탭 모양), `rowColOf`/`squareAt`/`isLightSquare`(좌표 규약), `PieceIcon`(기물 그림), 그리고 새 `SideTab`. `PuzzleScreen`의 `Board` 본체(이동 애니메이션, 드래그, 롱프레스 확대, 리뷰 스텝)는 **손대지 않았다**: 오프닝 탐색에는 되돌릴 오답도, 드러낼 상대 응수도 없어서 그 기계장치가 필요 없고, 억지로 일반화하면 검증된 퍼즐 동작을 건드리게 된다(CLAUDE.md 3조). 대신 오프닝 화면이 자기 8×8 루프를 갖는다 — 약 15줄 중복이지만, 픽셀을 결정하는 부분은 전부 공유된다. `PuzzleScreen.kt`는 1039줄 → 813줄.
- **후보 표시 = 크기가 아니라 숫자**(사용자 요청): DESIGN.md 9.4절 참고. 순위(1~5)를 넣는다 — 비율은 두 자리라 배지 안에 읽히는 크기로 들어가지 않는다.
- **뒤로가기: `SwipeToDismissBox` → `BackHandler`**: Wear는 가장자리 스와이프 제스처를 back 이벤트로 전달하므로, `BackHandler` 하나가 스와이프와 하드웨어 back을 모두 받는다. 새 의존성(`wear-compose:compose-navigation`)도, 새 컴포저블 계층도 필요 없다. **트레이드오프**: 스와이프하는 동안 화면이 손가락을 따라 끌려오는 애니메이션은 없고, 제스처가 끝나는 시점에 그냥 뒤로 간다. 목록이 열려 있으면 안쪽 `BackHandler`가 먼저 받아 목록만 닫힌다(에뮬레이터에서 2단계 모두 확인).
- **취소를 실패로 오해하지 않기**: `OpeningRepository.explore`가 처음엔 `runCatching`이었는데, 이러면 **`CancellationException`까지 삼켜 `Result.failure`가 된다**. 수를 둘 때마다 직전 요청을 취소하는 구조라, 그 취소가 곧바로 "Connection Lost"로 깜빡일 수 있었다. `CancellationException`은 다시 던지도록 고쳤다.
- **하단 칩 폭 버그**: 승/무/패 막대를 칩 아랫변에 붙이려고 `Column` 안에서 `fillMaxWidth()`를 썼는데, Compose에서 이 값은 형제의 측정 폭이 아니라 **들어온 제약(=화면 폭)** 기준이라 칩이 화면 전체로 늘어난다. `Modifier.width(IntrinsicSize.Max)`로 칩의 폭을 텍스트에 맞춘 뒤 막대가 그것을 채우게 고쳤다.
- **레이팅 밴드는 프로세스당 1회**: `GET /api/account`는 오프닝 화면 첫 조회 때만 부르고 결과를 보관한다. 실패하거나 레이티드 대국이 없으면 기본값(1600 → `1600,1800`)으로 진행한다 — 통계를 아예 못 보는 것보다 낫고, 퍼즐 화면에는 아무 영향이 없다.
- **이번 범위에서 의도적으로 빼둔 것**: 퍼즐 화면의 **롱프레스 확대/드래그 착수**는 오프닝 보드에 넣지 않았다(요청받은 기능이 아니고, 후보 수는 목록에서 48dp 타깃으로 고를 수 있어 정밀 탭 부담이 낮다). **차례 표시**도 없다 — 배지 자체가 항상 "지금 둘 수 있는 수"라 차례를 알려주지만, 정석 밖이라 배지가 없는 국면에서는 알 수 없다.
