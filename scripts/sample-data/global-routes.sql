\set ON_ERROR_STOP on

-- RoutePlan local/staging sample data set v1.
-- 40 public routes and 160 real-world places across six inhabited continents.
-- Coordinates were resolved from English Wikipedia pages and Wikidata P625 values
-- on 2026-09-02. Descriptions, schedules and popularity counters are synthetic.
-- Re-running this file replaces only routes owned by the dedicated sample curator.

BEGIN;

CREATE TEMP TABLE sample_routes (
    route_no SMALLINT PRIMARY KEY,
    route_key VARCHAR(40) NOT NULL UNIQUE,
    title VARCHAR(150) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    region VARCHAR(100) NOT NULL,
    travel_days INTEGER NOT NULL,
    transport_mode VARCHAR(30) NOT NULL,
    pace VARCHAR(20) NOT NULL
) ON COMMIT DROP;

INSERT INTO sample_routes
    (route_no, route_key, title, description, region, travel_days, transport_mode, pace)
VALUES
    (1, 'seoul', '서울 궁궐과 도시 디자인 루트', '궁궐과 한옥 골목, 전망대와 현대 건축을 연결한 서울 입문 일정입니다.', '대한민국 · 서울', 2, 'PUBLIC_TRANSIT', 'STANDARD'),
    (2, 'busan', '부산 바다와 산복도로 루트', '해안 사찰과 해변, 산복도로 마을과 전통시장을 둘러보는 부산 일정입니다.', '대한민국 · 부산', 2, 'PUBLIC_TRANSIT', 'ACTIVE'),
    (3, 'tokyo', '도쿄 전통과 스카이라인 루트', '아사쿠사의 전통부터 메이지 신궁과 시부야의 현대 풍경까지 잇습니다.', '일본 · 도쿄', 2, 'PUBLIC_TRANSIT', 'STANDARD'),
    (4, 'kyoto', '교토 사찰과 대나무숲 루트', '후시미이나리와 기요미즈데라, 금각사와 아라시야마를 천천히 만납니다.', '일본 · 교토', 2, 'PUBLIC_TRANSIT', 'RELAXED'),
    (5, 'osaka', '오사카 성과 미식 거리 루트', '오사카의 대표 성곽과 사찰, 도톤보리와 우메다 야경을 하루에 압축했습니다.', '일본 · 오사카', 1, 'PUBLIC_TRANSIT', 'ACTIVE'),
    (6, 'beijing', '베이징 황실 문화 루트', '자금성과 천안문, 천단과 이화원으로 이어지는 중국 황실 문화 일정입니다.', '중국 · 베이징', 3, 'PUBLIC_TRANSIT', 'STANDARD'),
    (7, 'shanghai', '상하이 고전과 마천루 루트', '예원과 박물관의 역사에서 와이탄과 상하이 타워의 야경으로 이어집니다.', '중국 · 상하이', 2, 'PUBLIC_TRANSIT', 'STANDARD'),
    (8, 'bangkok', '방콕 왕궁과 사원 루트', '왕궁과 왓 포·왓 아룬을 둘러보고 짜뚜짝 시장까지 경험하는 일정입니다.', '태국 · 방콕', 2, 'PUBLIC_TRANSIT', 'STANDARD'),
    (9, 'singapore', '싱가포르 정원과 건축 루트', '마리나베이의 정원과 상징물, 미술관과 보타닉 가든을 연결합니다.', '싱가포르 · 싱가포르', 2, 'PUBLIC_TRANSIT', 'RELAXED'),
    (10, 'delhi', '델리 세계유산 건축 루트', '레드 포트와 인디아 게이트, 후마윤 묘와 쿠트브 미나르를 탐방합니다.', '인도 · 델리', 2, 'DRIVING', 'STANDARD'),
    (11, 'paris', '파리 예술과 센강 루트', '에펠탑과 루브르, 개선문과 노트르담을 중심으로 파리의 상징을 만납니다.', '프랑스 · 파리', 2, 'PUBLIC_TRANSIT', 'RELAXED'),
    (12, 'london', '런던 왕실과 박물관 루트', '런던탑과 대영박물관, 웨스트민스터 사원과 버킹엄 궁전을 잇습니다.', '영국 · 런던', 2, 'PUBLIC_TRANSIT', 'STANDARD'),
    (13, 'rome', '로마 고대 유적 산책 루트', '콜로세움과 판테온에서 트레비 분수와 나보나 광장까지 걷습니다.', '이탈리아 · 로마', 2, 'WALKING', 'RELAXED'),
    (14, 'florence', '피렌체 르네상스 하루 루트', '두오모와 우피치, 베키오 다리와 베키오 궁전을 한 번에 둘러봅니다.', '이탈리아 · 피렌체', 1, 'WALKING', 'STANDARD'),
    (15, 'barcelona', '바르셀로나 가우디와 구시가 루트', '사그라다 파밀리아와 구엘 공원, 카사 바트요와 고딕 지구를 연결합니다.', '스페인 · 바르셀로나', 2, 'PUBLIC_TRANSIT', 'STANDARD'),
    (16, 'madrid', '마드리드 미술과 왕궁 루트', '프라도 미술관과 왕궁, 마요르 광장과 레티로 공원을 둘러봅니다.', '스페인 · 마드리드', 2, 'PUBLIC_TRANSIT', 'RELAXED'),
    (17, 'lisbon', '리스본 대항해 시대 루트', '벨렝의 유산과 코메르시우 광장, 상 조르제 성을 만나는 일정입니다.', '포르투갈 · 리스본', 2, 'PUBLIC_TRANSIT', 'STANDARD'),
    (18, 'amsterdam', '암스테르담 미술관과 운하 루트', '국립미술관과 반고흐 미술관, 안네 프랑크의 집과 담 광장을 잇습니다.', '네덜란드 · 암스테르담', 1, 'PUBLIC_TRANSIT', 'ACTIVE'),
    (19, 'berlin', '베를린 역사와 장벽 루트', '브란덴부르크 문과 국회의사당, 박물관섬과 이스트사이드 갤러리를 봅니다.', '독일 · 베를린', 2, 'PUBLIC_TRANSIT', 'STANDARD'),
    (20, 'prague', '프라하 성과 구시가 루트', '프라하성과 성 비투스 대성당에서 카를교와 구시가 광장까지 걷습니다.', '체코 · 프라하', 1, 'WALKING', 'STANDARD'),
    (21, 'vienna', '빈 궁전과 클래식 건축 루트', '쇤브룬과 호프부르크, 벨베데레와 슈테판 대성당을 둘러봅니다.', '오스트리아 · 빈', 2, 'PUBLIC_TRANSIT', 'RELAXED'),
    (22, 'athens', '아테네 고대 문명 루트', '아크로폴리스와 박물관, 아고라와 올림피아 제우스 신전을 탐방합니다.', '그리스 · 아테네', 2, 'WALKING', 'STANDARD'),
    (23, 'new-york', '뉴욕 공원과 랜드마크 루트', '센트럴파크와 메트로폴리탄 미술관, 타임스스퀘어와 자유의 여신상을 만납니다.', '미국 · 뉴욕', 3, 'PUBLIC_TRANSIT', 'ACTIVE'),
    (24, 'washington-dc', '워싱턴 DC 역사박물관 루트', '국회의사당과 자연사박물관, 링컨기념관과 백악관을 연결합니다.', '미국 · 워싱턴 DC', 2, 'PUBLIC_TRANSIT', 'STANDARD'),
    (25, 'san-francisco', '샌프란시스코 베이 루트', '금문교와 알카트라즈, 팰리스 오브 파인아츠와 페리 빌딩을 둘러봅니다.', '미국 · 샌프란시스코', 2, 'PUBLIC_TRANSIT', 'STANDARD'),
    (26, 'los-angeles', '로스앤젤레스 문화와 해변 루트', '그리피스 천문대와 게티센터, 할리우드와 산타모니카를 잇습니다.', '미국 · 로스앤젤레스', 2, 'DRIVING', 'ACTIVE'),
    (27, 'mexico-city', '멕시코시티 역사예술 루트', '소칼로와 미술궁전, 국립인류학박물관과 차풀테펙성을 탐방합니다.', '멕시코 · 멕시코시티', 2, 'PUBLIC_TRANSIT', 'STANDARD'),
    (28, 'toronto', '토론토 도심 문화 루트', 'CN 타워와 로열온타리오박물관, 세인트로렌스마켓과 카사 로마를 봅니다.', '캐나다 · 토론토', 2, 'PUBLIC_TRANSIT', 'STANDARD'),
    (29, 'vancouver', '밴쿠버 공원과 항구 루트', '스탠리파크와 그랜빌아일랜드, 캐필라노 현수교와 캐나다플레이스를 잇습니다.', '캐나다 · 밴쿠버', 2, 'DRIVING', 'RELAXED'),
    (30, 'rio', '리우 전망과 현대문화 루트', '구세주 그리스도상과 슈거로프산, 셀라론 계단과 미래박물관을 만납니다.', '브라질 · 리우데자네이루', 2, 'DRIVING', 'ACTIVE'),
    (31, 'buenos-aires', '부에노스아이레스 탱고와 거리 루트', '마요광장과 콜론극장, 라보카와 레콜레타 묘지를 둘러봅니다.', '아르헨티나 · 부에노스아이레스', 2, 'PUBLIC_TRANSIT', 'STANDARD'),
    (32, 'lima', '리마 고대와 식민도시 루트', '마요르 광장과 라르코박물관, 와카 푸클라나와 분수공원을 잇습니다.', '페루 · 리마', 2, 'DRIVING', 'STANDARD'),
    (33, 'cusco', '쿠스코 잉카 유산 루트', '아르마스광장과 삭사이와만, 코리칸차와 산페드로시장을 방문합니다.', '페루 · 쿠스코', 2, 'WALKING', 'RELAXED'),
    (34, 'cairo', '카이로 고대 이집트 루트', '기자 피라미드와 이집트박물관, 카이로성채와 칸엘칼릴리를 탐방합니다.', '이집트 · 카이로', 2, 'DRIVING', 'STANDARD'),
    (35, 'marrakech', '마라케시 메디나와 정원 루트', '제마엘프나와 바히아궁전, 마조렐정원과 쿠투비아모스크를 만납니다.', '모로코 · 마라케시', 2, 'WALKING', 'RELAXED'),
    (36, 'cape-town', '케이프타운 산과 바다 루트', '테이블마운틴과 로벤섬, V&A 워터프런트와 커스텐보시를 잇습니다.', '남아프리카공화국 · 케이프타운', 3, 'DRIVING', 'ACTIVE'),
    (37, 'nairobi', '나이로비 자연과 문화 루트', '국립박물관과 국립공원, 기린센터와 카렌블릭센박물관을 방문합니다.', '케냐 · 나이로비', 3, 'DRIVING', 'RELAXED'),
    (38, 'sydney', '시드니 하버와 미술 루트', '오페라하우스와 하버브리지, 왕립식물원과 뉴사우스웨일스 미술관을 걷습니다.', '호주 · 시드니', 2, 'PUBLIC_TRANSIT', 'STANDARD'),
    (39, 'melbourne', '멜버른 예술과 시장 루트', '페더레이션광장과 빅토리아국립미술관, 퀸빅토리아마켓과 왕립식물원을 잇습니다.', '호주 · 멜버른', 2, 'PUBLIC_TRANSIT', 'RELAXED'),
    (40, 'auckland', '오클랜드 화산과 항구 루트', '스카이타워와 전쟁기념박물관, 마운트이든과 해양박물관을 만납니다.', '뉴질랜드 · 오클랜드', 2, 'DRIVING', 'STANDARD');

