# 07 — Mobil roadmap (React Native, Expo)

**Vizual mənbə:** `design/mobile_app.html` (soldakı "Bütün ekranlar" düymələri ilə hər ekrana keç). Rənglər və komponentlər: `08_DESIGN_SYSTEM.md`.

Bir mobil developer (**FE-M**). Demo telefonda **Expo Go** ilə işləyir (dev build lazım deyil).

## Ekranlar (Expo Router)

```
mobile/app/
├── _layout.tsx                 # providers, SSE, push banner, toast
├── index.tsx                   # "Demo ilə daxil ol"
├── (tabs)/_layout.tsx          # 5 tab: Xəritə, Səfər, Ödə (mərkəz), Bildiriş, Coin
├── (tabs)/map.tsx              # Xəritə + alt vərəq
├── (tabs)/trip.tsx             # Marşrut variantları
├── (tabs)/pay.tsx              # Ödəniş
├── (tabs)/notifications.tsx
├── (tabs)/wallet.tsx           # Coin cüzdanı
├── route/[optionId].tsx        # Dayanacağa yol
├── walk/[optionId].tsx         # Naviqasiya
├── ride/[tripId].tsx           # Səfər
├── review/[tripId].tsx         # Rəy (modal)
├── report/new.tsx              # Şikayət
├── report/[code].tsx           # Şikayət statusu
├── survey/[id].tsx             # Sorğu (modal)
├── convert.tsx                 # Banka çevir
├── profile.tsx
└── trips.tsx                   # Səfərlərim və izləmə
```

Paketlər: `expo-router`, `react-native-maps`, `expo-location`, `@tanstack/react-query`, `zustand`, `react-native-sse`, `@expo-google-fonts/onest`, `react-native-svg`, `expo-linking`.

---

## MOB-0 Təməl — H0–H3 · MUST

- [ ] **MOB-0.1** `create-expo-app` (TS), Expo Router, token-lər (`theme.ts`, açıq/tünd, sistemə görə), Onest şrifti.
- [ ] **MOB-0.2** Tab bar: 5 tab, ortada qaldırılmış "Ödə" düyməsi, Bildiriş tabında oxunmamış sayı nişanı.
- [ ] **MOB-0.3** Komponentlər: `Button`, `Plate`, `Occupancy` (3 insan ikonu + Boş/Orta/Dolu), `Status`, `Chip`, `Switch`, `Card`, `BottomSheet` (sabit hündürlük, scroll), `PushBanner` (yuxarıdan sürüşür, toxunanda hədəfə keçir), `Toast`, `CoinBurst` (+N coin animasiyası), `Empty`.
- [ ] **MOB-0.4** `index.tsx`: loqo + "Demo ilə daxil ol" → `/auth/demo-login`, token `expo-secure-store`-da.
- [ ] **MOB-0.5** API client (`EXPO_PUBLIC_API_MODE`, `EXPO_PUBLIC_API_URL`), SSE (`react-native-sse`, `?token=`), hadisələr: `notification.created` → PushBanner + siyahı; `coin.updated` → balans; `report.updated` → status; `bus.positions` → xəritə.
- [ ] **MOB-0.6** Məkan: `expo-location` icazə; rədd edilsə və ya simulyatorda — demo koordinatı (`mocks/places.json` → `demoUser`).

**Qəbul:** telefonda tətbiq açılır, demo girişi işləyir, 5 tab keçidi, dashboard-dan göndərilən sorğu bildirişi banner kimi görünür.

## MOB-1 Xəritə — H3–H8 · MUST

- [ ] **MOB-1.1** `react-native-maps`, istifadəçi mövqeyi (mavi nöqtə), yaxın dayanacaqlar (`/me/home`), dayanacaq markeri + ad etiketi.
- [ ] **MOB-1.2** Canlı avtobuslar (`bus.positions`, yalnız ekrandakı ərazi), lövhə markeri, rəng növə görə.
- [ ] **MOB-1.3** Qat çipləri: Dayanacaq, Avtobus, **Velo və skuter**, Tıxac, Sıxlıq. Default: yalnız ilk ikisi açıq.
- [ ] **MOB-1.4** **Velo və skuter qatı**: velo zolaqları (`/geo/layers/bike-lanes`, yaşıl kəsik xətt), velo parkları (yaşıl "P" + "8 velo"), skuterlər (mavi dairə, `/live/micromobility`). Skuterə toxunanda batareya faizi.
- [ ] **MOB-1.5** Tıxac qatı (`/live/traffic` polylines), Sıxlıq qatı (dayanacaq altında bənövşəyi "12 nəfər").
- [ ] **MOB-1.6** Dinamik legend: yalnız açıq qatı izah edir, bir sətir.
- [ ] **MOB-1.7** Yuxarı banner (`/me/home.banner`), toxunanda Tıxac qatı açılır.
- [ ] **MOB-1.8** Alt vərəq: **"Sizin üçün" kartı** (`/me/suggestion`; "Niyə görürəm?" → səbəb mətni; "Yola düş" → həmin variantın marşrutu), "Haraya gedirsiniz?", saxlanmış yerlər (Ev, İş: Nərimanov, Avtovağzal), yaxın dayanacaqlar: hər marşrut üçün ETA + doluluq, altında "3 skuter, velo parkı 8 yer, velo zolağı".

