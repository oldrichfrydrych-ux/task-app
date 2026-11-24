# Fáze 1 — rozhodnutí, která jsem udělal za tebe

24. 11. 2026. Během Fáze 1 jsem musel 39krát rozhodnout věc, kterou plán ani
`docs/design.md` neřešily, nebo kde si odporovaly. Rozhodoval jsem sám, abych tě nebudil
u každé maličkosti — ale rozhodnutí jsou tvoje věc, takže tady jsou všechna, s cenou za
to, kdyby bylo špatně. Většina je drobná. Hvězdičkou jsou ty, které bych ti ukázal,
i kdyby šel seznam zkrátit na pět.

Tenhle soubor je jen záznam. Kterékoliv z nich můžeš jednou větou zvrátit.

## Co uvidíš při používání

1. **\* Hotový úkol zmizí ze všech seznamů a vrátí ho jen „Vrátit" ve snackbaru.**
   Fáze 1 nemá nikde přehled hotových úkolů, takže řádky, které nic nedokáže zobrazit, by
   jen vytvářely nesoulad. *Cena:* dokončený úkol je do další fáze neviditelný.
2. **Hotové úkoly nenajde ani hledání.** Stejný důvod. *Cena:* dohledat dokončené nejde.
3. **\* Rozepsaný text v rychlém přidání se při zavření nezahodí.** Už dřív přežíval
   zabití aplikace, ale klepnutí mimo ho mazalo — jedno z toho je špatně a to, co ztrácí
   napsanou větu, je to špatné. Maže se jen po úspěšném uložení. *Cena:* rychlé přidání
   se někdy otevře předvyplněné; stačí říct a změním to.
4. **Počet u seznamu počítá jen úkoly první úrovně, ne podúkoly.** Jinak by číslo
   nesouhlasilo s tím, co po otevření seznamu vidíš. *Cena:* číslo říká méně práce, než
   seznam obsahuje.
5. **\* Smazání úkolu smaže i jeho podúkoly, a obnovení je vrátí zpět.** Podúkol
   osiřelého rodiče by nešel nikde zobrazit. *Cena:* mazání rodiče je ráznější, než možná
   čekáš.
6. **Vrácení dokončení vrátí i podúkoly, které se dokončily s rodičem.** Řeší se společnou
   časovou značkou. *Cena:* žádná; bez toho by „Vrátit" lhalo o tom, co vrátilo.
7. **Smazání úkolu se z aplikace vrátit nedá** (jen dokončení). Data v databázi zůstávají
   a obnovení je napsané, jen k němu nevede tlačítko; místo toho se ptá potvrzovací dialog.
   *Cena:* omyl v potvrzení je trvalý.
8. **Přejmenování nadpisu v seznamu jsem nechal postavit**, i když ho brief nechtěl. Bez
   něj by oprava překlepu znamenala nadpis smazat — a to vysype všechny jeho úkoly do
   volné části seznamu, odkud je Fáze 1 neumí zařadit zpět jinak než po jednom.
   *Cena:* jeden dialog navíc.
9. **Seznam lze přesunout do skupiny i při úpravě, ne jen při vytvoření.** Jinak by
   jediná cesta k reorganizaci byla seznam smazat a vytvořit znovu, což přesune všechny
   jeho úkoly do Inboxu. *Cena:* jedna metoda, kterou by jinak nic nevolalo.
10. **Inbox nelze přesunout do skupiny ani upravit.** Design to zakazuje a moje vlastní
    metoda z bodu 9 na to otevřela novou cestu. *Cena:* jedna pojistka a jeden test.
11. **Smazání seznamu smaže i jeho nadpisy a úkolům zruší zařazení**, než je přesune do
    Inboxu. Nadpis patří seznamu, nemůže ho přežít. *Cena:* žádná.
12. **\* Obrazovka Nastavení existuje, ale nikam z aplikace nevede.** Je to zkusmý
    vybírač barev, který si nic nepamatuje. Nastavení, které zapomíná, působí při denním
    používání jako rozbité — hůř než když tam není. *Cena:* jedna obrazovka, kterou
    nikdo neotevře, dokud nedostane co ukládat.
13. **Podúkol patří svému rodiči**, včetně seznamu a nadpisu, a sám je měnit nemůže.
    Google Tasks umí jen jednu úroveň. *Cena:* u podúkolu chybí vybírač seznamu.
14. **Barva textu v barvě seznamu je odvozený, tmavší odstín, ne surová barva z palety.**
    Tohle byla moje chyba, kterou našel až recenzent: schválil jsem pravidlo kontrastu,
    které bylo aritmeticky nesplnitelné (varovalo by u každé barvy včetně dobře čitelné
    tmavě modré). Odpověď byla celou dobu v `docs/design.md:173`. *Cena:* žádná, ale bez
    opravy by byla jména seznamů na řádcích úkolů nečitelná.

## Co se stane, až budeš chtít další fázi

15. **\* Fáze 2 nesmí vyjít bez skutečné `Migration(1,2)`.** Schéma je verze 1 a záměrně
    nemá „při změně smaž data". Jakákoli změna v tabulce bez migrace znamená, že nová
    verze aplikace po instalaci padá, dokud nevymažeš data — tedy přesně ten týden, pro
    který tahle fáze existuje. Podrobně v [phase-1-handover.md](phase-1-handover.md).