ALTER TABLE sample_routes ADD COLUMN time_zone_id VARCHAR(100);
UPDATE sample_routes SET time_zone_id = CASE
    WHEN route_key IN ('seoul', 'busan') THEN 'Asia/Seoul'
    WHEN route_key IN ('tokyo', 'kyoto', 'osaka') THEN 'Asia/Tokyo'
    WHEN route_key IN ('beijing', 'shanghai') THEN 'Asia/Shanghai'
    WHEN route_key = 'bangkok' THEN 'Asia/Bangkok'
    WHEN route_key = 'singapore' THEN 'Asia/Singapore'
    WHEN route_key = 'delhi' THEN 'Asia/Kolkata'
    WHEN route_key = 'paris' THEN 'Europe/Paris'
    WHEN route_key = 'london' THEN 'Europe/London'
    WHEN route_key IN ('rome', 'florence') THEN 'Europe/Rome'
    WHEN route_key IN ('barcelona', 'madrid') THEN 'Europe/Madrid'
    WHEN route_key = 'lisbon' THEN 'Europe/Lisbon'
    WHEN route_key = 'amsterdam' THEN 'Europe/Amsterdam'
    WHEN route_key = 'berlin' THEN 'Europe/Berlin'
    WHEN route_key = 'prague' THEN 'Europe/Prague'
    WHEN route_key = 'vienna' THEN 'Europe/Vienna'
    WHEN route_key = 'athens' THEN 'Europe/Athens'
    WHEN route_key IN ('new-york', 'washington-dc') THEN 'America/New_York'
    WHEN route_key IN ('san-francisco', 'los-angeles') THEN 'America/Los_Angeles'
    WHEN route_key = 'mexico-city' THEN 'America/Mexico_City'
    WHEN route_key = 'toronto' THEN 'America/Toronto'
    WHEN route_key = 'vancouver' THEN 'America/Vancouver'
    WHEN route_key = 'rio' THEN 'America/Sao_Paulo'
    WHEN route_key = 'buenos-aires' THEN 'America/Argentina/Buenos_Aires'
    WHEN route_key IN ('lima', 'cusco') THEN 'America/Lima'
    WHEN route_key = 'cairo' THEN 'Africa/Cairo'
    WHEN route_key = 'marrakech' THEN 'Africa/Casablanca'
    WHEN route_key = 'cape-town' THEN 'Africa/Johannesburg'
    WHEN route_key = 'nairobi' THEN 'Africa/Nairobi'
    WHEN route_key = 'sydney' THEN 'Australia/Sydney'
    WHEN route_key = 'melbourne' THEN 'Australia/Melbourne'
    WHEN route_key = 'auckland' THEN 'Pacific/Auckland'
