# 09 — 1 günlük plan, komanda bölgüsü və demo

## 1. Komanda (5 nəfər; 4 nəfər olarsa PM rolu bölünür)

| Kod | Rol | Əsas məsuliyyət | Roadmap |
|---|---|---|---|
| **BE-A** | Java, domen | Auth, şikayət, coin, bildiriş, səfər, planlayıcı, sorğu | BE-0, 4, 5, 6, 7 |
| **BE-B** | Java, data və real vaxt | Data, seed, SSE, simulyator, doluluq, hub, Biletim, analitika, hub təklifi, demo | BE-1, 2, 3, 8, 9 |
| **FE-W** | Next.js | Bütün dashboard | WEB-0…11 |
| **FE-M** | React Native | Bütün mobil | MOB-0…10 |
| **PM** | Məhsul, dizayn, pitch | Mock JSON-lar, FAKE səhifələrdə köməklik (analitika, bölgü, inteqrasiyalar), QA, pitch deck, demo məşqi | WEB-8, 9.3–9.5, 10.2 |

Hər kəs öz qovluğunda ayrıca Claude Code sessiyası işlədir. Müqavilə dəyişikliyi yalnız `03_API_CONTRACT.md` vasitəsilə, komanda kanalında bir cümlə ilə elan olunur.

## 2. Saat-saat plan (H0 = başlanğıc)

| Saat | BE-A | BE-B | FE-W | FE-M | PM |
|---|---|---|---|---|---|
| **H0–H1** | Repo, Spring skeleti, Docker | Data çevirmə, yoxlama | Next skeleti, token-lər | Expo skeleti, token-lər | `packages/shared/api.ts` (Claude Code ilə 03-dən), mock-ları yoxla |
| **H1–H3** | Flyway V1, JWT, auth | GeoStore, seed, SSE | Layout, primitivlər, login, mock client | Tab bar, primitivlər, demo login, mock client | `headway.ts`, `toll.ts` + test vektorları |
| **✅ CP1 (H3)** | | | | | **Hamı: backend başlayır, login işləyir, web və mobil mock rejimdə açılır** |
| **H3–H6** | Şikayət API | Simulyator: avtobus, hub | Canlı xəritə | Xəritə, qatlar, alt vərəq | Analitika səhifəsi (FAKE tablar) |
| **H6–H8** | Coin, bildiriş | Tıxac, mikromobillik, live API | Şikayətlər səhifəsi | Marşrut variantları | Bölgü səhifəsi, inteqrasiyalar |
| **✅ CP2 (H8)** | | | | | **Əsas dövrə: telefondan şikayət → dashboard-da canlı görünür → "Operatora göndər" → telefonda status dəyişir** |
| **H8–H11** | Səfər, rəy, planlayıcı | İnterval mühərriki, doluluq API | Doluluq səhifəsi | Dayanacağa yol, naviqasiya | Pitch deck qaralaması |
| **H11–H14** | Sorğu API, vərdiş (`/me/suggestion`) | Hub admin, Biletim, lent, KPI | Sorğular, hub kameraları | Ödəniş, səfər, avtomatik düşmə, rəy | QA: CP2 axınını 3 dəfə |
| **✅ CP3 (H14)** | | | | | **Bütün MUST səhifələr live rejimdə açılır, ən azı boş vəziyyətdə** |
| **H14–H17** | Coin çevirmə, xəta halları | Analitika API, hub təklifi qaydası, demo trigger-lər | Operatorlar, Avtovağzal, analitika (REAL hissə), coin, `/demo` | Şikayət, bildiriş, sorğu, cüzdan, çevirmə | Demo ssenarisini yaz, məşq 1 |
| **H17–H18** | Yardım: FE inteqrasiyası | `/demo/reset`, sabitlik | Hub təklifi drawer-i | Profil, Səfərlərim | Məşq 2, ekran yazısı |
| **🧊 FREEZE (H18)** | | | | | **Yeni funksiya yoxdur. Yalnız bug fix və cilalama** |
| **H18–H20** | Bug fix | Bug fix, LAN test | Cilalama (WEB-11) | Cilalama (MOB-10) | Tam məşq 3, vaxt ölçmə |
| **H20–H22** | Hamı: 2 tam demo məşqi, ehtiyat video yazısı | | | | |
| **H22–H24** | Ehtiyat vaxt, istirahət, son yoxlama | | | | |

**Git qaydası:** hər rol öz branch-ında, hər 2–3 saatdan bir `main`-ə merge (CP-lərdə mütləq). `main` həmişə işləyən vəziyyətdə qalır.

**12 saatlıq qısa variant:** H3 → H2, CP2 → H5, CP3 → H8, freeze → H9. Kəsilir: avtobus bölgüsü, coin proqramı səhifəsi (FAKE), çevirmə (FAKE), analitikada OD REAL (FAKE olur), real düşmə məntiqi, skuter variantı.

## 3. Demo konfiqurasiyası

