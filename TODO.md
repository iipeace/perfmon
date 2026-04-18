# PerfMon 개발 아이템 목록

초보 개발자가 이 프로젝트를 기반으로 도전해볼 수 있는 실습 아이디어 모음입니다.
각 아이템에는 난이도(⭐~⭐⭐⭐)와 핵심 학습 포인트를 함께 표시했습니다.

---

## 1. 리소스 모니터 대시보드 (실시간)

**난이도:** ⭐⭐
**관련 명령어:** `resmon|-a`
**관련 파일:** `monitor/ResourceMonitor.kt`, `ui/screen/MonitorScreen.kt`

`ResourceSnapshot`으로 수신되는 CPU·메모리·네트워크·스토리지 데이터를
`MonitorScreen`에 실제로 렌더링하는 대시보드를 구현합니다.

**구현 항목 예시**
- CPU 사용률 원형 게이지 또는 프로그레스 바
- 메모리 사용/여유 막대 그래프 (anon + kernel vs available)
- 네트워크 수신/송신 바이트 실시간 수치 표시
- 스토리지 사용/여유 표시

**학습 포인트**
- `StateFlow` → `collectAsState()` → Compose 재구성 흐름
- `ResourceSnapshot` 데이터 모델 구조 이해
- Compose의 `Canvas` 또는 외부 차트 라이브러리 사용법

---

## 2. 프로세스 목록 화면

**난이도:** ⭐⭐
**관련 명령어:** `resmon|-a`
**관련 파일:** `model/ResourceSnapshot.kt` (`ProcessStats`), `monitor/ResourceMonitor.kt`

`ResourceSnapshot.processes` (Map<Int, ProcessStats>)를 활용해
현재 실행 중인 프로세스를 목록으로 표시합니다.

**구현 항목 예시**
- PID, 프로세스명, CPU 시간, RSS(메모리) 컬럼으로 구성된 테이블
- CPU 시간 또는 메모리 기준 정렬 기능
- 프로세스 검색 (이름 필터링)

**학습 포인트**
- `LazyColumn`으로 동적 리스트 렌더링
- `sortedByDescending` 등 컬렉션 정렬 처리
- `remember` + `mutableStateOf`로 로컬 UI 상태 관리

---

## 3. 시스템 정보 화면 (sysinfo 파싱)

**난이도:** ⭐
**관련 명령어:** `sysinfo`
**관련 파일:** `probe/SystemProbe.kt`, `model/SystemReport.kt`, `ui/screen/SystemScreen.kt`

현재 `SystemScreen`은 수신 줄 수만 표시합니다.
`SystemReport.lines`를 파싱해 실제 정보를 카드 형태로 보여주는 화면을 만듭니다.

**구현 항목 예시**
- OS 버전, 커널 버전, 기기명 카드
- 부팅 시간(uptime) 표시
- 줄 단위 원문 스크롤 뷰 (개발 디버깅용)

**학습 포인트**
- 문자열 파싱 (`split`, `contains`, 정규식)
- `LazyColumn` + `Card` 컴포넌트 구성
- `suspend` 함수 호출 흐름 (`ViewModel` → `SystemProbe.fetch()`)

---

## 4. CPU 사용률 히스토리 그래프

**난이도:** ⭐⭐⭐
**관련 명령어:** `resmon|-a`
**관련 파일:** `monitor/ResourceMonitor.kt`, `ui/viewmodel/MonitorViewModel.kt`

수신되는 `CpuStats.usagePercent` 값을 시계열로 누적해
시간 흐름에 따른 CPU 사용률 꺾은선 그래프를 그립니다.

**구현 항목 예시**
- 최근 N개(예: 60개) 데이터 포인트를 링 버퍼로 보관
- Compose `Canvas`로 꺾은선 그래프 직접 구현
- 최댓값·평균값 오버레이 표시

**학습 포인트**
- `ViewModel` 내부에서 `MutableList`로 히스토리 누적
- `Canvas` API (`drawLine`, `drawPath`)
- `ArrayDeque`를 사용한 고정 크기 큐 구현

---

## 5. 메모리 사용량 실시간 트렌드

**난이도:** ⭐⭐
**관련 명령어:** `resmon|-a`
**관련 파일:** `model/ResourceSnapshot.kt` (`MemStats`)

`MemStats`(anon, kernel, available)를 영역 그래프(stacked area chart)로 표현합니다.