END;
ALTER TABLE sample_routes ALTER COLUMN time_zone_id SET NOT NULL;

CREATE TEMP TABLE sample_places (
    route_key VARCHAR(40) NOT NULL REFERENCES sample_routes(route_key),
    sequence SMALLINT NOT NULL,
    source_id VARCHAR(100) NOT NULL UNIQUE,
    place_name VARCHAR(150) NOT NULL,
    latitude NUMERIC(9, 6) NOT NULL,
    longitude NUMERIC(10, 6) NOT NULL,
    category VARCHAR(50) NOT NULL,
    PRIMARY KEY (route_key, sequence)
) ON COMMIT DROP;

INSERT INTO sample_places
    (route_key, sequence, source_id, place_name, latitude, longitude, category)
VALUES
    ('seoul', 1, 'Q482485', '경복궁', 37.579884, 126.976800, '역사'),
    ('seoul', 2, 'Q490981', '북촌한옥마을', 37.583056, 126.983611, '문화'),
    ('seoul', 3, 'Q69134', 'N서울타워', 37.551216, 126.988276, '전망'),
    ('seoul', 4, 'Q5295847', '동대문디자인플라자', 37.566900, 127.009400, '건축'),
    ('busan', 1, 'Q2494268', '해동용궁사', 35.188333, 129.223333, '종교'),
    ('busan', 2, 'Q491203', '해운대해수욕장', 35.158552, 129.160539, '해변'),
    ('busan', 3, 'Q18641306', '감천문화마을', 35.097500, 129.009167, '문화'),
    ('busan', 4, 'Q490809', '자갈치시장', 35.096650, 129.029661, '시장'),
    ('tokyo', 1, 'Q615183', '센소지', 35.714556, 139.796639, '종교'),
    ('tokyo', 2, 'Q57965', '도쿄 스카이트리', 35.710056, 139.810722, '전망'),
    ('tokyo', 3, 'Q287165', '메이지 신궁', 35.676111, 139.699167, '종교'),
    ('tokyo', 4, 'Q21083961', '시부야 스크램블 교차로', 35.659503, 139.700544, '도시'),
    ('kyoto', 1, 'Q714828', '후시미 이나리 신사', 34.967202, 135.773386, '종교'),
    ('kyoto', 2, 'Q221716', '기요미즈데라', 34.994831, 135.785003, '종교'),
    ('kyoto', 3, 'Q270983', '금각사', 35.039500, 135.728500, '종교'),
    ('kyoto', 4, 'Q2859566', '아라시야마', 35.009583, 135.666389, '자연'),
    ('osaka', 1, 'Q321242', '오사카성', 34.687222, 135.525833, '역사'),
    ('osaka', 2, 'Q964876', '도톤보리', 34.668611, 135.501389, '거리'),
    ('osaka', 3, 'Q339859', '시텐노지', 34.653900, 135.516450, '종교'),
    ('osaka', 4, 'Q1151808', '우메다 스카이빌딩', 34.705278, 135.489722, '전망'),
    ('beijing', 1, 'Q80290', '자금성', 39.915833, 116.390833, '역사'),
    ('beijing', 2, 'Q125445', '천단', 39.882200, 116.406600, '역사'),
    ('beijing', 3, 'Q4132', '이화원', 39.997500, 116.268900, '역사'),
    ('beijing', 4, 'Q164055', '천안문광장', 39.904567, 116.391392, '광장'),
    ('shanghai', 1, 'Q125474', '와이탄', 31.238028, 121.486139, '거리'),
    ('shanghai', 2, 'Q1328025', '예원', 31.229167, 121.487500, '정원'),
    ('shanghai', 3, 'Q18547', '상하이 타워', 31.235500, 121.501000, '전망'),
    ('shanghai', 4, 'Q1051293', '상하이박물관', 31.230278, 121.470556, '박물관'),
    ('bangkok', 1, 'Q873769', '방콕 왕궁', 13.750139, 100.492028, '역사'),
    ('bangkok', 2, 'Q1059910', '왓 포', 13.746389, 100.493611, '종교'),
    ('bangkok', 3, 'Q724970', '왓 아룬', 13.743689, 100.488919, '종교'),
    ('bangkok', 4, 'Q1068311', '짜뚜짝 주말시장', 13.800833, 100.551389, '시장'),
    ('singapore', 1, 'Q630135', '가든스 바이 더 베이', 1.283319, 103.865270, '정원'),
    ('singapore', 2, 'Q208760', '멀라이언', 1.286806, 103.854500, '도시'),
    ('singapore', 3, 'Q6970475', '내셔널 갤러리 싱가포르', 1.290490, 103.851860, '미술관'),
    ('singapore', 4, 'Q3046409', '싱가포르 보타닉 가든', 1.315100, 103.816200, '정원'),
    ('delhi', 1, 'Q45957', '레드 포트', 28.655833, 77.240278, '역사'),
    ('delhi', 2, 'Q245347', '인디아 게이트', 28.612864, 77.229306, '기념물'),
    ('delhi', 3, 'Q189648', '후마윤의 묘', 28.593264, 77.250602, '역사'),
    ('delhi', 4, 'Q187635', '쿠트브 미나르', 28.524355, 77.185248, '역사'),
    ('paris', 1, 'Q243', '에펠탑', 48.858296, 2.294479, '전망'),
    ('paris', 2, 'Q19675', '루브르박물관', 48.861111, 2.335833, '박물관'),
    ('paris', 3, 'Q64436', '에투알 개선문', 48.873780, 2.295040, '기념물'),
    ('paris', 4, 'Q2981', '노트르담 대성당', 48.853000, 2.349800, '종교'),
    ('london', 1, 'Q62378', '런던탑', 51.508200, -0.076198, '역사'),
    ('london', 2, 'Q6373', '대영박물관', 51.519444, -0.126944, '박물관'),
    ('london', 3, 'Q5933', '웨스트민스터 사원', 51.499400, -0.127367, '종교'),
    ('london', 4, 'Q42182', '버킹엄 궁전', 51.501000, -0.142000, '역사'),
    ('rome', 1, 'Q10285', '콜로세움', 41.890278, 12.492222, '역사'),
    ('rome', 2, 'Q99309', '판테온', 41.898611, 12.476944, '역사'),
    ('rome', 3, 'Q185382', '트레비 분수', 41.900833, 12.483056, '기념물'),
    ('rome', 4, 'Q463400', '나보나 광장', 41.898710, 12.473160, '광장'),
    ('florence', 1, 'Q191739', '피렌체 대성당', 43.773056, 11.256944, '종교'),
    ('florence', 2, 'Q51252', '우피치 미술관', 43.768333, 11.255278, '미술관'),
    ('florence', 3, 'Q208633', '베키오 다리', 43.767989, 11.253192, '건축'),
    ('florence', 4, 'Q271928', '베키오 궁전', 43.769444, 11.256111, '역사'),
    ('barcelona', 1, 'Q48435', '사그라다 파밀리아', 41.403690, 2.174330, '종교'),
    ('barcelona', 2, 'Q212867', '구엘 공원', 41.413611, 2.152778, '공원'),
    ('barcelona', 3, 'Q461371', '카사 바트요', 41.391580, 2.164920, '건축'),
    ('barcelona', 4, 'Q17154', '바르셀로나 고딕 지구', 41.382778, 2.176944, '거리'),
    ('madrid', 1, 'Q160112', '프라도 미술관', 40.413889, -3.692222, '미술관'),
    ('madrid', 2, 'Q171517', '마드리드 왕궁', 40.417955, -3.714312, '역사'),
    ('madrid', 3, 'Q1123493', '마요르 광장', 40.415456, -3.707381, '광장'),
    ('madrid', 4, 'Q1131807', '레티로 공원', 40.415260, -3.684500, '공원'),
    ('lisbon', 1, 'Q215003', '벨렝탑', 38.691389, -9.215833, '역사'),
    ('lisbon', 2, 'Q272781', '제로니무스 수도원', 38.697846, -9.205601, '종교'),
    ('lisbon', 3, 'Q999002', '코메르시우 광장', 38.707500, -9.136389, '광장'),
    ('lisbon', 4, 'Q636780', '상 조르제 성', 38.713890, -9.133330, '역사'),
    ('amsterdam', 1, 'Q190804', '암스테르담 국립미술관', 52.360000, 4.885278, '미술관'),
    ('amsterdam', 2, 'Q165366', '안네 프랑크의 집', 52.375147, 4.884040, '박물관'),
    ('amsterdam', 3, 'Q224124', '반고흐 미술관', 52.358333, 4.881111, '미술관'),
    ('amsterdam', 4, 'Q839050', '담 광장', 52.373056, 4.892778, '광장'),
    ('berlin', 1, 'Q82425', '브란덴부르크 문', 52.516272, 13.377722, '기념물'),
    ('berlin', 2, 'Q151897', '독일 국회의사당', 52.518611, 13.376111, '건축'),
    ('berlin', 3, 'Q151963', '박물관섬', 52.521389, 13.395556, '박물관'),
    ('berlin', 4, 'Q313746', '이스트사이드 갤러리', 52.503056, 13.444722, '거리'),
    ('prague', 1, 'Q193369', '프라하성', 50.090000, 14.400000, '역사'),
    ('prague', 2, 'Q204871', '카를교', 50.086389, 14.411944, '건축'),
    ('prague', 3, 'Q421678', '구시가 광장', 50.087000, 14.421000, '광장'),
    ('prague', 4, 'Q5949', '성 비투스 대성당', 50.090833, 14.400556, '종교'),
    ('vienna', 1, 'Q131330', '쇤브룬 궁전', 48.184790, 16.312270, '역사'),
    ('vienna', 2, 'Q46242', '호프부르크 왕궁', 48.206389, 16.365278, '역사'),
    ('vienna', 3, 'Q211818', '벨베데레 궁전', 48.193400, 16.380519, '미술관'),
    ('vienna', 4, 'Q5943', '슈테판 대성당', 48.208427, 16.373256, '종교'),
    ('athens', 1, 'Q131013', '아테네 아크로폴리스', 37.971667, 23.726111, '역사'),
    ('athens', 2, 'Q421084', '아크로폴리스 박물관', 37.968417, 23.728472, '박물관'),
    ('athens', 3, 'Q395367', '아테네 고대 아고라', 37.975000, 23.722500, '역사'),
    ('athens', 4, 'Q1123019', '올림피아 제우스 신전', 37.969372, 23.733078, '역사'),
    ('new-york', 1, 'Q160409', '센트럴파크', 40.782500, -73.966111, '공원'),
    ('new-york', 2, 'Q160236', '메트로폴리탄 미술관', 40.779444, -73.963333, '미술관'),
    ('new-york', 3, 'Q11259', '타임스스퀘어', 40.757500, -73.985833, '광장'),
    ('new-york', 4, 'Q9202', '자유의 여신상', 40.689209, -74.044425, '기념물'),
    ('washington-dc', 1, 'Q54109', '미국 국회의사당', 38.889722, -77.009167, '건축'),
    ('washington-dc', 2, 'Q148554', '스미스소니언 자연사박물관', 38.891300, -77.025900, '박물관'),
    ('washington-dc', 3, 'Q213559', '링컨기념관', 38.889278, -77.050139, '기념물'),
    ('washington-dc', 4, 'Q35525', '백악관', 38.897778, -77.036667, '건축'),
    ('san-francisco', 1, 'Q44440', '금문교', 37.819722, -122.478611, '건축'),
    ('san-francisco', 2, 'Q131354', '알카트라즈섬', 37.826720, -122.422833, '역사'),
    ('san-francisco', 3, 'Q966263', '팰리스 오브 파인아츠', 37.802778, -122.448333, '건축'),
    ('san-francisco', 4, 'Q1408117', '샌프란시스코 페리 빌딩', 37.795556, -122.393611, '시장'),
    ('los-angeles', 1, 'Q575901', '그리피스 천문대', 34.118561, -118.300369, '전망'),
    ('los-angeles', 2, 'Q29247', '게티센터', 34.077500, -118.475000, '미술관'),
    ('los-angeles', 3, 'Q71719', '할리우드 명예의 거리', 34.101400, -118.344967, '거리'),
    ('los-angeles', 4, 'Q595439', '산타모니카 피어', 34.008611, -118.498611, '해변'),
    ('mexico-city', 1, 'Q1348507', '소칼로', 19.432778, -99.133333, '광장'),
    ('mexico-city', 2, 'Q1139081', '멕시코시티 미술궁전', 19.435278, -99.141389, '미술관'),
    ('mexico-city', 3, 'Q524249', '멕시코 국립인류학박물관', 19.426111, -99.186111, '박물관'),
    ('mexico-city', 4, 'Q1072510', '차풀테펙성', 19.420556, -99.181667, '역사'),
    ('toronto', 1, 'Q134883', 'CN 타워', 43.642753, -79.387147, '전망'),
    ('toronto', 2, 'Q649250', '로열온타리오박물관', 43.667572, -79.394683, '박물관'),
    ('toronto', 3, 'Q7589489', '세인트로렌스마켓', 43.648700, -79.371500, '시장'),
    ('toronto', 4, 'Q1046446', '카사 로마', 43.678117, -79.409342, '역사'),
    ('vancouver', 1, 'Q1126258', '스탠리파크', 49.300000, -123.140000, '공원'),
    ('vancouver', 2, 'Q1231423', '그랜빌아일랜드', 49.271389, -123.135000, '시장'),
    ('vancouver', 3, 'Q862283', '캐필라노 현수교', 49.342800, -123.112000, '자연'),
    ('vancouver', 4, 'Q1032014', '캐나다플레이스', 49.288600, -123.111000, '건축'),
    ('rio', 1, 'Q79961', '구세주 그리스도상', -22.951916, -43.210464, '기념물'),
    ('rio', 2, 'Q210722', '슈거로프산', -22.949444, -43.156667, '자연'),
    ('rio', 3, 'enwiki:Selaron_Steps', '셀라론 계단', -22.915200, -43.179100, '거리'),
    ('rio', 4, 'Q10333874', '미래박물관', -22.894130, -43.179400, '박물관'),
    ('buenos-aires', 1, 'Q1126357', '마요광장', -34.608333, -58.371944, '광장'),
    ('buenos-aires', 2, 'Q827401', '콜론극장', -34.601083, -58.383083, '공연장'),
    ('buenos-aires', 3, 'Q690649', '라보카', -34.635556, -58.364722, '거리'),
    ('buenos-aires', 4, 'Q831322', '레콜레타 묘지', -34.588056, -58.393056, '역사'),
    ('lima', 1, 'Q2536763', '리마 마요르광장', -12.045960, -77.030540, '광장'),
    ('lima', 2, 'Q1954240', '라르코박물관', -12.072496, -77.070862, '박물관'),
    ('lima', 3, 'Q677387', '와카 푸클라나', -12.111111, -77.033889, '역사'),
    ('lima', 4, 'Q5201334', '레세르바 공원', -12.070833, -77.033333, '공원'),
    ('cusco', 1, 'Q9060929', '쿠스코 아르마스광장', -13.516711, -71.978823, '광장'),
    ('cusco', 2, 'Q828336', '삭사이와만', -13.507778, -71.982222, '역사'),
    ('cusco', 3, 'Q817594', '코리칸차', -13.520111, -71.975722, '역사'),
    ('cusco', 4, 'enwiki:San_Pedro_Market_Cusco', '쿠스코 산페드로시장', -13.520500, -71.987200, '시장'),
    ('cairo', 1, 'Q12508', '기자 피라미드 단지', 29.976111, 31.132778, '역사'),
    ('cairo', 2, 'Q201219', '이집트박물관', 30.047778, 31.233333, '박물관'),
    ('cairo', 3, 'Q1988240', '카이로성채', 30.029400, 31.261400, '역사'),
    ('cairo', 4, 'Q1061621', '칸엘칼릴리', 30.047500, 31.262222, '시장'),
    ('marrakech', 1, 'Q258348', '제마엘프나', 31.625971, -7.989098, '광장'),
    ('marrakech', 2, 'Q2465115', '바히아궁전', 31.621592, -7.982231, '역사'),
    ('marrakech', 3, 'Q1395431', '마조렐정원', 31.641500, -8.002900, '정원'),
    ('marrakech', 4, 'Q1137533', '쿠투비아모스크', 31.624124, -7.993541, '종교'),
    ('cape-town', 1, 'Q213360', '테이블마운틴', -33.962160, 18.413520, '자연'),
    ('cape-town', 2, 'Q192493', '로벤섬', -33.805000, 18.370000, '역사'),
    ('cape-town', 3, 'Q2166975', 'V&A 워터프런트', -33.903056, 18.422778, '도시'),
    ('cape-town', 4, 'Q289277', '커스텐보시 국립식물원', -33.987500, 18.432500, '정원'),
    ('nairobi', 1, 'Q3330879', '나이로비 국립박물관', -1.273889, 36.815000, '박물관'),
    ('nairobi', 2, 'Q739993', '나이로비 국립공원', -1.373333, 36.858889, '자연'),
    ('nairobi', 3, 'Q5564103', '기린센터', -1.374469, 36.742324, '자연'),
    ('nairobi', 4, 'Q367631', '카렌 블릭센 박물관', -1.351944, 36.712500, '박물관'),
    ('sydney', 1, 'Q45178', '시드니 오페라하우스', -33.857058, 151.214897, '건축'),
    ('sydney', 2, 'Q54495', '시드니 하버브리지', -33.852232, 151.210684, '건축'),
    ('sydney', 3, 'Q54489', '시드니 왕립식물원', -33.862222, 151.217500, '정원'),
    ('sydney', 4, 'Q705551', '뉴사우스웨일스 미술관', -33.868611, 151.217222, '미술관'),
    ('melbourne', 1, 'Q923304', '페더레이션광장', -37.817798, 144.968714, '광장'),
    ('melbourne', 2, 'Q1464509', '빅토리아국립미술관', -37.822500, 144.968889, '미술관'),
    ('melbourne', 3, 'Q860621', '퀸빅토리아마켓', -37.807000, 144.957000, '시장'),
    ('melbourne', 4, 'Q101433843', '빅토리아 왕립식물원', -37.833400, 144.980330, '정원'),
    ('auckland', 1, 'Q722125', '오클랜드 스카이타워', -36.848472, 174.762306, '전망'),
    ('auckland', 2, 'Q758657', '오클랜드 전쟁기념박물관', -36.860600, 174.777800, '박물관'),
    ('auckland', 3, 'Q477145', '마운트이든', -36.882208, 174.756418, '자연'),
    ('auckland', 4, 'Q7942457', '뉴질랜드 해양박물관', -36.842170, 174.763740, '박물관');

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM sample_routes) <> 40 THEN
        RAISE EXCEPTION 'Global sample data must contain exactly 40 routes.';
    END IF;
    IF (SELECT COUNT(*) FROM sample_places) <> 160 THEN
        RAISE EXCEPTION 'Global sample data must contain exactly 160 places.';
    END IF;
    IF EXISTS (
        SELECT route_key FROM sample_places GROUP BY route_key HAVING COUNT(*) <> 4
    ) THEN
        RAISE EXCEPTION 'Every global sample route must contain exactly four places.';
    END IF;
