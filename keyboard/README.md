# FoldKey

갤럭시 Z 폴드7 내부 화면에서 Python, 셸, vim, Go, 터미널 작업을 하기 위한 Android 키보드(IME, Input Method Editor)다. 한글 두벌식 입력을 지원한다. 배열과 기능을 정한 근거는 [docs/research.md](docs/research.md)에 있다.

## 빌드와 설치

필요한 것: JDK(Java Development Kit) 17 이상(Robolectric 테스트는 JDK 21), Android SDK(Software Development Kit) Platform 36, Build-Tools 36.0.0.

```sh
cd keyboard
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

설치 후 앱 목록의 FoldKey를 열고 다음 순서로 켠다.

1. "시스템 설정에서 FoldKey 사용 켜기"를 눌러 FoldKey를 켠다.
2. "입력 방법을 FoldKey로 선택"을 눌러 FoldKey를 고른다.
3. 같은 화면의 입력창과 "키 이벤트 확인창"에서 동작을 확인한다. 확인창은 터미널처럼 `TYPE_NULL` 입력을 요청하고, 받은 글자와 키 이벤트를 그대로 보여 준다.

adb(Android Debug Bridge)로 켜려면 다음 명령을 쓴다.

```sh
adb shell ime enable io.github.patissiermongs.foldkey/.ime.FoldKeyService
adb shell ime set io.github.patissiermongs.foldkey/.ime.FoldKeyService
```

## 배열

| 화면 | 기본 배열 | 키 간격(폴드7) |
|---|---|---|
| 내부 화면 세로(책 자세, 135.8 mm) | 전체 배열: ANSI(American National Standards Institute) 15칸 | 9.0 mm |
| 내부 화면 가로(150.7 mm) | 분할 배열 | 8.5 mm |
| 폭 110 mm 미만(커버 화면 65 mm) | 10열 compact 배열 | 6.4 mm |

- 세로와 가로의 전체/분할 선택은 따로 저장된다. 상단 줄의 "분할"/"전체" 버튼으로 현재 방향의 배열을 바꾼다.
- 분할 배열의 경계는 T|Y, G|H, V|B다. 두벌식 자음이 모두 왼쪽, 모음이 모두 오른쪽에 온다. 두 반쪽 사이 안쪽 가장자리에는 보이지 않는 복제 키(T 옆 Y, Y 옆 T 등)가 있다.
- 분할 배열 가운데의 빈 공간이 16 mm 이상 남으면 그 자리에 클립보드 기록과 입력 내용을 보여 준다([분할 배열 가운데](#분할-배열-가운데)). 폴드7 가로 화면에서는 빈 공간 28.6 mm 가운데 20.1 mm를 쓰고, 이때 복제 키 폭은 한 칸에서 반 칸(4.25 mm)으로 줄어든다. 세로 분할에서는 빈 공간이 16.6 mm라 표시하지 않는다.
- 숫자 행은 항상 보인다. 기호 키는 PC(personal computer) 키보드와 같은 자리에 있다.

## 입력 방법

| 동작 | 결과 |
|---|---|
| 키를 위로 밀기 | Shift 글자(`9` → `(`, `'` → `"`, `-` → `_`, `4` → `$`, `\` → `\|`, 글자는 대문자, 한글은 쌍자음·ㅒ·ㅖ) |
| 키를 아래로 밀기 | Ctrl(control)+키(`c` → `^C`, `[` → Esc와 같은 `^[`). 숫자 행과 `-` `=`은 F1–F12 |
| 길게 누르기(기본 400 ms) | 위로 밀기와 같음. 설정에서 반복 입력으로 바꾸면 vim의 `j`처럼 누르고 있는 동안 반복 |
| 스페이스에서 끌기 | 좌우는 커서 이동(2.5 mm마다 한 칸), 위아래는 ↑/↓(4 mm마다 한 줄). 처음 움직인 방향으로 고정된다. Shift를 켜고 끌면 선택 |
| Esc/Ctrl 키(Caps Lock 자리) | 짧게 누르면 Esc(escape), 누른 채 다른 키를 치면 Ctrl. Fn을 켜고 누르면 Insert(Shift도 켜면 Shift+Insert) |
| Shift, Ctrl, Alt(alternate), Fn(function) | 한 번 누르면 다음 키 한 번에 적용, 350 ms 안에 두 번 누르면 고정, 누른 채 치면 조합 |
| Fn + 숫자, `-`, `=` | F1–F12 |
| Fn + `⌫` | Del(forward delete) |
| Fn + h j k l / y o / u i | ← ↓ ↑ → / Home End / PgDn(Page Down) PgUp(Page Up) |
| Fn + 나머지 키, Fn을 켜고 위로 밀기 | 특수문자([특수문자](#특수문자fn-레이어) 표). Fn이 켜져 있으면 키 표시가 기호로 바뀐다 |
| 방향키 위로 밀기 | Home, PgDn, PgUp, End |
| 방향키·`⌫` 누르고 있기 | 반복 입력 |
| `한/A` | 한글·영문 전환 |

밀기 동작은 손을 떼기 전에 결과(`^C`, `F5`, `(` 등)를 팝업으로 보여 준다. 손가락을 처음 자리로 되돌리면 일반 탭이 된다.

상단 줄에는 현재 언어, 켜진 수정키, 조합 중인 한글(터미널에서), 최근에 보낸 특수키(`Esc`, `^C`, `M-b`(Alt+b), `Tab`, `⏎`, 방향키)와 모두 선택·복사·붙여넣기·분할·키보드 전환·설정·숨김 버튼이 있다. 모두 선택, 복사, 붙여넣기는 입력창이 가진 같은 동작(`performContextMenuAction`)을 부른다. 그래서 비밀번호 입력창처럼 앱이 복사를 막은 곳에서는 복사되지 않는다. 터미널 입력창에서는 모두 선택과 복사를 숨기고, 붙여넣기는 클립 글자를 그대로 보낸다.

## 특수문자(Fn 레이어)

ASCII(American Standard Code for Information Interchange) 95자는 Fn 없이 모두 입력된다. 아래 기호는 Fn을 켜고(한 번 탭, 두 번 탭 고정, 누른 채 입력 모두 가능) 키를 누르거나 위로 민다. 한글 맞춤법 문장 부호 가운데 ASCII에 없는 것(가운뎃점, 줄임표, 따옴표, 낫표, 화살괄호, 줄표, 숨김표, 빠짐표)을 모두 넣었고, 코드 문서에 쓰는 수학 기호, 화살표, 통화 기호를 더했다. 글자 모양은 KS X 1001의 Unicode 대응을 따른다(가운뎃점 U+00B7, 줄표 U+2015, 낫표 U+300C–300F, 화살괄호 U+3008–300B).

| 키 | Fn | Fn + 위로 밀기 | | 키 | Fn | Fn + 위로 밀기 |
|---|---|---|---|---|---|---|
| `` ` `` | ≈ | ∼ | | `p` | π | ¶ |
| `1` `2` `3` | F1 F2 F3 | ¹ ² ³ | | `[` `]` | 「 」 | 『 』 |
| `4` `5` | F4 F5 | £ ‰ | | `\` | ₩ | |
| `6` `7` | F6 F7 | 《 》 | | `s` | § | |
| `8` | F8 | ※ | | `d` | ° | ℃ |
| `9` `0` | F9 F10 | 〈 〉 | | `h` `j` `k` `l` | ← ↓ ↑ → 키 | ← ↓ ↑ → 글자 |
| `-` `=` | F11 F12 | ± ≠ | | `;` `'` | ‘ ’ | “ ” |
| `q` | ★ | ☆ | | `x` `c` | × © | |
| `e` `r` `t` | € ® ™ | | | `v` | ✓ | √ |
| `y` `o` | Home End | ¥ ○ | | `b` | • | □ |
| `u` `i` | PgDn PgUp | µ ∞ | | `n` `m` | – — | ―(`m`) |
| `,` `.` `/` | · … ÷ | ≤ ≥ | | Esc, ⌫ | Insert, Del | |

`\`의 ₩는 한국 PC 키보드가 같은 키에 ₩를 인쇄하는 데서 가져왔다. 줄표는 두 가지다. `m`은 영문 em dash(U+2014), Fn + 위로 민 `m`은 KS X 1001의 줄표(U+2015)다.

## 분할 배열 가운데

가로 분할 배열에서 두 반쪽 사이를 두 칸으로 나눠 쓴다. 엄지에서 가장 먼 자리라서 자주 누르는 키는 두지 않고, 보기만 하는 정보와 가끔 누르는 항목만 둔다.

- 위 두 줄: 복사한 글 기록. 누르면 그 글을 붙여 넣고, 위아래로 끌면 다음 기록으로 넘어간다. 기록이 두 칸보다 많으면 오른쪽에 위치 막대가 보인다. 여러 줄이면 미리보기에 ⏎가 보인다.
- 기록을 길게 누르면 그 칸이 고정·삭제 두 버튼으로 바뀐다. 다른 곳을 누르면 닫힌다.
  - 고정한 글은 📌와 함께 맨 위에 오고, 시간이 지나도 지워지지 않는다. 앱 저장소의 백업 제외 영역(`noBackupFilesDir`)에 저장되며 20개까지 고정할 수 있다. 고정을 풀면 일반 기록의 맨 위로 돌아간다.
  - 고정하지 않은 글은 메모리에만 두고 기본 20개, 24시간까지 보관한다(설정에서 5–50개, 1–72시간). 키보드 프로세스가 끝나면 사라진다.
  - 지운 글이 시스템 클립보드의 현재 클립이면 시스템 클립보드도 비운다. 그러지 않으면 다음에 키보드를 띄울 때 같은 클립이 다시 기록된다.
- 비밀번호 관리 앱처럼 `ClipDescription`에 민감 표시(`android.content.extra.IS_SENSITIVE`)를 붙인 클립은 기록하지 않으므로 고정할 수도 없다.
- 아래 세 줄: 커서 앞 글자. 일반 입력창은 커서가 있는 줄의 끝부분을 보여 주고, 조합 중인 한글은 밑줄로 표시한다. 비밀번호 입력창에서는 보여 주지 않는다.
- 터미널은 화면 내용을 키보드에 넘겨주지 않으므로, FoldKey가 보낸 글자와 키를 Enter 전까지 보여 준다. 터미널의 비밀번호 프롬프트를 키보드가 구별할 수 없어서 이 표시는 기본으로 꺼져 있다(설정 "터미널에서 친 글자도 표시"). 꺼져 있을 때는 조합 중인 한글만 보여 준다.
- 터미널에서 여러 줄 클립을 붙이면 셸이 줄마다 실행할 수 있다. 붙이기 전에 미리보기의 ⏎를 확인한다.

## 반쪽 높이와 크기

- "분할 반쪽을 화면 아래 끝에서 띄우는 높이"(0–15 mm)를 올리면 반쪽 아래에 빈 공간이 생기고 키보드 높이가 그만큼 늘어난다. 맨 아래 행의 판정 영역은 그 빈 공간까지 넓어진다.
- "엄지 범위를 재서 분할 키 너비 정하기"는 측정 화면을 연다. 기기를 가로로 쥐고 엄지마다 키보드 다섯 줄을 지나는 선을 세 번 긋는다. 줄마다 엄지가 닿은 가장 먼 지점을 구하고, 그중 가장 짧은 값을 그 획의 도달 거리로 삼는다. 세 획의 차이가 6 mm와 중앙값의 10% 가운데 큰 값보다 크면 다시 긋게 한다. 일정하면 중앙값으로 두 반쪽이 각 엄지 범위 안에 들어가는 키 너비를 계산하고(7.0–11.0 mm), "적용"을 누르면 저장한다.

## 터미널과 vim

- 입력창의 `inputType` class가 `TYPE_NULL`이면 터미널로 다룬다. Termux, ConnectBot, JuiceSSH, Android Terminal Emulator는 설정의 목록으로도 지정된다.
- 터미널에서는 조합 중인 한글을 앱에 보내지 않고 상단 줄에 표시한다. 음절이 완성되면 보낸다. Enter, 방향키, Esc, Ctrl 조합을 보내기 전에는 조합 중인 음절을 먼저 보낸다.
- Ctrl과 Alt는 `META_CTRL_ON | META_CTRL_LEFT_ON`, `META_ALT_ON | META_ALT_LEFT_ON`을 붙인 키 이벤트로 보낸다. Termux의 Alt+글자(ESC(escape) 문자 접두어)와 일반 입력창의 Ctrl+A/C/V/X/Z가 이 방식으로 동작한다.
- Termux는 Ctrl+Alt 키 이벤트를 자체 단축키로 가져가므로, 터미널에서 Ctrl+Alt+글자는 ESC와 제어 문자를 이어 붙인 문자열로 보낸다.
- 한글 모드에서 Esc나 Ctrl+[를 보내면 영문 모드로 바뀐다(설정에서 끌 수 있음). vim에서 normal mode(일반 모드)로 돌아갈 때 한글로 명령이 들어가는 일을 막는다.
- 터미널에서 Ctrl+Alt+글자는 ESC와 제어 문자를 이어 붙인 문자열로 보낸다. 제어 문자 대응은 Termux와 같다(Space·2·@ → NUL, 3 → ESC, 4 → FS, 5 → GS, 6 → RS, 7·/ → US, 8·? → DEL).
- Termux의 extra keys 줄은 FoldKey와 기능이 겹친다. 필요 없으면 `~/.termux/termux.properties`에 `extra-keys = []`를 넣고 `termux-reload-settings`를 실행한다.

## 오타를 줄이는 장치

- 키 판정은 손가락이 닿은 순간의 좌표로 하고, 입력 확정은 뗄 때 한다. 두 엄지가 겹쳐 눌러도 누른 순서대로 입력된다.
- 사용자의 체계적인 터치 편차를 화면에 보이지 않게 학습해서 판정 위치를 보정한다. 키 중심부(가로·세로 ±25%)는 보정과 관계없이 그 키다. backspace 직후나 1초 넘게 쉬었다가 누른 키에는 보정을 쓰지 않는다. 설정에서 끄거나 초기화한다.
- 자동 수정과 단어 예측은 없다. 명령어와 식별자는 사전 단어가 아니기 때문이다.
- 햅틱은 누르는 순간 시스템 키보드 진동(`KEYBOARD_TAP`)으로 낸다. 설정에서 "틱"이나 "클릭"을 고르면 진동 효과를 직접 재생한다.

## 설정

| 항목 | 기본값 |
|---|---|
| 세로 화면에서 분할 배열 | 끔 |
| 가로 화면에서 분할 배열 | 켬 |
| 행 높이 | 9.5 mm(7.0–13.0) |
| 분할 배열 키 너비 | 8.5 mm(7.0–11.0), 화면에 맞지 않으면 줄어듦. 엄지 범위 측정으로도 정한다 |
| 분할 반쪽 올림 높이 | 0 mm(0–15.0) |
| 가운데에 복사한 글 표시 | 켬 |
| 복사 기록 개수 | 20개(5–50), 고정한 글은 따로 20개까지 |
| 복사 기록 보관 시간 | 24시간(1–72) |
| 가운데에 커서 앞 글자 표시 | 켬 |
| 터미널에서 친 글자도 표시 | 끔 |
| 한글 키에 영문 글자 함께 표시 | 켬 |
| 키 누름 햅틱 / 햅틱 효과 | 켬 / 시스템 키보드 진동 |
| 키 소리 | 끔 |
| 탭할 때 미리보기 팝업 | 자동(600dp(density-independent pixel)보다 좁은 화면에서만 켬) |
| 키를 아래로 밀면 Ctrl+키 | 켬 |
| Esc를 누르면 영문으로 전환 | 켬 |
| 길게 누르기 시간 | 400 ms(0이면 길게 누르기 없음) |
| 길게 누르기 동작 | 윗글자(위로 밀기와 같음). 반복 입력으로 바꿀 수 있음 |
| 내 터치 편차 학습 | 켬 |
| 터미널로 취급할 앱 | `com.termux, org.connectbot, com.sonelli.juicessh, jackpal.androidterm` |

## 소스 구조

| 경로 | 내용 |
|---|---|
| `hangul/HangulComposer.kt` | 두벌식 조합기(겹모음, 겹받침, 도깨비불, 자모 단위 backspace) |
| `hangul/Dubeolsik.kt` | KS(Korean Industrial Standards) X 5002 자판 배치 |
| `engine/KeyboardEngine.kt` | 수정키, 언어, 조합, 터미널/일반 입력창 분기 |
| `engine/Modifiers.kt` | one-shot, lock, chord 상태 |
| `engine/UsKeyMap.kt` | 문자 → US 배열 keycode와 Shift |
| `engine/EchoBuffer.kt` | 터미널에서 보낸 글자와 키 기록(가운데 표시용) |
| `input/TouchTracker.kt` | 다중 터치, 밀기 판정, 반복, 길게 누르기, 스페이스 커서 이동 |
| `input/OffsetModel.kt` | 터치 편차 학습 |
| `input/ReachCalibration.kt` | 엄지 도달 거리 계산, 세 획 일치 검사, 키 너비 계산 |
| `layout/Layouts.kt`, `layout/Geometry.kt` | 전체·분할·compact 배열과 mm 단위 배치 |
| `ime/FoldKeyService.kt` | `InputMethodService` |
| `ime/InputConnectionEditor.kt` | `InputConnection` 호출, 키 이벤트 생성 |
| `ime/ClipboardHistory.kt` | 클립보드 기록(메모리, 개수·보관 시간 설정, 고정·삭제) |
| `ime/PinStore.kt` | 고정한 클립 저장(백업 제외 영역) |
| `ui/KeyboardView.kt` | 그리기, 터치 처리, 상단 줄 |
| `ui/HapticFeedback.kt` | 햅틱과 소리 |
| `settings/SettingsActivity.kt` | 설정 화면, 입력 시험, 키 이벤트 확인창 |
| `settings/ReachCalibrationActivity.kt` | 엄지 범위 측정 화면 |

## 테스트

```sh
./gradlew testDebugUnitTest lintDebug
```

JVM(Java Virtual Machine) 단위 테스트와 Robolectric 테스트가 함께 돈다. `RenderTest`는 폴드7 내부 화면과 커버 화면 크기의 키보드를 `app/build/render/*.png`로 그린다(Fn 기호 표시, 가운데 클립보드와 입력 표시, 클립 고정·삭제 버튼, 터미널 입력 표시 포함). `ReachCalibrationActivityTest`는 측정 화면에 획을 넣어 키 너비 저장까지 확인하고 `reach_calibration.png`를 그린다. Robolectric의 `KeyCharacterMap`은 Ctrl+A에도 글자 `a`를 돌려주는 등 실제 Android와 달라서, 일반 입력창의 Ctrl 단축키는 에뮬레이터나 기기에서 확인해야 한다. 기기에서 확인할 항목은 [docs/research.md의 10.3절](docs/research.md#103-기기에서-확인할-항목)에 있다.

## 알려진 한계

- 폴드7 실기기에서 시험하지 않았다. Android 11 에뮬레이터(폴드7 내부 화면 해상도와 밀도)에서 키 이벤트, 한글 조합, Ctrl 단축키, 가로 분할 배열을 확인했다. 햅틱 느낌, One UI(삼성 user interface)의 키보드 진동 설정 연동, `xdpi` 정확도, 작업 표시줄과의 상호작용, Android 16의 하단 insets 처리는 기기 확인이 필요하다.
- 접근성 서비스(TalkBack)용 가상 뷰 구조를 제공하지 않는다.
- 하드웨어 키보드의 키는 처리하지 않는다. 앱이 직접 받는다.
- 한자 변환은 없다. 사전 데이터가 필요해서 넣지 않았다.
- 분할 배열 가운데에 입력 내용을 보여 주는 형태는 Apple 특허 US 8,547,354(우선일 2010-11-05) 명세서에도 나온다. 이 특허의 청구항이 이 기능을 포함하는지는 검토하지 않았다.
