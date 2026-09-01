# 실제 외부 API 점검 — 2026-08-28

## 현재 결과

로컬 개발 환경에서 날씨·영업시간·도보 경로·날짜 지정 대중교통 응답과 Google 한국어 지도·실제 경로선 표시를 확인했습니다. 초기 `403 SERVICE_DISABLED`는 Routes API 활성화 후 해소됐습니다. 대표 공개 장소의 단일 표본이며 성능·운행 정확도·모든 지역의 경로 제공을 보장하지 않습니다.

### 2026-09-01 V25 교통량·계층 캐시 검증

RoutePlan 백엔드를 통해 미래 날짜의 `TRAFFIC_AWARE` 자동차 Matrix와 시간 의존 전역 최적화를 실제 Google Routes API로 검증했습니다. 검증 여행은 2026-09-08 오사카 1일, 난바 숙소·오사카성·쿠로몬 시장, `Asia/Tokyo`, 09:00~13:00으로 제한했습니다. 임시 계정과 여행은 검증 직후 API로 삭제했고 기존 사용자·여행은 변경하지 않았습니다.

| 실행 | 앱 소요 | Google 호출 | Matrix 요소 | Cache hit/miss | 결과 |
|---|---:|---:|---:|---:|---|
| 최초 계산 | 1,839ms | 5회 | 54 | 6 / 30 | Google Routes, 전역 탐색 적용 |
| 동일 조건 재계산 | 50ms | 0회 | 54 | 36 / 0 | Redis L1 100% 재사용 |
| Redis Route key 삭제 후 재계산 | 95ms | 0회 | 54 | 36 / 0 | PostGIS L2 30건 적중, Redis 30건 재가열 |

최초 계산은 기본 Matrix와 5개 시간 Matrix를 사용했습니다. 첫 시간 버킷은 기본 계산 결과를 재사용해 실제 Google 요청은 5회, 공급자 과금 단위는 `3 origins × 3 destinations × 5 requests = 45 elements`였습니다. 앱 집계는 성공 5회·실패 0회·성공 요소 45개, 공급자 총 지연 1,082ms·최대 447ms였습니다. PostGIS에는 09:00~13:00 JST의 출발 버킷 5개와 방향별 경로 30건이 저장됐습니다.

시간대별 반환값도 동일한 정적 결과가 아니었습니다. 오사카성→쿠로몬 시장은 09시 16분, 10~12시 18분, 13시 17분이었고, 전체 방향 평균은 시간대에 따라 13.17~13.83분으로 달라졌습니다. 일정 응답은 매 실행마다 `예측 교통량을 반영한 5개 시간 Matrix`와 전역 탐색 적용을 표시했으며 Provider·Cache 실패는 0건이었습니다.

Google 공식 분류상 `TRAFFIC_AWARE` Compute Route Matrix는 Pro SKU이고 2026-09-01 공개 단가는 월 5,000요소 무료 사용량 이후 1,000요소당 USD 10입니다. 따라서 이번 45요소의 무료 사용량 적용 전 정가 환산은 **USD 0.45**이며, 해당 결제 계정의 월 무료 사용량이 남아 있다면 증분 청구액은 USD 0일 수 있습니다. 실제 청구액과 무료 잔량은 Google Cloud Billing이 최종 기준입니다. 현재 RoutePlan은 이동수단별 SKU를 하나의 `GOOGLE_ROUTES` 집계로 합치므로 로컬 `GOOGLE_ROUTES_USD_PER_THOUSAND`는 0으로 유지하고 이번 검증의 Pro 비용을 별도로 계산했습니다.

