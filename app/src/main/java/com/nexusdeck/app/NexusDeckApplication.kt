package com.nexusdeck.app

import com.taras.nexusplay.app.NexusPlayApplication

/**
 * Fase 2 (merge): Application aplikasi gabungan.
 *
 * Subclass dari NexusPlayApplication — BUKAN pembangunan ulang komponen
 * Dagger. Komponen Dagger (NexusPlayApplicationComponent, 9 modul, 21
 * @ContributesAndroidInjector) diparameterisasi pada NexusPlayApplication,
 * sehingga subclass ini tetap di-inject oleh graf yang sama tanpa perubahan.
 * onCreate() diwarisi apa adanya: init process-aware (Main/Game) +
 * WorkManager sudah ditangani di sana.
 */
class NexusDeckApplication : NexusPlayApplication()
