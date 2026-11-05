<div align="center">

# 🌐 DevLab Network Toolkit

**Zestaw narzędzi sieciowych na Androida – diagnostyka i analiza sieci prosto z kieszeni.**

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-blue)
![Release](https://img.shields.io/github/v/release/DevLabStudy/DevLabNetworkToolkit)

[📥 Pobierz APK](https://www.devlabstudy.ovh/phone/) • [🌍 Strona projektu](https://www.devlabstudy.ovh/) • [🐛 Zgłoś błąd](../../issues)

</div>

---

## 📖 O projekcie

**DevLab Network Toolkit** to aplikacja dla administratorów, studentów IT i hobbystów sieci. Pozwala zdiagnozować sieć lokalną, sprawdzić trasę pakietów i znaleźć urządzenia w LAN bez komputera i bez roota.

Projekt powstał w ramach **DevLabStudy** jako autorski projekt edukacyjny i hobbystyczny, rozwijany przez **Tobiasza Kajzera**.

## 🧰 Funkcje

| Narzędzie | Opis |
|---|---|
| 🔍 **Skaner LAN** | Wykrywa urządzenia w podsieci, pokazuje IP, nazwę hosta (NetBIOS / DNS), typ urządzenia i czas odpowiedzi |
| 🛰️ **Traceroute** | Śledzi trasę pakietów do domeny lub adresu IP, hop po hopie |
| [🔧 Kolejne narzędzie] | [Krótki opis] |
| [🔧 Kolejne narzędzie] | [Krótki opis] |

### Skaner LAN – szczegóły
- Automatyczne wykrywanie podsieci Wi-Fi i bramy
- Rozpoznawanie typów urządzeń: router, switch/AP, serwer NAS, Proxmox, Jellyfin/Plex, serwery baz danych, komputery Windows, Raspberry Pi
- Wielowątkowe skanowanie z paskiem postępu

## 💎 Model Freemium

| | **Free** | **PRO** |
|---|:---:|:---:|
| Wszystkie narzędzia | ✅ | ✅ |
| Skaner LAN – wątki równolegle | 8 | **100** |
| Czas skanowania podsieci | wolniejszy | **kilka sekund** |

Wersja Free ma celowo ograniczoną wydajność wybranych operacji. Wersję PRO można odblokować w aplikacji (Google Play) lub kluczem licencyjnym.

## 📥 Pobieranie i instalacja

1. Wejdź na **[devlabstudy.ovh/phone](https://www.devlabstudy.ovh/phone/)** i pobierz najnowszy plik `.apk`
   (lub skorzystaj z zakładki [Releases](../../releases))
2. Na telefonie zezwól na instalację z nieznanych źródeł
3. Otwórz pobrany plik i zainstaluj aplikację

**Wymagania:** Android [minimalna wersja] lub nowszy, połączenie z Wi-Fi (dla skanera LAN).

## ⚠️ Ograniczenia
- Od Androida 10 system blokuje aplikacjom dostęp do adresów MAC innych urządzeń
- Traceroute może nie działać w sieciach blokujących ICMP oraz na emulatorze
- Skaner LAN działa tylko w sieciach IPv4

## 🛠️ Technologie
- **Kotlin** + **Jetpack Compose** (Material 3)
- Coroutines (skanowanie równoległe)
- Google Play Billing
- Supabase (licencje)

## 🔐 Konfiguracja Supabase

Klucz `anon` jest kluczem publicznym aplikacji, ale klucz `service_role` nigdy nie może
trafić do APK. Przed zbudowaniem aplikacji:

1. Skopiuj `local.properties.example` do `local.properties`.
2. Wpisz publiczny klucz `anon` z Supabase jako `supabaseAnonKey`.
3. W Supabase ustaw sekret `SUPABASE_SERVICE_ROLE_KEY` dla Edge Functions.
4. Wdróż funkcję aktywacji:

```bash
supabase functions deploy activate-license
```

Aktywacja licencji odbywa się przez `supabase/functions/activate-license`, dzięki czemu
klucz serwisowy pozostaje wyłącznie po stronie Supabase, a zapis użycia klucza jest
wykonywany warunkowo (`is_used = false`).

## 🧪 Dodatkowe narzędzia

W aplikacji dostępne są również **HTTP Inspector** (kod odpowiedzi, czas i nagłówki)
oraz **Publiczne IP**. Obie funkcje wymagają wyłącznie uprawnienia `INTERNET`.

## 🗺️ Plan rozwoju
- [ ] Wykrywanie rzeczywistej maski podsieci
- [ ] Eksport wyników skanowania
- [ ] [Twój pomysł]

## 📄 Licencja

Projekt jest udostępniony na licencji **MIT**. Szczegóły znajdziesz w pliku [LICENSE](LICENSE).

---

<div align="center">

**DevLabStudy 2026** • Wersja 1.1.1 • Stworzone przez Tobiasza Kajzera

</div>
