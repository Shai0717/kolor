# Kolor

Kolor 1.0은 Paper와 Folia 26.3을 위한 경량 기반 프레임워크입니다. Java 25를 대상으로 하며 NMS나 Kotlin 런타임을 JAR에 포함하지 않습니다. Paper의 플러그인 로더가 Maven Central 미러에서 Kotlin 표준 라이브러리 2.3.20을 받아 시작 시 제공합니다. 최초 실행에는 저장소 접근이 필요합니다.

## 라이선스와 호환성 목표

Kolor는 GNU General Public License version 3.0만 적용합니다
([GPL-3.0-only](LICENSE)). 호환 계층은 원본 구현 코드를 복사하지 않는
클린룸 원칙을 유지하면서 Kommand와 Tap의 공개 API 구조 및 기능에 가능한
한 가깝게 구현하는 것을 목표로 합니다. 현재 지원 범위는 일부이며,
NMS·리플렉션 없이 구현할 수 없는 패킷 전용 기능은 동일하게 제공하지 않습니다.

## 빌드 및 설치

Windows에서는 Java 25 환경에서 다음 명령을 실행합니다.

```powershell
.\gradlew.bat build
```

`build/libs/Kolor-1.0.0.jar`를 Paper 또는 Folia 26.3 서버에 설치합니다. Kolor를 사용하는 별도 플러그인은 `compileOnly` 의존성을 선언하고 Paper 플러그인 의존성에 `join-classpath: true`를 설정해야 합니다.

```yaml
dependencies:
  server:
    Kolor:
      load: BEFORE
      required: true
      join-classpath: true
```

사용 플러그인은 Paper 플러그인 의존성에 `join-classpath: true`를 설정해 Kolor가 제공하는 Kotlin 표준 라이브러리를 공유해야 합니다. 같은 Kotlin 클래스를 별도 플러그인 클래스 로더에 중복 추가하면 타입 식별 오류가 발생할 수 있습니다. 독립 클래스 로더를 써야 하는 경우 클래스 경계와 런타임 공유 여부를 검증하고, Kotlin 함수 타입 대신 JDK 인터페이스를 사용하는 `KolorInterop`을 이용하세요.

## 스케줄링

Kotlin API는 전역·비동기·위치·엔티티 소유 스케줄링을 제공합니다.

```kotlin
val scheduler = KolorScheduler(this)
scheduler.runGlobal(delayTicks = 1) { /* 전역 상태 */ }
with(scheduler) {
    location.runAt { /* 해당 위치 영역의 상태 */ }
    player.runOn(retiredCallback = { /* 알림만 처리 */ }) { /* 해당 플레이어 상태 */ }
}
scheduler.runAsync { /* 월드나 엔티티에 접근하지 않는 작업 */ }
```

런타임 경계의 클래스 로더를 넘는 플러그인은 `KolorInterop`의 Java 인터페이스 기반 오버로드를 사용할 수 있습니다.

```kotlin
val scheduler = KolorScheduler(this)
KolorInterop.runGlobal(scheduler, 1, 0, Runnable { /* 전역 작업 */ })
KolorInterop.runAsync(scheduler, 1, 0, Runnable { /* 비동기 작업 */ })
```

`KolorInterop`은 위치용 `runAt`과 엔티티용 `runOn`도 `Runnable` 콜백으로 제공합니다. 모든 지연·주기는 틱 단위이며 음수는 허용되지 않습니다. 전역/위치/엔티티 반복 작업은 적어도 다음 틱부터 실행합니다. 비동기 스케줄러의 틱은 50ms의 벽시계 시간입니다. 주기 0은 단발 실행입니다.

엔티티 스케줄링은 엔티티가 이미 제거된 경우 `null`을 반환할 수 있으며, 등록 후 엔티티가 제거되면 retired 콜백이 호출될 수 있습니다. retired 콜백은 Paper의 제한된 실행 컨텍스트에서 실행되므로 엔티티·월드 접근이나 청크 로딩을 하지 마세요. 작업 취소는 retired 콜백을 호출하지 않습니다.

## 명령

Paper의 명령 생명주기 이벤트 전에 명령을 등록합니다.

```kotlin
kolorCommand("example", "ex") {
    requires { it.hasPermission("example.use") }
    literal("count") {
        intArg("n", 1, 10) {
            executes { ctx -> ctx.sender.sendMessage("Count: ${ctx.int("n")}"); 1 }
        }
    }
    literal("safe") {
        executesOnEntity { player, _ -> player.sendMessage("On your region") }
    }
}
```

