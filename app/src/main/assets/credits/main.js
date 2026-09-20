// main.js — the credits roll's orchestrator. Vanilla-JS equivalent of
// cicchetto's CreditsModal.tsx (Solid + CSS-Animation-phase introspection),
// simplified to a single requestAnimationFrame loop that drives the scroll
// position directly instead of restarting a CSS animation with a
// per-pass-measured duration. Same sequence, same rain/audio behavior;
// ponytail: dropped the "hold to read" touch-pause and the reading-speed-vs-
// content-height pacing model — a fixed px/s crawl, which is what cic's own
// pace amounts to for a set of typical length.
//
// Classic scripts, not ES modules: a WebView loading `file:///android_asset/`
// treats each `file://` resource as its own opaque origin, and a
// `<script type="module">` import across those is blocked by CORS (silently,
// no visible error without remote debugging — the page just stays blank).
// index.html loads creditsAudio.js / creditsText.js / matrixRain.js / main.js
// as plain sequential <script> tags instead, so their top-level `const`s and
// `function`s land in the one shared global scope this file reads directly.

const params = new URLSearchParams(location.search);
const RESENTIN_VERSION = params.get("version") ?? "";

const ROLL_SPEED_PX_S = 42; // cic's own roll settles around ~34-40px/s.
const INTERLUDE_MS = 3200; // the parked "pure rain" pause between passes.
const BLOCK_FADE_START = 0.9; // fraction of the travel where a block dissolves.
// Fast forward (vjt, #grappa: "un bottone fast forward che velocizza lo
// scroll?"), same multiplier as cicchetto's own button. Resentin drives the
// scroll from a plain px/s number rather than a CSS Animation's playbackRate,
// so this is a straight multiplier on ROLL_SPEED_PX_S in tick() — no rate to
// re-apply after a restart, no durations to divide it back out of.
const FAST_FORWARD_RATE = 5;

const $roll = document.getElementById("roll");
const $viewport = document.getElementById("viewport");
const $canvas = document.getElementById("rain-canvas");
const $movement = document.getElementById("movement-label");
const $muteBtn = document.getElementById("mute-btn");
const $closeBtn = document.getElementById("close-btn");
const $ffBtn = document.getElementById("ff-btn");

let muted = localStorage.getItem("credits-muted") === "1";
$muteBtn.setAttribute("aria-pressed", String(muted));

let arpeggio = null;
let movementIndex = 0;
let phase = "traveling"; // "traveling" | "interlude" | "ended"
let fadingBlock = null; // the DOM node currently mid-dissolve, or null
let travelY = 0; // px translated up from the start position, this pass
let travelDistance = 0; // this pass's total travel (viewport + roll height)
let lastFrameAt = 0;
let fastForward = false; // held down: see fastForwardOn/Off below.

const deck = createProseDeck();
// Two separate first-block passes, not one concatenated block: combining the
// Resentin block and grappa's own block into a single tall element made the
// fade-out (timed as the LAST 10% of that pass's own travel) dissolve well
// before the grappa half had fully scrolled through, and the following prose
// pass then entered while that tail was still mid-transition — read as "the
// next block starts from halfway". Each block now gets its own full
// cic-sized travel + fade, same as the roll's every other pass.
let stage = "resentin_block"; // -> "grappa_block" -> "prose" -> "manifesto" -> "ended"

function closeCredits() {
  if (window.ResentinCredits?.close) {
    window.ResentinCredits.close();
    return;
  }
  // Fallback for opening this page outside the Android WebView bridge.
  document.body.classList.add("credits-closed");
}

$closeBtn.addEventListener("click", closeCredits);
$muteBtn.addEventListener("click", () => {
  muted = !muted;
  localStorage.setItem("credits-muted", muted ? "1" : "0");
  $muteBtn.setAttribute("aria-pressed", String(muted));
  arpeggio?.setMuted(muted);
});

// Press and hold, like the button on a tape deck (cic's own wording): the
// roll runs at FAST_FORWARD_RATE for exactly as long as the finger is down,
// not a toggle a second press has to undo — a fast-forward left latched by a
// missed release would never slow back down. Pointer capture on the button
// itself means a release outside its bounds (drag off, or the finger simply
// lifting past the edge) still reaches fastForwardOff.
function fastForwardOn(event) {
  $ffBtn.setPointerCapture?.(event.pointerId);
  fastForward = true;
  $ffBtn.setAttribute("aria-pressed", "true");
}
function fastForwardOff() {
  if (!fastForward) return;
  fastForward = false;
  $ffBtn.setAttribute("aria-pressed", "false");
}
$ffBtn.addEventListener("pointerdown", fastForwardOn);
$ffBtn.addEventListener("pointerup", fastForwardOff);
$ffBtn.addEventListener("pointercancel", fastForwardOff);
$ffBtn.addEventListener("lostpointercapture", fastForwardOff);
// The backstop for what capture cannot cover: the app going away mid-hold
// (task switcher, lock button, a call) does not always deliver a pointer
// event on the way out, and a roll left stuck at 5x behind a hidden tab is
// exactly the state this guards against.
document.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "hidden") fastForwardOff();
});

