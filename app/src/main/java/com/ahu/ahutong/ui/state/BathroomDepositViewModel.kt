package com.ahu.ahutong.ui.state

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahu.ahutong.data.AHURepository
import com.ahu.ahutong.data.AHUResponse
import com.ahu.ahutong.data.crawler.PayState
import com.ahu.ahutong.data.crawler.model.ycard.BathroomPayRequest
import com.ahu.ahutong.data.crawler.model.ycard.BathroomRequest
import com.ahu.ahutong.data.crawler.model.ycard.PayResponse
import com.ahu.ahutong.data.dao.AHUCache
import com.ahu.ahutong.data.model.BathroomTelInfo
import com.ahu.ahutong.ext.launchSafe
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BathroomDepositViewModel: ViewModel() {

    val TAG = "BathroomDepositViewModel"

    private  val _info = MutableStateFlow<AHUResponse<BathroomTelInfo>?>(null)

    val info: StateFlow<AHUResponse<BathroomTelInfo>?> = _info

    private val _isQuerying = MutableStateFlow(false)
    val isQuerying: StateFlow<Boolean> = _isQuerying

    private var queryJob: Job? = null

    var _payState = MutableStateFlow<PayState>(PayState.Idle)

    val payState : StateFlow<PayState> = _payState

    fun resetPaymentState() {
        _payState.value = PayState.Idle
    }

    fun clearBathroomInfo() {
        queryJob?.cancel()
        _isQuerying.value = false
        _info.value = null
    }

    fun getBathroomInfo(bathroom: String, tel: String) {
        if (tel.length != 11) return
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            _isQuerying.value = true
            _info.value = null
            try {
                _info.value = withContext(Dispatchers.IO) {
                    AHURepository.getBathroomInfo(bathroom = bathroom, tel = tel)
                }
            } finally {
                _isQuerying.value = false
            }
        }
    }



    val paymentSuccessEvent = MutableLiveData<Unit>()
    fun pay(bathroom:String,amount: String,password: String){

        _payState.value = PayState.InProgress
        if (info.value?.data?.map?.data == null) {
            _payState.value = PayState.Failed("请先查询有效的浴室账户")
            return
        }

        viewModelScope.launchSafe {
            withContext(Dispatchers.IO){
                info.value!!.data.map!!.data?.let{ //????
                    val data = it
                    data.myCustomInfo = "手机号：${data.telPhone}"

                    val thirdPartyJson = Gson().toJson(data)

                    val request = BathroomRequest(bathroom,amount,thirdPartyJson)


                    var res = AHURepository.pay(request).data
                    val jsonString = res.body()!!.string()

                    val regex = """"orderid"\s*:\s*"([^"]+)"""".toRegex()
                    val match = regex.find(jsonString)
                    val orderId = match?.groups?.get(1)?.value


                    orderId?.let{ orderId ->
                        val payRequest = BathroomPayRequest(orderId,password)
                        val res = AHURepository.pay(payRequest)


                        val payResponse: PayResponse? = res.data.body()?.string()?.let {
                            Gson().fromJson(it, PayResponse::class.java)
                        }

                        if(payResponse?.code == 200){
                            AHUCache.savePhone(it.telPhone)
                            _payState.value = PayState.Succeeded(message = payResponse.data)
                            paymentSuccessEvent.postValue(Unit)
                            delay(1_000)
                            _info.value = AHURepository.getBathroomInfo(
                                bathroom = bathroom,
                                tel = data.telPhone
                            )
                        }else{
                            _payState.value = PayState.Failed(message = payResponse?.msg?:"未知错误")
                        }

                    }



                }
            }

        }
    }

}


