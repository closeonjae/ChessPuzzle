# RESEARCH.md

갤럭시 워치 8 (블루투스 모델)에서 동작하는, Lichess OAuth 로그인 기반 레이팅 맞춤 체스 퍼즐 앱에 대한 코드리서치 결과.

- 대상 기기: **Samsung Galaxy Watch 8 (Bluetooth/비-LTE 모델)**
- 앱 구조: **Wear OS 단독(standalone) 앱** — 별도 폰 컴패니언 앱 없음
- 배포: **개인용 사이드로드** (Play 스토어 미배포)

리서치는 general-purpose 에이전트 2개(Lichess API/OAuth, Wear OS/워치8/개발스택)를 병렬로 실행하고, 워치 위 OAuth 구현 세부 API는 추가로 공식 문서를 직접 확인해 보강했다. 각 항목에 출처 URL을 표기한다.

---

## 1. 대상 기기: Galaxy Watch 8 (Bluetooth 모델)

**소프트웨어**
- **Wear OS 6 + One UI 8 Watch** 탑재. Wear OS 6은 **Android 16 (API 36)** 기반.
  ([GSMArena](https://www.gsmarena.com/samsung_galaxy_watch8-13997.php), [Android Developers – Prepare for Wear OS 6](https://developer.android.com/training/wearables/versions/6/setup))
- 개인 사이드로드 앱이므로 Play 정책상의 target API 강제는 해당 없음 → `targetSdk`를 실제 OS(36)에 맞춰도 무방. `minSdk`는 Wear Compose Material3를 쓰려면 통상 26+ 권장.

**하드웨어**
- Exynos W1000 (3nm), Mali-G68 GPU, **RAM 2GB**, 저장공간 32GB.
- 디스플레이: **1.47" Super AMOLED, 480×480(원형)**, 40mm/44mm 케이스.
- 연결: Bluetooth 5.3, Wi-Fi, NFC, GPS. Bluetooth 모델은 LTE 모뎀만 빠지고 칩셋/RAM/디스플레이는 LTE 모델과 동일.
- **로터리 입력 주의**: 일반 Galaxy Watch 8(비-Classic)에는 **물리 회전 베젤이 없다.** 회전 베젤은 **Watch 8 Classic 전용**이며, 일반 모델은 회전형 사이드 버튼(RSB) + 터치 베젤만 있다. (실제 보유 기기가 Classic이 아니라면 이 전제로 설계)
  ([SamMobile](https://www.sammobile.com/news/galaxy-watch-8-classic-with-rotating-bezel-leaks-and-youll-love-it/))

**Bluetooth-only 인터넷 접근 방식**
- 공식 문서: *"워치가 폰과 블루투스로 연결되어 있으면, 워치의 네트워크 트래픽은 일반적으로 폰을 통해 프록시된다."* — OS/연결 계층에서 투명하게 처리되므로, 워치 앱 코드는 **일반 OkHttp/Ktor HTTP 클라이언트를 그대로 쓰면 된다** (별도 프록시 설정 불필요).
  ([Android Developers – Network access on standalone devices](https://developer.android.com/training/wearables/data-layer/network-access))
- 주의점:
  - `android.webkit`(CookieManager 등)은 Wear OS에서 **사용 불가**.
  - Data Layer API는 폰↔워치 앱 동기화용이지 일반 인터넷 통신용이 아님 — 일반 네트워킹 API를 그대로 사용할 것.
  - 백그라운드/비동기 네트워크 호출은 Doze 대응을 위해 `WorkManager` 사용 권장.
  - **커뮤니티 보고에 따르면, 폰의 Wear 컴패니언 앱(Galaxy Wearable 등) 프로세스가 살아있지 않으면 블루투스 프록시 인터넷이 끊기는 사례가 있음** → 재시도/오프라인 허용 UX 필요.
    ([Wear OS by Google 커뮤니티 스레드](https://support.google.com/wearos/thread/136137806))

---

## 2. Wear OS 단독(standalone) 앱 구조

**Manifest 요건**
```xml
<uses-feature android:name="android.hardware.type.watch" />
<application>
    <meta-data
        android:name="com.google.android.wearable.standalone"
        android:value="true" />
</application>
```
([Android Developers – Standalone vs non-standalone apps](https://developer.android.com/training/wearables/apps/standalone-apps))

- **폰 컴패니언 앱은 필요 없음.** Standalone 앱은 정의상 핵심 기능에 폰 앱이 불필요하며, 설치도 **ADB로 워치에 직접** 할 수 있다 (워치 개발자 옵션 → ADB/무선 디버깅 → `adb pair` → `adb install app.apk`). Play 스토어 배포 시엔 통상 더미 폰 APK 동반이 필요하지만, 사이드로드에는 해당 없음.
  ([사이드로드 가이드](https://www.howtogeek.com/how-to-sideload-apps-on-your-wear-os-smartwatch/))
- **단, 3절의 OAuth 로그인(RemoteAuthClient)은 로그인 "그 순간"에만 페어링된 폰이 근처에 있어야 한다** — 이는 폰 전용 컴패니언 앱을 별도로 만드는 것과는 다르며, Wear OS/Galaxy Wearable 같은 시스템 컴패니언이 이미 처리해준다. "단독 앱" 정의(핵심 기능에 폰 앱 불필요)와 상충하지 않음.

**개발 스택**
- `androidx.wear.compose:compose-material3`(또는 M2), `compose-foundation`, `compose-navigation` — 모바일용 Compose Material이 아닌 **Wear 전용 Compose 라이브러리**를 사용해야 함(원형 화면 대응 `ScalingLazyColumn`, `AppScaffold`/`ScreenScaffold` 등).
  ([Android Developers – Compose on Wear OS](https://developer.android.com/training/wearables/compose))
- **Horologist** (`google/horologist`) 중 유용한 모듈: `compose-layout`(원형 화면 레이아웃 헬퍼), `network-awareness`(연결 상태 감지 — 2절의 BT 프록시 끊김 이슈 대응에 적합). `datalayer`/`media`/`tiles`는 이 앱 범위에 불필요.

---

## 3. Lichess OAuth2 (Authorization Code + PKCE)

출처: Lichess API의 실제 OpenAPI 소스(`lichess-org/api` 레포의 `doc/specs/*.yaml`, spec 버전 2.0.162) 직접 확인.

- **인가 엔드포인트**: `GET https://lichess.org/oauth`
- **토큰 엔드포인트**: `POST https://lichess.org/api/token`
  (구 `oauth.lichess.org` 엔드포인트는 폐기됨)
  ([oauth.yaml](https://github.com/lichess-org/api/blob/master/doc/specs/tags/oauth/oauth.yaml), [api-token.yaml](https://github.com/lichess-org/api/blob/master/doc/specs/tags/oauth/api-token.yaml))

**인가 요청 파라미터**: `response_type=code`, `client_id`(임의 문자열, 사전 등록 불필요), `redirect_uri`(스킴 제약 문서화 안 됨), `code_challenge_method=S256`(고정), `code_challenge`, `scope`(선택), `state`(선택, CSRF 방지 권장).

**토큰 요청**: `grant_type=authorization_code`, `code`, `code_verifier`, `redirect_uri`, `client_id`. **클라이언트 시크릿 필드 자체가 없음** — 완전한 public client 흐름.

**토큰 응답**: `{ token_type: "Bearer", access_token, expires_in }`. **`expires_in` ≈ 31,536,000초(1년)**. **리프레시 토큰 미지원** — 대신 토큰이 장기 유효하며 `DELETE /api/token`(Bearer 인증)으로 폐기 가능.

**사전 등록 불필요**: 문서 원문 — *"Lichess supports unregistered and public clients (no client authentication, choose any unique client id)."*
([lichess-api.yaml L74-79](https://github.com/lichess-org/api/blob/master/doc/specs/lichess-api.yaml))

**redirect_uri 방식**: Lichess 공식 Flutter 앱은 커스텀 URI 스킴을 사용 (`org.lichess.mobile://login-callback`, `client_id=lichess_mobile`) — "커스텀 스킴이 App Link보다 Android OEM 브라우저 전반에서 더 안정적으로 인터셉트된다"는 코드 주석이 있음. 단, **워치에서는 4절의 `RemoteAuthClient`가 자체 redirect_uri(`https://wear.googleapis.com/3p_auth/{패키지명}?code=...`)를 제공**하므로 이 값을 그대로 써야 함(직접 커스텀 스킴을 정할 필요 없음).
([lichess-org/mobile auth_repository.dart](https://github.com/lichess-org/mobile/blob/main/lib/src/model/auth/auth_repository.dart))

---

## 4. 워치 화면에서의 OAuth 로그인 — 핵심 리스크 및 결론

**문제**: Wear OS는 기본 탑재된 시스템 브라우저가 없다. `AppAuth-Android`의 `AuthorizationService`는 Custom Tabs를 지원하는 브라우저가 기기에 있어야 동작하는데, 워치엔 그런 브라우저가 기본으로 없다. 즉 **워치 화면에서 직접 브라우저 기반 OAuth 동의 화면을 띄우는 방식은 사실상 불가능/비권장**이다.
([AppAuth-Android README](https://github.com/openid/appauth-android))

**Google 공식 대응 — `RemoteAuthClient`**: 워치가 OAuth 요청을 만들면, **동의 화면은 페어링된 폰의 브라우저에서 뜨고**, 결과가 자동으로 워치로 중계된다. 폰 쪽에 별도 앱 개발이 필요 없고(시스템이 처리), 워치는 로그인 "그 순간"만 폰이 근처에 있으면 된다. 공식 문서에서 확인한 실제 API:

```kotlin
// import androidx.wear.phone.interactions.authentication.*

val oauthRequest = OAuthRequest.Builder(context)
    .setAuthProviderUrl(uri)          // Lichess: https://lichess.org/oauth?...
    .setCodeChallenge(codeChallenge)  // PKCE S256
    .setClientId(CLIENT_ID)
    .build()

// redirect_uri로 써야 할 값 (Google이 자동 발급):
// oauthRequest.redirectUrl() -> https://wear.googleapis.com/3p_auth/<패키지명>?code=xyz

RemoteAuthClient.create(context).sendAuthorizationRequest(
    request = oauthRequest,
    executor = { command -> command?.run() },
    clientCallback = object : RemoteAuthClient.Callback() {
        override fun onAuthorizationResponse(request: OAuthRequest, response: OAuthResponse) {
            // response에서 authorization code 추출 → /api/token 교환
        }
        override fun onAuthorizationError(request: OAuthRequest, errorCode: Int) {
            // 실패 처리 (구체적 에러 코드는 공식 문서에 열거되어 있지 않음)
        }
    }
)
```
("This request triggers a call to the companion app, which then presents an authorization UI in a web browser on the user's mobile phone." — [Android Developers – Auth on wearables](https://developer.android.com/training/wearables/apps/auth-wear))

- Lichess는 `redirect_uri`를 사전 등록하지 않고 `/oauth`에 보낸 값과 `/api/token`에 보낸 값의 일치만 검사하므로, `wear.googleapis.com` 리다이렉트 URI를 그대로 써도 동작에 지장이 없다(3절).
- 문서에 없는 부분(구체적 에러 코드, PKCE code_verifier/challenge 수동 생성 필요 여부, 필요 권한/manifest 항목)은 **구현 단계에서 실제 기기 테스트로 확인 필요** — 리스크로 남겨둔다.
- Ambient mode 진입 시 로그인 흐름이 끊기지 않도록 `WearableActivityController` 사용 권장.

**다른 대안과의 비교**
| 방식 | 워치 단독 요건 부합 | OAuth 정식 흐름 | 비고 |
|---|---|---|---|
| **(A) RemoteAuthClient** (권장) | O (로그인 순간만 폰 필요) | O | Google 공식 지원, Lichess와 호환 확인됨 |
| (B) 워치에 브라우저 사이드로드 후 AppAuth 직접 실행 | 부분(브라우저 별도 설치 필요) | O | 비권장·불안정, 실사용 사례 없음 |
| (C) OAuth2 Device Authorization Grant (기기 코드 입력) | O | O(단, **Lichess가 미지원** — API 문서에 해당 엔드포인트 없음) | **Lichess에는 사용 불가** |
| (D) Lichess 개인 액세스 토큰(Personal API token)을 워치에 수동 입력 | O | X (OAuth 아님) | 가장 단순/견고하지만 "OAuth 로그인" 요구사항과 다름 |

→ **(A) RemoteAuthClient가 "워치 단독 앱 + OAuth 로그인" 요구를 모두 만족하는 유일한 현실적 방법**으로 판단됨. (C)는 애초에 Lichess가 지원하지 않아 제외. 이 판단은 PLAN.md에서 최종 확정.

---

## 5. Lichess 퍼즐 API — 레이팅 맞춤 퍼즐

**필요 스코프**: `puzzle:read` (조회), 진행 반영까지 하려면 `puzzle:write`도 필요(6절 참고).

### `GET /api/puzzle/next`
- `angle`(테마/오프닝 필터), `difficulty`(`easiest|easier|normal|harder|hardest`, **인증된 사용자의 현재 퍼즐 레이팅 기준 상대값**, 비로그인 시 1500 기준), `color`.
- 인증 시: *"사용자가 한 번도 본 적 없는 퍼즐만 반환"* — lichess.org/training과 동일한 개인화.
- **대량 다운로드 목적 사용 금지** 경고가 문서에 명시됨(대신 공개 퍼즐 DB 덤프 이용).
- 응답 예시:
```json
{
  "game": { "id": "50ZuAmiN", "perf": {...}, "rated": true, "players": [...], "pgn": "e4 e6 ...", "clock": "5+1" },
  "puzzle": { "id": "QBX2O", "rating": 1632, "plays": 3889, "solution": ["f2g1","h1g1","c8c1"], "themes": [...], "initialPly": 66 }
}
```
- **시작 포지션은 FEN이 아니라 `game.pgn` + `puzzle.initialPly`로 제공됨** → 워치 앱은 PGN을 initialPly까지 리플레이해서 시작 포지션을 만들어야 함(8절 체스 라이브러리로 처리). `fen`/`lastMove` 필드는 스키마상 optional로 존재하나 `/next` 예시엔 없음 — 실제 응답에서 존재 여부 확인 필요.
- `solution`은 **UCI 표기법 배열**(퍼즐의 강제 응수 전체, 상대 응수 포함 교대로).

([api-puzzle-next.yaml](https://github.com/lichess-org/api/blob/master/doc/specs/tags/puzzles/api-puzzle-next.yaml), [PuzzleAndGame.yaml](https://github.com/lichess-org/api/blob/master/doc/specs/schemas/PuzzleAndGame.yaml))

### 5-A. 퍼즐 API로 얻을 수 있는 전체 정보 (스키마 전수 조사, 힌트 버그 재조사 중 정리)

**엔드포인트는 3개뿐**(모두 `PuzzleAndGame` 스키마를 응답에 사용) — `GET /api/puzzle/next`, `GET /api/puzzle/batch/{angle}`(이 앱이 실제로 쓰는 것, `LichessApiClient.fetchPuzzleBatch`), `POST /api/puzzle/batch/{angle}`(정답 보고, `solvePuzzleBatch`). 세 엔드포인트가 주는 **퍼즐/게임 데이터 자체는 완전히 동일한 모양**이다.

**요청 파라미터 (batch GET, `api-puzzle-batch-angle.yaml`)**
| 파라미터 | 위치 | 설명 | 앱에서 사용? |
|---|---|---|---|
| `angle` | path, 필수 | 테마/오프닝 필터. `"mix"` = 전체 뒤섞임 | O (`"mix"` 고정) |
| `nb` | query | 몇 개 받을지, 1~50, 기본 15 | O (`count=1`) |
| `difficulty` | query | `easiest\|easier\|normal\|harder\|hardest`, 저장된 레이팅 대비 상대값 | **X — 항상 서버 기본값** |
| `color` | query | `white\|black`, 원하는 플레이 색. **문서에 "nb=1일 때만 동작"이라 명시** | **X — 노출 안 됨** (지금은 매번 무작위 색) |

`color`는 이 앱처럼 `nb=1`로 매번 부르는 구조와 정확히 궁합이 맞는 파라미터라, 노출하면 "항상 흑/항상 백만 풀기" 같은 향후 설정에 바로 쓸 수 있다 — 지금은 미사용.

**응답 필드 전체 (`PuzzleAndGame` → `game` + `puzzle`, `PuzzleAndGame.yaml` 전수 조사)**

`game` (필수: `clock`, `id`, `perf`, `pgn`, `players`, `rated`):
- `id`(string), `pgn`(string, RESEARCH.md 3절), `rated`(bool), `clock`(string, "5+1" 형식)
- `perf`: `key`(예: "blitz"), `name`(예: "Blitz")
- `players[2]`: `name`, `id`, `color`("white"/"black"), `rating`(int) + 앱에서 안 쓰는 선택 필드 `flair`, `patron`, `patronColor`, `title`(타이틀 보유자면 "GM" 등)

`puzzle` (필수: `id`, `initialPly`, `plays`, `rating`, `solution`, `themes`):
- `id`, `rating`(int), `plays`(int, 이 퍼즐이 풀린 횟수), `solution`(UCI 문자열 배열), `themes`(문자열 배열, 예: `"mateIn2"`, `"middlegame"`, `"short"`, `"attraction"`, `"sacrifice"` — 태그 종류가 매우 많고 공식 목록은 별도 조사 필요), `initialPly`(int)
- **`fen`(string, optional)**, **`lastMove`(string, UCI, optional)** — `PuzzleData`/`Puzzle`에 이미 필드는 있으나(`LichessModels.kt`) **`toPuzzleData()`도 `PuzzleEngine`도 전혀 안 씀**. 이 둘이 실제로 채워져서 온다면 `game.pgn`을 `initialPly`까지 SAN으로 리플레이하는 지금 방식 대신 **`fen`을 그대로 `Board().loadFromFen(fen)`에 넣어 시작 포지션을 만들 수 있어**, PGN 토큰 파싱(기보 표기 변형, 캐슬링 표기 등)에서 올 수 있는 오류를 원천적으로 없앨 수 있다 — 실제 응답에 이 두 필드가 채워지는지가 아직 미확인이라(RESEARCH.md 3절에 이미 남겨둔 리스크) 다음 `Log.d`(이번 세션에 추가함, `PuzzleViewModel.loadNextPuzzle`) 결과로 확인 예정.

**POST(정답 보고) 요청/응답**: 요청 `{ "solutions": [{ "id", "win", "rated" }] }`, `nb`(0~50, 0=다음 배치 안 받음). 응답 `PuzzleBatchSolveResponse`: `puzzles[]`(nb>0일 때 다음 배치), `glicko{rating, deviation}`, `rounds[]{id, win, ratingDiff}`.

**`solution[]`의 첫 항목이 누구 수인지 — 공식 예시로 재확인**: `/next` 문서의 실제 예시 퍼즐(`QBX2O`, `themes: ["mateIn2", ...]`, `solution: ["f2g1","h1g1","c8c1"]`)을 수순으로 풀어보면 정확히 **"...Rf2-g1+(체크) 2.Kxg1(외통 없는 유일한 응수) Rc8-c1#(백랭크 메이트)"** 패턴이다 — 체크·메이트를 거는 두 수(1·3번째)가 같은 편(솔버)이고, 강제로 낀 응수(2번째, 킹이 잡는 수) 하나가 상대편이라는 게 수 자체의 체스적 의미로 명확하다. 즉 **`solution[0]`은 솔버의 수가 맞다** — DESIGN.md 5절에 기록된 "auto-play 시도 → 실기기에서 정상 퍼즐 로딩이 거의 다 깨짐 → 되돌림" 사건은 곧 이 공식 예시로도 재확인된 것: Lichess **퍼즐 데이터베이스 CSV 덤프**(database.lichess.org, FEN+Moves, opponent-first)와 이 **REST API**(game.pgn+initialPly+solution, solver-first)는 같은 데이터를 담고 있어도 규약이 다른 별개의 두 형식이었다. 남은 미해결 문제(실기기에서 힌트가 상대 기물을 가리킴)는 이 "누가 먼저"의 문제가 아니라 **다른 원인**이라는 뜻 — 실제 `Log.d` 캡처로 계속 조사 중.

## 6. 퍼즐 결과 반영(레이팅 갱신) — `/api/puzzle/next` 단독으론 부족

**중요 발견**: `/api/puzzle/next`를 반복 호출하는 것만으로는 **사용자의 퍼즐 레이팅이 갱신되지 않는다.** `difficulty` 파라미터는 "저장된 현재 레이팅" 대비 상대값일 뿐, 그 저장된 레이팅 자체를 갱신하는 경로가 아니다.

**레이팅을 실제로 갱신하며 lichess.org/training과 동일하게 동작하려면** 다음 배치 API 쌍을 써야 한다:

- `GET /api/puzzle/batch/{angle}` (`puzzle:read`) — 퍼즐 묶음(`nb`개, 기본 15) 조회.
- `POST /api/puzzle/batch/{angle}` (`puzzle:write`) — **"Set puzzles as solved and update ratings."**
  요청: `{ "solutions": [{ "id": "QBX2O", "win": true, "rated": true }] }`
  응답: 갱신된 Glicko 레이팅(`glicko.rating`, `glicko.deviation`)과 `rounds[].ratingDiff`, 필요 시(`nb`>0) 새로 갱신된 레이팅 기준 다음 배치까지 함께 반환.

→ **설계 결론**: `angle=mix`, `nb=1`로 `GET`/`POST` batch 엔드포인트를 조합해 "퍼즐 1개 요청 → 정답/오답 즉시 보고 → 갱신된 레이팅으로 다음 퍼즐" 루프를 구성하는 것이 "레이팅에 맞는 퍼즐"이라는 요구사항에 정확히 부합한다. `/api/puzzle/next`는 이 목적에 부적합.

([api-puzzle-batch-angle.yaml](https://github.com/lichess-org/api/blob/master/doc/specs/tags/puzzles/api-puzzle-batch-angle.yaml))

## 7. Rate limit / 사용 정책

- 명시적 수치 없음. *"한 번에 한 요청만 하라. 429를 받으면 최소 1분 대기 후 재시도."* 개인 단일 사용자 앱(순차 요청, 열거/대량 다운로드 아님)은 문제 될 소지 낮음.
  ([lichess-api.yaml L42-49](https://github.com/lichess-org/api/blob/master/doc/specs/lichess-api.yaml))

## 8. 체스 규칙 라이브러리 (합법수 검증 · FEN/PGN 파싱)

| | bhlangonijr/chesslib | cvb941/kchesslib |
|---|---|---|
| 언어 | Java (순수 JVM) | Kotlin Multiplatform (JVM 타겟) |
| 라이선스 | Apache 2.0 | 확인 필요 |
| 배포 | JitPack `com.github.bhlangonijr:chesslib:1.3.7` | **Maven Central** `io.github.cvb941:kchesslib:1.0.5` |
| 활동성 | 매우 활발(2026-06 최신 릴리스) | 원본의 KMP 포크, 최신성 미확인 |
| 기능 | 합법수 생성, FEN/PGN 파싱(대용량 PGN 스트리밍), SAN, 무르기, 체크메이트/스테일메이트/무승부 판정, Chess960 | 동일(포크) |

→ **권장: `bhlangonijr/chesslib`** (Apache 2.0, 활발한 유지보수, Android/Wear에서 순수 JVM이라 그대로 사용 가능). Maven Central을 선호하면 `kchesslib`도 대안.
([bhlangonijr/chesslib](https://github.com/bhlangonijr/chesslib))

이 라이브러리로 `game.pgn`을 `initialPly`까지 재생해 시작 포지션을 만들고, 사용자가 둔 수가 합법수인지 + `puzzle.solution`의 다음 수와 일치하는지 검증한다.

## 9. 체스판 UI/입력 (Compose, 480×480 원형 화면)

- Compose `Canvas`로 8×8 격자와 기물을 그리고, `Modifier.pointerInput`으로 입력 처리.
- **탭-투-무브(선택 후 목적지 선택) 권장** — 480×480 원형 화면에서 8칸 분할 시 칸당 ~45–55dp로, 드래그보다 탭이 터치 정확도 면에서 안정적.
- 원형 화면 안전영역을 고려해 보드를 내접 정사각형에 배치(Horologist `compose-layout` 활용).
- **로터리(RSB/터치베젤) 입력은 체스판 2차원 선택에 부적합** — 이번 앱의 핵심 상호작용에서는 사용하지 않는 것으로 결론(요청 범위 밖의 기능 추가 지양, CLAUDE.md 2조 참고). 필요시 나중에 접근성 보조 기능으로 검토 가능.
- 참고 오픈소스: [jlmcdonnell/chess](https://github.com/jlmcdonnell/chess)(드래그앤드롭, 합법수 하이라이트), [tabasavr/chessboard](https://github.com/tabasavr/chessboard).

---

## 10-A. 좌표 입력(키보드)을 통한 이동 — Wear OS 텍스트 입력

퍼즐 화면에 "탭으로 좌표 텍스트(예: `e2e4`)를 입력해 이동" 기능을 추가하기로 하여 별도로 조사함.

- **Compose for Wear OS(`androidx.wear.compose.material3`)에는 `TextField`/`EditText`가 없다.** 인라인 텍스트 입력 컴포저블 자체가 제공되지 않음.
  ([Wear Compose Material3 릴리스 노트](https://developer.android.com/jetpack/androidx/releases/wear-compose-m3))
- 대신 공식 권장 패턴은 **`androidx.wear.input.RemoteInputIntentHelper`**(아티팩트 `androidx.wear:wear-input:1.0.0`)로 전체화면 입력 인텐트를 실행하는 것:
  ```kotlin
  val remoteInputs = listOf(RemoteInput.Builder(RESULT_KEY).setLabel("Enter move").build())
  val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
  RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      val text = RemoteInput.getResultsFromIntent(result.data)?.getCharSequence(RESULT_KEY)
  }
  Button(onClick = { launcher.launch(intent) }) { Text("Enter move") }
  ```
  이 인텐트가 뜨면 워치의 "입력 선택기"(기본 키보드/딕테이션/손글씨/이모지)가 전체화면으로 표시됨.
  ([Android Developers – Create input method editors on Wear](https://developer.android.com/training/wearables/user-input/wear-ime), [RemoteInputIntentHelper reference](https://developer.android.com/reference/androidx/wear/input/RemoteInputIntentHelper))
- **갤럭시 워치(One UI Watch)는 자체 키보드 IME를 탑재**한다 — One UI Watch 4.5부터 QWERTY + 스와이프 입력 지원(그 전엔 T9 방식). 다른 IME(Gboard 등)로 교체 가능한 표준 안드로이드 IME 프레임워크 위에서 동작하므로, 앱 입장에서는 어떤 키보드가 뜨는지 제어할 수 없음.
  ([Samsung Newsroom – One UI Watch4.5](https://news.samsung.com/us/one-ui-watch45-galaxy-convenience-accessibility/))
- **리스크**: 짧은 고정 형식 문자열을 입력하기엔 스와이프 타이핑/자동완성/딕테이션이 오히려 방해가 될 수 있음(조사 에이전트는 이 때문에 텍스트 입력 대신 보드 2탭 방식이나 커스텀 좌표 키패드를 대안으로 제시했음). 그럼에도 **사용자가 명시적으로 "키보드를 열어서" 입력하는 방식을 요청**했으므로 `RemoteInputIntentHelper` 경로로 구현하고, 이 리스크는 실기기 검증 대상으로 남긴다(자동완성으로 잘못된 문자열이 들어오면 형식 유효성 검사 후 재입력 요청).
- **입력 형식은 UCI(`e2e4`)가 아니라 표준 대수 기보법(SAN, 예: `Nc3`)으로 확정**(사용자 요청). SAN은 기물 종류·목적지만 표기하고 출발 칸을 생략하므로, 같은 목적지로 이동 가능한 기물이 둘 이상이면 표준 SAN 표기법의 소거 규칙(예: `Nbd2`처럼 파일/랭크를 덧붙임)을 그대로 따라야 함 — bhlangonijr/chesslib은 현재 보드 상태 기준으로 SAN 문자열을 합법수로 파싱하는 기능을 제공하므로(8절) 별도 구현 없이 그대로 활용 가능.

## 10. 남은 미확정 사항 (PLAN.md에서 확정 필요)

1. **로그인 방식**: `RemoteAuthClient` 기반 OAuth(권장, 4절) 확정 여부 — 사용자 확인 필요.
2. `RemoteAuthClient`의 정확한 에러 코드 체계, PKCE code_verifier/challenge 생성이 라이브러리 내장인지 수동 구현인지, 필요한 manifest 권한 — **공식 문서에 미기재**, 프로토타입 단계에서 실기기로 검증 필요(리스크로 PLAN.md에 명시).
3. Bluetooth 프록시 인터넷 연결 불안정 시 재시도/오프라인 UX 정책.
4. 패키지명(`redirect_uri`의 `wear.googleapis.com/3p_auth/{패키지명}` 형태에 들어갈 값) 및 Lichess OAuth `client_id` 문자열 결정.
