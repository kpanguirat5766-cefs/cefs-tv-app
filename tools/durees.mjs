// Mesure la durée de chaque vidéo YouTube avec le vrai lecteur (comme sur la chaîne).
import { chromium } from "playwright";
import fs from "fs";
import http from "http";
const d = JSON.parse(fs.readFileSync("docs/data/tv.json", "utf8"));
const page = `<!doctype html><div id="p"></div><script src="https://www.youtube.com/iframe_api"></script>
<script>let P,ready;const R=new Promise(r=>ready=r);function onYouTubeIframeAPIReady(){P=new YT.Player("p",{width:640,height:360,playerVars:{autoplay:1,mute:1,playsinline:1},events:{onReady:()=>ready()}})}
async function dur(id){await R;P.loadVideoById(id);for(let i=0;i<40;i++){await new Promise(r=>setTimeout(r,500));const x=P.getDuration();if(x>0)return Math.round(x)}return 0}</script>`;
const srv = http.createServer((q, s) => { s.writeHead(200, { "content-type": "text/html" }); s.end(page); }).listen(8765);
const b = await chromium.launch({ args: ["--autoplay-policy=no-user-gesture-required"] });
const p = await b.newPage();
await p.goto("http://localhost:8765/");
const out = {};
for (const v of d.videos) {
  if (v.src !== "youtube") continue;
  try { out[v.yt] = await p.evaluate(id => dur(id), v.yt); } catch (e) { out[v.yt] = 0; }
  console.log(v.yt, out[v.yt]);
}
await b.close(); srv.close();
fs.writeFileSync("docs/data/durees.json", JSON.stringify(out, null, 1));
let n = 0;
for (const v of d.videos) { const x = out[v.yt]; if (x > 0 && v.dur !== x) { v.dur = x; n++; } }
if (n) { d.rev = Date.now(); fs.writeFileSync("docs/data/tv.json", JSON.stringify(d)); }
console.log("mises à jour:", n);
