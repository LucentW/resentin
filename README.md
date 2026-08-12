# Resentin ☕

*"Resentin"* è, in dialetto veneto/friulano, l'atto di pulire i fondi di una
tazzina di caffè con la grappa. È anche il nome di questo client nativo Android per
[grappa-irc](https://github.com/vjt/grappa-irc), il bouncer IRC self-hosted
con API REST + Phoenix Channels.

Scritto in Kotlin + Jetpack Compose.

## Screenshot

<p align="center">
  <img src="docs/screenshots/home.png" width="30%" alt="Schermata iniziale con badge non letti">
  <img src="docs/screenshots/chat-bubbles.png" width="30%" alt="Chat in modalità bolle">
  <img src="docs/screenshots/chat-irc-line.png" width="30%" alt="Chat in modalità monoriga IRC">
</p>

*(Sopra: home con contatori messaggi non letti, chat in modalità "Bolle" con
link cliccabili e upload immagini, chat in modalità "Monoriga IRC" — screenshot
presi collegandosi come visitor a [irc.sindro.me](https://irc.sindro.me),
rete Azzurra.)*

## Funzionalità

- Login via client token o username/password (inclusi i client token per
  account con 2FA), con supporto ai login "visitor" anonimi dei bouncer che
  li offrono.
- Elenco reti/canali con contatori dei messaggi non letti e delle menzioni
  (badge rosso se ci sono menzioni), in grassetto se ci sono messaggi da
  leggere.
- Chat in tempo reale via Phoenix Channels, con backfill dello storico e
  cursore di lettura sincronizzato col server (riprende da dove eri rimasto,
  con un divisore "hai letto fino a qui").
- Due modalità di visualizzazione, selezionabili dalle impostazioni:
  **Bolle** e **Monoriga IRC** (`[timestamp] <nick> messaggio`), entrambe
  con prefisso di ruolo sul nick (`~&@%+`) e timestamp opzionali coi secondi.
- Parsing colori mIRC (nei messaggi e nei topic) e link cliccabili in chat.
- Popup con il topic completo del canale al tap sull'header.
- Upload di file/foto in chat (pulsante allegato) e integrazione come
  **Share Target** Android — puoi condividere foto/file da altre app
  direttamente su una chat di Resentin.
- Notifiche push per messaggi privati e menzioni, con servizio in
  background opzionale.
- Alias di testo personalizzabili.

## Requisiti

Serve un'istanza di [grappa-irc](https://github.com/vjt/grappa-irc)
raggiungibile via HTTPS e un token client (o le credenziali del tuo
account). Se vuoi solo provare l'app, molte istanze pubbliche di grappa-irc
accettano login "visitor" anonimi senza bisogno di un account.

## Build

```sh
./gradlew assembleDebug
```

L'APK di debug viene generato in `app/build/outputs/apk/debug/`.

## Licenza

Rilasciato con licenza MIT — vedi [LICENSE](LICENSE), come
[grappa-irc](https://github.com/vjt/grappa-irc/blob/main/LICENSE).
