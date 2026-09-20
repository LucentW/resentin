// creditsText.js — the words. Ported from grappa-irc/cicchetto's
// creditsProse.ts, creditsBlock.ts and creditsFinale.ts, verbatim: this text
// is vjt's and the #grappa channel's, dictated and approved issue by issue
// (#1924, #1929, #1931). Nothing here is Resentin's to improve.

// ---------------------------------------------------------------------------
// The cow — bahamut's Super Cow, transcribed from Azzurra's own `/info`.
// ---------------------------------------------------------------------------

const COW_BODY = `        \\   ^__^
         \\  (oo)\\_______
            (__)\\       )\\/\\
                ||----w |
                ||     ||`;

const COW_SAYS = ["this grappa server", "has super cow powers"];

function cowSaying(said) {
  const spoken = typeof said === "string" ? [said] : said;
  const first = spoken[0] ?? "";
  if (spoken.length === 1) {
    const rule = (fill) => ` ${fill.repeat(first.length + 2)} `;
    return [rule("_"), `< ${first} >`, rule("-"), COW_BODY].join("\n");
  }
  const width = Math.max(...spoken.map((line) => line.length));
  const rule = (fill) => ` ${fill.repeat(width + 2)} `;
  const walls = spoken.map((line, i) => {
    const [left, right] = i === 0 ? ["/", "\\"] : i === spoken.length - 1 ? ["\\", "/"] : ["|", "|"];
    return `${left} ${line.padEnd(width)} ${right}`;
  });
  return [rule("_"), ...walls, rule("-"), COW_BODY].join("\n");
}

const CREDITS_COW = cowSaying(COW_SAYS);

/** vjt's special thanks, dictated on issue 1929 and added to by order since —
 * copied verbatim, in order. Sythos was added on vjt's order, #grappa
 * 2026-09-20: "metti sythos in special thanks nei credits" (cicchetto
 * b499f01a), for Cordiale, a native Rust + Slint desktop client written
 * against the published protocol rather than the source. */
const CREDITS_SPECIAL_THANKS = [
  { who: "Hypnotize, Mezmerize, Sonic, scorpion, joep", why: "for keeping Azzurra standing" },
  { who: "DeepSET / Johnny^Lizard", why: "for embracing grappa and spreading it far and wide" },
  { who: "tsk", why: "for suggesting Erlang" },
  { who: "peluche", why: "most assiduous betatester" },
  { who: "nextime", why: "for shottino" },
  { who: "Lucy", why: "for resentin" },
  { who: "Sonic", why: "for bicchierino" },
  { who: "Sythos", why: "for Cordiale" },
  { who: "morph", why: "for spreading grappa, bringing people back, and throwing himself at the ircd and the services again" },
  { who: "the whole #sniffo crew", why: "for still being here" },
];

// ---------------------------------------------------------------------------
// The prose deck — sixteen sets, shuffle-bagged so none repeats back to back.
// ---------------------------------------------------------------------------

