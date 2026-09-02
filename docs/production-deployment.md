# V23 스테이징·운영 배포 기반

V23은 개발용 `compose.yaml`과 운영 환경을 분리합니다. 운영 구성은 Caddy만 호스트의 80/443 포트를 열고 PostgreSQL, Redis, Backend, Frontend는 격리된 Docker Network에서만 통신합니다. Caddy가 인증서를 자동 발급·갱신하며, 애플리케이션 이미지는 GitHub Container Registry(GHCR)에 커밋 SHA 태그로 보존합니다.

## 운영 구조

```text
Internet
  │ 80/443
  ▼
Caddy ── edge ── Frontend Nginx:8080 ── app ── Backend
                                           ├─ data ── PostgreSQL
                                           ├─ data ── Redis
                                           ├─ outbound ── Google/OpenAI/Open-Meteo
                                           └─ monitoring ── Prometheus ── Alertmanager ── SMTP
                                                        └─ Grafana ── Caddy /monitoring/
```

- DB·Redis·Backend 포트는 호스트에 게시하지 않습니다.
- Caddy가 직접 연결된 클라이언트 주소를 덮어쓴 뒤 Frontend가 Backend로 전달합니다. Backend는 외부에서 접근할 수 없는 `app` 네트워크만 신뢰하므로 임의 `X-Real-IP` 헤더를 신뢰하지 않습니다.
- 운영 프로필은 Swagger와 API 문서를 끄고 오류 상세를 응답에 포함하지 않습니다.
- Backend와 Frontend는 비루트 사용자로 실행하며, 컨테이너 파일 시스템은 읽기 전용이고 필요한 임시 디렉터리만 `tmpfs`로 제공합니다.
- Prometheus·Alertmanager·Backend 관리 포트는 내부 `monitoring` Network에만 두고, 로그인 보호된 Grafana만 `/monitoring/`으로 제공합니다.
- 기본 2개 Backend replica를 동적 Nginx upstream으로 분산하고 Prometheus DNS service discovery로 각각 수집합니다.
- PostgreSQL은 PostGIS 3.5가 포함된 PostgreSQL 16 이미지를 사용하며 DB·Redis는 외부에 게시하지 않습니다.
- 유료 Provider는 키·Quota를 확인하기 전까지 `DISABLED`, `SIMPLE`, `RULE_BASED`가 기본입니다.

## 1. 서버 준비

권장 기준은 Docker Engine과 Compose Plugin을 설치한 단일 Linux 서버입니다.

1. 배포할 도메인의 A/AAAA 레코드를 서버로 연결합니다.
2. 방화벽은 관리용 SSH와 TCP 80/443, UDP 443만 허용합니다. DB·Redis·Backend 포트는 열지 않습니다.
3. 서버에 저장소를 Clone하고 GHCR 이미지를 읽을 수 있도록 `docker login ghcr.io`를 한 번 실행합니다. 비공개 Package라면 `read:packages` 권한의 별도 토큰을 사용합니다.
4. 저장소 루트에서 `.env.production.example`을 `.env.production`으로 복사하고 실제 값으로 바꿉니다.
5. 비밀 파일 권한을 제한합니다.

```bash
chmod 600 .env.production
bash ./scripts/validate-production-env.sh
```

Windows에서 배포 값을 준비할 때는 다음 검사를 사용할 수 있습니다.

```powershell
Copy-Item .env.production.example .env.production
.\scripts\validate-production-env.ps1
```

검사기는 키와 비밀번호를 출력하지 않습니다. `ROUTEPLAN_PUBLIC_URL`은 도메인과 정확히 같은 HTTPS Origin이어야 하고, SMTP·Secure Cookie·프록시 신뢰 범위와 Provider별 필수 키를 확인합니다.

Grafana 관리자와 운영 경고 수신 주소도 설정합니다. 실제 배포 스크립트는 런타임 비밀 파일을 자동 생성하지만 Compose를 직접 실행할 때는 먼저 `bash ./scripts/prepare-monitoring-config.sh .env.production`을 실행해야 합니다. 자세한 경고 기준과 보안 경계는 [V24 운영 모니터링 안내](production-monitoring.md)를 참고하세요.