**Qəbul:** xəritə 5 elementdən çox rəng göstərmir (default vəziyyətdə); hər qat açılanda legend dəyişir; "Sizin üçün" kartından 2 toxunuşla marşrut ekranına çatılır.

## MOB-2 Marşrut variantları — H8–H11 · MUST

- [ ] **MOB-2.1** Haradan (sabit "Mənim yerim"), Haraya (input + `/places` təklifləri).
- [ ] **MOB-2.2** Filtr çipləri: Ən sürətli, Az piyada, Az dolu, Əlçatan → `/trip/plan` yenidən.
- [ ] **MOB-2.3** Variant kartları: etiket, cəm vaxt, addım zolağı (piyada/skuter › lövhə › piyada), doluluq, əlçatan, minmə dayanacağı, ən sürətlidə və tıxacdan keçəndə `why` izahı.
- [ ] **MOB-2.4** Haraya = Avtovağzal olduqda Biletim.az kartı (`mocks/biletim_tickets.json`, FAKE).
- [ ] **MOB-2.5** Google Maps və Waze düymələri (`Linking.openURL`, deep-link URL-ləri `02_ARCHITECTURE` → inteqrasiyalar).

## MOB-3 Dayanacağa yol və naviqasiya — H10–H13 · MUST

- [ ] **MOB-3.1** Xəritədə piyada polyline (nöqtəli bənövşəyi; skuterdə kəsik), hədəf dayanacaq vurğulanır, avtobus xətti və gələn avtobus markeri + "88 gəlir: 6 dəq" etiketi.
- [ ] **MOB-3.2** Alt vərəq: "Avtobusa çatırsınız / çatmırsınız" yoxlaması (`catchable`, `spareMin`), addım siyahısı (ikon, başlıq, izah, məsafə), avtobus addımı (doluluq), son addım.
- [ ] **MOB-3.3** "Avtobus yaxınlaşanda xəbər ver" açarı (lokal; ETA ≤ 2 olduqda banner).
- [ ] **MOB-3.4** Naviqasiya ekranı (FAKE animasiya): istifadəçi nöqtəsi polyline boyunca 11 saniyəyə (skuter: 6) hərəkət edir, yuxarıda böyük dönüş göstərişi (ox fırlanır), aşağıda "Siz / 88" yarış zolaqları, "Dayandır". Çatanda "Ödə və min" (metroda "Metroya daxil oldum") + banner.

**Qəbul:** 88 variantından naviqasiya sonuna qədər axın xətasız; "Dayandır" animasiyanı dayandırır və geri qayıdır.

## MOB-4 Ödəniş və səfər, düşmənin aşkarlanması — H12–H15 · MUST

- [ ] **MOB-4.1** Ödəniş: QR (FAKE), 0,60 ₼, üsullar (BakıKart, bank kartı, coin ilə 60 coin + balans). Coin azdırsa inline xəta (`COINS_INSUFFICIENT` mesajı). "Ödənişi təsdiqlə" → `POST /trips`.
- [ ] **MOB-4.2** Səfər ekranı: üst xəritə (marşrut xətti + avtobus), lövhə, istiqamət, bort, operator, ödəniş xətti, salon doluluğu kartı (avtobus kamerası, "növbəti avtobus daha boşdur"), dayanacaqlar siyahısı, "Şikayət et", "Səfəri paylaş" (Share API), "Düşdüm".
- [ ] **MOB-4.3** **Düşmənin avtomatik aşkarlanması.** Demo rejimi: səfər ekranı açıldıqdan 10 saniyə sonra `POST /trips/{id}/end {detected:"AUTO"}`. Real rejim (SHOULD, `EXPO_PUBLIC_DEMO=false`): hər 5 saniyədə mövqe, hədəf dayanacağa ≤ 80 m və sürət < 2 m/s ardıcıl 30 saniyə → `AUTO`. Səfər izləmə söndürülübsə avtomatik bitmə işləmir, yalnız "Düşdüm".
- [ ] **MOB-4.4** Bitəndə: toast ("Düşdüyünüz aşkarlandı: Nərimanov, 08:41"), xəritəyə qayıt, 1 saniyə sonra "Səfəriniz necə keçdi?" banneri → rəy.