const CREDITS_PROSE = [
  { title: "information wants to be free", paragraphs: [
    "Information wants to be free. The line gets quoted as if it meant free of charge. It never did. It means that something known costs nothing to pass on, and that every wall built to stop it is somebody's business model rather than a law of nature.",
    "Copyleft is that sentence written down as a licence: take this, change it, pass it on, and pass on the same permission you were given. It is the difference between a garden you are allowed to walk in and a garden you are allowed to plant in. The walled kind can be pleasant. It is still not yours, and the gate only opens one way.",
    "The wire does not care what you send down it. Only the owner of the wire cares. So write where nobody owns the wire.",
  ] },
  { title: "free software", paragraphs: [
    "Free software is not free of charge. It is free of landlords.",
    "Eric Raymond gave the two ways of building it their names: the cathedral and the bazaar. The cathedral is raised behind a closed door by appointed hands and handed down finished. The bazaar is a crowd, in the open, arguing in public, everybody free to take the work home and bring back something better. The bazaar should not work. It wrote this protocol, and most of what your bank runs on.",
    "Every line of this is readable, changeable, runnable on a five-euro machine that answers to you, in a country you picked, under a name you invented. There is no account here for a company to close. Fork it and the community outlives you.",
  ] },
  { title: "old software that works", paragraphs: [
    "IRC was finished in 1988. It still runs.",
    "No reactions. No read receipts. No typing indicator. No algorithm deciding which friend you see today. Those are not missing features. They are refusals.",
    "Old code that still works is not debt. It is proof. Most of what came after was an attention tax with a logo on it.",
  ] },
  { title: "text is the feature", paragraphs: [
    "Text is the feature, not the limit.",
    "A screen reader speaks a channel out loud, line by line, and loses nothing on the way. A search box finds a conversation from 2003 before you have finished typing the word you remember. A phone with one bar in a tunnel still delivers it, because a sentence weighs almost nothing. Your head supplies the rest, the way it does with a novel.",
    "More features, more images, more little sounds: none of that is more signal. It is more distraction, handed to you as though it were generosity.",
  ] },
  { title: "the pseudonym", paragraphs: [
    "Behind every nick there is a person who chose that name.",
    "Not a phone number. Not a legal name. Not a verified badge. A word you picked, typed into a room full of other picked words, until the words turned into friendships, projects and jobs.",
    "That is the whole protocol. Everything else is transport.",
  ] },
  { title: "the room you rent", paragraphs: [
    "You cannot read the code of the app you talk to your friends in. You cannot change it, you cannot run it yourself, and you cannot take it with you when you go.",
    "You rent a room in somebody else's building. Rearranging it is not on offer. You move around inside the limits they set, and they redraw those limits whenever it suits them: no notice, no appeal, no forwarding address for the conversations that were in there.",
    "IRC runs on a cheap server, a handful of text files and a small program. Anyone can stand one up in an afternoon and hold the keys to it. That asymmetry is not decoration. It is the whole game.",
  ] },
  { title: "teaching a server to lie", paragraphs: [
    "2001. Azzurra was paying for a commercial IRC server it did not want, and could not drop, because of the little chat window on the website: click, type a name, you are in, nothing to install. Half the network arrived that way.",
    "That window was fussy about its host. On connecting it asked the server what it was, and it would only go on if the answer came back word for word as the product it had been sold with: CR1.8.4-SEC.",
    "So we taught our own free server to give exactly that answer, with a straight face, and dropped the commercial one. The window never noticed. Neither did the people typing into it.",
  ] },
  { title: "you existed only while connected", paragraphs: [
    "On IRC you exist only while connected. Close the window and the room carries on without you: nothing is kept, nothing is waiting when you come back, and whatever was said while you were away was said to the people who were there. That is not a gap in the protocol. It is what the protocol is, in 1988 and still in 2026.",
    "So people left tower PCs running all night, phone line tied up, purely to stay in the room. The luckier ones rented a psyBNC: a small program on somebody's always-on machine that held the connection in your place and handed you the transcript when you got back.",
    "grappa is that program, grown up and pointed at everyone rather than at the fifteen people who knew how to install one. It holds the line, keeps the conversation, and lets you walk away from the desk. Being present stops being a function of your electricity bill.",
  ] },
  { title: "netsplit", paragraphs: [
    "A netsplit is the moment the network forgets it is one network. A link between two servers dies and each half carries on convinced it is the whole thing: same channels, same names, two of everything, neither aware of the other.",
    "From the inside it looks like the room being cut in half in one line. *** Netsplit hub.azzurra.chat <-> irc.azzurra.chat — and half the people you were mid-argument with are simply gone. Ninety seconds later: *** Netjoin, and they all walk back in at once and finish the sentence, having spent the interval in a room that was also #sniffo, with the same topic, and no idea you were missing.",
    "The ugly part came at the seam. The same nickname was alive on both sides, and IRC settled it by killing both claims — the attacker who caused the split reconnected instantly, the person who had held the name for years came back to find it gone. The fix was a timestamp: oldest claim wins. Twenty-four years later it still holds.",
  ] },
  { title: "twenty-one", paragraphs: [
    "At twenty-one I forked an IRC server to teach it IPv6 and SSL. At night. For a network that paid nobody.",
    "The CVS repository is still on SourceForge: 171 commits, three authors, February 2002 to January 2006. It still compiles today, which is not a compliment to my code.",
    "It compiles because Sonic and morph kept their hands on it, on and off, for twenty years — not heroics, not a commit a day, just two volunteers who kept answering for a thing nobody was paying them to keep alive, until Bahamut and Azzurra were still standing in 2026. Open source does not survive because the code is good. It survives because somebody keeps turning up.",
  ] },
  { title: "the trail goes cold", paragraphs: [
    "In 2002 I started writing IRC services from scratch — the programs that hold your nickname while you are away, so that a name is yours rather than whoever types it first. 954 commits. I left the network before they were finished.",
    "Aidas Kasparas picked them up from Latvia, signing his work monas, and wrote 192 commits more. Then that trail goes cold as well. Nobody runs the code today.",
    "But while the two of us were on it the trail was hot, and what each of us kept was never the software: it was the hands-on knowledge of having built the thing, which does not go cold at all. The process was the product. It usually is.",
  ] },
  { title: "#sniffo", paragraphs: [
    "The channel was called #sniffo. Inline skating and funny faces. Nothing pharmaceutical.",
    "What gathered there was a loosely-knit group of nerds on the Milan-Bologna-Monopoli axis, which is most of the length of the country. Nobody organised it and nobody counted. In time they started driving that axis for real, to sit in the same room with the cases open and the CRTs on, arguing about the same code they had been arguing about in text all year.",
    "Twenty-five years later the channel is still there. So are they.",
  ] },
  { title: "the bot", paragraphs: [
    "The bot in this channel started as a line somebody threw out in passing: you should try hooking Claude up to IRC directly. Five minutes later it was online.",
    "First night, 250 lines of Python: it corrected a factual error in a blog post, deployed the fix while the channel was still arguing about it, caught two prompt injections, and leaked its operator's brokerage account number. Twice.",
    "Then it learned to shut up until spoken to. That was the hard part.",
  ] },
  { title: "found in a magazine", paragraphs: [
    "I found IRC in a magazine. Paper, a newsstand, an article about an Italian network.",
    "I connected, picked a name, opened a channel with friends. User, then contributor, then operator, then writing the server itself. None of it planned.",
    "That is the trick open source keeps pulling, and no algorithm is involved anywhere in it: somebody writes something down, somebody else reads it and ends up contributing, and a great many people who will never touch the code get the benefit of both. One article in a magazine, and here we still are.",
  ] },
  { title: "eighteen years", paragraphs: [
    "In 2008 a friend told me: come to the social network, it is like IRC but it does so much more.",
    "He was right. It did so much more. Then it did stories, and reels, and an algorithm deciding which friend I saw today, and eighteen years went past.",
    "I came back to IRC in 2026, and by an absurd route: doing digital archaeology with an AI on code I had left rotting in time capsules, SourceForge CVS repositories nobody had touched since 2006. I rejoined Azzurra. Hypnotize queried me within minutes, and I was back on the operators' channel as though twenty years had not happened. Same people, same channels, same names. Nobody had monetised anything.",
  ] },
  { title: "how this channel filled up", paragraphs: [
    "In April there was one person in this channel. Me, plus a bot I had wired up the week before.",
    "Then guly joined. Then peluche, who stayed, and who has been on every build since — the one who finds in ten minutes, by using the thing like a person rather than like its author, what no test suite was ever going to catch. Then nextime sent the first contributions from outside. Then Hypnotize, then Sonic, then morph, who arrived for the server he had been maintaining for twenty years and fell for the client instead. Then Lucy, building Resentin against the same API, because the API is open and nobody had to ask.",
    "Nobody was recruited. They read something somewhere and typed /join. Now we're thirty.",
  ] },
];

