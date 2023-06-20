# Instrukcja obsługi systemu
1. Stwórz model w języku *AADL*:
   1. Uruchom aplikację *Osate2* i stwórz model,
   2. Wygeneruj diagram modelu,
   3. Stwórz instancję modelu w formacie *AAXL2*.
2. Uruchom aplikację translatora języka *AADL* do kolorowanych sieci Petriego:
   - poprzez wiersz poleceń (z użyciem pliku *jar*)
   ```
   java -jar TranslatorAADLtoPetriNet.jar
   ```
   - poprzez środowisko programistyczne
3. Wcisnij przycisk *Choose file* w oknie dialogowym i wybierz wygenerowany plik *AAXL2*,
4. Uruchom aplikację *CPN Tools* i otwórz plik *generatedPetriNetFile.xml* znajdujący się
   w tej samej lokalizacji co plik *AAXL2*.
