# 전 세계 추천 루트 샘플 데이터

실제 배포 전 목록·검색·상세 지도·개인화·복사 흐름을 충분한 데이터로 점검할 수 있도록 전 세계 40개 도시의 공개 추천 루트를 제공합니다.

## 데이터 범위

- 공개 추천 루트 40개
- 루트별 실제 명소 4개, 총 160개 일정 항목
- 아시아·유럽·북아메리카·남아메리카·아프리카·오세아니아 포함
- 1~3일 일정과 도보·대중교통·자동차 이동 방식 혼합
- 전용 작성자 `RoutePlan 글로벌 샘플 봇`으로 기존 사용자 데이터와 분리

장소명과 좌표는 2026-09-02 기준 English Wikipedia 문서가 연결한 Wikidata 항목의 [coordinate location(P625)](https://www.wikidata.org/wiki/Property:P625)을 기준으로 확인했습니다. `source_id`가 `Q`로 시작하는 장소는 Wikidata 항목 ID이며, 해당 항목에 좌표가 없던 일부 장소는 Wikipedia 문서와 공개 지도 좌표를 교차 확인해 `enwiki:` 식별자로 기록했습니다.

루트 설명, 체류시간, 이동시간, 숙소명, 조회·복사·좋아요 수는 기능 검증용 합성값입니다. 이동 거리는 PostGIS가 인접 장소의 지리 좌표를 계산한 직선거리에 보정계수를 적용하므로 실제 도로 경로가 아닙니다. 영업시간과 입장 가능 여부는 시시각각 바뀌므로 여행 실행용 데이터로 사용하지 않습니다.

## 로컬 데이터베이스에 적용

PostgreSQL을 포함한 로컬 Compose가 실행 중인 상태에서 PowerShell로 실행합니다.

```powershell
cd C:\Users\DE-2212-003\Documents\ChatGPT\RoutePlan
.\scripts\seed-global-samples.ps1
```

Git Bash 또는 Linux에서는 다음 명령을 사용합니다.

```bash
bash ./scripts/seed-global-samples.sh
```

스크립트는 기존 일반 사용자·여행·공개 루트를 삭제하지 않습니다. 같은 샘플 버전을 다시 실행하면 전용 작성자의 샘플 40개만 교체하므로 중복이 생기지 않습니다.

## 확인

브라우저에서 `http://localhost:3100/`을 새로고침한 뒤 인기 루트와 커뮤니티 목록을 확인합니다. API에서는 한 번에 최대 50개까지 확인할 수 있습니다.

```powershell
Invoke-RestMethod "http://localhost:8180/api/v1/routes?sort=POPULAR&size=50"
```

데이터베이스 수량 검증:

```powershell
docker compose exec -T postgres psql -U routeplan -d routeplan -c `
  "select count(*) routes, sum(place_count) items from shared_routes where source_trip_name like '[GLOBAL-SAMPLE-V1] %';"
```

스테이징에는 로컬 검증이 끝난 뒤에만 동일 SQL을 명시적으로 적용합니다. 운영 데이터베이스에는 자동으로 적재되지 않으며, 실제 운영 배포에는 포함하지 않는 것을 원칙으로 합니다.
