
package com.lagradost

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import com.lagradost.cloudstream3.ui.settings.SettingsAccount

@CloudstreamPlugin
class MacIPTVProviderPlugin : Plugin() {
    val iptvboxApi = MacIptvAPI(0)
    val tagsiptvboxApi = TagsMacIptvAPI(1)

    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        iptvboxApi.init()
        tagsiptvboxApi.init()
        registerMainAPI(MacIPTVProvider())
        ioSafe {
            iptvboxApi.initialize()
            tagsiptvboxApi.initialize()
        }
    }

    init {
        this.openSettings = {
            val activity = it as? AppCompatActivity
            if (activity != null) {
                val frag = MacIptvSettingsFragment(this, iptvboxApi,tagsiptvboxApi)
                frag.show(activity.supportFragmentManager, iptvboxApi.name)
            }
        }
    }
}