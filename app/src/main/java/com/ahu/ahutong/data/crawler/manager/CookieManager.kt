package com.ahu.ahutong.data.crawler.manager

import com.ahu.ahutong.AHUApplication
import com.ahu.ahutong.data.api.AHUCookieJar
import com.franmontiel.persistentcookiejar.cache.SetCookieCache
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor

object CookieManager {
    private val encryptedPersistor = EncryptedCookiePersistor().also { encrypted ->
        // One-time, destructive migration: plaintext cookies must not remain on disk.
        val legacy = SharedPrefsCookiePersistor(AHUApplication.getApp())
        val legacyCookies = legacy.loadAll()
        if (legacyCookies.isNotEmpty()) encrypted.saveAll(legacyCookies)
        legacy.clear()
    }

    val cookieJar = AHUCookieJar(SetCookieCache(), encryptedPersistor)

}
