# 01 — Nə real, nə simulyasiya, nə fake

1 günlük vaxtda hər şeyi real qurmaq mümkün deyil və lazım da deyil. Bölgü bir prinsipə əsaslanır:

> **Demo-nun "canlı an"-ını yaradan axınlar REAL olmalıdır** (sərnişin bir şey edir, dispetçer dərhal görür və cavab verir). **Xarici sistemdən gəlməli olan data SIM-dir** (kamera, GPS, Biletim.az). **Yalnız göstərmək üçün olan analitik ekranlar FAKE-dir.**

## Etiketlər

| Etiket | Mənası | Kim yazır |
|---|---|---|
| **REAL** | Java endpoint + Postgres. Data saxlanılır, qaydalar serverdə işləyir | Backend + frontend |
| **SIM** | Java simulyatoru datanı yaradır, API və SSE realdır. Sonra real mənbə ilə əvəzlənir | Backend (simulyator) + frontend |
| **FAKE** | Backend yoxdur. `mocks/*.json` və ya ortaq TS formulası. UI tam interaktiv | Yalnız frontend |

## Bölgü cədvəli

### Ortaq və platforma

| Funksiya | Etiket | Qeyd |
|---|---|---|
| Giriş (JWT, 3 demo hesab) | **REAL** | Qeydiyyat yoxdur. Mobil: "Demo ilə daxil ol" |
| SSE canlı axın `/api/v1/stream` | **REAL** | Bütün canlı hissələrin əsası |
| Statik geo qatlar (avtovağzallar, marşrut xətləri, velo, metro çıxışları) | **REAL** | Fayldan yaddaşa yüklənir, DB-yə yazılmır |
| Şəhər dayanacaqları | **REAL (seed)** | Verilən datada dayanacaq yoxdur → demo ərazi üçün 15–20 dayanacaq `mocks/stops_seed.json`-dan |
| Bildirişlər (siyahı, oxundu, SSE) | **REAL** | Push əvəzinə tətbiqdaxili banner (SSE ilə) |
| Demo idarə paneli `/demo` | **REAL** | Səhnədə ssenariləri düymə ilə işə salmaq üçün. Mütləq lazımdır |

### Mobil

| Funksiya | Etiket | Qeyd |
|---|---|---|
| Xəritə: istifadəçi, dayanacaqlar, qatlar | **REAL** | Dayanacaqlar API-dən |
| Xəritədə avtobuslar | **SIM** | Simulyatorun mövqeləri, SSE |
| Tıxac qatı | **SIM** | Dəhliz seqmentləri, saata görə profil |
| Sıxlıq qatı (dayanacaqda insan sayı) | **SIM** | Hub kamera simulyatoru |
| Növbəti avtobus və doluluğu | **SIM** | Simulyatordan ETA + sərnişin sayı |
| Marşrut variantları (planlayıcı) | **REAL (sadə)** | Yaxın dayanacaq + ortaq marşrut + sabit sürət. Tam routing yoxdur |
| Dayanacağa piyada yol və addımlar | **REAL (sadə)** | Serverdə L-formalı xətt + istiqamət sözləri. İstəyə görə: açıq OSRM piyada servisi |
| Piyada naviqasiya animasiyası | **FAKE** | Real GPS izləmə yox, yol boyu animasiya. Demo-da etibarlıdır |
| Ödəniş: BakıKart, bank kartı | **FAKE** | Həmişə uğurlu |
| Ödəniş: coin ilə | **REAL** | Server 60 coin çıxır, balans yoxlanır |
| Səfər başlat və bitir | **REAL** | `trips` cədvəli, bitəndə rəy bildirişi |
| Rəy (səfərdən sonra) | **REAL** | +5 coin, bir səfərə bir rəy |
| Şikayət göndər, status tarixçəsi | **REAL** | Dashboard-da dərhal görünür |
| Foto əlavə et | **FAKE** | Yalnız "foto var" bayrağı. Fayl yükləmə yoxdur |
| Sorğu cavabla | **REAL** | Bir sorğuya bir cavab, +coin |
| Coin cüzdanı, tarixçə | **REAL** | Ledger əsasında |
| Coin-i banka çevir | **REAL + SIM** | Sorğu realdır, bank cavabı 60 saniyəlik simulyasiya |
| Biletim.az bileti (cüzdanda) | **FAKE** | `mocks/biletim_tickets.json` |
| Google Maps / Waze düymələri | **REAL** | Sadəcə deep-link URL |
| Profil: əlçatanlıq, böyük şrift, dil | **FAKE** | Lokal state. Əlçatanlıq filtri planlayıcıya parametr kimi gedir |
| Velo zolaqları və velo parkları qatı | **REAL** | AYNA datası: `bike-lanes`, `bike-parkings` geo qatları |
| Skuterlər (dayanacaq yanında) | **SIM** | Operator API-si yoxdur → simulyator dayanacaq ətrafında 0–5 skuter yerləşdirir |
| Səfər izləmə razılığı | **REAL** | `PUT /me/settings` → `users.tracking_consent` |
| Minmə və düşmə qeydi | **REAL** | `trips` cədvəlində dayanacaq, vaxt, `alight_detected=AUTO/MANUAL` |
| Düşmənin avtomatik aşkarlanması | **FAKE (demo) / REAL (sonra)** | Demo: 10 saniyəlik taymer. Real məntiq: geofence 80 m + sürət < 2 m/s, 30 san (`07_MOBILE_ROADMAP` MOB-4) |
| "Sizin üçün" proaktiv təklif | **REAL** | `GET /me/suggestion`: vərdiş sorğusu, ≥ 3 oxşar səfər |
| Səfərlərim ekranı (vərdişlər, tarixçə) | **REAL** | `GET /me/trips`, `GET /me/patterns` |

