# RoutePlan 2.0 고도화 분석과 1차 구현 기준

## 1. 현재 구조

- 백엔드: Java 21, Spring Boot, Spring Security 세션 인증, JPA, PostgreSQL/PostGIS, Redis
- 프론트엔드: React 19, TypeScript, Vite
- 외부 연동: Google Places/Routes/Maps, Open-Meteo, OpenAI Responses API
- 핵심 모듈: `auth`, `user`, `trip`, `place`, `optimization`, `itinerary`, `community`, `budget`, `weather`, `ai`, `integration`
- 운영 기반: Docker Compose, Flyway, Actuator/Prometheus, GitHub Actions, Playwright E2E

## 2. 그대로 재사용할 부분

- `PlaceSearchProvider`: 가져온 장소명을 Google Places 후보로 매칭
- `PlaceService.importExternal`: 외부 장소를 중복 없이 로컬 `places`로 영속화
- `TripService.createFromSnapshot`: 선택한 위시리스트 장소를 제약 조건과 함께 여행으로 복사
- 다일 일정 최적화 파이프라인: 여행 기간별 분배, 시간창·영업시간·우선순위·숙소 복귀·교통량을 기존 알고리즘으로 계산
- `OpenAiHttpClient`: 선택적으로 콘텐츠의 장소명 추출에 사용
- 세션 인증과 리소스 소유권 검사 패턴, 공통 예외 응답, 외부 API 회복성/사용량 계측

## 3. 변경할 부분

- 메인 탐색 진입점을 도시 검색뿐 아니라 SNS/웹 URL 가져오기로 확장
- `TripSetup`이 빈 여행뿐 아니라 위시리스트 스냅샷으로 여행을 만들 수 있도록 확장
- 장소 후보 검토와 위시리스트 관리를 별도의 발견 화면으로 제공
- 가져오기 작업은 요청 스레드를 오래 점유하지 않도록 비동기 상태 모델로 제공

## 4. 새 엔티티와 테이블

- `Wishlist`: 사용자별 장소 모음
- `WishlistPlace`: 저장 장소, 우선순위, 원본 URL/유형, 메모, 예상 비용
- `ContentImport`: URL/텍스트 가져오기 작업과 상태·경고
- `ContentImportCandidate`: 콘텐츠에서 추출한 언급과 Places 매칭 후보

Flyway `V19`에서 테이블·소유자 인덱스·중복 제약·좌표 제약을 함께 생성한다.

## 5. API

- `POST/GET /api/v1/wishlists`, `GET /api/v1/wishlists/{id}`
- `POST/PATCH/DELETE /api/v1/wishlists/{id}/places[//{wishlistPlaceId}]`
- `POST /api/v1/wishlists/{id}/trips`: 선택 장소를 새 여행으로 복사
- `POST /api/v1/imports/url`, `GET /api/v1/imports/{id}`
- `POST /api/v1/imports/{id}/retry`, `POST /api/v1/imports/{id}/save`

모든 개인 API는 로그인 필수이며 사용자 ID를 요청 본문에서 받지 않고 인증 주체에서 얻는다.

## 6. 외부 API와 장애 처리

- Instagram은 약관을 우회해 크롤링하지 않는다. URL을 출처로 기록하고 사용자가 붙여 넣은 캡션/본문만 분석한다.
- 일반 웹 URL은 HTTP(S), 80/443 포트, 공개 IP, HTML, 제한된 응답 크기만 허용한다. 리디렉션을 따르지 않아 SSRF 우회를 막는다.
- Google Places가 꺼져 있거나 일시 장애이면 작업 전체를 실패시키지 않고 미매칭 후보와 경고를 반환한다.
- OpenAI는 `routeplan.ai.provider=OPENAI`일 때만 사용하며 기본값은 결정적인 규칙 기반 추출기다.

## 7. 최적화 결정

별도 K-means/OR-Tools 경로를 추가하지 않는다. 현재 다일 제약 최적화기가 이미 날짜별 배치, 시간창, 체류시간, 이동행렬, 날씨, 예산과 숙소 복귀를 함께 처리한다. 위시리스트는 우선순위를 기존 `TripPlace` 제약으로 변환하고 기존 최적화 API를 호출하는 구조가 일관성과 검증 범위에서 유리하다.

## 8. SNS 가져오기 구조와 보안

`ContentSourceDetector -> ContentImporter -> ContentPlaceExtractor -> PlaceSearchProvider`의 어댑터 체인으로 구성한다. 플랫폼별 수집기는 같은 인터페이스를 구현하므로 공식 API가 준비되면 교체할 수 있다. 작업과 후보는 사용자 소유로 저장하며 URL 길이, 입력 크기, 후보 개수, DNS/IP, 콘텐츠 유형과 응답 크기를 제한한다.

## 9. 구현 순서

1. Phase 1: URL/텍스트 가져오기, 후보 매칭, 위시리스트, 여행 생성, 기존 자동 일정 최적화 연결
2. Phase 2: 드래그앤드롭, 일자 단위 부분 재최적화, AI 편집 고도화, 환율 포함 예산
3. Phase 3: 여행 멤버, 투표, 분담/정산, 현재 위치 기반 주변 추천
4. Phase 4: 공식 SNS 제공자 추가와 커뮤니티 가져오기 확장

이번 변경은 Phase 1을 실제 사용 가능한 종단 흐름으로 완성하고, 나머지는 기존 기능을 깨지 않는 순서로 후속 구현한다.
