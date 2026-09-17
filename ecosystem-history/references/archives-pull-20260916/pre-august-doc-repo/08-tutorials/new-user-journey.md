# Tutorial: new user journey

This walkthrough teaches **support and marketing** how a cautious user experiences RootRecord end-to-end.

## Step 1 — Discovery

User lands on **rootrecord.info**, reads product pages, compares pricing.

**Teaching point:** emphasize **which platform** each app targets (Android-first vs Windows).

## Step 2 — Account creation

User opens **account.html**, signs up, verifies email if required.

**Teaching point:** billing status appears **after** Stripe checkout—not magically at signup unless free tier exists.

## Step 3 — App install

User installs **Business Manager**, **Weather Manager**, or **Token Manager** from the store or sideloads a debug build (internal only).

**Teaching point:** each app has **its own icon and color**—Account Hub is the cross-app spine.

## Step 4 — First session

User signs in inside the app. JWT lands in local storage; API calls succeed.

**Teaching point:** if login fails, distinguish **client network** issues from **API outages** (status page / logs).

## Step 5 — Paid features

User upgrades via Stripe portal linked from **Account Hub** or site.

**Teaching point:** entitlements refresh through **`/api/auth/entitlement`**—if UI lags, user can pull-to-refresh or re-login as temporary workaround (fix server-side if persistent).

## Step 6 — Ongoing use

User receives push notifications (if enabled) and scheduled weather/business data updates.

**Teaching point:** Android battery savers can delay background behavior—set expectations.

## Related reading

- [../04-accounts/billing-and-licensing.md](../04-accounts/billing-and-licensing.md)
