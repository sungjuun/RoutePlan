# 실제 데이터·개인화·여행 장부 (V17–V18)

## 화면에서 사용하기

1. 일정 화면의 **날씨·현지 시간대 → 날씨 자동 조회**: 숙소 좌표의 예보와 IANA 시간대를 불러옵니다. 예보 범위 밖의 날짜는 자동으로 채우지 않습니다. 직접 입력한 예보는 보존합니다. 변경 뒤 새 일정 버전을 계산하세요.
2. Google에서 가져온 장소의 카드를 열고 **Google 영업시간 가져오기**: 정규 주간 영업시간을 확인합니다. 일정 계산·재최적화에서도 자동 조회하며 수동 영업시간이 우선합니다. 원본 Google 응답은 DB에 저장하지 않습니다.
3. 일정 지도에서 **실제 도로 경로**: 선택한 날짜의 미완료 방문 구간과 숙소 복귀선을 Google 지도에 표시합니다. 기본 방문 순서 선과 구분됩니다. 조회 시점 경로이며 기존 일정 시각을 다시 계산하지는 않습니다.
4. **날짜별·항목별 예산과 지출**: 날짜 전체, 항목 전체, 날짜+항목 한도를 설정하고 실제 지출을 기록·수정·삭제합니다. 같은 구간을 저장하면 한도를 바꿉니다. 예산 통화를 바꾼 뒤에는 장부를 새로고침하세요.
5. **마이페이지 → 나의 여행 취향**: 관심사, 지역, 여행 속도, 이동수단을 저장합니다. 메인 페이지에 맞춤 루트와 추천 근거가 나타납니다. 전부 해제해 저장하면 취향을 지울 수 있습니다.
6. 공개 루트 상세의 **댓글과 후기**: 로그인 후 댓글 작성·수정·삭제, 1~5점 후기 저장, 루트/댓글/후기 신고가 가능합니다. 자신이 공개한 루트의 후기는 금지합니다. 후기는 실제 방문 인증을 거치지 않은 이용자 의견입니다.
7. 지정된 운영자만 마이페이지의 **신고 관리**에서 대상 내용을 확인하고 숨김/기각할 수 있습니다. 숨긴 내용은 삭제되지 않으며 신고 처리자와 처리 시각을 남깁니다.

## 키와 실행 설정

루트의 `.env.advanced.example`은 추가 설정 참고용입니다. 기존 `.env`에 필요한 항목만 합치세요. 같은 변수는 한 번만 정의해야 합니다. 실제 키는 `.env`에만 입력하고 예제 파일이나 Git 저장소에는 포함하지 마세요.

```dotenv
ROUTEPLAN_PLACE_PROVIDER=GOOGLE
ROUTEPLAN_ROUTE_PROVIDER=GOOGLE
ROUTEPLAN_ROUTE_DB_CACHE_ENABLED=true
# 추가 Matrix 비용을 확인한 뒤에만 활성화
ROUTEPLAN_TIME_DEPENDENT_GLOBAL_ENABLED=false
GOOGLE_MAPS_API_KEY=서버용_키
GOOGLE_MAPS_BROWSER_KEY=별도의_브라우저용_키
ROUTEPLAN_MODERATOR_EMAILS=운영자계정이메일
```

- 서버 키: Places API (New)와 Routes API 활성화 및 해당 API 제한. 안정적인 서버 공인 IP가 있으면 IP 제한도 설정합니다. 브라우저에는 이 키를 반환하지 않습니다.
- 브라우저 키: Maps JavaScript API 전용, 웹사이트 제한 `http://localhost:3100/*`. `127.0.0.1`로 접속한다면 그 주소도 별도 추가해야 합니다. 배포할 때는 운영 HTTPS 도메인만 허용합니다.
- 빈 브라우저 키일 때 프론트는 유료 Geometry 요청을 보내지 않고 설정 안내를 표시합니다. 키는 브라우저에 공개되는 식별자이므로 API·웹사이트 제한이 중요합니다.
- 미설정 상태에서는 기존 SIMPLE 계산, 직접 입력 날씨, 방문 순서 지도와 장부/커뮤니티 기능을 계속 사용할 수 있습니다. Google 실패를 가짜 실측 경로로 대체하지 않습니다.
- 시간대는 기본 `Asia/Seoul`입니다. 외국 여행은 날씨 자동 조회 또는 수동 시간대 저장 후 계산하세요. 복사한 루트는 원본 Itinerary의 시간대를 이어받습니다. 숙소를 다른 시간대 지역으로 바꾸었다면 다시 확인해야 합니다.