`executes`는 Paper 명령 호출 스레드에서 실행되므로 엔티티·월드 작업은 명시적으로 디스패치해야 합니다. `executesOnEntity`는 플레이어 명령을 엔티티 스케줄러에 등록하며, 작업 완료가 아니라 등록 성공 여부를 반환합니다. 캡처한 명령 컨텍스트는 변경 가능한 월드 상태의 스냅샷이 아닙니다. 인자 `player(name)`은 Paper 셀렉터를 해석하므로 적절한 서버 컨텍스트에서만 사용하세요. 자동 완성 및 권한 조건에서도 관련 없는 영역에 접근하면 안 됩니다.

호환 계층은 마이그레이션에 필요한 일부 API만 제공합니다. 다른 라이브러리와 바이너리 또는 전체 소스 호환을 보장하지 않습니다.

```kotlin
import io.github.Shai0717.kolor.compat.kommand

kommand {
    register("legacy") {
        requires { it.hasPermission("example.use") }
        then("hello") { executesOnEntity { player, _ -> player.sendMessage("Hello") } }
        then("count" to int(1, 10), "name" to string()) {
            executes { context ->
                val count: Int by context
                val name: String = context["name"]
                context.sender.sendMessage("$name: $count")
            }
        }
    }
}
```

`then`은 리터럴과 타입 인자 쌍을 지원합니다. `string`, `word`,
`greedyString`, `int`, `long`, `float`, `double`, `boolean`, `player`와
사용자 변환 함수 `dynamic { context, input -> ... }`를 사용할 수 있습니다.
`dynamic`은 한 단어를 소비하고 실행 중 값을 변환하며, 자동으로 값의 목록이나
완성 후보를 만들지 않습니다. 변환에서 `IllegalArgumentException`이 발생하면
명령 구문 오류로 전달됩니다. 같은 이름의 기존 라이브러리 인자와 완전히
같은 해석이나 호출 형태를 보장하지 않습니다. 기본 명령 DSL의 인자 빌더도
`register` 및 `then` 안에서 사용할 수 있습니다.

## 표시 및 수학 유틸리티

`KolorInventory`는 상자형 인벤토리, 테두리 채우기, Adventure 이름 및 설명이 있는 아이템을 만듭니다. 클릭 처리와 GUI 세션 관리는 호출 플러그인의 책임입니다. `KolorDisplay.item`은 선택적으로 유한한 양수 크기 배율을 받을 수 있습니다.

`KolorDisplay` 및 `compat.FakeEntityServer`는 실제 비영구 디스플레이 엔티티를 생성합니다. `FakeEntityServer`는 아이템·텍스트·블록 표시를 지원하며 `FakeEntity`는 표시, 숨김, 수정, 이동 및 제거를 제공합니다. 뷰어를 지정하면 기본적으로 숨겨져 있으며 지정한 뷰어에게 표시합니다. NMS 패킷 전용 엔티티가 아니며 다른 서버 플러그인에서도 볼 수 있습니다. 생성은 위치 영역, 표시/숨김은 뷰어 엔티티 스케줄러에서 실행됩니다. Future는 뷰어 표시 작업이 요청되었을 때 완료됩니다. `remove`를 호출하고 null 작업 및 retired 콜백을 처리하세요. `teleport`의 반환값은 스케줄 등록 여부이며 이동 완료를 의미하지 않습니다. 이동 실패는 로그에 기록됩니다.

`compat.FakeBlockView`는 Paper의 플레이어별 블록 변경 API를 사용해 실제
월드를 변경하지 않고 클라이언트에 블록을 표시합니다. `show`는 플레이어
스케줄러에서 요청을 전송하고, `restore`는 위치 소유 영역에서 실제 블록을
읽은 뒤 플레이어 스케줄러에서 전송합니다. 두 메서드의 Future를 확인하세요.
복원할 청크가 로드되지 않았거나 뷰어가 다른 월드에 있으면 실패합니다.
클라이언트 청크 갱신이나 재접속은 표시를 덮어쓸 수 있으므로 장기 추적 및
재전송은 호출자의 책임입니다. 실제 플레이어 클라이언트 검증은 아직 필요합니다.

원본 Tap의 임의 패킷 전송, 가상 플레이어, 가상 발사체 및 문자열 템플릿은
구현되어 있지 않습니다. 기존 라이브러리와 같은 API나 바이너리 호환을
보장하지 않습니다.

