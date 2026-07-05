# TV Uzaktan Kumanda (TvRemoteUniversal)

Turkcell TV+ Android TV Box, Google TV / Android TV cihazları ve Bluetooth destekleyen
Android TV kutuları için profesyonel bir evrensel uzaktan kumanda uygulaması.

- **Kotlin + Jetpack Compose + Material 3**, MVVM mimari, Hilt ile bağımlılık enjeksiyonu, Coroutines/Flow.
- Ağ üzerinden **Android TV Remote Protocol** (Google TV / Google Home uygulamalarının kullandığı
  aynı protokol) ile bağlanır; yalnızca gerektiğinde **Bluetooth HID** yöntemine düşer.
- İlk çalışan yöntem otomatik seçilir ve cihaz bilgisi kaydedilerek sonraki açılışlarda
  otomatik yeniden bağlanılır.

## Hızlı başlangıç

1. Android Studio (Ladybug/Koala veya daha yenisi) ile bu klasörü **açın** (`File > Open`,
   `settings.gradle.kts` dosyasının olduğu kök klasörü seçin).
2. Android Studio "Gradle wrapper bulunamadı" uyarısı gösterirse **"OK/Use Gradle from..."**
   seçeneğini onaylayın; proje `gradlew`/`gradle-wrapper.jar` dosyalarını otomatik oluşturacaktır
   (bu ikili dosyalar depoya dahil edilmedi, bkz. "Bilinen sınırlamalar").
3. Gradle sync tamamlandıktan sonra bir Android telefon/emülatörde (API 28+) çalıştırın.
4. TV'nizle aynı Wi-Fi ağında olduğunuzdan emin olun, uygulamayı açın, cihazınızı seçin.

## Mimari

```
data/
  model/            TvDevice, ConnectionState, ConnectionType, RemoteKey (transport-agnostic)
  local/db/         Room: eşleştirilmiş cihazların kalıcı kaydı
  discovery/        NSD (mDNS) ve klasik Bluetooth keşfi
  remote/
    atv/            Android TV Remote Protocol: eşleştirme + uzaktan kumanda istemcisi
    bluetooth/      Bluetooth HID (klavye+medya+fare) istemcisi
    RemoteTransport  Her iki istemcinin de uyduğu ortak arayüz
  repository/       RemoteControlRepository: hangi yöntemin kullanılacağına karar verir
di/                 Hilt modülleri
ui/                 Compose ekranları (Discovery, Pairing, Remote) + ViewModel'ler + bileşenler
```

`RemoteControlRepositoryImpl`, bir cihaza bağlanırken önce ağ üzerinden Android TV Remote
Protokolü'nü dener; yalnızca bu mümkün değilse (cihaz mDNS ile bulunamadıysa veya protokol
bağlantısı kurulamadıysa) Bluetooth HID'e geçer. Bağlantı türü ve (varsa) eşleştirme sertifika
parmak izi Room veritabanında saklanır, böylece bir sonraki açılışta hiçbir soru sormadan aynı
yöntemle otomatik bağlanılır.

## Android TV Remote Protokolü hakkında önemli not

