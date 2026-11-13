package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable object SplashRoute
@Serializable object OnboardingRoute
@Serializable object HomeRoute
@Serializable data class ScannerRoute(val templateId: String = "normal", val isBatch: Boolean = false)
@Serializable object ScanPreviewRoute
@Serializable object ImageEditorRoute
@Serializable object PageManagerRoute
@Serializable data class PdfPreviewRoute(val docId: Long)
@Serializable data class PdfEditorRoute(val docId: Long)
@Serializable object ImagesToPdfRoute
@Serializable data class PdfToImagesRoute(val docId: Long = -1L)
@Serializable object MyDocumentsRoute
@Serializable object FavoritesRoute
@Serializable object TrashRoute
@Serializable object SettingsRoute
@Serializable object AboutRoute
@Serializable object PrivacyPolicyRoute
