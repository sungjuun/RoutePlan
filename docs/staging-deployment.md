# RoutePlan 스테이징 배포 준비

스테이징은 운영과 동일한 `compose.production.yaml`·Spring `production` 프로필을 사용하되 별도 서버, 별도 도메인, 별도 데이터와 GitHub `staging` Environment로 격리합니다. 서버 안의 환경 파일 이름은 배포 스크립트 호환을 위해 `.env.production`을 유지하고, 파일 내부의 `ROUTEPLAN_DEPLOYMENT_ENVIRONMENT=staging`으로 대상을 구분합니다.

## 1. 필요한 실제 정보

다음 값이 준비되기 전에는 외부 서버 배포를 실행하지 않습니다. 비밀번호·개인키·API key를 Git이나 채팅에 올리지 말고 서버의 `.env.production`과 GitHub Environment Secret에만 저장합니다.

- Linux 서버의 공인 주소, SSH 사용자·포트, RoutePlan 절대 경로
- 스테이징 전용 도메인과 DNS A/AAAA 레코드
- 배포 전용 SSH 개인키와 확인한 서버 host key
- GHCR Package 읽기 권한
- 스테이징 전용 SMTP 계정·발신 주소·수신 운영자 주소
- PostgreSQL·Grafana용 난수 비밀번호
- Google 실검증 단계에서 사용할 서버 키·브라우저 키, 계약 단가와 승인 예산

기본 호스트 사전검사는 총 메모리 4GB와 저장소 기준 여유 디스크 10GB를 요구합니다. 더 작은 검증 서버를 의도적으로 사용할 때만 `ROUTEPLAN_MIN_MEMORY_MB`, `ROUTEPLAN_MIN_FREE_DISK_MB`로 기준을 명시적으로 낮춥니다.

## 2. 서버 준비

스테이징과 운영은 80/443 Port, Docker Volume, 장애 훈련 범위를 완전히 분리하기 위해 별도 서버를 권장합니다. 서버에는 Docker Engine, Compose Plugin, Git, curl을 설치하고 배포 사용자에게 해당 저장소와 Docker daemon 접근 권한만 부여합니다.

```bash
git clone https://github.com/sungjuun/RoutePlan.git /srv/routeplan
cd /srv/routeplan
docker login ghcr.io
cp .env.production.example .env.production
chmod 600 .env.production
```

`.env.production`의 최소 핵심값은 다음과 같습니다.

```dotenv
ROUTEPLAN_DEPLOYMENT_ENVIRONMENT=staging
ROUTEPLAN_DOMAIN=staging.your-domain.example
ROUTEPLAN_PUBLIC_URL=https://staging.your-domain.example
ROUTEPLAN_IMAGE_PREFIX=ghcr.io/sungjuun/routeplan
ROUTEPLAN_BACKEND_REPLICAS=2
ROUTEPLAN_SESSION_COOKIE_SECURE=true
ROUTEPLAN_AUTH_MAIL_MODE=SMTP
```

첫 배포는 유료 호출을 분리해 확인할 수 있도록 `ROUTEPLAN_PLACE_PROVIDER=DISABLED`, `ROUTEPLAN_ROUTE_PROVIDER=SIMPLE`, `ROUTEPLAN_AI_PROVIDER=RULE_BASED`, `ROUTEPLAN_TIME_DEPENDENT_GLOBAL_ENABLED=false`로 시작합니다. HTTPS·인증 메일·기본 여행 흐름이 정상인 것을 확인한 다음 Google 설정을 켭니다.

서버에서 다음 사전검사를 실행합니다. 비밀 값은 출력하지 않으며 환경 불일치, 파일 권한, Docker 접근, DNS, 외부 HTTPS, 메모리·디스크, Compose와 모니터링 설정을 검사합니다.

```bash
ROUTEPLAN_EXPECTED_DEPLOYMENT_ENV=staging \
  bash ./scripts/preflight-deployment-host.sh
```

## 3. GitHub `staging` Environment

GitHub 저장소의 **Settings → Environments**에 `staging`을 만들고 가능하면 승인자를 지정합니다. 아래 Secret 이름은 `production` Environment와 같지만 값은 반드시 스테이징 서버 전용이어야 합니다.

| Secret | 값 |
|---|---|
| `DEPLOY_HOST` | 스테이징 서버 주소 |
| `DEPLOY_PORT` | SSH Port, 기본 22 |
| `DEPLOY_USER` | 배포 전용 사용자 |
| `DEPLOY_PATH` | 예: `/srv/routeplan` |
| `DEPLOY_SSH_PRIVATE_KEY` | 배포 전용 개인키 |
| `DEPLOY_KNOWN_HOSTS` | 지문을 별도로 확인한 host key |

`DEPLOY_KNOWN_HOSTS`는 신뢰할 수 있는 경로로 서버 지문을 먼저 확인한 뒤 생성합니다.

```bash
ssh-keyscan -p 22 staging.your-domain.example > routeplan-staging-known-hosts
ssh-keygen -lf routeplan-staging-known-hosts
```

## 4. 최초 배포

GitHub Actions의 **RoutePlan Deploy**를 `main`에서 수동 실행합니다.

- `target_environment`: `staging`
- `confirmation`: `DEPLOY_STAGING`

Workflow는 테스트와 운영 설정 검사를 다시 통과한 SHA 이미지를 GHCR에 게시합니다. 원격 서버에서는 선택한 GitHub Environment와 `.env.production`의 환경값이 정확히 일치하는지 사전검사한 뒤 `routeplan-staging` Compose Project로 배포합니다. 운영을 선택할 때는 확인값 `DEPLOY_PRODUCTION`과 GitHub `production` Environment가 필요합니다.

## 5. 배포 직후 확인

```bash
docker compose -p routeplan-staging --env-file .env.production \
  --env-file .routeplan-release.env -f compose.production.yaml ps

curl --fail https://staging.your-domain.example/api/v1/auth/me
curl --fail https://staging.your-domain.example/monitoring/api/health
```

다음 순서로 확인합니다.

1. Backend 두 개, Frontend, PostgreSQL, Redis, Caddy, Prometheus, Alertmanager, Grafana가 모두 `healthy`인지 확인합니다.
2. 회원가입 메일 수신·인증·로그인·여행 생성·기본 일정 생성을 확인합니다.
3. Grafana에서 `environment=staging`, Backend replica 2개와 5xx·지연시간 지표를 확인합니다.
4. 시험 경고와 복구 메일이 스테이징 수신함에 도착하는지 확인합니다.
5. 기본 흐름이 안정된 뒤 Google 키·예산·Quota를 설정하고 [V25 운영 검증](operational-validation.md)을 수행합니다.

## 현재 외부 준비 상태

저장소에는 스테이징 선택형 GitHub Actions, 환경 불일치 차단, 호스트 사전검사, 환경별 Compose Project와 Prometheus 라벨 분리가 준비되어 있습니다. 실제 최초 배포에는 이 문서 1절의 서버·도메인·SMTP·SSH 값과 GitHub `staging` Environment 등록이 필요합니다.
