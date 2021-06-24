/*
 *  Copyright 2021 Curity AB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.curity.identityserver.client.views.unauthenticated;

import android.content.Intent
import androidx.databinding.BaseObservable
import io.curity.identityserver.client.AppAuthHandler
import io.curity.identityserver.client.ApplicationStateManager
import io.curity.identityserver.client.configuration.ApplicationConfig
import io.curity.identityserver.client.errors.ApplicationException
import io.curity.identityserver.client.views.error.ErrorFragmentViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse

class UnauthenticatedFragmentViewModel(
    private val events: UnauthenticatedFragmentEvents,
    private val appauth: AppAuthHandler,
    private val error: ErrorFragmentViewModel) : BaseObservable() {

    var isRegistered = false

    /*
     * Startup handling to lookup metadata and do the dynamic client registration if required
     * Make HTTP requests on a worker thread and then perform updates on the UI thread
     */
    fun registerIfRequired() {

        CoroutineScope(Dispatchers.IO).launch {

            try {

                if (ApplicationStateManager.serverConfiguration == null) {
                    ApplicationStateManager.serverConfiguration = appauth.fetchMetadata(ApplicationConfig.issuer)
                }
                if (ApplicationStateManager.registrationResponse == null) {
                    ApplicationStateManager.registrationResponse = appauth.registerClient(ApplicationStateManager.serverConfiguration!!)
                }

                withContext(Dispatchers.Main) {
                    isRegistered = true
                    notifyChange()
                }

            } catch (ex: ApplicationException) {

                withContext(Dispatchers.Main) {
                    error.setDetails(ex)
                }
            }
        }
    }

    /*
     * Build the authorization redirect URL and then ask the view to redirect
     */
    fun startLogin() {

        error.clearDetails()
        val intent = appauth.getAuthorizationRedirectIntent(
            ApplicationStateManager.serverConfiguration!!,
            ApplicationStateManager.registrationResponse!!
        )

        events.startLoginRedirect(intent)
    }

    /*
     * Redeem the code for tokens and also handle failures or the user cancelling the Chrome Custom Tab
     * Make HTTP requests on a worker thread and then perform updates on the UI thread
     */
    fun endLogin(data: Intent) {

        try {

            val authorizationResponse = appauth.handleAuthorizationResponse(
                AuthorizationResponse.fromIntent(data),
                AuthorizationException.fromIntent(data))

            CoroutineScope(Dispatchers.IO).launch {
                try {

                    val tokenResponse = appauth.redeemCodeForTokens(
                        authorizationResponse,
                        ApplicationStateManager.registrationResponse!!
                    )

                    withContext(Dispatchers.Main) {
                        ApplicationStateManager.tokenResponse = tokenResponse
                        events.onAuthenticated()
                    }
                } catch (ex: ApplicationException) {

                    withContext(Dispatchers.Main) {
                        error.setDetails(ex)
                    }
                }
            }

        } catch (ex: ApplicationException) {
            error.setDetails(ex)
        }
    }
}
