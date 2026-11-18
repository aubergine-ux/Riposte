// Test harness: connects bot clients to a local server and drives them from a control file.
// Usage: node harness.js <ctlFile> <logFile> name1 name2 ...
// Control lines: "<bot|*> <action> [args]"
const mineflayer = require('mineflayer');
const fs = require('fs');

const [ctlFile, logFile, ...names] = process.argv.slice(2);
const HOST = process.env.MC_HOST || 'localhost';
const PORT = parseInt(process.env.MC_PORT || '25599', 10);
const VERSION = process.env.MC_VERSION || '26.1';

const out = fs.createWriteStream(logFile, { flags: 'a' });
const log = (who, msg) => out.write(`${new Date().toISOString().slice(11, 23)} [${who}] ${msg}\n`);

const nbt = require('prismarine-nbt');
let ChatMessage = null;
const toJson = (c) => {
  if (c == null) return null;
  if (typeof c === 'string') { try { return JSON.parse(c); } catch (e) { return c; } }
  if (c.type && c.value !== undefined && typeof c.type === 'string') { try { return nbt.simplify(c); } catch (e) { return c; } }
  if (c.json) return c.json;
  return c;
};
const flatten = (j) => {
  if (j == null) return '';
  if (typeof j === 'string') return j;
  if (Array.isArray(j)) return j.map(flatten).join('');
  if (typeof j !== 'object') return String(j);
  let s = '';
  if (j[''] !== undefined) s += flatten(j['']);
  if (j.text !== undefined) s += flatten(j.text);
  if (j.translate !== undefined) s += j.translate + (j.with ? '(' + flatten(j.with) + ')' : '');
  if (j.extra) s += flatten(j.extra);
  return s;
};
const plain = (c) => {
  try { return flatten(toJson(c)); } catch (e) { return '?'; }
};

const bots = {};
const fights = {};

function itemName(item) {
  if (!item) return null;
  let name = item.name;
  try {
    const comp = (item.components || []).find((x) => x.type === 'custom_name');
    const custom = comp ? comp.data : item.customName;
    if (custom) name = plain(custom) || name;
  } catch (e) { /* ignore */ }
  return `${name}x${item.count}`;
}

function spawnBot(name) {
  const bot = mineflayer.createBot({ host: HOST, port: PORT, username: name, version: VERSION, auth: 'offline' });
  bots[name] = bot;
  trackScoreboard(bot);
  bot.on('login', () => log(name, 'LOGIN'));
  bot.on('spawn', () => log(name, `SPAWN ${fmtPos(bot)}`));
  bot.on('messagestr', (msg, pos) => {
    if (pos === 'game_info') return; // action bar is noisy
    if (msg.trim()) log(name, `CHAT ${msg}`);
  });
  bot.on('message', (msg) => {
    const cmds = [];
    const walk = (j) => {
      if (!j || typeof j !== 'object') return;
      const ce = j.clickEvent || j.click_event;
      if (ce && (ce.value || ce.command)) cmds.push(ce.value || ce.command);
      (j.extra || []).forEach(walk);
      (j.with || []).forEach(walk);
    };
    try { walk(msg.json || msg); } catch (e) { /* ignore */ }
    if (cmds.length) log(name, `CLICKABLE ${cmds.join(' , ')}`);
  });
  bot.on('actionBar', (msg) => {
    const s = plain(msg);
    if (/COMBO|Watching/.test(s)) log(name, `ACTIONBAR ${s}`);
  });
  bot.on('title', (text, type) => { if (type === 'title') log(name, `TITLE ${plain(text)}`); });
  bot.on('windowOpen', (win) => {
    const items = win.slots.slice(0, win.inventoryStart)
      .map((it, i) => (it && !/pane/.test(it.name) ? `${i}:${itemName(it)}` : null)).filter(Boolean);
    log(name, `WINDOW "${plain(win.title)}" ${items.join(' | ')}`);
  });
  bot.on('death', () => log(name, 'DEATH'));
  bot.on('kicked', (reason) => log(name, `KICKED ${plain(reason)}`));
  bot.on('error', (err) => log(name, `ERROR ${err.message}`));
  bot.on('end', (reason) => log(name, `END ${reason}`));
}

function safeJson(s) {
  if (typeof s !== 'string') return s;
  try { return JSON.parse(s); } catch (e) { return s; }
}

function fmtPos(bot) {
  const p = bot.entity && bot.entity.position;
  return p ? `${p.x.toFixed(1)},${p.y.toFixed(1)},${p.z.toFixed(1)} ${bot.game && bot.game.gameMode}` : '?';
}

function sidebar(bot) {
  const sb = bot._rp;
  if (!sb || !sb.sidebar) return 'none';
  const obj = sb.objectives[sb.sidebar] || { title: '', scores: {} };
  const items = Object.values(obj.scores).sort((a, b) => b.value - a.value);
  return `"${obj.title}" ` + items.map((i) => i.text).join(' / ');
}

function trackScoreboard(bot) {
  bot._rp = { objectives: {}, sidebar: null };
  const c = bot._client;
  c.on('scoreboard_objective', (p) => {
    if (p.action === 1) { delete bot._rp.objectives[p.name]; return; }
    const o = bot._rp.objectives[p.name] || (bot._rp.objectives[p.name] = { scores: {} });
    o.title = plain(p.displayText);
  });
  c.on('scoreboard_display_objective', (p) => { if (p.position === 1) bot._rp.sidebar = p.name; });
  c.on('scoreboard_score', (p) => {
    const o = bot._rp.objectives[p.scoreName];
    if (!o) return;
    o.scores[p.itemName] = { value: p.value, text: p.display_name ? plain(p.display_name) : p.itemName };
  });
  c.on('reset_score', (p) => {
    const o = bot._rp.objectives[p.objective_name || p.objectiveName];
    if (o) delete o.scores[p.entity_name || p.entityName];
  });
}

