package com.example.metacheck.testutil

import android.content.SharedPreferences

/** Минимальные SharedPreferences в памяти для JVM-тестов. */
class FakePrefs : SharedPreferences {
    private val map = HashMap<String, Any?>()
    override fun getAll(): MutableMap<String, *> = HashMap(map)
    override fun getString(key: String?, defValue: String?) = map[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?) = map[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String?, defValue: Int) = map[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long) = map[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float) = map[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean) = map[key] as? Boolean ?: defValue
    override fun contains(key: String?) = map.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = HashMap<String, Any?>()
        private val removed = HashSet<String>()
        private var clear = false
        override fun putString(k: String?, v: String?) = apply { pending[k!!] = v }
        override fun putStringSet(k: String?, v: MutableSet<String>?) = apply { pending[k!!] = v }
        override fun putInt(k: String?, v: Int) = apply { pending[k!!] = v }
        override fun putLong(k: String?, v: Long) = apply { pending[k!!] = v }
        override fun putFloat(k: String?, v: Float) = apply { pending[k!!] = v }
        override fun putBoolean(k: String?, v: Boolean) = apply { pending[k!!] = v }
        override fun remove(k: String?) = apply { removed += k!! }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) map.clear()
            removed.forEach { map.remove(it) }
            map.putAll(pending)
        }
    }
}
