# Hello Internet
### From Local App to Hosted Paid Service — The Decision Record

A brief record of **how** and **why** we decided to turn The Game Pensieve from a single-user app
that runs locally into a hosted, multi-tenant service with paid accounts.

## The problem we were solving
Today the app is open and single-user. To host it and charge for it, four things had to be true:
visitors must **authenticate**, each user must see **only their own data**, paying users get **more
than free ones**, and it must **run somewhere** without surprise bills — all while keeping the public
portfolio version intact.

## The choices and why

**1. Keep one public repo; gate auth behind a Spring profile (not a fork).**
A fork means maintaining two diverging codebases forever. Instead, Spring Security is a
*conditional* layer toggled by the `secured` profile: the public build behaves exactly as today, the
hosted build requires auth — same code. Bonus: the auth work stays *visible* in the portfolio.

**2. Authenticate with JWT (OIDC/social login later).**
Stateless tokens scale horizontally and fit our frontend's server-to-server model. We accepted JWT's
weak spot (revocation) by using short-lived access tokens + refresh. Social login is a later add-on,
not a launch need.

**3. Multi-tenancy by shared schema + an `owner_id` column, enforced by Postgres RLS.**
Three models existed: one shared table with an owner column, a schema per user, or a database per
user. Shared-schema is the industry-standard that scales and tells the "designed for scale" story.
Its one risk — a forgotten `WHERE owner_id` leaking data in our handwritten SQL — is closed by
**Row-Level Security**, which enforces isolation in the database itself. We kept it simple:
**one user = one tenant** (orgs/teams are out of scope this round).

**4. Money model: Paddle as Merchant of Record, billed annually.**
As a solo dev, the worst part of selling globally is sales-tax/VAT compliance. A Merchant of Record
(Paddle) becomes the legal seller and handles that for us. We bill **annually** because the fixed
per-transaction fee (~$0.30) destroys a $1/month charge (~33% lost) but is trivial on a yearly one
(~5%).

**5. Entitlements live in OUR backend, not the payment processor.**
We wanted to grant free trials, comps, and promotions ourselves without going through Paddle. So the
backend owns an adjustable entitlement (a plan and an "access-until" date). Paddle's webhook merely
updates it. This also lets us **build and launch the whole paid model before integrating Paddle** —
the processor is automation, not a dependency.

**6. The product's value gate is *filtering*.**
Data entry is the hard part (a future iteration), but **filtering is what makes the collection
valuable**. So: **Guests** can read and filter the public "my collection" showcase (the hook) but
can't write; **Paid** users read/filter/write their own data; **Lapsed** users can still read their
data but **lose filtering** until they renew. We never delete a user's data.

**7. Host on a single fixed-price VPS (not autoscaling cloud).**
This isn't a vital service, so predictable cost beat elastic scale. A fixed box **can't autoscale
into a surprise bill**, runs all three containers (frontend, backend, Postgres) from our existing
Compose file, and stays cheap. We documented Cloud Run + Neon as the future scale-up path, and noted
guardrails (Cloudflare in front, billing alerts) for it.

**8. Sessions: the browser never holds the token.**
Our frontend is a Backend-for-Frontend, so the Next.js server holds the JWT and the browser gets only
an opaque, encrypted **httpOnly cookie**. This removes the classic token-theft risk and gives the
server a clean place to silently refresh tokens.

**9. Transactional email via Resend** — simplest to start; not a decision worth over-thinking.

## The thread that ties it together
Every choice is optimized for the same four things, in priority order:
1. **Functionality first** (the product must actually work for paying users),
2. **Don't fork** (one visible, maintainable codebase),
3. **No surprises** (bill-certain hosting, DB-enforced data isolation),
4. **Sequence to de-risk** — build auth → isolation → entitlements → deploy, and integrate the
   **payment processor last**, because manual entitlements made it the easiest thing to defer.