Bu protokol **Google tarafından resmî olarak yayımlanmamıştır**. Google TV / Google Home
mobil uygulamalarının kullandığı, topluluk tarafından tersine mühendislikle çözülmüş bir
protokoldür (mDNS keşfi `_androidtvremote2._tcp.`, port 6467 üzerinden PIN kodlu eşleştirme,
port 6466 üzerinden Protocol Buffers tabanlı tuş/uygulama-başlatma mesajları). Bu projedeki
`pairing.proto` ve `remotemessage.proto` şemaları, birden fazla bağımsız açık kaynak
uygulamanın (ör. Home Assistant'ın `androidtvremote2` entegrasyonu, çeşitli GitHub projeleri)
yayımladığı gerçek bayt izleriyle çapraz doğrulanarak oluşturuldu; alan numaraları (key inject,
ping/pong, ses seviyesi, uygulama başlatma) doğrulanmış durumda. IME/klavye ile ilgili birkaç
mesaj (`RemoteImeBatchEdit` vb.) daha düşük güvenilirlikte olduğu için **kullanılmıyor** — metin
gönderimi bunun yerine, her karakteri tek tek tuş basışı olarak gönderen, çok daha güvenilir bir
yöntemle yapılıyor (bkz. `AndroidKeyCodeMapper`).

**Sonuç olarak:** Google TV / Google Home uygulamasından telefonla kumanda özelliği bir cihazda
çalışıyorsa, bu uygulamanın ağ üzerinden kumanda özelliği de büyük olasılıkla çalışacaktır.
Çalışmıyorsa (cihaz Google tarafından lisanslı/sertifikalı bir Android TV değilse, ör. bazı
operatör kutuları), Bluetooth HID moduna otomatik geçilir.

## Turkcell TV+ uyumluluğu

Turkcell **TV+ Pro** kutusu, Android 11 tabanlı, Google tarafından lisanslı bir **Google TV**
cihazıdır (Google Play Store dahil) ve Bluetooth 5.0 üzerinden klavye/fare/gamepad
bağlanmasını destekler — yani hem Android TV Remote Protokolü hem de Bluetooth HID yolu için
uygun bir donanımdır. Daha eski/lisanssız TV+ kutuları (ör. "TV+ Ready") Google sertifikalı
olmayabilir; bu durumda uygulama otomatik olarak Bluetooth'a düşer. TV+ kısayol düğmesi
`com.turkcell.ott` paket adını hedefler; TV kutunuzdaki gerçek paket adı farklıysa
`RemoteViewModel.kt` içindeki `PKG_TV_PLUS` sabitini güncelleyin (TV'de Ayarlar > Uygulamalar'dan
kontrol edebilirsiniz).

## Bluetooth HID modu hakkında

Telefon, TV'ye bir Bluetooth klavye/medya kumandası/fare gibi görünen bir **HID cihazı** olarak
kaydolur (`android.bluetooth.BluetoothHidDevice`, API 28+). D-Pad, OK, ses, sessiz ve
oynat/duraklat düğmeleri standart USB-HID klavye/tüketici sayfası kodlarıyla gönderildiği için
güvenilir çalışır. **Home/Back/Menu** düğmelerinin bu modda TV tarafından nasıl yorumlandığı
TV'nin Android sürümüne/başlatıcısına bağlıdır (klavyenin Home/Escape/Uygulama tuşlarına eşlenir)
— bu, Bluetooth HID uzaktan kumandalarının genel bir sınırlamasıdır, bu uygulamaya özgü değildir.
**Uygulama kısayolları (YouTube/Play Store/TV+) yalnızca ağ bağlantısında kullanılabilir**; HID'de
"şu uygulamayı aç" gibi bir kavram yoktur.

## Bilinen sınırlamalar / sonraki adımlar

- **Gradle wrapper ikilisi dahil değil.** `gradlew`/`gradlew.bat`/`gradle-wrapper.jar`
  ikili dosyaları bu ortamda üretilemediği için depoya eklenmedi; Android Studio ilk açılışta
  bunu otomatik tamamlar (yukarıdaki "Hızlı başlangıç" adım 2'ye bakın).
- **Bu kod, bu sohbet ortamında gerçek bir Android SDK/Gradle derlemesinden geçirilemedi**
  (bu sanal alanın ağ erişimi Google'ın Maven deposuna kapalı). Kod özenle, standart
  Android/Kotlin/Compose kalıpları izlenerek yazıldı ve paket/parantez/importlar otomatik
  olarak denetlendi, ancak ilk gerçek derlemede küçük bağımlılık sürüm uyumsuzlukları
  (Android Studio genelde otomatik güncelleme önerir) çıkabilir.
- Türkçe'ye özgü karakterler (ç, ğ, ı, ö, ş, ü) klavye gönderiminde en yakın ASCII karşılığına
  çevrilir; ne bu protokolün ne de standart USB-HID klavyenin genel bir Unicode giriş yöntemi
  vardır.
- Ses seviyesi göstergesi (`RemoteSetVolumeLevel` olayları) alt yapıda dinleniyor ama henüz bir
  UI bileşeni yok; `RemoteControlRepositoryImpl.observeTransportEvents` içine kolayca eklenebilir.
- Dokunmatik alan (touchpad), ağ modunda D-Pad adımlarına yaklaştırılıyor (protokolün gerçek bir
  imleç/dokunma mesajı olup olmadığı doğrulanamadı); Bluetooth modunda ise gerçek göreli fare
  hareketi gönderiliyor.

## Paket adını değiştirmek isterseniz

`app/build.gradle.kts` içindeki `namespace`/`applicationId` ve her Kotlin dosyasındaki
`package com.batin.tvremote...` satırlarını Android Studio'nun "Refactor > Rename Package"
özelliğiyle güvenle değiştirebilirsiniz.
