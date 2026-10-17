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

KMZ = zip + `doc.kml`. Java-da KML parse etməyə vaxt itirmə, əvvəlcədən GeoJSON-a çevir:

```bash
mkdir -p data/processed
for f in routes bike_parkings bike_lanes; do
  unzip -o data/raw/$f.kmz -d /tmp/$f
  ogr2ogr -f GeoJSON data/processed/$f.geojson /tmp/$f/doc.kml -t_srs EPSG:4326
done
cp data/raw/terminals.geojson data/processed/terminals.geojson
```

`ogr2ogr` yoxdursa Python:

```python
import zipfile, geopandas as gpd
for f in ["routes","bike_parkings","bike_lanes"]:
    zipfile.ZipFile(f"data/raw/{f}.kmz").extractall(f"/tmp/{f}")
    gpd.read_file(f"/tmp/{f}/doc.kml", driver="KML").to_file(f"data/processed/{f}.geojson", driver="GeoJSON")
```

Metro çıxışları CSV-si: lat/lon sütunlarını yoxla (Bakı: lat 40.3–40.6, lon 49.7–50.1). `data/processed/metro_exits.geojson`-a çevir (Point feature, `properties.station`, `properties.exit`).

**Yoxlama (MUST, 15 dəq):** hər faylda feature sayı, həndəsə tipi, atribut adları. Trayektoriyalarda marşrut nömrəsi hansı atributdadır (`Name`, `description`)? `<when>` vaxt damğaları varmı? Nəticəni bu faylın sonuna yaz.

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
- Hub sayı: `mocks/hubs.json` → `base × (1 + 0.35·exp(−(t−8.4)²/1.4) + 0.25·exp(−(t−18.2)²/2))` + səs-küy ±5%. `t` = günün saatı (onluq). Demo zamanı saat sabit 08:12-dən başlayır (`halqa.demo.clock-start=08:12`), real vaxtla irəliləyir.
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

## 7. Yoxlama nəticələri (doldurun)

- Trayektoriyalarda marşrut atributu: …
- Vaxt damğası var/yox: …
- Metro CSV sütunları: …
- Velo parkı sayı: … , velo zolağı feature sayı: …