function startFight(name, targetName, opts = {}) {
  stopFight(name);
  const bot = bots[name];
  let lastHit = 0;
  const interval = setInterval(() => {
    const target = bot.players[targetName] && bot.players[targetName].entity;
    if (!target || !bot.entity) {
      bot.clearControlStates();
      return;
    }
    const dist = bot.entity.position.distanceTo(target.position);
    bot.lookAt(target.position.offset(0, target.height * 0.85, 0), true);
    bot.setControlState('forward', dist > 2.6);
    bot.setControlState('sprint', dist > 2.6);
    const now = Date.now();
    if (dist < 3.4 && now - lastHit > (opts.cps ? 1000 / opts.cps : 600)) {
      bot.attack(target);
      lastHit = now;
    }
  }, 50);
  fights[name] = interval;
}

function stopFight(name) {
  if (fights[name]) clearInterval(fights[name]);
  delete fights[name];
  if (bots[name]) bots[name].clearControlStates();
}

async function run(name, action, args) {
  const bot = bots[name];
  if (!bot) return log(name, `NO BOT`);
  switch (action) {
    case 'chat': bot.chat(args.join(' ')); break;
    case 'slot': bot.setQuickBarSlot(parseInt(args[0], 10)); break;
    case 'use': bot.activateItem(); setTimeout(() => bot.deactivateItem(), 100); break;
    case 'click': {
      const win = bot.currentWindow;
      if (!win) return log(name, 'CLICK no window');
      await bot.clickWindow(parseInt(args[0], 10), parseInt(args[1] || '0', 10), parseInt(args[2] || '0', 10)).catch((e) => log(name, `CLICK err ${e.message}`));
      break;
    }
    case 'close': if (bot.currentWindow) bot.closeWindow(bot.currentWindow); break;
    case 'inv': log(name, `INV ${bot.inventory.slots.map((it, i) => (it ? `${i}:${itemName(it)}` : null)).filter(Boolean).join(' | ')}`); break;
    case 'held': log(name, `HELD ${itemName(bot.heldItem)}`); break;
    case 'sidebar': log(name, `SIDEBAR ${sidebar(bot)}`); break;
    case 'pos': log(name, `POS ${fmtPos(bot)} hp=${bot.health} food=${bot.food}`); break;
    case 'fight': startFight(name, args[0], { cps: args[1] ? parseFloat(args[1]) : 0 }); break;
    case 'stop': stopFight(name); break;
    case 'control': bot.setControlState(args[0], args[1] === 'on'); break;
    case 'look': bot.look(parseFloat(args[0]) * Math.PI / 180, parseFloat(args[1] || '0') * Math.PI / 180, true); break;
    case 'jump': bot.setControlState('jump', true); setTimeout(() => bot.setControlState('jump', false), 300); break;
    case 'place': {
      // place the held block on the block below-in-front
      const ref = bot.blockAt(bot.entity.position.offset(parseFloat(args[0] || '0'), parseFloat(args[1] || '-1'), parseFloat(args[2] || '0')));
      if (ref) await bot.placeBlock(ref, new (require('vec3'))(0, 1, 0)).catch((e) => log(name, `PLACE err ${e.message}`));
      break;
    }
    case 'dig': {
      const b = bot.blockAt(bot.entity.position.offset(parseFloat(args[0]), parseFloat(args[1]), parseFloat(args[2])));
      if (b) await bot.dig(b).catch((e) => log(name, `DIG err ${e.message}`));
      log(name, `DIG ${b && b.name}`);
      break;
    }
    case 'blockat': {
      const b = bot.blockAt(bot.entity.position.offset(parseFloat(args[0]), parseFloat(args[1]), parseFloat(args[2])));
      log(name, `BLOCK ${b && b.name}`);
      break;
    }
    case 'leftclick': {
      const b = bot.blockAt(bot.entity.position.offset(parseFloat(args[0]), parseFloat(args[1]), parseFloat(args[2])));
      bot.dig(b).catch(() => {});
      setTimeout(() => bot.stopDigging(), 250);
      break;
    }
    case 'rightclick': {
      const b = bot.blockAt(bot.entity.position.offset(parseFloat(args[0]), parseFloat(args[1]), parseFloat(args[2])));
      await bot.activateBlock(b).catch((e) => log(name, `RCLICK err ${e.message}`));
      break;
    }
    case 'quit': stopFight(name); bot.quit(); break;
    case 'join': spawnBot(name); break;
    default: log(name, `UNKNOWN ${action}`);
  }
}

names.forEach((n, i) => setTimeout(() => spawnBot(n), i * 600));

// follow the control file
let offset = fs.existsSync(ctlFile) ? fs.statSync(ctlFile).size : 0;
setInterval(() => {
  if (!fs.existsSync(ctlFile)) return;
  const size = fs.statSync(ctlFile).size;
  if (size <= offset) return;
  const fd = fs.openSync(ctlFile, 'r');
  const buf = Buffer.alloc(size - offset);
  fs.readSync(fd, buf, 0, buf.length, offset);
  fs.closeSync(fd);
  offset = size;
  for (const line of buf.toString().split('\n')) {
    const parts = line.trim().split(/\s+/);
    if (parts.length < 2) continue;
    const [who, action, ...args] = parts;
    if (action === 'join' && !bots[who]) { spawnBot(who); continue; }
    const targets = who === '*' ? Object.keys(bots) : [who];
    targets.forEach((t) => run(t, action, args).catch((e) => log(t, `ERR ${e.message}`)));
  }
}, 100);

process.on('uncaughtException', (e) => log('harness', `EXC ${e.stack.split('\n').slice(0, 2).join(' ')}`));
