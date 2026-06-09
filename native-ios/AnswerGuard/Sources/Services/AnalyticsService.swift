import Foundation
import os
#if canImport(PostHog)
import PostHog
#endif
#if canImport(AdServices)
import AdServices
#endif

/// Analytics Service for PostHog integration
@MainActor
final class AnalyticsService {
    static let shared = AnalyticsService()
    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "AnswerGuard", category: "Analytics")

    private var initialized = false
    private let distinctIdDefaultsKey = "posthog_distinct_id"
    private let hasFirstOpenedKey = "has_first_opened"
    private let hasFirstProtectionEnabledKey = "has_first_protection_enabled"
    private let hasTrackedApplicationInstalledKey = "has_tracked_application_installed"
    private let utmKeys = ["utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term"]
    private let appleAdsAttributionFetchedKey = "apple_ads_attribution_fetched"

    private var apiKey: String {
        Bundle.main.object(forInfoDictionaryKey: "POSTHOG_API_KEY") as? String ?? ""
    }
    private var appVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "unknown"
    }
    private let host = "https://us.i.posthog.com"

#if DEBUG
    var testEventHandler: ((_ event: String, _ properties: [String: Any]?) -> Void)?
#endif

    private init() {}

    private var analyticsContextProperties: [String: Any] {
        [
            "platform": "ios",
            "app_name": "answerguard",
            "app_version": appVersion,
            AnalyticsProperties.environment: environment,
            AnalyticsProperties.buildAudience: buildAudience,
            AnalyticsProperties.buildType: buildType,
            AnalyticsProperties.runtimeTarget: runtimeTarget,
            AnalyticsProperties.distributionChannel: distributionChannelValue,
            "is_internal": isInternalUser,
        ]
    }

    private var distributionChannelValue: String {
#if DEBUG
        return "dev"
#else
        #if targetEnvironment(simulator)
        return "simulator"
        #else
        return "app_store"
        #endif
#endif
    }

    private var isInternalUser: Bool {
#if DEBUG
        return true
#else
        #if targetEnvironment(simulator)
        return true
        #else
        return false
        #endif
#endif
    }

    private var buildAudience: String {
#if DEBUG
        return "dev"
#else
        #if targetEnvironment(simulator)
        return "dev"
        #else
        return "live"
        #endif
#endif
    }

    private var buildType: String {
#if DEBUG
        return "debug"
#else
        return "release"
#endif
    }

    private var environment: String {
        buildAudience == "live" ? "production" : "development"
    }

    private var runtimeTarget: String {
#if targetEnvironment(simulator)
        return "simulator"
#else
        return "device"
#endif
    }

    private func mergedProperties(_ properties: [String: Any]?) -> [String: Any] {
        guard var props = properties else { return analyticsContextProperties }
        for (key, value) in analyticsContextProperties {
            props[key] = value
        }
        return props
    }

    func initialize() {
        guard !initialized else { return }
        guard !apiKey.isEmpty else {
            logger.notice("No API key configured - analytics disabled")
            return
        }

#if canImport(PostHog)
        let config = PostHogConfig(apiKey: apiKey, host: host)
        config.captureApplicationLifecycleEvents = false
        config.captureScreenViews = false
        PostHogSDK.shared.setup(config)
#endif
        initialized = true
        let distinctId = getOrCreateDistinctId()
        identify(userId: distinctId, properties: analyticsContextProperties)
        trackApplicationLifecycleEvents()
        logger.info("PostHog initialized")

        trackFirstOpenIfNeeded()
        fetchAppleSearchAdsAttribution()
    }

    func track(_ event: String, properties: [String: Any]? = nil) {
        let payload = mergedProperties(properties)
#if DEBUG
        testEventHandler?(event, payload)
#endif
        guard initialized else { return }
#if canImport(PostHog)
        PostHogSDK.shared.capture(event, properties: payload)
#endif
    }

    func screen(_ screenName: String, properties: [String: Any]? = nil) {
        guard initialized else { return }
#if canImport(PostHog)
        PostHogSDK.shared.screen(screenName, properties: mergedProperties(properties))
#endif
    }

    func identify(userId: String, properties: [String: Any]? = nil) {
        guard initialized else { return }
#if canImport(PostHog)
        PostHogSDK.shared.identify(userId, userProperties: mergedProperties(properties))
#endif
    }

    func reset() {
        guard initialized else { return }
#if canImport(PostHog)
        PostHogSDK.shared.reset()
#endif
    }

    func flush() {
        guard initialized else { return }
#if canImport(PostHog)
        PostHogSDK.shared.flush()
#endif
    }

    // MARK: - UTM Attribution

    func trackDeepLink(_ url: URL) {
        guard initialized else { return }
        let utmParams = extractUtmParams(from: url)
        guard !utmParams.isEmpty else { return }

        let defaults = UserDefaults.standard
        for (key, value) in utmParams {
            defaults.set(value, forKey: key)
        }

#if canImport(PostHog)
        PostHogSDK.shared.identify(
            PostHogSDK.shared.getDistinctId(),
            userProperties: utmParams
        )
#endif
        track(AnalyticsEvents.deepLinkOpened, properties: utmParams)
    }

    private func extractUtmParams(from url: URL) -> [String: Any] {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: true) else {
            return [:]
        }
        var params: [String: Any] = [:]
        for key in utmKeys {
            if let value = components.queryItems?.first(where: { $0.name == key })?.value,
               !value.isEmpty {
                params[key] = value
            }
        }
        if let path = components.path as String?, !path.isEmpty {
            params["referring_path"] = path
        }
        return params
    }

    // MARK: - Apple Search Ads Attribution

    func fetchAppleSearchAdsAttribution() {
        guard initialized else { return }
        guard !UserDefaults.standard.bool(forKey: appleAdsAttributionFetchedKey) else { return }

#if canImport(AdServices)
        if #available(iOS 14.3, *) {
            guard let token = try? AAAttribution.attributionToken() else { return }

            var request = URLRequest(url: URL(string: "https://api-adservices.apple.com/api/v1/")!)
            request.httpMethod = "POST"
            request.setValue("text/plain", forHTTPHeaderField: "Content-Type")
            request.httpBody = Data(token.utf8)

            URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
                guard let data = data, error == nil else { return }
                guard let result = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }

                let campaignId = result["campaignId"] as? Int ?? 0
                guard campaignId != 0, campaignId != 1234567890 else {
                    DispatchQueue.main.async {
                        guard let key = self?.appleAdsAttributionFetchedKey else { return }
                        UserDefaults.standard.set(true, forKey: key)
                    }
                    return
                }

                let attribution: [String: Any] = [
                    "utm_source": "apple_search_ads",
                    "utm_medium": "asa",
                    "utm_campaign": result["campaignName"] as? String ?? "unknown",
                    "apple_ads_campaign_id": campaignId,
                ]

                DispatchQueue.main.async { [weak self] in
                    guard let self else { return }
                    UserDefaults.standard.set(true, forKey: self.appleAdsAttributionFetchedKey)

#if canImport(PostHog)
                    PostHogSDK.shared.identify(PostHogSDK.shared.getDistinctId(), userProperties: attribution)
                    PostHogSDK.shared.capture(AnalyticsEvents.appleAdsAttribution, properties: attribution)
#endif
                }
            }.resume()
        }
