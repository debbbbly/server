# PRODUCT.md

This document describes what Debbly is, why it exists, and where it's going. It's written for product people joining the team and for AI agents building new features.

## Mission

Make debate available for everyone. What YouTube did for creators and Twitch did for streamers — Debbly does for debaters.

## The Problem

There is no video platform where regular people can find a debate opponent and challenge ideas face-to-face. Existing platforms are text-based (Reddit, X) or voice-only (Twitter Spaces, Clubhouse). The debate celebrities everyone watches are always in video format — but that format isn't available to everyday people. Debbly changes that.

## Vision

Become the go-to platform for public discourse. A media platform built around video debates — like a smaller X.com but centered on live 1v1 video. Debaters build audiences, grow their reputation, and eventually earn money on the platform.

## Target Audience

- **B2C, open to everyone.** No gatekeeping.
- The core power users are people who debate daily, build an audience, and become known on the platform.
- Think: politically engaged people, aspiring commentators, students, anyone who wants to sharpen their arguments on camera.

## Product Philosophy

We follow **lean startup** principles. Ship the simplest version, get real users, learn what they want, then decide where to go. Features are earned by user demand, not planned in a vacuum.

---

## How Debbly Works (Current)

The core loop is simple:

1. **Pick a claim** — Browse existing claims or create your own (e.g., "AI will replace most jobs within 10 years").
2. **Choose your stance** — Agree, Disagree, or Debate Either Side.
3. **Enter matchmaking** — Confirm your camera/mic and join the queue. Wait to be paired with someone who holds the opposing stance.
4. **Go live** — Once matched, both debaters accept and the live 1v1 video debate begins. An audience watches and participates in real time.
5. **Recording** — After the debate ends, a recording is available publicly for anyone to watch and share.

### Key Concepts

| Concept | Description |
|---------|-------------|
| **Claim** | A debatable statement (e.g., "Remote work is better than office work"). Claims have categories and can be created by any user. |
| **Stance** | A debater's position on a claim: FOR (agree), AGAINST (disagree), or EITHER (willing to argue either side). |
| **Stage** | A live debate room. Two debaters on camera, audience watching via chat and reactions. |
| **Queue** | The matchmaking waiting list. Users enter the queue for a specific claim+stance and get paired when an opponent is available. |
| **Recording** | A saved video of a completed debate, available for public playback. |

### Debate Format

- **1v1 only** (for now — panel formats like 2v2 may come later based on demand).
- **15 minutes** per debate (exploring longer formats like 20+ minutes, possibly debater-chosen duration).
- Debates are **public by default** — anyone can watch live or view the recording.

### Audience Participation

Audience members can:
- **Chat** in real time during the debate.
- **React** with emoji reactions.
- **Vote** on who's winning.
- **Tip** debaters (planned).

### Content & Moderation

- Content policy similar to X/Twitch — broad expression allowed within community guidelines.
- **AI moderation first**, scaling toward community moderators over time.
- Rule violations during a live debate: debate ends, violator gets a timeout.

---

## Current Product Pages

| Page | Path | Purpose |
|------|------|---------|
| Home | `/` | Live debates, trending claims, past debates |
| Claim Detail | `/claims/[slug]` | Claim info, vote counts, queue status, past debates for this claim |
| Waiting Queue | `/waiting` | Claims the user is queued for, option to leave queue |
| Stage (Live) | `/stages/[stageId]` | Live debate view with video, chat, reactions |
| User Profile | `/users/[username]` | Debate history and settings |
| About | `/about` | How Debbly works, links to legal pages |
| Community Guidelines | `/community` | Rules and expectations |

---

## Business Model

**Freemium** with multiple revenue streams (planned):

| Stream | Description |
|--------|-------------|
| **Tips** | Audience tips debaters during or after debates. Platform takes a cut. |
| **Premium subscription** | No ads, priority matchmaking, claim creation perks. |
| **Ads** | Non-intrusive ads for free-tier users (pre-roll on recordings, display ads). |

---

## Reputation & Rankings

Planned but not yet implemented. The idea:
- Debate score or rating based on audience votes and activity.
- Win/loss records visible on profiles.
- Leaderboards by category or overall.
- Reputation drives visibility and matchmaking quality.

---

## Roadmap Themes

We don't have a rigid roadmap — we ship, learn, and adapt. But these are the areas we're thinking about:

### Near-Term (validate with early users)
- **Scheduled debate events** — challenge anyone to debate at a set time with a shareable link. Solves cold-start.
- **Better recordings** — clips, highlights, shareable moments, embed support. Recordings are the growth engine.
- **Debater profiles as highlight reels** — best clips, topics, win record. Shareable creator pages.
- **Tipping** — let audiences support debaters financially. First step to creator monetization.

### Medium-Term (based on user demand)
- **AI-powered features** — live fact-checking (like xAI Grok), AI chat bot posting insights during debates, post-debate AI summaries.
- **Discovery feed** — algorithmic feed of recordings (like YouTube), trending debates, followed debaters.
- **Shorts / clips** — short-form debate highlights for sharing and discovery.
- **Comments on recordings** — post-debate discussion threads.
- **Advanced matchmaking** — skill-based matching, topic expertise, language preferences.

