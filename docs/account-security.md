# V19 회원·보안 보강

## 사용자 흐름

| 기능 | 사용 위치 | 동작 |
| --- | --- | --- |
| 이메일 인증 | 회원가입 후 메일 / 마이페이지 → 계정 보안 | 링크를 열고 **이메일 인증하기**를 눌러 완료. 24시간 유효 |
| 인증 메일 재발송 | 마이페이지 → 인증 메일 보내기 | 1분에 1번. 다른 탭에서 인증한 경우 **인증 상태 새로고침** |
| 비밀번호 찾기 | 로그인 → 비밀번호를 잊으셨나요? | 이메일 입력 → 30분 유효 링크 → 새 비밀번호 2회 입력 → 로그인 |
| 비밀번호 변경 | 마이페이지 → 계정 보안 → 비밀번호 변경 | 현재 비밀번호 확인 후 새 비밀번호 저장, 모든 기기 로그아웃 |
| 로그인 유지 | 별도 조작 없음 | PostgreSQL 세션을 사용해 백엔드 재시작 후에도 기존 쿠키로 계속 이용 |

기존 회원·여행·프로필 사진은 그대로 유지합니다. 기존 회원을 임의로 인증 완료 처리하지 않으며, 마이페이지에서 본인이 인증해야 합니다. **인증 전에도 기존 여행 기능을 이용할 수 있습니다.** 이메일 인증은 소유 여부 확인이며, 현재 공개/여행 기능의 필수 접근 조건은 아닙니다.

새 비밀번호는 10자 이상, UTF-8 기준 72바이트 이하입니다. 공백을 임의 제거하지 않으며 현재와 같은 비밀번호로 변경할 수 없습니다. 로그인 시도·메일 요청 한도를 초과하면 잠시 기다려야 합니다.

## 지금 로컬에서 확인하기

프로젝트: `C:\Users\DE-2212-003\Documents\ChatGPT\RoutePlan`

```powershell
docker compose up -d --build --wait backend frontend mailpit
```

