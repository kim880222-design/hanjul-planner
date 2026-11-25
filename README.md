# 한줄일정

문장 한 줄로 할 일·일정·프로젝트를 입력하는 안드로이드 일정관리 앱.

## 입력 예시
| 입력 | 결과 |
|---|---|
| 내일 3시 회의 | 일정, 내일 오후 3시 |
| 우유 사기 | 날짜 없는 할 일 |
| 금요일까지 보고서 | 할 일, 금요일 마감 |
| 이사 준비: 짐 싸기, 업체 예약, 주소 변경 | 프로젝트, 단계 3개 |
| 이사 준비: 짐 싸기 12/1, 업체 예약 금요일까지 | 프로젝트, 단계마다 마감일 |
| 매주 월요일 9시 팀회의 | 반복 일정 |
| 매달 25일 월세 | 반복 할 일 |

## 설치
`main`에 올라가면 GitHub Actions가 APK를 만들어 **Releases**에 올린다.
휴대폰에서 Releases 페이지 → `hanjul-planner.apk` 내려받기 → 설치.

## 구조
- `domain/` 한국어 문장 분석(KoreanParser), 반복 규칙(Recurrence)
- `data/` Room 데이터베이스, 화면용 계산(Agenda)
- `notify/` 알림 예약(AlarmManager)
- `ui/` Jetpack Compose 화면: 현황, 오늘, 달력, 프로젝트

## 디자인
DOS 단말기 · 카세트 퓨처리즘 스타일. 색은 xterm 256색 팔레트 안에서만 사용(`ui/Theme.kt`의 `Pal`).
글꼴: [Galmuri](https://github.com/quiple/galmuri) (SIL Open Font License 1.1, 라이선스 전문은 `app/src/main/assets/galmuri-license.txt`).
