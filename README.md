# SSH Tunnel Manager

App desktop Java 21 per tunnel SSH locali. Ogni profilo ha un ID stabile e ogni connessione usa una sessione SSH e un listener indipendenti.

Il codice dell'app è distribuito sotto [Apache License 2.0](LICENSE). Le licenze e le attribuzioni delle dipendenze incluse nei pacchetti sono elencate in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Avvio

Con Java 21 disponibile:

```sh
./gradlew run
```

Su Windows: `gradlew.bat run`. Per compilare e testare: `./gradlew build`. Il wrapper scarica Gradle 8.13 e Maven Central fornisce le dipendenze al primo avvio; serve una connessione Internet per la prima build.

## Uso

1. Fai clic destro nello spazio vuoto dell'elenco e scegli **Nuovo** per creare un profilo con host e porta SSH, utente, destinazione raggiungibile **dal server SSH**, porta remota e percorso di una chiave privata leggibile. Funziona anche quando l'elenco è vuoto. Il nome utente del computer è solo una proposta modificabile. **Test** verifica SSH e listener locale senza salvare il profilo o aprire il browser, poi chiude il tunnel di prova. Se confermi la fiducia in una nuova host key durante il test, questa viene salvata in `known_hosts`.
2. Seleziona una porta locale fissa oppure la selezione automatica. Il listener è sempre `127.0.0.1`.
3. Fai clic destro sulla riga del tunnel e scegli **Connetti**. Il menu contestuale contiene anche **Modifica**, **Duplica**, **Elimina**, **Disconnetti** e **Apri nel browser**. Su macOS funziona il clic secondario del trackpad o mouse; da tastiera si può usare Maiusc+F10 sulla riga selezionata. Se l'host non è in `~/.ssh/known_hosts`, confronta il fingerprint mostrato con una fonte affidabile prima di confermare. Una host key cambiata viene rifiutata. La prima chiave accettata viene aggiunta a `known_hosts`.
4. A tunnel avviato, l'app può aprire il browser su `http://127.0.0.1:<porta effettiva>/`. Puoi anche usare **Apri nel browser**.

Puoi avviare più profili contemporaneamente. Modifiche a un profilo già connesso si applicano alla prossima connessione. Eliminare un profilo attivo chiede conferma e attende la chiusura del suo tunnel. La disconnessione non elimina chiavi né voci `known_hosts`.

La finestra mostra l'elenco dei tunnel nella parte superiore e il log del tunnel selezionato nella parte inferiore, scorrevole e ridimensionabile. Il cerchio dello stato è rosso per disconnesso o errore, verde per connesso e ambra durante la connessione; il testo resta visibile accanto al colore. Gli eventi del log sono filtrati per ID del profilo e includono le sessioni precedenti finché restano nei file di log a rotazione.

I profili sono salvati come JSON UTF-8 versionato in `%APPDATA%/SSH Tunnel Manager/profiles.json` su Windows, `~/Library/Application Support/SSH Tunnel Manager/profiles.json` su macOS e `${XDG_CONFIG_HOME:-~/.config}/ssh-tunnel-manager/profiles.json` su Linux. Si salvano solo percorsi delle chiavi, mai contenuti o passphrase. La versione di schema iniziale è 1; versioni sconosciute vengono rifiutate senza sovrascrivere il file in fase di caricamento.

Per diagnosticare un errore, consulta `diagnostic.log` nella stessa directory di `profiles.json`. Il log registra data/ora, ID del profilo, fase (`SERVER`, `IDENTITY`, `AUTHENTICATION`, `FORWARD`), esito e classe dell'eccezione. Non registra host, utente, percorso della chiave, passphrase o stack trace. Il file è limitato a circa 1 MB, con un solo archivio `.old`.

## Sicurezza e limiti attuali

- Autenticazione solo tramite un file di chiave privata scelto esplicitamente. Le chiavi cifrate richiedono una passphrase durante la connessione. Nessuna scansione automatica delle identità, password SSH o supporto SSH agent.
- Il caricamento delle chiavi usa i formati PEM/OpenSSH supportati da Apache MINA SSHD. Ed25519 OpenSSH, anche cifrata, è supportata tramite Bouncy Castle e verificata da test locale. Chiavi hardware, PuTTY PPK e formati non riconosciuti non sono supportati.
- Il client usa `known_hosts` standard e rifiuta chiavi modificate. La conferma di primo utilizzo va fatta solo dopo verifica indipendente del fingerprint.
- Il file JSON è versionato, ma non esistono ancora vecchie versioni da migrare.
- Lo stato di una connessione caduta viene rilevato quando la sessione SSH si chiude. Una perdita di rete che non chiude subito il socket può richiedere tempo per comparire nella UI.

## Pacchetti nativi

Per creare un pacchetto Debian su Linux amd64 con JDK 21 e gli strumenti Debian disponibili:

```sh
./gradlew installDist
jpackage --type deb --name ssh-tunnel-manager --app-version 0.1.0 \
  --vendor Flossconsulting \
  --description 'Desktop SSH local port forward manager' \
  --input build/install/ssh-tunnel-manager/lib \
  --main-jar ssh-tunnel-manager-0.1.0.jar \
  --main-class it.flossconsulting.sshtunnel.Main \
  --dest build/packages --linux-shortcut \
  --linux-app-category Network --linux-menu-group Network \
  --add-modules java.base,java.desktop,java.management,java.naming,java.rmi,java.security.jgss,java.sql,java.logging,jdk.crypto.ec,jdk.unsupported
```

Il risultato è `build/packages/ssh-tunnel-manager_0.1.0_amd64.deb`; `jpackage` include un runtime Java. Il `.deb` va costruito su un sistema compatibile con la distribuzione Linux di destinazione: le dipendenze native dipendono dall'OS usato per crearlo. Per altri OS, `jpackage` crea `.rpm` su Linux, `.msi`/`.exe` su Windows e `.dmg`/`.pkg` su macOS con `--type` appropriato. I pacchetti nativi vanno costruiti sul rispettivo OS; la distribuzione pubblica su macOS e Windows richiede procedure di firma, e macOS può richiedere notarizzazione.

### macOS

Su un Mac con JDK 21 (Intel o Apple Silicon) esegui dalla directory del progetto:

```sh
bash scripts/package-macos.sh
```

Lo script esegue i test, crea la distribuzione Java e produce un `.dmg` in `build/packages/` con runtime incluso. Se `JAVA_HOME` non è impostata, cerca il JDK 21 tramite `/usr/libexec/java_home`. Il pacchetto è specifico dell'architettura del Mac usato per la build: per distribuire sia a Intel sia ad Apple Silicon occorrono due build. Il DMG così creato non è firmato né notarizzato; prima della distribuzione pubblica occorrono certificati Apple Developer ID, firma e notarizzazione. Non è possibile generare un DMG macOS da Linux con `jpackage`.
