# Massiveo's Freaky Addons

En client-side Forge 1.8.9 addon-hub til prison-serveren FreakyVille. Tryk **B**
(eller skriv `/celler`) for at åbne hubben og vælge en addon. Alt kører på din
egen klient.

## Installation

1. Installer Forge til Minecraft 1.8.9.
2. Hent jar-filen fra [nyeste release](../../releases/latest) og læg den i din
   `mods`-mappe.
3. Start spillet. Auto-opdatering holder den ajour (kan slås fra under
   Opdatering-addonen).

## Funktioner

<details>
<summary><b>Vis alle addons</b></summary>

### Celler

- **Celle Scanner**: scanner celle-skilte i indlæste chunks, HUD og ESP gennem
  vægge på celler der snart bliver ledige, og kan dele fundene med de andre.
- **Celle Finder**: søg efter en bestemt celle og få vist vej til den.
- **Mine Celler**: se og find dine egne, delte og inviterede celler.
- **Celle Bot**: deler dine scannede celler med de andre (til/fra).
- **Celle Buyer**: køber cellen i det sekund den bliver ledig. Automatisering,
  slået fra som standard, på eget ansvar.
- **Kiste Organisering**: venstreklik en kiste for at tilføje et flydende
  3D-ikon.

### Tracking

- **ESP**: se spillere gennem vægge, farvet efter bande, vagt eller andre.
- **Navne ESP**: navneskilte gennem vægge, farvet på samme måde.
- **Spiller Info**: shift + højreklik en spiller for at se 3D-model, rustning,
  fortryllelser og celle-oplysninger. Virker også på spillere der er offline.
- **Spiller Logger**: viser i 3D hvor andre spillere loggede ud.
- **Chest Alarm**: notifikation og lyd når en kiste bliver åbnet i chatten.
- **PvP Mine**: drop-timer på et HUD, plus alarm når nogen går ind i PvP-minen.
- **Mine Tracker**: holder øje med Iron Ore i minuttet og estimerede
  diamantblokke.

### Automatisering

Alt herunder er automatisering. Det er slået fra som standard, og du bruger det
på eget ansvar.

- **Auto Mine**: miner et fast mine-område i slangemønster, finder selv vej hen
  for at aflevere når der er fyldt op, spiser undervejs og kravler på stiger.
- **Auto Fish**: fisker automatisk i fiske-zoner, hiver ind på plask og kaster
  ud igen.
- **Auto Crate**: åbner kasser automatisk så længe du har nøgler.
- **Fast Mine**: miner i takt med musen når du graver.
- **Farm Bot**: høster og genplanter modne afgrøder automatisk.
- **Auto Følg**: går og løber bag en anden spiller automatisk (`/følg <navn>`).
- **Anti AFK**: små bevægelser med jævne mellemrum, så inaktivitets-timeren
  aldrig når at slå til.
- **Auto Armour**: tryk R for hurtigt at tage rustning på eller af.
- **Skralde-Filter**: smider automatisk skrald ud (cobblestone, jord, træ- og
  stenredskaber).

### Quality of life

- **Troll Lyde**: fjollede lyde på død, kill, hop og AFK. Kun du kan høre dem.
- **Celle Alarm**: lyd og nedtælling på skærmen når en fulgt celle udløber
  (2m, 1m, 30s og countdown).
- **Armour HUD**: viser rustningens holdbarhed og advarer når den er lav.
- **Armour Skins**: forskellige teksturer på Protection 1-4 jern og diamant, så
  du kan kende dem fra hinanden uden en hel texture pack.
- **Prisguide**: bladr i FreakyVilles prisguide inde i spillet, hentet live.
- **Flip Case**: CS:GO-agtig case-åbning når du flipper, i stedet for
  flip-kisten.
- **Jernlåge Lyde**: afspiller dør-lyde når jernlåger åbnes og lukkes.
- **Opdatering**: auto-opdatering fra GitHub, med en valgfri test-kanal til
  pre-releases.

Tandhjulet i hubben åbner en HUD-editor, hvor du kan trække hvert HUD derhen
hvor du vil have det.

</details>

Mod-id'et hedder stadig `cellescanner` internt, så gammel config og gamle
gemte filer bliver ved med at virke.

## Byg selv

```
./gradlew clean build
```

Kræver Java 8. Jar-filen lander i `build/libs`.

## Licens

Koden er udgivet under [MIT-licensen](LICENSE). De medfølgende lydeffekter
tilhører deres respektive ejere og er kun med til personlig brug.
