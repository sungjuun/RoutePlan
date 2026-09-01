# V21–V22 외부 API 품질·비용·장애 격리 운영

V21은 Google Maps Platform과 OpenAI의 실제 HTTP **시도마다** 앱 안전 한도를 먼저 예약하고 결과를 PostgreSQL에 기록합니다. 애플리케이션 재시작이나 여러 Backend 인스턴스에서도 같은 UTC 월 집계를 공유합니다.

V22는 Google과 OpenAI를 서로 독립된 Circuit Breaker·동시 호출 제한으로 격리합니다. 기본값은 재시도를 모두 소진한 일시적 장애가 5회 연속되면 30초 동안 해당 공급자만 차단하고, 이후 단일 시험 호출 성공 시 자동 복구합니다. Redis 경로 캐시는 동일한 미스 집합에 분산 잠금을 사용해 다중 Backend의 중복 Google 조회를 줄입니다. 잠금 또는 Redis 장애 시에는 서비스 가용성을 위해 잠금 없이 진행합니다.

## 집계 범위

| 작업 | 앱 집계 단위 | 추가 품질 정보 |
|---|---|---|
| Google 장소 검색·상세·도로 경로선 | 요청 | 성공/실패, 평균/최대 지연시간 |
| Google Route Matrix | origin × destination 요소 | 성공/실패 요소, 평균/최대 지연시간 |
| OpenAI Responses | 요청 | 성공/실패, 평균/최대 지연시간, 입력/출력 토큰 |

- Retry가 발생하면 각각 별도 시도로 집계합니다. 실패했더라도 공급자가 처리했을 수 있으므로 예약한 사용량은 되돌리지 않습니다.
- V21 이전에 저장된 `units`는 보존하며 `분류 전 호출`로 표시합니다. V21 이후 결과부터 성공·실패로 분류합니다.
- API 키, 요청/응답 본문, 사용자 자연어, 장소명은 운영 집계 테이블에 저장하지 않습니다.
- Open-Meteo, 브라우저의 Dynamic Maps 로드, 다른 애플리케이션과 진단 스크립트 호출은 포함하지 않습니다.

## 설정

저장소의 `.env.operations.example`을 참고해 루트 `.env`에 필요한 값만 추가하고 Backend를 다시 빌드합니다.

```powershell
cd C:\Users\DE-2212-003\Documents\ChatGPT\RoutePlan
docker compose up -d --build backend frontend
```

주요 설정:

- `ROUTEPLAN_USAGE_WARNING_PERCENT`: 정상에서 주의로 바뀌는 사용률, 기본 80
- `OPENAI_MONTHLY_REQUEST_LIMIT`: OpenAI 실제 시도 횟수 한도, 기본 500
- `OPENAI_MONTHLY_TOKEN_LIMIT`: 응답으로 확인된 입력+출력 토큰 한도, 기본 1,000,000
- `GOOGLE_MONTHLY_*_LIMIT`: Google 작업별 앱 한도
- `ROUTEPLAN_CIRCUIT_FAILURE_THRESHOLD`, `ROUTEPLAN_CIRCUIT_OPEN_DURATION`: 회로 차단 조건과 복구 대기
- `ROUTEPLAN_PROVIDER_MAX_CONCURRENT_CALLS`: Google/OpenAI 각각의 동시 호출 제한
- `GOOGLE_MONTHLY_BUDGET_USD`, `OPENAI_MONTHLY_BUDGET_USD`: 0보다 클 때 앱 추정비용 경고 기준
- `ROUTEPLAN_ROUTE_CACHE_REFRESH_*`: Redis/PostgreSQL 분산 갱신 잠금·대기 설정
- `ROUTEPLAN_ROUTE_DB_CACHE_ENABLED`, `ROUTEPLAN_ROUTE_CACHE_DEPARTURE_BUCKET`: PostGIS L2와 시각 버킷
- `ROUTEPLAN_TIME_DEPENDENT_*`: 추가 시간 Matrix 비용·후보·날짜·탐색 상한

OpenAI 토큰은 응답 뒤에 확정되므로 마지막 성공 응답이 토큰 한도를 조금 넘을 수 있습니다. 그 다음 시도부터 차단합니다. 요청 횟수 한도는 전송 전에 원자적으로 차단합니다.

## 비용 추정

가격은 SKU·기능 옵션·모델·지역·계약·시점에 따라 달라질 수 있으므로 RoutePlan에 고정 가격을 넣지 않습니다. `.env.operations.example`의 단가를 실제 계약/공식 가격표 기준으로 입력했을 때만 추정액을 표시합니다.

- Google 값은 앱 집계 단위 **1,000개당 USD**입니다.
- OpenAI 값은 입력 또는 출력 **1,000,000토큰당 USD**입니다.
- 단가가 0이면 `비용 단가 미설정`으로 표시합니다.
- Compute Route Matrix는 요청 수가 아니라 origin과 destination 조합의 요소 수를 단가에 곱합니다.

추정치는 세금, 환율, 무료 구간, 구간 할인, 캐시/다른 프로젝트 사용량을 반영하지 않습니다. 최종 비용은 [Google Cloud Billing](https://console.cloud.google.com/billing)과 [OpenAI Usage](https://platform.openai.com/usage)에서 확인하세요. Google의 최신 SKU 구조는 [공식 가격표](https://developers.google.com/maps/billing-and-pricing/pricing), Routes 요소 계산과 한도는 [Routes 사용량·청구 안내](https://developers.google.com/maps/documentation/routes/usage-and-billing)를 기준으로 다시 확인합니다.

## 확인 절차

1. 로그인 후 **마이페이지 → 외부 API 품질·비용** 또는 여행 일정의 **실제 여행 데이터 → API 품질·비용 대시보드**를 엽니다.
2. 지표 조회 전 값을 기록하고 장소 검색·영업시간·경로 계산 또는 자연어 미리보기 한 가지를 실행합니다.
3. 다시 조회해 시도량과 성공/실패, 지연시간, OpenAI 토큰 증가를 확인합니다.
4. 같은 시간대의 공급자 Usage/Cloud Metrics와 비교합니다. 앱 외 호출 때문에 공급자 값이 더 큰 것은 정상입니다.
5. Google Cloud에서 API별 일/분 쿼터와 예산 알림을 함께 설정합니다. RoutePlan 월 한도는 DB가 삭제되거나 우회 호출이 발생하면 과금을 막지 못합니다.

대시보드는 요청/토큰 사용량, 공급자 회로 상태, 동시 호출 수, 실패율과 설정 예산 초과를 활성 경고로 표시합니다. 공급자 장애 시 Correlation ID와 `routeplan.external.api.*`, `routeplan.external.circuit.*`, `routeplan.external.calls.rejected`, `routeplan.route.cache.refresh.*` Prometheus 지표를 함께 추적하세요.