표시 엔티티의 `transform`은 엔티티 소유 스레드에서 호출해야 합니다. `lookTarget`과 `compat.raycast`는 블록과 엔티티를 포함한 월드 레이 트레이스를 사용합니다. 플레이어 소유 영역에서 호출하고, 광선이 통과하는 모든 청크/영역을 호출자가 소유하는지 확인하세요. 긴 영역 간 광선은 자동으로 Folia 안전해지지 않습니다. Vector와 Location 연산자는 복사본을 반환합니다.

## Tap 스타일 유틸리티

다음 API는 기존 Tap 구현을 복사하지 않은 **대체 API**이며, 원본과
시그니처 또는 모든 동작이 같지는 않습니다.

- `Ticker.global(plugin)`, `Ticker.at(plugin, location)`, `Ticker.on(plugin, entity)`는
  각각 전역·위치·엔티티 스케줄러를 이용합니다. `runTask`, `runTaskTimer`,
  `cancelAll`, `close`를 제공하며 엔티티가 이미 제거됐으면 등록 결과가
  `null`입니다. `close`는 등록된 작업을 취소하지만 이미 실행 중인 콜백을
  중단하지는 않습니다. 엔티티 retired 콜백에는 위 스케줄러 주의사항이
  동일하게 적용됩니다.
- `EntityEvents(plugin).listen(entity, eventType, target = ..., action = ...)`은
  이벤트에서 대상 엔티티를 추출해 UUID로 필터링합니다. 반환된 구독을
  `close`하여 해제하세요. 콜백은 이벤트 발생 스레드에서 실행되며 자동으로
  엔티티 스케줄러로 전달되지 않습니다. 구독 생성 시 대상의 UUID를 읽으므로
  엔티티 소유권을 지키고, 실행 중인 콜백은 구독 종료로 중단되지 않습니다.
- `plugin.persistentKey(name, type)`은 Paper의 PDC 타입을 묶습니다.
  `container[key] = value`, `container[key]`, `container.has(key)`,
  `container.remove(key)`, `container.require(key)`를 지원합니다.
  `require`는 누락되거나 호환되지 않는 값에 대해 예외를 발생시킵니다.
  사용 시 해당 확장 함수를 import하고 PDC 소유 객체의 스레드 소유권을 지키세요.

## 모듈

플러그인은 `KolorPlugin`을 상속하고 고유 ID를 가진 `KolorModule`을 연결하거나 명시적으로 해제할 수 있습니다. 플러그인 종료 시 남아 있는 모듈은 해제됩니다. 추가 정리는 `onKolorDisable`에서 수행하세요. 모듈 콜백은 순차 호출되며 영역에 종속된 작업은 적절한 스케줄러로 전달해야 합니다.

## 설정

`KolorConfig.load(file, resourceOwner, resourcePath)`는 UTF-8 YAML을 읽고 내장 리소스를 기본값으로 연결합니다. 설정 파일이 없으면 내장 YAML로 먼저 생성합니다. `resourcePath`는 클래스패스 루트 기준 경로 또는 절대 클래스 리소스 경로를 사용할 수 있습니다. YAML이 잘못되었거나 기본 리소스가 없으면 명시적으로 실패하므로, 호출자는 새 설정을 활성 값으로 교체하기 전에 검증해야 합니다.

`KolorSchema`는 설정 섹션에서 타입이 명확한 값을 읽고 누락된 기본값을 기록합니다. 숫자 범위, enum, ItemStack, 문자열 목록 및 ItemStack 목록을 지원하며 타입이 맞지 않거나 필수 항목이 없으면 예외를 발생시킵니다. 이 API는 특정 플러그인의 어노테이션이나 데이터 모델에 의존하지 않습니다.

## 테스트

`gradlew.bat test`는 Paper 스케줄러와 Brigadier를 모킹한 API 테스트를 실행합니다. `gradlew.bat integrationProbeJar`는 별도 테스트 플러그인을 빌드합니다. 이를 격리된 Paper 또는 Folia 서버에 Kolor와 함께 설치한 뒤 `kolor-native-check 7` 및 `kolor-compat-check ping`을 실행하면 명령/스케줄러 실행 로그를 확인할 수 있습니다.

통합 프로브는 실제 플레이어 표시 동작이나 영역 간 retired 동작을 검증하지 않습니다. 해당 항목은 접속 클라이언트가 있는 별도 서버 테스트가 필요합니다.
