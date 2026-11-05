import { reactive, watch } from 'vue'

/**
 * 将 reactive 对象持久化到 localStorage，页面切换 / 刷新后恢复。
 *
 * @param {string} key storage key
 * @param {Record<string, any>} defaults 默认值
 */
export function usePersistedState(key, defaults) {
  const storageKey = `tools.form.${key}`
  let initial = { ...defaults }
  try {
    const raw = localStorage.getItem(storageKey)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (parsed && typeof parsed === 'object') {
        initial = { ...defaults, ...parsed }
      }
    }
  } catch {
    // ignore
  }
  const state = reactive(initial)
  watch(
    state,
    (val) => {
      try {
        localStorage.setItem(storageKey, JSON.stringify(val))
      } catch {
        // ignore
      }
    },
    { deep: true }
  )
  return state
}