function personRow(name, nick, commits) {
  const li = document.createElement("li");
  li.className = "credits-person";
  const label = nick ? `${nick} (${name})` : name;
  li.innerHTML = `<span class="credits-person-name">${escapeHtml(label)}</span><span class="credits-person-count">${commits}</span>`;
  return li;
}

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
}

function thanksList(entries) {
  const ul = document.createElement("ul");
  ul.className = "credits-thanks";
  for (const { who, why } of entries) {
    const li = document.createElement("li");
    li.innerHTML = `<span class="credits-thanks-who">${escapeHtml(who)}</span> — <span class="credits-thanks-why">${escapeHtml(why)}</span>`;
    ul.appendChild(li);
  }
  return ul;
}

/** Resentin's own block, shown FIRST — title, version, the contributor roll
 * baked in by the resentin-credits GitHub Actions step (credits-data.js). */
function buildResentinBlock() {
  const wrap = document.createElement("div");
  wrap.innerHTML = `
    <h1 class="credits-title">RESENTIN</h1>
    ${RESENTIN_VERSION ? `<p class="credits-version">v${escapeHtml(RESENTIN_VERSION)}</p>` : ""}
    <h2 class="credits-heading">contributors</h2>
  `;
  const list = document.createElement("ul");
  list.className = "credits-list";
  const credits = window.RESENTIN_CREDITS;
  const contributors = Array.isArray(credits?.contributors) ? credits.contributors : [];
  if (contributors.length === 0) {
    const p = document.createElement("p");
    p.className = "credits-empty";
    p.textContent = "(contributor roll unavailable in this build)";
    wrap.appendChild(p);
  } else {
    for (const c of contributors) list.appendChild(personRow(c.name, c.nick, c.commits));
    wrap.appendChild(list);
  }
  wrap.appendChild(thanksList(RESENTIN_THANKS));
  return wrap;
}

/** grappa-irc's own block — the cow and the special thanks, ported verbatim
 * from cicchetto. No contributor roll here: that is grappa-irc's OWN repo
 * history, which this build has no access to — showing a roll frozen at
 * whatever day this page was last copied over would only rot silently. */
function buildGrappaBlock() {
  const wrap = document.createElement("div");
  wrap.innerHTML = `
    <h2 class="credits-heading">and the server it talks to</h2>
    <h1 class="credits-title">GRAPPA-IRC</h1>
    <pre class="credits-cow">${escapeHtml(CREDITS_COW)}</pre>
  `;
  wrap.appendChild(thanksList(CREDITS_SPECIAL_THANKS));
  return wrap;
}

function buildProseSet(set) {
  const wrap = document.createElement("div");
  wrap.className = "credits-prose";
  const h = document.createElement("h3");
  h.className = "credits-prose-title";
  h.textContent = set.title;
  wrap.appendChild(h);
  for (const para of set.paragraphs) {
    const p = document.createElement("p");
    p.textContent = para;
    wrap.appendChild(p);
  }
  return wrap;
}

function buildManifesto() {
  const wrap = document.createElement("div");
  wrap.className = "credits-manifesto";
  wrap.innerHTML = `
    <p class="credits-manifesto-text">${escapeHtml(CREDITS_MANIFESTO)}</p>
    <p class="credits-manifesto-attribution">${escapeHtml(CREDITS_MANIFESTO_ATTRIBUTION)}</p>
  `;
  return wrap;
}

function buildFinale() {
  const wrap = document.createElement("div");
  wrap.innerHTML = `
    <div class="credits-heart">${CREDITS_HEART}</div>
    <p class="credits-finale-line">${escapeHtml(CREDITS_FINALE_LINE)}</p>
  `;
  const btn = document.createElement("button");
  btn.type = "button";
  btn.className = "credits-close-cta";
  btn.textContent = CREDITS_CLOSE_LABEL;
  btn.addEventListener("click", closeCredits);
  wrap.appendChild(btn);
  return wrap;
}

/**
 * Builds the content of the NEXT pass and advances `stage` for the pass
 * after it. Returns `null` once there is nothing left to travel with — the
 * caller then shows the static ending instead of starting another pass.
 */
function buildNextScreen() {
  if (stage === "resentin_block") {
    const block = document.createElement("div");
    block.className = "credits-block";
    block.appendChild(buildResentinBlock());
    fadingBlock = block;
    stage = "grappa_block";
    return block;
  }

  if (stage === "grappa_block") {
    const block = document.createElement("div");
    block.className = "credits-block";
    block.appendChild(buildGrappaBlock());
    fadingBlock = block;
    stage = "prose";
    return block;
  }

  if (stage === "prose") {
    fadingBlock = null;
    const set = deck.draw();
    movementIndex += 1;
    if ($movement) $movement.textContent = creditsMovementName(movementIndex % MOVEMENT_COUNT);
    if (deck.exhausted()) stage = "manifesto";
    return buildProseSet(set);
  }

  if (stage === "manifesto") {
    fadingBlock = null;
    stage = "ended";
    arpeggio?.setPiece("manifesto");
    if ($movement) $movement.textContent = "";
    return buildManifesto();
  }

  return null; // stage === "ended": nothing more to travel with
}

