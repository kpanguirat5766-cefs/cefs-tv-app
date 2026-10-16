// CEFS TV pour PC : ouvre la chaîne dans sa propre fenêtre, comme l'application Android.
const { app, BrowserWindow, shell, Menu } = require("electron");
const path = require("path");

const HOME = "https://kpanguirat5766-cefs.github.io/cefs-tv-app/";
const INTERNAL = h => h === "kpanguirat5766-cefs.github.io";

app.commandLine.appendSwitch("autoplay-policy", "no-user-gesture-required");
if (!app.requestSingleInstanceLock()) app.quit();

let win;
function create() {
  win = new BrowserWindow({
    width: 1280, height: 760, minWidth: 360, minHeight: 400,
    backgroundColor: "#110D1B", title: "CEFS TV", autoHideMenuBar: true,
    icon: path.join(__dirname, "assets", "icon.png"),
    webPreferences: { contextIsolation: true, sandbox: true }
  });
  Menu.setApplicationMenu(null);
  win.webContents.setUserAgent(win.webContents.getUserAgent() + " CEFSTV-PC/1.0");

  const outside = url => {
    try { const u = new URL(url); if ((u.protocol === "http:" || u.protocol === "https:") && INTERNAL(u.hostname)) return false; } catch (_) {}
    return true;
  };
  win.webContents.setWindowOpenHandler(({ url }) => { shell.openExternal(url); return { action: "deny" }; });
  win.webContents.on("will-navigate", (e, url) => { if (outside(url)) { e.preventDefault(); shell.openExternal(url); } });

  // F11 : plein écran · F5 : actualiser · Échap : quitter le plein écran
  win.webContents.on("before-input-event", (e, i) => {
    if (i.type !== "keyDown") return;
    if (i.key === "F11") { win.setFullScreen(!win.isFullScreen()); e.preventDefault(); }
    else if (i.key === "F5") { win.webContents.reload(); e.preventDefault(); }
    else if (i.key === "Escape" && win.isFullScreen()) { win.setFullScreen(false); }
  });

  // Sans connexion : page simple qui réessaie toute seule.
  win.webContents.on("did-fail-load", (e, code, desc, url, isMain) => {
    if (!isMain || code === -3) return;
    win.loadURL("data:text/html;charset=utf-8," + encodeURIComponent(
      `<body style="margin:0;height:100vh;display:grid;place-items:center;background:#110D1B;color:#EEE9F7;font:18px 'Segoe UI',sans-serif;text-align:center">
       <div><h1 style="margin:0 0 8px">CEFS TV</h1><p>Pas de connexion Internet.<br>Nouvel essai dans quelques secondes…</p></div>
       <script>setTimeout(()=>location.replace(${JSON.stringify(HOME)}),8000)</script></body>`));
  });

  win.loadURL(HOME);
}

app.on("second-instance", () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.whenReady().then(create);
app.on("window-all-closed", () => app.quit());
