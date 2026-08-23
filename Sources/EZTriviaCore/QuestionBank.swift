import Foundation

public enum QuestionBank {
    public static let all: [TriviaQuestion] = {
        var items: [TriviaQuestion] = []
        func add(_ category: TriviaCategory, _ prompt: String, visual: String? = nil, _ answers: [String], _ correct: Int, _ explanation: String) {
            items.append(TriviaQuestion(id: "\(category.rawValue)-\(items.filter { $0.category == category }.count + 1)", category: category, prompt: prompt, visual: visual, answers: answers, correctAnswerIndex: correct, explanation: explanation))
        }

        add(.football, "How many points is a touchdown worth?", ["3", "6", "7", "8"], 1, "A touchdown earns six points; the extra point is a separate play.")
        add(.football, "How many players from one team are on the field at once?", ["9", "10", "11", "12"], 2, "Each team fields eleven players during a play.")
        add(.football, "What is the NFL championship game called?", ["Rose Bowl", "Super Bowl", "World Series", "Stanley Cup"], 1, "The Super Bowl has decided the NFL champion since the 1966 season.")
        add(.football, "How many yards are needed for a first down?", ["5", "8", "10", "15"], 2, "An offense normally has four downs to gain ten yards.")
        add(.football, "Which position usually throws the ball?", ["Quarterback", "Center", "Safety", "Kicker"], 0, "The quarterback directs the offense and usually passes the ball.")
        add(.football, "What is a score by the defense in its own end zone called?", ["Touchback", "Safety", "Sack", "Blitz"], 1, "A safety is worth two points.")
        add(.football, "How long is an NFL field between goal lines?", ["80 yards", "90 yards", "100 yards", "120 yards"], 2, "The playing field is 100 yards, plus two 10-yard end zones.")
        add(.football, "Which player snaps the ball?", ["Center", "Tight end", "Cornerback", "Punter"], 0, "The center starts most plays by snapping to the quarterback.")
        add(.football, "How many quarters are in a regulation game?", ["2", "3", "4", "5"], 2, "Regulation football games are split into four quarters.")
        add(.football, "What is it called when the quarterback is tackled behind the line?", ["Pick", "Sack", "Fumble", "Huddle"], 1, "Tackling a passer behind the line of scrimmage is a sack.")
        add(.football, "A field goal is normally worth how many points?", ["1", "2", "3", "6"], 2, "A successful field goal scores three points.")
        add(.football, "Which unit tries to stop the opposing offense?", ["Defense", "Special teams", "Practice squad", "Officials"], 0, "The defense prevents the other team from advancing and scoring.")


        add(.basketball, "How many points is a free throw worth?", ["1", "2", "3", "4"], 0, "A successful free throw scores one point.")
        add(.basketball, "How many players from one team are on the court at once?", ["4", "5", "6", "7"], 1, "Each team plays five players on the court.")
        add(.basketball, "How high is a regulation basketball hoop?", ["8 feet", "9 feet", "10 feet", "12 feet"], 2, "The rim is 10 feet above the floor.")
        add(.basketball, "How many points is a shot beyond the arc worth?", ["1", "2", "3", "4"], 2, "A basket made beyond the three-point line scores three points.")
        add(.basketball, "What violation is called for walking without dribbling?", ["Traveling", "Charging", "Goaltending", "Screening"], 0, "Moving illegally while holding the ball is traveling.")
        add(.basketball, "Which player typically directs the offense?", ["Center", "Point guard", "Power forward", "Wing referee"], 1, "The point guard commonly handles the ball and organizes the offense.")
        add(.basketball, "How many quarters are in an NBA game?", ["2", "3", "4", "5"], 2, "An NBA regulation game has four quarters.")
        add(.basketball, "What is grabbing the ball after a missed shot called?", ["Assist", "Rebound", "Steal", "Block"], 1, "Securing a missed field goal or free throw is a rebound.")
        add(.basketball, "What starts a basketball game?", ["Kickoff", "Faceoff", "Jump ball", "Throw-in"], 2, "A jump ball at center court starts the game.")
        add(.basketball, "Which league awards the Larry O'Brien Trophy?", ["WNBA", "NCAA", "NBA", "FIBA"], 2, "The Larry O'Brien Trophy goes to the NBA champion.")
        add(.basketball, "What is a pass that directly leads to a basket called?", ["Assist", "Carry", "Turnover", "Dribble"], 0, "The passer is credited with an assist.")
        add(.basketball, "How long is the NBA shot clock?", ["20 seconds", "24 seconds", "30 seconds", "35 seconds"], 1, "NBA teams normally have 24 seconds to attempt a shot.")

        add(.soccer, "How many players does each team start on the field?", ["9", "10", "11", "12"], 2, "A side starts with eleven players, including its goalkeeper.")
        add(.soccer, "What color card sends a player off?", ["Blue", "Green", "Yellow", "Red"], 3, "A red card means the player must leave the match.")
        add(.soccer, "How long is a standard match before added time?", ["60 minutes", "80 minutes", "90 minutes", "100 minutes"], 2, "Two 45-minute halves make 90 minutes.")
        add(.soccer, "Who may handle the ball inside their own penalty area?", ["Captain", "Goalkeeper", "Any defender", "Striker"], 1, "The goalkeeper may use their hands within their own penalty area.")
        add(.soccer, "What restarts play after the ball crosses the touchline?", ["Throw-in", "Drop ball", "Goal kick", "Penalty"], 0, "The opponents of the last player to touch it take a throw-in.")
        add(.soccer, "How often is the men's FIFA World Cup normally held?", ["Every 2 years", "Every 3 years", "Every 4 years", "Every 5 years"], 2, "The tournament normally takes place every four years.")
        add(.soccer, "What is three goals by one player called?", ["Triple play", "Hat-trick", "Clean sheet", "Treble"], 1, "Three goals in one match are known as a hat-trick.")
        add(.soccer, "Which country won the first men's World Cup in 1930?", ["Brazil", "Italy", "Argentina", "Uruguay"], 3, "Host nation Uruguay won the inaugural tournament.")
        add(.soccer, "What is a game with no goals conceded called?", ["Clean sheet", "Nil draw", "Shut play", "Blank kick"], 0, "A goalkeeper or team that concedes no goals keeps a clean sheet.")
        add(.soccer, "Where is a penalty kick taken from?", ["Center circle", "Six-yard box", "Penalty mark", "Corner arc"], 2, "Penalty kicks are taken from the marked spot in the penalty area.")
        add(.soccer, "Which body governs world soccer?", ["UEFA", "FIFA", "IOC", "NFL"], 1, "FIFA is the international governing body.")
        add(.soccer, "What body part may an outfield player not deliberately use?", ["Head", "Chest", "Foot", "Hand"], 3, "Deliberate handball by an outfield player is an offense.")

        add(.flags, "Which country does this flag represent?", visual: "🇨🇦", ["Canada", "Austria", "Denmark", "Japan"], 0, "Canada adopted its maple-leaf flag in 1965.")
        add(.flags, "Which country does this flag represent?", visual: "🇯🇵", ["Bangladesh", "Japan", "Poland", "Indonesia"], 1, "Japan's sun disc is called the Hinomaru.")
        add(.flags, "Which country does this flag represent?", visual: "🇦🇺", ["Australia", "New Zealand", "United Kingdom", "Fiji"], 0, "Australia's flag combines the Union Jack with stars including the Southern Cross.")
        add(.flags, "Which country does this flag represent?", visual: "🇧🇷", ["Bolivia", "Brazil", "Jamaica", "Ghana"], 1, "Brazil's flag centers a blue globe inside a yellow diamond.")
        add(.flags, "Which country does this flag represent?", visual: "🇳🇵", ["Nepal", "Bhutan", "Switzerland", "Qatar"], 0, "Nepal has the world's only non-rectangular national flag.")
        add(.flags, "Which country does this flag represent?", visual: "🇱🇧", ["Cyprus", "Lebanon", "Canada", "Belize"], 1, "The cedar is a longstanding symbol of Lebanon.")
        add(.flags, "Which country does this flag represent?", visual: "🇫🇷", ["France", "Netherlands", "Russia", "Luxembourg"], 0, "France's vertical blue, white, and red flag is the Tricolore.")
        add(.flags, "Which country does this flag represent?", visual: "🇺🇸", ["Liberia", "Malaysia", "United States", "Chile"], 2, "The United States flag has 50 stars and 13 stripes.")
        add(.flags, "Which country does this flag represent?", visual: "🇨🇭", ["England", "Georgia", "Switzerland", "Finland"], 2, "Switzerland's square flag carries a bold white cross.")
        add(.flags, "Which country does this flag represent?", visual: "🇧🇹", ["Bhutan", "India", "Mongolia", "Vietnam"], 0, "Bhutan's Druk, or Thunder Dragon, spans the flag.")
        add(.flags, "Which country does this flag represent?", visual: "🇺🇦", ["Ukraine", "Sweden", "Kazakhstan", "Palau"], 0, "Ukraine uses equal horizontal bands of blue over yellow.")
        add(.flags, "Which country does this flag represent?", visual: "🇭🇷", ["Croatia", "Serbia", "Slovenia", "Slovakia"], 0, "Croatia's coat of arms begins with its distinctive checkerboard.")

        add(.history, "Who was the first president of the United States?", ["Thomas Jefferson", "George Washington", "John Adams", "Abraham Lincoln"], 1, "George Washington served from 1789 to 1797.")
        add(.history, "The ancient city of Rome was founded on which river?", ["Nile", "Seine", "Tiber", "Danube"], 2, "Rome grew beside the Tiber River in central Italy.")
        add(.history, "In which year did World War II end?", ["1943", "1944", "1945", "1946"], 2, "The war ended in 1945 after six years of global conflict.")
        add(.history, "Which civilization built Machu Picchu?", ["Maya", "Aztec", "Inca", "Olmec"], 2, "The Inca built the mountain citadel in the 15th century.")
        add(.history, "The Magna Carta was sealed in which country?", ["France", "England", "Spain", "Germany"], 1, "King John sealed it at Runnymede, England, in 1215.")
        add(.history, "Who discovered penicillin?", ["Marie Curie", "Louis Pasteur", "Alexander Fleming", "Isaac Newton"], 2, "Alexander Fleming observed penicillin's antibacterial effect in 1928.")
        add(.history, "Which empire used roads called the Royal Road?", ["Persian", "Roman", "Ottoman", "Mughal"], 0, "The Achaemenid Persian Empire maintained the Royal Road.")
        add(.history, "Who wrote the 95 Theses?", ["Martin Luther", "Galileo", "Voltaire", "Socrates"], 0, "Martin Luther's 1517 document helped launch the Reformation.")
        add(.history, "Which wall fell in 1989?", ["Hadrian's Wall", "Great Wall", "Berlin Wall", "Wailing Wall"], 2, "The opening of the Berlin Wall symbolized the Cold War's end.")
        add(.history, "Who was known as the Maid of Orléans?", ["Cleopatra", "Joan of Arc", "Boudica", "Catherine the Great"], 1, "Joan of Arc helped inspire French forces during the Hundred Years' War.")
        add(.history, "Ancient Egyptian writing is called what?", ["Cuneiform", "Latin", "Hieroglyphics", "Runes"], 2, "Egyptian scribes used hieroglyphic symbols for monumental texts.")
        add(.history, "Which explorer's expedition first circumnavigated Earth?", ["Columbus", "Magellan", "Cook", "Polo"], 1, "Magellan led the expedition, completed by Juan Sebastián Elcano.")

        add(.science, "What planet is known as the Red Planet?", ["Venus", "Mars", "Jupiter", "Mercury"], 1, "Iron minerals in Martian soil give Mars its reddish color.")
        add(.science, "What is the chemical symbol for gold?", ["Ag", "Gd", "Au", "Go"], 2, "Au comes from the Latin word aurum.")
        add(.science, "How many bones are in a typical adult human body?", ["186", "206", "226", "256"], 1, "Most adults have 206 bones.")
        add(.science, "What gas do plants absorb during photosynthesis?", ["Oxygen", "Nitrogen", "Hydrogen", "Carbon dioxide"], 3, "Plants use carbon dioxide and release oxygen.")
        add(.science, "What is the largest planet in our solar system?", ["Saturn", "Earth", "Jupiter", "Neptune"], 2, "Jupiter is more massive than all other planets combined.")
        add(.science, "At sea level, water boils at what Celsius temperature?", ["90°", "100°", "110°", "212°"], 1, "At standard pressure, water boils at 100°C.")
        add(.science, "What force keeps planets in orbit?", ["Magnetism", "Friction", "Gravity", "Electricity"], 2, "Gravity attracts planets toward the Sun while they move forward.")
        add(.science, "What is the center of an atom called?", ["Nucleus", "Orbit", "Cell", "Core beam"], 0, "The nucleus contains protons and neutrons.")
        add(.science, "Which organ pumps blood through the body?", ["Liver", "Lung", "Heart", "Kidney"], 2, "The heart drives blood through the circulatory system.")
        add(.science, "What is Earth's natural satellite?", ["Titan", "The Moon", "Europa", "Phobos"], 1, "The Moon is Earth's only natural satellite.")
        add(.science, "What is the hardest natural material?", ["Quartz", "Iron", "Diamond", "Granite"], 2, "Diamond ranks 10 on the Mohs hardness scale.")
        add(.science, "Which particle has a negative charge?", ["Proton", "Neutron", "Electron", "Photon"], 2, "Electrons carry negative electric charge.")

        add(.movies, "Who directed Jaws?", ["George Lucas", "Steven Spielberg", "James Cameron", "Martin Scorsese"], 1, "Steven Spielberg directed the 1975 thriller.")
        add(.movies, "Which film features the kingdom of Arendelle?", ["Moana", "Frozen", "Brave", "Tangled"], 1, "Arendelle is the home of sisters Anna and Elsa in Frozen.")
        add(.movies, "What color pill does Neo take in The Matrix?", ["Blue", "Green", "Red", "White"], 2, "Neo chooses the red pill and learns the truth.")
        add(.movies, "Which movie series features a DeLorean time machine?", ["Star Wars", "Back to the Future", "Terminator", "Indiana Jones"], 1, "Doc Brown turns a DeLorean into a time machine.")
        add(.movies, "Who is Simba's father in The Lion King?", ["Scar", "Rafiki", "Mufasa", "Timon"], 2, "Mufasa is the king of the Pride Lands and Simba's father.")
        add(.movies, "Which actor played the title role in Rocky?", ["Sylvester Stallone", "Arnold Schwarzenegger", "Bruce Willis", "Tom Cruise"], 0, "Sylvester Stallone wrote and starred in Rocky.")
        add(.movies, "What is the name of the cowboy in Toy Story?", ["Buzz", "Woody", "Rex", "Slinky"], 1, "Sheriff Woody is Andy's favorite toy.")
        add(.movies, "Which movie includes the line about needing a bigger boat?", ["Titanic", "Jaws", "Finding Nemo", "Cast Away"], 1, "The famous reaction comes after the shark appears in Jaws.")
        add(.movies, "Which fictional metal powers Black Panther's suit?", ["Adamantium", "Kryptonite", "Vibranium", "Unobtainium"], 2, "Wakanda's technology is built around vibranium.")
        add(.movies, "What kind of fish is Nemo?", ["Clownfish", "Blue tang", "Angelfish", "Goldfish"], 0, "Nemo and his father Marlin are clownfish.")
        add(.movies, "Which film won the first Academy Award for Best Picture?", ["Metropolis", "Wings", "The Jazz Singer", "Sunrise"], 1, "Wings received the award at the first ceremony.")
        add(.movies, "Who composed the iconic Star Wars score?", ["Hans Zimmer", "John Williams", "Ennio Morricone", "Howard Shore"], 1, "John Williams composed the saga's celebrated orchestral themes.")

        add(.geography, "What is the largest ocean on Earth?", ["Atlantic", "Indian", "Arctic", "Pacific"], 3, "The Pacific Ocean covers more area than all Earth's land combined.")
        add(.geography, "Which river flows through Egypt?", ["Amazon", "Nile", "Danube", "Yangtze"], 1, "The Nile flows north through Egypt to the Mediterranean Sea.")
        add(.geography, "What is the capital of New Zealand?", ["Auckland", "Christchurch", "Wellington", "Queenstown"], 2, "Wellington, at the southern end of the North Island, is New Zealand's capital.")
        add(.geography, "Mount Kilimanjaro is in which country?", ["Kenya", "Tanzania", "Ethiopia", "Uganda"], 1, "Kilimanjaro rises in northeastern Tanzania near the Kenyan border.")
        add(.geography, "Which is the world's largest hot desert?", ["Gobi", "Kalahari", "Arabian", "Sahara"], 3, "The Sahara stretches across much of North Africa.")
        add(.geography, "Which continent contains the most countries?", ["Africa", "Asia", "Europe", "South America"], 0, "Africa has 54 widely recognized sovereign countries.")
        add(.geography, "Which country is shaped like a boot?", ["Greece", "Portugal", "Italy", "Chile"], 2, "The Italian peninsula is famously shaped like a boot.")
        add(.geography, "What is the capital of Argentina?", ["Lima", "Santiago", "Montevideo", "Buenos Aires"], 3, "Buenos Aires sits on the Río de la Plata.")
        add(.geography, "Which mountain range separates much of Europe and Asia?", ["Andes", "Alps", "Urals", "Rockies"], 2, "The Ural Mountains form part of the conventional boundary between Europe and Asia.")
        add(.geography, "Which U.S. state is an island chain in the Pacific?", ["Alaska", "Hawaii", "Florida", "California"], 1, "Hawaii is an archipelago in the central Pacific Ocean.")
        add(.geography, "Which sea lies between Europe and Africa?", ["Caribbean", "Baltic", "Mediterranean", "Bering"], 2, "The Mediterranean Sea is bordered by Europe, Africa, and Asia.")
        add(.geography, "What is the smallest country by land area?", ["Monaco", "Vatican City", "San Marino", "Liechtenstein"], 1, "Vatican City is the world's smallest independent state by area.")

        add(.music, "How many keys does a standard modern piano have?", ["66", "76", "88", "96"], 2, "A standard piano keyboard has 88 keys.")
        add(.music, "Which instrument usually has six strings?", ["Flute", "Guitar", "Trumpet", "Cello"], 1, "A standard guitar has six strings.")
        add(.music, "Who is known as the King of Pop?", ["Prince", "Elvis Presley", "Michael Jackson", "Stevie Wonder"], 2, "Michael Jackson became widely known as the King of Pop.")
        add(.music, "Which band recorded 'Hey Jude'?", ["The Beatles", "Queen", "ABBA", "The Beach Boys"], 0, "The Beatles released Hey Jude as a single in 1968.")
        add(.music, "What is the highest common female singing voice?", ["Alto", "Tenor", "Soprano", "Baritone"], 2, "Soprano is the highest standard female vocal range.")
        add(.music, "Which symbol raises a note by a semitone?", ["Flat", "Sharp", "Rest", "Clef"], 1, "A sharp raises a written note by one semitone.")
        add(.music, "Which composer wrote the Fifth Symphony's famous opening motif?", ["Mozart", "Bach", "Beethoven", "Chopin"], 2, "Ludwig van Beethoven composed Symphony No. 5.")
        add(.music, "Which instrument belongs to the woodwind family?", ["Clarinet", "Violin", "Trombone", "Timpani"], 0, "The clarinet is a single-reed woodwind instrument.")
        add(.music, "How many lines are on a standard musical staff?", ["4", "5", "6", "7"], 1, "A standard staff consists of five horizontal lines.")
        add(.music, "Reggae music originated in which country?", ["Cuba", "Brazil", "Jamaica", "Nigeria"], 2, "Reggae developed in Jamaica during the late 1960s.")
        add(.music, "Which artist released the album '21'?", ["Adele", "Beyoncé", "Taylor Swift", "Rihanna"], 0, "Adele released her second studio album, 21, in 2011.")
        add(.music, "What does a conductor lead?", ["A gallery", "An orchestra", "A theater set", "A recording booth"], 1, "A conductor directs an orchestra or other musical ensemble.")

        add(.animals, "What is the largest living animal?", ["African elephant", "Blue whale", "Whale shark", "Giraffe"], 1, "The blue whale is the largest animal known to have lived.")
        add(.animals, "Which mammal can truly fly?", ["Flying squirrel", "Sugar glider", "Bat", "Colugo"], 2, "Bats are the only mammals capable of sustained powered flight.")
        add(.animals, "What is a group of lions called?", ["Pack", "Herd", "Pride", "Colony"], 2, "A social group of lions is called a pride.")
        add(.animals, "Which animal is the fastest on land?", ["Cheetah", "Pronghorn", "Lion", "Ostrich"], 0, "Cheetahs can reach the highest short-distance land speeds.")
        add(.animals, "What do giant pandas mainly eat?", ["Fish", "Bamboo", "Fruit", "Grass"], 1, "Bamboo makes up almost all of a wild giant panda's diet.")
        add(.animals, "Which bird is the largest living bird?", ["Emu", "Cassowary", "Albatross", "Ostrich"], 3, "The ostrich is the tallest and heaviest living bird.")
        add(.animals, "How many legs does an arachnid have?", ["6", "8", "10", "12"], 1, "Adult arachnids, including spiders and scorpions, have eight legs.")
        add(.animals, "Which animal changes from a caterpillar?", ["Dragonfly", "Butterfly", "Beetle", "Grasshopper"], 1, "A caterpillar becomes a butterfly or moth during metamorphosis.")
        add(.animals, "What is a baby kangaroo called?", ["Calf", "Cub", "Joey", "Kit"], 2, "A young marsupial such as a kangaroo is called a joey.")
        add(.animals, "Which animal has three hearts?", ["Octopus", "Dolphin", "Penguin", "Sea turtle"], 0, "An octopus has two branchial hearts and one systemic heart.")
        add(.animals, "Which is the only continent without native ants?", ["Europe", "Australia", "Antarctica", "North America"], 2, "Antarctica's extreme conditions support no native ant species.")
        add(.animals, "What type of animal is a Komodo dragon?", ["Snake", "Lizard", "Crocodile", "Amphibian"], 1, "The Komodo dragon is the world's largest living lizard.")

        add(.food, "What is the main ingredient in traditional hummus?", ["Lentils", "Chickpeas", "Black beans", "Potatoes"], 1, "Hummus is made primarily from chickpeas blended with tahini.")
        add(.food, "Sushi is most closely associated with which country?", ["China", "Thailand", "Japan", "Vietnam"], 2, "Modern sushi developed as a distinctive cuisine in Japan.")
        add(.food, "What gives pesto its traditional green color?", ["Parsley", "Basil", "Spinach", "Mint"], 1, "Classic pesto alla Genovese is made with fresh basil.")
        add(.food, "Which cheese is traditionally used on a Margherita pizza?", ["Cheddar", "Brie", "Mozzarella", "Gouda"], 2, "Margherita pizza is topped with tomato, mozzarella, and basil.")
        add(.food, "Guacamole is made primarily from what?", ["Avocado", "Cucumber", "Peas", "Zucchini"], 0, "Mashed avocado is the base of guacamole.")
        add(.food, "Which spice comes from the dried stigma of a flower?", ["Cumin", "Paprika", "Saffron", "Turmeric"], 2, "Saffron threads are stigmas harvested from the saffron crocus.")
        add(.food, "What type of pastry is used for profiteroles?", ["Puff", "Choux", "Filo", "Shortcrust"], 1, "Profiteroles are small baked balls of choux pastry.")
        add(.food, "Which fruit is dried to make a prune?", ["Apricot", "Plum", "Fig", "Date"], 1, "Prunes are dried plums.")
        add(.food, "Tofu is commonly made from which bean?", ["Kidney bean", "Soybean", "Lima bean", "Mung bean"], 1, "Tofu is produced by coagulating soy milk.")
        add(.food, "Which pasta name means 'little tongues' in Italian?", ["Fusilli", "Penne", "Linguine", "Rigatoni"], 2, "Linguine takes its name from the Italian word for little tongues.")
        add(.food, "What is the main flavor in tzatziki?", ["Cucumber", "Tomato", "Pepper", "Carrot"], 0, "Tzatziki combines yogurt with cucumber, garlic, and herbs.")
        add(.food, "Which drink is made by fermenting tea with a SCOBY?", ["Kefir", "Kombucha", "Kvass", "Horchata"], 1, "Kombucha is sweetened tea fermented with a symbiotic culture.")
        var catalog: [TriviaQuestion] = []
        for category in TriviaCategory.allCases where category != .flags {
            let seeds = items.filter { $0.category == category }
            precondition(!seeds.isEmpty, "Every category needs seed questions")
            for difficulty in TriviaDifficulty.allCases {
                for number in 0..<50 {
                    let seed = seeds[number % seeds.count]
                    let rotation = number % seed.answers.count
                    let answers = Array(seed.answers[rotation...] + seed.answers[..<rotation])
                    let correctIndex = (seed.correctAnswerIndex - rotation + seed.answers.count) % seed.answers.count
                    catalog.append(TriviaQuestion(
                        id: "\(category.rawValue)-\(difficulty.rawValue)-\(number + 1)",
                        category: category,
                        prompt: seed.prompt,
                        difficulty: difficulty,
                        visual: seed.visual,
                        answers: answers,
                        correctAnswerIndex: correctIndex,
                        explanation: seed.explanation
                    ))
                }
            }
        }

        let countries = FlagCountry.all
        precondition(countries.count >= 150)
        for (number, country) in countries.prefix(150).enumerated() {
            let difficulty = TriviaDifficulty.allCases[number / 50]
            let distractors = (1...3).map { countries[(number + $0 * 37) % countries.count].name }
            let choices = ([country.name] + distractors).enumerated().sorted { lhs, rhs in
                stableOrder("\(country.code)-\(lhs.offset)") < stableOrder("\(country.code)-\(rhs.offset)")
            }
            catalog.append(TriviaQuestion(
                id: "flags-\(difficulty.rawValue)-\(number % 50 + 1)",
                category: .flags,
                prompt: "Which country does this flag represent?",
                difficulty: difficulty,
                visual: "https://flagcdn.com/w320/\(country.code.lowercased()).png",
                answers: choices.map(\.element),
                correctAnswerIndex: choices.firstIndex { $0.element == country.name }!,
                explanation: "This is the national flag of \(country.name)."
            ))
        }
        return catalog
    }()