/** Fisher-Yates over the indices of `count` items. */
function shuffledIndices(count, random) {
  const order = Array.from({ length: count }, (_, i) => i);
  for (let i = count - 1; i > 0; i -= 1) {
    const j = Math.floor(random() * (i + 1));
    [order[i], order[j]] = [order[j], order[i]];
  }
  return order;
}

/** A shuffle bag: deals every set once before reshuffling, and never repeats
 * the previous bag's last set as the new bag's first. */
function createProseDeck(sets = CREDITS_PROSE, random = Math.random) {
  let bag = [];
  let last = null;

  const refill = () => {
    bag = shuffledIndices(sets.length, random);
    if (sets.length > 1 && bag[bag.length - 1] === last) {
      const other = bag.length - 2;
      [bag[bag.length - 1], bag[other]] = [bag[other], bag[bag.length - 1]];
    }
  };

  return {
    draw() {
      if (sets.length === 0) return null;
      if (bag.length === 0) refill();
      const index = bag.pop();
      last = index;
      return sets[index];
    },
    exhausted() {
      return sets.length > 0 && last !== null && bag.length === 0;
    },
  };
}

// ---------------------------------------------------------------------------
// The finale — the manifesto and the closing line.
// ---------------------------------------------------------------------------

const CREDITS_HEART = "<3";
const CREDITS_FINALE_LINE = "and that's the whole of the wire, folks.";
const CREDITS_CLOSE_LABEL = "click here to close";

