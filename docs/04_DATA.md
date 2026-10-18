# 04 — Data, seed və simulyator

## 1. Verilən datalar

| Fayl | Format | Harada istifadə olunur |
|---|---|---|
| Avtovağzal və avtostansiyalar | .geojson | `/geo/layers/terminals`, Avtovağzal hub-ı |
| Avtobus trayektoriyaları | .kmz | `/geo/layers/routes`, simulyatorun marşrut həndəsəsi |
| Velosiped parkinqləri | .kmz | `/geo/layers/bike-parkings`, mobil "Velo və skuter" qatı, hub təklifi qaydası |
| Velosiped zolaqları | .kmz | `/geo/layers/bike-lanes`, mobil qat |
| Metro çıxışları (opendata.az) | .csv | `/geo/layers/metro-exits`, planlayıcı, hub təklifi qaydası |
| Stansiyalar üzrə gündəlik sərnişin (opendata.az) | .csv | Analitika KPI-ları, hub baza sıxlığı |

Şəhər **dayanacaqları verilməyib**. Demo ərazisi üçün `mocks/stops_seed.json` (16 dayanacaq) istifadə olunur. Koordinatlar təxminidir, metro çıxışları datası ilə yoxlanmalıdır.

## 2. Çevirmə (bir dəfə, H0–H1, nəticə git-ə)

`data/raw/` git-də deyil (yenidən yükləmək olar), `data/processed/` git-dədir.

**Yükləmə** (opendata.az CKAN API, `https://admin.opendata.az/api/3/action/`):
`package_search?fq=organization:azerbaycan-yerustu-neqliyyat-agentliyi&rows=50`, `package_show?id=metro-cixislari-uzre-koordinatlar`, `package_show?id=stansiyalar-uzre-gundelik-sernisin-gedislerinin-sayi` → `resources[].url`. Şəbəkə yavaşdırsa: `curl -L --retry 3 -C - -o data/raw/<ad> <url>`.

| `data/raw/` | Mənbə resursu |
|---|---|
| `routes.kmz` | `buslines.kmz` (Avtobusların hərəkət trayektoriyası, 5,3 MB) |
| `bike_parkings.kmz` | `mpv-v-mps.-son.kmz` |
| `bike_lanes.kmz` | `movcud-zolaqlar-velo.kmz` |
| `terminals.geojson` | `map.geojson` (Avtovağzal və avtostansiyalar) |
| `metro_exits.csv` | `pathways.csv` |
| `metro_ridership_2025.csv`, `metro_ridership_2026.csv` | `passenger_2025.csv`, `passenger-2026.csv` |

**Çevirmə:** `python3 -I data/convert.py` (yalnız stdlib; `ogr2ogr`/`geopandas` lazım deyil). Özünü yoxlama: `python3 -I data/convert.py --test`. Nə edir:

- KMZ → `doc.kml` → GeoJSON (Point, LineString, Polygon, MultiGeometry, gx:Track). ArcGIS-in `description`-dakı HTML cədvəli (`<td>açar</td><td>dəyər</td>`) atributlara açılır.
- `routes.geojson`: hər feature-a `properties.route` (= Placemark `name`) əlavə olunur.
- `bike_parkings.geojson`: yalnız Bakı daxilindəki Point-lər (mənbədə 10 Google Earth nümunə nöqtəsi və velo zolaqlarının 44 təkrar xətti var, atılır).
- `terminals.geojson`: HTML `description` → atributlar, stil sahələri (`fill`, `stroke`, `label-*`) atılır.
- `metro_exits.geojson`: CSV (`;`, dırnaqlı) → Point, `properties.station`, `properties.exit`. Koordinatlar mənbədə `dərəcə × 10⁶` tam ədəddir, bölünür. "28 May" Excel tərəfindən `28.05.2026 00:00:00`-a çevrilib, ingiliscə `Station` sütunundan bərpa olunur.
- `metro_ridership.geojson`: hər stansiya üçün Point (çıxışların mərkəzi) + `avgDaily2025`, `avgDaily2026`, `monthlyAvgDaily` (`"2025-01"` → orta gündəlik gediş). 2026 faylında adlar latın transliterasiyasıdır, `STATION_ALIASES` + `Station` sütunu ilə uyğunlaşdırılır.

## 3. GeoStore (backend, yaddaşda)

