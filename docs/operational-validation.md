# V25 운영 부하·장애·비용 검증

이 문서는 실제 스테이징 또는 운영급 RoutePlan 서버에서 다중 Backend, Redis/PostGIS cache fail-open, 시간 의존 탐색 상한, Google 사용량·비용을 반복 검증하는 절차입니다. 일정 최적화 버전을 실제로 생성하고 Google Routes 사용량이 발생할 수 있으므로 전용 계정·전용 Trip과 명시적인 호출 상한을 사용합니다.

## 1. 배포 전 준비

`.env.production`에 배포 환경과 Backend 수를 설정합니다.

```dotenv
ROUTEPLAN_DEPLOYMENT_ENVIRONMENT=staging
ROUTEPLAN_BACKEND_REPLICAS=2

ROUTEPLAN_ROUTE_PROVIDER=GOOGLE
ROUTEPLAN_ROUTE_CACHE_ENABLED=true
ROUTEPLAN_ROUTE_DB_CACHE_ENABLED=true
ROUTEPLAN_TIME_DEPENDENT_GLOBAL_ENABLED=true

# 실제 계약 또는 현재 Google Cloud 가격 기준을 직접 입력합니다.
GOOGLE_ROUTES_USD_PER_THOUSAND=<실제 양수 단가>
GOOGLE_MONTHLY_BUDGET_USD=<승인된 양수 예산>
ROUTEPLAN_USAGE_METRICS_REFRESH_INTERVAL=1m
```

Google Routing을 운영에서 활성화하면 환경 검사기는 `GOOGLE_ROUTES_USD_PER_THOUSAND`와 `GOOGLE_MONTHLY_BUDGET_USD`가 0보다 큰지 확인합니다. 저장소가 임의의 단가를 넣지 않는 이유는 지역·SKU·계약 할인에 따라 실제 가격이 달라질 수 있기 때문입니다.

배포 스크립트는 `ROUTEPLAN_BACKEND_REPLICAS`만큼 Backend를 기동하고 모든 컨테이너가 healthy인지 검사합니다. Nginx는 Docker DNS를 10초마다 다시 해석해 Backend replica에 요청을 분산하고, Prometheus는 DNS service discovery로 각 Backend의 `9090`을 별도 target으로 수집합니다.

## 2. 전용 시나리오 데이터

실제 사용자 Trip은 사용하지 않습니다. 최소 Backend replica 수만큼 별도 사용자와 Trip을 준비합니다.

- 모든 Trip은 같은 미래 날짜, 시간대, 숙소·장소 좌표, 이동수단을 사용합니다.
- 사용자와 Trip ID는 서로 달라야 합니다.
- 자동차 2~4개 장소로 시작하고 모든 장소의 영업시간·체류시간을 동일하게 맞춥니다.
- 계정은 운영 부하 검증 전용이며 종료 후 일정 버전을 정리할 수 있어야 합니다.

[`tests/operations/scenarios.example.json`](../tests/operations/scenarios.example.json)을 서버의 `tests/operations/scenarios.json`으로 복사하고 실제 전용 계정만 입력합니다. 이 파일과 결과 디렉터리는 Git에서 제외됩니다.

```json
[
  { "email": "load-1@your-domain", "password": "...", "tripId": 1001 },
  { "email": "load-2@your-domain", "password": "...", "tripId": 1002 }
]
```

```bash
chmod 600 tests/operations/scenarios.json
```

## 3. 부하·장애 훈련

먼저 dry-run으로 환경·파일·Provider·cache·확인 문구를 검사합니다. 아래 상한은 각 단계에서 허용할 Google Routes 요소 수와 HTTP 호출 수입니다. 앱 월 한도와 Google Cloud quota보다 충분히 작게 설정하세요.

```bash
export ROUTEPLAN_OPERATION_MAX_GOOGLE_UNITS_DELTA=500
export ROUTEPLAN_OPERATION_MAX_GOOGLE_CALLS_DELTA=20
export ROUTEPLAN_FAULT_DRILL_CONFIRM=RUN_ROUTEPLAN_STAGING_FAULT_DRILL

bash ./scripts/run-v25-operational-validation.sh
```

승인된 스테이징 점검 시간에만 실제 실행합니다.

