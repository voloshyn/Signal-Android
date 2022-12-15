package org.thoughtcrime.securesms.badges.gifts.flow

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.signal.core.util.logging.Log
import org.signal.core.util.money.FiatMoney
import org.thoughtcrime.securesms.badges.models.Badge
import org.thoughtcrime.securesms.components.settings.app.subscription.errors.DonationError
import org.thoughtcrime.securesms.components.settings.app.subscription.getGiftBadgeAmounts
import org.thoughtcrime.securesms.components.settings.app.subscription.getGiftBadges
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.util.ProfileUtil
import org.whispersystems.signalservice.api.profiles.SignalServiceProfile
import org.whispersystems.signalservice.internal.push.DonationsConfiguration
import java.io.IOException
import java.util.Currency
import java.util.Locale

/**
 * Repository for grabbing gift badges and supported currency information.
 */
class GiftFlowRepository {

  companion object {
    private val TAG = Log.tag(GiftFlowRepository::class.java)
  }

  fun getGiftBadge(): Single<Pair<Int, Badge>> {
    return Single
      .fromCallable {
        ApplicationDependencies.getDonationsService()
          .getDonationsConfiguration(Locale.getDefault())
      }
      .flatMap { it.flattenResult() }
      .map { DonationsConfiguration.GIFT_LEVEL to it.getGiftBadges().first() }
      .subscribeOn(Schedulers.io())
  }

  fun getGiftPricing(): Single<Map<Currency, FiatMoney>> {
    return Single
      .fromCallable {
        ApplicationDependencies.getDonationsService()
          .getDonationsConfiguration(Locale.getDefault())
      }
      .subscribeOn(Schedulers.io())
      .flatMap { it.flattenResult() }
      .map { it.getGiftBadgeAmounts() }
  }

  /**
   * Verifies that the given recipient is a supported target for a gift.
   *
   * TODO[alex] - this needs to be incorporated into the correct flows.
   */
  fun verifyRecipientIsAllowedToReceiveAGift(badgeRecipient: RecipientId): Completable {
    return Completable.fromAction {
      Log.d(TAG, "Verifying badge recipient $badgeRecipient", true)
      val recipient = Recipient.resolved(badgeRecipient)

      if (recipient.isSelf) {
        Log.d(TAG, "Cannot send a gift to self.", true)
        throw DonationError.GiftRecipientVerificationError.SelectedRecipientDoesNotSupportGifts
      }

      if (recipient.isGroup || recipient.isDistributionList || recipient.registered != RecipientTable.RegisteredState.REGISTERED) {
        Log.w(TAG, "Invalid badge recipient $badgeRecipient. Verification failed.", true)
        throw DonationError.GiftRecipientVerificationError.SelectedRecipientIsInvalid
      }

      try {
        val profile = ProfileUtil.retrieveProfileSync(ApplicationDependencies.getApplication(), recipient, SignalServiceProfile.RequestType.PROFILE_AND_CREDENTIAL)
        if (!profile.profile.capabilities.isGiftBadges) {
          Log.w(TAG, "Badge recipient does not support gifting. Verification failed.", true)
          throw DonationError.GiftRecipientVerificationError.SelectedRecipientDoesNotSupportGifts
        } else {
          Log.d(TAG, "Badge recipient supports gifting. Verification successful.", true)
        }
      } catch (e: IOException) {
        Log.w(TAG, "Failed to retrieve profile for recipient.", e, true)
        throw DonationError.GiftRecipientVerificationError.FailedToFetchProfile(e)
      }
    }.subscribeOn(Schedulers.io())
  }
}
