# Dede Takip — Android uygulaması

Yaşlı bir yakını uzaktan kollamak için konum uygulaması. Arka planda seyrek (varsayılan 5 dk)
sunucuya konum gönderir; bakan kişi panelden **"Şimdi konum iste"** deyince bir sonraki
kontrolde (en geç ~1 dk) anlık konum gelir. **Az internet, az pil.**

Uygulamanın içinde gömülü gizli bilgi yoktur: sunucu adresi ve cihaz kodu telefonda ilk
kurulumda bir kez girilir. Bu yüzden depo herkese açık olabilir.

## APK nasıl oluşuyor
GitHub'a her gönderimde **GitHub Actions** uygulamayı bulutta derler ve
`dede-takip.apk` dosyasını **Releases** altında yayımlar. Sabit indirme linki:

```
https://github.com/<KULLANICI>/<DEPO>/releases/latest/download/dede-takip.apk
```

Bu linki dedenin telefonunun tarayıcısında açıp indir, dokunup kur.

## Telefonda kurulum (bir kez, bakan kişi yapar)
1. Linki aç → `dede-takip.apk` iner. "Bilinmeyen uygulama" uyarısında tarayıcıya izin ver.
2. Uygulamayı aç.
3. **1) İzinleri ver** → konumu **"Her zaman izin ver"** seç; bildirim iznini onayla;
   açılan ekranda **pil optimizasyonunu kapat**.
4. Sunucu adresi (hazır gelir), Cihaz adı (`dede`) ve panelden verilen **Cihaz kodu**'nu yaz.
5. **2) Kaydet ve başlat.** Bildirimde "Dede Takip çalışıyor" görünür. Uygulamayı kapatabilirsin.

## Teknik
- `MainActivity` — tek seferlik kurulum ekranı + izinler.
- `TakipService` — foreground servis; ~60 sn'de bir sunucuya "istek var mı" sorar, gerekince
  GPS alıp gönderir.
- `BootReceiver` — telefon yeniden başlarsa takibi sürdürür.
- Harici bağımlılık yalnızca Google Play Konum servisleri; ağ/JSON Android'in kendi
  sınıflarıyla.
