# Google Play IARC content-rating evidence

Package: `com.rsm.eztrivia`

Last audited: September 15, 2026

This document is an evidence sheet for completing Google Play's IARC questionnaire. It is **not** a substitute for the live questionnaire and it does not predict the final regional ratings; IARC authorities calculate those from the submitted answers.

The repository's manual **Android Content Rating Audit** workflow scans the complete shipped `QuestionReview.csv` catalog and archives non-secret JSON evidence. Keyword hits are review candidates, not automated policy decisions.

## Questionnaire category

Use the **Game** category. EZ Trivia is an entertainment trivia game. Google explicitly says that apps containing a significant portion of gaming, including educational games, should be categorized as Games rather than Reference/News/Educational.

## Current catalog evidence

The September 15, 2026 audit scanned all **2,341** shipped questions and found:

- 77 referred-violence keyword candidates;
- 1 illegal/recreational-drug candidate;
- 18 alcohol candidates;
- 5 sex/nudity candidates;
- 1 gambling-term candidate;
- 0 strong-profanity candidates.

The raw counts deliberately over-include ambiguous words and must be reviewed in context. For example, the single gambling-term candidate is `movies-medium-17`, where **Casino** is merely an incorrect movie-title answer choice in a question about *The Godfather*. It is not gambling functionality.

## Recommended questionnaire answers from current evidence

### Violence

**Contains violent material: Yes.** Google says text must be considered, not only images/video/audio. The app has non-graphic factual or fictional text references including:

- `history-medium-10`: Archduke Franz Ferdinand was assassinated; the explanation refers to his killing;
- `movies-medium-31`: a murder verdict in *12 Angry Men*;
- `mythology-medium-10`: Theseus killed the Minotaur;
- `literature-medium-2`: Raskolnikov commits a murder;
- `history-easy-19`: the 1945 atomic bombings.

**Presentation: Referred to / text only.** The violence is communicated in trivia text and explanations. The game does not visually depict or interactively enact these acts.

**Realistic or historical war setting: No.** War is frequently referenced in history questions, but war is not the game's main theme or setting. Google's questionnaire help explicitly says to answer No where an app only references war in text/dialogue or only a small portion of gameplay involves a war scenario.

**Blood/gore: No graphic depiction.** The app contains ordinary scientific/animal references to blood, but no gore imagery or graphic violent blood depiction.

### Sex and nudity

**References to sex without detail: Yes.** `movies-hard-40` says pre-Code films could address “sex, crime, and social issues” more openly. This is a non-graphic textual reference.

**Detailed sexual references / depictions of sexual activity: No.** No shipped question describes sexual activity graphically or in detail.

**Nudity: textual reference only.** `art-hard-2` describes Manet's *Olympia* as a “modern nude,” but the question does not display the painting or another nude image. If the live questionnaire distinguishes references from visual depictions, answer according to that distinction rather than treating the word “nude” as an image.

### Illegal or recreational drugs

**Contains an illegal/recreational-drug reference: Yes.** `tv-easy-5` explains that Walter White enters the “drug trade.” Google says this question covers material a user can access through the app.

**Interactive use of illegal drugs: No.** There is no drug-use mechanic.

**Incentives for illegal drugs: No.** The app does not reward, instruct, encourage, sell, or glamorize drug use.

**Focus of app: No.** Drugs are not a focus of EZ Trivia.

### Alcohol / tobacco

**Alcohol references: Yes.** Examples include `history-medium-27` (US Prohibition and a ban on alcohol production/sale), `food-easy-9` (wine/beer/cider/sake), wine-making questions, Champagne, and rum. These are factual history/food/culture references rather than encouragement of drinking.

**Tobacco/nicotine: No known content.** The reviewed catalog does not contain a substantive tobacco, nicotine, cigarette, cigar, or vaping question. Cooking references to “smoke” are not tobacco content.

### Gambling and cash payouts

**Real gambling or cash payouts: No.** EZ Trivia has no wagering, casino mechanic, betting, real-money prize, or cash payout. The word `Casino` occurs only as a movie-title distractor in `movies-medium-17`.

**Simulated gambling: No.** There is no simulated casino or wagering mechanic.

### Language

**Strong/offensive language: No known strong profanity.** The automated catalog audit currently finds zero strong-profanity candidates. Retake/review the questionnaire if the question catalog changes materially.

### Digital purchases

**Digital purchases: Yes.** The app offers the one-time Google Play purchase `com.rsm.eztrivia.removeads` to disable ads. Google's questionnaire guidance explicitly includes paying to disable ads as a digital purchase.

### User-generated content / online interaction

**Online interaction or user-created content exchange: No.** Friend Challenge exchanges a deterministic challenge code/link; there is no in-app freeform chat, comments, photo/file sharing, or user-authored content feed. Google says multiplayer alone does not require a Yes answer when users cannot communicate/share content, and sharing performed through a secondary app is not counted as the app's native content exchange.

**Social network / forum / UGC-sharing app: No.** That is not EZ Trivia's primary purpose.

## Target audience is a separate decision

Do not infer target-audience age groups solely from the eventual IARC rating. The intended product is **not a children's app**. Google applies Families requirements when selected target-audience groups include children, so only select age groups the product is genuinely intended for.

## Submission rule

Do not submit a blanket “No” questionnaire merely because the app is trivia. The current catalog contains textual violence, a drug reference, alcohol references, a non-detailed sex reference, and a textual nude-art reference. Conversely, do not turn keyword matches into exaggerated answers: the app does not contain visual/interactive violence, detailed sexual activity, drug mechanics, gambling, cash payouts, or native UGC exchange.

After Play calculates the regional ratings, review the summary before submitting. If the result appears inconsistent with the answers, retake the questionnaire rather than changing truthful answers simply to target a preferred rating.

## Official Google references

- Content ratings policy and questionnaire process: https://support.google.com/googleplay/android-developer/answer/9859655
- Violent material: https://support.google.com/googleplay/android-developer/answer/6159992
- Violence presentation / “Referred To”: https://support.google.com/googleplay/android-developer/answer/6170620
- Realistic or historical war setting: https://support.google.com/googleplay/android-developer/answer/7444145
- References to sex without detail: https://support.google.com/googleplay/android-developer/answer/6161078
- Illegal or recreational drugs: https://support.google.com/googleplay/android-developer/answer/6159991
- Digital purchases: https://support.google.com/googleplay/android-developer/answer/6161124
- Online interaction/content exchange: https://support.google.com/googleplay/android-developer/answer/7021383
- Target audience / Families implications: https://support.google.com/googleplay/android-developer/answer/9867159