설정 후 RoutePlan 폴더에서 실행합니다. 기존 DB 볼륨은 유지합니다.

```powershell
docker compose up -d --build
```

기본 주소: [RoutePlan](http://localhost:3100), backend `localhost:8180`.

2026-08-28 검증 환경에서는 서버·브라우저 키를 분리하고 `GOOGLE` Route Provider를 적용한 뒤 실제 API 응답과 Google 한국어 지도 표시를 확인했습니다. 저장소 기본값이 모든 환경에서 Google 호출을 활성화하는 것은 아니므로 새 환경에서는 위 설정을 별도로 적용해야 합니다.

## 날씨와 시간 계산의 범위

- Open-Meteo `/v1/forecast`: 숙소 좌표, `timezone=auto`, 최대 16일, 일별 WMO 날씨 코드와 최대 강수확률. 동일 좌표의 성공 응답은 메모리에서 15분, 최대 256건 재사용합니다. DB에는 여행 기간에 해당하는 요약 예보와 `MANUAL`/`OPEN_METEO` 출처를 기록합니다.
- 수동 예보를 자동 값으로 바꾸려면 수동 날씨 설정에서 해당 예보를 삭제/초기화한 뒤 자동 조회하세요. V18부터 여행별로 3시간 주기 자동 갱신을 켤 수 있습니다. 기본은 꺼짐이며 서버가 실행 중일 때 동작합니다. 직접 입력한 예보·시간대와 저장된 일정은 보존합니다. [자동 갱신·정확도 상세](schedule-accuracy-profile.md).
- 무료 Open-Meteo endpoint는 **비상업 용도만** 허용합니다. 상용 배포는 적절한 서비스 계약/자가 호스팅 및 호출 정책 검토가 필요합니다. 무료 서비스 공식 한도는 분당 600, 시간당 5,000, 일당 10,000 미만이며 현재 앱은 날씨 쿼터를 DB에서 집계하지 않습니다. [Open-Meteo 문서](https://open-meteo.com/en/docs), [이용 조건](https://open-meteo.com/en/terms).
- Google Transit Matrix는 각 여행일의 **현지 하루 출발시각**을 UTC RFC3339로 바꿔 날짜별 요청합니다. 재최적화 첫날은 현재시각을 사용합니다. 자동차·대중교통 캐시는 기본 15분 출발 버킷을 포함하며 DST에 존재하지 않거나 두 번 발생하는 현지 시각은 거부합니다.
- 선택적 시간 의존 전역 탐색은 시간 버킷별 Matrix를 미리 만든 뒤 날짜와 방문 순서를 함께 재탐색합니다. 후보·날짜·Matrix 요소·탐색 상태 상한을 넘으면 기존 순서를 각 방문 종료 시각의 대중교통으로 검증하는 보수적 fallback을 사용합니다. 지도 Geometry에도 각 구간의 계획 출발시각을 전달합니다. 설정·비용 계산은 [V25 안내](time-dependent-routing.md)를 확인하세요.
- Google 대중교통은 현재 시점 기준 과거 7일~미래 100일 조회만 지원합니다. 전체 기간 날짜를 확인하세요. Google의 지역·교통수단 지원 여부에 따라 경로가 없을 수 있습니다. [대중교통 경로 문서](https://developers.google.com/maps/documentation/routes/transit-route).
- Google Details는 `id,regularOpeningHours,currentOpeningHours,utcOffsetMinutes`를 요청합니다. 분할/야간 영업을 날짜별 구간으로 나누며, 현지 오늘~6일 뒤의 제공된 특별 영업시간·휴무를 우선합니다. 그 밖은 정규 시간과 휴일 확인 경고를 사용합니다. 정보가 없는 장소는 영업 제약 없이 계산하므로 실제 방문 가능을 보장하지 않습니다. 두 openingHours 필드는 동일한 [Details Enterprise SKU](https://developers.google.com/maps/documentation/places/web-service/place-details)에 속합니다.
- 수동 영업시간은 기존 Place opening-hours API로 입력합니다. 프론트의 선호 방문시간은 영업시간 자체와 다른 제약입니다. Google 원본 정규 영업시간·Polyline은 저장/캐시하지 않습니다. 계산 결과에 영업시간 적용 경고만 남깁니다.

## API 호출·비용 안전장치

| 작업 | 사용 필드/SKU | 앱 기본 월 한도 | 집계 단위 |
|---|---|---:|---|
| 장소 검색 | Text Search Pro | 4,000 | 요청 |
| 영업시간 | Place Details Enterprise | 900 | 요청 |
| 거리/시간 행렬 | Compute Route Matrix Essentials (현재 옵션) | 9,000 | origin × destination 요소 |
| 지도 경로 선 | Compute Routes Essentials (현재 옵션) | 9,000 | 구간 요청 |
| 브라우저 지도 | Dynamic Maps | Google Cloud에서 별도 설정 | 지도 로드 |

`GOOGLE_MONTHLY_SEARCH_LIMIT`, `GOOGLE_MONTHLY_DETAILS_LIMIT`, `GOOGLE_MONTHLY_MATRIX_LIMIT`, `GOOGLE_MONTHLY_GEOMETRY_LIMIT`로 조정합니다. `0`은 해당 호출 차단입니다. PostgreSQL의 UTC 월별 원자적 예약으로 여러 요청이 동시에 들어와도 앱 한도를 넘겨 전송하지 않습니다. 재시도마다 다시 집계하고 실패 요청도 보수적으로 차감합니다. V21부터 성공/실패·평균/최대 지연시간과 OpenAI 요청·토큰도 영속 집계합니다. 조회 경로: 일정 → 실제 여행 데이터 → API 품질·비용 대시보드. 이 수치는 **앱 시도량이지 청구서가 아닙니다**. 한도·단가·검증 절차는 [외부 API 운영 안내](provider-operations.md)를 확인하세요.

Google의 무료 사용량은 SKU별로 다르고 청구 계정 내 다른 앱의 사용량도 합쳐질 수 있습니다. 문서 확인일(2026-08-28)의 Text Search Pro 5,000회, Details Enterprise 1,000회, 위 Routes Essentials 각각 10,000단위 무료 구간을 참고해 보수적인 기본값을 정했지만 무료를 보장하지 않습니다. 현재 Field Mask/옵션 변경 시 SKU도 다시 확인해야 합니다. 가격은 지역·계약·시점에 따라 달라질 수 있습니다. [Google 공식 요금표](https://developers.google.com/maps/billing-and-pricing/pricing), [Place Details 필드와 SKU](https://developers.google.com/maps/documentation/places/web-service/place-details).

- 50개 장소+숙소의 51×51 행렬은 날짜당 최대 2,601요소입니다. 대중교통 다일 계산은 날짜만큼 증가할 수 있어 월 기본 한도에 일찍 도달할 수 있습니다. 요청 한도 때문에 중간에 계산이 실패하더라도 이미 시도한 외부 호출은 복구되지 않습니다.
- Geometry는 방문 N곳이면 최대 N+1회 요청합니다. 지도 날짜 변경/다시 조회는 추가 요청입니다. 프론트는 로딩 중 재시도 버튼을 비활성화합니다.
- Cloud Console에서 API별 분당/일 한도, 브라우저 지도 한도, 예산 알림을 함께 설정하세요. 예산 알림은 하드 과금 차단이 아닙니다. 실제 청구액/남은 무료량은 Cloud Billing에서 확인해야 합니다.
- 진단 스크립트, 다른 프로그램, 브라우저 지도, 기존 사용량은 앱 DB 집계에 포함되지 않습니다. DB 볼륨 초기화는 집계를 지우므로 한도를 과금 보증으로 해석하지 마세요.
- Google Routes geometry는 정책에 따라 **Google 지도에만** 표시합니다. 기본 Leaflet 방문순서 선은 실제 도로가 아닙니다. 기존 Google 검색 Place의 장기 보관·비Google 지도 표시, 선택적 Redis 캐시 등 기존 기능도 상용 공개 전에 계약에 맞게 별도 검토해야 합니다. Redis 기본값은 꺼짐입니다. [Routes 정책](https://developers.google.com/maps/documentation/routes/policies).

## 예산·지출과 추천의 계산 규칙

- 금액은 정수 최소 단위로 저장합니다. KRW/JPY 1, USD/EUR 0.01 단위입니다. 범위는 0~1,000,000,000,000 최소 단위, 소수 최소 단위·음수·여행 밖 날짜는 거부합니다.
- 예상 비용과 실제 지출은 분리합니다. 날짜/항목 한도는 **지출 관리용**이며 일정 선택에는 기존 여행 전체 예상 예산만 적용합니다. 날짜 한도와 항목 한도가 같은 지출에 겹쳐도 합산하지 않고 각각 비교합니다.
- 지출은 UUID 요청 ID로 멱등 생성합니다. 같은 요청 ID+같은 내용의 재전송은 중복되지 않고, 내용이 다르면 충돌합니다. 다른 여행 ID로 지출을 수정/삭제할 수 없습니다.
- 장부에 기록이 있으면 통화를 바꿀 수 없습니다. 환율 변환·카드 연동·영수증 OCR·인원별 정산은 없습니다. 여행 기간을 바꿀 때 밖으로 밀려나는 날짜 예산/지출도 보호합니다.
- 추천은 최근 공개 루트 최대 200개 중 본인/UNLISTED/운영자 숨김 루트를 제외합니다. 관심 지역 +40, 속도 +15, 이동수단 +15, 관심 장소 유형별 +20점입니다. 동점은 좋아요→복사→ID 순으로 정렬해 최대 6개를 제공합니다. 맞는 정보가 없으면 인기 기준임을 밝힙니다. 행동 추적·학습 모델이나 외부 AI 호출은 사용하지 않습니다.
- 댓글·후기·신고 작성/수정은 계정당 고정 1시간 구간에 30회로 제한합니다. 삭제해도 사용량을 복구하지 않습니다. 인터넷 공개 전에는 회원가입/로그인/네트워크별 남용 방어와 운영자 이메일 소유 검증이 추가로 필요합니다. 운영자 권한은 설정 allowlist만으로 부여되므로 운영자 이메일 계정은 먼저 안전하게 확보해야 합니다.

## 추가 API

모든 경로는 `/api/v1` 기준. GET discussion만 비회원에게 공개되며 나머지는 로그인 필요. Trip/Itinerary는 소유자만 접근, 변경 요청은 CSRF 필요.

| API | 용도 |
|---|---|
| `GET/PUT /trips/{id}/time-zone` | 시간대 조회/수동 저장 |
| `POST /trips/{id}/weather/refresh` | 자동 예보+시간대 갱신 |
| `POST /trips/{id}/places/{placeId}/opening-hours/refresh` | Google 영업시간 미리보기 |
| `GET /integrations/usage` | UTC 월별 앱 사용량 |
| `GET /integrations/maps-config` | 브라우저 전용 키만 반환 |
| `POST /itineraries/{id}/road-geometry?date=YYYY-MM-DD` | 해당 날짜 실제 경로 |
| `GET /trips/{id}/spending` | 전체 장부와 구간별 집계 |
| `PUT /trips/{id}/spending/allocations` | 통화+날짜/항목 한도 목록 교체 |
| `POST /trips/{id}/spending/expenses` | 통화+UUID 포함 지출 생성 |
| `PUT/DELETE /trips/{id}/spending/expenses/{expenseId}` | 지출 수정/삭제 |
| `GET/PUT /me/preferences`, `GET /me/recommendations` | 내 취향/추천 |
| `GET /routes/{id}/discussion?page=0` | 댓글/후기 각 20개 및 전체 집계 |
| `POST /routes/{id}/comments`, `PUT/DELETE /routes/{id}/comments/{commentId}` | 댓글 |
| `PUT /routes/{id}/review`, `DELETE /routes/{id}/reviews/{reviewId}` | 내 후기 |
| `POST /routes/{id}/reports` | 유형/대상/사유/설명 신고 |
| `GET /moderation/access`, `GET /moderation/reports` | 권한/미처리 신고 100건 |
| `POST /moderation/reports/{reportId}/resolve` | HIDE/DISMISS, 성공 204 |

스키마: Flyway V12 실제 데이터/시간대/호출량, V13 장부/취향/댓글/후기/신고, V14 프로필 사진/날씨 정기 갱신. 애플리케이션 버전과 DB migration 번호는 별개입니다. 오래된 일정에 지도용 이동수단/숙소 Snapshot이 없다면 새 버전을 계산한 뒤 실제 경로를 조회해야 합니다.

## 검증

자동 테스트는 기존 테스트에 더해 비용·통화·날짜·CSRF·소유권, 자동 예보 보존, 후기 upsert, 숨김과 신고 권한, 추천 제외 규칙, 월별 한도 동시성, Google 헤더·영업시간 파싱·날짜별 Transit 요청, WMO/DST, UI 오류·지출 중복 방지를 다룹니다. 경로 출처는 서버의 `GOOGLE_ROUTES`와 `STRAIGHT_LINE_ESTIMATE`를 구분하며 두 표시 분기를 회귀 테스트로 확인합니다. E2E는 실제 서버/DB와 브라우저를 연결하지만 유료 외부 Provider/키는 강제로 비활성화합니다.

실제 API 점검 결과는 [실호출 점검 기록](live-validation-2026-08-28.md)에 있습니다. 공개 명소로 제한된 실호출을 수행했고 로컬 브라우저에서 날짜·시간대 기반 대중교통 계산, 한국어 지도·경로선 표시, 출처 문구 수정을 확인했습니다. 원본 응답과 키는 기록하지 않았습니다. 실제 청구액·무료 잔량·한도 소진과 운영 도메인의 키 제한은 별도 검증 대상입니다.

PowerShell 7에서 아래 명령은 Open-Meteo 1회만 호출합니다. `-IncludeGoogle`은 최대 5회의 추가 Google 요청이 발생하므로 **수동 점검용**이며 CI에서 실행하지 않습니다.

```powershell
.\scripts\verify-live-integrations.ps1
.\scripts\verify-live-integrations.ps1 -IncludeGoogle
.\scripts\verify-live-integrations.ps1 -IncludeOpenAI
```

`-IncludeOpenAI`는 `.env`의 키와 모델로 Responses API 최소 요청 1회를 실행합니다. 생성 텍스트·응답 ID·키는 출력하지 않고 상태와 토큰 수만 표시합니다. Google과 OpenAI 옵션을 함께 사용하면 두 공급자를 한 번에 점검합니다.

이 스크립트는 Compose에서 서버 키를 메모리로만 읽고 키/프로젝트 ID/원본 응답을 출력하지 않습니다. 이 직접 진단 호출은 앱 월별 집계 밖에 있습니다.