### Long-Term (if product-market fit is proven)
- **React Native mobile app**.
- **Panel debates** — 2v2, moderated formats.
- **Flexible duration** — debaters choose debate length (5, 15, 20, 30+ minutes).
- **Creator monetization** — subscriptions to debaters, revenue sharing.
- **Social features** — following, notifications, streaks.
- **Multi-language support**.
- **Embeddable debates** — embed live or recorded debates on external sites.

---

## Strategic Insights

### The Cold-Start Problem

The biggest risk isn't technology — it's the empty room. Real-time matchmaking requires two people online, wanting the same claim, on opposite sides, at the same time. At low user counts, this almost never happens.

**Solution: Scheduled debate challenges.** Instead of relying solely on real-time matchmaking, let debaters challenge opponents to debate at a specific time. An open challenge like "I'll debate anyone on immigration policy at 8pm EST tomorrow" removes the "both online now" constraint and creates a shareable event before the debate even happens.

Scheduled challenges with shareable invite links (`debbly.com/challenge/abc123`) solve cold-start, create pre-debate buzz, and let debaters promote on their existing social channels. This is a growth hack and a matchmaking fix in one feature.

### Supply-Side First (Debaters Are the Priority)

Debbly is a two-sided marketplace: debaters (supply) and audience (demand). The playbook is to **focus on supply first** — find 20-50 people who want to debate on camera regularly (politics Twitter, debate subreddits, college debate teams, small commentary YouTubers). Make their experience incredible. They bring their own audience.

A debater with 500 followers who posts "I'm debating X on Debbly tonight" is the growth engine.

### Recordings > Live

99% of traffic will come from recordings, not live viewership. That's how YouTube and Twitch work too. **Live is the creation mechanism, recordings are the distribution mechanism.**

This means the recording/content pipeline is critical for growth:
- AI-generated clips of heated moments (cross-talk, audience reaction spikes).
- Shareable clips with embed support — this is how debates spread on X and Reddit.
- SEO-optimized recording pages (claim as H1, transcript, timestamps).
- The organic growth loop: debate happens → clip goes viral → new users arrive → some want to debate.

### The 15-Minute Format Is a Feature

Short, structured, time-boxed. This is the TikTok-length advantage over 3-hour podcast debates. The constraint is the brand: **"15 minutes. No scripts. No edits."** Experiment with shorter (5-min lightning rounds) before going longer.

### Content > Reputation

Rankings and leaderboards don't drive growth at early stage. **Content drives growth.** Before building reputation scores, build:
- Debate profiles that look like highlight reels (best clips, win record, topics debated).
- Shareable debater pages — like a creator's YouTube channel.
- Embeddable profiles and clips for debaters to put on their own sites.

### Path to $1B

**Path A: Media platform (the YouTube path)**
Scale to millions of recordings watched monthly. Monetize via ads and creator revenue sharing. Requires massive content volume and discovery algorithms. Timeline: 5-7 years.

**Path B: Creator economy platform (the Patreon/Substack path)**
Focus on power debaters who each have paying fans. Monetize via tipping, subscriptions, pay-per-view. 1,000 debaters x 1,000 fans x $10/month = $120M ARR. Timeline: 3-5 years.

**Strategy: Start with Path B, grow into Path A.** Get debaters earning money on Debbly as fast as possible. When a debater makes their first $100 on the platform, they'll never leave.

---

## Competitive Landscape

| Platform | Format | Why Debbly is different |
|----------|--------|------------------------|
| X / Twitter | Text + Spaces (audio) | No live video debates, no matchmaking |
| Reddit | Text threads | No real-time, no video |
| Clubhouse | Audio rooms | No video, no structured debate format |
| Kialo | Structured text debates | No live interaction, no video |
| YouTube / Twitch | Video streaming | No built-in debate matchmaking, one-way broadcast |

**Debbly's differentiator: live 1v1 video debates with automatic opponent matching.** No other platform combines video, structured debate format, and matchmaking.

---

## For AI Agents Building Features

When building new features, keep these principles in mind:

1. **Simplicity first.** Don't over-engineer. Ship the simplest version that works.
2. **The core loop is sacred.** Claim → Stance → Queue → Match → Live Debate → Recording. Every feature should enhance this loop, not complicate it.
3. **Real-time matters.** Debbly is a live platform. Latency, responsiveness, and real-time feedback are critical.
4. **Mobile-ready.** Everything must work on mobile web. Don't build desktop-only features.
5. **Debaters are the product.** Help debaters grow their audience and reputation. The platform succeeds when debaters succeed.
6. **Audience engagement drives retention.** Make watching debates interactive and fun — chat, reactions, voting, tipping.
7. **Lean decisions.** When in doubt about a feature direction, prefer the option that ships faster and can be validated with real users.
