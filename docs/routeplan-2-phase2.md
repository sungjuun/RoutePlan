# RoutePlan 2.0 Phase 2

## 구현 범위

Phase 2는 기존 자연어 조건 수정, 예상 비용, 여행 예산, 날짜·항목별 한도와 실제 지출 기능을 유지하면서 다음 빈 구간을 완성한다.

- 일정 장소의 날짜 이동 및 같은 날짜 안 순서 변경
- 저장 전 전체 이동시간·거리 변화 미리보기
- 변경된 날짜만 경로·영업시간·숙소 복귀 조건 재계산
- 더 짧은 날짜별 순서 추천과 추천안 적용
- 장소별 실제 지출 연결과 날짜별 예상/실제 비교
- 교체 가능한 `ExchangeRateProvider`와 원화 참고 환산

## 직접 일정 편집과 부분 재계산

일정은 저장된 Snapshot을 직접 수정하지 않는다. 사용자가 편집한 날짜별 `itineraryItemId` 배열을 보내면 최신 버전과 정확히 대조하고 새 버전을 만든다.

```text
Drag & Drop / 키보드 이동 / DAY 선택
→ 모든 날짜·항목·중복·소유권 검증
→ 변경된 날짜 집합 계산
→ 해당 날짜 Route Matrix만 생성
→ 사용자가 지정한 순서를 시간창 제약으로 평가
→ 기존 전체 합계와 차이 미리보기
→ 저장 시 변경 날짜는 재계산, 나머지 날짜는 Snapshot 복사
→ REOPTIMIZATION 새 버전 저장
```

완료된 방문은 같은 날짜의 연속된 앞부분으로 고정된다. 장소 누락·중복, 여행 기간 밖 날짜, 오래된 원본 버전, 영업시간 또는 하루 종료 조건을 만족하지 못하는 순서는 명시적인 오류로 거부한다.

추천 순서는 `NEAREST_NEIGHBOR_2_OPT`로 같은 날짜 안의 이동 부담을 비교한다. 미리보기의 사용자안과 추천안은 같은 Route Matrix를 재사용해 외부 경로 호출을 중복하지 않는다. 추천을 적용해도 즉시 저장하지 않고 다시 미리보기를 거치므로 사용자가 최종 결과를 결정한다.

### API

```http
POST /api/v1/trips/{tripId}/itineraries/manual-edit/preview
POST /api/v1/trips/{tripId}/itineraries/manual-edit
```

요청 예시:

```json
{
  "sourceItineraryId": 44,
  "assignments": [
    { "visitDate": "2026-09-10", "itineraryItemIds": [102, 101] },
    { "visitDate": "2026-09-11", "itineraryItemIds": [103] }
  ]
}
```

## 예산과 여행 가계부

실제 지출에는 선택적으로 여행 장소를 연결할 수 있다. 최신 일정의 장소별 예상 비용 Snapshot을 날짜별로 집계하고 실제 지출과 비교해 `남은 예상` 또는 `예상 초과`를 보여준다. 날짜·항목별 한도는 별도 비교 기준으로 유지한다.

`V20__expense_place_link.sql`은 기존 지출 데이터를 보존한 채 nullable `place_id`만 추가한다. 여행에 포함되지 않은 장소를 연결하려는 요청은 거부한다.

## 환율 Provider

`ExchangeRateProvider`가 서비스 경계를 제공하며 기본 구현은 Frankfurter v2 단일 통화쌍 API를 사용한다. API 키가 필요 없고, `BigDecimal`로 환율을 읽으며 최신 환율은 기본 6시간 동안 메모리에 캐시한다.

```text
ROUTEPLAN_EXCHANGE_PROVIDER=FRANKFURTER  # 기본값
ROUTEPLAN_EXCHANGE_PROVIDER=DISABLED     # 외부 호출 차단
ROUTEPLAN_EXCHANGE_BASE_URL=https://api.frankfurter.dev
ROUTEPLAN_EXCHANGE_REQUEST_TIMEOUT=5s
ROUTEPLAN_EXCHANGE_CACHE_TTL=6h
```

```http
GET /api/v1/trips/{tripId}/exchange-rate?quote=KRW
```

환율 조회 실패는 일정·예산·지출 저장을 막지 않는다. 화면에는 원래 통화를 유지하고 인라인 안내만 표시한다. 표시 금액은 참고값이며 실제 카드 결제 환율, 환전 스프레드와 수수료를 포함하지 않는다.

## 기존 기능 재사용

- 자연어 → 구조화 제약 → 최적화 엔진 흐름은 기존 `NaturalLanguageConstraintService`를 그대로 사용한다.
- 예상 비용과 총예산 Snapshot은 `TripBudgetService`, `BudgetInput`, `Itinerary.recordBudget`을 사용한다.
- 실제 지출 멱등성은 기존 `(trip_id, request_id)` 제약을 유지한다.
- 경로 계산은 현재 선택된 `RouteMatrixProvider`를 사용하므로 SIMPLE·Google·Redis/PostGIS 캐시 설정이 동일하게 적용된다.
