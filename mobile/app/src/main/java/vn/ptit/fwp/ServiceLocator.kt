package vn.ptit.fwp

import android.content.Context
import vn.ptit.fwp.data.FakeFwpRepository
import vn.ptit.fwp.data.FwpRepository
import vn.ptit.fwp.data.RemoteFwpRepository
import vn.ptit.fwp.data.Settings
import vn.ptit.fwp.data.remote.ApiClient

/** Nơi duy nhất tạo các đối tượng dùng chung. Đổi nguồn dữ liệu = sửa ở đây. */
object ServiceLocator {
    lateinit var settings: Settings
        private set

    private val fake by lazy { FakeFwpRepository() }
    private val remote by lazy { RemoteFwpRepository(ApiClient.create()) }

    fun init(context: Context) {
        if (!::settings.isInitialized) settings = Settings(context.applicationContext)
    }

    fun repository(): FwpRepository = if (settings.useServer) remote else fake
}
