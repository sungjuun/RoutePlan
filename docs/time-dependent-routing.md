# 시간대별 경로 최적화와 PostGIS 영속 캐시 (V25)

## 처리 흐름

Google Route Provider의 경로 조회는 다음 순서로 처리합니다.

```text
요청 단위 Route Matrix
→ Redis L1 MGET
→ L1 miss만 PostGIS L2 배치 조회
→ L2 hit로 Redis 재가열
→ 최종 miss만 Google Routes API
→ PostGIS UPSERT + Redis pipeline SET
```

도보 경로는 출발 시각과 무관한 키를 사용합니다. 자동차와 대중교통은 좌표·방향·이동수단과 함께 기본 15분 출발 버킷을 키에 포함합니다. TTL은 도보 7일, 자동차 15분, 대중교통 5분이며 `.env`에서 조정할 수 있습니다.

PostGIS 마이그레이션 V18은 `postgis` 확장, 장소의 생성형 `GEOGRAPHY(POINT,4326)` 컬럼, GiST 인덱스, `route_leg_cache`, 만료 가능한 갱신 잠금 테이블을 만듭니다. Docker Compose는 PostgreSQL 16과 호환되는 `postgis/postgis:16-3.5` 이미지를 사용합니다. 관리형 PostgreSQL에서는 배포 DB 사용자가 `CREATE EXTENSION postgis`를 실행할 권한이 있는지 먼저 확인해야 합니다.

## 시간 의존 전역 탐색

`ROUTEPLAN_TIME_DEPENDENT_GLOBAL_ENABLED=true`이고 Google Routes를 사용하며 이동수단이 자동차 또는 대중교통일 때만 실행합니다.

1. 여행일별 하루 시작~종료 범위를 기본 한 시간 버킷으로 나눕니다.
2. 모든 시간 버킷의 방향별 Matrix를 먼저 조회합니다. 자동차 요청은 `TRAFFIC_AWARE`와 미래 `departureTime`을 사용합니다.
3. 외부 호출이 끝난 뒤 날짜 배치와 방문 순서를 하나의 bounded beam search에서 함께 평가합니다.
4. 영업·선호시간, 체류시간, 숙소 복귀, Must Visit, 날씨 점수, 예산을 모든 상태에서 검사합니다.
5. 외부 호출·탐색 상한 또는 공급자 오류가 발생하면 기존 정적 일정과 출발시각별 최종 검증으로 되돌아갑니다.

전역이라는 표현은 날짜와 순서를 함께 탐색한다는 의미입니다. 후보가 많을 때 무제한 완전탐색을 하는 것은 아니며 beam 폭과 상태 수가 제한되므로 수학적 최적해를 항상 보장하지 않습니다.

## 안전 기본값

```dotenv
ROUTEPLAN_ROUTE_DB_CACHE_ENABLED=true
ROUTEPLAN_ROUTE_CACHE_DEPARTURE_BUCKET=15m
ROUTEPLAN_ROUTE_CACHE_DATABASE_BATCH_SIZE=500
ROUTEPLAN_ROUTE_CACHE_CLEANUP_INTERVAL=10m
ROUTEPLAN_ROUTE_CACHE_CLEANUP_BATCH_SIZE=5000

# 추가 Google Matrix 사용량이 발생하므로 저장소 기본값은 false입니다.
ROUTEPLAN_TIME_DEPENDENT_GLOBAL_ENABLED=false
ROUTEPLAN_TIME_DEPENDENT_BUCKET=1h
ROUTEPLAN_TIME_DEPENDENT_MAX_CANDIDATES=8
ROUTEPLAN_TIME_DEPENDENT_MAX_DAYS=3
ROUTEPLAN_TIME_DEPENDENT_MAX_MATRIX_BUILDS=36
ROUTEPLAN_TIME_DEPENDENT_MAX_MATRIX_ELEMENTS=2500
ROUTEPLAN_TIME_DEPENDENT_BEAM_WIDTH=512
ROUTEPLAN_TIME_DEPENDENT_MAX_STATES=250000
ROUTEPLAN_TIME_DEPENDENT_MAX_SEARCH_DURATION=5s
```

예상 추가 요소 수는 대략 `시간 버킷 수 × 위치 수²`입니다. 앱의 `GOOGLE_MONTHLY_MATRIX_LIMIT`과 Google Cloud의 quota·예산 알림을 이 상한보다 먼저 검토하세요. 한도 초과로 fallback하더라도 이미 성공한 Matrix 요소는 사용량에 포함되며, 일정 응답의 Provider 호출·요소·캐시 수치에도 합산됩니다.

운영 지표는 계층별 캐시 read/write, 만료 정리, 시간 의존 탐색 결과·상태 수·버킷 수·소요시간을 제공합니다. 캐시 장애와 반복 fallback은 Prometheus 경고 대상으로 포함합니다.

## 배포 전 확인

```sql
SELECT PostGIS_Version();
SELECT COUNT(*) FROM route_leg_cache WHERE expires_at > CURRENT_TIMESTAMP;
```

기존 `postgres:16-alpine` 볼륨은 PostgreSQL major version이 같으므로 Compose 이미지 교체 뒤 그대로 연결할 수 있습니다. 그래도 운영에서는 먼저 Custom Format 백업과 체크섬을 만든 뒤 마이그레이션하고, Flyway V18 성공과 readiness를 확인하세요.