- Bir laptop: Docker Postgres + backend (`demo` profili) + web (`pnpm build && pnpm start`, dev yox).
- Telefon (və ehtiyat ikinci telefon) eyni Wi-Fi-da, Expo Go, `EXPO_PUBLIC_API_URL` = laptopun IP-si. Hotspot ehtiyat kimi hazır.
- Proyektora web (1920 × 1080), telefon ekranı kamera və ya ekran yayımı ilə.
- Demo-dan 5 dəqiqə əvvəl: `/demo/reset`.

## 4. Demo ssenarisi (4 dəqiqə)

| Vaxt | Kim | Nə edir | Nə görünür |
|---|---|---|---|
| 0:00 | Danışan | Problem: "Keçid nöqtələri görünmür: sərnişin yolu, AYNA boşluğu görmür" | Slayd |
| 0:20 | Telefon | Xəritə: Koroğlu tıxacı banneri, "Velo və skuter" qatı açılır | Velo zolaqları, skuterlər |
| 0:35 | Telefon | "Sizin üçün: İş günləri bu saatda Nərimanova gedirsiniz" → Yola düş | Tətbiq yolu özü təklif edir |
| 0:50 | Telefon | Dayanacağa yol → Naviqasiyanı başla → Ödə (coin) | Piyada yol, "88 6 dəq", coin −60 |
| 1:10 | Telefon | Səfər: doluluq kartı. 10 saniyə sonra avtomatik düşmə → rəy → +5 coin | "Düşdüyünüz aşkarlandı" |
| 1:30 | Telefon | Şikayət: "Dayanacaqda saxlamadı" → göndər | — |
| 1:35 | Dashboard | Şikayət lentdə və cədvəldə görünür → aç → "Operatora göndər" | Telefonda status canlı dəyişir |
| 2:00 | Dashboard | Avtobus doluluğu: 88 92% → AI 12 → 8 dəq → tətbiq et | Doluluq düşür, məmnunluq +0,7 |
| 2:25 | Dashboard | Canlı xəritə → "Süni tıxac yarat" | Babək pr. qırmızı, "Yeni hub təklifi" drawer-i |
| 2:50 | Dashboard | Data analitika: OD matrisi → Ödənişli yollar (Yusif Səfərov, 1 ₼) → Datanın tətbiqi | Qərar və gəlir modeli |
| 3:20 | Dashboard | Sorğular → yeni sorğu göndər | Telefonda bildiriş düşür |
| 3:35 | Danışan | Bağlanış: "Bir data qatı, iki ekran, hər siqnal qərara çevrilir" + real/simulyasiya fərqi | Slayd |

**Ehtiyat:** hər addım `/demo` panelindəki düymə ilə də işə salına bilər (`new-report`, `overload-88`, `artificial-jam`, `stadium`).

## 5. Risklər və ehtiyat planlar

| Risk | Əlamət | Plan |
|---|---|---|
| Wi-Fi telefonu laptopla bağlamır | Mobil "bağlantı yoxdur" | Telefon hotspot-u, laptop ona qoşulur; olmasa mobil `API_MODE=mock` |
| SSE qırılır | "Bağlantı bərpa olunur" | Avtomatik yenidən qoşulma; demo öncəsi səhifəni yenilə |
| Docker işləmir | DB bağlantı xətası | `h2` profili |
| Xəritə tile-ları yüklənmir | Boz fon | Offline: web-də `design/web_dashboard.html`-dəki sxematik SVG xəritə komponenti ehtiyat kimi |
| Seed pozulub | Qəribə rəqəmlər | `/demo/reset`, olmasa DB-ni sil və yenidən başlat |
| Canlı demo tam çökür | — | H20-də yazılmış 4 dəqiqəlik ehtiyat video |

## 6. Münsiflərə dürüst izah (pitch-ə daxil edin)

- **Real işləyən:** şikayət, sorğu, coin, səfər, rəy, bildiriş, interval mühərriki, planlayıcı, vərdiş aşkarlanması, hub təklifi qaydası, AYNA və opendata.az dataları.
- **Simulyasiya:** avtobus GPS və kamera rəqəmləri, hub kameraları, tıxac, Biletim.az, bank. Hər biri adapter arxasındadır: real mənbə qoşulanda yalnız adapter dəyişir.
- **Növbəti mərhələ:** AYNA GPS və Biletim.az API, kamera pilotu (1 hub + 10 avtobus), bank müqaviləsi, ML proqnoz modeli.

## 7. Son yoxlama siyahısı (H22)

- [ ] `/demo/reset` işləyir, demo istifadəçinin balansı 245
- [ ] Telefon və laptop eyni şəbəkədə, IP düzgün
- [ ] Web production build-də işləyir
- [ ] Hər iki telefonda demo axını bir dəfə tam keçib
- [ ] Ehtiyat video laptopda və flash-da
- [ ] Pitch deck son versiya, demo linkləri işləyir
- [ ] Bildirişlər, sorğu və şikayət əvvəlki məşqlərdən təmizlənib