END $$;

INSERT INTO users (nickname)
VALUES ('RoutePlan 글로벌 샘플 봇')
ON CONFLICT (nickname) DO NOTHING;

DELETE FROM shared_routes route
USING users curator
WHERE route.user_id = curator.id
  AND curator.nickname = 'RoutePlan 글로벌 샘플 봇'
  AND route.source_trip_name LIKE '[GLOBAL-SAMPLE-V1] %';

DELETE FROM trips trip
USING users curator
WHERE trip.user_id = curator.id
  AND curator.nickname = 'RoutePlan 글로벌 샘플 봇'
  AND trip.name LIKE '[GLOBAL-SAMPLE-V1] %';

INSERT INTO places (
    external_place_id, name, latitude, longitude, category,
    average_stay_minutes, environment, created_at, updated_at
)
SELECT
    'routeplan-global-v1:' || source_id,
    place_name,
    latitude,
    longitude,
    category,
    CASE
        WHEN category IN ('박물관', '미술관') THEN 120
        WHEN category IN ('역사', '종교') THEN 90
        WHEN category IN ('공원', '정원', '자연') THEN 100
        ELSE 75
    END,
    CASE
        WHEN category IN ('박물관', '미술관', '공연장') THEN 'INDOOR'
        WHEN category IN ('시장', '도시') THEN 'MIXED'
        ELSE 'OUTDOOR'
    END,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM sample_places