function enterEnded() {
  phase = "ended";
  $viewport.classList.add("ended");
  $roll.classList.add("ended");
  $roll.style.transform = "none";
  $roll.innerHTML = "";
  $roll.appendChild(buildFinale());
  arpeggio?.setPiece("cadence");
}

// The viewport height for the pass CURRENTLY traveling — captured once in
// startPass() and reused by every tick() of that pass, rather than re-reading
// window.innerHeight every frame. Read off .credits-viewport itself (the
// element the roll's own travel is measured against) rather than the window
// global: a WebView's window.innerHeight can shift slightly as system bars/
// insets settle, and a start-of-pass value that later drifts from the
// per-frame value is exactly what makes the math land somewhere other than
// "fully below the fold".
let passVh = 0;

// A stall this long or longer (a backgrounded/dimmed WebView resuming, a GC
// pause, ...) is treated as "start this frame's motion from zero" rather than
// integrated as one giant dtMs — the latter is what turns a single dropped
// frame into travelY jumping clean past the interlude and into the NEXT
// pass's territory in one step, which reads as the roll skipping/looping.
const MAX_FRAME_DT_MS = 250;

function startPass() {
  const content = buildNextScreen();
  if (content === null) {
    enterEnded();
    return;
  }
  $roll.innerHTML = "";
  $roll.appendChild(content);
  travelY = 0;
  passVh = $viewport.clientHeight || window.innerHeight;
  const rollH = Math.max($roll.scrollHeight, 1);
  travelDistance = passVh + rollH;
  $roll.style.transform = `translateY(${passVh}px)`;
  // Reset so tick()'s next frame measures a fresh delta — without this, the
  // first frame after an interlude (or the very first pass) computes dtMs
  // against a stale timestamp, jumps travelY past the whole distance in one
  // frame, and immediately falls right back into another interlude. From the
  // outside that reads as "the roll never advances to the next block".
  lastFrameAt = 0;
  phase = "traveling";
}

function tick(now) {
  requestAnimationFrame(tick);
  if (phase !== "traveling") return;
  if (lastFrameAt === 0) {
    lastFrameAt = now;
    return; // no elapsed time to integrate on the first frame of a pass
  }
  const dtMs = Math.min(now - lastFrameAt, MAX_FRAME_DT_MS);
  lastFrameAt = now;

  const speed = fastForward ? ROLL_SPEED_PX_S * FAST_FORWARD_RATE : ROLL_SPEED_PX_S;
  travelY += (speed * dtMs) / 1000;
  $roll.style.transform = `translateY(${passVh - travelY}px)`;

  if (fadingBlock !== null) {
    const progress = travelY / travelDistance;
    if (progress >= BLOCK_FADE_START) {
      const fadeProgress = Math.min(1, (progress - BLOCK_FADE_START) / (1 - BLOCK_FADE_START));
      fadingBlock.style.opacity = String(1 - fadeProgress);
    }
  }

  if (travelY >= travelDistance) {
    phase = "interlude";
    setTimeout(startPass, INTERLUDE_MS);
  }
}

function rainLook() {
  if (phase === "interlude" || phase === "ended") return CREDITS_RAIN_BURST_LOOK;
  if (fadingBlock !== null) {
    const progress = travelDistance > 0 ? travelY / travelDistance : 0;
    if (progress >= BLOCK_FADE_START) return CREDITS_RAIN_BURST_LOOK;
  }
  return CREDITS_RAIN_LOOK;
}

mountMatrixRain($canvas, rainLook);

if (!window.matchMedia?.("(prefers-reduced-motion: reduce)")?.matches) {
  requestAnimationFrame(tick);
  startPass();
} else {
  // Reduced motion: render the whole sequence as one static, scrollable
  // column instead of animating any of it.
  $viewport.classList.add("ended");
  $roll.classList.add("ended");
  const block = document.createElement("div");
  block.className = "credits-block";
  block.appendChild(buildResentinBlock());
  block.appendChild(buildGrappaBlock());
  $roll.appendChild(block);
  let set = deck.draw();
  while (set !== null && !deck.exhausted()) {
    $roll.appendChild(buildProseSet(set));
    set = deck.draw();
  }
  if (set !== null) $roll.appendChild(buildProseSet(set));
  $roll.appendChild(buildManifesto());
  $roll.appendChild(buildFinale());
}

// Audio — WebView autoplay is allowed because the Android host disables the
// user-gesture requirement (this page only ever opens from an explicit tap
// on /credits). A refused AudioContext leaves the roll silent, not broken.
try {
  const Ctx = window.AudioContext || window.webkitAudioContext;
  if (Ctx) {
    const ctx = new Ctx();
    arpeggio = startCreditsArpeggio(ctx, muted, () => movementIndex % MOVEMENT_COUNT);
  }
} catch {
  // No audio in this build target; the visual roll still runs.
}