```bash
bash ./scripts/run-v25-operational-validation.sh --execute
```

스크립트는 다음 순서로 실행되고 어느 단계에서 실패해도 trap으로 cache table과 Redis를 복구합니다.

1. 설정된 수의 Backend가 모두 healthy인지 확인하고 baseline 동시 최적화를 실행합니다.
2. Redis 컨테이너를 실제로 중지하고 PostGIS L2로 계속 성공하는지 확인합니다.
3. Redis를 재기동하고 `PONG`/health를 기다립니다.
4. V25 Route Cache key만 제거하고 `route_leg_cache`를 일시적으로 다른 이름으로 바꿔 PostGIS L2 오류를 실제로 발생시킵니다.
5. Google과 사용 가능한 Redis로 일정이 계속 생성되는지 확인하고 즉시 table 이름을 복구합니다.
6. 복구 후 다시 최적화하고 Prometheus 장기 보고서를 생성합니다.

전체 PostgreSQL을 중지하지 않는 이유는 Trip·인증·세션·일정도 같은 PostgreSQL을 사용하므로 서버 자체가 동작할 수 없기 때문입니다. 대신 PostGIS Route Cache relation만 중단해 검증하려는 fail-open 경계를 정확하게 고립합니다.

운영 환경은 기본적으로 거부됩니다. 실제 운영에서 수행하려면 별도의 변경 승인과 maintenance window를 확보한 뒤 두 조건을 모두 명시해야 합니다.

```bash
export ROUTEPLAN_FAULT_DRILL_CONFIRM=RUN_ROUTEPLAN_PRODUCTION_FAULT_DRILL
bash ./scripts/run-v25-operational-validation.sh --execute --allow-production
```

결과는 `tests/operations/results/<UTC 시각>/`에 저장되며 계정 비밀번호나 Provider key는 기록하지 않습니다. 각 부하 결과에는 성공률, 처리량, p50/p95/p99, 응답을 처리한 Backend instance, cache hit/miss/failure, Google Routes 요소·호출·추정 비용 증가량이 포함됩니다.

## 4. 개별 부하 실행

장애 주입 없이 부하만 다시 실행할 수도 있습니다.

```bash
export ROUTEPLAN_OPERATION_SCENARIOS_FILE="$PWD/tests/operations/scenarios.json"
export ROUTEPLAN_LOAD_CONFIRM=RUN_ROUTEPLAN_MUTATING_LOAD
export ROUTEPLAN_LOAD_CONCURRENCY=2
export ROUTEPLAN_LOAD_ITERATIONS_PER_USER=5
export ROUTEPLAN_LOAD_MAX_P95_MS=5000
export ROUTEPLAN_LOAD_MAX_FAILURE_PERCENT=0
export ROUTEPLAN_EXPECTED_MIN_INSTANCES=2
export ROUTEPLAN_MAX_GOOGLE_UNITS_DELTA=500
export ROUTEPLAN_MAX_GOOGLE_CALLS_DELTA=20
export ROUTEPLAN_COMPOSE_PROJECT_NAME=routeplan-staging

docker compose -p "$ROUTEPLAN_COMPOSE_PROJECT_NAME" \
  --env-file .env.production --env-file .routeplan-release.env \
  -f compose.production.yaml -f compose.operations.yaml --profile operations \
  run --rm operations-runner
```

`ROUTEPLAN_LOAD_CONFIRM`이 정확하지 않거나, 외부 HTTP URL을 사용하거나, 시나리오 수가 동시 사용자 수보다 적거나, Google 단가가 설정되지 않으면 실행기는 요청 전에 중단됩니다.

## 5. Beam과 안전 상한 판정

최소 1~2주의 정상 트래픽을 수집한 후 다음 보고서를 확인합니다.

```bash
export ROUTEPLAN_REPORT_LOOKBACK=7d
export ROUTEPLAN_COMPOSE_PROJECT_NAME=routeplan-staging
docker compose -p "$ROUTEPLAN_COMPOSE_PROJECT_NAME" \
  --env-file .env.production --env-file .routeplan-release.env \
  -f compose.production.yaml -f compose.operations.yaml --profile operations \
  run --rm operations-report
```

보고서는 현재 Beam·상태·시간 상한과 적용된 탐색의 p95 상태 수·p95 시간, fallback 비율·`search_limit` 발생 수를 함께 보여줍니다.