- [Routes API 사용량·과금](https://developers.google.com/maps/documentation/routes/usage-and-billing)
- [Google Maps Platform 가격표](https://developers.google.com/maps/billing-and-pricing/pricing)

### 2026-08-31 V22 재점검

공급자 장애 격리 적용 후 Open-Meteo 1회와 Google 공개 표본 5회를 다시 실행했고 모두 HTTP 200이었습니다. 장소 검색 398ms, 장소 상세 190ms, 도보 Matrix 282ms, 도로 Geometry 228ms, 날짜 지정 서울 대중교통 292ms였습니다. 도보는 `ROUTE_EXISTS`·1,237m·1,036초, 대중교통은 `ROUTE_EXISTS`·1,169초, Geometry는 encoded polyline을 반환했습니다.

RoutePlan 백엔드 경유 검증에서는 임시 계정으로 한국어 장소 검색 1회만 실행했습니다. 결과 1개를 받았고 PostgreSQL `GOOGLE_PLACES` 시도량·성공량이 각각 정확히 1 증가했습니다. Google Circuit Breaker는 `CLOSED`, 활성 운영 경고는 0건이었으며 임시 계정은 API로 삭제한 뒤 DB에 남지 않았습니다. 현재 UTC 월 앱 집계는 Google Places 1/1 성공, Geometry 4/4 성공, Routes 9/9 성공이며 기록된 실패는 없습니다. 재시작 후 Prometheus의 Google·OpenAI 회로 상태도 모두 0(`CLOSED`)이었습니다.

현재 `.env`에는 OpenAI API 키가 없으므로 OpenAI 실호출은 계속 보류했습니다. `scripts/verify-live-integrations.ps1 -IncludeOpenAI`는 키를 설정한 환경에서 응답 본문을 출력하지 않고 Responses API 최소 요청 1회, 상태와 토큰 수만 검사합니다. RoutePlan 요청은 `store=false`를 사용합니다. 실제 비용·잔액은 OpenAI Usage/Billing이 최종 기준입니다.

### 2026-08-31 V21 재점검

V21 품질·비용 계측 구현 후 같은 안전 스크립트로 다시 확인했습니다. Open-Meteo 1회와 Google 최대 5회의 공개 표본만 사용했으며 모두 HTTP 200이었습니다. 장소 검색 413ms, 장소 상세 187ms, 도보 Matrix 322ms, 도로 Geometry 304ms, 날짜 지정 서울 대중교통 287ms였습니다. 도보 표본은 `ROUTE_EXISTS`·1,237m·1,036초, 대중교통 표본은 `ROUTE_EXISTS`·1,169초를 반환했습니다. 진단 스크립트는 애플리케이션을 우회하므로 새 V21 DB 집계에는 포함되지 않습니다.

현재 로컬 `.env`에는 OpenAI API 키가 없어 OpenAI 실호출은 수행하지 않았습니다. 응답 `usage`의 입력/출력 토큰 저장, 요청·토큰 한도 차단, 성공/실패·지연시간은 HTTP Stub과 PostgreSQL 통합 테스트로 검증했습니다. 실제 OpenAI 비용 비교는 키와 결제 프로젝트를 설정한 운영 환경에서 남은 항목입니다.

실행: PowerShell 7의 `scripts/verify-live-integrations.ps1 -IncludeGoogle`. 재점검은 Open-Meteo 1회와 Google 5회를 요청했습니다. 직접 진단 호출은 앱 월별 집계에 포함되지 않습니다. 문서 정리·커밋 과정에서는 유료 API를 다시 호출하지 않았습니다.

| 점검 | 응답 | 소요 시간 | 확인 사항 |
|---|---:|---:|---|
| 서울 Open-Meteo 예보 | 200 | 1,167ms | 16일, `Asia/Seoul` |
| 경복궁 Text Search | 200 | 381ms | 장소 검색 응답 |
| 해당 장소 Details | 200 | 200ms | 영업 구간 6개, 요일 설명 7개 |
| 도쿄 도보 Matrix, 1요소 | 200 | 286ms | `ROUTE_EXISTS`, 1,237m, 1,036초 |
| 도쿄 도로 Geometry, 1구간 | 200 | 236ms | encoded polyline 반환 |
| 서울 대중교통 Matrix, 다음 날 09:00 KST, 1요소 | 200 | 272ms | `ROUTE_EXISTS`, 1,169초 |

검증 당시 Compose 설정과 실행 중인 백엔드의 `ROUTEPLAN_ROUTE_PROVIDER=GOOGLE`이 일치했고, 서로 다른 서버·브라우저 키가 반영되어 있었습니다. 백엔드 health는 `UP`, 프론트 응답은 HTTP 200이었습니다. 저장소 기본 설정은 유지하므로 다른 환경에서는 각 API와 키를 별도로 설정해야 합니다.

## 초기 점검 이력

같은 날짜의 설정 전 점검에서는 날씨·장소 검색·영업시간은 성공했지만 도쿄 도보 Matrix(208ms), 도쿄 Geometry(166ms), 서울 대중교통 Matrix(157ms)가 모두 `403 SERVICE_DISABLED`였습니다. 당시에는 `SIMPLE`을 유지했고 브라우저 지도 키도 미설정이었습니다. 이는 과거 상태이며 현재 재점검 결과는 위의 HTTP 200 표입니다.

## 실제 브라우저 검증

사용자 로그인 후 공개 장소만 사용하는 검증용 여행 1개를 새로 만들었습니다. 기존 여행과 계정 정보는 변경하지 않았으며 검증용 여행은 로컬에 남겨두었습니다. 출발점은 실제 숙소나 사용자 위치가 아닌 테스트 좌표입니다.

- 여행: `V17 지도 검증 · 서울역–경복궁`
- 날짜·시간: 2026-08-29, 09:00 출발, `Asia/Seoul`
- 이동수단: 대중교통
- 입력: 서울역을 검증용 출발점으로 지정, 경복궁 1곳을 좌표로 추가, 체류 60분
- 앱 계산: 왕복 약 7.2km·40분, 경복궁 09:20 도착·10:20 출발, 출발점 10:40 복귀
- 계산 메타데이터: Matrix 4요소, 생성 484ms
- 지도: `실제 도로 경로` 전환 후 Google 한국어 지도와 왕복 경로선 표시 확인
- 인증·오류: 브라우저 키 인증 오류 및 확인 시점의 콘솔 오류·경고 없음

앱 일정 시각은 날짜별 출발시각 행렬에 기반한 예측입니다. 지도 경로 조회는 방문별 계획 출발시각을 전달하지만 기존 일정 시각을 다시 계산하지 않으며, 실제 배차나 교통 상황을 보장하지 않습니다.

## 출처 문구 수정 및 회귀 검증

지도 검증 중 실제 Google 계산 결과 아래에 `좌표 기반 예상 경로`가 잘못 표시되는 문제를 발견했습니다. 프론트가 서버에 없는 `EXTERNAL_PROVIDER`와 비교하고 있었으므로 다음과 같이 수정했습니다.

- `GOOGLE_ROUTES` → `실제 도로 경로`
- `STRAIGHT_LINE_ESTIMATE` → `좌표 기반 예상 경로`
- `routeDataType`을 두 서버 enum 값의 TypeScript union으로 제한
- 실제 일정 화면을 렌더링하는 회귀 테스트 2개 추가: 수정 전 Google 분기 실패, 수정 후 두 분기 통과

프론트 컨테이너만 재빌드·교체한 뒤 기존 검증용 일정의 VERSION 1을 유지한 채 올바른 문구와 잘못된 문구의 부재를 확인했습니다. 이 수정 검증에서는 일정 재계산이나 추가 Google 경로 호출을 하지 않았습니다.

## 아직 검증하지 않은 항목

- Google Cloud Billing의 실제 청구액, 청구 계정 전체 무료 잔량
- 실제 quota 소진 동작 및 운영 환경의 호출 한도
- 다른 국가·이동수단·여행 날짜 조합, 대규모 입력의 실환경 성능
- 운영 도메인·서버 IP의 키 제한, 허용하지 않은 출처에서의 차단 동작
- 실제 OpenAI 키·결제 프로젝트를 사용한 Responses API와 앱 집계 비교

한도 소진은 비용을 발생시켜 재현하지 않고 기존 Retry HTTP stub과 월별 원자적 예약 통합 테스트로 검사했습니다. 키, 프로젝트 ID, 계정 식별 정보와 원본 Places/Routes 응답은 이 기록에 포함하지 않았습니다. 테스트 통과나 앱 내부 호출 한도가 무료 사용을 보장하지 않습니다.

## 자동 검증 범위

- V22 구현 검증: Backend 134개, Frontend 49개, Chromium/모바일 E2E 11개, ESLint와 Backend/Frontend 프로덕션 빌드 통과.

- V17 구현 검증: Backend 100개, Chromium E2E 6개 통과. 이 두 묶음은 출처 문구만 수정하는 후속 작업에서 다시 실행하지 않았습니다.
- 출처 문구 수정 후: Frontend 34개 전체 테스트, ESLint, TypeScript·Vite 프로덕션 빌드 통과. Docker 프론트 빌드와 실제 화면 반영도 확인했습니다.
- E2E의 외부 유료 Provider는 비활성화합니다. 기존 E2E는 393px 모바일 장부 가로 넘침, 지출·한도·후기·신고·운영자 처리 흐름을 포함하며 실제 Google 지도 검증은 위의 수동 브라우저 확인과 구분합니다.
