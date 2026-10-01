# FoldKey 설계 조사: 갤럭시 Z 폴드7 내부 화면용 코딩·터미널 키보드

이 문서는 FoldKey의 배열, 피드백, 특수키, 한글 입력 방식을 정하기 위해 조사한 연구와 소스 코드, 그리고 각 근거가 구현의 어느 부분으로 이어졌는지를 정리한다. 수치는 원문(논문 PDF(Portable Document Format), 공식 문서, AOSP(Android Open Source Project) 소스)에서 확인한 값만 적었다. 원문을 열람하지 못하고 초록이나 2차 자료로만 확인한 항목은 문장 끝에 `[2차]`로 표시했다.

## 목차

1. [결론 요약](#1-결론-요약)
2. [대상 기기: 갤럭시 Z 폴드7](#2-대상-기기-갤럭시-z-폴드7)
3. [입력 자세와 배열 형태](#3-입력-자세와-배열-형태)
4. [키 크기와 터치 정확도](#4-키-크기와-터치-정확도)
5. [두 엄지 rollover 처리](#5-두-엄지-rollover-처리)
6. [피드백: 햅틱, 소리, 화면 표시](#6-피드백-햅틱-소리-화면-표시)
7. [코드와 터미널 입력](#7-코드와-터미널-입력)
8. [한글 입력](#8-한글-입력)
9. [Android 플랫폼 요구 사항](#9-android-플랫폼-요구-사항)
10. [검증 방법과 한계](#10-검증-방법과-한계)
11. [얽힌 이야기들](#11-얽힌-이야기들)
12. [참고 자료](#12-참고-자료)

## 1. 결론 요약

| 항목 | 결정 | 주요 근거 |
|---|---|---|
| 배열 형태 | 세로(책 자세)는 전체 배열, 가로는 분할 배열, 폭 110 mm 미만 화면(커버 화면)은 10열 compact 배열. 방향별로 사용자가 바꿀 수 있다 | Trudeau 외(2013), Aschim 외(2019), KALQ(2013) |
| 글자 배치 | QWERTY(윗줄 왼쪽 여섯 글자로 부르는 영문 배열)와 ANSI(American National Standards Institute) 키보드의 기호 위치를 그대로 둔다 | Bi, Smith, Zhai(2010) |
| 기호 입력 | 숫자 행 상시 표시. 위로 밀기(swipe up)는 PC(personal computer) 키캡의 Shift 글자, 아래로 밀기는 Ctrl(control)+키, 숫자 행의 아래로 밀기는 F1–F12 | 코드 말뭉치 측정(7.1절), Greene 외(2014) |
| 수정키 | Shift, Ctrl, Alt(alternate) 모두 one-shot(한 번 적용), 빠른 두 번 누르기로 lock(고정), 누른 채 다른 키를 치는 chord(동시 입력) 모두 지원. Fn(function)은 0.4.1부터 누를 때마다 숫자판을 켜고 끄는 전환 키 | Fennedy 외(2020), 기존 프로그래머용 키보드, 사용자 요청(7.9절) |
| Esc(escape) | Caps Lock 자리에 dual-role(이중 역할) 키: 짧게 누르면 Esc, 누른 채 다른 키를 치면 Ctrl | ADM-3A 배열, keyd·xcape·Karabiner 관행 |
| 키 판정 | 누른 순간(touch-down) 위치로 키를 고르고 뗄 때(touch-up) 확정. 겹친 터치는 누른 순서대로 확정 | AOSP LatinIME, Dhakal 외(2018) |
| 오타 보정 | 언어 모델 없이 사용자별 터치 편차(offset)를 보이지 않게 학습해 판정 위치를 보정. 키 중심부(±25%)는 항상 그 키로 판정 | Findlater·Wobbrock(2012), Henze 외(2012), Yin 외(2013) |
| 자동 수정 | 없음 | Lertvittayakumjorn 외(2024) |
| 햅틱 | 누르는 순간 `HapticFeedbackConstants.KEYBOARD_TAP`. 반복 입력에는 햅틱 없음 | Ma 외(2015), Kaaresoja 외(2014), AOSP 16 소스 |
| 소리 | 기본 꺼짐 | Ma 외(2015) |
| 미리보기 팝업 | 탭 미리보기는 600dp(density-independent pixel) 이상 화면에서 기본 꺼짐. 밀기 동작의 결과 미리보기(^C 등)는 항상 표시 | AOSP LatinIME 태블릿 기본값, Vogel·Baudisch(2007) |
| 한글 | 두벌식(KS(Korean Industrial Standards, 한국산업표준) X 5002). libhangul과 같은 조합 규칙. 터미널에서는 조합 중인 글자를 키보드 상단 줄에 표시하고 완성된 음절만 보낸다 | KS X 5002, libhangul, Termux 소스 |
| vim 대응 | Esc 또는 Ctrl+[ 입력 시 한글 모드에서 영문으로 전환 | ibus-hangul, 구름 입력기 |
| 분할 배열 모양 | 0.4.0부터 두 반쪽 모두 ANSI·HHKB(Happy Hacking Keyboard)의 줄 어긋남을 그대로 둔다. ⌫는 HHKB처럼 `]` 오른쪽(⏎ 위)에 1.5칸, ⏎는 2.25칸, 오른쪽 Shift는 2.75칸 | 사용자 제보, PFU HHKB 설명서, KALQ(2013)(3.8절) |
| Fn 레이어 | 0.4.0부터 Fn을 켜면 위 네 줄의 오른쪽 끝에 글자 키와 같은 폭의 5×4 숫자판(`7 8 9 / ⌫`, `4 5 6 * (`, `1 2 3 - )`, `0 . = + ⏎`)이 나온다. 커버 화면에서는 오른쪽 반쪽이 숫자판이 된다. 0.2.0에 넣은 특수문자 53개와 다른 키와 겹치던 Fn 조합은 뺐다. Fn+Esc는 Insert | 사용자 요청, PC 숫자 패드 배열(7.8–7.9절) |
| 커서와 선택 | 스페이스를 좌우·위아래로 끌면 커서 이동, 꾹 누른 뒤 끌면 Shift+방향키로 선택. 터미널에서는 Shift 없이 커서만 움직인다 | 사용자 요청, Termux `KeyHandler.java`(7.10절) |
| 분할 배열 가운데 | 위 두 줄은 클립보드 기록, 아래 세 줄은 커서 앞 글자. 자주 누르는 키는 두지 않음 | KALQ(2013), Bergstrom-Lehtovirta·Oulasvirta(2014), Trudeau 외(2013), Lu 외(2019), Jiang 외(2020)(3.6절) |
| 반쪽 크기와 높이 | 엄지 범위 측정(엄지마다 세 획, 서로 일치할 때만 적용), 반쪽 올림 높이 설정 | KALQ(2013), Windows 8 thumb keyboard(3.7절) |

## 2. 대상 기기: 갤럭시 Z 폴드7

삼성 글로벌 뉴스룸의 사양표(2025년 7월 9일)에 실린 값이다.

| 항목 | 값 |
|---|---|
| 내부 화면 | 8.0인치 QXGA+(Quad Extended Graphics Array 확장 해상도) Dynamic AMOLED(active-matrix organic light-emitting diode) 2X, 2184×1968, 368 ppi(pixels per inch), 1–120 Hz |
| 커버 화면 | 6.5인치 FHD+(Full High Definition 확장 해상도), 2520×1080(21:9), 422 ppi |
| 접었을 때 | 72.8×158.4×8.9 mm |
| 펼쳤을 때 | 143.2×158.4×4.2 mm |
| 무게 | 215 g |
| 출시 OS(Operating System) | Android 16, One UI(user interface) 8 |

삼성 개발자 사이트의 에뮬레이터 스킨 페이지는 내부 화면을 "1968 x 2184 pixels (~368 ppi)"로 적는다. 펼친 책 자세에서 가로 방향이 1968 px(pixel)이라는 뜻이다. S Pen은 지원하지 않는다 `[2차]`.

화면 크기를 mm로 환산하면 다음과 같다. 1 px = 25.4 / 368 = 0.0690 mm이다.

- 내부 화면(책 자세): 1968 px → 135.8 mm(가로), 2184 px → 150.7 mm(세로). 대각선 √(1968² + 2184²) = 2940 px, 2940 / 368 = 7.99인치로 사양의 8.0인치와 맞는다.
- 내부 화면(가로 자세): 150.7 mm × 135.8 mm. 접히는 선(crease)은 아래에서 67.9 mm 높이를 가로지른다.
- 커버 화면: 1080 px / 422 ppi = 65.0 mm(가로), 151.7 mm(세로).

Android가 화면 계산에 쓰는 밀도 값(`densityDpi`)과 smallest width(최소 너비) dp는 공식 자료에서 찾지 못했다. 사용자가 화면 확대 설정으로 바꿀 수도 있는 값이다. 그래서 FoldKey는 키 크기를 dp가 아니라 mm로 정하고, `DisplayMetrics.xdpi`와 `ydpi`로 px를 계산한다. Android 문서는 `xdpi`를 "The exact physical pixels per inch of the screen in the X dimension"으로 정의한다. 기기가 엉뚱한 값을 보고하는 경우에 대비해, `xdpi`가 `densityDpi`와 35% 넘게 어긋나면 `densityDpi`를 쓴다.

## 3. 입력 자세와 배열 형태

### 3.1 입력 자세별 속도

- Palin 외(2019)는 37,370명의 참가자를 분석했다. 평균 속도는 36.17 WPM(words per minute, 분당 단어 수, 표준편차 13.22), 수정되지 않은 오류율은 2.34%였다. 82% 이상이 두 엄지로 입력했고, 이 자세가 가장 빨랐다(38.02 WPM).
- Azenkot·Zhai(2012)의 실험실 측정치는 두 엄지 50.03 WPM, 한 손가락(검지) 36.34 WPM, 한 엄지 33.78 WPM이다. 참가자가 오류를 고칠 수 없는 조건이어서 상한값에 가깝다.

### 3.2 분할 배열과 전체 배열

- Trudeau 외(2013)는 두 손으로 태블릿을 쥐고 엄지로 입력하는 12명을 측정했다. 일반 배열이 분할 배열보다 빨랐다(분당 127±5자 대 113±4자). 방향과 위치에 관계없이 같은 결과였다. 분할 배열은 가로 방향에서 엄지의 뻗는 거리와 불편감을 줄였다(불편감 2.7 대 4.5). 세로 방향의 일반 배열은 불편감이 2.9로 낮았다.
- Aschim 외(2019)는 태블릿 분할 배열이 일반 배열보다 속도, 오류율, 선호도에서 모두 나빴다고 보고했다 `[2차: 초록]`.
- KALQ(Oulasvirta 외, 2013, 두 엄지용으로 최적화한 분할 배열)는 7인치 태블릿을 쥔 자세에서 엄지가 편하게 닿는 범위를 반지름 58 mm로 잡고 키 크기를 9.9 mm로 정했다. 훈련 후 37 WPM에 도달했다. 저자들은 새 배열을 배우지 않더라도 쥐는 자세와 "hover-over"(쉬는 엄지를 다음 키 위에 미리 대기) 요령만으로 QWERTY에서도 개선을 얻을 수 있다고 적었다.
- Bergstrom-Lehtovirta·Oulasvirta(2014)의 엄지 기능 영역 모델에서 도달 거리와 상관이 있는 손 치수는 엄지–검지 벌림 폭 하나였다(r = 0.70).
- 삼성 키보드는 폴드 내부 화면에서 처음부터 분할 배열로 뜬다(삼성 영국 고객지원 문서, 2020). Gboard도 폴드5 내부 화면에서 분할을 기본값으로 바꿨다 `[2차]`.

**결정.** 통제 실험 두 건(Trudeau, Aschim)은 일반 배열 쪽이 빠르다는 결과를 냈고, Trudeau의 세로 방향 iPad(가로 약 148 mm)는 폴드7 내부 화면 세로 자세(135.8 mm)보다 넓다. 그래서 세로 자세의 기본값은 전체 배열로 정했다. 가로 자세(150.7 mm)에서는 분할이 엄지 부담을 줄였다는 결과를 따라 분할을 기본값으로 정했다. 두 값 모두 상단 줄의 버튼 하나로 바꾼다.

### 3.3 분할 경계와 한글

분할 경계는 T|Y, G|H, V|B이고 B를 오른쪽 반쪽에 둔다. iPad 분할 키보드도 B를 오른쪽에 둔다. 이 경계에는 두벌식과 관련된 성질이 있다. KS X 5002에서 왼쪽 반쪽의 글자 키(q w e r t, a s d f g, z x c v)는 모두 자음(ㅂㅈㄷㄱㅅ, ㅁㄴㅇㄹㅎ, ㅋㅌㅊㅍ)이고, 오른쪽 반쪽의 글자 키(y u i o p, h j k l, b n m)는 모두 모음(ㅛㅕㅑㅐㅔ, ㅗㅓㅏㅣ, ㅠㅜㅡ)이다. 그래서 자음과 모음이 번갈아 나오는 한글 음절은 두 엄지가 번갈아 치게 된다. KALQ가 최적화 목표로 삼은 것이 바로 엄지 교대 비율이다(KALQ는 62%). B를 왼쪽에 두면 ㅠ가 자음 쪽 엄지로 넘어간다. 이 성질은 `LayoutsTest.splitPutsEveryDubeolsikVowelOnTheRightHalf` 테스트로 확인한다.

두 반쪽 사이 빈 공간의 안쪽 가장자리에는 보이지 않는 복제 키(ghost key)를 둔다. 왼쪽 반쪽 T 옆에는 Y, G 옆에는 H가 들어가는 식이다. 2012년 기술 매체들이 iPad 분할 키보드에서 같은 숨은 키(T/G/V와 Y/H/B)를 찾아 보도했다 `[2차]`. 손가락 배정을 다르게 배운 사용자의 입력이 빈 공간으로 빠지지 않게 하려는 장치다. 0.4.0부터 복제 키는 줄마다 그 줄의 안쪽 끝 바로 옆에 붙는다(3.8절).

### 3.4 반쪽 너비

분할 배열의 목표 키 너비는 8.5 mm다. Parhi 외(2006)에서 연속 입력 과제의 오류율은 7.7 mm 이상에서 유의한 차이가 없었다. 0.4.0에서 배열 폭이 14.25칸에서 15칸으로 늘었다(3.8절). 폴드7 가로 자세에서 왼쪽 반쪽은 왼쪽 끝에서 57.9 mm, 오른쪽 반쪽은 오른쪽 끝에서 77.0 mm(숫자 행 `6`의 안쪽 끝)까지 온다. 세로 자세에서는 화면에 맞추느라 키 너비가 7.93 mm로 줄고, 두 반쪽은 각각 54.0 mm, 71.9 mm가 된다(테스트 `fold7SplitHalfSpansFromTheScreenEdges`, `fold7PortraitSplitKeysAreAbout7_9mm`). 7.93 mm는 Parhi 외의 7.7 mm보다 크다. 오른쪽 반쪽이 KALQ의 58 mm보다 넓은 이유는 ANSI 배열의 기호 키(`[ ] ; ' , . /`)와 Enter, Backspace, 오른쪽 Shift가 오른쪽에 몰려 있고, 줄 어긋남 때문에 숫자 행이 오른쪽 반쪽에서 가장 안쪽까지 나오기 때문이다. 오른쪽 반쪽을 58 mm 안에 넣으려면 키 너비가 6.4 mm 아래여야 해서 설정 범위(7.0 mm 이상)로는 들어오지 않는다. 0.4.0까지 분할 배열은 `\` 키를 1.25칸 왼쪽 Shift 옆에 두었다. 영국식 ISO(International Organization for Standardization) 배열이 같은 자리에 `\`를 둔다. 0.4.1부터는 왼쪽 Shift가 작다는 사용자 의견에 따라 Shift를 ANSI·HHKB와 같은 2.25칸으로 넓히고, `\`는 HHKB처럼 숫자 행 `=` 오른쪽으로 옮겼다.

### 3.5 커버 화면

커버 화면 너비 65.0 mm에 ANSI 15칸 배열을 넣으면 키가 4.3 mm로 줄어 쓸 수 없다. 폭 110 mm 미만에서는 10열 compact 배열로 바꾼다. 키 너비는 6.4 mm다. 첫 행에 Esc/Ctrl, Tab과 기호 8개(`` ` - = [ ] \ ; ' ``)를 두고 `, . /`는 맨 아래 행에 둔다. 0.3.0까지 방향키는 Fn + h/j/k/l로 입력했다. 0.4.0부터 Fn이 숫자판으로 바뀌어 커버 화면에는 방향키가 없고, 커서는 스페이스를 끌어 옮긴다. 키보드 높이가 화면 높이의 50%를 넘으면 행 높이를 줄인다.

### 3.6 분할 배열 가운데 공간

폴드7 가로 화면(150.7 mm)에서 키 너비 8.5 mm로 계산하면 왼쪽 반쪽은 왼쪽 끝에서 57.9 mm, 오른쪽 반쪽은 오른쪽 끝에서 64.3 mm까지 온다. 그 사이 빈 공간은 폭 28.6 mm, 높이 47.5 mm다. 빈 공간의 중심은 왼쪽 끝에서 72.2 mm, 오른쪽 끝에서 78.6 mm 떨어져 있다. 0.1.0에서는 위 네 행의 양쪽 1칸(8.5 mm)을 복제 키가 차지하고 나머지 11.6 mm에 닿은 터치는 버려졌다. 세로 분할(135.8 mm)에서는 빈 공간이 16.6 mm이고 복제 키가 모두 채운다. 책 자세에서 접히는 선(왼쪽에서 67.9 mm)이 이 구간을 지난다.

0.4.0부터는 두 반쪽이 줄마다 다른 자리에서 끝나고, 같은 줄의 두 반쪽 사이 간격은 모든 줄에서 같다(3.8절). 폴드7 가로 화면에서 이 간격은 22.2 mm, 세로 분할에서는 15.9 mm다. 세로 분할의 간격은 복제 키 두 칸이 채운다.

연구 결과:

- KALQ는 엄지가 쥔 자세를 풀지 않고 닿는 범위를 반지름 58 mm로 잡고 그 안에 키 격자를 넣었다. 저자들은 그 범위의 가장자리와 모서리를 누르는 것이 더 느리다는 선행 연구를 인용했다. FoldKey의 반쪽 안쪽 끝은 이미 그 반지름 근처이고, 빈 공간 중심은 그 밖이다.
- Bergstrom-Lehtovirta·Oulasvirta(2014)는 엄지가 닿는 한계선을 포물선으로 모델링하고 "UI(user interface) elements should not be placed close to the predicted extrema of the thumb's reach"라는 heuristic(경험 규칙)을 제시했다.
- Trudeau 외(2013)에서 여러 참가자는 분할 배열이 "양쪽 반쪽을 번갈아 봐야 하는 끊긴 구역(non-continuous zone)"이라 더 집중해야 했다고 답했다. 저자들은 시험에 쓴 iOS 6처럼 두 반쪽 사이로 본문이 보이면 키보드 높이와 본문 가시성 사이의 상충이 일부 줄어든다고 적었다. 참가자들이 키를 가리지 않으려고 엄지를 구부려 세웠다는 관찰도 있다.
- Lu 외(2019)는 분할 키보드를 보며 치면 본문, 왼쪽 반쪽, 오른쪽 반쪽 세 곳을 오가야 한다고 지적했다. Samsung Galaxy Tab S(SM-T800)의 가로 분할 키보드에서 본문만 보고 키보드는 peripheral vision(주변시)에 두는 방식이 27 WPM으로 키를 보며 친 21 WPM보다 28% 빨랐다. 이 수치는 단어 단위 언어 모델로 터치를 해석한 사전 내 단어 입력에서 나왔다. 사전이 없는 명령어와 식별자에는 그대로 적용되지 않는다.
- Jiang 외(2020, Galaxy S6, 30명)에서 시선은 입력 시간의 약 60%를 키보드에 두었다(물리 키보드 숙련자 연구는 20%). 문장 하나를 치는 동안 본문으로 시선을 옮긴 횟수는 평균 3.4회였다(물리 키보드 0.92회). 시선 이동 횟수는 속도와 음의 상관을 보였다(β = −0.51, 오류 수정량을 통제하면 −0.17). 두 엄지 입력에서는 본문을 다시 보기 전에 더 많은 키를 쳐서 오류를 늦게 알아챘다.
- Microsoft의 Windows 8 터치 키보드 설계 글(2012)은 시선 추적에서 사람들이 본문 아니면 키보드를 보고 그 사이는 거의 보지 않았다고 적었다. 그래서 단어 제안을 키보드 위 띠가 아니라 커서 옆에 띄웠다.

선례:

- Windows 8 thumb keyboard는 가운데에 숫자 패드를 두었다 `[2차: 2012년 사용자 블로그, Microsoft 블로그 댓글]`. SwiftKey는 "Thumb layout numpad" 옵션으로 가운데 숫자 패드를 둔다(Microsoft 지원 문서).
- Unexpected Keyboard는 가로 분할에서 가운데 열에 단어 제안을 둔다(`split_middle_column.xml`).
- Apple 특허 US 8,547,354(우선일 2010-11-05)의 명세서는 분할 키보드 두 반쪽 사이의 center portion에 두 번째 입력란을 두는 형태를 기술한다. Google Patents는 이 특허를 2031-08-03 만료 예정의 유효 특허로 표시한다. 어느 청구항이 이 형태를 포함하는지는 확인하지 않았다.

**결정.** 가운데는 두 엄지 모두에서 가장 먼 곳이라 자주 누르는 키는 두지 않는다. 보기만 하는 정보와 가끔 누르는 항목만 둔다.

- 가운데 패널은 줄마다 한 칸씩, 그 줄의 간격에서 복제 키를 뺀 자리에 만든다. 칸 폭이 12 mm 이상일 때만 만들고, 복제 키가 한 칸이면 이 폭이 나오지 않을 때 복제 키를 반 칸으로 줄인다. 폴드7 가로 화면에서 칸 폭은 위 네 줄이 13.7 mm, 스페이스 줄이 22.2 mm이고 복제 키는 4.25 mm다(테스트 `fold7LandscapeSplitGetsOnePanelBoxPerRowBetweenHalfWidthGhostKeys`). 0.3.0까지는 모든 줄에 걸친 직사각형 하나(폭 20.1 mm, 최소 16 mm)였다. 줄 어긋남을 넣은 뒤에는 모든 줄에 걸친 직사각형의 폭이 7.4 mm로 줄어서 줄마다 칸을 나누고 최소 폭을 12 mm로 낮췄다.
- 위 두 줄은 클립보드 기록이다. 가운데에서 가장 위쪽이라 엄지가 빗나가 닿을 가능성이 가장 작은 자리에 누르는 항목을 둔다. 스페이스 양끝과 맞닿은 맨 아래 줄에는 누르는 항목을 두지 않는다.
- 아래 세 줄은 커서 앞 글자다. 본문까지 시선을 옮기지 않고 키 근처에서 확인하게 하려는 것이다. 이 형태가 속도나 오류에 주는 효과를 측정한 연구는 찾지 못했다.
- 숫자 패드는 넣지 않았다. 숫자 행이 이미 있고, 가운데가 숫자 행보다 엄지에서 멀다. 0.4.0의 Fn 숫자판은 가운데가 아니라 오른쪽 반쪽 자리에 나온다(7.9절).
- 가운데를 투명하게 만들어 앱을 보이게 하는 방식은 쓰지 않았다. 코드와 터미널의 줄은 왼쪽 끝에서 시작하므로 20–29 mm 폭으로는 줄의 중간만 보인다. Android에서는 `InputMethodService.Insets.TOUCHABLE_INSETS_REGION`으로 구현할 수 있다(SDK의 `android.jar`에서 필드 확인).
- 2차원 커서 패드 대신 스페이스를 위아래로 끄는 동작을 넣었다. 같은 기능을 엄지가 닿는 곳에서 쓰기 위해서다.

클립보드 구현 근거(AOSP `ClipboardService` main 브랜치 확인): 기본 IME는 언제나 클립보드를 읽을 수 있고("The default IME is always allowed to access the clipboard"), 클립 변경 알림도 받는다. 클립보드 접근 알림(toast)도 기본 IME에는 뜨지 않는다("Exclude special cases: IME, ContentCapture, Autofill"). FoldKey는 `ClipDescription`의 `android.content.extra.IS_SENSITIVE`가 켜진 클립을 기록하지 않는다. 고정하지 않은 기록은 메모리에만 두고(0.3.0부터 기본 20개, 24시간, 설정으로 조절), 사용자가 고정한 글만 백업 제외 영역에 저장한다.

모두 선택, 복사, 붙여넣기 버튼은 `InputConnection.performContextMenuAction`만 부른다. androidx-main의 Jetpack Compose `InputConnection` 두 구현(`RecordingInputConnection`, `StatelessInputConnection`)은 selectAll·copy·paste를 처리한 뒤에도 항상 false를 돌려준다. false를 보고 클립 글자를 직접 넣는 대체 경로를 두면 Compose 입력창에 두 번 붙는다. 0.2.0까지의 붙여넣기에는 이 대체 경로가 있었고 0.3.0에서 뺐다. 터미널(TYPE_NULL)은 이 동작을 처리하지 않으므로 붙여넣기는 클립 글자를 `commitText`로 보내고, 모두 선택과 복사는 숨긴다.

입력 표시 구현 근거: 일반 입력창은 `onUpdateSelection` 뒤에 `getTextBeforeCursor(120)`로 커서가 있는 줄을 읽고, 입력이 시작될 때는 `EditorInfo.getInitialTextBeforeCursor`를 쓴다. AOSP `EditorInfo.setInitialSurroundingSubText`는 비밀번호 입력 종류이면 초기 텍스트를 저장하지 않는다. FoldKey도 비밀번호 입력창에서는 표시하지 않는다. 터미널은 Termux의 `commitText`가 받은 글자를 보낸 직후 내부 `Editable`을 비우므로 화면 내용을 읽을 수 없다. 그래서 FoldKey가 보낸 글자와 키를 Enter 전까지 기록한다. 터미널 비밀번호 프롬프트를 키보드가 구별할 수 없으므로 이 기록은 기본으로 끄고, 꺼져 있을 때는 조합 중인 한글만 보여 준다.

### 3.7 반쪽 높이와 키 너비 맞춤

- KALQ는 화면 아래 가장자리 근처가 특히 닿기 어려워 키보드를 5 mm 올렸다. 같은 논문의 행 위치 조정 실험에서는 행마다 가로 위치를 0–60 px(pixel) 옮긴 조합 65,536개 가운데 가장 나은 것도 예측 속도를 0.1 wpm 올리는 데 그쳤다. 한 키를 몇몇 키에 가깝게 옮기면 다른 키에서 멀어지기 때문이라고 설명했다.
- Trudeau 외(2013)는 아래 위치가 전체적으로 가장 좋았다고 보고하면서, 가운데 높이는 위쪽보다 빠르고 기기 아래 모서리로 인한 불편을 줄일 수 있다고 적었다.
- Windows 8 thumb keyboard는 손 크기에 맞춰 크기를 바꿀 수 있었다. Microsoft는 센서를 붙인 태블릿으로 손 크기가 다양한 사람들의 엄지가 편하게 닿는 곳, 뻗으면 닿는 곳, 불편한 곳을 측정해 배열을 정했다.
- Bergstrom-Lehtovirta·Oulasvirta(2014)에서 엄지 도달 거리와 상관이 있는 손 치수는 엄지–검지 벌림 폭뿐이었다(r = 0.70).

**결정.** 반쪽 올림 높이(0–15 mm)를 설정으로 둔다. 맨 아래 행의 판정 영역은 올린 만큼 아래로 넓힌다(테스트 `liftAddsHeightBelowTheHalvesAndExtendsTheBottomRowTouchArea`). 엄지 범위 측정 화면은 키 너비를 사람마다 정한다.

- 한 획에서 키보드 다섯 줄 각각의 가장 먼 지점을 구하고, 그중 가장 짧은 값을 그 획의 도달 거리로 쓴다. 안쪽 끝이 모든 줄에서 닿아야 하기 때문이다. 다섯 줄을 모두 지나지 않은 획은 버린다.
- 0.4.0부터 반쪽 안쪽 끝은 줄마다 다르다(왼쪽 6.0–6.75칸, 오른쪽 8.25–9.0칸). 키 너비는 가장 짧은 도달 거리와 가장 먼 안쪽 끝(왼쪽 6.75칸, 오른쪽 9.0칸)으로 계산하므로 줄마다 따로 계산할 때보다 작게 나온다. 측정 화면도 줄마다 안쪽 끝 선을 그린다.
- 엄지마다 세 획을 받는다. 세 값의 차이가 6 mm와 중앙값의 10% 가운데 큰 값보다 크면 적용하지 않고 다시 긋게 한다. 한 번의 측정으로 정하지 않기 위해서다. 일정하면 중앙값으로 두 반쪽이 각 범위 안에 들어가는 키 너비를 0.1 mm 단위로 내림해 계산한다(7.0–11.0 mm).
- 이 방법은 KALQ처럼 엄지의 최대 도달선만 잰다. 편한 범위 안에서의 속도 차이와 화면 아래 가장자리의 어려움은 반영하지 않는다. 사람을 대상으로 검증하지 않았다.

### 3.8 줄 어긋남과 ⌫ 자리(0.4.0)

0.3.0까지 분할 배열은 반쪽마다 안쪽 열을 세로로 맞추고 줄 어긋남을 바깥 가장자리로 보냈다. 홈 행을 고정하고 ANSI와 비교하면 두 반쪽 모두 위 글자 행이 0.25칸, 아래 글자 행이 0.5칸, 숫자 행이 0.75칸 오른쪽에 있었다. HHKB를 쓰는 사용자가 이 배열에서 오타가 심하다고 알렸고, 실제 키보드처럼 줄을 어긋나게 해 달라고 요청했다.

- 엄지 입력에서 줄 어긋남이 곧은 열보다 빠르거나 정확하다는 측정은 찾지 못했다. KALQ의 행 이동 실험에서 가장 나은 조합의 예측 이득은 0.1 wpm이었다(3.7절). Deskthority 위키는 곧은 열(matrix) 배열이 더 낫다는 주장을 뒷받침할 연구를 찾을 수 없다고 적는다. 그래서 이 변경의 근거는 성능 연구가 아니라 사용자가 물리 키보드에서 익힌 키 위치와 맞추는 것이다.
- 두 반쪽의 키는 ANSI와 같은 가로 위치에 둔다. `1`, `q`, `a`, `z`는 왼쪽 끝에서 1.0, 1.5, 1.75, 2.25칸, `6`, `y`, `h`, `b`는 6.0, 6.5, 6.75, 6.25칸이다(테스트 `splitHalvesKeepTheAnsiRowStagger`). 오른쪽 반쪽은 왼쪽 반쪽의 같은 줄 끝에서 같은 간격만큼 떨어져 있어서 가운데 빈 공간이 계단 모양이 된다. 복제 키도 줄마다 그 줄 끝에 붙는다.
- ⌫는 HHKB의 Delete 자리인 `]` 오른쪽(⏎ 바로 위)에 HHKB와 같은 1.5칸으로 두고, ⏎는 HHKB와 같은 2.25칸으로 늘린다(PFU 설명서 P3PC-6661-01EN). 원래 ⌫ 자리에는 ANSI와 같은 2.75칸 오른쪽 Shift를 둔다(테스트 `splitUsesHhkbPlacesAndWidthsForBackspaceEnterShiftAndBackslash`). 0.4.1부터 `\`는 HHKB와 같은 숫자 행 `=` 오른쪽에 있고, HHKB의 마지막 키인 `` ` ``는 FoldKey에서 왼쪽 위에 있으므로 숫자 행 오른쪽 끝 1칸은 비워 둔다. 이 자리를 눌러도 아무 키도 입력되지 않는다.
- 오른쪽 스페이스를 2.5칸에서 3.5칸으로 늘려 스페이스 줄의 간격도 다른 줄과 같게 했다.
- 비용: 배열 폭이 14.25칸에서 15칸으로 늘어 폴드7 세로 분할의 키 너비가 8.3 mm에서 7.93 mm로 줄었다. 오른쪽 반쪽의 가장 안쪽 키(숫자 행 `6`)는 오른쪽 끝에서 세로 71.9 mm, 가로 77.0 mm에 있다. 0.3.0에서 오른쪽 반쪽의 안쪽 끝은 각각 62.8 mm, 64.3 mm였다. ANSI 어긋남은 왼쪽 반쪽의 위쪽 안쪽 키(5, T)를 왼손 엄지 쪽으로 당기고, 오른쪽 반쪽의 6, Y는 같은 양만큼 오른손 엄지에서 멀어지게 한다. Microsoft가 공개한 Windows 8 thumb keyboard 그림에서 글자 위치를 재면 오른쪽 반쪽은 왼쪽과 거울 대칭으로 어긋나 있다(그림에서 잰 값, 설계 이유는 글에 없음).
- 키 위치가 바뀌었으므로 분할 배열에서 학습한 터치 편차는 새 이름(`split_ansi_…`)으로 처음부터 다시 쌓는다. 전체 배열과 커버 화면 배열의 학습값은 그대로 쓴다.

## 4. 키 크기와 터치 정확도

### 4.1 키 크기

- Parhi, Karlson, Bederson(2006): 한 손 엄지 조작에서 단일 선택 과제는 9.6 mm 이상, 연속 입력 과제는 7.7 mm 이상에서 오류율 차이가 유의하지 않았다. 권장값은 단일 선택 9.2 mm, 연속 입력 9.6 mm다.
- Bi, Li, Zhai(2013, FFitts law(finger Fitts law)): 손가락의 절대 정밀도는 1차원 0.94 mm, 2차원 1.5 mm다.

폴드7 내부 화면에서 FoldKey 전체 배열의 키 간격은 세로 자세 8.99 mm, 가로 자세 9.98 mm다(테스트 `fold7InnerPortraitFullLayoutPitch`, `fold7InnerLandscapeFullLayoutPitch`). 세로 자세 값은 Parhi의 연속 입력 권장값보다 작지만 유의 차이가 없던 7.7 mm보다는 크다. 행 높이는 기본 9.5 mm이고 설정에서 7.0–13.0 mm로 바꾼다.

### 4.2 체계적 터치 편차

- Holz·Baudisch(2010): 사용자와 손가락 자세에 따른 편차가 흔히 "fat finger(굵은 손가락)" 탓으로 돌리는 부정확성의 67%를 설명했다.
- Holz·Baudisch(2011): 사용자는 손가락 윗면의 시각적 특징으로 조준한다. 사용자별 세로 모델이 편차를 60% 줄였고, 가로 모델은 15% 줄였다.
- Henze, Rukzio, Boll(2012): 4,780만 번의 키 입력에서 터치는 키 중심보다 평균 2.24 mm(LG Optimus One), 1.85 mm(Huawei Ascend) 아래에 떨어졌다. 기기별로 맞춘 이동 함수가 이동 없는 조건보다 오류를 7.1%, 기본 Android 키보드보다 9.1% 줄였다. 고정 10 dp 위쪽 보정은 오히려 오류율을 2.2% 높였다.
- Yin 외(2013): 언어 모델 없이 사용자·자세별 모델이 문자 오류율을 8.64%에서 7.50%로 낮췄다(−13.2%).
- Findlater·Wobbrock(2012): 화면에 보이지 않게 판정 영역만 바꾸는 개인화 키보드가 속도를 12.9% 높였고(3회차 15.2%) 오류율 차이는 없었다. 키를 눈에 보이게 움직이는 방식은 정적 키보드보다 빠르지 않았다. 이 키보드는 backspace 직후 한 번의 입력과, 초당 1타보다 느린 입력에서는 학습 모델을 끄고 보이는 키 경계로 판정했다. 지워진 입력은 학습 데이터에서 뺐다.

**구현.** `OffsetModel`은 키 행과 화면 가로 4구역으로 나눈 zone(구역)마다 평균 편차를 mm 단위로 학습한다. 드물게 쓰는 키도 같은 구역의 표본을 공유한다. 표본 20개까지는 보정량을 비례해서 줄이고, 보정량은 키 크기의 35%를 넘지 않는다. 키 크기의 50%를 넘게 벗어난 표본은 버린다. Findlater·Wobbrock의 방식을 따라 backspace로 지워진 입력은 학습하지 않고, backspace 직후나 1초 넘게 쉬었다가 누른 입력에는 보정을 쓰지 않는다. 학습값은 배열 종류와 화면 너비별로 따로 저장하고 설정에서 초기화한다. 고정 보정값은 쓰지 않는다(Henze의 10 dp 결과, AOSP LatinIME의 `config_keyboard_vertical_correction`이 현재 0.0 dp).

### 4.3 판정 시점과 영역

- AOSP LatinIME의 `PointerTracker`는 touch-down에서 키를 고르고, 이동 중에는 8 dp hysteresis(이력 여유)를 넘어야 다른 키로 바꾸며, touch-up에서 입력을 확정한다.
- Goodman 외(2002)는 펜을 뗀 위치가 판정에 도움이 되지 않았다고 보고했다. Vogel·Baudisch(2007)는 손가락을 뗄 때 굴러서 좌표가 움직인다고 적었다.
- Lertvittayakumjorn 외(2024)는 키 중심에서 가로·세로 ±25% 안의 탭을 모델 없이 그 키로 판정했다.
- LatinIME은 키 사이 간격의 절반씩을 양옆 키에 나눠 주고, 가장자리 키의 판정 영역을 화면 끝까지 넓힌다. Parhi 외(2006)도 오른쪽 열 키를 화면 끝까지 넓히라고 권했다.

**구현.** 누른 순간의 좌표로 키를 정하고 뗄 때 확정한다. 키 중심 ±25% 안의 터치는 보정과 관계없이 그 키다. 키 사이 간격과 줄 사이 간격은 가까운 키에 속하고, 각 행의 첫 키와 마지막 키는 화면 끝까지 판정 영역을 넓힌다(테스트 `touchAreasTileEachRowWithoutOverlapOrHoles`). 시스템 탐색 막대 영역에는 키를 두지 않는다. Android 문서가 일반 탭 전달을 보장하는 범위가 system window insets 바깥이기 때문이다. Android 13부터 들어온 `MotionEvent.FLAG_CANCELED`(손바닥·쥔 손가락 오인식 취소)가 붙은 터치는 입력하지 않는다.

## 5. 두 엄지 rollover 처리

rollover(키 겹침)는 앞 키를 떼기 전에 다음 키가 눌리는 현상이다.

- Dhakal 외(2018)의 물리 키보드 1억 3,600만 타 분석에서 rollover는 평균 25%(표준편차 17%)였고 속도와 상관이 있었다(0.73). 빠른 타자에서는 40–70%였다.
- 터치스크린 두 엄지 입력에서 rollover 빈도를 측정한 연구는 찾지 못했다. Palin 외(2019)는 브라우저 이벤트가 rollover 때 첫 키의 key-up을 두 번째 손가락이 닿는 순간 보내 버려서 타이밍을 분석하지 못했다고 적었다.
- AOSP LatinIME은 어떤 포인터가 떨어질 때 그보다 먼저 눌린 포인터를 모두 확정한다(`PointerTrackerQueue.releaseAllPointersOlderThan`). 수정키는 이 처리에서 빠진다. 수정키가 눌리면 다른 포인터를 먼저 확정한다.

**구현.** `TouchTracker`가 같은 방식을 쓴다. 뒤에 눌린 손가락이 먼저 떨어져도 출력 순서는 누른 순서를 따른다(테스트 `rolloverKeepsTouchDownOrderWhenSecondFingerLiftsFirst`, `threeFingerRolloverCommitsOlderPointersFirst`). Shift, Ctrl, Alt, Fn, Esc/Ctrl은 누른 채 유지되어 chord 입력이 된다.

## 6. 피드백: 햅틱, 소리, 화면 표시

### 6.1 연구 결과

- Brewster, Chohan, Brown(2007): 촉각 피드백을 준 조건에서 입력량이 늘고 오류가 줄었으며 고친 오류가 많았다. 흔들리는 지하철 안에서는 수정되지 않은 오류가 줄고 작업 부하가 낮아졌다.
- Hoggan, Brewster, Johnston(2008): 정확히 입력한 문장 비율이 물리 키보드 88.25%, 촉각 피드백 터치스크린 82.7%, 일반 터치스크린 69.6%였다. 물리 키보드와 촉각 조건 사이의 차이는 유의하지 않았다.
- Ma, Edge, Findlater, Tan(2015): 평평한 키보드에서 햅틱 keyclick(키 클릭)이 속도를 29.0–33.0 WPM에서 36.5–38.5 WPM으로 올리고 전체 오류율을 8.9%·8.2%에서 7.3–7.9%로 낮췄다. 햅틱이 있을 때 소리 클릭의 효과는 유의하지 않았다.
- Kaaresoja, Brewster, Lantz(2014): 누름과 피드백이 동시에 느껴지는 지연은 촉각 5 ms, 소리 19 ms, 화면 32 ms였다. 권장 범위는 촉각 5–50 ms, 소리 20–70 ms, 화면 30–85 ms다. 촉각과 소리는 70–100 ms 사이에서 품질 평가가 유의하게 떨어졌다.
- Android 햅틱 설계 문서: 좋은 keyclick은 10–20 ms이고, "buzzy(웅웅거리는) 햅틱과 햅틱 없음 중에서는 햅틱 없음을 고르라"고 적는다.

### 6.2 Android 구현 근거(AOSP 16 소스 확인)

- `View.performHapticFeedback`은 뷰가 `TYPE_INPUT_METHOD` 창에 있으면 `PRIVATE_FLAG_APPLY_INPUT_METHOD_SETTINGS`를 붙인다(`View.java`).
- `HapticFeedbackVibrationProvider`는 `KEYBOARD_TAP`을 `PRIMITIVE_CLICK`(없으면 `EFFECT_CLICK`)으로, `KEYBOARD_RELEASE`를 `PRIMITIVE_TICK`(없으면 fallback(대체 효과) 없는 `EFFECT_TICK`)으로 바꾸고 키보드용 진동 속성을 붙인다.
- `VibrationAttributes.USAGE_IME_FEEDBACK`은 AOSP에 feature flag(기능 플래그)로 감싼 API(Application Programming Interface)로만 있고 공개 SDK(Software Development Kit) 문서에는 없다.
- `performHapticFeedback`은 VIBRATE 권한이 필요 없고 사용자 설정을 따른다. `FLAG_IGNORE_GLOBAL_SETTING`은 API 33에서 폐기되었다.
- AOSP LatinIME은 햅틱과 소리를 touch-down에서 내고, 키 반복 중에는 햅틱을 내지 않는다.

**구현.** 기본값은 touch-down 처리의 첫 단계에서 `performHapticFeedback(KEYBOARD_TAP)`을 부르는 것이다. 사용자의 시스템 키보드 진동 설정을 그대로 따른다. 설정에서 "틱", "클릭"을 고르면 `Vibrator`로 `EFFECT_TICK`, `EFFECT_CLICK`을 직접 재생한다(VIBRATE 권한 사용). 반복 입력에는 햅틱이 없다. 밀기 결과가 바뀔 때와 스페이스 드래그로 커서가 움직일 때는 `CLOCK_TICK`을 쓰고, 커서 이동 중에는 40 ms에 한 번으로 제한한다. 소리는 기본 꺼짐이고, 켜면 일반 벨소리 모드에서만 `FX_KEYPRESS_*`를 재생한다.

### 6.3 화면 표시

- AOSP LatinIME은 smallest width 600dp 이상(태블릿)에서 키 팝업을 끄고 설정 항목도 숨긴다.
- Vogel·Baudisch(2007)의 Shift 기법은 작은 대상에서만 가려진 영역을 확대해 보여 준다.
- Henze 외(2012)에서 터치 위치에 점을 찍어 보여 주면 오류가 18.3% 줄었지만 속도가 5.2% 느려졌다.
- Kim, Yi, Yoon(2019)에서 현재 단어를 키 근처 팝업으로 보여 주자 숙련도가 낮은 사용자의 속도가 11% 올랐다.
- iPadOS 전체 키보드에 팝업이 없다는 주장은 Apple 문서에서 확인하지 못했다.

**구현.** 탭 미리보기는 "자동"(600dp 미만 화면에서만 켬)이 기본이다. 폴드7 내부 화면에서는 꺼지고 커버 화면에서는 켜진다. 밀기와 길게 누르기의 결과 미리보기는 항상 표시한다. 아래로 밀면 손을 떼기 전에 `^C`, `^D` 같은 결과가 뜨고, 손가락을 되돌리면 탭으로 바뀐다. 터미널에서 Ctrl+D는 셸을 닫을 수 있으므로 떼기 전에 결과를 보여 주는 쪽을 택했다. 상단 줄에는 최근에 보낸 특수키(`Esc`, `^C`, `M-b`, `Tab`, `⏎`, 방향키)를 표시한다. 터미널은 이런 키를 화면에 되돌려 보여 주지 않는 경우가 많아서다.

## 7. 코드와 터미널 입력

### 7.1 코드의 기호 빈도(직접 측정)

이 작업 환경(Ubuntu 24.04)에 설치된 소스로 측정했다. 주석과 docstring은 빼고, 줄 앞 들여쓰기는 지웠다. Go는 `Code generated ... DO NOT EDIT.` 표시가 있는 생성 파일과 `testdata`, `_test.go`를 뺐다. 측정 스크립트는 [tools/symbol_freq.py](tools/symbol_freq.py)이고, 경로를 인자로 바꿔 다른 말뭉치에도 쓸 수 있다.

| 말뭉치 | 파일 수 | 문자 수 | Shift가 필요한 문자 비율 | 그중 대문자 | 그중 Shift 기호 |
|---|---|---|---|---|---|
| Python 3.12 표준 라이브러리 | 516 | 5,434,724 | 11.45% | 3.57% | 7.88% |
| Python 서드파티(pypdf, fontTools 등) | 587 | 6,086,890 | 14.49% | 5.07% | 9.41% |
| Go 1.24.7 표준 라이브러리 | 3,275 | 15,592,205 | 18.51% | 8.62% | 9.89% |
| 셸 스크립트(`/usr`, `/etc` 등) | 754 | 1,768,891 | 24.50% | 10.86% | 13.65% |

전체 문장부호 가운데 비율(상위 항목):

- Python 표준 라이브러리: `_` 15.4%, `'` 15.2%, `(` `)` 각 8.9%, `.` 8.9%, `,` 8.8%, `:` 7.8%, `=` 7.2%, `"` 3.9%, `\` 3.5%, `-` 3.0%
- Go: `,` 14.2%, `.` 13.0%, `(` `)` 각 11.2%, `=` 8.5%, `"` 5.8%, `{` `}` 각 5.6%, `:` 5.4%, `_` 3.3%
- 셸: `"` 15.8%, `_` 11.7%, `$` 10.6%, `-` 8.1%, `=` 5.4%, `;` 5.4%, `/` 4.5%, `.` 3.9%, `)` 3.5%, `'` 3.1%, `|` 3.0%

Black 포매터를 쓰는 서드파티 Python에서는 `"`가 1위(19.6%)였다. 따옴표 선택은 프로젝트 규칙에 따라 바뀐다. Xah Lee의 블로그 집계(동료 심사 없음)도 Python에서 `_ . ' ( ) ,`를 상위로 보고해 순위가 비슷했다.

**해석과 결정.** 코드에서 Shift 기호가 전체 문자의 7.9–13.7%를 차지한다. Shift를 따로 누르는 방식이면 이 문자마다 두 번 눌러야 한다. FoldKey는 모든 Shift 기호를 해당 키의 위로 밀기 한 번으로 낸다(`9`↑ = `(`, `-`↑ = `_`, `'`↑ = `"`, `4`↑ = `$`, `\`↑ = `|`). 기호 레이어를 두지 않은 이유는 Greene 외(2014)의 결과다. 문자열 하나를 입력할 때 키보드 화면 전환이 한 번 늘면 키 입력이 하나 줄어도 3초 넘게 느려졌다. 저자들은 화면 전환을 작은 작업 중단으로 설명했다.

### 7.2 PC 배열 유지

Bi, Smith, Zhai(2010)의 초보자 첫 단어 입력 시간은 QWERTY 2,110 ms, 글자를 한 칸 안에서만 옮긴 quasi-QWERTY 3,234 ms, 자유 배치 4,705 ms였다. 저자들은 사용자가 배우려 하지 않는다는 점을 지적했다. FoldKey는 글자뿐 아니라 기호 키의 PC 위치(ANSI)도 그대로 둔다. 물리 키보드에서 익힌 기호 위치를 그대로 쓸 수 있다.

### 7.3 수정키 방식

Fennedy 외(2020)의 소프트 키보드 단축키 실험에서 두 손 선택 시간은 "Once"(수정키를 한 번 탭하고 키 누르기) 0.85초, 누른 채 입력 0.98초, 수정키에서 밀기 1.04초였다. 정확도는 각각 99.5%, 99.5%, 95.0%였고, 자유 선택에서 86.2%가 Once를 골랐다.

FoldKey의 Shift, Ctrl, Alt는 상태 3가지를 가진다. Fn은 0.4.1부터 다르게 동작한다(7.9절).

- 한 번 탭하면 다음 키 한 번에 적용된다(one-shot).
- 350 ms 안에 두 번 탭하면 고정된다(lock). 다시 탭하면 풀린다.
- 누른 채 다른 키를 치면 그 동안만 적용된다(chord). 이때는 손을 떼도 one-shot으로 남지 않는다.

고정된 Shift는 글자에만 적용되는 Caps Lock으로 동작한다. `MAX_SIZE_2` 같은 상수를 칠 때 숫자가 기호로 바뀌지 않게 하려는 선택이다. 한글 모드에서는 고정 Shift가 쌍자음을 만들지 않는다. libhangul 문서도 Caps Lock을 Shift처럼 다루지 말라고 적는다.

### 7.4 Esc와 Ctrl의 자리

- ADM-3A 터미널 매뉴얼의 키보드 그림(3-1)에서 ESC(escape)는 Q 왼쪽(지금의 Tab 자리), CTRL(control)은 A 왼쪽(지금의 Caps Lock 자리)에 있고 H·J·K·L 키에 화살표가 인쇄되어 있다.
- Linux의 xkeyboard-config는 `ctrl:nocaps`(Caps Lock을 Ctrl로), `caps:escape`(Caps Lock을 Esc로) 옵션을 제공한다. keyd README의 `capslock = overload(control, esc)`는 "tapped면 escape, held면 control"이다. xcape와 Karabiner-Elements의 `to_if_alone`도 같은 동작이며, 누르는 동안 다른 키가 눌리면 Esc를 취소한다.
- Hacker's Keyboard는 Caps Lock 자리에 Ctrl을 둔다.

**구현.** Caps Lock 자리의 키는 dual-role이다. 500 ms 안에 다른 키 없이 떼면 Esc, 누른 채 다른 키를 치면 Ctrl이다. Tab은 셸 자동 완성에 자주 쓰므로 원래 자리에 둔다. 한 손으로 Ctrl 조합을 치려면 맨 아래 행의 Ctrl(one-shot)이나 아래로 밀기를 쓴다. Esc는 Ctrl+[로도 보낼 수 있다. ASCII(American Standard Code for Information Interchange)에서 제어 문자는 문자 코드와 0x1F의 AND로 만들고, `[`(0x5B) & 0x1F = 0x1B(ESC)이기 때문이다.

### 7.5 터미널이 키보드 입력을 받는 방식(Termux 소스 확인)

Android 키보드 앱은 IME(Input Method Editor)라는 서비스로 동작하고, 앱과는 `InputConnection`으로 글자와 키 이벤트를 주고받는다.

- `TerminalView.onCreateInputConnection`은 기본으로 `InputType.TYPE_NULL`을 요청한다. `enforce-char-based-input` 설정을 켜면 `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | TYPE_TEXT_FLAG_NO_SUGGESTIONS`를 쓴다. 소스 주석은 주로 삼성 기본 키보드 문제 때문이라고 적는다. 둘 다 input class(입력 종류) 비트가 0이다.
- 연결 객체는 `BaseInputConnection(this, true)`이고 `commitText`, `finishComposingText`만 터미널로 글자를 보낸다. `setComposingText`로 보낸 조합 중 글자는 화면에 그리지 않는다.
- `commitText`로 들어온 0x1F 이하 제어 문자(ESC 제외)는 Ctrl+글자로 되돌린 뒤 다시 제어 문자로 바뀐다. NUL(null 문자, 0x00)은 이 과정에서 백틱이 되므로 Ctrl+Space 키 이벤트로 보내야 한다.
- `onKeyDown`은 Ctrl을 `isCtrlPressed()`로 읽고, 글자 앞에 ESC를 붙이는 Alt는 `META_ALT_LEFT_ON` 비트가 있어야 인식한다.
- 방향키, Home/End, F1–F12, PgUp(Page Up)/PgDn(Page Down), Insert/Delete는 `KeyHandler`가 DECCKM(Digital Equipment Corporation cursor key mode, 커서 키 모드)과 xterm 수정키 코드를 반영해 이스케이프 시퀀스로 바꾼다. 그래서 IME는 시퀀스를 직접 만들지 말고 keycode를 보내야 한다.
- Termux는 Ctrl과 Alt가 함께 눌린 키 이벤트를 모두 자체 단축키(세션 전환 등)로 가져간다.

**구현.** `(inputType & TYPE_MASK_CLASS) == TYPE_NULL`이거나 설정의 터미널 앱 목록(기본: `com.termux`, `org.connectbot`, `com.sonelli.juicessh`, `jackpal.androidterm`)에 있는 앱이면 raw(가공하지 않은 입력) 모드로 다룬다.

- 글자는 `commitText`로 보낸다.
- Esc, Tab, Enter, 방향키, F키, Backspace는 keycode로 보낸다.
- Ctrl과 Alt는 `META_CTRL_ON | META_CTRL_LEFT_ON`, `META_ALT_ON | META_ALT_LEFT_ON`을 붙인 KeyEvent로 보내고, Hacker's Keyboard와 Unexpected Keyboard처럼 `KEYCODE_CTRL_LEFT` 등 수정키 자체의 down/up으로 감싼다.
- raw 모드에서 Ctrl+Alt+글자는 ESC와 제어 문자를 이어 붙인 문자열로 commit(확정 전송)한다. Termux가 Ctrl+Alt 키 이벤트를 가져가기 때문이다.
- raw 모드의 Enter는 편집기 동작(`performEditorAction`)이 아니라 `KEYCODE_ENTER`다.

일반 입력창에서의 Ctrl 조합도 확인했다. AOSP 16 `KeyCharacterMap::matchesMetaState`는 이벤트에 Ctrl, Alt, Meta가 있으면 키 동작 정의가 그 수정키를 정확히 포함해야 문자를 돌려준다. `Virtual.kcm`의 글자 키에는 ctrl 정의가 없다. 그래서 Ctrl+A는 글자를 만들지 않고 `ViewRootImpl`의 단축키 단계로 넘어가며, `TextView.onKeyShortcut`이 Ctrl+A/Z/X/C/V/Y를 전체 선택, 실행 취소, 잘라내기, 복사, 붙여넣기, 다시 실행으로 처리한다.

### 7.6 자동 수정을 쓰지 않는 이유

Lertvittayakumjorn 외(2024)에서 언어 모델은 영어 문장의 문자 오류를 절반으로 줄였지만(6.04% → 3.02%) 무작위 문자열에서는 두 배 넘게 늘렸다(3.55% → 7.78%). 저자들은 입력이 사전 단어가 아닐 때 언어 모델을 끄라고 권했다. Goel 외(2013, ContextType)에서는 개인화 터치 모델이 있으면 언어 모델의 추가 효과가 유의하지 않았다. 명령어, 경로, 식별자는 사전 단어가 아니므로 FoldKey에는 자동 수정과 단어 예측이 없다. 오타는 4.2절의 터치 편차 보정으로 줄인다.

### 7.7 기존 키보드와의 비교

| 키보드 | 특징 | FoldKey에서 가져온 것과 다른 점 |
|---|---|---|
| Hacker's Keyboard | 5행 PC 배열, Caps Lock 자리 Ctrl, Esc 왼쪽 아래 | 수정키를 meta state와 수정키 down/up으로 보내는 방식을 따른다 |
| Unexpected Keyboard | 키 모서리로 밀어 기호 입력, Termux용으로 설계 | 밀기 입력은 가져오되, 기호를 PC 키캡의 Shift 위치에 둔다 |
| Termux extra keys | 기본 `ESC / - HOME UP END PGUP`, `TAB CTRL ALT LEFT DOWN RIGHT PGDN` | 같은 키를 모두 한 동작 안에 둔다 |
| Blink Shell(iOS) | 화면 키보드 위 Smart Keys, 수정키 연속 입력 | 수정키 chord와 one-shot을 모두 지원한다 |

### 7.8 빠진 문자와 키 검토(0.2.0)

확인 방법과 결과:

- **ASCII.** 95자는 세 배열 모두에서 입력된다. 테스트 `everyLayoutReachesTheSameKeySet`이 글자 키 47개와 Shift 대응을 검사한다.
- **특수 키.** Termux extra keys가 이름으로 지원하는 키(`ExtraKeysConstants.java`: SPACE, ESC, TAB, HOME, END, PGUP, PGDN, INS, DEL, BKSP, 방향키, ENTER, F1–F12) 가운데 0.1.0에 없던 것은 INS 하나였다. Unexpected Keyboard는 Fn+Esc에 Insert를 둔다(`KeyModifier.apply_fn_keyevent`).
- **한글 문장 부호.** 국립국어원 「문장 부호 해설」(2015)의 21종 가운데 ASCII에 없는 것은 가운뎃점, 큰따옴표·작은따옴표(“ ” ‘ ’), 겹낫표·겹화살괄호, 홑낫표·홑화살괄호, 줄표, 물결표(∼), 드러냄표, 숨김표(○ ×), 빠짐표(□), 줄임표다. 0.1.0에는 이 가운데 아무것도 없었다.
- **코드 문서용 기호.** Unexpected Keyboard의 Fn 표(`srcs/compose/fn.json`)는 `.`→…, `,`→·, `-`→–, `_`→—, `=`→≈, `*`→°, 통화 기호를 둔다. 0.1.0에는 비ASCII 문자가 하나도 없었다.
- **제어 문자.** 터미널에서 Ctrl+Alt 조합을 ESC와 제어 문자로 바꾸는 표가 글자와 `[ \ ] ^ _`만 다뤘다. Termux `TerminalView.inputCodePoint`는 Ctrl과 함께 Space·2 → 0, 3 → 27, 4 → 28, 5 → 29, 6 → 30, 7·/ → 31, 8 → 127로 바꾼다.
- **길게 누르기.** 길게 누르기가 윗글자 입력이라서, vim에서 `j`를 누르고 있으면 반복 대신 `J`(줄 합치기)가 들어갔다.
- **한자 변환.** 없다. 사전 데이터가 필요해서 이번 범위에서 뺐다.

**결정.**

- 비ASCII 기호는 Fn 레이어에 둔다. Fn을 켜면 키 표시가 기호로 바뀌므로 위치를 외우지 않아도 된다. 한 키에 Fn 누르기와 Fn + 위로 밀기 두 가지를 둔다. 0.2.0–0.3.0에서는 테스트가 세 배열 모두에 같은 53개가 한 번씩 있는지, 문장 부호가 빠지지 않았는지 확인했다. 0.4.0에서 사용자 요청으로 모두 뺐다(7.9절).
- 글자 모양은 KS X 1001의 Unicode 대응을 따른다. Python `euc_kr` 코덱으로 KS X 1001의 기호 영역(0xA1–0xAC 행, 987자)을 뽑아 대조했다. 1행에 `、。·‥…¨〃―∥＼∼‘’“”〔〕〈〉《》「」『』【】±×÷≠≤≥∞∴°′″`가 있다. 그래서 가운뎃점은 U+00B7, 줄표는 U+2015, 물결표는 U+223C, 화살괄호는 U+3008–300B, 낫표는 U+300C–300F다. 영문 em dash(U+2014)는 따로 둔다.
- Fn+Esc는 Insert, Shift를 함께 켜면 Shift+Insert다. 터미널의 Ctrl+Alt 제어 문자 표는 Termux와 같게 넓히고, 관례상 같은 코드인 `@`(NUL)와 `?`(DEL)를 더했다(테스트 `ctrlAltDigitsSpaceAndSlashFollowTermuxControlMapping`).
- 길게 누르기 동작을 "윗글자"와 "반복 입력" 가운데 고르게 했다. 기본은 윗글자다.
- 스페이스를 위아래로 끌면 ↑/↓를 보낸다. 셸 history와 vim 줄 이동에 쓴다. 처음 움직인 방향(세로는 가로의 1.2배 이상일 때)으로 고정되고, 4 mm마다 한 줄이다.

### 7.9 Fn 숫자판(0.4.0)

사용자는 Fn을 누르면 오른쪽에서 숫자판처럼 숫자를 입력하게 하고, 겹치는 키와 쓰지 않는 특수문자를 정리해 달라고 요청했다. 이 키보드의 용도(Python, 셸, vim, Go, 터미널)에 따라 특수문자 묶음은 모두 빼기로 했다. 숫자판은 키 자리에 숫자를 겹치는 노트북식 대신 곧은 별도 숫자판을 골랐다.

- 배치는 PC 숫자 패드처럼 7이 위에 온다. `7 8 9 / ⌫`, `4 5 6 * (`, `1 2 3 - )`, `0 . = + ⏎`이다(테스트 `numpadIsSevenEightNineOnTopWithOperatorsAndEnter`).
- 숫자판은 위 네 줄의 오른쪽 끝 다섯 칸에 글자 키와 같은 폭으로 놓인다(폴드7 세로 분할 7.93 mm). 같은 줄에서 `h` 키부터 오른쪽 끝까지의 키는 숨기고, 숫자판과 왼쪽 부분 사이는 비워 둔다. 맨 아래 줄(스페이스, 한/A, 방향키)과 왼쪽 부분은 그대로다(테스트 `fnSwapsTheRightPartOfTheTopFourRowsForAStraightNumpad`).
- 처음에는 `h` 키부터 오른쪽 끝까지를 숫자판으로 채웠다(키 폭 1.65칸, 13.1 mm). 사용자가 렌더링을 보고 숫자판이 가운데에 너무 가까워 치기 힘들다고 알려, 오른쪽 끝으로 옮기고 키 폭을 글자 키와 같게 줄였다. `7 4 1 0` 열의 중심은 폴드7 세로 분할에서 오른쪽 끝으로부터 59.4 mm에서 36.2 mm가 되었다(테스트 `splitNumpadSitsAtTheRightEdgeWithLetterSizedKeys`).
- 키 판정은 누른 순간의 Fn 상태로 정한다. Fn을 누른 채 숫자를 치다가 Fn을 먼저 떼도 그 숫자가 들어간다.
- 0.4.0에서는 Fn이 다른 수정키처럼 one-shot이라 숫자 하나 뒤 원래 배열로 돌아왔고, 여러 자리는 두 번 눌러 고정해야 했다. Fn의 역할이 숫자판뿐이므로 사용자 요청에 따라 0.4.1부터 Fn은 누를 때마다 숫자판을 켜고 끄는 전환 키다. 누른 채 치면 손을 뗄 때 꺼진다(테스트 `fnLatchesUntilTappedAgainWhileOtherModifiersStayOneShot`, `fnTapSwitchesToTheNumpadUntilFnIsTappedAgain`, `numpadStaysThroughDigitsAndBackspaceUntilFnIsTappedAgain`, `heldFnShowsTheNumpadOnlyWhileHeld`). 숫자판이 켜져 있는 동안 Esc 자리는 Insert다.
- 0.4.0의 첫 에뮬레이터 시험에서는 one-shot Fn과 숫자판 ⌫의 조합 때문에 결과가 어긋났다. ⌫는 수정키 상태가 붙지 않으면 one-shot 수정키를 쓰지 않으므로 Fn이 켜진 채 남았고, 시험 순서가 그 뒤에 Fn을 다시 눌러 Fn을 껐다. 다음 두 탭이 일반 배열의 ⌫와 `u`로 들어가 `7+(u` 대신 `7u`가 되었다. 0.4.1의 전환 방식에서는 이 순서가 생기지 않는다.
- 숫자판 키는 Shift·Ctrl·Alt와 상관없이 표시된 글자를 `commitText`로 보낸다. 밀기와 길게 누르기 동작이 없으므로 빠르게 치다 손가락이 미끄러져도 Ctrl+숫자가 나가지 않는다. 숫자판 ⌫는 누르고 있으면 반복하고, 위로 밀면 Del이다.
- 분할 배열에서는 숫자판이 나온 줄의 복제 키를 없애 가운데 빈 공간을 눌러도 아무것도 입력되지 않게 했다. 숫자판 왼쪽의 빈 자리도 같다. 오른쪽 끝 열의 판정 영역만 화면 끝까지 넓힌다.
- 숫자판을 누른 위치는 터치 편차 학습에 넣지 않는다. 키 폭이 글자 키와 달라서 같은 구역의 글자 키 학습값이 흐려지기 때문이다.
- 뺀 Fn 조합은 특수문자 53개, Fn+숫자 행(F1–F12, 숫자 행 아래로 밀기와 겹침), Fn+방향키(방향키 위로 밀기와 겹침), Fn+h j k l·y o·u i(방향키, 방향키 위로 밀기와 겹침), Fn+⌫(Del, 숫자판 ⌫ 위로 밀기로 옮김)다. Fn+Esc(Insert)는 남겼다.
- 커버 화면에도 숫자판을 넣었다(사용자 요청). 커버 화면 배열은 폭이 10칸이라 오른쪽 반쪽(`h`부터)이 그대로 숫자판이 된다(테스트 `coverScreenGetsTheNumpadOnTheRightHalf`). 커버 화면 배열에는 방향키가 없어 0.3.0까지 Fn + h j k l이 유일한 방향키였고, Fn + y o, u i가 Home·End·PgDn·PgUp이었다. 숫자판이 이 키들을 덮으므로 이 조합은 없어졌다. 커서는 스페이스를 끌어 옮기고, Home·End·PgUp·PgDn은 커버 화면에서 입력할 수 없다. 테스트 `everyLayoutReachesTheSameKeySet`은 커버 화면에 이 키코드가 없다는 것을 명시적으로 확인한다.

### 7.10 스페이스로 선택(0.4.0)

사용자 요청에 따라 스페이스를 꾹 누른 뒤 끌면 글자를 선택한다.

- 길게 누르기 시간(기본 400 ms, 설정이 0이면 400 ms)이 지나면 스페이스 키 색이 바뀌고 햅틱이 한 번 울린다. 그 뒤 처음 움직인 방향으로 축이 고정되고, 좌우는 2.5 mm마다 Shift+←/→, 위아래는 4 mm마다 Shift+↑/↓를 보낸다. 움직이지 않고 떼면 아무것도 입력하지 않는다(테스트 `spaceHeldStillThenDraggedSelectsInsteadOfTyping`, `spaceHeldAndReleasedWithoutMovingTypesNothing`).
- 시간이 지나기 전에 3 mm 넘게 움직이면 0.3.0과 같이 선택 없이 커서만 움직인다. 시간이 지나기 전에 다른 키를 누르면 스페이스가 먼저 입력된다(테스트 `spaceDragBeforeTheHoldMovesTheCursorAndNeverStartsSelecting`, `anotherKeyPressedDuringTheHoldTypesSpaceFirst`).
- 축을 처음 방향으로 고정하는 방식은 0.2.0의 커서 끌기와 같다. 두 축을 함께 받으면 좌우로 끄는 동안 생기는 위아래 흔들림이 줄 선택으로 들어간다.
- 일반 입력창에서는 Shift를 붙인 방향키 이벤트를 보낸다. Robolectric(Android 16 프레임워크 코드)의 `EditText`에서 Shift+← 6번이 6글자를 선택했고, 이어 입력한 글자가 선택 영역을 바꿨다(테스트 `selectionDragExtendsTheEditTextSelection`). Robolectric의 기본 그래픽 모드에서는 글자 폭을 재지 못해 Shift+← 한 번에 줄 처음까지 선택되었으므로, 이 테스트는 네이티브 그래픽 모드로 돈다.
- 터미널(TYPE_NULL)에서는 Shift 없이 방향키만 보낸다. Termux `KeyHandler.java`는 Shift+←를 `ESC [1;2D`로 바꿔 보내는데, 기본 설정의 셸은 이 순서열을 선택으로 쓰지 않는다.

## 8. 한글 입력

### 8.1 표준과 자판

KS X 5002(정보 처리용 건반 배열)는 1982년 6월 17일 제정, 2007년 10월 24일 개정, 2023년 12월 8일 확인되었다. 원래 번호는 KS C 5715였고, 정보 처리 분야 표준 번호가 1997년 8월 20일 C에서 X로 옮겨졌다. libhangul의 `hangul_keyboard_table_2`와 Microsoft 문서의 배치는 다음과 같다.

| 행 | 기본 | Shift |
|---|---|---|
| 윗줄 | q ㅂ, w ㅈ, e ㄷ, r ㄱ, t ㅅ, y ㅛ, u ㅕ, i ㅑ, o ㅐ, p ㅔ | Q ㅃ, W ㅉ, E ㄸ, R ㄲ, T ㅆ, O ㅒ, P ㅖ |
| 가운데 줄 | a ㅁ, s ㄴ, d ㅇ, f ㄹ, g ㅎ, h ㅗ, j ㅓ, k ㅏ, l ㅣ | 같음 |
| 아랫줄 | z ㅋ, x ㅌ, c ㅊ, v ㅍ, b ㅠ, n ㅜ, m ㅡ | 같음 |

폴드7의 8인치 화면에서 천지인 같은 12키 방식을 쓸 이유는 찾지 못했다. 두벌식 사용 비율이 높고(스마트워치 연구 참가자 32명 중 21명이 휴대폰에서 두벌식 사용, Ilinkin·Kim 2017), 터치스크린 휴대폰이나 태블릿에서 두벌식, 천지인, 나랏글의 속도와 오류를 비교한 동료 심사 연구는 찾지 못했다.

### 8.2 조합 규칙

Unicode 표준 3.12절의 식을 쓴다. 음절 = 0xAC00 + (초성 × 21 + 중성) × 28 + 종성이고, 초성 19개, 중성 21개, 종성 27개(없음 포함 28)로 11,172개 음절이 된다. 조합 규칙은 libhangul의 기본값과 같다.

- 겹모음 7개: ㅗ+ㅏ=ㅘ, ㅗ+ㅐ=ㅙ, ㅗ+ㅣ=ㅚ, ㅜ+ㅓ=ㅝ, ㅜ+ㅔ=ㅞ, ㅜ+ㅣ=ㅟ, ㅡ+ㅣ=ㅢ. ㅏ+ㅣ는 합치지 않는다.
- 겹받침 11개: ㄳ ㄵ ㄶ ㄺ ㄻ ㄼ ㄽ ㄾ ㄿ ㅀ ㅄ. 같은 자음을 두 번 눌러 쌍자음을 만드는 동작은 libhangul 기본값처럼 끈다.
- ㄸ, ㅃ, ㅉ는 받침이 될 수 없으므로 새 음절을 시작한다(가 + ㄸ + ㅏ → 가따).
- 도깨비불 현상: 받침 뒤에 모음이 오면 받침이 다음 음절의 초성으로 옮겨 간다. 겹받침은 뒤쪽 자음만 옮긴다(값 + ㅏ → 갑사, 맑 + ㅗ → 말고).
- 모음 없이 겹받침이 되는 두 자음(ㄱ+ㅅ)은 ㄳ 하나로 보인다. 뒤에 모음이 오면 ㄱ + 사로 나뉜다. libhangul이 MS IME(Microsoft Input Method Editor) 호환을 위해 켜 둔 동작이다.
- backspace는 입력한 자모를 하나씩 되돌린다(관 → 과 → 고 → ㄱ). 조합이 비어 있을 때만 앞 글자를 지운다. 이미 확정한 음절을 다시 열지 않는다.

이 규칙은 libhangul 테스트의 입력열(akfr → 맑, akfrh → 말고, qjTm → 버쓰, rkl → 가ㅣ, rkW → 가ㅉ)과 FoldKey 테스트(`libhangulReferenceSequences`)로 맞춰 보았다. 초성·중성·종성 표의 순서는 Unicode 문자 이름으로, 조합식은 11,172개 음절 전부에 대해 Java `Normalizer`의 NFC(Normalization Form C) 결과와 비교했다.

### 8.3 터미널과 한글

- Termux는 조합 중인 글자(preedit)를 그리지 않는다. 음절이 확정되어야 글자가 나타난다.
- 조합 중에 키 이벤트를 먼저 보내면 셸에는 키가 음절보다 먼저 도착한다. Windows Terminal 이슈 #20038("Arrow key during composition inserts character at wrong position")이 같은 종류의 문제였다.
- 터미널 글자 폭: glibc `wcwidth`는 호환 자모(U+3131–U+318E)와 완성형 음절을 2칸, 조합형 중성·종성(U+1160–U+11FF)을 0칸으로 계산한다. Termux의 `WcWidth.java`는 U+1160–11FF를 넓은 문자로 보지 않는다. 그래서 터미널에는 완성형 음절과 호환 자모만 보내야 한다.

**구현.** raw 모드에서는 `setComposingText`를 쓰지 않는다. 조합 중인 음절은 키보드 상단 줄에 밑줄과 함께 표시하고, 음절이 확정될 때 `commitText`로 보낸다. Enter, 방향키, Esc, Ctrl 조합 같은 키 이벤트를 보내기 전에는 조합 중인 음절을 먼저 확정한다(테스트 `hangulInTerminalUsesPreeditStripAndCommitsFinishedSyllables`). 일반 입력창에서는 `setComposingText`와 `commitText`로 조합을 보여 준다. 커서가 앱 쪽에서 움직이면(`onUpdateSelection`) 조합을 끝낸다.

### 8.4 vim과 한글 모드

vim의 normal mode(일반 모드) 명령은 ASCII 글자라서, 한글 모드로 Esc를 누른 뒤 `dd`를 치면 `ㅇㅇ`이 들어간다. 데스크톱에서는 입력기가 이를 처리한다.

- ibus-hangul은 `off-keys` 기본값이 Escape다. 소스 주석은 "This feature is for vi* users"이고, Esc를 소비하지 않고 vi에 넘긴다.
- macOS용 구름 입력기에는 "Esc 키로 로마자 자판으로 전환 (vi 모드)" 옵션이 있다(기본 꺼짐). Esc와 Ctrl+[에 반응한다.
- vim-xkbswitch, im-select 같은 플러그인은 운영체제의 입력기를 바꾼다. Termux 안의 vim은 Android IME를 바꿀 수 없다.

**구현.** Esc 또는 Ctrl+[를 보내면 조합 중인 음절을 확정하고, 키를 그대로 보낸 뒤 영문 모드로 바꾼다(기본 켜짐). Ctrl이나 Alt가 켜져 있으면 한글 모드여도 조합하지 않고 라틴 keycode를 보낸다(`ctrlInHangulModeSendsLatinKey`). 터미널, 비밀번호, URL(Uniform Resource Locator), 이메일, 숫자 입력창과 `IME_FLAG_FORCE_ASCII`가 붙은 입력창은 영문으로 시작한다. 일반 입력창은 마지막으로 고른 언어로 시작한다. 한/영 전환은 `한/A` 키가 맡는다. 데스크톱 관행인 Shift+Space 전환은 넣지 않았다. 터미널에서 실수로 한글 모드가 되면 이어지는 명령이 한글로 들어가기 때문이다.

## 9. Android 플랫폼 요구 사항

AOSP 16(android-16.0.0_r1) 소스와 Android 문서에서 확인한 사항이다.

- **IME 창과 insets(가장자리 점유 영역).** `InputMethodService.onCreate()`는 IME 창에 `Gravity.BOTTOM`, `setFitInsetsTypes(statusBars() | navigationBars())`, `setFitInsetsSides(Side.all() & ~Side.BOTTOM)`을 설정한다. 창이 아래쪽 탐색 막대 밑까지 내려간다는 뜻이다. targetSdk 35 이상에서는 edge-to-edge(화면 끝까지 그리기)가 강제되어 콘텐츠 뷰가 insets를 그대로 받는다. FoldKey는 `systemBars().bottom`만큼 아래를 비우고 그 영역에는 키를 두지 않는다. 좌우는 `displayCutout()`을 반영한다. 키보드 배경이 밝으면 `APPEARANCE_LIGHT_NAVIGATION_BARS`를 설정한다.
- **전체 화면 모드.** 기본 `onEvaluateFullscreenMode()`는 가로 방향이면 전체 화면 추출 모드를 켠다. 폴드7 내부 화면 가로 자세에서 앱이 가려지므로 항상 `false`를 돌려준다.
- **구성 변경.** Android 문서에 따르면 UPSIDE_DOWN_CAKE부터 CINNAMON_BUN 사이 버전에서는 IME가 선언한 `configChanges`가 XML(Extensible Markup Language) 파싱 오류로 무시된다. 화면 전환(내부 ↔ 커버) 때 입력 뷰가 다시 만들어지므로 `onCreateInputView`에서 매번 새 뷰를 만든다.
- **뒤로 가기.** Android 16에서 targetSdk 36 앱은 predictive back(예측형 뒤로 가기)이 기본이다. `InputMethodService`가 자체 콜백으로 키보드를 닫으므로 추가 코드가 없다.
- **Enter.** `EditorInfo.IME_FLAG_NO_ENTER_ACTION`이 없고 동작이 `IME_ACTION_NONE`이 아니면 `performEditorAction`을 부른다. `TextView`는 여러 줄 입력창에 이 플래그를 자동으로 붙이므로 줄바꿈이 된다.
- **도구 버전.** AGP(Android Gradle Plugin) 9.4.1은 Gradle 9.6.0 이상이 필요하다. 저장소의 Gradle wrapper는 9.8.0이다. Robolectric 4.17은 API 23–37을 지원하며 API 36·37에는 JDK(Java Development Kit) 21이 필요하다.

## 10. 검증 방법과 한계

### 10.1 수행한 검증

- **JVM(Java Virtual Machine) 단위 테스트.** 한글 조합기, 수정키 상태, 입력 엔진, 터치 추적(rollover, 밀기, 반복, 커서 드래그, 취소), 터치 편차 모델, 동작 해석, 배열의 키 구성과 mm 치수를 확인한다.
- **독립 자료와의 대조.** 한글 표는 Unicode 문자 이름과 NFC 결과로 확인했다. 배열 치수는 삼성이 공개한 해상도와 ppi로 계산한 값과 비교한다.
- **Robolectric 통합 테스트.** 실제 `EditText`의 `InputConnection`에서 한글 조합, backspace, 여러 줄 Enter를 확인했다. TYPE_NULL 입력창(설정 화면의 키 이벤트 확인창)에서 확정 음절, Esc, Ctrl+C, Alt+B, Enter가 보낸 순서대로 도착하는지 확인했다.
- **렌더링.** Robolectric의 네이티브 그래픽으로 폴드7 내부 화면(1968 px, 2184 px 폭, 368 ppi)과 커버 화면(1080 px, 422 ppi) 크기의 키보드를 PNG(Portable Network Graphics)로 그려 배치를 눈으로 확인했다.
- **정적 검사.** Android lint에서 오류는 없다. 경고 2개는 compileSdk·targetSdk 37이 나와 있다는 내용이다. 폴드7이 Android 16(API 36)이라 36을 유지했다.
- **에뮬레이터 실행.** 작업 환경에 하드웨어 가상화(KVM, Kernel-based Virtual Machine)가 없어서 Android 11(API 30) x86_64 이미지를 소프트웨어 에뮬레이션으로 부팅했다. 화면은 폴드7 내부 화면과 같은 1968×2184, 368 dpi(dots per inch)로 맞췄다. APK(Android Package)를 설치하고 `adb shell input`으로 키보드 좌표를 눌러 다음을 확인했다.
  - TYPE_NULL 확인창에 `l`, `s`, Enter, c 아래로 밀기(`CTRL_LEFT` 다음 Ctrl+C), Esc/Ctrl 키 탭(ESCAPE), 9 위로 밀기(`(`)가 이 순서로 도착했다.
  - 같은 확인창에서 한글 모드로 ㅇㅏㄴ을 치면 상단 줄에만 "안"이 보이고 확인창에는 아무것도 가지 않았다. 이어서 Esc를 누르자 "안"이 먼저 확정되고 ESCAPE가 뒤따랐으며, 다음 `j`는 영문으로 들어갔다.
  - 여러 줄 `EditText`에서 ㅎㅏㄴㄱㅡㄹ과 스페이스가 "한글 "이 되었다. a 아래로 밀기(Ctrl+A)로 전체가 선택되었고 `x`가 선택 영역을 바꿨다. z 아래로 밀기(Ctrl+Z)로 "한글 "이 되돌아왔다.
  - 가로로 돌리자 분할 배열이 나왔고, 앱이 전체 화면 추출 모드로 바뀌지 않았다.
- **0.2.0 자동 테스트.** 테스트는 123개다. 새로 넣은 것은 Fn 기호 53개의 배열별 중복·누락 검사, 한글 문장 부호 포함 검사, Insert, Termux 제어 문자 대응, 터미널 입력 기록(`EchoBuffer`), 클립보드 기록(중복, 개수, 1시간 만료), 가운데 패널과 복제 키 폭, 반쪽 올림, 스페이스 위아래 끌기, 길게 누르기 반복, 엄지 범위 계산, 측정 화면의 저장이다. Robolectric으로 서비스를 띄워 클립 변경 알림, 민감 클립 제외, 비밀번호 입력창의 표시 제외도 확인했다.
- **0.3.0 자동 테스트.** 새로 넣은 것은 다음과 같다.
  - 클립 기록의 고정 순서·만료 제외·고정 해제·삭제·고정 한도·개수 축소, 고정 저장 파일의 왕복·손상·삭제
  - 서비스 재시작 뒤 고정 유지, 지운 클립이 다시 기록되지 않는지, 기록 개수 설정 반영, 터미널에서 편집 버튼 숨김
  - 가운데 패널의 탭 붙여넣기·끌어 넘기기·길게 눌러 고정·삭제·메뉴 닫기, 상단 줄 버튼 순서
  - `EditText`에서 모두 선택과 복사, 처리하고도 false를 돌려주는 입력창에서 붙여넣기가 한 번만 되는지
  - 하단 insets: insets 배분이 끝난 IME 창에 붙인 키보드 뷰가 IME 내비게이션 바 위에 놓이는지(`BottomInsetTest`)
- **0.4.0 자동 테스트.** 테스트는 165개다. 새로 넣거나 바꾼 것은 다음과 같다.
  - 분할 배열의 ANSI 줄 어긋남, 줄마다 같은 간격, ⌫·⏎·오른쪽 Shift의 자리와 폭, 폴드7 세로 키 너비 7.93 mm와 반쪽 범위
  - 줄마다 나눈 가운데 패널의 칸 폭(가로 13.7 mm, 스페이스 줄 22.2 mm)
  - Fn 숫자판의 배치와 곧은 열, 오른쪽 끝 정렬과 글자 키와 같은 폭, 가운데와 숫자판 왼쪽 빈 자리에서 입력이 없는지, 전체·커버 화면 배열의 판정 영역이 겹치지 않는지
  - 키보드 뷰를 직접 터치해 Fn 고정·한 번·누른 채 입력, 숫자판 ⌫ 뒤 Fn 유지, ⏎, 커버 화면 숫자판
  - 스페이스 꾹 눌러 선택: 터치 추적 단계(축 고정, 움직이지 않고 떼기, 시간 전 끌기, 다른 키와 겹침), 엔진(일반 입력창은 Shift, 터미널은 Shift 없음), 키보드 뷰, `EditText` 선택
  - 0.3.0까지의 Fn 특수문자 테스트는 특수문자가 남지 않았는지 보는 테스트로 바꿨다.
- **0.4.1 자동 테스트.** 테스트는 166개다. 새로 넣거나 바꾼 것은 다음과 같다.
  - 엔진: Fn은 한 번 누르면 다시 누를 때까지 켜져 있고, 같은 조건에서 Shift·Ctrl·Alt는 one-shot으로 남는지(`fnLatchesUntilTappedAgainWhileOtherModifiersStayOneShot`)
  - 키보드 뷰: Fn을 한 번 누른 뒤 숫자·⌫·⏎를 여러 번 쳐도 숫자판이 남고 Fn을 다시 누르면 돌아오는지, Fn을 누른 채 치면 뗄 때 꺼지는지
  - 분할 배열의 왼쪽 Shift 2.25칸과 `=` 오른쪽의 `\`
- **0.2.0 에뮬레이터 실행.** 같은 에뮬레이터(Android 11, 1968×2184, 368 dpi)를 가로로 놓고 확인했다.
  - 여러 줄 `EditText`에서 Fn+`,`, Fn을 켜고 `.` 위로 밀기, Fn+`\`가 `·≥₩`를 입력했다. 가운데 아래쪽에 같은 글자가 표시되었다.
  - a 아래로 밀기(Ctrl+A)와 c 아래로 밀기(Ctrl+C) 뒤 가운데 위쪽에 복사한 글이 나타났고, 그 칸을 누르자 커서 위치에 붙여 넣어져 `·≥₩·≥₩`가 되었다.
  - TYPE_NULL 확인창에서 Fn+Esc는 `down INSERT`, Ctrl·Alt one-shot 뒤 2는 `text "^[^@"`(ESC, NUL)로 도착했다.
  - 두 줄을 만든 뒤 스페이스를 9.4 mm 위로 끌고 y를 치자 윗줄의 같은 열에 들어갔다(`·y≥₩·≥₩\nx`). 스페이스는 입력되지 않았다.
  - 엄지 범위 측정 화면에 `adb shell input swipe`로 왼쪽 60·61·62 mm, 오른쪽 66·67·65 mm 세로 획을 그리자 "왼쪽 61.0 mm, 오른쪽 66.0 mm → 키 너비 8.7 mm"가 나왔고, 적용 뒤 설정 파일에 87(0.1 mm 단위)이 저장되었다.
  - 반쪽 올림 5 mm와 길게 누르기 반복을 설정 파일에 넣고 키보드를 다시 띄우자 맨 아래에 빈 띠가 생기고 키 너비가 8.7 mm로 그려졌다. `j`를 2.5초 누르자 `jjjjjjj`가 입력되었고 `J`는 나오지 않았다.

- **0.3.0 에뮬레이터 실행.** 같은 Android 11 에뮬레이터를 데이터를 지운 뒤 다시 부팅해 확인했다.
  - 여러 줄 `EditText`에 `git log --oneline`을 넣고 상단 줄의 모두 선택을 누르자 전체가 선택 표시되었다.
  - 이어서 복사를 누르고 커서를 끝으로 옮긴 뒤 붙여넣기를 누르자 `git log --onelinegit log --oneline`이 되었다. 한 번만 붙었다.
  - 가로로 돌리자 가운데 패널에 복사한 글이 나왔다. 그 칸을 1.5초 누르자 고정·삭제 버튼으로 바뀌었고, 고정을 누르자 📌가 붙었다.

- **0.4.0 에뮬레이터 실행.** 같은 Android 11 에뮬레이터를 데이터를 지운 뒤 다시 부팅해 확인했다.
  - 세로 화면에서 상단 줄의 Split을 누르자 계단 모양 분할 배열이 나왔다. ⌫는 ⏎ 바로 위에, 오른쪽 Shift는 아래 글자 줄 끝에 있었다.
  - Fn을 누르자 오른쪽 끝에 숫자판이 나왔다. Fn+`7`, Fn+`+`, Fn+`4`, Fn+⌫, `(`를 차례로 누르고 Fn 없이 숫자판 `1` 자리(일반 배열의 `l`)를 누르자 `7+(l`이 되었다.
  - 오른쪽 스페이스를 누른 채 8초 기다리자 스페이스 키가 강조색으로 바뀌었다. 왼쪽으로 12.4 mm를 세 번에 나눠 끌고 떼자 `+(l`이 선택되었고, `x`를 누르자 `7x`가 되었다.
  - 첫 시험은 숫자판을 `h`부터 채우던 배치에서 Fn+⌫ 뒤에 Fn을 다시 누르는 순서였고, 결과가 `7u`였다(7.9절).

- **0.4.1 에뮬레이터 실행.** 같은 Android 11 에뮬레이터를 데이터를 지운 뒤 다시 부팅해 확인했다.
  - 세로 분할 배열에서 왼쪽 Shift가 2.25칸으로, `\`가 숫자 행 `=` 오른쪽에 그려졌다.
  - Fn을 한 번만 누른 뒤 숫자판의 `7`, `+`, `4`, ⌫, `(`, `1`, `2`, `)`를 차례로 누르자 `7+(12)`가 되었다. 그동안 숫자판과 상단 줄의 "Fn" 표시가 유지되었다.
  - Fn을 다시 누르자 원래 배열로 돌아왔다. 이어서 숫자판 `1` 자리(`l`), 0.4.0의 `\` 자리였던 왼쪽 Shift 오른쪽 부분, `z`, 숫자 행 `\`, 숫자 행 오른쪽 끝 빈 자리를 누르자 `7+(12)lZ\`가 되었다.
  - 부팅 직후 system_server가 한 번 재시작했고 시스템 응답 없음 대화상자가 두 번 떴다. 대화상자를 닫고 시험했다.

### 10.2 이 환경에서 확인하지 못한 것

- **Robolectric과 실제 Android의 차이.** Robolectric의 `KeyCharacterMap`은 Ctrl+A에도 `'a'`를 돌려준다(실험으로 확인). AOSP는 0을 돌려주므로 Ctrl+A가 단축키 단계로 넘어간다. 그래서 일반 입력창의 Ctrl 단축키는 Robolectric이 아니라 에뮬레이터에서 확인했다. Robolectric에서는 `commitText`가 즉시 반영되고 키 이벤트가 나중에 처리되어 순서가 바뀌는 현상도 있었다. 에뮬레이터에서는 보낸 순서대로 처리되었다.
- **에뮬레이터에서 본 이상 현상.** 첫 시험에서 "ㅎㅏㄴㄱㅡㄹ 스페이스"가 "한ㅡ "로 들어갔다. 당시 시스템 UI 응답 없음 대화상자가 떠 있었고 로그를 남기지 않아 원인을 확정하지 못했다. 입력 연결이 다시 시작되면 조합기만 비워지고 입력창의 조합 영역은 남아, 다음 자모가 그 영역을 덮어쓸 수 있다는 점을 코드에서 찾았다. 그래서 입력이 다시 시작될 때 조합 중이던 글자를 먼저 확정하도록 고쳤다(`restartDuringCompositionKeepsTypedJamo`). 수정 뒤 같은 순서(터미널 확인창에서 입력한 뒤 `EditText`로 이동)는 "한글 "이 되었다. 알림창을 여닫은 직후에 친 ㅡ가 빠져 "한ㄱㄹ "이 된 경우도 한 번 있었다. 이 결과는 ㅡ 탭이 키보드에 도달하지 않았을 때 조합기가 내는 출력과 같고, 임시로 넣은 디버그 로그에 조합 초기화 기록이 없었다. 그래서 키보드 밖에서 사라진 입력으로 판단했다.
- **에뮬레이터의 한계.** 소프트웨어 에뮬레이션이라 탭 하나를 처리하는 데 10초 넘게 걸렸다. 길게 누르기 반복도 2.5초 동안 7번만 나와 설계값(400 ms 뒤 50 ms마다)과 맞지 않았다. 앱을 `am force-stop`으로 멈추면 Android 11은 기본 입력 방법을 LatinIME으로 되돌렸다. 그래서 타이밍(두 엄지 rollover, 길게 누르기, 반복 입력)은 에뮬레이터 결과로 판단하지 않았다. 이미지가 Android 11이라 Android 15 이상의 edge-to-edge 처리도 여기서는 확인되지 않는다.
- **실기기 동작.** 폴드7 실기기가 없다. 햅틱 강도와 느낌, One UI가 서드파티 IME의 `KEYBOARD_TAP`을 시스템 키보드 진동 설정에 연결하는지, `xdpi` 값의 정확도, 작업 표시줄(taskbar)과 IME의 상호작용, Android 16에서의 하단 insets 처리는 기기에서 확인해야 한다.
- **사용자 실험.** 입력 속도와 오류율을 사람으로 측정한 적이 없다. 이 문서의 수치는 모두 인용한 연구의 값이다. 평가할 때는 Soukoreff·MacKenzie(2003)의 전체·수정·미수정 오류율과 KSPC(keystrokes per character, 문자당 키 입력 수)를 쓰고, 명령어와 식별자 같은 사전 밖 문자열을 과제에 넣어야 한다. Palin 외(2019)의 두 엄지 평균(38 WPM, 미수정 오류 2.3%)이 휴대폰 입력의 참고값이다.

### 10.3 기기에서 확인할 항목

1. 설정 앱의 "키 이벤트 확인창"에서 Esc, `^C`(c 아래로 밀기), Alt+b, F5(5 아래로 밀기), ←가 표시되는지 본다.
2. Termux에서 `cat -v`를 실행하고 Esc(`^[`), Ctrl+C, 방향키(`^[[A`), Alt+b(`^[b`)가 기대한 바이트로 나오는지 본다.
3. 한/A로 한글을 켜고 Termux에서 `echo 한글`을 입력한다. 상단 줄에 조합 중 글자가 보이고, 음절이 셸에 순서대로 들어가는지 본다. vim insert mode(입력 모드)에서 한글을 친 뒤 Esc를 누르면 영문으로 돌아오는지 확인한다.
4. 일반 입력창에서 Ctrl+A, Ctrl+C, Ctrl+V, Ctrl+Z가 동작하는지 본다.
5. 가로·세로 전환과 내부·커버 화면 전환에서 배열이 전체·분할·compact로 바뀌는지 본다.
6. 시스템 설정의 키보드 진동을 끄고 켜며 기본 햅틱이 따라가는지 본다.
7. Termux에서 Fn 숫자판으로 친 `7`, `(`, `+`가 셸에 그대로 들어가는지 본다.
8. 다른 앱에서 복사한 글이 가운데에 나타나는지, 비밀번호 관리 앱에서 복사한 비밀번호는 나타나지 않는지 본다.
9. 엄지 범위 측정을 실제 손으로 세 번씩 해서 측정값이 일치 범위 안에 들어오는지, 정한 키 너비에서 안쪽 키(T, Y, 5, 6)가 편하게 닿는지 본다.
10. 길게 누르기 반복을 켜고 vim normal mode에서 `j`를 누르고 있을 때 줄 이동이 반복되는지 본다.
11. 세로 분할에서 HHKB 습관대로 쳤을 때 0.3.0보다 오타가 줄었는지, 오른쪽 반쪽 안쪽 키(6, Y, H, B)가 편하게 닿는지 본다.
12. 스페이스를 꾹 누른 뒤 끌어 일반 입력창에서 선택이 되는지, One UI의 텍스트 선택 도구 막대와 겹치지 않는지 본다.

## 11. 얽힌 이야기들

- **vi와 ADM-3A.** Bill Joy는 1984년 Unix Review 인터뷰에서 "what started it all was that we got some ADM-3As to do screen editing"이라고 말했다. 그 터미널의 키보드에는 ESC가 지금의 Tab 자리에, CTRL이 지금의 Caps Lock 자리에 있었고, H·J·K·L에 화살표가 인쇄되어 있었다. vi의 hjkl 이동과 잦은 Esc 사용이 이 배열에서 나왔다는 설명은 널리 퍼져 있지만, Joy가 직접 그렇게 말한 기록은 찾지 못했다. 같은 인터뷰에서 그는 "I wish we hadn't used all the keys on the keyboard"라고도 했다. 0.3.0까지 FoldKey의 Fn + h/j/k/l이 방향키였던 것도 이 전통을 따랐다. 0.4.0에서 Fn이 숫자판으로 바뀌면서 뺐다.
- **Ctrl+[가 Esc인 이유.** VT100 사용 설명서(표 3-5)에 따르면 CTRL을 누르면 000–037(8진수) 범위의 코드가 전송된다. ASCII에서 `[`는 0x5B이고 여기에 0x1F를 AND하면 0x1B, 곧 ESC다. Termux의 `inputCodePoint`도 `[`를 27로 바꾼다. 같은 이유로 Ctrl+M은 CR(carriage return, 13), Ctrl+I는 Tab(9)이다.
- **iPad의 숨은 키.** 2012년 2월 David Chartier가 iPad 분할 키보드에서 보이지 않는 키 여섯 개(T/G/V 옆의 Y/H/B, Y/H/B 옆의 T/G/V)를 찾아냈다. Apple은 이 기능을 문서로 설명하지 않았다 `[2차]`.
- **KALQ라는 이름.** 논문은 "pronounced as in 'calculated'"라고만 적는다. 논문 그림 1을 좌표로 복원하면 오른쪽 격자의 맨 아래 글자 줄이 K A L Q다 `[조사 과정에서 복원]`. 저자들은 사용자가 새 배열을 배우려 하지 않을 수 있다고 스스로 적었다.
- **네벌식에서 두벌식으로.** 국가기록원 자료에 따르면 1969년 정부는 네벌식 타자기 자판을 표준으로 정했다(국무총리 훈령 제81호). 국사편찬위원회 우리역사넷은 이 네벌식을 "초성 자음 한 벌, 긴 모음 한 벌, 짧은 모음 한 벌, 받침 한 벌"로 설명하고, 기존 제조사들이 "강하게 반발"했다고 적는다. 1982년 텔레타이프에 쓰던 두벌식이 국가 표준(지금의 KS X 5002)이 되었고, 1983년 네벌식 타자기 표준이 폐지되었다. 공병우가 초성·중성·종성을 따로 둔 세벌식 타자기를 만든 것은 1949년이다.
- **천지인 특허.** 천지인 특허는 삼성전자와 조관현 아이디엔 사장이 모두 보유했다. 두 쪽은 2002년부터 소송을 벌였고, 2009년 양쪽의 특허가 모두 인정되었다. 조관현 사장은 2010년 10월 19일 특허를 기술표준원에 기증했다(서울신문 2010년 10월 20일). 2011년 6월 천지인이 피처폰 단일 표준, 천지인·나랏글·SKY가 스마트폰 복수 표준으로 정해졌다.
- **인용 속의 뒤바뀐 숫자.** Palin 외(2019)의 관련 연구 절은 Azenkot·Zhai(2012)의 결과를 "two thumbs, one thumb or the index finger" 순서로 50.03, 36.34, 33.78 WPM이라고 옮겼다. 원문은 한 손가락(검지)이 36.34, 한 엄지가 33.78이다. 이번 조사에서 두 PDF를 대조해 확인했다. 인용을 거친 수치는 원문과 대조할 필요가 있다는 예다.
- **문장 부호 해설 페이지의 코드 포인트.** 국립국어원 「문장 부호 해설」 웹 페이지의 HTML(HyperText Markup Language)을 받아 글자를 확인했다. 가운뎃점은 U+318D(한글 아래아), 겹화살괄호는 U+226A·U+226B(수학의 ≪ ≫), 홑낫표는 U+FF62·U+FF63(반각 ｢ ｣), 홑화살괄호는 ASCII `<` `>`였다. 겹낫표(U+300E·U+300F), 줄표(U+2015), 물결표(U+223C)는 KS X 1001 대응과 같았다. 모양이 비슷한 다른 문자는 검색과 비교에서 서로 다른 글자로 취급된다. FoldKey는 KS X 1001의 Unicode 대응을 따른다.
- **₩와 역슬래시.** KS X 1003(옛 KS C 5636)은 ASCII의 0x5C 자리에 ₩를 둔다. 한국어 Windows 키보드의 ₩ 키는 U+005C를 보내고, 맑은 고딕은 U+005C를 ₩ 모양으로 그린다 `[2차]`. FoldKey는 코드 입력을 위해 `\` 키에서 U+005C를 보낸다. 0.2.0–0.3.0에서는 Fn을 켜면 같은 키에서 U+20A9(₩)를 보냈고, 0.4.0에서 Fn 특수문자와 함께 뺐다.
- **가운데를 두고 갈린 선택.** Windows 8 thumb keyboard는 2011년 첫 Windows 8 시연에서 공개되었고, 당시 사용자 블로그는 "가운데에 숫자 패드가 있는 옛 인체공학 키보드"로 묘사했다 `[2차]`. 2012년 Microsoft 설계 글의 댓글에는 가운데 숫자가 없는 분할 키보드와 빈 공간을 투명하게 해 달라는 요청이 함께 달렸다. Apple은 iPad 분할 키보드의 가운데로 본문이 보이게 출시했지만(Trudeau 외가 시험한 iOS 6), 2010년 우선일의 특허 명세서에는 가운데에 두 번째 입력란을 두는 형태도 적었다.
- **Microsoft 문서의 오타.** Microsoft의 한국어 IME 문서는 "여름"을 입력하는 키를 "O, U, F, M, and A"로 적는다. ㅇ은 D 키이므로 D, U, F, M, A가 맞다.

## 12. 참고 자료

학회와 출판 약칭: ACM(Association for Computing Machinery), CHI(ACM Conference on Human Factors in Computing Systems), MobileHCI(International Conference on Human-Computer Interaction with Mobile Devices and Services), UIST(ACM Symposium on User Interface Software and Technology), IUI(International Conference on Intelligent User Interfaces), INTERACT(IFIP TC13 Conference on Human-Computer Interaction), IFIP(International Federation for Information Processing), HAS(International Conference on Human Aspects of Information Security, Privacy and Trust), LNCS(Lecture Notes in Computer Science), IEEE(Institute of Electrical and Electronics Engineers), PLoS(Public Library of Science), doi(digital object identifier), HCI(human-computer interaction).

### 논문

- Aschim, T. S., et al. (2019). Are split tablet keyboards better? INTERACT 2019, LNCS, 647–655. doi:10.1007/978-3-030-29387-1_37
- Azenkot, S., & Zhai, S. (2012). Touch behavior with different postures on soft smartphone keyboards. MobileHCI '12, 251–260. doi:10.1145/2371574.2371612
- Bergstrom-Lehtovirta, J., & Oulasvirta, A. (2014). Modeling the functional area of the thumb on mobile touchscreen surfaces. CHI '14, 1991–2000. doi:10.1145/2556288.2557354
- Bi, X., Li, Y., & Zhai, S. (2013). FFitts law: Modeling finger touch with Fitts' law. CHI '13, 1363–1372. doi:10.1145/2470654.2466180
- Bi, X., Smith, B. A., & Zhai, S. (2010). Quasi-qwerty soft keyboard optimization. CHI '10, 283–286. doi:10.1145/1753326.1753367
- Brewster, S., Chohan, F., & Brown, L. (2007). Tactile feedback for mobile interactions. CHI '07, 159–162. doi:10.1145/1240624.1240649
- Dhakal, V., Feit, A. M., Kristensson, P. O., & Oulasvirta, A. (2018). Observations on typing from 136 million keystrokes. CHI '18. doi:10.1145/3173574.3174220
- Fennedy, K., Malacria, S., Lee, H., & Perrault, S. T. (2020). Investigating performance and usage of input methods for soft keyboard hotkeys. MobileHCI '20. doi:10.1145/3379503.3403552
- Findlater, L., & Wobbrock, J. O. (2012). Personalized input: Improving ten-finger touchscreen typing through automatic adaptation. CHI '12, 815–824. doi:10.1145/2207676.2208520
- Findlater, L., Wobbrock, J. O., & Wigdor, D. (2011). Typing on flat glass. CHI '11, 2453–2462. doi:10.1145/1978942.1979301
- Goel, M., et al. (2013). ContextType: Using hand posture information to improve mobile touch screen text entry. CHI '13, 2795–2798. doi:10.1145/2470654.2481386
- Goodman, J., Venolia, G., Steury, K., & Parker, C. (2002). Language modeling for soft keyboards. IUI '02, 194–195. doi:10.1145/502716.502753
- Greene, K. K., Gallagher, M. A., Stanton, B. C., & Lee, P. Y. (2014). I can't type that! P@$$w0rd entry on mobile devices. HAS 2014, LNCS 8533, 160–171. doi:10.1007/978-3-319-07620-1_15
- Henze, N., Rukzio, E., & Boll, S. (2012). Observational and experimental investigation of typing behaviour using virtual keyboards for mobile devices. CHI '12, 2659–2668. doi:10.1145/2207676.2208658
- Hoggan, E., Brewster, S. A., & Johnston, J. (2008). Investigating the effectiveness of tactile feedback for mobile touchscreens. CHI '08, 1573–1582. doi:10.1145/1357054.1357300
- Holz, C., & Baudisch, P. (2010). The generalized perceived input point model and how to double touch accuracy by extracting fingerprints. CHI '10, 581–590. doi:10.1145/1753326.1753413
- Holz, C., & Baudisch, P. (2011). Understanding touch. CHI '11, 2501–2510. doi:10.1145/1978942.1979308
- Jiang, X., Li, Y., Jokinen, J. P. P., Hirvola, V. B., Oulasvirta, A., & Ren, X. (2020). How we type: Eye and finger movement strategies in mobile typing. CHI '20. doi:10.1145/3313831.3376711
- Ilinkin, I., & Kim, S. (2017). Design and evaluation of Korean text entry methods for smartwatches. CHI '17. doi:10.1145/3025453.3025657
- Kaaresoja, T., Brewster, S., & Lantz, V. (2014). Towards the temporally perfect virtual button. ACM Transactions on Applied Perception 11(2), Article 9. doi:10.1145/2611387
- Kim, H., Yi, S., & Yoon, S. Y. (2019). Exploring touch feedback display of virtual keyboards for reduced eye movements. Displays 56, 38–48. doi:10.1016/j.displa.2018.11.004
- Lertvittayakumjorn, P., et al. (2024). UIST '24. doi:10.1145/3654777.3676420 (arXiv:2410.02264)
- Lu, Y., Yu, C., Fan, S., Bi, X., & Shi, Y. (2019). Typing on split keyboards with peripheral vision. CHI '19, Paper 200. doi:10.1145/3290605.3300430
- Ma, Z., Edge, D., Findlater, L., & Tan, H. Z. (2015). Haptic keyclick feedback improves typing speed and reduces typing errors on a flat keyboard. IEEE World Haptics Conference 2015, 220–227. doi:10.1109/WHC.2015.7177717
- Oulasvirta, A., et al. (2013). Improving two-thumb text entry on touchscreen devices. CHI '13, 2765–2774. doi:10.1145/2470654.2481383
- Palin, K., Feit, A. M., Kim, S., Kristensson, P. O., & Oulasvirta, A. (2019). How do people type on mobile devices? Observations from a study with 37,000 volunteers. MobileHCI '19. doi:10.1145/3338286.3340120
- Parhi, P., Karlson, A. K., & Bederson, B. B. (2006). Target size study for one-handed thumb use on small touchscreen devices. MobileHCI '06, 203–210. doi:10.1145/1152215.1152260
- Soukoreff, R. W., & MacKenzie, I. S. (2003). Metrics for text entry research. CHI '03, 113–120. doi:10.1145/642611.642632
- Trudeau, M. B., Catalano, P. J., Jindrich, D. L., & Dennerlein, J. T. (2013). Tablet keyboard configuration affects performance, discomfort and task difficulty for thumb typing in a two-handed grip. PLoS ONE 8(6), e67525. doi:10.1371/journal.pone.0067525
- Vogel, D., & Baudisch, P. (2007). Shift: A technique for operating pen-based interfaces using touch. CHI '07, 657–666. doi:10.1145/1240624.1240727
- Yin, Y., Ouyang, T. Y., Partridge, K., & Zhai, S. (2013). Making touchscreen keyboards adaptive to keys, hand postures, and individuals. CHI '13, 2775–2784. doi:10.1145/2470654.2481384

### 표준, 문서, 소스 코드

- Samsung Global Newsroom, Galaxy Z Fold7 사양(2025-07-09): https://news.samsung.com/global/samsung-galaxy-z-fold7-raising-the-bar-for-smartphones
- Samsung Developers, Galaxy Z 에뮬레이터 스킨: https://developer.samsung.com/galaxy-emulator-skin/galaxy-z.html
- KS X 5002 정보 처리용 건반 배열: https://standard.go.kr
- 국가기록원, 한글 기계화: https://theme.archives.go.kr/next/hangeulPolicy/mechanization.do
- The Unicode Standard, 3.12 Conjoining Jamo Behavior: https://www.unicode.org/versions/latest/core-spec/chapter-3/
- libhangul: https://github.com/libhangul/libhangul
- ibus-hangul: https://github.com/libhangul/ibus-hangul
- 구름 입력기: https://github.com/gureum/gureum
- AOSP frameworks/base, frameworks/native(android-16.0.0_r1): `InputMethodService.java`, `View.java`, `HapticFeedbackVibrationProvider.java`, `KeyCharacterMap.cpp`, `Virtual.kcm`
- AOSP LatinIME: `PointerTracker.java`, `PointerTrackerQueue.java`, `config-per-form-factor.xml`
- Termux: https://github.com/termux/termux-app (`TerminalView.java`, `KeyHandler.java`, `TermuxPropertyConstants.java`, `ExtraKeysConstants.java`)
- Hacker's Keyboard: https://github.com/klausw/hackerskeyboard
- Unexpected Keyboard: https://github.com/Julow/Unexpected-Keyboard (`srcs/compose/fn.json`, `KeyModifier.java`, `res/xml/split_middle_column.xml`)
- AOSP frameworks/base(main): `ClipboardService.java`, `EditorInfo.java`, `AbstractInputMethodService.java`
- 국립국어원, 문장 부호 해설(2015-02-13): https://www.korean.go.kr/front/etcData/etcDataView.do?mn_id=46&etc_seq=431
- KS X 1001 기호 영역: Python `euc_kr` 코덱으로 0xA1–0xAC 행을 디코딩해 확인
- KS X 1003, 위키백과: https://ko.wikipedia.org/wiki/KS_X_1003
- Microsoft, Designing the Windows 8 touch keyboard(2012-07-17): https://learn.microsoft.com/en-us/archive/blogs/b8/designing-the-windows-8-touch-keyboard
- M. Garvis, Windows 8 On-Screen Keyboards(2012-03-01): https://garvis.ca/2012/03/01/windows-8-on-screen-keyboards/
- Microsoft 지원, SwiftKey 키보드 모드 변경: https://support.microsoft.com/en-us/swiftkey-keyboard/how-to-change-your-keyboard-mode-on-microsoft-swiftkey-keyboard
- Apple Inc., US 8,547,354 B2, Device, method, and graphical user interface for manipulating soft keyboards: https://patents.google.com/patent/US8547354B2
- Android Developers, Haptics design principles: https://developer.android.com/develop/ui/views/haptics/haptics-principles
- PFU, HHKB Professional Classic 사용 설명서 P3PC-6661-01EN: https://origin.pfultd.com/downloads/hhkb/manual/P3PC-6661-01EN.pdf
- Deskthority wiki, Staggering: https://deskthority.net/wiki/Staggering
- Android Developers, Behavior changes: Android 15, Android 16: https://developer.android.com/about/versions/16/behavior-changes-16
- ADM-3A Operator's Manual: https://vt100.net/lsi/adm3a-om.pdf
- VT100 User Guide, Chapter 3: https://vt100.net/docs/vt100-ug/chapter3.html
- keyd: https://github.com/rvaiya/keyd
