# PerfMon - Android 성능 모니터링 앱

PerfMon은 guider 서버와 TCP 소켓으로 통신하여 시스템 리소스(CPU, 메모리, 네트워크, 스토리지)를 실시간으로 모니터링하는 Android 앱입니다.
실제 UI 시각화보다는 **TCP 통신 및 데이터 수신 검증**에 초점을 맞춘 개발/디버그용 앱이며, 수신된 데이터는 Logcat으로 확인합니다.

---

## 목차

1. [빌드 환경](#1-빌드-환경)
2. [프로젝트 구조](#2-프로젝트-구조)
3. [아키텍처 개요](#3-아키텍처-개요)
4. [기술 스택 및 라이브러리](#4-기술-스택-및-라이브러리)
5. [파일별 상세 설명](#5-파일별-상세-설명)
   - [진입점: PerfMonApp / MainActivity](#51-진입점-perfmonapp--mainactivity)
   - [화면 (UI Layer)](#52-화면-ui-layer)
   - [ViewModel (상태 관리)](#53-viewmodel-상태-관리)
   - [Repository (데이터 요청)](#54-repository-데이터-요청)
   - [TcpClient (네트워크)](#55-tcpclient-네트워크)
   - [Data Model (데이터 구조)](#56-data-model-데이터-구조)
   - [DI: AppModule (의존성 주입)](#57-di-appmodule-의존성-주입)
6. [전체 동작 흐름](#6-전체-동작-흐름)
   - [Resource Monitor (resmon) 흐름](#61-resource-monitor-resmon-흐름)
   - [System Info (sysinfo) 흐름](#62-system-info-sysinfo-흐름)
7. [TCP 통신 프로토콜](#7-tcp-통신-프로토콜)
8. [JSON 데이터 구조](#8-json-데이터-구조)
9. [UI 상태 머신](#9-ui-상태-머신)
10. [로그 확인 방법](#10-로그-확인-방법)
11. [빌드 및 실행 방법](#11-빌드-및-실행-방법)
12. [자주 발생하는 문제](#12-자주-발생하는-문제)

---

## 1. 빌드 환경

| 항목 | 버전 |
|------|------|
| Android Studio | Ladybug (2024.2.1) 이상 |
| AGP (Android Gradle Plugin) | 8.6.0 |
| Gradle | 8.7 |
| Kotlin | 2.0.0 |
| Java | 17 |
| Compile SDK | 35 (Android 15) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |

---

## 2. 프로젝트 구조

```
perfmon/
│
├── build.gradle.kts                    # 루트 빌드 설정 (플러그인 선언)
├── settings.gradle.kts                 # 프로젝트 이름, 모듈 구성, 저장소 설정
├── gradle.properties                   # Gradle 전역 옵션
├── local.properties                    # SDK 경로 (로컬 설정, git 제외)
│
├── gradle/
│   ├── libs.versions.toml              # 모든 의존성 버전을 한곳에서 관리
│   └── wrapper/
│       ├── gradle-wrapper.jar          # Gradle 실행 바이너리
│       └── gradle-wrapper.properties   # Gradle 다운로드 URL (8.7-bin)
│
└── app/
    ├── build.gradle.kts                # 앱 모듈 빌드 설정 (의존성, SDK 버전 등)
    ├── proguard-rules.pro              # 난독화 규칙 (현재 비활성화)
    │
    └── src/main/
        ├── AndroidManifest.xml         # 앱 선언 (권한, Activity, Application)
        │
        ├── res/
        │   ├── values/
        │   │   ├── strings.xml         # 문자열 리소스
        │   │   ├── colors.xml          # 색상 리소스
        │   │   └── themes.xml          # 라이트 테마 정의
        │   ├── values-night/
        │   │   └── themes.xml          # 다크 테마 정의
        │   ├── drawable/               # 아이콘, 이미지 리소스
        │   ├── mipmap-*/               # 런처 아이콘 (해상도별)
        │   └── xml/
        │       ├── backup_rules.xml    # 백업 대상 설정
        │       └── data_extraction_rules.xml
        │
        └── java/com/oss/perfmon/
            │
            ├── PerfMonApp.kt           # Application 클래스 (Hilt 초기화)
            ├── MainActivity.kt         # 유일한 Activity (화면 전환 관리)
            │
            ├── di/
            │   └── AppModule.kt        # Hilt 의존성 제공 모듈
            │
            ├── data/
            │   ├── model/
            │   │   ├── ResmonData.kt   # resmon 응답 데이터 구조
            │   │   └── SysinfoData.kt  # sysinfo 응답 데이터 구조
            │   ├── network/
            │   │   └── TcpClient.kt    # TCP 소켓 통신 클라이언트
            │   └── repository/
            │       ├── ResmonRepository.kt   # resmon 명령 요청 + JSON 파싱
            │       └── SysinfoRepository.kt  # sysinfo 명령 요청
            │
            └── ui/
                ├── viewmodel/
                │   ├── ResmonViewModel.kt    # resmon 화면 상태 관리
                │   └── SysinfoViewModel.kt   # sysinfo 화면 상태 관리
                └── screen/
                    ├── HomeScreen.kt         # 홈 화면 (메뉴 선택)
                    ├── ResmonScreen.kt       # 리소스 모니터 화면
                    └── SysinfoScreen.kt      # 시스템 정보 화면
```

---

## 3. 아키텍처 개요

이 앱은 **MVVM (Model-View-ViewModel)** 아키텍처를 사용합니다.
각 계층은 한 방향으로만 데이터를 전달하며, UI가 직접 네트워크에 접근하지 않습니다.

```
┌─────────────────────────────────────────────────────────────┐
│                        UI Layer                             │
│   HomeScreen ──┐                                            │
│   ResmonScreen ├── ViewModel ── Repository ── TcpClient     │
│   SysinfoScreen┘                                            │
└─────────────────────────────────────────────────────────────┘
```

### 각 계층의 역할

| 계층 | 역할 | 예시 |
|------|------|------|
| **UI (Screen)** | 사용자에게 화면을 보여줌. 버튼 클릭 이벤트를 ViewModel에 전달 | `ResmonScreen.kt` |
| **ViewModel** | UI 상태(UiState)를 관리. Repository를 호출하고 결과를 상태로 변환 | `ResmonViewModel.kt` |
| **Repository** | 서버에 명령을 보내고 응답을 파싱하여 데이터 모델로 변환 | `ResmonRepository.kt` |
| **TcpClient** | 실제 TCP 소켓 연결/전송/수신/해제를 담당 | `TcpClient.kt` |
| **Data Model** | 파싱된 데이터를 담는 구조체 | `ResmonData.kt` |

---

## 4. 기술 스택 및 라이브러리

### Jetpack Compose
> 안드로이드의 최신 UI 프레임워크. XML 레이아웃 없이 Kotlin 코드로 UI를 선언적으로 작성합니다.

```kotlin
// 예시: 텍스트를 화면 중앙에 표시
Text(
    text = "PerfMon",
    style = MaterialTheme.typography.headlineLarge
)
```

### Hilt (의존성 주입)
> 클래스 간 의존 관계를 자동으로 연결해주는 프레임워크. 직접 `new TcpClient()`를 호출하지 않아도 Hilt가 알아서 주입해줍니다.

```kotlin
// Hilt가 자동으로 ResmonRepository를 주입해줌
@HiltViewModel
class ResmonViewModel @Inject constructor(
    private val repository: ResmonRepository
) : ViewModel()
```

### Navigation Compose
> 화면 전환을 관리하는 라이브러리. `NavController`로 화면 이동을 처리합니다.

```kotlin
// "resmon" 화면으로 이동
navController.navigate("resmon")
```

### Coroutines + Flow
> 비동기 처리 라이브러리. 네트워크 통신처럼 시간이 걸리는 작업을 메인 스레드를 블록하지 않고 처리합니다.

- **Flow**: 데이터 스트림. resmon처럼 연속된 데이터를 한 줄씩 받을 때 사용
- **suspend 함수**: sysinfo처럼 한 번만 요청하고 응답을 기다릴 때 사용

### Material3
> Google의 디자인 시스템. 버튼, 카드, 텍스트 스타일 등 일관된 UI 컴포넌트를 제공합니다.

---

## 5. 파일별 상세 설명

### 5.1 진입점: PerfMonApp / MainActivity

#### `PerfMonApp.kt` — 앱의 시작점

```kotlin
@HiltAndroidApp
class PerfMonApp : Application()
```

- `Application`을 상속받아 앱 전체의 생명주기를 관리합니다
- `@HiltAndroidApp` 어노테이션이 Hilt DI 시스템을 초기화합니다
- AndroidManifest.xml에 `android:name=".PerfMonApp"`으로 등록되어 있어, 앱 실행 시 가장 먼저 생성됩니다

#### `MainActivity.kt` — 유일한 Activity

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home")   { HomeScreen(navController) }
                    composable("resmon") { ResmonScreen(navController) }
                    composable("sysinfo"){ SysinfoScreen(navController) }
                }
            }
        }
    }
}
```

- 앱에 Activity가 이것 하나뿐입니다 (Single Activity 패턴)
- `NavHost`가 화면 라우터 역할을 합니다. URL처럼 문자열("home", "resmon", "sysinfo")로 화면을 구분합니다
- `@AndroidEntryPoint`: Hilt가 이 Activity에 의존성을 주입할 수 있도록 허용하는 어노테이션

---

### 5.2 화면 (UI Layer)

#### `HomeScreen.kt` — 홈 화면

앱을 실행하면 가장 먼저 보이는 화면입니다.

```
┌──────────────────────┐
│       PerfMon        │
│   com.oss.perfmon    │
│                      │
│ ┌────────────────┐   │
│ │Resource Monitor│   │
│ │  (Streaming)   │   │
│ │   [시작]       │   │
│ └────────────────┘   │
│                      │
│ ┌────────────────┐   │
│ │  System Info   │   │
│ │  (One-shot)    │   │
│ │   [시작]       │   │
│ └────────────────┘   │
└──────────────────────┘
```

- **Resource Monitor 카드**: `resmon|-a` 명령으로 실시간 스트리밍
- **System Info 카드**: `sysinfo` 명령으로 단발성 정보 조회

#### `ResmonScreen.kt` — 리소스 모니터 화면

```
┌──────────────────────────┐
│  Resource Monitor        │
│  명령어: resmon|-a       │
│  HOST: 127.0.0.1:55555   │
│ ─────────────────────────│
│  상태                    │
│  수신 중... 42회  (초록) │
│                          │
│  ※ Logcat에서 확인       │
│                          │
│  [Start]  [Stop]         │
│       [돌아가기]         │
└──────────────────────────┘
```

- 실제 데이터(CPU%, 메모리 등)는 **화면에 표시하지 않고** Logcat에만 출력합니다
- 화면에는 현재 상태(대기/연결중/수신중/오류)와 몇 번째 데이터를 받았는지만 표시합니다

#### `SysinfoScreen.kt` — 시스템 정보 화면

```
┌──────────────────────────┐
│  System Info             │
│  명령어: sysinfo         │
│  HOST: 127.0.0.1:55555   │
│ ─────────────────────────│
│  상태                    │
│  수신 완료: 15줄  (초록) │
│                          │
│  ※ Logcat에서 확인       │
│                          │
│  [      Fetch      ]     │
│       [돌아가기]         │
└──────────────────────────┘
```

- Fetch 버튼을 누르면 서버에 한 번 요청하고, 응답을 모두 받으면 완료로 표시합니다

---

### 5.3 ViewModel (상태 관리)

ViewModel은 **화면에 보여줄 상태**를 관리합니다. 화면(Screen)은 ViewModel이 가진 상태만 보고 그림을 그립니다.

#### `ResmonViewModel.kt`

```kotlin
sealed class UiState {
    object Idle    : UiState()                       // 아무것도 안 함
    object Loading : UiState()                       // 연결 중
    data class Running(val count: Int) : UiState()   // 수신 중 (count번째)
    data class Error(val message: String) : UiState() // 오류 발생
}
```

| 상태 | 의미 | 화면 표시 |
|------|------|----------|
| `Idle` | 기본 대기 상태 | "대기 중" (회색) |
| `Loading` | TCP 연결 시도 중 | "연결 중..." (파란색) |
| `Running(n)` | n번째 JSON 데이터 수신 완료 | "수신 중... n회" (초록색) |
| `Error(msg)` | 연결 실패 또는 파싱 오류 | "오류: msg" (빨간색) |

**상태 전환 흐름:**
```
Idle ──[Start 클릭]──▶ Loading ──[첫 데이터 수신]──▶ Running
                                                         │
                                                    [Stop 클릭]
                                                         │
                                                         ▼
                                                       Idle
Running/Loading ──[오류]──▶ Error ──[Start 클릭]──▶ Loading
```

**`startMonitoring()` 동작:**

```kotlin
fun startMonitoring() {
    if (monitoringJob?.isActive == true) return  // 이미 실행 중이면 무시

    _uiState.value = UiState.Loading
    var count = 0

    monitoringJob = viewModelScope.launch {      // 백그라운드 코루틴 시작
        repository.requestResmon().collect { result ->
            result.fold(
                onSuccess = { count++; _uiState.value = UiState.Running(count) },
                onFailure = { _uiState.value = UiState.Error(it.message ?: "Unknown") }
            )
        }
    }
}
```

#### `SysinfoViewModel.kt`

```kotlin
sealed class UiState {
    object Idle    : UiState()                        // 아무것도 안 함
    object Loading : UiState()                        // 조회 중
    data class Done(val lineCount: Int) : UiState()   // 완료 (n줄 수신)
    data class Error(val message: String) : UiState() // 오류
}
```

| 상태 | 의미 |
|------|------|
| `Idle` | Fetch 버튼 누르기 전 |
| `Loading` | 서버에 요청 보내고 응답 기다리는 중 |
| `Done(n)` | n줄 수신 완료 |
| `Error(msg)` | 연결 실패 |

---

### 5.4 Repository (데이터 요청)

Repository는 TcpClient로부터 받은 날(raw) 데이터를 앱이 쓸 수 있는 형태로 변환합니다.

#### `ResmonRepository.kt` — 스트리밍 방식

```kotlin
fun requestResmon(): Flow<Result<ResmonData>> {
    return tcpClient.sendCommandAsLines("resmon|-a").map { result ->
        result.mapCatching { jsonLine ->
            val data = parseResmonJson(jsonLine)  // JSON 문자열 → ResmonData 객체
            Log.d(TAG, data.toString())           // Logcat 출력
            data
        }
    }
}
```

- `sendCommandAsLines()`에서 JSON 한 줄이 들어올 때마다 `map`으로 파싱합니다
- 파싱 실패해도 Flow가 끊기지 않고, 해당 항목만 `Result.failure`로 emit합니다

**JSON 파싱 로직 — CPU 사용률 계산:**
```kotlin
private fun parseCpu(obj: JSONObject): CpuData {
    val idle  = obj.optInt("idle", 0)   // 유휴 시간
    val total = obj.optInt("total", 0)  // 총 사용 시간
    // 사용률(%) = total / (total + idle) * 100
    val usage = if (idle == 0 && total == 0) 0
                else total * 100 / (total + idle)
    return CpuData(usage)
}
```

#### `SysinfoRepository.kt` — 단발성 방식

```kotlin
suspend fun getSysinfo(): Result<SysinfoData> {
    return tcpClient.sendCommandAsList("sysinfo").mapCatching { lines ->
        Log.d(TAG, "Received ${lines.size} lines")  // 수신 라인 수 출력
        SysinfoData(lines)
    }
}
```

- `suspend` 함수: 코루틴 안에서만 호출 가능
- 모든 라인을 한번에 받아 `SysinfoData`로 래핑

---

### 5.5 TcpClient (네트워크)

TCP 소켓 통신의 모든 세부 처리를 담당합니다.

```kotlin
companion object {
    const val HOST = "127.0.0.1"  // guider 서버 주소 (로컬)
    const val PORT = 55555         // guider 서버 포트
}
```

#### 두 가지 통신 방식

| 메서드 | 용도 | 반환 타입 |
|--------|------|-----------|
| `sendCommandAsLines()` | resmon (스트리밍) | `Flow<Result<String>>` |
| `sendCommandAsList()` | sysinfo (단발성) | `Result<List<String>>` |

#### 소켓 내부 상태

```kotlin
private var socket: Socket? = null        // TCP 소켓
private var reader: BufferedReader? = null // 수신용 스트림 (라인 단위 읽기)
private var writer: PrintWriter? = null   // 송신용 스트림
```

#### `sendCommandAsLines()` 내부 동작

```kotlin
fun sendCommandAsLines(command: String): Flow<Result<String>> = flow {
    try {
        if (connect() && sendAndWaitAck(command)) {  // 연결 + ACK 확인
            reader?.lineSequence()?.forEach { line ->
                emit(Result.success(line))            // 한 줄씩 emit
            }
        } else {
            emit(Result.failure(Exception(CONNECTION_ERROR)))
        }
    } catch (e: Exception) {
        emit(Result.failure(Exception(e.message)))
    } finally {
        disconnect()                                 // 항상 연결 해제
    }
}.flowOn(Dispatchers.IO)  // IO 스레드에서 실행 (메인 스레드 블록 방지)
```

#### `connect()` — 소켓 연결

```kotlin
private fun connect(): Boolean {
    // 이미 연결된 소켓이 있으면 재사용
    if (socket?.isConnected == true && socket?.isClosed == false) return true

    socket = Socket(HOST, PORT)   // TCP 연결 시도
    socket?.let {
        reader = BufferedReader(InputStreamReader(it.getInputStream()))
        writer = PrintWriter(it.getOutputStream())
    }
    return socket?.isConnected ?: false
}
```

#### `sendAndWaitAck()` — 명령 전송 후 ACK 확인

```kotlin
private fun sendAndWaitAck(message: String): Boolean {
    send(message)              // 명령 전송 ("resmon|-a" 또는 "sysinfo")
    val buffer = CharArray(3)
    val bytesRead = reader?.read(buffer)           // 3바이트 읽기
    return (bytesRead == 3 && String(buffer) == "ACK")  // "ACK" 확인
}
```

#### `disconnect()` — 연결 해제

```kotlin
private fun disconnect() {
    try {
        socket?.let {
            if (!it.isInputShutdown) it.shutdownInput()
            if (!it.isOutputShutdown) it.shutdownOutput()
            it.close()
        }
        reader?.close()
        writer?.close()
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        socket = null; reader = null; writer = null  // 상태 초기화
    }
}
```

---

### 5.6 Data Model (데이터 구조)

#### `ResmonData.kt` — resmon 응답 구조

```kotlin
data class ResmonData(
    val cpu      : CpuData,
    val memory   : MemoryData,
    val network  : NetworkData,
    val storage  : StorageData,
    val processes: Map<Int, ProcessData>  // key = PID
)

data class CpuData(val usagePercent: Int)          // CPU 사용률 (0~100%)

data class MemoryData(
    val anonKb     : Int,  // 앱 힙 등 익명 메모리 (KB)
    val kernelKb   : Int,  // 커널 메모리 (KB)
    val availableKb: Int   // 사용 가능한 메모리 (KB)
)

data class NetworkData(
    val inboundBytes : Int,  // 수신 바이트
    val outboundBytes: Int   // 송신 바이트
)

data class StorageData(
    val freeKb: Int,  // 여유 공간 (KB)
    val usedKb: Int   // 사용 중 공간 (KB)
)

data class ProcessData(
    val name   : String,  // 프로세스 이름 (comm)
    val cpuTime: Int,     // CPU 누적 시간 (ttime)
    val rssKb  : Int      // 상주 메모리 - Resident Set Size (KB)
)
```

#### `SysinfoData.kt` — sysinfo 응답 구조

```kotlin
data class SysinfoData(val lines: List<String>)
// 서버에서 받은 텍스트 라인들을 그대로 보관
```

---

### 5.7 DI: AppModule (의존성 주입)

Hilt가 어떤 클래스에 어떤 객체를 주입할지 정의하는 모듈입니다.

```kotlin
@Module
@InstallIn(SingletonComponent::class)  // 앱 생명주기와 동일한 범위
object AppModule {

    // @Singleton 없음: 매번 새 TcpClient 인스턴스 생성
    @Provides
    fun provideTcpClient(): TcpClient = TcpClient()

    @Provides
    @Singleton  // 앱 전체에서 하나의 인스턴스만 사용
    fun provideResmonRepository(client: TcpClient): ResmonRepository =
        ResmonRepository(client)

    @Provides
    @Singleton
    fun provideSysinfoRepository(client: TcpClient): SysinfoRepository =
        SysinfoRepository(client)
}
```

**TcpClient를 Singleton으로 만들지 않는 이유:**

TcpClient는 내부에 `socket`, `reader`, `writer` 같은 **상태 변수**를 가지고 있습니다.
만약 하나의 TcpClient를 ResmonRepository와 SysinfoRepository가 공유하면,
동시에 두 화면에서 접근할 때 소켓 상태가 충돌하여 오류가 발생합니다.
따라서 Repository마다 독립적인 TcpClient를 가집니다.

```
ResmonRepository  ──▶  TcpClient (인스턴스 A) ──▶ 소켓 연결 A
SysinfoRepository ──▶  TcpClient (인스턴스 B) ──▶ 소켓 연결 B
```

---

## 6. 전체 동작 흐름

### 6.1 Resource Monitor (resmon) 흐름

```
사용자: [Start] 버튼 클릭
         │
         ▼
ResmonScreen.kt
  └─▶ viewModel.startMonitoring()
         │
         ▼
ResmonViewModel.kt
  ├─ UiState = Loading
  └─▶ viewModelScope.launch { repository.requestResmon().collect { ... } }
         │
         ▼
ResmonRepository.kt
  └─▶ tcpClient.sendCommandAsLines("resmon|-a")
         │
         ▼
TcpClient.kt  [IO 스레드]
  ├─ Socket("127.0.0.1", 55555) 연결
  ├─ "resmon|-a" 전송
  ├─ "ACK" 3바이트 수신 확인
  └─ reader.lineSequence() 로 JSON 한 줄씩 읽기
         │ (JSON 한 줄 수신마다 반복)
         ▼
ResmonRepository.kt
  ├─ parseResmonJson(jsonLine) → ResmonData 객체 변환
  └─ Log.d("ResmonRepository", data.toString())  → Logcat 출력
         │
         ▼
ResmonViewModel.kt
  ├─ count++
  └─ UiState = Running(count)
         │
         ▼
ResmonScreen.kt
  └─ "수신 중... n회" 텍스트 업데이트
```

### 6.2 System Info (sysinfo) 흐름

```
사용자: [Fetch] 버튼 클릭
         │
         ▼
SysinfoScreen.kt
  └─▶ viewModel.fetchSysinfo()
         │
         ▼
SysinfoViewModel.kt
  ├─ UiState = Loading
  └─▶ viewModelScope.launch { repository.getSysinfo() }
         │
         ▼
SysinfoRepository.kt
  └─▶ tcpClient.sendCommandAsList("sysinfo")
         │
         ▼
TcpClient.kt  [IO 스레드]
  ├─ Socket("127.0.0.1", 55555) 연결
  ├─ "sysinfo" 전송
  ├─ "ACK" 3바이트 수신 확인
  ├─ socket.soTimeout = 2000ms 설정 (2초 내 응답 없으면 읽기 종료)
  └─ reader.lineSequence().toList() 로 모든 라인 수집
         │
         ▼
SysinfoRepository.kt
  └─ Log.d("SysinfoRepository", "Received n lines")  → Logcat 출력
         │
         ▼
SysinfoViewModel.kt
  └─ UiState = Done(lineCount)
         │
         ▼
SysinfoScreen.kt
  └─ "수신 완료: n줄" 텍스트 업데이트
```

---

## 7. TCP 통신 프로토콜

guider 서버와의 통신 규약입니다.

```
Android App                        guider 서버 (127.0.0.1:55555)
    │                                        │
    │──── TCP 연결 (3-way handshake) ────────▶│
    │                                        │
    │──── 명령 전송 ─────────────────────────▶│
    │     "resmon|-a" 또는 "sysinfo"          │
    │                                        │
    │◀─── ACK 수신 (3바이트) ────────────────│
    │     "ACK"                              │
    │                                        │
    │◀─── 데이터 수신 ───────────────────────│
    │     [resmon] JSON 한 줄씩 스트리밍      │
    │     [sysinfo] 텍스트 라인 목록          │
    │                                        │
    │──── 연결 종료 ──────────────────────────│
```

### 명령어 형식

| 명령 | 형식 | 설명 |
|------|------|------|
| resmon | `resmon\|-a` | 리소스 모니터링 스트리밍. `-a`는 all processes 옵션 |
| sysinfo | `sysinfo` | 시스템 정보 단발성 조회 |

### ACK 확인이 중요한 이유

ACK를 확인하지 않으면 서버가 아직 준비되지 않은 상태에서 데이터를 읽으려 해서 오류가 발생할 수 있습니다.
3바이트가 정확히 "ACK"인지 검증하여 서버의 준비 완료를 확인합니다.

---

## 8. JSON 데이터 구조

서버(guider)가 resmon 명령에 응답하는 JSON 형식입니다.

```json
{
  "cpu": {
    "total": 80,
    "idle": 20
  },
  "mem": {
    "anon": 512000,
    "kernel": 102400,
    "available": 2048000
  },
  "net": {
    "inbound": 1024,
    "outbound": 512
  },
  "storage": {
    "total": {
      "free": 10240,
      "usage": 5120
    }
  },
  "process": {
    "1234": {
      "comm": "system_server",
      "ttime": 500,
      "rss": 102400
    },
    "5678": {
      "comm": "com.example.app",
      "ttime": 120,
      "rss": 51200
    }
  }
}
```

### 파싱 후 앱 내부 데이터 구조

| JSON 키 | 앱 내부 필드 | 단위 | 설명 |
|---------|------------|------|------|
| `cpu.total` / `cpu.idle` | `CpuData.usagePercent` | % | `total/(total+idle)*100` 계산 |
| `mem.anon` | `MemoryData.anonKb` | KB | 익명 메모리(앱 힙 등) |
| `mem.kernel` | `MemoryData.kernelKb` | KB | 커널이 사용하는 메모리 |
| `mem.available` | `MemoryData.availableKb` | KB | 사용 가능한 여유 메모리 |
| `net.inbound` | `NetworkData.inboundBytes` | Bytes | 수신 데이터량 |
| `net.outbound` | `NetworkData.outboundBytes` | Bytes | 송신 데이터량 |
| `storage.total.free` | `StorageData.freeKb` | KB | 스토리지 여유 공간 |
| `storage.total.usage` | `StorageData.usedKb` | KB | 스토리지 사용 중인 공간 |
| `process.{pid}.comm` | `ProcessData.name` | - | 프로세스 이름 |
| `process.{pid}.ttime` | `ProcessData.cpuTime` | - | CPU 누적 시간 |
| `process.{pid}.rss` | `ProcessData.rssKb` | KB | 실제 사용 중인 메모리 |

> **참고:** ttime과 rss가 모두 0인 프로세스는 파싱 시 자동으로 제외됩니다.

---

## 9. UI 상태 머신

Compose UI는 ViewModel이 가진 `UiState`를 보고 화면을 다시 그립니다.
이를 **상태 기반 UI (State-driven UI)** 라고 합니다.

### 상태 구독 방식 (Compose)

```kotlin
// ResmonScreen.kt
val uiState by viewModel.uiState.collectAsState()
// uiState가 바뀔 때마다 이 Composable이 자동으로 재실행(recomposition)됨

val (statusText, statusColor) = when (val state = uiState) {
    is ResmonViewModel.UiState.Idle    -> "대기 중"      to 회색
    is ResmonViewModel.UiState.Loading -> "연결 중..."   to 파란색
    is ResmonViewModel.UiState.Running -> "수신 중... ${state.count}회" to 초록색
    is ResmonViewModel.UiState.Error   -> "오류: ${state.message}"      to 빨간색
}
```

### StateFlow 동작 원리

```
ViewModel                    Screen
    │                           │
    │  _uiState (쓰기 전용)      │
    │  val uiState (읽기 전용)   │
    │                           │
    │──── UiState.Running ──────▶│  화면 재구성
    │──── UiState.Error  ──────▶│  오류 텍스트 표시
```

- `MutableStateFlow`: ViewModel 내부에서만 값을 변경 가능 (`_uiState`)
- `StateFlow`: Screen에서 읽기만 가능 (`uiState`)
- `collectAsState()`: Flow의 최신 값을 Compose 상태로 변환

---

## 10. 로그 확인 방법

실제 수신 데이터는 화면에 표시되지 않고 **Android Studio Logcat** 또는 **adb logcat**으로 확인합니다.

### Android Studio Logcat 사용 (권장)

1. Android Studio 하단 **Logcat** 탭 클릭
2. 필터 입력창에 태그 입력:

| 확인할 데이터 | 필터 태그 |
|--------------|----------|
| resmon 수신 데이터 | `ResmonRepository` |
| sysinfo 수신 라인 수 | `SysinfoRepository` |

### adb logcat 명령 사용

```bash
# resmon 데이터만 필터
adb logcat -s ResmonRepository

# sysinfo 라인 수만 필터
adb logcat -s SysinfoRepository

# 두 가지 동시에 보기
adb logcat -s ResmonRepository:D SysinfoRepository:D
```

### 로그 출력 예시

#### ResmonRepository 로그 (resmon)
```
D/ResmonRepository: ResmonData(
    cpu=CpuData(usagePercent=35),
    memory=MemoryData(anonKb=524288, kernelKb=102400, availableKb=2097152),
    network=NetworkData(inboundBytes=1024, outboundBytes=512),
    storage=StorageData(freeKb=10240, usedKb=5120),
    processes={1234=ProcessData(name=system_server, cpuTime=500, rssKb=102400)}
  )
```

#### SysinfoRepository 로그 (sysinfo)
```
D/SysinfoRepository: Received 15 lines
```

---

## 11. 빌드 및 실행 방법

### Android Studio에서 빌드 (권장)

#### 0단계: Android Studio 설치
1. https://developer.android.com/studio?hl=ko에서 설치
2. Android Studio 실행하여 GitHub 탭에서 로그인 정보 입력
3. Repository URL 팁에서 Clone Repository 실행
4. URL에 https://github.com/iipeace/perfmon 입력 후 Clone 버튼 누르기

#### 1단계: 프로젝트 열기 (0단계 진행하지 않고 이미 수동으로 환경 설정한 경우)

1. Android Studio 실행
2. **File > Open** → `perfmon` 폴더 선택
3. Gradle sync 자동 시작 (최초 1회 Gradle 8.7 다운로드 필요)

#### 2단계: `local.properties` 확인

프로젝트 루트의 `local.properties` 파일에 **현재 OS에 맞는** SDK 경로가 설정되어 있어야 합니다.

파일이 없거나 경로가 다른 OS 형식으로 되어 있으면 Android Studio가 자동으로 생성/수정합니다.
수동으로 설정하는 경우 아래 형식을 사용합니다.

**Windows:**
```properties
sdk.dir=C\:\\Users\\사용자명\\AppData\\Local\\Android\\Sdk
```

**Linux / macOS:**
```properties
sdk.dir=/home/사용자명/Android/Sdk
```

> `local.properties`는 `.gitignore`에 포함되어 있어 버전 관리에서 제외됩니다.
> 새 환경에서 클론하면 반드시 이 파일을 새로 설정해야 합니다.

#### 3단계: 빌드 및 실행

- 상단 Run 버튼 (▶) 클릭 또는 `Shift+F10`
- 빌드만 하려면: **Build > Make Project** 또는 `Ctrl+F9`

---

### 터미널에서 빌드

```bash
# Debug APK 빌드
./gradlew assembleDebug

# 연결된 기기에 설치
./gradlew installDebug

# 빌드 + 설치 + 실행 한 번에
./gradlew installDebug && adb shell am start -n com.oss.perfmon/.MainActivity
```

빌드된 APK 위치: `app/build/outputs/apk/debug/app-debug.apk`

---

### 에뮬레이터(AVD) 생성 및 실행

실물 기기가 없을 경우 Android Studio의 에뮬레이터를 사용합니다.

#### 1단계: AVD Manager 열기

```
Tools → Device Manager
```

#### 2단계: 가상 기기 생성

1. **+** 버튼 클릭 → **Create Virtual Device**
2. **Phone** 카테고리에서 기기 선택 (예: Pixel 6) → **Next**
3. 시스템 이미지 선택:
   - API Level **26 이상** 선택 (앱 minSdk = 26)
   - 권장: **API 35 (Android 15)**, ABI: `x86_64`
   - 이미지가 없으면 옆의 **Download** 클릭 후 설치
4. **Next** → AVD 이름 확인 → **Finish**

#### 3단계: 에뮬레이터 실행

- Device Manager에서 생성한 AVD 옆 ▶ 버튼 클릭
- 또는 Android Studio 상단 기기 선택 드롭다운에서 AVD 선택 후 Run (▶)

#### 4단계: 에뮬레이터에서 서버 연결 설정

에뮬레이터는 호스트 PC의 `127.0.0.1`에 직접 접근할 수 없습니다.
에뮬레이터에서 `10.0.2.2`가 호스트 PC의 localhost에 해당합니다.

**방법 A — adb 포트 포워딩 (권장):**
```bash
# 에뮬레이터의 55555 포트를 호스트 PC의 55555 포트로 포워딩
adb forward tcp:55555 tcp:55555
```
이 방법을 쓰면 앱 코드(`ServerConfig.kt`)를 수정하지 않아도 됩니다.

**방법 B — 서버 주소 직접 변경:**
`app/src/main/java/com/oss/perfmon/config/ServerConfig.kt`에서 HOST 수정:
```kotlin
const val HOST = "10.0.2.2"  // 에뮬레이터에서 호스트 PC의 localhost
```

---

### 실행 전 확인 사항

| 확인 항목 | 방법 |
|----------|------|
| guider 서버가 실행 중인지 | 서버 로그 확인 |
| 포트 55555가 열려있는지 | `adb shell nc -zv 127.0.0.1 55555` |
| 기기가 연결됐는지 | `adb devices` |
| 에뮬레이터 포트 포워딩 | `adb forward tcp:55555 tcp:55555` |

---

## 12. 자주 발생하는 문제

### 문제 1: "서버에 연결할 수 없습니다" 오류

**원인:** guider 서버가 실행되지 않았거나, 포트가 다름

**해결 방법:**
1. guider 서버 실행 상태 확인
2. `TcpClient.kt`의 `PORT` 값이 실제 서버 포트와 일치하는지 확인
   ```kotlin
   const val PORT = 55555  // 여기를 실제 포트로 수정
   ```

### 문제 2: Gradle Sync 실패

**원인:** 인터넷 연결 없음 또는 Java 버전 불일치

**해결 방법:**
- 인터넷 연결 확인 (Maven Central, Google Maven에서 의존성 다운로드 필요)
- JDK 17 설치 확인: **File > Project Structure > SDK Location > JDK Location**
- Gradle 캐시 삭제: `File > Invalidate Caches and Restart`

### 문제 3: "수신된 데이터가 없습니다" 오류 (sysinfo)

**원인:** 서버가 ACK는 보냈지만 데이터를 보내지 않음, 또는 2초 타임아웃 초과

**해결 방법:**
- `TcpClient.kt`에서 타임아웃 값 늘리기:
  ```kotlin
  socket?.soTimeout = 5000  // 2000ms → 5000ms
  ```

### 문제 4: Logcat에 로그가 안 보임

**원인:** Logcat 필터 설정 문제

**해결 방법:**
1. Android Studio Logcat에서 필터를 `No Filters`로 변경 후 검색
2. 기기 로그 레벨이 Debug 이상으로 설정되어 있는지 확인:
   ```bash
   adb shell setprop log.tag.ResmonRepository D
   ```

### 문제 5: Windows에서 경로 오류

**원인:** `local.properties`의 SDK 경로가 Linux 형식으로 되어 있음

**해결 방법:**
`local.properties` 파일을 열어 SDK 경로를 Windows 형식으로 수정:
```properties
# Windows 형식
sdk.dir=C\:\\Users\\사용자명\\AppData\\Local\\Android\\Sdk
```

또는 `local.properties` 파일을 삭제하면 Android Studio가 자동으로 현재 OS에 맞게 재생성합니다.

---

## 의존성 버전 관리 (`gradle/libs.versions.toml`)

이 앱은 **Version Catalog** 방식으로 모든 의존성 버전을 한 파일에서 관리합니다.

```toml
[versions]
kotlin = "2.0.0"      # ← 여기서 버전을 바꾸면
...

[plugins]
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
# ↑ 자동으로 모든 kotlin 관련 의존성에 반영됨
```

버전을 올리거나 내릴 때는 `[versions]` 섹션만 수정하면 됩니다.