ON CONFLICT (external_place_id) DO UPDATE SET
    name = EXCLUDED.name,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    category = EXCLUDED.category,
    average_stay_minutes = EXCLUDED.average_stay_minutes,
    environment = EXCLUDED.environment,
    updated_at = CURRENT_TIMESTAMP;

CREATE TEMP TABLE sample_route_metrics ON COMMIT DROP AS
WITH ordered_places AS (
    SELECT
        sample.*,
        LAG(sample.latitude) OVER route_order AS previous_latitude,
        LAG(sample.longitude) OVER route_order AS previous_longitude
    FROM sample_places sample
    WINDOW route_order AS (PARTITION BY sample.route_key ORDER BY sample.sequence)
)
    SELECT
        route_key,
        COUNT(*) AS place_count,
        STRING_AGG(place_name, ' · ' ORDER BY sequence) AS place_preview,
        AVG(latitude) AS center_latitude,
        AVG(longitude) AS center_longitude,
        COALESCE(SUM(
            CASE WHEN previous_latitude IS NULL THEN 0 ELSE
                ST_Distance(
                    ST_SetSRID(ST_MakePoint(previous_longitude, previous_latitude), 4326)::geography,
                    ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
                )
            END
        ), 0) AS straight_distance
    FROM ordered_places
    GROUP BY route_key;