### Web dashboard

| Səhifə / funksiya | Etiket | Qeyd |
|---|---|---|
| Canlı xəritə (2 avtobus, hub-lar, tıxac, kameralar) | **SIM** | Xəritədə yalnız 2 avtobus göstərilir: 88 və 4 |
| Hadisələr lenti | **REAL + SIM** | Real şikayətlər + simulyator xəbərdarlıqları + demo triggerləri |
| KPI kartları (orta sürət, gözləyən, doluluq, məmnunluq) | **SIM** | Simulyatordan; məmnunluq reallardan hesablanır |
| Hub kameraları: kartlar və rəqəmlər | **SIM** | Rəqəm simulyatordan, kamera "görüntüsü" SVG çəkilişdir (FAKE) |
| Hub cədvəli və 15 dəq proqnoz | **SIM** | Sadə trend formulası |
| Avtobus doluluğu: marşrutlar, avtobuslar | **SIM** | Simulyator |
| AI interval tövsiyəsi | **REAL** | Deterministik formula (`02_ARCHITECTURE.md` → İnterval mühərriki) |
| İntervalı tətbiq et | **REAL** | `route_config`-də saxlanılır, simulyator yeni intervalla işləyir |
| Avtomatik tənzimləmə açarı | **REAL** | Açıqdırsa, simulyator hər 30 saniyədə tövsiyəni özü tətbiq edir |
| Topa yığılma xəbərdarlığı | **SIM** | Simulyator iki avtobusu qəsdən yaxınlaşdırır, aşkarlama alqoritmi realdır |
| Sürücüyə mesaj | **FAKE** | Toast + hadisə qeydi |
| Doluluq və məmnunluq qrafiki | **FAKE** | Statik data |
| Avtobus bölgüsü (33 → 88) | **FAKE** | `packages/shared/headway.ts` formulası, slayder tam işləyir |
| Avtovağzal: Biletim.az gəlişləri | **SIM** | `mocks/biletim_arrivals.json` saatı "indi"yə görə sürüşdürülür |
| Ekspress xətt tövsiyəsi | **FAKE** | Satılmış biletlərin cəmindən sadə hesab, frontend-də |
| Şikayətlər: siyahı, axtarış, filtr, səhifələmə | **REAL** | |
| Şikayət detalı, status dəyişimi, operatora göndər | **REAL** | Sərnişinə bildiriş + SSE |
| Şikayəti paylaş (qurumlarla) | **REAL (yüngül)** | Yalnız hadisə qeydi yazılır |
| Sorğular: bütün sorğular, nəticələr | **REAL** | Cavablardan aqreqasiya |
| Yeni sorğu göndər, qaralama, bitir | **REAL** | Göndəriləndə bütün sərnişinlərə bildiriş |
| Coin proqramı: KPI və qaydalar | **REAL (icmal)** | Ledger-dən cəmlər. "Dövriyyədə 3,1 mln" kimi böyük rəqəmlər seed-dən |
| Coin çevirmə sorğuları | **REAL** | |
| Operatorlar: kartlar | **REAL** | Seed + reylərdən orta reytinq + şikayət sayı |
| Avtopark: bütün avtobuslar, filtr, axtarış | **REAL** | 60+ avtobus seed-dən. Doluluq sütunu SIM |
| İnteqrasiyalar səhifəsi | **FAKE** | `mocks/integrations.json` |
| Süni tıxac düyməsi | **REAL (demo)** | `POST /demo/trigger/artificial-jam` → simulyator Fətəli Xan Xoyski pr.-ni JAM edir |
| Yeni hub təklifi | **REAL (sadə qayda)** | Qayda: tıxac ≥ 20 dəq + ≥ 3 marşrut + 400 m-də metro + dayanacaqda ≥ 40 nəfər → `hub_proposals` |
| Hub təklifini plana əlavə et | **REAL** | Status `ACCEPTED` |
| Analitika: haradan-haraya (OD) matrisi | **REAL** | `trips` aqreqatı. Demo üçün 5 000 sintetik səfər seed olunur |
| Analitika: düşmə nöqtələri cədvəli | **REAL** | `trips` aqreqatı (eyni seed) |
| Analitika: KPI-lar (izlənən səfər, orta müddət...) | **REAL + seed** | |
| Analitika: hub saat × həftə günü matrisi | **FAKE** | `mocks/analytics_static.json` |
| Analitika: tələbat proqnozu qrafiki və amillər | **FAKE** | Statik seriya. Təqdimatda "model gələcək mərhələdə" deyin |
| Analitika: ödənişli yol namizədləri və ssenari | **FAKE** | Bal və formula `packages/shared/src/toll.ts`-də |
| Analitika: datanın tətbiqi matrisi, data məhsulları | **FAKE** | Statik məzmun |
| Qlobal axtarış (bort, ID) | **REAL** | Şikayət və ya avtobus səhifəsinə yönləndirir |

