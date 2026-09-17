# Nibble.love – Community Food Exchange (Static Site)

Clean, modern static rebuild of nibble.love for the Big Island Hawaiʻi community food exchange.

## Pages included
- `index.html` – Home
- `locations.html` – All drop points with Google Maps links
- `how-it-works.html` – How to give & get food + guidelines
- `about.html` – Mission, Marvin Franklin, Battery Bill Hawaii
- `contact.html` – Phone + simple contact form

## How to upload to Cloudflare Pages

1. Unzip this folder.
2. Go to [Cloudflare Dashboard → Pages](https://dash.cloudflare.com/) → Create a project → Upload assets.
3. Drag the entire contents of the `nibble-love` folder (or the folder itself).
4. Deploy.
5. Point your domain `nibble.love` to the Pages project (Custom domains).

### Optional improvements later
- Replace the mailto form on contact.html with Formspree or a Cloudflare Worker form.
- Add a real logo image in `/images/`.
- Add more locations or a simple calendar of exchange times.
- Connect a custom domain and force HTTPS (Cloudflare does this automatically).

## Local preview
Just open `index.html` in a browser, or run a simple static server:

```bash
npx serve .
```

---

Built with pure HTML + CSS + a tiny bit of JS. No build step required.
Mahalo!