INSERT INTO trips (
    user_id, name, start_date, end_date, accommodation_name,
    accommodation_latitude, accommodation_longitude, transport_mode, status,
    created_at, updated_at, daily_start_time, daily_end_time, pace, time_zone_id
)
SELECT
    curator.id,
    '[GLOBAL-SAMPLE-V1] ' || route.route_key,
    CURRENT_DATE + 30,
    CURRENT_DATE + 29 + route.travel_days,
    route.region || ' 중심 숙소(샘플)',
    ROUND(metrics.center_latitude, 6),
    ROUND(metrics.center_longitude, 6),
    route.transport_mode,
    'OPTIMIZED',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    TIME '09:00',
    TIME '20:00',
    route.pace,
    route.time_zone_id
FROM sample_routes route
JOIN sample_route_metrics metrics USING (route_key)
CROSS JOIN users curator
WHERE curator.nickname = 'RoutePlan 글로벌 샘플 봇';

INSERT INTO itineraries (
    trip_id, version, algorithm, total_distance_meters,
    estimated_travel_minutes, created_at, optimization_score,
    total_stay_minutes, total_waiting_minutes,
    return_travel_distance_meters, return_travel_minutes,
    return_arrival_time, returned_to_accommodation, time_zone_id,
    data_warnings, travel_mode_snapshot,
    hotel_latitude_snapshot, hotel_longitude_snapshot
)
SELECT
    trip.id,
    1,
    'NEAREST_NEIGHBOR_2_OPT',
    GREATEST(1000, ROUND(metrics.straight_distance * 1.25))::BIGINT,
    GREATEST(15, CEIL(
        metrics.straight_distance * 1.25 /
        CASE route.transport_mode
            WHEN 'WALKING' THEN 75.0
            WHEN 'PUBLIC_TRANSIT' THEN 250.0
            ELSE 500.0
        END
    ))::INTEGER,
    CURRENT_TIMESTAMP,
    80 + MOD(route.route_no, 18),
    360,
    0,
    0,
    0,
    TIME '18:30',
    TRUE,
    route.time_zone_id,
    '전 세계 추천 루트 기능 검증용 합성 일정입니다.',
    route.transport_mode,
    trip.accommodation_latitude,
    trip.accommodation_longitude
