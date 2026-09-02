# V24 운영 모니터링과 경고

V24는 V23 운영 Compose에 Prometheus, Alertmanager, Grafana를 추가합니다. Backend의 애플리케이션 포트 `8080`과 관리 포트 `9090`을 분리하고, 관리 포트와 Prometheus·Alertmanager는 호스트에 게시하지 않습니다. 로그인 화면이 있는 Grafana만 Caddy를 통해 `https://<ROUTEPLAN_DOMAIN>/monitoring/`에 노출됩니다.

```text
Browser ─ HTTPS ─ Caddy ─ /monitoring/ ─ Grafana
                         /            ─ Frontend ─ Backend:8080

Backend:9090 ─ monitoring ─ Prometheus ─ Alertmanager ─ SMTP
                                 │
                                 └─ Grafana datasource
```

구성 파일은 저장소에서 버전 관리합니다.

- `deploy/monitoring/prometheus.yml`: 수집 대상, 15초 수집·평가 주기, Alertmanager 연결
- `deploy/monitoring/alerts.yml`: HTTP·다중 Backend·V25 탐색·Google 사용량 기록 규칙과 운영 경고
- `deploy/monitoring/grafana/provisioning`: Prometheus datasource와 Dashboard Provider
- `deploy/monitoring/grafana/dashboards/routeplan-overview.json`: RoutePlan 운영 현황 대시보드
- `scripts/prepare-monitoring-config.sh`: SMTP와 Grafana 비밀 파일 및 Alertmanager 런타임 설정 생성

Prometheus 시계열은 30일 또는 5GB 중 먼저 도달하는 한도로 보존합니다. PostgreSQL 월별 Provider 집계는 기존처럼 영속 보존되며 Prometheus 보존 정책과 별개입니다.

## 1. 운영 환경 값

`.env.production`에 다음 값을 실제 운영 값으로 설정합니다.

```dotenv
GRAFANA_ADMIN_USER=routeplan-admin
GRAFANA_ADMIN_PASSWORD=충분히-긴-무작위-비밀번호
ROUTEPLAN_ALERT_EMAIL_TO=운영경고를받을주소@example.com
```

Alertmanager는 기존 `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, `SMTP_SSL`, `SMTP_FROM`을 재사용합니다. 운영 환경 검사는 STARTTLS 또는 SSL 중 하나가 켜져 있는지, Grafana 비밀번호가 20자 이상인지, 경고 수신 주소가 단일 유효 이메일인지 확인합니다.

배포 스크립트는 환경 검사 뒤 `prepare-monitoring-config.sh`를 자동 실행합니다. Compose를 수동으로 실행할 때는 먼저 다음 순서를 실행해야 합니다.

```bash
bash ./scripts/validate-production-env.sh .env.production
bash ./scripts/prepare-monitoring-config.sh .env.production
docker compose --env-file .env.production --env-file .routeplan-release.env \
  -f compose.production.yaml up -d
```

생성물은 Git에서 제외된 `.routeplan-runtime`에 저장됩니다. 디렉터리는 소유자만 접근할 수 있고 Alertmanager 설정은 SMTP 비밀번호의 파일 경로만 포함합니다. 실제 SMTP 비밀번호와 Grafana 관리자 비밀번호는 각각 별도 파일로 마운트됩니다. 이 디렉터리와 `.env.production`을 백업·전송할 때는 애플리케이션 비밀로 취급해야 합니다.

## 2. 대시보드

배포 후 다음 주소로 접속합니다.

```text
https://<ROUTEPLAN_DOMAIN>/monitoring/
```

`.env.production`의 Grafana 관리자 계정으로 로그인하면 **RoutePlan → RoutePlan 운영 현황** 대시보드에서 다음 항목을 볼 수 있습니다.

- Backend 수집 상태와 현재 활성 경고
- 전체 HTTP 요청률, 5xx 비율, p50/p95 응답시간
- 일정 생성·재최적화 성공/실패
- Google·OpenAI 실제 시도 결과와 Provider Circuit 상태
- Route Cache 전체 및 Redis/PostGIS 계층별 hit/miss/failure
- 시간대별 전역 최적화 적용·skip·fallback 결과
- 정상 Backend replica 수와 시간 의존 탐색 p95 상태 수·p95 시간·fallback 비율
- Google Routes 월 요소·HTTP 호출과 설정 단가 기반 월 추정 비용·예산
- JVM Heap과 HikariCP 연결 Pool 사용률

익명 접근, 회원가입, 사용 통계 전송, 플러그인 설치·자동 갱신은 비활성화됩니다. Dashboard와 datasource는 파일 Provisioning으로 관리되므로 운영 UI에서 직접 바꾼 내용은 재배포 가능한 원본이 아닙니다. 변경은 저장소 JSON/YAML에 반영해야 합니다.

## 3. 경고 기준

| 경고 | 기준 | 대기 | 등급 |
|---|---|---:|---|
| Backend Down | Prometheus scrape 실패 | 2분 | Critical |
| Backend replica 부족 | 정상 Backend 2개 미만 | 2분 | Critical |
| HTTP 5xx 증가 | 요청이 있는 동안 5xx 비율 5% 초과 | 10분 | Critical |
| p95 지연 | 요청이 있는 동안 p95 2초 초과 | 10분 | Warning |
| 일정 생성 실패 | 10분 동안 3회 이상 | 즉시 | Warning |
| 외부 Circuit Open | Google/OpenAI Circuit 값 2 | 1분 | Warning |
| 외부 API 실패율 | 공급자별 실패 비율 25% 초과 | 10분 | Warning |
| JVM Heap 높음 | 최대 Heap의 85% 초과 | 15분 | Warning |
| DB Pool 포화 | HikariCP 연결의 90% 초과 | 10분 | Warning |
| PostGIS Route Cache 실패 | 10분 동안 read/write 실패 합계 3회 이상 | 즉시 | Warning |
| 시간 의존 최적화 fallback | 15분 동안 fallback 3회 이상 | 즉시 | Warning |
| 시간 의존 탐색 상한 접근 | p95 상태 수 또는 시간이 상한의 80% 초과 | 30분 | Warning |
| Google 월 사용량 | 앱 안전 한도 80% 이상 | 15분 | Warning |
| Google 월 추정 비용 | 설정 예산 80% 이상 | 15분 | Warning |
| Alertmanager Down | Alertmanager scrape 실패 | 2분 | Critical |

Alertmanager는 `alertname`, `severity`, `provider`로 경고를 묶습니다. Critical은 30분, Warning은 4시간 간격으로 반복하며 복구 메일도 보냅니다. Backend Down이 발생한 동안에는 같은 서비스의 Warning을 억제해 중복 알림을 줄입니다.

임계값은 초기 운영 기준입니다. 실제 트래픽의 정상 범위를 최소 1~2주 관찰한 뒤 `deploy/monitoring/alerts.yml`에서 조정하고 `promtool check rules`와 CI를 통과시켜 배포해야 합니다. 다중 인스턴스 부하·cache 장애 훈련과 Beam 판정 절차는 [V25 운영 검증 안내](operational-validation.md)를 따릅니다.

## 4. 상태 점검과 문제 해결

```bash
docker compose --env-file .env.production --env-file .routeplan-release.env \
  -f compose.production.yaml ps

