<div align="center">

<img src="https://capsule-render.vercel.app/api?type=rect&color=0:0b1220,100:0e7490&height=110&section=header&text=Trade&fontSize=42&fontColor=22d3ee&fontAlignY=54&desc=Safe%20player-to-player%20trading&descSize=13&descColor=94a3b8&descAlignY=80" width="100%" alt="Trade" />

<p>
<img src="https://img.shields.io/github/v/release/chizzar-dev/Trade?style=flat&label=release&color=06b6d4&labelColor=0b1220" alt="release" />
<img src="https://img.shields.io/badge/Minecraft-1.8%20%E2%80%93%201.21.11-0891b2?style=flat&labelColor=0b1220" alt="Minecraft 1.8 - 1.21.11" />
<img src="https://img.shields.io/badge/Java-8%2B-155e75?style=flat&labelColor=0b1220&logo=openjdk&logoColor=22d3ee" alt="Java 8+" />
<a href="LICENSE"><img src="https://img.shields.io/github/license/chizzar-dev/Trade?style=flat&label=license&color=0e7490&labelColor=0b1220" alt="license" /></a>
</p>

</div>

Trade lets two players exchange items through a shared window. Confirmations reset when an offer changes, and items are never lost if someone disconnects mid-trade.

*Trade, iki oyuncunun ortak bir pencerede esya degismesini saglar. Teklif degisince onaylar sifirlanir, takas sirasinda biri cikarsa esyalar kaybolmaz.*

## Features · Özellikler
- Yan yana iki taraflı takas penceresi — karşının koyduğu her şeyi anlık görürsün
- **Teklif değişince iki tarafın da onayı sıfırlanır** — son an değiştirme scam'ini engeller
- Onay sonrası kısa bir **kilit süresi**; bu sırada teklif değiştirilemez
- **Hiçbir eşya kaybolmaz** — karşı taraf çıkarsa eşyalar teslim kuyruğuna yazılır ve bir dahaki girişinde verilir
- Takas sırasında ölüm, çıkış veya sunucu kapanması: takas iptal olur, herkes kendi eşyasını geri alır
- Belirli eşyalar takasa kapatılabilir (`blacklist`)
- İsteğe bağlı mesafe sınırı
- Tamamlanan takaslar konsola yazılır

## Security · Güvenlik
| Risk | Önlem |
| :-- | :-- |
| Son an eşya değiştirme | Teklif değişince iki onay da sıfırlanır + kilit süresi |
| Karşı taraf çıkınca eşya kaybı | Teslim kuyruğu (`pending.yml`), girişte otomatik verilir |
| Takas sırasında ölüm | Takas iptal, herkes kendi eşyasını alır |
| Sunucu çökmesi / kapanması | Açık takaslar kapanışta iptal edilir |
| Sürükle-bırak ile kopyalama | Pencerede tüm sürükleme ve kaydırma işlemleri kapalı |

## Installation · Kurulum
1. [Releases](https://github.com/chizzar-dev/Trade/releases/latest) sayfasından `Trade.jar` dosyasını indir.
2. Sunucunun `plugins/` klasörüne at.
3. Sunucuyu yeniden başlat.
4. `plugins/Trade/config.yml` dosyasından süreleri ve mesajları düzenle.

## Commands · Komutlar
| Komut | Açıklama | Yetki |
|-------|----------|-------|
| `/trade <oyuncu>` | Takas isteği gönderir | `trade.use` |
| `/trade kabul` | Gelen isteği kabul eder | `trade.use` |
| `/trade ret` | Gelen isteği reddeder | `trade.use` |
| `/trade iptal` | İsteği ya da açık takası iptal eder | `trade.use` |

**Alias:** `/takas`

## Permissions · Yetkiler
| Yetki | Açıklama | Varsayılan |
|-------|----------|------------|
| `trade.use` | Takas yapabilir | herkes |

## Configuration · Ayarlar
| Anahtar | Açıklama |
|---------|----------|
| `request-timeout` | İstek kaç saniye sonra düşer |
| `max-distance` | Takas için en fazla uzaklık (0 = sınırsız) |
| `confirm-delay-ticks` | Onay sonrası kilit süresi (20 tick = 1 sn) |
| `blacklist` | Takas edilemeyecek eşyalar |
| `log-trades` | Takasları konsola yaz |
| `gui.title` / `gui.filler` | Pencere başlığı ve dolgu bloğu |
| `messages.*` | Tüm mesajlar ve GUI düğme yazıları |

## Building · Derleme
```bash
mvn clean package
```
Çıktı · Output: `target/Trade.jar`

Her push [GitHub Actions](https://github.com/chizzar-dev/Trade/actions/workflows/build.yml) ile derlenir; `v*` etiketli sürümler jar'la birlikte [Releases](https://github.com/chizzar-dev/Trade/releases) sayfasına eklenir.
<br><sub>Every push is built by GitHub Actions; tagged `v*` releases attach the jar.</sub>

## License · Lisans
[MIT](LICENSE) — istediğin gibi kullan, değiştir, dağıt · use, modify and distribute freely

<div align="center"><sub>chizzar-dev · Minecraft plugins for 1.8 – 1.21.11</sub></div>
