package cz.vcely.zapisnik.tasks

/**
 * Orientační přehled typických včelařských prací podle měsíce v roce
 * (obecný kalendář pro mírné pásmo, ČR). Slouží jako připomenutí, ne jako
 * závazný návod – konkrétní termíny se liší podle počasí a lokality.
 */
object SeasonalTasks {

    val monthNames = listOf(
        "Leden", "Únor", "Březen", "Duben", "Květen", "Červen",
        "Červenec", "Srpen", "Září", "Říjen", "Listopad", "Prosinec"
    )

    val tasks: Map<Int, List<String>> = mapOf(
        1 to listOf(
            "Klid včelstev – zbytečně neotvírat úly",
            "Kontrola zásob nadzvednutím úlu (odhad váhy)",
            "Kontrola náletových otvorů – průchodnost, hrabošci, sníh",
            "Kontrola stanoviště po větru/sněhu, zajištění proti poškození",
            "Příprava a oprava rámků, mezistěn a nářadí na jarní sezónu",
        ),
        2 to listOf(
            "Klid včelstev, jen nutné kontroly zásob",
            "V teplejších dnech možnost prvního prolétnutí – sledovat čistírnu",
            "Dokoupení/výroba mezistěn a rámků na novou sezónu",
            "Příprava podložek pro jarní sledování spadu/náletu",
            "Objednávka léčiv a pomůcek na jaro",
        ),
        3 to listOf(
            "První jarní prohlídka v teplém bezvětrném dnu",
            "Kontrola síly včelstva, matky a zásob",
            "Odstranění uhynulých včel a vlhké podložky z dna úlu",
            "Zúžení prostoru na sílu včelstva, případné sloučení slabých včelstev",
            "Podnětové krmení, pokud chybí zásoby",
        ),
        4 to listOf(
            "Pravidelné jarní prohlídky (á 7–10 dní)",
            "Rozšiřování díla – přidávání mezistěn podle síly včelstva",
            "Kontrola plodu (zavíčkovaný i otevřený), přítomnosti matky",
            "Prevence a kontrola rojové nálady",
            "Čištění/výměna beznástavkových den, desinfekce starých rámků",
        ),
        5 to listOf(
            "Intenzivní rojová sezóna – pravidelná kontrola rojových matečníků",
            "Tvorba oddělků, případná výměna matek",
            "Přidávání medníků při silném náletu",
            "Kontrola prostoru pro kladení – zabránění zalehnutí plodiště medem",
            "Sledování zdravotního stavu (hniloba, křídla, trus)",
        ),
        6 to listOf(
            "Hlavní snůška – pravidelné sledování plnosti medníků",
            "První jarní medobraní dle plnosti a vyzrálosti medu",
            "Péče o mladé matky a oddělky založené v květnu",
            "Pokračující kontrola rojové nálady",
            "Příprava na léčení (výpočet termínů dle snůšky)",
        ),
        7 to listOf(
            "Medobraní z hlavní snůšky, vytáčení a zpracování medu",
            "Sledování konce snůšky, případně podletní snůšky (lípa, medovice)",
            "Kontrola zdravotního stavu a spadu varroázy",
            "Slučování slabých včelstev a oddělků",
            "Příprava na letní léčení proti varroáze",
        ),
        8 to listOf(
            "Léčení proti varroáze po posledním medobraní",
            "Zahájení zazimovacího krmení (cukerný sirup)",
            "Kontrola síly a zdraví včelstev před zimou",
            "Výměna/omlazení matek u slabších včelstev",
            "Zúžení prostoru úlu na zimní sílu",
        ),
        9 to listOf(
            "Dokončení zazimovacího krmení",
            "Kontrola zásob (cíl cca 15–20 kg na včelstvo dle oblasti)",
            "Doléčení/kontrolní léčení proti varroáze",
            "Sjednocení slabých včelstev, vyřazení neperspektivních",
            "Úprava a utěsnění úlů proti chladu a hlodavcům",
        ),
        10 to listOf(
            "Poslední kontrola zásob a síly včelstva před zazimováním",
            "Instalace mších/ochranných mřížek proti hlodavcům",
            "Zúžení/odstranění nadbytečných nástavků",
            "Kontrola a případné doléčení (aerosol/kyselina šťavelová bez plodu)",
            "Úklid a dezinfekce nepoužívaného nářadí a rámků",
        ),
        11 to listOf(
            "Zimní klid – minimální zásahy",
            "Kontrola náletových otvorů a ochrany proti vlhku a hlodavcům",
            "Léčení kyselinou šťavelovou (bezplodové období)",
            "Kontrola upevnění střech a ochrany stanoviště před větrem",
            "Plánování a nákup pomůcek na příští sezónu",
        ),
        12 to listOf(
            "Zimní klid – včelstva nerušit",
            "Příležitostná kontrola spadu na podložce (zdraví, aktivita)",
            "Údržba a oprava nářadí, výroba rámků na jaro",
            "Kontrola stanoviště po sněhu a větru",
            "Bilance uplynulé sezóny a plán na další rok",
        ),
    )
}
