# 실제 외부 API 점검 — 2026-08-28

실행: `scripts/verify-live-integrations.ps1 -IncludeGoogle`. 단일 표본 latency이며 성능 보장이 아닙니다. 저장된 여행/계정은 변경하지 않았습니다.

| 점검 | 응답 | 소요 시간 | 확인 사항 |
|---|---:|---:|---|
| 서울 Open-Meteo 예보 | 200 | 1,161ms | 16일, `Asia/Seoul` |
| 경복궁 Text Search | 200 | 414ms | 장소 검색 응답 |
| 해당 장소 Details | 200 | 361ms | 영업 구간 6개, 요일 설명 7개 |
| 도쿄 도보 Matrix, 1요소 | 403 | 208ms | `SERVICE_DISABLED` |
| 도쿄 도로 Geometry, 1구간 | 403 | 166ms | `SERVICE_DISABLED` |
| 서울 대중교통 Matrix, 다음 날 09:00 KST, 1요소 | 403 | 157ms | `SERVICE_DISABLED` |

현지 날짜/시간은 요청에 포함했지만 Routes API가 비활성화되어 **실제 경로 응답·지도 표시·대중교통 소요시간은 아직 실검증하지 못했습니다**. Google Cloud에서 Routes API를 활성화한 뒤 확인이 필요합니다. 브라우저 전용 키도 현재 비어 있어 Maps JavaScript API 실렌더링은 미검증입니다.

현재 로컬 Route Provider는 `SIMPLE`을 유지했습니다. Google 응답을 받을 수 없는 상태에서 기존 계산까지 실패하도록 자동 전환하지 않았습니다. 영업시간과 날씨 실응답은 확인했습니다.

Google Cloud Billing의 청구액, 계정 전체 무료 잔량, 실제 quota 소진 동작은 접근/검증하지 않았습니다. 한도 소진은 비용을 발생시켜 재현하지 않고 기존 Retry HTTP stub과 월별 원자적 예약 통합 테스트로 검사했습니다. 키, 프로젝트 ID, 원본 Places/Routes 응답은 이 기록에 넣지 않았습니다.

자동 검증 결과: backend 100개, frontend 32개, Chromium E2E 6개 통과. 프론트 lint/build 통과. E2E의 외부 유료 Provider는 비활성화되어 있으며 393px 모바일 장부 가로 넘침, 지출·한도·후기·신고·운영자 처리 흐름을 함께 확인했습니다.
