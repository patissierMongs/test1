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
- 숫자 행은 항상 보인다. 기호 키는 PC(personal computer) 키보드와 같은 자리에 있다.

## 입력 방법

| 동작 | 결과 |
|---|---|
| 키를 위로 밀기 | Shift 글자(`9` → `(`, `'` → `"`, `-` → `_`, `4` → `$`, `\` → `|`, 글자는 대문자, 한글은 쌍자음·ㅒ·ㅖ) |
| 키를 아래로 밀기 | Ctrl(control)+키(`c` → `^C`, `[` → Esc와 같은 `^[`). 숫자 행과 `-` `=`은 F1–F12 |
| 길게 누르기(기본 400 ms) | 위로 밀기와 같음 |
| 스페이스에서 좌우로 끌기 | 커서 이동. Shift를 켜고 끌면 선택 |
| Esc/Ctrl 키(Caps Lock 자리) | 짧게 누르면 Esc(escape), 누른 채 다른 키를 치면 Ctrl |
| Shift, Ctrl, Alt(alternate), Fn(function) | 한 번 누르면 다음 키 한 번에 적용, 350 ms 안에 두 번 누르면 고정, 누른 채 치면 조합 |
| Fn + 숫자, `-`, `=` | F1–F12 |
| Fn + `⌫` | Del(forward delete) |
| Fn + h j k l / y o / u i | ← ↓ ↑ → / Home End / PgDn(Page Down) PgUp(Page Up) |
| 방향키 위로 밀기 | Home, PgDn, PgUp, End |
| 방향키·`⌫` 누르고 있기 | 반복 입력 |
| `한/A` | 한글·영문 전환 |

밀기 동작은 손을 떼기 전에 결과(`^C`, `F5`, `(` 등)를 팝업으로 보여 준다. 손가락을 처음 자리로 되돌리면 일반 탭이 된다.

상단 줄에는 현재 언어, 켜진 수정키, 조합 중인 한글(터미널에서), 최근에 보낸 특수키(`Esc`, `^C`, `M-b`(Alt+b), `Tab`, `⏎`, 방향키)와 붙여넣기·분할·키보드 전환·설정·숨김 버튼이 있다.

## 터미널과 vim

- 입력창의 `inputType` class가 `TYPE_NULL`이면 터미널로 다룬다. Termux, ConnectBot, JuiceSSH, Android Terminal Emulator는 설정의 목록으로도 지정된다.
- 터미널에서는 조합 중인 한글을 앱에 보내지 않고 상단 줄에 표시한다. 음절이 완성되면 보낸다. Enter, 방향키, Esc, Ctrl 조합을 보내기 전에는 조합 중인 음절을 먼저 보낸다.
- Ctrl과 Alt는 `META_CTRL_ON | META_CTRL_LEFT_ON`, `META_ALT_ON | META_ALT_LEFT_ON`을 붙인 키 이벤트로 보낸다. Termux의 Alt+글자(ESC(escape) 문자 접두어)와 일반 입력창의 Ctrl+A/C/V/X/Z가 이 방식으로 동작한다.
- Termux는 Ctrl+Alt 키 이벤트를 자체 단축키로 가져가므로, 터미널에서 Ctrl+Alt+글자는 ESC와 제어 문자를 이어 붙인 문자열로 보낸다.
- 한글 모드에서 Esc나 Ctrl+[를 보내면 영문 모드로 바뀐다(설정에서 끌 수 있음). vim에서 normal mode(일반 모드)로 돌아갈 때 한글로 명령이 들어가는 일을 막는다.
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
| 분할 배열 키 너비 | 8.5 mm(7.0–11.0), 화면에 맞지 않으면 줄어듦 |
| 한글 키에 영문 글자 함께 표시 | 켬 |
| 키 누름 햅틱 / 햅틱 효과 | 켬 / 시스템 키보드 진동 |
| 키 소리 | 끔 |
| 탭할 때 미리보기 팝업 | 자동(600dp(density-independent pixel)보다 좁은 화면에서만 켬) |
| 키를 아래로 밀면 Ctrl+키 | 켬 |
| Esc를 누르면 영문으로 전환 | 켬 |
| 길게 눌러 윗글자 입력 | 400 ms(0이면 끔) |
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
| `input/TouchTracker.kt` | 다중 터치, 밀기 판정, 반복, 길게 누르기, 스페이스 커서 이동 |
| `input/OffsetModel.kt` | 터치 편차 학습 |
| `layout/Layouts.kt`, `layout/Geometry.kt` | 전체·분할·compact 배열과 mm 단위 배치 |
| `ime/FoldKeyService.kt` | `InputMethodService` |
| `ime/InputConnectionEditor.kt` | `InputConnection` 호출, 키 이벤트 생성 |
| `ui/KeyboardView.kt` | 그리기, 터치 처리, 상단 줄 |
| `ui/HapticFeedback.kt` | 햅틱과 소리 |
| `settings/SettingsActivity.kt` | 설정 화면, 입력 시험, 키 이벤트 확인창 |

## 테스트

```sh
./gradlew testDebugUnitTest lintDebug
```

JVM(Java Virtual Machine) 단위 테스트와 Robolectric 테스트가 함께 돈다. `RenderTest`는 폴드7 내부 화면과 커버 화면 크기의 키보드를 `app/build/render/*.png`로 그린다. Robolectric의 `KeyCharacterMap`은 Ctrl+A에도 글자 `a`를 돌려주는 등 실제 Android와 달라서, 일반 입력창의 Ctrl 단축키는 에뮬레이터나 기기에서 확인해야 한다. 기기에서 확인할 항목은 [docs/research.md의 10.3절](docs/research.md#103-기기에서-확인할-항목)에 있다.

## 알려진 한계

- 폴드7 실기기에서 시험하지 않았다. Android 11 에뮬레이터(폴드7 내부 화면 해상도와 밀도)에서 키 이벤트, 한글 조합, Ctrl 단축키, 가로 분할 배열을 확인했다. 햅틱 느낌, One UI(삼성 user interface)의 키보드 진동 설정 연동, `xdpi` 정확도, 작업 표시줄과의 상호작용, Android 16의 하단 insets 처리는 기기 확인이 필요하다.
- 접근성 서비스(TalkBack)용 가상 뷰 구조를 제공하지 않는다.
- 하드웨어 키보드의 키는 처리하지 않는다. 앱이 직접 받는다.