`ROUTEPLAN_BACKEND_REPLICAS`는 2~20 범위로 설정합니다. 배포 스크립트는 지정된 개수 전체가 healthy가 아니면 배포를 성공으로 처리하지 않습니다. 실제 동시 요청과 cache 장애 복구 검증은 [V25 운영 검증 안내](operational-validation.md)를 참고하세요.

## 2. SMTP·DNS·외부 Provider

- `ROUTEPLAN_AUTH_MAIL_MODE=SMTP`와 실제 SMTP 접속 정보를 입력합니다.
- 발신 도메인에 SPF, DKIM, DMARC를 설정하고 회원가입 인증과 비밀번호 재설정 메일을 실제 수신함에서 확인합니다.
- Google을 켤 때 서버 키는 서버 IP와 Places/Routes API로, 브라우저 키는 운영 HTTPS Referrer와 Maps JavaScript API로 각각 제한합니다.
- OpenAI는 결제 프로젝트와 앱 월별 요청·토큰 한도를 확인한 후에만 `ROUTEPLAN_AI_PROVIDER=OPENAI`로 전환합니다.
- `.env.production`은 Git에 포함되지 않으며 Docker Build Context에서도 제외됩니다.

스테이징 최초 구성은 [스테이징 배포 준비](staging-deployment.md)를 먼저 따릅니다.

## 3. GitHub Actions 배포

GitHub의 `staging`과 `production` Environment를 만들고 각각 보호 규칙과 다음 Secret을 등록합니다. 같은 이름을 사용하되 서버·SSH key·경로는 환경별 값으로 완전히 분리합니다.

| Secret | 설명 |
|---|---|
| `DEPLOY_HOST` | 서버 주소 |
| `DEPLOY_PORT` | SSH 포트, 비우면 22 |
| `DEPLOY_USER` | 권한을 제한한 배포 사용자 |
| `DEPLOY_PATH` | 서버의 RoutePlan Clone 절대 경로 |
| `DEPLOY_SSH_PRIVATE_KEY` | 배포 전용 SSH 개인키 |
| `DEPLOY_KNOWN_HOSTS` | 사전에 검증한 서버 Host Key 한 줄 |

`RoutePlan Deploy` workflow를 `main`에서 수동 실행합니다. 스테이징은 `target_environment=staging`, `confirmation=DEPLOY_STAGING`, 운영은 `target_environment=production`, `confirmation=DEPLOY_PRODUCTION`을 사용합니다. Workflow는 테스트·Lint·Build·운영 구성 검사를 다시 수행하고 Backend/Frontend 이미지를 `ghcr.io/<owner>/<repository>-{backend,frontend}:<commit-sha>`로 게시합니다. 서버는 해당 SHA의 저장소와 이미지만 사용하며, 선택한 GitHub Environment와 서버 환경값이 다르면 배포 전에 중단됩니다.

배포 스크립트는 기존 DB가 있으면 먼저 Custom Format 백업을 만들고, 서비스 health와 공개 HTTPS `/api/v1/auth/me`, `/monitoring/api/health`를 검사합니다. 애플리케이션 검증이 실패하면 이전 이미지로 복구를 시도하지만 DB를 자동으로 되돌리지는 않습니다. 애플리케이션은 정상이고 모니터링만 실패한 경우에는 정상 이미지를 되돌리지 않고 운영자 확인이 필요한 실패로 종료합니다. 새 Flyway Migration은 이전 애플리케이션과 함께 실행 가능한 확장형 변경으로 작성해야 합니다.

V25부터 Flyway V18이 `CREATE EXTENSION postgis`를 실행합니다. Compose의 기존 PostgreSQL 16 볼륨은 같은 major version의 PostGIS 이미지에 연결되지만, 운영 전 백업을 만든 뒤 `SELECT PostGIS_Version()`과 공간 인덱스 생성, readiness를 확인해야 합니다. 외부 관리형 PostgreSQL을 사용한다면 애플리케이션 배포 전에 PostGIS 확장 사용 권한과 제공 버전을 확인하세요.

