package com.ahu.ahutong.ui.state

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.ext.launchSafe
import com.ahu.ahutong.personalization.prefetch.PaymentQrRepository
import com.ahu.ahutong.personalization.runtime.BehaviorPredictionRuntime
import com.ahu.ahutong.personalization.context.BalanceBucket
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.BarcodeEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * @Author Simon
 * @Date 2021/8/3-22:12
 * @Email 330771794@qq.com
 */


@HiltViewModel
class DiscoveryViewModel @Inject constructor(
    private val paymentQrRepository: PaymentQrRepository,
    private val behaviorRuntime: BehaviorPredictionRuntime
) : ViewModel() {

    val TAG = DiscoveryViewModel::class.java.simpleName

    val bathroom = mutableStateMapOf<String, String>()
    var balance by mutableStateOf(0.0)
    var transitionBalance by mutableStateOf(0.0)

    var qrcode = MutableStateFlow<Bitmap?>(null)
    var state = MutableStateFlow<Boolean>(false);

    fun loadActivityBean() {
        // 优先加载缓存
        if (!AHUCache.getMockData()) {
            AHUCache.getCardBalance()?.let {
                balance = it
            }
        }

        viewModelScope.launchSafe {
            val (cardResult, bathroomResult) = coroutineScope {
                val card = async { AHURepository.getCardMoney() }
                val bathrooms = async { AHURepository.getBathRooms() }
                card.await() to bathrooms.await()
            }
            cardResult.onSuccess {
                applyCardBalance(it.balance, it.transitionBalance)
            }
            bathroomResult.onSuccess {
                bathroom.clear()
                it.forEach { room ->
                    bathroom += room.bathroom to room.openStatus
                }
            }
        }
    }

    fun refreshCardBalance() {
        if (!AHUCache.getMockData()) {
            AHUCache.getCardBalance()?.let {
                balance = it
            }
        }

        viewModelScope.launchSafe {
            AHURepository.getCardMoney().onSuccess {
                applyCardBalance(it.balance, it.transitionBalance)
            }
        }
    }

    private fun applyCardBalance(balanceValue: Double?, transitionBalanceValue: Double?) {
        val newBalance = balanceValue ?: 0.0
        balance = newBalance
        AHUCache.saveCardBalance(newBalance)
        transitionBalance = transitionBalanceValue ?: transitionBalance
        behaviorRuntime.onBusinessContextChanged(
            newBalanceBucket = when {
                newBalance < 5.0 -> BalanceBucket.ZERO_TO_FIVE
                newBalance < 10.0 -> BalanceBucket.FIVE_TO_TEN
                newBalance < 20.0 -> BalanceBucket.TEN_TO_TWENTY
                newBalance < 50.0 -> BalanceBucket.TWENTY_TO_FIFTY
                else -> BalanceBucket.FIFTY_PLUS
            },
            newBalanceFresh = true
        )
    }

    fun loadQrCode(forceRefresh: Boolean = false) {
        viewModelScope.launchSafe {
            withContext(Dispatchers.IO){
                state.value = false
                try {
                    val response = paymentQrRepository.getForDisplay(forceRefresh = forceRefresh)
                    if (response.isSuccess) {
                        val hints = HashMap<EncodeHintType, Any>()

                        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.L
                        hints[EncodeHintType.MARGIN] = 1
                        val encoder = BarcodeEncoder()
                        qrcode.value = encoder.encodeBitmap(
                            response.getOrThrow(),
                            BarcodeFormat.QR_CODE,
                            400,
                            400,
                            hints
                        )
                    } else {
                        Log.e("QR", "付款码加载失败")
                    }
                } catch (e: Exception) {
                    Log.e("QR", "付款码加载异常")
                }
                state.value = true
            }
        }

    }

    fun clearQrCode() {
        qrcode.value = null
        state.value = false
    }

}
