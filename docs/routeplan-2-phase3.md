# RoutePlan 2.0 Phase 3

## 구현 범위

Phase 3는 하나의 여행을 여러 사용자가 함께 편집하고, 장소 선호를 일정 계산에 반영하며, 공동 지출부터 최종 송금안까지 같은 작업공간에서 관리하는 흐름을 완성한다.

- 여행 동행자와 `OWNER`·`EDITOR`·`VIEWER` 권한
- 장소별 찬성·반대 투표와 다음 최적화 우선순위 반영
- 결제자·분담자를 지정하는 공동 지출과 균등 분할
- 사용자별 결제·부담·순잔액과 최소 송금안
- 현재 위치·다음 장소·남는 시간·영업시간·관심 카테고리 기반 주변 추천

## 동행자와 권한

새 여행을 만들면 생성자가 `OWNER` 멤버로 함께 저장된다. 기존 여행은 Flyway V21이 소유자 멤버를 자동 보강한다. 현재 초대는 가입된 이메일을 조회해 즉시 동행자로 추가하며, 미가입 사용자에게 메일을 보내는 대기 초대는 범위에 포함하지 않는다.

| 작업 | OWNER | EDITOR | VIEWER |
|---|---:|---:|---:|
| 여행·일정·예산 조회 | O | O | O |
| 장소·일정·설정·예산 수정 | O | O | X |
| 공동 지출 등록 | O | O | X |
| 장소 투표 | O | O | O |
| 동행자 추가·권한 변경·내보내기 | O | X | X |

소유자 역할은 변경하거나 제거할 수 없다. 여행당 멤버는 최대 20명이며, 정산 기록의 결제자 또는 분담자로 참조된 멤버는 관련 지출을 정리하기 전까지 내보낼 수 없다.

회원 탈퇴 시 삭제된 사용자 때문에 잔액 합계가 깨지지 않도록 그 사용자가 결제·작성·분담한 공동 지출 전체를 함께 삭제한다. 소유한 여행은 기존 계정 삭제 정책대로 Cascade 삭제된다.

## 투표와 일정 우선순위

투표는 기존 `TripPlace.priority`를 삭제하지 않고 다음 최적화 때 사용할 유효 우선순위를 계산한다.

```text
MUST 지정              → 100점
전원 찬성              → 90점 / HIGH
3분의 2 이상 찬성      → 60점 / NORMAL
한 명 이상 찬성        → 30점 / LOW
찬성 없이 투표만 존재  → 10점 / LOW
투표 없음              → 기존 설정 점수 유지
```

투표 추가·변경·취소 시 여행은 `DRAFT`로 전환된다. 초기 최적화와 여행 중 재최적화는 같은 계산 규칙을 사용하며, `MUST`는 항상 투표보다 우선한다.

## 공동 지출과 송금안

공동 지출은 기존 `trip_expenses` 장부를 확장해 별도 중복 장부를 만들지 않는다. 결제자와 작성자를 기록하고, 각 분담자의 닉네임과 몫은 Snapshot으로 저장한다. 금액을 인원수로 나눈 나머지는 사용자 ID 순으로 1 최소 통화 단위씩 배분하므로 모든 분담액의 합은 원금과 정확히 같다.

```text
사용자 순잔액 = 대신 결제한 금액 - 본인이 부담할 금액
양수 = 받을 금액
음수 = 보낼 금액
```

정산되지 않은 사용자가 12명 이하이면 모든 상계 조합을 탐색해 송금 횟수가 가장 적은 안을 선택한다. 12명을 초과하면 응답시간을 제한하기 위해 금액이 큰 채무자와 채권자부터 상계하는 빠른 방식으로 전환하고 `exactMinimum=false`로 표시한다.

## 빈 시간 주변 추천

브라우저 위치 권한으로 현재 좌표를 채우거나 직접 입력할 수 있다. 서버는 여행에 이미 포함된 장소를 제외하고 다음 조건을 적용한다.

1. PostGIS `places.location` GiST 인덱스로 현재 위치 반경 5km 후보를 최대 250개 조회
2. 현재 위치 → 후보 → 다음 장소의 직선거리와 도보 시간을 추정
3. 이동시간과 평균 체류시간이 남는 시간 안에 들어오는지 확인
4. 등록된 영업시간이 있으면 예상 방문 구간에 영업 중인지 확인
5. 사용자 관심 카테고리와 일치하면 가중치 부여
6. 우회거리와 소요시간이 작은 후보부터 최대 10개 반환

현재 장소 엔티티에는 공급자 평점이 영속화되지 않으므로 평점은 점수에 포함하지 않는다. 영업시간이 없는 장소는 배제하지 않고 `영업시간 미확인`으로 명시하며, 실제 방문 전에 사용자가 확인하도록 한다. 이동시간은 Google 호출 없이 동작하는 직선거리 기반 추정치다.

## API

```http
GET    /api/v1/trips/{tripId}/collaboration
POST   /api/v1/trips/{tripId}/members
PATCH  /api/v1/trips/{tripId}/members/{memberId}
DELETE /api/v1/trips/{tripId}/members/{memberId}
PUT    /api/v1/trips/{tripId}/places/{placeId}/vote
DELETE /api/v1/trips/{tripId}/places/{placeId}/vote

GET    /api/v1/trips/{tripId}/settlement
POST   /api/v1/trips/{tripId}/settlement/expenses
DELETE /api/v1/trips/{tripId}/settlement/expenses/{expenseId}

GET    /api/v1/trips/{tripId}/nearby-recommendations
```

`GET /api/v1/trips`는 소유한 여행뿐 아니라 초대받은 여행도 반환하고 `ownerId`, `accessRole`을 함께 제공한다.

## 데이터 마이그레이션

`V21__trip_collaboration.sql`은 다음을 추가한다.

- `trip_members`: 여행 멤버와 권한
- `trip_place_votes`: 여행 장소별 사용자 투표
- `trip_expenses.payer_user_id`, `created_by_user_id`, `payer_nickname_snapshot`
- `trip_expense_participants`: 공동 지출 분담자와 금액 Snapshot

기존 여행과 지출은 각각 여행 소유자를 `OWNER`, 단독 결제자·분담자로 보강하므로 데이터 삭제 없이 새 정산 화면에서 이어서 사용할 수 있다.

## 검증

- Backend 통합 테스트: 소유자·편집자·조회자 권한, 3/3·2/3·1/3 투표 점수와 최적화 반영, 균등 분할과 송금안, 영업 중 주변 추천
- Frontend 단위 테스트: 소유자 동행자 관리, 조회자 편집 제어 차단, 모든 역할의 투표, 실패 시 공동 지출 입력 보존
- Java 21 `javac` 기준 main·test 소스 전체 컴파일
- Vitest 53개, ESLint, TypeScript/Vite production build

PostgreSQL Testcontainers 통합 테스트는 Docker Engine이 실행되는 환경에서 `./gradlew test`로 최종 실행한다.
