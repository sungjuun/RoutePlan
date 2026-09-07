# RoutePlan 2.0 Phase 4

Phase 4는 기존 공개 Route 커뮤니티를 다시 만들지 않고, 이미 제공하던 공유·복사·좋아요·댓글·후기·신고 흐름에 **Route 저장 보관함**과 **플랫폼별 콘텐츠 가져오기**를 추가합니다.

## 사용자 흐름

### 공개 Route 저장

1. 메인 커뮤니티 또는 여행 안의 커뮤니티에서 공개 Route를 찾습니다.
2. 상세 화면의 `저장`을 누르면 현재 계정의 보관함에 추가됩니다.
3. `저장한 루트` 필터에서 계정별 저장 목록을 확인합니다.
4. 저장한 Route는 그대로 열어 보거나 내 날짜·숙소·이동수단으로 복사해 다시 최적화할 수 있습니다.
5. 저장 취소 시 보관함 목록과 공개 저장 수가 함께 갱신됩니다.

동일 사용자가 같은 Route를 중복 저장할 수 없고, Route 삭제 시 연결된 저장 기록도 함께 삭제됩니다.

### SNS·블로그에서 장소 가져오기

1. 위시리스트 화면에 게시물 또는 웹 URL을 입력합니다.
2. URL 호스트로 YouTube, TikTok, Instagram, 블로그 또는 일반 웹을 판별합니다.
3. 지원되는 공식 메타데이터나 사용자가 붙여 넣은 내용을 장소 후보로 추출합니다.
4. Google Places가 활성화된 경우 실제 장소와 자동 매칭합니다.
5. 사용자가 후보를 확인한 뒤 위시리스트에 저장합니다.

플랫폼 조회 실패는 가져오기 작업 전체의 실패로 바꾸지 않습니다. 대신 캡션 또는 장소 목록을 붙여 넣을 수 있는 입력 대기 상태로 전환합니다.

## 플랫폼별 처리 원칙

| 소스 | 처리 방식 | 키·제한 |
|---|---|---|
| YouTube | Data API `videos.list(part=snippet)`의 제목·설명·태그 | `YOUTUBE_API_KEY` 선택 설정, 미설정 시 직접 입력 |
| TikTok | 공식 oEmbed의 공개 게시물 제목·캡션 | 별도 키 없음, 비공개·삭제 게시물은 직접 입력 |
| Instagram | 사용자가 붙여 넣은 캡션·장소 목록만 사용 | 비공식 크롤링 없음 |
| Naver Blog·Tistory·Medium·Brunch | SSRF 방어가 적용된 공개 HTML 텍스트 추출 | HTTP(S) 80·443, 응답 크기·시간 제한 |
| 기타 공개 웹 | 블로그와 동일한 안전한 HTML 추출 | 내부 IP·리디렉션 차단 |

## API

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/api/v1/routes/{routeId}/saves` | 로그인 사용자의 Route 저장 |
| `DELETE` | `/api/v1/routes/{routeId}/saves` | 저장 취소 |
| `GET` | `/api/v1/me/saved-routes?page=0&size=12` | 내 저장 보관함 |

공개 Route 요약에는 `saveCount`, 상세에는 `saveCount`와 `savedByViewer`가 포함됩니다.

## 데이터 모델

- `route_saves`: `shared_route_id`, `user_id`, `created_at`
- `(shared_route_id, user_id)` 유니크 제약으로 중복 저장 방지
- `shared_routes.save_count`: 목록 조회용 비정규화 카운터
- 사용자 삭제 시 저장 기록 삭제, Route 삭제 시 저장 기록 삭제

## 설정

`.env`에 다음 값을 선택적으로 추가합니다.

```dotenv
YOUTUBE_API_KEY=
```

키가 없어도 YouTube 설명이나 장소 목록을 직접 붙여 넣는 흐름은 동작합니다. 실제 장소 자동 매칭에는 별도로 `ROUTEPLAN_PLACE_PROVIDER=GOOGLE`과 서버용 `GOOGLE_MAPS_API_KEY`가 필요합니다.

## 검증 범위

- Route 저장·중복 저장 차단·보관함 조회·저장 취소 통합 테스트
- YouTube Data API 응답 파싱과 키 미설정 fallback
- TikTok oEmbed 응답 파싱
- 공급자 제한 또는 장애 시 사용자 입력 fallback
- 플랫폼별 URL 판별
- 저장 보관함 프론트엔드 렌더링
- 전체 백엔드·프론트엔드 회귀 테스트와 프로덕션 빌드
