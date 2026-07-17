'use strict';

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('lume', {
  checkKey: (key) => ipcRenderer.invoke('check-key', key),
  getHwid: () => ipcRenderer.invoke('get-hwid'),
  launch: (payload) => ipcRenderer.invoke('launch', payload),
  cancelLaunch: () => ipcRenderer.invoke('cancel-launch'),
  openLicenses: () => ipcRenderer.invoke('open-licenses'),
  closeWindow: () => ipcRenderer.invoke('window-close'),
  minWindow: () => ipcRenderer.invoke('window-min'),
  getTheme: () => ipcRenderer.invoke('get-theme'),
  setTheme: (theme) => ipcRenderer.invoke('set-theme', theme),
  getSettings: () => ipcRenderer.invoke('get-settings'),
  setSettings: (settings) => ipcRenderer.invoke('set-settings', settings),
  getSysInfo: () => ipcRenderer.invoke('get-sysinfo'),
  listWallpapers: () => ipcRenderer.invoke('list-wallpapers'),
  readWallpaper: (name) => ipcRenderer.invoke('read-wallpaper', name),
  openWallpaperFolder: () => ipcRenderer.invoke('open-wallpaper-folder'),
  onStatus: (cb) => ipcRenderer.on('status', (_e, d) => cb(d)),
  onLog: (cb) => ipcRenderer.on('log', (_e, d) => cb(d)),
  onProgress: (cb) => ipcRenderer.on('progress', (_e, d) => cb(d)),
  onGameClosed: (cb) => ipcRenderer.on('game-closed', (_e, d) => cb(d)),
});