- 앱: [localhost:3100](http://localhost:3100)
- 로컬 개발 메일함: [localhost:8026](http://localhost:8026)
- Compose 기본값은 `LOCAL`입니다. **실제 Gmail·네이버 등의 수신함으로 발송하지 않습니다.** 가입·인증·재설정 메일은 위 개발 메일함에서 확인합니다.
- 메일은 약 5초 주기의 작업자가 처리합니다. SMTP 오류 시 1·2·4·8분 뒤 재시도하여 총 5번까지 시도합니다.
- Mailpit SMTP 포트는 호스트에 노출하지 않고 UI만 `127.0.0.1:8026`에 바인딩합니다. 메일은 최대 500개·24시간 보관하며 컨테이너 재생성 시 사라집니다. 인증 링크가 들어 있는 메일함을 외부에 공개하지 마세요.
- V19 최초 반영 때 기존 메모리 세션은 옮길 수 없으므로 한 번 다시 로그인해야 합니다. 그 이후의 백엔드 재시작은 로그인을 유지합니다.
- 기본 세션은 **12시간 미활동** 시 만료됩니다. 로그아웃, 비밀번호 변경/재설정, 쿠키 삭제 또는 DB 볼륨 삭제 시 유지되지 않습니다. 브라우저를 닫아도 유지하는 ‘자동 로그인’ 기능과는 다릅니다.

## 실제 메일 발송 설정

기존 `.env`의 지도 API 키 등을 덮어쓰지 말고 [.env.auth.example](../.env.auth.example)의 필요한 항목만 추가하세요. `SMTP` 모드는 발송 상태 안내를 구분하기 위한 값이며, 실제 연결은 `SMTP_*` 값으로 설정합니다.

1. 사용할 메일 서비스에서 SMTP 서버 주소·포트·인증 수단(앱 비밀번호/SMTP 자격증명)을 발급받습니다. 일반 로그인 비밀번호나 임의 서버 주소를 쓰지 마세요.
2. 발신 이메일/도메인을 제공자에서 승인받습니다. 운영 도메인의 SPF·DKIM·DMARC는 해당 제공자 안내대로 설정합니다.
3. `.env`에 `ROUTEPLAN_AUTH_MAIL_MODE=SMTP`, 외부에서 접근 가능한 HTTPS 원점 `ROUTEPLAN_PUBLIC_URL`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_FROM`을 설정합니다.
4. 587/STARTTLS 구성은 `SMTP_AUTH=true`, `SMTP_STARTTLS=true`, `SMTP_SSL=false`를 사용합니다. 465/implicit TLS는 제공자 지시에 따라 `SMTP_SSL=true`, `SMTP_STARTTLS=false`로 설정하세요. 실제 SMTP 인증을 평문 연결로 보내지 마세요.
5. HTTPS 서비스는 `ROUTEPLAN_SESSION_COOKIE_SECURE=true`로 설정하고 위 Compose 명령으로 재생성합니다. HTTP localhost에서 이 옵션을 켜면 쿠키가 전달되지 않을 수 있습니다.
6. 본인의 테스트 계정으로 인증·재설정·변경 알림 수신, 발신 도메인, 링크 도메인, 스팸 분류를 확인합니다. **실제 외부 SMTP 수신/도메인 설정 검증은 별도입니다.**

`ROUTEPLAN_PUBLIC_URL`은 요청 Host 헤더에서 만들지 않습니다. 임의 경로·쿼리·fragment·사용자정보가 붙은 URL을 거부하며, HTTP는 localhost/loopback에서만 허용합니다. 프론트 포트를 바꾸면 이 값도 같이 바꾸세요. SMTP 자격증명·메일 본문·링크·쿠키는 커밋하거나 로그로 공유하지 마세요.

Docker 없이 직접 백엔드를 실행하는 기본값은 `DISABLED`입니다. 이 경우 계정/로그인/비밀번호 변경은 가능하지만 메일 발송 요청은 `503 AUTH_MAIL_DISABLED`로 안내합니다. 별도 SMTP를 연결하거나 Mailpit을 연결한 뒤 `LOCAL`/`SMTP` 모드를 설정하세요. `spring.mail.host`의 직접 실행 기본값은 `localhost:1025`이며 Compose의 내부 `mailpit` 이름은 호스트에서 접근할 수 없습니다.

## API와 보안 경계

| 메서드·경로 (`/api/v1` 기준) | 권한 | 결과 |
| --- | --- | --- |
| `GET /auth/options` | 공개 | `mailMode`: LOCAL / SMTP / DISABLED |
| `GET /auth/me` | 공개 | 사용자에 `emailVerified` 포함 |
| `POST /auth/email/verification-request` | 로그인 | 본인 이메일에만 발송 요청, 204 |
| `POST /auth/email/verify` | 공개 + CSRF | `{ token }`, 성공 204 |
| `POST /auth/password/reset-request` | 공개 + CSRF | `{ email }`, 일반화된 안내 202 |
| `POST /auth/password/reset` | 공개 + CSRF | `{ token, newPassword }`, 성공 204 |
| `POST /auth/password/change` | 로그인 + CSRF | `{ currentPassword, newPassword }`, 성공 204 |

모든 상태 변경 API는 CSRF를 유지합니다. `/auth/csrf`에서 받은 헤더/토큰과 세션 쿠키를 함께 전달해야 합니다. 로그인 후 세션 ID·CSRF 토큰을 회전하며 비밀번호 변경 후 클라이언트의 CSRF 캐시도 초기화합니다.

- 인증/재설정 링크는 256비트 난수이며 DB에는 SHA-256 해시만 저장합니다. 원문은 발송 순간에 생성하므로 메일 작업 테이블에도 원문 링크를 저장하지 않습니다.
- 링크는 URL fragment에 있어 HTTP 요청/Referer로 전달되지 않습니다. 앱이 읽은 뒤 주소창에서도 제거하며 메모리에서만 유지합니다. 따라서 처리 전 새로고침하면 메일 링크를 다시 열어야 합니다.
- GET으로 링크를 열기만 해서는 계정을 변경하지 않습니다. 메일 미리보기/스캐너로 링크가 소비되는 것을 방지합니다.
- 계정 행 잠금 후 만료·용도·보안 버전·사용 여부를 다시 확인해 동시 재사용을 차단합니다. 재발송만으로 기존 정상 링크가 취소되지 않지만, 하나를 사용하면 같은 목적의 다른 링크도 무효화됩니다.
- 비밀번호 변경/재설정은 해당 계정의 모든 세션과 토큰을 폐기하고 보안 버전을 올립니다. 동시 로그인/진행 중 요청이 오래된 세션을 다시 저장하더라도 다음 요청에서 버전 검사로 거부합니다.
- 세션에는 평문 비밀번호나 비밀번호 해시를 직렬화하지 않습니다. DB 세션 데이터 역시 인증 자격이므로 운영 DB·백업 접근을 제한하고 암호화하세요.
- 재설정 요청은 존재/미존재 계정과 이메일별 한도 초과에 같은 응답을 반환합니다. SMTP를 비동기 큐로 분리해 메일 발송 시간으로 가입 여부가 드러나지 않도록 합니다. 모든 네트워크/DB 지연이 완전히 동일함을 보장하는 것은 아닙니다.
- 이전부터 있던 회원가입의 이메일/닉네임 중복 응답은 그대로입니다. 전체 서비스의 계정 열거 방지가 완성된 것은 아닙니다.

### 요청 제한

| 대상 | 제한 |
| --- | --- |
| 로그인 이메일 | 15분에 10회 (성공·실패 포함, 대소문자 정규화) |
| 로그인 클라이언트 주소 | 15분에 100회 |
| 회원가입 클라이언트 주소 | 1시간에 20회 |
| 재설정 요청 이메일 | 15분에 3회 (초과해도 동일 202, 추가 발송 없음) |
| 재설정 요청 클라이언트 주소 | 15분에 20회 |
| 링크 사용 클라이언트 주소 | 15분에 30회 |
| 인증 재발송 계정 | 1분에 1회 |
| 로그인 상태의 비밀번호 변경 계정 | 15분에 5회 |

PostgreSQL 원자적 upsert로 여러 프로세스/재시작/동시 요청에도 횟수를 공유합니다. 키에는 원문 이메일/IP 대신 해시를 저장합니다. IP 등 추측 가능한 값의 단순 해시는 익명화를 보장하지 않으므로 운영 데이터로 보호하세요. 일반 한도 초과는 `429 AUTH_RATE_LIMITED` + `Retry-After`이며, UI에 대기 시간을 안내합니다.

기본값은 전달 헤더를 **신뢰하지 않습니다**. Nginx 뒤에서는 주소별 제한이 프록시의 공용 예산처럼 적용될 수 있습니다. 운영에서는 백엔드 직접 접근을 차단하고, `ROUTEPLAN_AUTH_TRUSTED_PROXIES`에 제어하는 프록시의 정확한 IP/CIDR만 지정하세요. 그 프록시가 `X-Real-IP`를 항상 덮어써야 합니다. `0.0.0.0/0`로 모든 주소를 허용하지 마세요. 분산 공격 방어·CAPTCHA·MFA는 별도 확장 항목입니다.

## 저장 구조·운영

Flyway **V15**에 `users.email_verified_at`, `users.security_version`, `spring_session`/`spring_session_attributes`, `auth_tokens`, `auth_mail_jobs`, `auth_rate_limits`를 추가합니다. 기능 버전 V19와 DB 마이그레이션 번호 V15는 다릅니다. 기존 마이그레이션과 사용자 데이터를 다시 작성하지 않습니다.

메일 작업자는 `FOR UPDATE SKIP LOCKED`로 작업을 선점하고 제한된 SMTP 타임아웃(연결/읽기/쓰기 각각 5초)을 적용합니다. SMTP 예외의 본문/주소는 기록하지 않고 작업 ID만 경고합니다. 발송과 DB 커밋 사이 장애가 생기면 중복 메일 또는 사용 불가 링크가 도착할 수 있으므로 새 메일 요청을 안내합니다. 정확히 한 번 전송을 보장하지 않습니다.

작업자는 만료 토큰·한도 기록·하루가 지난 메일 작업을 매시간 정리합니다. Spring Session은 만료 세션을 자체 정리합니다. 발송 실패 작업은 최대 5회 이후 멈추며 24시간 내 운영 확인이 필요합니다. PII 없이 상태를 확인할 수 있는 예시:

```sql
SELECT purpose, attempts, count(*) FROM auth_mail_jobs GROUP BY purpose, attempts;
SELECT count(*) FROM auth_mail_jobs WHERE attempts >= 5;
SELECT count(*) FROM spring_session WHERE expiry_time > extract(epoch FROM now()) * 1000;
```

SMTP 장애는 기존 여행 이용/로그인/비밀번호 변경을 막지 않도록 readiness 조건에서 제외했습니다. 따라서 health가 정상이라고 발송 성공을 의미하지 않습니다. 운영 시 실패 작업 알림과 발송 제공자 모니터링을 별도로 연결해야 합니다. 운영 배포에서는 개발 Mailpit 서비스/메일함을 제거하고 실제 SMTP만 사용하세요.

## 재현 검증

```powershell
# Backend: Java 21과 실행 중인 Docker 필요
.\gradlew.bat test
cd frontend
npm test
npm run lint
npm run build
npm run test:e2e:docker
```

E2E는 전용 `routeplan-e2e` 프로젝트와 메일함 8027을 사용하며 외부 메일·유료 지도 API 설정을 재사용하지 않습니다. 테스트 계정의 메일만 읽고, **해당 테스트 프로젝트의 backend만 재시작**해 로그인/저장 여행 유지를 확인합니다. 개발 프로젝트는 재시작하거나 삭제하지 않습니다. `npm run test:e2e`로 기존 환경만 검사할 때는 이 격리 메일/재시작 검증을 건너뜁니다.

검증 대상: 해시·쿠키 직렬화, 인증/재설정 만료·잘못된 용도·중복·동시 사용, 기존 비밀번호 확인, 모든 세션 폐기·오래된 인증 버전 거부, CSRF, 계정/IP 한도·동시 증가·재시작 지속성, SMTP 실패 재시도, 신뢰하지 않은 프록시 헤더, 모바일 화면, 실제 로컬 SMTP 수신과 서버 재시작.

2026-08-28 검증 결과: Backend **128개**, Frontend **47개**, 격리 브라우저 E2E **10개** 통과. ESLint·프로덕션 빌드 통과, 초기 JS 청크 489.22kB. 개발 DB에 Flyway V15 적용 후 기존 사용자 5명·여행 6개·일정 11개가 유지됐으며 로컬 서비스 health가 정상입니다. 이 검증은 실제 외부 SMTP 수신을 포함하지 않습니다.

참고한 공식 문서: [Spring Session JDBC](https://docs.spring.io/spring-session/reference/guides/boot-jdbc.html), [Spring Boot 메일](https://docs.spring.io/spring-boot/reference/io/email.html), [OWASP 비밀번호 재설정](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html), [Mailpit Docker](https://mailpit.axllent.org/docs/install/docker/).