- p95 상태 또는 시간이 상한의 80% 이상이면 Beam을 먼저 올리지 않습니다. 장소 수·시간 버킷·Matrix 비용을 분석하고 canary에서 품질 이득을 증명해야 합니다.
- `search_limit`이 없고 p95가 상한의 50% 미만이며 fallback이 1% 이하이면 Beam 128과 현재 상한을 유지합니다.
- 한 번의 합성 부하 결과만으로 운영 기본값을 자동 변경하지 않습니다.
- Beam 256 canary는 같은 입력 Snapshot의 점수·제외 장소·이동시간이 Beam 128보다 유의하게 개선되는 경우에만 검토합니다.

## 6. 장기 Google 사용량·비용

Backend는 PostgreSQL의 월 누적 사용량을 1분마다 다음 Prometheus gauge로 게시합니다. 여러 Backend가 같은 값을 게시하므로 PromQL에서는 `sum`이 아니라 replica 간 `max`를 먼저 사용합니다.

- `routeplan_external_usage_units`: 월별 과금 단위
- `routeplan_external_usage_calls`: 시도·성공·실패 HTTP 호출
- `routeplan_external_usage_cost_usd`: 설정 단가 기반 월 추정 비용
- `routeplan_external_usage_ratio`: 앱 월 안전 한도 사용률
- `routeplan_external_budget_usd`: 설정한 월 예산

Grafana **RoutePlan 운영 현황**에는 정상 Backend 수, 시간 의존 p95 상태·시간·fallback, Google Routes 요소·호출, Google 추정 비용·예산 패널이 추가됩니다. Backend replica 부족, 탐색 상한 80% 접근, Google 안전 한도 80%, 비용 예산 80%에 Alertmanager 경고가 발생합니다.

RoutePlan의 비용은 설정 단가 기반 추정치입니다. 할인·무료 크레딧·SKU 변경·세금이 반영된 최종 금액은 Google Cloud Billing이 기준이므로 Billing Budget과 quota 알림을 별도로 유지해야 합니다.

## 7. 자동 회귀 검증 범위

`npm run test:e2e:v25`는 실제 Google 과금 없이 Backend 두 개를 동시에 띄우고 동적 Nginx upstream을 통해 두 사용자의 최적화를 보냅니다. `X-RoutePlan-Instance` 응답으로 두 컨테이너가 각각 요청을 처리했는지, 공유 Redis 잠금으로 동일 Matrix 호출이 중복되지 않는지, Redis L1과 PostGIS L2 재가열이 유지되는지 확인합니다. Redis 컨테이너를 실제 중지한 PostGIS fallback과 `route_leg_cache` table을 실제 격리한 Google·Redis fallback 및 복구도 전용 DB에서 수행합니다. 2026-09-02 로컬 실행에서는 서로 다른 두 인스턴스, 동시 테스트 Google stub 4회·36요소, 4개 V25 브라우저 시나리오가 모두 통과했습니다.

이 자동 검증은 다중 인스턴스 애플리케이션 경로의 회귀 방지용입니다. 실제 서버 CPU·Network·Google quota·계약 비용과 컨테이너 중단 복구는 승인된 스테이징에서 3절 스크립트를 별도로 실행해야 확정됩니다.

## 8. 수동 복구

스크립트가 강제 종료된 뒤 상태가 남았다면 다음 순서로 복구합니다.

```bash
export ROUTEPLAN_COMPOSE_PROJECT_NAME=routeplan-staging
docker compose -p "$ROUTEPLAN_COMPOSE_PROJECT_NAME" \
  --env-file .env.production --env-file .routeplan-release.env \
  -f compose.production.yaml start redis

docker compose -p "$ROUTEPLAN_COMPOSE_PROJECT_NAME" \
  --env-file .env.production --env-file .routeplan-release.env \
  -f compose.production.yaml exec -T postgres sh -c \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "ALTER TABLE IF EXISTS route_leg_cache_fault_drill RENAME TO route_leg_cache;"'
```

이후 모든 Backend·Redis·PostgreSQL health, `routeplan:backend_instances:healthy`, PostGIS cache failure 경고 복구, 신규 일정 생성을 차례대로 확인합니다.