## Qurulmayacaqlar (bilərəkdən)

| Nə | Niyə |
|---|---|
| Real kompüter görmə (insan sayma) | Kamera yoxdur. Simulyasiya + gələcək inteqrasiya kimi təqdim olunur |
| ML model təlimi | Formula kifayətdir və izah olunandır. Təqdimatda "qayda əsaslı AI, sonra ML" deyin |
| Real ödəniş, real bank API | Hüquqi və texniki cəhətdən 1 gündə mümkün deyil |
| Native push (APNs/FCM) | SSE ilə tətbiqdaxili banner eyni effekti verir |
| PostGIS | Datanın həcmi kiçikdir, haversine Java-da kifayətdir |
| Qeydiyyat, şifrə bərpası | Demo hesablar |
| Mikroservislər, Kafka, Redis | Bir monolit, bir DB |
| Çoxdillilik | Yalnız `az`. Dil seçimi UI-da var, mətn dəyişmir |

## Vaxt çatmasa nə kəsilir (sıra ilə)

1. Web: Coin proqramı səhifəsini FAKE et (mocks).
2. Web: Analitikadakı OD matrisini və düşmə cədvəlini FAKE et (mocks).
3. Web: Operator kartlarındakı reytinqi seed-dən götür (reylərdən hesablama yox).
4. Mobil: Skuter variantı və əlçatanlıq filtri.
5. Backend: Avtomatik tənzimləmə rejimi.
6. Backend: Coin çevirmə → FAKE.

**Heç vaxt kəsilməyənlər:** şikayət axını (mobil → web → mobil), interval tövsiyəsi və tətbiqi, sorğu göndərmə, coin qazanma, xəritədə canlı avtobus, süni tıxac → hub təklifi, "Sizin üçün" təklifi, `/demo` paneli.
