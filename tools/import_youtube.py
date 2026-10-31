"""Ajoute automatiquement à la vidéothèque de CEFS TV les nouvelles vidéos
de la chaîne YouTube de l'Apôtre Guirat Kpan (directs enregistrés compris).
Seules les vidéos publiées après la date de départ sont prises."""
import json, time, urllib.request, xml.etree.ElementTree as ET

DATA = "docs/data/tv.json"
d = json.load(open(DATA, encoding="utf-8"))
cfg = d.get("autoImport") or {}
chan = cfg.get("channel") or d.get("ytChannel")
since = cfg.get("since", "2026-10-31T00:00:00+00:00")
if not chan:
    raise SystemExit("pas de chaîne")

req = urllib.request.Request("https://www.youtube.com/feeds/videos.xml?channel_id=" + chan,
                             headers={"User-Agent": "Mozilla/5.0"})
xml = urllib.request.urlopen(req, timeout=30).read()
ns = {"a": "http://www.w3.org/2005/Atom", "yt": "http://www.youtube.com/xml/schemas/2015"}
root = ET.fromstring(xml)

have = {v.get("yt") for v in d["videos"]}
skip = set(cfg.get("ignore", []))
added = []
for e in root.findall("a:entry", ns):
    vid = e.findtext("yt:videoId", namespaces=ns)
    title = (e.findtext("a:title", namespaces=ns) or "").strip()
    pub = e.findtext("a:published", namespaces=ns) or ""
    if not vid or vid in have or vid in skip or pub < since:
        continue
    n = 1
    while any(v["id"] == f"y{n}" for v in d["videos"]):
        n += 1
    d["videos"].insert(1, {"id": f"y{n}", "title": title or "Vidéo de la chaîne", "desc": "",
                           "cat": "Chaîne YouTube", "src": "youtube", "yt": vid, "dur": 0,
                           "added": pub[:19] + "Z", "auto": True})
    have.add(vid)
    added.append(title)

if added:
    d["rev"] = int(time.time() * 1000)
    json.dump(d, open(DATA, "w", encoding="utf-8"), ensure_ascii=False)
entries = root.findall("a:entry", ns)
latest = entries[0].findtext("a:title", namespaces=ns) if entries else "-"
open("tools/import-youtube.txt", "w", encoding="utf-8").write(
    f"Flux lu : {len(entries)} vidéos récentes sur la chaîne\nDernière : {latest}\nAjoutées cette fois : {len(added)}\n")
print("ajoutées :", added)