- Startup-da `data/processed/*.geojson` oxunur (Jackson), `Map<String, JsonNode>` kimi saxlanılır, `/geo/layers/{name}` olduğu kimi qaytarır (cache header: 1 saat).
- Simulyator üçün: trayektoriya feature-ları marşrut nömrəsinə görə `Map<String, List<LatLon>>`-a çevrilir. Demo marşrutları (88, 4, 65, 33, M1, H1) tapılmazsa `mocks/sim_routes.json`-dakı dayanacaq ardıcıllığından xətt qurulur.
- Velo parkları və metro çıxışları `List<Point>` — məsafə sorğuları üçün (haversine, xətti axtarış kifayətdir).

## 4. Seed (DB, `SeedRunner`, yalnız boş DB-də)

| Mənbə | Cədvəl | Qeyd |
|---|---|---|
| sabit | `users` | dispatcher, admin, demo passenger (Nigar Əliyeva, 245 coin), + 20 sintetik sərnişin |
| `mocks/operators.json` | `operators` | BakuBus, Xaliq Faiqoğlu, Çinar-Trans, 6 Dost |
| `mocks/buses.json` | `buses` | 66 avtobus, 88-in 6 bortu sabitdir |
| `mocks/route_config.json` | `route_config` | interval və avtobus sayı |
| `mocks/surveys.json` | `surveys`, `survey_questions`, `survey_answers` | Cavab faizlərinə görə sintetik cavablar yarat (sintetik sərnişinlərdən) və ya `responses` sayını ayrıca saxla |
| `mocks/reports_seed.json` | `reports`, `report_events` | `minutesAgo` → `created_at` |
| generator | `trips` (`synthetic=true`) | 5 000 səfər: OD matrisinə uyğun paylanma (aşağıda) |
| generator | `trips` (demo passenger) | Son 3 həftədə iş günləri 08:05–08:20, Elmlər → Nərimanov, 88, 12 dəfə + axşam 65 Gənclik → Elmlər, 5 dəfə |
| generator | `coin_tx` | Demo passenger üçün 4 tarixçə sətri (cüzdanla uyğun) |
| generator | `reviews` | 300 rəy, operator reytinqlərini yaratmaq üçün (BakuBus orta 4,6, Faiqoğlu 3,9, Çinar-Trans 4,2, 6 Dost 3,5) |

**Sintetik səfər paylanması (analitika üçün):** zonalar `Avtovağzal, Koroğlu, 28 May, Nizami, Gənclik, Nərimanov`. Çəkilər web mockup-dakı OD matrisi ilə eynidir (`design/web_dashboard.html` → `OD`). Vaxt: 07:00–10:00 arası 70%, qalanı gün ərzində. `alight_stop` = hədəf zona, `alight_detected=AUTO` 85%.

## 5. Simulyator parametrləri

`mocks/sim_routes.json`:

| Marşrut | Növ | Sürət km/s | Baza doluluq | Qeyd |
|---|---|---|---|---|
| 88 | Elektrik | 22 | 88% | Demo-nun əsas marşrutu |
| 4 | Dizel | 9 | 81% | Yusif Səfərov tıxacından keçir |
| 65 | Dizel | 14 | 58% | |
| 33 | Elektrik | 20 | 31% | Bölgü üçün "boş" marşrut |
| M1 | Elektrik | 28 | 76% | Avtovağzal ekspressi |
| H1 | Elektrik | 26 | 60% | Avtovağzal → Koroğlu |

- Doluluq: `base × (newInterval/oldInterval)` + random walk ±4, [8..100] aralığında.
- Hub sayı: `mocks/hubs.json` → `base × (1 + 0.35·exp(−(t−8.4)²/1.4) + 0.25·exp(−(t−18.2)²/2))` + səs-küy ±5%. `t` = günün saatı (onluq). Demo zamanı saat sabit 08:12-dən başlayır (`yolcu.demo.clock-start=08:12`), real vaxtla irəliləyir.
- Tıxac: `mocks/traffic_segments.json` `baseLevel`. `artificial-jam` → `babek` seqmenti.
- Skuterlər: hər dayanacağın 150 m ətrafında 0–5 skuter, hər 60 saniyədə ±1. Batareya 30–95%.
- Biletim gəlişləri: `minutesFromNow` + indiki vaxt. Gəliş keçdikdən sonra siyahının sonuna +180 dəq ilə qayıdır.
- Hamısı yaddaşda. `/demo/reset` hamısını başlanğıca qaytarır.