docker compose --env-file .env.production --env-file .routeplan-release.env \
  -f compose.production.yaml logs --tail 120 prometheus alertmanager grafana

curl --fail https://<ROUTEPLAN_DOMAIN>/monitoring/api/health
```

설정 파일 자체는 다음 명령으로 다시 검사할 수 있습니다.

```bash
bash ./scripts/check-production-config.sh
```

검사기는 Prometheus 설정·규칙, Alertmanager UTF-8 Strict 설정, Grafana Provisioning/Dashboard 문법, 운영·검증 Compose, Caddy·Nginx 설정과 운영 Node 실행기 문법을 확인합니다. 배포 스크립트는 애플리케이션 공개 상태를 먼저 검증한 다음 Alertmanager·Prometheus·Grafana와 공개 Grafana health를 검증합니다. 모니터링만 실패한 경우 정상 애플리케이션 이미지를 되돌리지 않고 운영자 조치가 필요하다고 종료합니다.

## 5. 보존·복구·보안 경계

- Prometheus 데이터와 Alertmanager Silence, Grafana 내부 DB는 Docker Volume에 남습니다. V23 PostgreSQL 백업에는 포함되지 않습니다.
- Dashboard와 datasource는 Git 원본에서 복구할 수 있습니다. Prometheus 시계열은 파생 운영 데이터이므로 현재 자동 원격 백업 대상이 아닙니다.
- Grafana 관리자 비밀번호 환경 값은 새 Grafana DB의 최초 관리자 생성에 사용됩니다. 이미 생성된 Volume에서 비밀번호를 바꾸려면 Grafana 관리자 비밀번호 재설정 절차도 함께 수행해야 합니다.
- Prometheus·Alertmanager UI와 Backend `9090`은 공개하지 않습니다. Grafana 세션 Cookie는 HTTPS Secure·SameSite Strict이고 Caddy가 운영 TLS를 종료합니다.
- SMTP 발신 도메인의 SPF·DKIM·DMARC와 실제 수신 여부는 운영 계정에서 별도 검증해야 합니다.

## 현재 제한

- 단일 Prometheus·Alertmanager이므로 서버 전체가 중단되면 자체 경고 메일도 보낼 수 없습니다. 외부 Uptime 감시가 `/api/v1/auth/me`를 별도로 확인해야 합니다.
- Node Exporter와 디스크/호스트 CPU 경고, 장기 Remote Write, 중앙 로그, 분산 Trace는 아직 연결하지 않았습니다.
- 실제 운영 SMTP 전달, 스팸 분류, 경고 피로도는 로컬 합성 환경에서 재현할 수 없으므로 첫 배포 후 반드시 시험 경고와 복구 알림을 확인해야 합니다.

## 구현 검증 기록

2026-09-02 현재 Prometheus `3.14.0`, Alertmanager `0.34.0`, Grafana `13.2.0` 구성, DNS 기반 다중 Backend 수집, V25 탐색·Google 월 사용량 기록/경고 규칙을 `promtool`로 재검증했습니다. Grafana datasource·19개 패널 Dashboard, 동적 Nginx upstream, 운영/검증 Compose와 Node 실행기 문법도 함께 확인했습니다. Backend 두 개를 띄운 V25 E2E는 서로 다른 인스턴스 처리, Redis 실제 중지와 PostGIS cache table 실제 격리 후 fallback·복구를 검증했습니다. SMTP 실제 발송과 외부 운영 서버 장애 주입은 실제 인프라 승인·계정이 필요한 별도 실행 단계입니다.