## 4. 백업과 복구

수동 백업:

```bash
bash ./scripts/backup-production.sh
```

백업은 `backups/postgres`에 UTC 시각의 PostgreSQL Custom Format과 SHA-256 파일을 생성합니다. 기본 보존 기간은 14일이며 `ROUTEPLAN_BACKUP_RETENTION_DAYS`로 조정합니다. 서버 로컬 백업만으로는 장애에 대비할 수 없으므로 암호화한 사본을 별도 스토리지로 전송하고 실제 복구를 정기적으로 연습해야 합니다.

복구는 현재 DB를 교체하는 파괴적 작업입니다. 스크립트가 먼저 현재 상태를 다시 백업하고, Backend·Frontend·Caddy를 중지한 뒤 정확한 확인 문구가 있을 때만 실행합니다.

```bash
bash ./scripts/restore-production.sh backups/postgres/routeplan-YYYYMMDDTHHMMSSZ.dump REPLACE_ROUTEPLAN_DATABASE
```

복구 실패 시 애플리케이션은 중지된 상태를 유지하므로 로그와 안전 백업을 확인한 뒤 수동으로 조치합니다.

## 5. 배포 후 점검

```bash
docker compose -p routeplan-production --env-file .env.production --env-file .routeplan-release.env \
  -f compose.production.yaml ps
curl --fail https://your-domain.example/api/v1/auth/me
curl --fail https://your-domain.example/monitoring/api/health
```

추가로 회원가입→메일 인증→로그인→여행 생성→일정 생성→로그아웃 흐름을 운영 계정으로 확인하고, Grafana `/monitoring/` 로그인·Prometheus target·Alertmanager 시험 메일·Caddy JSON 로그·Provider 대시보드·Cloud Billing 경고가 연결됐는지 점검합니다. 서버와 별개인 외부 상태 점검 서비스도 `/api/v1/auth/me`를 주기적으로 확인하도록 설정하세요.

## 운영 제한

- 현재 배포는 단일 서버 Docker Compose 기준이며 무중단 다중 노드 배포가 아닙니다.
- 자동 이미지 복구는 DB Migration을 되돌리지 않습니다. 데이터 복구는 운영자 확인이 필요한 별도 절차입니다.
- 인증서 발급 전에 DNS와 80/443 인바운드 연결이 완료되어야 합니다.
- 실제 서버, 도메인, SMTP, GHCR 권한과 외부 모니터링 연결은 저장소만으로 자동 생성할 수 없습니다.

## 구현 검증 기록

2026-08-31에 합성 운영 환경으로 PowerShell/Bash 환경 검사, Docker Compose 렌더링, Caddy·Prometheus·Alertmanager·Grafana 설정, Backend/Frontend 운영 이미지 빌드를 확인했습니다. 격리 프로젝트에서 전체 애플리케이션·데이터·모니터링 서비스를 실제 기동했고 Backend는 `routeplan`, Frontend는 `nginx`, Prometheus·Alertmanager는 `nobody`, Grafana는 `472` 사용자로 실행됐습니다. 다섯 서비스 모두 읽기 전용 Root Filesystem과 `healthy` 상태였으며 모든 서비스의 호스트 Port Binding은 비어 있었습니다.

백업 후 검증 테이블 값을 변경하고 Restore를 실행해 백업 시점 값으로 복구되는 것을 확인했으며, 복구 전 안전 백업과 애플리케이션 정지·재기동도 함께 검증했습니다. Backend 147개, Frontend 49개 테스트와 Frontend Lint·프로덕션 빌드, GitHub Actions `actionlint`, Shell 문법 검사가 통과했습니다. V25 E2E에서는 Backend 두 개를 동시에 기동해 서로 다른 인스턴스의 처리와 Google Matrix 중복 호출 방지를 확인했습니다. Windows 호스트의 Gradle loopback 오류 때문에 Backend 테스트는 동일 소스를 Linux JDK 21 컨테이너에서 실행했습니다.