## 6. Mock rejim (frontend)

`API_MODE=mock` olduqda client cavabları bu fayllardan qurur:

| Endpoint | Mock mənbə |
|---|---|
| `/admin/operators`, `/admin/buses` | `operators.json`, `buses.json` (filtr və səhifələmə client-də) |
| `/admin/reports` | `reports_seed.json` |
| `/admin/surveys`, `/results` | `surveys.json` |
| `/admin/biletim/arrivals` | `biletim_arrivals.json` |
| `/stops`, `/places` | `stops_seed.json`, `places.json` |
| Analitika FAKE hissələri | `analytics_static.json` (mock rejimdən asılı olmayaraq həmişə) |
| İnteqrasiyalar | `integrations.json` (həmişə) |

## 7. Yoxlama nəticələri (2026-10-17, `data/convert.py`)

| `data/processed/` | Feature | Həndəsə | Atributlar | Qeyd |
|---|---|---|---|---|
| `routes.geojson` (8,9 MB) | 243 | LineString 236, MultiLineString 7 | `name`, `Name`, `FID`, `Shape_Leng` (m), `route`, `styleUrl` | 239 unikal marşrut; `1`, `83`, `174`, `197` iki dəfə. `88` və `4` var (bir feature). 405 861 nöqtə, lon 49,40–50,36, lat 39,95–40,64 |
| `bike_parkings.geojson` | 834 | Point | `name` (`MPS-1` … ), `styleUrl` | lon 49,79–49,98, lat 40,34–40,59 |
| `bike_lanes.geojson` | 60 | LineString | `name` (küçə adı, məs. "Filarmoniya bağı."), `styleUrl` | 575 nöqtə, mərkəz (lat 40,35–40,39) |
| `terminals.geojson` | 69 | Point | `name`, `Name`, `FID`, `X`, `Y` (vergüllü mətn), `icon`, `styleUrl` | Bütün respublika (lat 38,47–41,71), Bakı yalnız bir hissədir |
| `metro_exits.geojson` | 98 | Point | `Stansiya`, `Station`, `yerləşdiyi ünvan`, `Çıxış nörmələri`, `station`, `exit` | 27 stansiya (Memar Əcəmi 2 daxil); bəzi sətirlərdə `exit` boşdur |
| `metro_ridership.geojson` | 27 | Point | `station`, `exits`, `avgDaily2025`, `avgDaily2026`, `monthlyAvgDaily` | 2025-01 … 2026-09. Ən yüklü 2025: Koroğlu 55 479, 28 May 50 755, 20 Yanvar 49 019 |

- **Trayektoriyalarda marşrut atributu:** Placemark `name` (= HTML cədvəlində `Name`), çevrilmiş faylda `properties.route` (məs. `"88"`, `"7A"`, `"H1"`).
- **Vaxt damğası var/yox:** trayektoriyalarda **yoxdur** (`<when>` yox, yalnız həndəsə). `bike_parkings.kmz`-də 230 `<when>` var, amma bunlar Google Earth kamerasının tarixi şəkil tarixləridir (`<Camera><gx:TimeStamp>`), data deyil.
- **Metro CSV sütunları:** çıxışlar — `Stansiya;Station;coğrafi enliyi (latitude);coğrafi uzunluğu (longitude);yerləşdiyi ünvan;Çıxış nörmələri` (98 sətir); sərnişin — `Tarix (gün.ay.il);Stansiya;Gündəlik gediş sayı` (2025: 9 855 sətir, 2026: 7 356 sətir; tarix `2025-01-01 00:00:00,000` formatında).
- **Velo parkı sayı:** 834, **velo zolağı feature sayı:** 60.
- **Tıxac seqmenti `babek` (yoxlama 2026-10-18):** mockup-da "Babək pr." adlanırdı, amma real trayektoriyadan (M8) kəsilmiş xətt OSM-də **Fətəli Xan Xoyski prospekti** üzərindədir (Gənclik metrosundan şərqə). Real Babək prospekti (OSM: lat 40,388–40,395, lon 49,863–49,967) Gənclik metrosundan 1 641 m uzaqdadır. Qərar: həndəsə dəyişmir, göstərilən ad real küçə adıdır; daxili id `babek` sabit saxlanılır.
