import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const appSource = readFileSync(new URL('../public/app.js', import.meta.url), 'utf8');

function boardWithAlarm(registration) {
  const elements = new Map();
  const storage = new Map();
  storage.set('checkit-alarms', JSON.stringify([{
    id: 'departure-1', line: '6', destination: 'Flughafen',
    stopId: 'stop-1', stopName: 'Bremen Hbf',
    fireAt: Date.now() + 60_000, minutesBefore: 5
  }]));
  const element = id => {
    if (!elements.has(id)) elements.set(id, {
      classList: { add() {}, remove() {}, contains() { return true; } },
      addEventListener() {}, querySelectorAll() { return []; },
      textContent: '', innerHTML: ''
    });
    return elements.get(id);
  };
  const notifications = [];
  const sandbox = vm.createContext({
    document: { getElementById: element, addEventListener() {} },
    localStorage: {
      getItem: key => storage.get(key) ?? null,
      setItem: (key, value) => storage.set(key, value)
    },
    window: { Notification: true },
    Notification: class {
      static permission = 'granted';
      constructor() { throw new TypeError('Mobile browsers reject Notification()'); }
    },
    navigator: { serviceWorker: {
      register: async () => registration,
      getRegistration: async () => registration
    } },
    fetch: () => new Promise(() => {}),
    setTimeout: () => 1, clearTimeout() {}, setInterval() {},
    URLSearchParams, AbortController, Intl, Date
  });
  vm.runInContext(appSource, sandbox);
  return { sandbox, elements, storage, notifications };
}

test('mobile web alarm uses the service worker when Notification() is unavailable', async () => {
  const shown = [];
  const registration = {
    active: {},
    showNotification: async (title, options) => shown.push({ title, body: options.body })
  };
  const { sandbox, elements, storage } = boardWithAlarm(registration);
  await vm.runInContext('fireAlarm("departure-1")', sandbox);
  assert.equal(shown.length, 1);
  assert.match(shown[0].body, /Linie 6 nach Flughafen/);
  assert.equal(JSON.parse(storage.get('checkit-alarms')).length, 0);
  assert.match(elements.get('status').textContent, /Flughafen/);
});

test('web alarm reports a failed system notification in the board', async () => {
  const { sandbox, elements } = boardWithAlarm(undefined);
  await vm.runInContext('fireAlarm("departure-1")', sandbox);
  assert.match(elements.get('status').textContent, /Systembenachrichtigung nicht verfügbar/);
});
