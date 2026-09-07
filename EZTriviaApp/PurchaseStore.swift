import Foundation
import StoreKit

/// The one-time "Remove Ads" purchase.
///
/// The entitlement is cached in UserDefaults and published immediately at
/// launch. StoreKit's own entitlement check is asynchronous, and a player who
/// has paid should never watch the banner they bought their way out of appear
/// for a moment before it disappears. The cache is only ever a head start:
/// `refreshEntitlements()` is authoritative and will correct it, including
/// revoking the entitlement after a refund.
@MainActor
final class PurchaseStore: ObservableObject {
    static let removeAdsProductID = "com.rsm.eztrivia.removeads"

    @Published private(set) var hasRemovedAds: Bool
    @Published private(set) var removeAdsProduct: Product?
    @Published private(set) var isPurchasing = false
    @Published var errorMessage: String?

    private let defaults: UserDefaults
    private let entitlementKey = "purchase.removeAds.v1"

    // Held for the lifetime of the app rather than cancelled in deinit: this
    // object is a @StateObject on the App itself, so it never goes away, and a
    // deinit touching main-actor state is a concurrency problem in waiting.
    private var updatesTask: Task<Void, Never>?

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        hasRemovedAds = defaults.bool(forKey: entitlementKey)

        // Purchases made on another device, Ask to Buy approvals, and refunds
        // all arrive here rather than through the purchase call.
        updatesTask = Task { [weak self] in
            for await update in Transaction.updates {
                await self?.apply(update)
            }
        }
    }

    func start() async {
        await loadProduct()
        await refreshEntitlements()
    }

    /// Re-fetch only when there is nothing to show.
    ///
    /// The product is loaded once at launch, and a single failure there used to
    /// strand Settings on "unavailable right now" for the whole session with
    /// nothing the player could do about it -- a transient network error at the
    /// wrong moment was indistinguishable from a product that does not exist.
    /// Settings calls this on appear, so simply revisiting the screen retries.
    /// It no-ops once a product is in hand, so it costs nothing in the normal
    /// case.
    func reloadProductIfMissing() async {
        guard removeAdsProduct == nil else { return }
        await loadProduct()
    }

    /// Why the product is not on screen, when it is not.
    ///
    /// `try?` collapsed two very different situations into the same nil: the
    /// request failed, and the request succeeded but App Store Connect returned
    /// nothing for this identifier. Settings said "unavailable right now" for
    /// both, which is exactly the information a person debugging it does not
    /// have. StoreKit returns no product at all while one sits in Missing
    /// Metadata, so "not in the catalogue" is the common case and worth naming.
    enum LoadFailure: Equatable {
        case notInCatalog
        case requestFailed(String)
    }

    @Published private(set) var loadFailure: LoadFailure?

    func loadProduct() async {
        do {
            let products = try await Product.products(for: [Self.removeAdsProductID])
            removeAdsProduct = products.first
            loadFailure = products.isEmpty ? .notInCatalog : nil
            if products.isEmpty {
                Telemetry.log("iap.product_missing", parameters: ["id": Self.removeAdsProductID])
            }
        } catch {
            removeAdsProduct = nil
            loadFailure = .requestFailed(error.localizedDescription)
            Telemetry.record(error)
        }
    }

    func refreshEntitlements() async {
        var owned = false
        for await result in Transaction.currentEntitlements {
            guard case let .verified(transaction) = result else { continue }
            if transaction.productID == Self.removeAdsProductID, transaction.revocationDate == nil {
                owned = true
            }
        }
        setRemovedAds(owned)
    }

    func purchase() async {
        guard let product = removeAdsProduct, !isPurchasing else { return }
        isPurchasing = true
        defer { isPurchasing = false }

        do {
            switch try await product.purchase() {
            case let .success(verification):
                await apply(verification)
            case .pending:
                // Ask to Buy: the parent has not approved it yet. Transaction
                // .updates delivers the result whenever that happens, so there
                // is nothing to report and nothing to wait on here.
                break
            case .userCancelled:
                break
            @unknown default:
                break
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// `AppStore.sync()` rather than only re-reading entitlements, because a
    /// player tapping Restore has usually just reinstalled or switched devices
    /// and the local receipt may not exist yet.
    func restore() async {
        do {
            try await AppStore.sync()
            await refreshEntitlements()
            if !hasRemovedAds {
                errorMessage = "No Remove Ads purchase was found for this Apple Account."
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func apply(_ result: VerificationResult<Transaction>) async {
        guard case let .verified(transaction) = result else { return }
        if transaction.productID == Self.removeAdsProductID {
            setRemovedAds(transaction.revocationDate == nil)
        }
        // Unfinished transactions are redelivered on every launch forever.
        await transaction.finish()
    }

    private func setRemovedAds(_ value: Bool) {
        defaults.set(value, forKey: entitlementKey)
        guard hasRemovedAds != value else { return }
        hasRemovedAds = value
    }
}
