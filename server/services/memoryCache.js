export class MemoryCache {
  #items = new Map();

  get(key) {
    const item = this.#items.get(key);
    if (!item || item.expires <= Date.now()) {
      this.#items.delete(key);
      return null;
    }
    return item.value;
  }

  set(key, value, ttl) {
    if (this.#items.size >= 500) this.#items.delete(this.#items.keys().next().value);
    this.#items.set(key, { value, expires: Date.now() + ttl });
  }
}