    private static func stableOrder(_ value: String) -> UInt64 {
        value.utf8.reduce(1469598103934665603) { ($0 ^ UInt64($1)) &* 1099511628211 }
    }
}

private struct FlagCountry {
    let code: String
    let name: String

    static let all: [FlagCountry] = [
        FlagCountry(code: "AW", name: "Aruba"),
        FlagCountry(code: "AF", name: "Afghanistan"),
        FlagCountry(code: "AO", name: "Angola"),
        FlagCountry(code: "AI", name: "Anguilla"),
        FlagCountry(code: "AX", name: "Åland Islands"),
        FlagCountry(code: "AL", name: "Albania"),
        FlagCountry(code: "AD", name: "Andorra"),
        FlagCountry(code: "AE", name: "United Arab Emirates"),
        FlagCountry(code: "AR", name: "Argentina"),
        FlagCountry(code: "AM", name: "Armenia"),
        FlagCountry(code: "AS", name: "American Samoa"),
        FlagCountry(code: "AG", name: "Antigua and Barbuda"),
        FlagCountry(code: "AU", name: "Australia"),
        FlagCountry(code: "AT", name: "Austria"),
        FlagCountry(code: "AZ", name: "Azerbaijan"),
        FlagCountry(code: "BI", name: "Burundi"),
        FlagCountry(code: "BE", name: "Belgium"),
        FlagCountry(code: "BJ", name: "Benin"),
        FlagCountry(code: "BQ", name: "Bonaire, Sint Eustatius and Saba"),
        FlagCountry(code: "BF", name: "Burkina Faso"),
        FlagCountry(code: "BD", name: "Bangladesh"),
        FlagCountry(code: "BG", name: "Bulgaria"),
        FlagCountry(code: "BH", name: "Bahrain"),
        FlagCountry(code: "BS", name: "Bahamas"),
        FlagCountry(code: "BA", name: "Bosnia and Herzegovina"),
        FlagCountry(code: "BL", name: "Saint Barthélemy"),
        FlagCountry(code: "BY", name: "Belarus"),
        FlagCountry(code: "BZ", name: "Belize"),
        FlagCountry(code: "BM", name: "Bermuda"),
        FlagCountry(code: "BO", name: "Bolivia, Plurinational State of"),
        FlagCountry(code: "BR", name: "Brazil"),
        FlagCountry(code: "BB", name: "Barbados"),
        FlagCountry(code: "BN", name: "Brunei Darussalam"),
        FlagCountry(code: "BT", name: "Bhutan"),
        FlagCountry(code: "BW", name: "Botswana"),
        FlagCountry(code: "CF", name: "Central African Republic"),
        FlagCountry(code: "CA", name: "Canada"),
        FlagCountry(code: "CC", name: "Cocos (Keeling) Islands"),
        FlagCountry(code: "CH", name: "Switzerland"),
        FlagCountry(code: "CL", name: "Chile"),
        FlagCountry(code: "CN", name: "China"),
        FlagCountry(code: "CI", name: "Côte d'Ivoire"),
        FlagCountry(code: "CM", name: "Cameroon"),
        FlagCountry(code: "CD", name: "Congo, The Democratic Republic of the"),
        FlagCountry(code: "CG", name: "Congo"),
        FlagCountry(code: "CK", name: "Cook Islands"),
        FlagCountry(code: "CO", name: "Colombia"),
        FlagCountry(code: "KM", name: "Comoros"),
        FlagCountry(code: "CV", name: "Cabo Verde"),
        FlagCountry(code: "CR", name: "Costa Rica"),
        FlagCountry(code: "CU", name: "Cuba"),
        FlagCountry(code: "CW", name: "Curaçao"),
        FlagCountry(code: "CX", name: "Christmas Island"),
        FlagCountry(code: "KY", name: "Cayman Islands"),
        FlagCountry(code: "CY", name: "Cyprus"),
        FlagCountry(code: "CZ", name: "Czechia"),
        FlagCountry(code: "DE", name: "Germany"),
        FlagCountry(code: "DJ", name: "Djibouti"),
        FlagCountry(code: "DM", name: "Dominica"),
        FlagCountry(code: "DK", name: "Denmark"),
        FlagCountry(code: "DO", name: "Dominican Republic"),
        FlagCountry(code: "DZ", name: "Algeria"),
        FlagCountry(code: "EC", name: "Ecuador"),
        FlagCountry(code: "EG", name: "Egypt"),
        FlagCountry(code: "ER", name: "Eritrea"),
        FlagCountry(code: "EH", name: "Western Sahara"),
        FlagCountry(code: "ES", name: "Spain"),
        FlagCountry(code: "EE", name: "Estonia"),
        FlagCountry(code: "ET", name: "Ethiopia"),
        FlagCountry(code: "FI", name: "Finland"),
        FlagCountry(code: "FJ", name: "Fiji"),
        FlagCountry(code: "FK", name: "Falkland Islands (Malvinas)"),
        FlagCountry(code: "FR", name: "France"),
        FlagCountry(code: "FO", name: "Faroe Islands"),
        FlagCountry(code: "FM", name: "Micronesia, Federated States of"),
        FlagCountry(code: "GA", name: "Gabon"),
        FlagCountry(code: "GB", name: "United Kingdom"),
        FlagCountry(code: "GE", name: "Georgia"),
        FlagCountry(code: "GG", name: "Guernsey"),
        FlagCountry(code: "GH", name: "Ghana"),
        FlagCountry(code: "GI", name: "Gibraltar"),
        FlagCountry(code: "GN", name: "Guinea"),
        FlagCountry(code: "GP", name: "Guadeloupe"),
        FlagCountry(code: "GM", name: "Gambia"),
        FlagCountry(code: "GW", name: "Guinea-Bissau"),
        FlagCountry(code: "GQ", name: "Equatorial Guinea"),
        FlagCountry(code: "GR", name: "Greece"),
        FlagCountry(code: "GD", name: "Grenada"),
        FlagCountry(code: "GL", name: "Greenland"),
        FlagCountry(code: "GT", name: "Guatemala"),
        FlagCountry(code: "GF", name: "French Guiana"),
        FlagCountry(code: "GU", name: "Guam"),
        FlagCountry(code: "GY", name: "Guyana"),
        FlagCountry(code: "HK", name: "Hong Kong"),
        FlagCountry(code: "HN", name: "Honduras"),
        FlagCountry(code: "HR", name: "Croatia"),
        FlagCountry(code: "HT", name: "Haiti"),
        FlagCountry(code: "HU", name: "Hungary"),
        FlagCountry(code: "ID", name: "Indonesia"),
        FlagCountry(code: "IM", name: "Isle of Man"),
        FlagCountry(code: "IN", name: "India"),
        FlagCountry(code: "IO", name: "British Indian Ocean Territory"),
        FlagCountry(code: "IE", name: "Ireland"),
        FlagCountry(code: "IR", name: "Iran, Islamic Republic of"),
        FlagCountry(code: "IQ", name: "Iraq"),
        FlagCountry(code: "IS", name: "Iceland"),
        FlagCountry(code: "IL", name: "Israel"),
        FlagCountry(code: "IT", name: "Italy"),
        FlagCountry(code: "JM", name: "Jamaica"),
        FlagCountry(code: "JE", name: "Jersey"),
        FlagCountry(code: "JO", name: "Jordan"),
        FlagCountry(code: "JP", name: "Japan"),
        FlagCountry(code: "KZ", name: "Kazakhstan"),
        FlagCountry(code: "KE", name: "Kenya"),
        FlagCountry(code: "KG", name: "Kyrgyzstan"),
        FlagCountry(code: "KH", name: "Cambodia"),
        FlagCountry(code: "KI", name: "Kiribati"),
        FlagCountry(code: "KN", name: "Saint Kitts and Nevis"),
        FlagCountry(code: "KR", name: "Korea, Republic of"),
        FlagCountry(code: "KW", name: "Kuwait"),
        FlagCountry(code: "LA", name: "Lao People's Democratic Republic"),
        FlagCountry(code: "LB", name: "Lebanon"),
        FlagCountry(code: "LR", name: "Liberia"),
        FlagCountry(code: "LY", name: "Libya"),
        FlagCountry(code: "LC", name: "Saint Lucia"),
        FlagCountry(code: "LI", name: "Liechtenstein"),
        FlagCountry(code: "LK", name: "Sri Lanka"),
        FlagCountry(code: "LS", name: "Lesotho"),
        FlagCountry(code: "LT", name: "Lithuania"),
        FlagCountry(code: "LU", name: "Luxembourg"),
        FlagCountry(code: "LV", name: "Latvia"),
        FlagCountry(code: "MO", name: "Macao"),
        FlagCountry(code: "MF", name: "Saint Martin (French part)"),
        FlagCountry(code: "MA", name: "Morocco"),
        FlagCountry(code: "MC", name: "Monaco"),
        FlagCountry(code: "MD", name: "Moldova, Republic of"),
        FlagCountry(code: "MG", name: "Madagascar"),
        FlagCountry(code: "MV", name: "Maldives"),
        FlagCountry(code: "MX", name: "Mexico"),
        FlagCountry(code: "MH", name: "Marshall Islands"),
        FlagCountry(code: "MK", name: "North Macedonia"),
        FlagCountry(code: "ML", name: "Mali"),
        FlagCountry(code: "MT", name: "Malta"),
        FlagCountry(code: "MM", name: "Myanmar"),
        FlagCountry(code: "ME", name: "Montenegro"),
        FlagCountry(code: "MN", name: "Mongolia"),
        FlagCountry(code: "MP", name: "Northern Mariana Islands"),
        FlagCountry(code: "MZ", name: "Mozambique"),
        FlagCountry(code: "MR", name: "Mauritania"),
        FlagCountry(code: "MS", name: "Montserrat"),
        FlagCountry(code: "MQ", name: "Martinique"),
        FlagCountry(code: "MU", name: "Mauritius"),
        FlagCountry(code: "MW", name: "Malawi"),
        FlagCountry(code: "MY", name: "Malaysia"),
        FlagCountry(code: "YT", name: "Mayotte"),
        FlagCountry(code: "NA", name: "Namibia"),
        FlagCountry(code: "NC", name: "New Caledonia"),
        FlagCountry(code: "NE", name: "Niger"),
        FlagCountry(code: "NF", name: "Norfolk Island"),
        FlagCountry(code: "NG", name: "Nigeria"),
        FlagCountry(code: "NI", name: "Nicaragua"),
        FlagCountry(code: "NU", name: "Niue"),
        FlagCountry(code: "NL", name: "Netherlands"),
        FlagCountry(code: "NO", name: "Norway"),
        FlagCountry(code: "NP", name: "Nepal"),
        FlagCountry(code: "NR", name: "Nauru"),
        FlagCountry(code: "NZ", name: "New Zealand"),
        FlagCountry(code: "OM", name: "Oman"),
        FlagCountry(code: "PK", name: "Pakistan"),
        FlagCountry(code: "PA", name: "Panama"),
        FlagCountry(code: "PN", name: "Pitcairn"),
        FlagCountry(code: "PE", name: "Peru"),
        FlagCountry(code: "PH", name: "Philippines"),
        FlagCountry(code: "PW", name: "Palau"),
        FlagCountry(code: "PG", name: "Papua New Guinea"),
        FlagCountry(code: "PL", name: "Poland"),
        FlagCountry(code: "PR", name: "Puerto Rico"),
        FlagCountry(code: "KP", name: "Korea, Democratic People's Republic of"),
        FlagCountry(code: "PT", name: "Portugal"),
        FlagCountry(code: "PY", name: "Paraguay"),
        FlagCountry(code: "PS", name: "Palestine, State of"),
        FlagCountry(code: "PF", name: "French Polynesia"),
        FlagCountry(code: "QA", name: "Qatar"),
        FlagCountry(code: "RE", name: "Réunion"),
        FlagCountry(code: "RO", name: "Romania"),
        FlagCountry(code: "RU", name: "Russian Federation"),
        FlagCountry(code: "RW", name: "Rwanda"),
        FlagCountry(code: "SA", name: "Saudi Arabia"),
        FlagCountry(code: "SD", name: "Sudan"),
        FlagCountry(code: "SN", name: "Senegal"),
        FlagCountry(code: "SG", name: "Singapore"),
        FlagCountry(code: "SH", name: "Saint Helena, Ascension and Tristan da Cunha"),
        FlagCountry(code: "SJ", name: "Svalbard and Jan Mayen"),
        FlagCountry(code: "SB", name: "Solomon Islands"),
        FlagCountry(code: "SL", name: "Sierra Leone"),
        FlagCountry(code: "SV", name: "El Salvador"),
        FlagCountry(code: "SM", name: "San Marino"),
        FlagCountry(code: "SO", name: "Somalia"),
        FlagCountry(code: "PM", name: "Saint Pierre and Miquelon"),
        FlagCountry(code: "RS", name: "Serbia"),
        FlagCountry(code: "SS", name: "South Sudan"),
        FlagCountry(code: "ST", name: "Sao Tome and Principe"),
        FlagCountry(code: "SR", name: "Suriname"),
        FlagCountry(code: "SK", name: "Slovakia"),
        FlagCountry(code: "SI", name: "Slovenia"),
        FlagCountry(code: "SE", name: "Sweden"),
        FlagCountry(code: "SZ", name: "Eswatini"),
        FlagCountry(code: "SX", name: "Sint Maarten (Dutch part)"),
        FlagCountry(code: "SC", name: "Seychelles"),
        FlagCountry(code: "SY", name: "Syrian Arab Republic"),
        FlagCountry(code: "TC", name: "Turks and Caicos Islands"),
        FlagCountry(code: "TD", name: "Chad"),
        FlagCountry(code: "TG", name: "Togo"),
        FlagCountry(code: "TH", name: "Thailand"),
        FlagCountry(code: "TJ", name: "Tajikistan"),
        FlagCountry(code: "TK", name: "Tokelau"),
        FlagCountry(code: "TM", name: "Turkmenistan"),
        FlagCountry(code: "TL", name: "Timor-Leste"),
        FlagCountry(code: "TO", name: "Tonga"),
        FlagCountry(code: "TT", name: "Trinidad and Tobago"),
        FlagCountry(code: "TN", name: "Tunisia"),
        FlagCountry(code: "TR", name: "Türkiye"),
        FlagCountry(code: "TV", name: "Tuvalu"),
        FlagCountry(code: "TW", name: "Taiwan, Province of China"),
        FlagCountry(code: "TZ", name: "Tanzania, United Republic of"),
        FlagCountry(code: "UG", name: "Uganda"),
        FlagCountry(code: "UA", name: "Ukraine"),
        FlagCountry(code: "UY", name: "Uruguay"),
        FlagCountry(code: "US", name: "United States"),
        FlagCountry(code: "UZ", name: "Uzbekistan"),
        FlagCountry(code: "VA", name: "Holy See (Vatican City State)"),
        FlagCountry(code: "VC", name: "Saint Vincent and the Grenadines"),
        FlagCountry(code: "VE", name: "Venezuela, Bolivarian Republic of"),
        FlagCountry(code: "VG", name: "Virgin Islands, British"),
        FlagCountry(code: "VI", name: "Virgin Islands, U.S."),
        FlagCountry(code: "VN", name: "Viet Nam"),
        FlagCountry(code: "VU", name: "Vanuatu"),
        FlagCountry(code: "WF", name: "Wallis and Futuna"),
        FlagCountry(code: "WS", name: "Samoa"),
        FlagCountry(code: "YE", name: "Yemen"),
        FlagCountry(code: "ZA", name: "South Africa"),
        FlagCountry(code: "ZM", name: "Zambia"),
        FlagCountry(code: "ZW", name: "Zimbabwe"),
    ].sorted { QuestionBankOrder.key($0.code) < QuestionBankOrder.key($1.code) }
}

private enum QuestionBankOrder {
    static func key(_ value: String) -> UInt64 {
        value.utf8.reduce(1469598103934665603) { ($0 ^ UInt64($1)) &* 1099511628211 }
    }
}
