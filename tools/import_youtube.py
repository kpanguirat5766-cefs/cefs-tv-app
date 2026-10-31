"""Ajoute automatiquement à la vidéothèque de CEFS TV les nouvelles vidéos
de la chaîne YouTube de l'Apôtre Guirat Kpan (directs enregistrés compris).
Seules les vidéos parues après la mise en service sont ajoutées."""
import json, re, time, urllib.request, urllib.parse, xml.etree.ElementTree as ET

DATA, LOG = "docs/data/tv.json", "tools/import-youtube.txt"
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
      "Accept-Language": "fr-FR,fr;q=0.9", "Cookie": "CONSENT=YES+1"}
d = json.load(open(DATA, encoding="utf-8"))
cfg = d.setdefault("autoImport", {})
chan = cfg.get("channel") or d.get("ytChannel")
since = cfg.get("since", "2026-10-31T00:00:00+00:00")
known = set(cfg.get("known", []))
first = not cfg.get("known")

def get(url, tries=3):
    for i in range(tries):
        try:
            return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30).read()
        except Exception as e:
            err = e; time.sleep(4 * (i + 1))
    raise err

found, source = [], ""   # (videoId, titre, date ou "")
try:
    ns = {"a": "http://www.w3.org/2005/Atom", "yt": "http://www.youtube.com/xml/schemas/2015"}
    root = ET.fromstring(get("https://www.youtube.com/feeds/videos.xml?channel_id=" + chan))
    for e in root.findall("a:entry", ns):
        found.append((e.findtext("yt:videoId", namespaces=ns), (e.findtext("a:title", namespaces=ns) or "").strip(),
                      e.findtext("a:published", namespaces=ns) or ""))
    source = "flux"
except Exception:
    ids = []
    for tab in ("videos", "streams"):
        try:
            html = get(f"https://www.youtube.com/channel/{chan}/{tab}").decode("utf-8", "ignore")
            for m in re.finditer(r'"videoId":"([A-Za-z0-9_-]{11})"', html):
                if m.group(1) not in ids: ids.append(m.group(1))
        except Exception:
            pass
    for v in ids[:30]:
        found.append((v, "", ""))
    source = "page de la chaîne" if ids else "aucune (YouTube n'a pas répondu)"

have = {v.get("yt") for v in d["videos"]}
skip = set(cfg.get("ignore", []))
added = []
for vid, title, pub in found:
    if not vid or vid in have or vid in skip or vid in known:
        continue
    if first and not pub:          # mise en service : on note l'existant sans l'ajouter
        continue
    if pub and pub < since:
        continue
    if not title:
        try:
            title = json.loads(get("https://www.youtube.com/oembed?format=json&url=" +
                                   urllib.parse.quote("https://www.youtube.com/watch?v=" + vid)))["title"].strip()
        except Exception:
            title = "Vidéo de la chaîne"
    n = 1
    while any(v["id"] == f"y{n}" for v in d["videos"]): n += 1
    d["videos"].insert(1, {"id": f"y{n}", "title": title, "desc": "", "cat": "Chaîne YouTube", "src": "youtube",
                           "yt": vid, "dur": 0, "added": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "auto": True})
    have.add(vid); added.append(title)

newknown = sorted(known | {v for v, _, _ in found if v})
changed = bool(added) or newknown != sorted(known)
if changed:
    cfg["known"] = newknown
    d["rev"] = int(time.time() * 1000)
    json.dump(d, open(DATA, "w", encoding="utf-8"), ensure_ascii=False)
open(LOG, "w", encoding="utf-8").write(
    f"Source : {source}\nVidéos vues sur la chaîne : {len(found)}\nAjoutées cette fois : {len(added)}\n" +
    "".join(f"- {t}\n" for t in added))
print(source, len(found), added)