#endif
    }

    // MARK: - Events

    private func trackApplicationLifecycleEvents() {
        let defaults = UserDefaults.standard
        track(AnalyticsEvents.applicationOpened)
        guard !defaults.bool(forKey: hasTrackedApplicationInstalledKey) else { return }
        track(AnalyticsEvents.applicationInstalled)
        defaults.set(true, forKey: hasTrackedApplicationInstalledKey)
    }

    private func trackFirstOpenIfNeeded() {
        let defaults = UserDefaults.standard
        guard !defaults.bool(forKey: hasFirstOpenedKey) else { return }
        track(AnalyticsEvents.firstOpen)
        defaults.set(true, forKey: hasFirstOpenedKey)
    }

    func trackFirstProtectionEnabledIfNeeded() {
        guard initialized else { return }
        let defaults = UserDefaults.standard
        guard !defaults.bool(forKey: hasFirstProtectionEnabledKey) else { return }
        track(AnalyticsEvents.firstProtectionEnabled)
        defaults.set(true, forKey: hasFirstProtectionEnabledKey)
    }

    private func getOrCreateDistinctId() -> String {
        let defaults = UserDefaults.standard
        if let existing = defaults.string(forKey: distinctIdDefaultsKey), !existing.isEmpty {
            return existing
        }
        let generated = UUID().uuidString
        defaults.set(generated, forKey: distinctIdDefaultsKey)
        return generated
    }
}

enum AnalyticsEvents {
    static let applicationInstalled = "Application Installed"
    static let applicationOpened = "Application Opened"
    static let callScreeningEnabled = "call_screening_enabled"
    static let callScreeningStatusRefreshed = "call_screening_status_refreshed"
    static let settingsChanged = "settings_changed"
    static let reviewPromptRequested = "review_prompt_requested"
    static let writeReviewTapped = "write_review_tapped"
    static let paywallViewed = "paywall_viewed"
    static let paywallDismissed = "paywall_dismissed"
    static let paywallPurchaseAttempt = "paywall_purchase_attempt"
    static let paywallPurchaseSuccess = "paywall_purchase_success"
    static let paywallPurchaseResult = "paywall_purchase_result"
    static let paywallRestoreResult = "paywall_restore_result"
    static let screeningServiceError = "screening_service_error"

    static let deepLinkOpened = "deep_link_opened"
    static let appleAdsAttribution = "apple_ads_attribution"
    static let firstOpen = "first_open"
    static let firstProtectionEnabled = "first_protection_enabled"
}

enum AnalyticsProperties {
    static let entryPoint = "entry_point"
    static let result = "result"
    static let productId = "product_id"
    static let environment = "environment"
    static let buildAudience = "build_audience"
    static let buildType = "build_type"
    static let runtimeTarget = "runtime_target"
    static let distributionChannel = "distribution_channel"
    static let appName = "app_name"
}