FROM sample_routes route
JOIN sample_route_metrics metrics USING (route_key)
JOIN users curator ON curator.nickname = 'RoutePlan 글로벌 샘플 봇'
JOIN trips trip
  ON trip.user_id = curator.id
 AND trip.name = '[GLOBAL-SAMPLE-V1] ' || route.route_key;

INSERT INTO shared_routes (
    user_id, source_trip_id, source_itinerary_id, source_itinerary_version,
    source_trip_name, source_start_date, daily_start_time, daily_end_time,
    accommodation_name, accommodation_latitude, accommodation_longitude,
    transport_mode, pace, algorithm, title, description, region, travel_days,
    visibility, place_count, place_preview, total_distance_meters,
    estimated_travel_minutes, optimization_score, view_count, copy_count,
    like_count, published_at, updated_at, moderated_hidden
)
SELECT
    curator.id,
    trip.id,
    itinerary.id,
    itinerary.version,
    '[GLOBAL-SAMPLE-V1] ' || route.route_key,
    trip.start_date,
    trip.daily_start_time,
    trip.daily_end_time,
    trip.accommodation_name,
    trip.accommodation_latitude,
    trip.accommodation_longitude,
    trip.transport_mode,
    trip.pace,
    itinerary.algorithm,
    route.title,
    route.description,
    route.region,
    route.travel_days,
    'PUBLIC',
    metrics.place_count,
    metrics.place_preview,
    GREATEST(1000, ROUND(metrics.straight_distance * 1.25))::BIGINT,
    GREATEST(15, CEIL(
        metrics.straight_distance * 1.25 /
        CASE route.transport_mode
            WHEN 'WALKING' THEN 75.0
            WHEN 'PUBLIC_TRANSIT' THEN 250.0
            ELSE 500.0
        END
    ))::INTEGER,
    80 + MOD(route.route_no, 18),
    150 + route.route_no * 37,
    5 + MOD(route.route_no * 7, 90),
    10 + MOD(route.route_no * 11, 140),
    CURRENT_TIMESTAMP - MAKE_INTERVAL(days => 40 - route.route_no),
    CURRENT_TIMESTAMP,
    FALSE
