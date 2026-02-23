<p align="center">
  <img src="icon/Icon.png" width="180">
</p>

# Vinyl

VPN клиент под андроид. Хотел свой клиент типа Happ, только чтобы выглядел нормально и ничего лишнего. Внутри ядро sing-box (libbox), интерфейс на Jetpack Compose.

<p align="center">
  <img src="screenshots/home.png" width="240">
  <img src="screenshots/routing.png" width="240">
  <img src="screenshots/settings.png" width="240">
</p>

### Что умеет

- подписки по ссылке (base64 или просто списком), обновление, остаток трафика и дата окончания если панель это отдает
- ключи vless (reality, tls, ws, grpc, httpupgrade), vmess, trojan, shadowsocks, hysteria2, tuic
- можно вставить сразу пачку ключей из буфера
- автовыбор сервера по пингу
- раздельное туннелирование по приложениям: все / только выбранные / все кроме выбранных
- банки, госуслуги и ру сайты можно пустить мимо впн одной галочкой
- свои сайты напрямую
- DNS через DoH внутри туннеля (Cloudflare, Google, Quad9)
- журнал ошибок, чтобы понять почему не подключается

XHTTP не работает, sing-box его не поддерживает, такие сервера в списке будут серыми. WireGuard пока тоже нет.

### Сборка

Проще всего открыть проект в Android Studio и нажать Run.

Или из консоли:

```
./gradlew assembleRelease
```

apk будет в `app/build/outputs/apk/release/`. Релиз пока подписан debug ключом, так что ставится без проблем.

Минимальный андроид 8.0 (API 26).

Тесты парсера и конфига:

```
./gradlew testDebugUnitTest
```

### TODO

- [ ] WireGuard
- [ ] плитка в шторке для быстрого включения
- [ ] импорт по QR коду
- [ ] нормальная подпись релиза

<br>

<p align="center">
  <sub>Developer <a href="https://github.com/claustrophobDev">claustrophobDev</a></sub>
</p>
