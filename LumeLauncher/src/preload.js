'use strict';

const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('lume', {
  checkKey: (key) => ipcRenderer.invoke('check-key', key),
  getHwid: () => ipcRenderer.invoke('get-hwid'),
  launch: (payload) => ipcRenderer.invoke('launch', payload),
  closeWindow: () => ipcRenderer.invoke('window-close'),
  minWindow: () => ipcRenderer.invoke('window-min'),
  onStatus: (cb) => ipcRenderer.on('status', (_e, d) => cb(d)),
  onLog: (cb) => ipcRenderer.on('log', (_e, d) => cb(d)),
  onProgress: (cb) => ipcRenderer.on('progress', (_e, d) => cb(d)),
  onGameClosed: (cb) => ipcRenderer.on('game-closed', (_e, d) => cb(d)),
});