FROM sample_routes route
JOIN sample_route_metrics metrics USING (route_key)
CROSS JOIN users curator
JOIN trips trip
  ON trip.user_id = curator.id
 AND trip.name = '[GLOBAL-SAMPLE-V1] ' || route.route_key
JOIN itineraries itinerary
  ON itinerary.trip_id = trip.id
 AND itinerary.version = 1
WHERE curator.nickname = 'RoutePlan 글로벌 샘플 봇';

WITH day_assignment AS (
    SELECT
        sample.*,
        route.travel_days,
        FLOOR(((sample.sequence - 1) * route.travel_days)::NUMERIC / 4)::INTEGER + 1 AS day_number,
        route.transport_mode
    FROM sample_places sample
    JOIN sample_routes route USING (route_key)
), slotted AS (
    SELECT
        assignment.*,
        ROW_NUMBER() OVER (
            PARTITION BY assignment.route_key, assignment.day_number
            ORDER BY assignment.sequence
        ) AS day_slot
    FROM day_assignment assignment
), planned AS (
    SELECT
        slotted.*,
        LAG(latitude) OVER day_order AS previous_latitude,
        LAG(longitude) OVER day_order AS previous_longitude
    FROM slotted
    WINDOW day_order AS (
        PARTITION BY slotted.route_key, slotted.day_number
        ORDER BY slotted.sequence
    )
), timed AS (
    SELECT
        planned.*,
        CASE WHEN previous_latitude IS NULL THEN 0 ELSE
            ROUND(ST_Distance(
                ST_SetSRID(ST_MakePoint(previous_longitude, previous_latitude), 4326)::geography,
                ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
            ) * 1.25)::BIGINT
        END AS travel_distance,
        CASE
            WHEN category IN ('박물관', '미술관') THEN 120
            WHEN category IN ('역사', '종교') THEN 90
            WHEN category IN ('공원', '정원', '자연') THEN 100
            ELSE 75
        END AS stay_minutes,
        TIME '09:30' + ((day_slot - 1) * INTERVAL '3 hours') AS planned_start
    FROM planned
), item_values AS (
    SELECT
        timed.*,
        CASE WHEN travel_distance = 0 THEN 0 ELSE GREATEST(5, CEIL(
            travel_distance /
            CASE transport_mode
                WHEN 'WALKING' THEN 75.0
                WHEN 'PUBLIC_TRANSIT' THEN 250.0
                ELSE 500.0
            END
        ))::INTEGER END AS travel_minutes
    FROM timed
)
INSERT INTO shared_route_items (
    shared_route_id, place_id, day_number, sequence, visit_date,
    place_name, latitude, longitude, category, arrival_time, start_time,
    end_time, travel_distance_meters, estimated_travel_minutes,
    waiting_minutes, stay_minutes, priority, must_visit
)
SELECT
    shared.id,
    place.id,
    item.day_number,
    item.sequence,
    shared.source_start_date + (item.day_number - 1),
    item.place_name,
    item.latitude,
    item.longitude,
    item.category,
    item.planned_start - (item.travel_minutes * INTERVAL '1 minute'),
    item.planned_start,
    item.planned_start + (item.stay_minutes * INTERVAL '1 minute'),
    item.travel_distance,
    item.travel_minutes,
    0,
    item.stay_minutes,
    100 - item.sequence * 5,
    item.day_slot = 1
FROM item_values item
JOIN places place
  ON place.external_place_id = 'routeplan-global-v1:' || item.source_id
JOIN shared_routes shared
  ON shared.source_trip_name = '[GLOBAL-SAMPLE-V1] ' || item.route_key
JOIN users curator
  ON curator.id = shared.user_id
 AND curator.nickname = 'RoutePlan 글로벌 샘플 봇';

DO $$
DECLARE
    route_total INTEGER;
    item_total INTEGER;
BEGIN
    SELECT COUNT(*) INTO route_total
    FROM shared_routes route
    JOIN users curator ON curator.id = route.user_id
    WHERE curator.nickname = 'RoutePlan 글로벌 샘플 봇'
      AND route.source_trip_name LIKE '[GLOBAL-SAMPLE-V1] %';

    SELECT COUNT(*) INTO item_total
    FROM shared_route_items item
    JOIN shared_routes route ON route.id = item.shared_route_id
    JOIN users curator ON curator.id = route.user_id
    WHERE curator.nickname = 'RoutePlan 글로벌 샘플 봇'
      AND route.source_trip_name LIKE '[GLOBAL-SAMPLE-V1] %';

    IF route_total <> 40 OR item_total <> 160 THEN
        RAISE EXCEPTION 'Global sample import verification failed: routes=%, items=%',
            route_total, item_total;
    END IF;
    IF EXISTS (
        SELECT 1
        FROM sample_routes sample
        JOIN shared_routes route
          ON route.source_trip_name = '[GLOBAL-SAMPLE-V1] ' || sample.route_key
        JOIN users curator
          ON curator.id = route.user_id
         AND curator.nickname = 'RoutePlan 글로벌 샘플 봇'
        LEFT JOIN trips trip ON trip.id = route.source_trip_id
        LEFT JOIN itineraries itinerary ON itinerary.id = route.source_itinerary_id
        WHERE trip.id IS NULL
           OR itinerary.id IS NULL
           OR itinerary.time_zone_id <> sample.time_zone_id
    ) THEN
        RAISE EXCEPTION 'Global sample source trip, itinerary or time-zone verification failed.';
    END IF;
END $$;

COMMIT;

SELECT
    COUNT(*) AS sample_routes,
    SUM(place_count) AS sample_route_items,
    COUNT(DISTINCT region) AS sample_regions
FROM shared_routes route
JOIN users curator ON curator.id = route.user_id
WHERE curator.nickname = 'RoutePlan 글로벌 샘플 봇'
  AND route.source_trip_name LIKE '[GLOBAL-SAMPLE-V1] %';