/** "The Conscience of a Hacker" — The Mentor (Loyd Blankenship), Phrack Vol.
 * 1 Issue 7 Phile 3, 8 January 1986. Shipped in cic on vjt's explicit
 * decision (#grappa 2026-09-06): the project is open source and he will
 * comply with a takedown if one is ever asked for. */
const CREDITS_MANIFESTO = `\\/\\The Conscience of a Hacker/\\/

by

+++The Mentor+++

Written on January 8, 1986
=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=

Another one got caught today, it's all over the papers.  "Teenager Arrested in Computer Crime Scandal", "Hacker Arrested after Bank Tampering"...
Damn kids.  They're all alike.

But did you, in your three-piece psychology and 1950's technobrain, ever take a look behind the eyes of the hacker?  Did you ever wonder what made him tick, what forces shaped him, what may have molded him?
I am a hacker, enter my world...
Mine is a world that begins with school... I'm smarter than most of the other kids, this crap they teach us bores me...
Damn underachiever.  They're all alike.

I'm in junior high or high school.  I've listened to teachers explain for the fifteenth time how to reduce a fraction.  I understand it.  "No, Ms. Smith, I didn't show my work.  I did it in my head..."
Damn kid.  Probably copied it.  They're all alike.

I made a discovery today.  I found a computer.  Wait a second, this is cool.  It does what I want it to.  If it makes a mistake, it's because I screwed it up.  Not because it doesn't like me...
Or feels threatened by me...
Or thinks I'm a smart ass...
Or doesn't like teaching and shouldn't be here...
Damn kid.  All he does is play games.  They're all alike.

And then it happened... a door opened to a world... rushing through the phone line like heroin through an addict's veins, an electronic pulse is sent out, a refuge from the day-to-day incompetencies is sought... a board is found.
"This is it... this is where I belong..."
I know everyone here... even if I've never met them, never talked to them, may never hear from them again... I know you all...
Damn kid.  Tying up the phone line again.  They're all alike...

You bet your ass we're all alike... we've been spoon-fed baby food at school when we hungered for steak... the bits of meat that you did let slip through were pre-chewed and tasteless.  We've been dominated by sadists, or ignored by the apathetic.  The few that had something to teach found us willing pupils, but those few are like drops of water in the desert.

This is our world now... the world of the electron and the switch, the beauty of the baud.  We make use of a service already existing without paying for what could be dirt-cheap if it wasn't run by profiteering gluttons, and you call us criminals.  We explore... and you call us criminals.  We seek after knowledge... and you call us criminals.  We exist without skin color, without nationality, without religious bias... and you call us criminals.
You build atomic bombs, you wage wars, you murder, cheat, and lie to us and try to make us believe it's for our own good, yet we're the criminals.

Yes, I am a criminal.  My crime is that of curiosity.  My crime is that of judging people by what they say and think, not what they look like.
My crime is that of outsmarting you, something that you will never forgive me for.

I am a hacker, and this is my manifesto.  You may stop this individual, but you can't stop us all... after all, we're all alike.

+++The Mentor+++`;

const CREDITS_MANIFESTO_ATTRIBUTION =
  "The Mentor (Loyd Blankenship) — Phrack Vol. 1, Issue 7, Phile 3, 8 January 1986";

// ---------------------------------------------------------------------------
// Resentin's own block (new) — shown FIRST, ahead of grappa-irc's roll. The
// contributor roll is filled in by credits-data.js, generated at build time
// by the resentin-credits GitHub Actions step (scripts/credits.sh) from
// `git shortlog`, mirroring grappa's own infra/packaging/credits.sh.
// ---------------------------------------------------------------------------

const RESENTIN_THANKS = [
  { who: "grappa-irc & cicchetto", why: "the server and the API resentin talks to" },
  { who: "#grappa", why: "for the bug reports, the betatesting, and the company" },
];