**구현 항목 예시**
- 전체 메모리 대비 각 영역 비율 시각화
- 메모리 압박 임계값(예: available < 500MB) 도달 시 경고 색상 변경
- 수치(MB 단위 변환) 실시간 레이블

**학습 포인트**
- KB → MB 단위 변환 유틸 함수 작성
- Compose `AnimatedContent` 또는 `animateFloatAsState`로 부드러운 전환
- 조건부 색상 처리 (`if`/`when`으로 `Color` 변경)

---

## 6. 네트워크 트래픽 모니터

**난이도:** ⭐⭐
**관련 명령어:** `resmon|-a`
**관련 파일:** `model/ResourceSnapshot.kt` (`NetStats`)

`NetStats`의 inbound/outbound 값을 이전 프레임과 비교해
초당 전송량(Bps)을 계산하고 시각화합니다.

**구현 항목 예시**
- 수신/송신 속도를 KB/s 또는 MB/s 단위로 변환하여 표시
- 막대 그래프로 수신·송신 비교
- 누적 트래픽 합계 카운터

**학습 포인트**
- 프레임 간 델타 계산 (이전 값 보관 → 차분)
- 단위 자동 선택 로직 (`B/KB/MB` 분기)
- `System.currentTimeMillis()`로 프레임 간 시간 측정

---

## 7. 스토리지 사용량 화면

**난이도:** ⭐
**관련 명령어:** `resmon|-a`
**관련 파일:** `model/ResourceSnapshot.kt` (`DiskStats`)

`DiskStats`(freeKb, usedKb)로 도넛 차트 또는 프로그레스 바를 구현합니다.

**구현 항목 예시**
- 사용/여유 용량을 GB 단위로 환산하여 표시
- 사용률(%)에 따라 색상 변경 (정상 → 주의 → 위험)
- 전체 용량 = free + used로 계산하여 표시

**학습 포인트**
- KB → GB 단위 변환
- `LinearProgressIndicator` 또는 `Canvas` 도넛 그래프
- 색상 임계값 분기 처리

---

## 8. 알림(Notification) 연동

**난이도:** ⭐⭐⭐
**관련 파일:** `monitor/ResourceMonitor.kt`, `ui/viewmodel/MonitorViewModel.kt`

CPU 또는 메모리가 임계값을 초과하면 Android 알림을 발송합니다.

**구현 항목 예시**
- CPU 90% 초과 시 "CPU 과부하" 알림 발송
- 알림 중복 방지 (일정 시간 내 1회만 발송)
- 알림 탭 시 앱 실행 및 해당 화면으로 이동

**학습 포인트**
- `NotificationManager` / `NotificationCompat` 사용법
- Android 13+ 알림 권한(`POST_NOTIFICATIONS`) 요청
- `PendingIntent`로 딥링크 처리

---

## 9. 설정 화면 (서버 주소·임계값 변경)

**난이도:** ⭐⭐**관련 파일:** `config/ServerConfig.kt`, `di/AppModule.kt`

현재 `ServerConfig`에 하드코딩된 HOST·PORT를
사용자가 앱 내에서 변경할 수 있도록 설정 화면을 추가합니다.

**구현 항목 예시**
- HOST IP 주소, PORT 번호 입력 필드
- DataStore 또는 SharedPreferences로 설정값 영속화
- 변경 후 즉시 적용 (재연결 트리거)

**학습 포인트**
- `TextField` 입력값 검증 (IP 형식, 포트 범위 0~65535)
- Jetpack DataStore 기본 사용법
- `ServerConfig`를 object 상수에서 런타임 값으로 전환하는 방법

---

## 10. 데이터 내보내기 (CSV / 로그 파일)

**난이도:** ⭐⭐⭐
**관련 파일:** `monitor/ResourceMonitor.kt`, `ui/viewmodel/MonitorViewModel.kt`

수신된 `ResourceSnapshot` 데이터를 CSV 파일로 저장하는 기능을 구현합니다.

**구현 항목 예시**
- 모니터링 중 수신 데이터를 메모리에 누적
- "내보내기" 버튼으로 CSV 파일 생성 (`/sdcard/Download/` 등)
- 공유 인텐트로 파일을 외부 앱에서 열기

**학습 포인트**
- `FileOutputStream` / `BufferedWriter`로 파일 쓰기
- Android 10+ 스코프 스토리지 (`MediaStore` API)
- `Intent.ACTION_SEND`로 파일 공유
