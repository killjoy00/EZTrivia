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

    func loadProduct() async {
        removeAdsProduct = try? await Product.products(for: [Self.removeAdsProductID]).first
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