16. **\* Databáze je vyňatá ze zálohování Androidu.** Živý SQLite soubor zkopírovaný
    s nedokončeným WAL není bezpečná záloha a tiše dodat takovou je horší než žádná,
    protože ji začneš považovat za zálohu. *Cena:* porucha telefonu ten týden sebere
    zkušební data (tvoje opravdové úkoly jsou pořád v TickTicku).
17. **\* `lists` dostal `deletedAt`, `updatedAt` a dvě jedinečná `googleId` už teď**,
    i když je ve Fázi 1 nic nečte. Bez nich by tvrdé smazání seznamu nenechalo Fázi 4
    žádnou stopu a další synchronizace by seznam vzkřísila. *Cena:* čtyři nepoužité
    sloupce.
18. **Bez SQL cizích klíčů, jen indexy.** Design říká „přesuň, nekaskáduj", takže
    `CASCADE` by mu odporoval. *Cena:* osiřelé řádky, kdyby pozdější úkol smazal rodiče
    bez toho přesunu.
19. **Obnovení používá společnou časovou značku smazání.** Smazat podúkol a pak rodiče
    v téže milisekundě by obnovilo oba. Rozlišit to chce nový sloupec kvůli srážce, na
    kterou rukou nedosáhneš. Fáze 4 to má přehodnotit. *Cena:* záměrně smazaný podúkol
    se v nedosažitelném případě vrátí.

## Technické volby, které neuvidíš

20. Pracoval jsem na větvi `phase-1` v tomhle adresáři, ne v odděleném worktree — abys
    práci viděl v Android Studiu a mohl ji pouštět na telefon. *Cena:* jedno sloučení
    na konci (to je teď tvoje volba).
21. KSP `2.3.6` místo linie svázané s Kotlinem, která pod AGP 9.5.2 neprojde.
    *Cena:* možná aktualizace později.
22. Verze knihoven (room 2.8.4, navigation-compose 2.9.6, lifecycle 2.9.4) jsem přijal,
    protože Gradle je přeložil — což je ta kontrola. *Cena:* žádná pozorovatelná.
23. `update` přepisoval celý řádek z kopie, kterou držel ViewModel, a tím umel vzkřísit
    smazaný úkol. Přepsáno na zápis jen editovatelných sloupců. *Cena:* žádná.
24. Prázdný text místo barvy („zvol další z palety") jsem dvakrát zakázal — jednou
    v datové vrstvě, podruhé o úroveň výš v dialogu. Jeden typ se dvěma významy.
    *Cena:* jeden parametr smí být `null`.
25. `AppNavHost` přišel o parametr `selected`; vybranou kartu si bere z historie
    navigace. *Cena:* kdyby ho někdo potřeboval, přidá se zpět.
26. Dvě různé podoby „ještě nenačteno" (`isLoaded` u složeného stavu, `null` u seznamu)
    místo společné abstrakce kvůli třem místům. *Cena:* čtenář narazí na stejnou myšlenku
    dvakrát.
27. „Vrátit" zmizí, když během snackbaru odejdeš z obrazovky; dokončení zůstane. Tak se
    snackbary chovají všude. *Cena:* dokončení se musí vrátit ručně.
28. Bliknutí prázdného stavu při startu jsem nechal vyřešit o úkol později, aby to jeden
    člověk vyřešil pro všechny tři obrazovky. *Cena:* bliknutí přežilo o úkol dýl.
29. `rememberCompletionUndo` bere dvě lambdy místo jedné, protože vracení dokončení už
    nešlo vyjádřit jedním přepínačem. *Cena:* tři místa předávají dvě funkce.
30. Dvakrát jsem poslal `TaskRow` na další kolo oprav: nejdřív proto, že testy používaly
    krátká anglická slova a česká dlouhá („zkontrolovat") se lámala uvnitř slova, pak
    proto, že první oprava se spouštěla podle velikosti písma, ne podle skutečné šířky —
    a svou vlastní hranicí tu chybu zase vyráběla. *Cena:* dvě kola na komponentě, kterou
    kreslí čtyři obrazovky.
31. Rozdělanou práci na vybírači barev (asi 260 řádků, nikdy nepřeložených) jsem zahodil
    a úkol začal znovu, protože test a implementace napsané společně nedovolí test
    poctivě vidět padat — a v tomhle projektu se už sedm testů ukázalo jako takových,
    které nemohly selhat. *Cena:* zahozená práce.
32. Pomocné testovací vkládání přes surové SQL jsem nechal nahradit skutečným DAO,
    protože se rozbije při každé změně schématu. *Cena:* jeden soubor nad rámec briefu.
33. Osm závažných nálezů z finální recenze jsem nechal opravit v jedné vlně ještě před
    tvým týdnem, místo odkládání. *Cena:* delší poslední commit.
34. Šest menších úprav plánu, kde si plán odporoval sám se sebou: který úkol vlastní
    dotaz na počty otevřených úkolů, kam patří test vracení dokončení, který soubor nese
    ikonu hledání, a jak se dialog úpravy seznamu dělí mezi dva úkoly, které ho oba píšou.
    *Cena:* žádná; bez nich by pozdější úkol selhal na cizím kódu.