## MOB-5 Rəy — H14–H15 · MUST

- [ ] **MOB-5.1** 3 ulduz sırası (sürücü, yol, avtobus), doluluq seçimi (tək), qeyd çipləri (çox), şərh. "Göndər və 5 coin al" → `POST /trips/{id}/review` → CoinBurst → cüzdan. İkinci dəfə düymə deaktiv, "Bu səfərə artıq rəy vermisiniz".

## MOB-6 Şikayət — H13–H16 · MUST

- [ ] **MOB-6.1** Forma: son səfərin lövhəsi və bortu (dəyişdirmək üçün "QR skan" — FAKE toast), 8 kateqoriya (tək seçim), foto (FAKE bayraq), mətn, məxfilik qeydi, "AYNA-ya göndər".
- [ ] **MOB-6.2** Status ekranı: 4 addımlı timeline, `report.updated` SSE ilə canlı dəyişir; cavab veriləndə +15 coin qeydi.
- [ ] **MOB-6.3** Profildə "Mənim şikayətlərim" siyahısı → status ekranı.

**Qəbul:** göndərəndən sonra dispetçer "Operatora göndər" basanda status ekranı 2 saniyədə yenilənir və banner gəlir.

## MOB-7 Bildirişlər və sorğu — H15–H17 · MUST

- [ ] **MOB-7.1** Siyahı (ikon, başlıq, mətn, vaxt, oxunmamış nöqtə), "Hamısını oxu", toxunanda oxunur və `target`-ə keçir (`survey:`, `report:`, `review:`, `wallet`).
- [ ] **MOB-7.2** Sorğu: bir sual bir ekranda, irəliləmə zolağı, cavab seçimi avtomatik növbəti suala keçir, sonda "Təşəkkürlər" + CoinBurst. Artıq cavablanıbsa "Bu sorğunu artıq cavablamısınız".

## MOB-8 Coin cüzdanı və çevirmə — H16–H18 · MUST

- [ ] **MOB-8.1** Cüzdan kartı: coin balansı, manat ekvivalenti, "Banka çevir", "Gedişə xərclə" (ödənişə coin seçili keçir).
- [ ] **MOB-8.2** "Coin necə qazanılır" (qaydalar), Biletim.az biletləri (FAKE) + "Avtovağzala yol", nişanlar (FAKE), tarixçə (`/me/wallet.history`).
- [ ] **MOB-8.3** Çevirmə: 100 / 200 / Hamısı, manat məbləği, kart, validasiya xətası, "Çevir və köçür" → `POST /me/wallet/conversions`; tarixçədə "icra olunur", bank cavabı gələndə "tamamlandı" + banner.

## MOB-9 Profil və Səfərlərim — H17–H18 · MUST

- [ ] **MOB-9.1** Profil: ad, telefon (maskalı), Əlçatanlıq rejimi (planlayıcıya `accessible=true`), Böyük şrift (bütün mətn × 1,15), Dil (FAKE), Biletim.az hesabı (FAKE açar), Məxfilik, Səfərlərim, Mənim şikayətlərim.
- [ ] **MOB-9.2** Səfərlərim: **Səfər izləmə açarı** (`PUT /me/settings`; söndürüləndə "Sizin üçün" kartı itir), "Tətbiqin öyrəndiyi vərdişlər" (`/me/patterns`), son səfərlər: marşrut, haradan → haraya, mindi/düşdü vaxtı, "avtomatik aşkarlandı".

## MOB-10 Cilalama — H18–H20 · MUST

- [ ] **MOB-10.1** Tünd rejim, böyük şrift rejimi bütün ekranlarda.
- [ ] **MOB-10.2** Yüklənir (skeleton), xəta (yenidən cəhd), oflayn zolaq.
- [ ] **MOB-10.3** Toxunma hədəfləri ≥ 44 pt, `accessibilityLabel` ikon düymələrdə.
- [ ] **MOB-10.4** İki telefonda (iOS + Android) tam demo axını.

## Claude Code üçün nümunə tapşırıq

```
07_MOBILE_ROADMAP.md MOB-1.3–MOB-1.6. design/mobile_app.html-də buildLocal funksiyasındakı
L-micro, L-traffic, L-crowd qatlarına və applyLayers-ə bax: eyni qayda (default yalnız
Dayanacaq və Avtobus, legend tək sətir). react-native-maps Polyline/Marker ilə qur.
```
