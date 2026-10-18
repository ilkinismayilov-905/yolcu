# 11 — Render-də deploy (Blueprint, pulsuz)

Bir Docker image: Next.js web (statik export) + Spring Boot API **eyni portda**, üstəlik Render-in pulsuz Postgres-i. Fayllar: `Dockerfile` (3 mərhələ: web → api → JRE), `.dockerignore`, `render.yaml` (Blueprint), `prod` Spring profili.

## 1. Addım-addım

1. Repo GitHub-dadır (`main`), kökdə `render.yaml` və `Dockerfile` var.
2. [dashboard.render.com](https://dashboard.render.com) → **New** → **Blueprint** → repo-nu seçin → Render `render.yaml`-ı oxuyur: **yolcu-db** (Postgres, free, Frankfurt) və **yolcu** (Docker web service, free, Frankfurt).
3. `DEMO_PASSWORD` `render.yaml`-da **demoyolcu123** kimi yazılıb (login səhifəsində münsiflər üçün göstərilir) — heç nə soruşulmur. Dəyişmək istəsəniz: dashboard → **yolcu** → Environment.
4. **Apply** → Render əvvəlcə bazanı, sonra image-i qurur (ilk build ~8–12 dəq: Node, Maven, JRE image-ləri və asılılıqlar). Flyway sxemi, `SeedRunner` demo datanı ilk açılışda yazır.
5. Deploy bitəndə: `https://yolcu.onrender.com` (ad tutulubsa Render başqa alt domen verir — dashboard-da görünür).
6. Yeni versiya: `main`-ə push → Render avtomatik yenidən qurur (Auto-Deploy).

## 2. Mühit dəyişənləri

| Dəyişən | Mənbə (`render.yaml`) | Məcburi | Nə edir |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | bəli | `prod` profilini açır |
| `DATABASE_URL` | `fromDatabase: yolcu-db / connectionString` | bəli | `postgresql://user:pass@host[:port]/db` → tətbiq özü JDBC URL, istifadəçi və şifrəyə çevirir |
| `JWT_SECRET` | `generateValue: true` | bəli | JWT imzası; yoxdursa və ya 32 baytdan qısadırsa tətbiq **başlamır** |
| `DEMO_PASSWORD` | `demoyolcu123` (login səhifəsində göstərilir) | bəli | Demo hesablarının şifrəsi; dəyişdirib yenidən deploy etsəniz, açılışda yenilənir |
| `DEMO_ENABLED` | `true` | yox (prod default `false`) | `/demo` paneli və `/api/v1/demo/**` — prod-da **yalnız ADMIN** (`admin@yolcu.az`) |
| `SWAGGER_ENABLED` | `false` | yox (prod default `false`) | `true` → `/swagger-ui.html` və `/v3/api-docs` açılır |
| `PORT` | Render özü verir | — | Spring `server.port=${PORT:8080}` |
| `JAVA_TOOL_OPTIONS` | Dockerfile | — | `-Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -Xss512k` + `-XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -XX:TieredStopAtLevel=1` (free plan 512 MB RAM; ölçü aşağıda) |

**Xəritə:** veb xəritə həmişə onlayn OpenFreeMap tile-larından istifadə edir; layihədə offline `data/tiles/baku.pmtiles` rejimi yoxdur, ona görə tile faylı lazım deyil (`data/tiles` image-ə düşmür).

## 3. Yuxuya getməsin: UptimeRobot

Render free web service 15 dəqiqə sorğu gəlməsə yuxuya gedir (növbəti açılış ~30–60 s).

1. [uptimerobot.com](https://uptimerobot.com) → **Add New Monitor** → **HTTP(s)**.
2. URL: `https://<sizin-servis>.onrender.com/api/v1/health`, **Monitoring Interval: 5 minutes**.
3. Gözlənilən cavab: `200` və `{"status":"ok"}` (istəsəniz **Keyword** monitor: `"ok"`).

Qeyd: bir servis 24/7 oyaq qalsa ayda ~744 saat edir, pulsuz limit (750 saat/ay) çatır. Pulsuz Postgres-in müddəti məhduddur (Render hazırda free bazanı 30 gündən sonra silir) — demo-dan sonra lazımdırsa, dashboard-da vaxtında yeniləyin və ya ödənişli plana keçin.

## 4. Deploydan sonra yoxlama siyahısı

- [ ] `GET /api/v1/health` → `200 {"status":"ok"}` (Render health check yaşıl)
- [ ] `https://<servis>/` → login səhifəsi loqo ilə açılır; tab başlığı "Daxil olun | Yolçu"
- [ ] `dispatcher@yolcu.az` + `DEMO_PASSWORD` ilə giriş → `/map`, topbar-da **Canlı** (SSE işləyir; 20 san-da `ping`)
- [ ] Xəritə: OSM tile-ları, 2 avtobus hərəkət edir, hub rəqəmləri dəyişir
- [ ] `/reports/` səhifəsini yeniləyəndə 404 yoxdur (SPA yönləndirməsi)
- [ ] `admin@yolcu.az` ilə `/demo/` → "Süni tıxac" işləyir, "Hamısını sıfırla" işləyir; dispetçer üçün `/api/v1/demo/**` → 403
- [ ] `/swagger-ui.html` → 404 (`SWAGGER_ENABLED=false`)
- [ ] Render **Logs**: `Started YolcuApplication`, xəta yoxdur; **Metrics**: yaddaş < 512 MB
- [ ] UptimeRobot monitoru "Up"

## 5. Yaddaş (512 MB)

`docker run --memory=512m` ilə ölçülüb (yük: xəritə, `routes` qatı, admin siyahıları, SSE):

| Konfiqurasiya | Konteyner yaddaşı |
|---|---|
| Yalnız `-Xmx320m -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -Xss512k` | ~490 MiB (96%) — Render-də OOM riski |
| + `ReservedCodeCacheSize=48m`, `MaxDirectMemorySize=32m`, `TieredStopAtLevel=1`, Tomcat 40 thread (prod) | **~436 MiB (85%)** |

GC-dən sonra canlı heap ~120 MB-dır; Render "Out of memory" ilə yenidən başladarsa, `JAVA_TOOL_OPTIONS`-da `-Xmx256m` edin.

## 6. Lokal yoxlama (Render-siz)

```bash
docker build -t yolcu .
docker run --rm --memory=512m -p 8090:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DATABASE_URL=postgresql://yolcu:yolcu@host.docker.internal:5433/yolcu \
  -e JWT_SECRET=$(openssl rand -base64 32) -e DEMO_PASSWORD=demoyolcu123 -e DEMO_ENABLED=true \
  yolcu
# http://localhost:8090 , http://localhost:8090/api/v1/health
```
