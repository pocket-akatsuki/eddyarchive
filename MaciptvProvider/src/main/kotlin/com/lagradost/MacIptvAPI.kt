package com.lagradost

import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.syncproviders.AuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPI
import com.lagradost.cloudstream3.syncproviders.InAppAuthAPIManager

class MacIptvAPI(index: Int) : InAppAuthAPIManager(index) {
    override val name = "Iptvbox"
    override val idPrefix = "iptvbox"
    override val icon = R.drawable.ic_baseline_extension_24
    override val requiresUsername = true
    override val requiresPassword = true
    override val requiresServer = true
    override val createAccountUrl = ""

    companion object {
        const val IPTVBOX_USER_KEY: String = "iptvbox_user"
    }

    override fun getLatestLoginData(): InAppAuthAPI.LoginData? {
        return getKey(accountId, IPTVBOX_USER_KEY)
    }

    override fun loginInfo(): AuthAPI.LoginInfo? {
        val data = getLatestLoginData() ?: return null
        return AuthAPI.LoginInfo(name = data.username ?: data.server, accountIndex = accountIndex)
    }

    override suspend fun login(data: InAppAuthAPI.LoginData): Boolean {
        if (data.server.isNullOrBlank() || !data.password?.contains("""(([0-9A-Za-z]{2}[:-]){5}[0-9A-Za-z]{2})""".toRegex())!!) return false // we require a server and a mac address
        switchToNewAccount()
        setKey(accountId, IPTVBOX_USER_KEY, data)
        registerAccount()
        initialize()
        inAppAuths

        return true
    }

    override fun logOut() {
        removeAccountKeys()
        initializeData()
    }

    private fun initializeData() {
        val data = getLatestLoginData() ?: run {
            MacIPTVProvider.overrideUrl = null
            MacIPTVProvider.loginMac = null
            MacIPTVProvider.companionName = null
            switchToNewAccount()
            setKey(
                accountId,
                IPTVBOX_USER_KEY,
                InAppAuthAPI.LoginData("Default Account", null, "none")
            )
            registerAccount()
            inAppAuths
            return
        }
        MacIPTVProvider.overrideUrl = data.server?.removeSuffix("/")
        MacIPTVProvider.loginMac = data.password ?: ""
        MacIPTVProvider.companionName = data.username
    }

    override suspend fun initialize() {
        initializeData()
    }
}