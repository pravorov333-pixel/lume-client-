'use strict';
// Tiny standalone script — NOT run as a normal Electron app. Invoked as:
//   electron.exe (with ELECTRON_RUN_AS_NODE=1) watcher.js <gamePid> <relaunchExe> [relaunchArg...]
// This makes it run as plain Node (no Chromium/GPU process, just the V8 runtime —
// a few MB, not the 150-500MB+ a full Electron/Chromium instance holds), so the launcher
// can fully app.quit() while Minecraft is running (freeing all its memory) without losing
// the "reopen automatically when you're done playing" behaviour.
//
// Logic: poll the game process's PID until it's gone, then spawn the launcher again
// (detached, so this watcher can exit cleanly right after) and exit.
const { spawn } = require('child_process');

const [, , pidArg, relaunchExe, ...relaunchArgs] = process.argv;
const pid = Number(pidArg);

function isRunning(p) {
  try {
    // Signal 0 doesn't actually kill anything — it's the standard Node/POSIX way to
    // just check "does a process with this PID exist" (works on Windows too via libuv).
    process.kill(p, 0);
    return true;
  } catch (e) {
    return false;
  }
}

function relaunch() {
  try {
    spawn(relaunchExe, relaunchArgs, { detached: true, stdio: 'ignore' }).unref();
  } catch (e) {
    // Nothing more this watcher can do — worst case the user just double-clicks the
    // launcher icon again themselves, same as if this feature didn't exist at all.
  }
  process.exit(0);
}

if (!pid || !isRunning(pid)) {
  // Bad/missing pid, or the game already closed before we even started polling — reopen now.
  relaunch();
} else {
  const iv = setInterval(() => {
    if (!isRunning(pid)) {
      clearInterval(iv);
      relaunch();
    }
  }, 2000);
}
