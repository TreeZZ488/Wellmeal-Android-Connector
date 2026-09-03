package com.wellmeal.connector

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.SignInParameters
import com.microsoft.identity.client.SilentAuthenticationCallback
import com.microsoft.identity.client.exception.MsalClientException
import com.microsoft.identity.client.exception.MsalException
import kotlinx.coroutines.CompletableDeferred

class MicrosoftAuthManager(
    context: Context
) {

    private var msalApp: ISingleAccountPublicClientApplication? = null

    private val readyDeferred = CompletableDeferred<Result<IAccount?>>()

    var currentUser by mutableStateOf<IAccount?>(null)
        private set

    var authError by mutableStateOf<String?>(null)
        private set

    var isInitialized by mutableStateOf(false)
        private set

    init {
        // Initialize MSAL single-account public client application
        PublicClientApplication.createSingleAccountPublicClientApplication(
            context.applicationContext,
            R.raw.auth_config,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication) {
                    msalApp = application
                    isInitialized = true

                    // Restore currently signed-in account
                    application.getCurrentAccountAsync(
                        object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                            override fun onAccountLoaded(activeAccount: IAccount?) {
                                currentUser = activeAccount
                                authError = null
                                readyDeferred.complete(Result.success(activeAccount))
                            }

                            override fun onAccountChanged(
                                priorAccount: IAccount?,
                                currentAccount: IAccount?
                            ) {
                                currentUser = currentAccount
                            }

                            override fun onError(exception: MsalException) {
                                authError = exception.message ?: "Failed to load account"
                                readyDeferred.complete(Result.failure(exception))
                            }
                        }
                    )
                }

                override fun onError(exception: MsalException) {
                    authError = exception.message ?: "Failed to initialize MSAL"
                    readyDeferred.complete(Result.failure(exception))
                }
            }
        )
    }

    /**
     * Suspends until MSAL initialization and cached account restoration finish.
     */
    suspend fun awaitReady(): Result<IAccount?> {
        return readyDeferred.await()
    }

    /**
     * Loads the active account if already signed in.
     */
    fun loadAccount() {
        val app = msalApp ?: return

        app.getCurrentAccountAsync(
            object : ISingleAccountPublicClientApplication.CurrentAccountCallback {
                override fun onAccountLoaded(activeAccount: IAccount?) {
                    currentUser = activeAccount
                    authError = null
                }

                override fun onAccountChanged(
                    priorAccount: IAccount?,
                    currentAccount: IAccount?
                ) {
                    currentUser = currentAccount
                }

                override fun onError(exception: MsalException) {
                    authError = exception.message ?: "Failed to load account"
                }
            }
        )
    }

    /**
     * Starts interactive sign-in flow.
     */
    fun signIn(
        activity: Activity,
        onSuccess: (() -> Unit)? = null
    ) {
        val app = msalApp
        if (app == null) {
            authError = "MSAL is not initialized yet"
            return
        }

        authError = null

        val parameters = SignInParameters.builder()
            .withActivity(activity)
            .withScopes(listOf("Files.ReadWrite.AppFolder"))
            .withCallback(object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    currentUser = authenticationResult.account
                    authError = null
                    onSuccess?.invoke()
                }

                override fun onError(exception: MsalException) {
                    authError = exception.message ?: "Sign-in failed"
                }

                override fun onCancel() {
                    // Sign-in cancelled by user
                }
            })
            .build()

        app.signIn(parameters)
    }

    /**
     * Signs out the current user.
     */
    fun signOut(
        onSuccess: (() -> Unit)? = null
    ) {
        val app = msalApp
        if (app == null) {
            authError = "MSAL is not initialized yet"
            return
        }

        app.signOut(object : ISingleAccountPublicClientApplication.SignOutCallback {
            override fun onSignOut() {
                currentUser = null
                authError = null
                onSuccess?.invoke()
            }

            override fun onError(exception: MsalException) {
                authError = exception.message ?: "Sign-out failed"
            }
        })
    }

    /**
     * Acquires access token silently for Microsoft Graph API calls.
     */
    fun acquireTokenSilent(
        scopes: List<String> = listOf("Files.ReadWrite.AppFolder"),
        onSuccess: (IAuthenticationResult) -> Unit,
        onError: (MsalException) -> Unit
    ) {
        val app = msalApp
        val account = currentUser

        if (app == null || account == null) {
            val exception = MsalClientException(
                "NO_CURRENT_ACCOUNT",
                "No user is currently signed in or MSAL is not initialized"
            )
            authError = exception.message
            onError(exception)
            return
        }

        val parameters = AcquireTokenSilentParameters.Builder()
            .forAccount(account)
            .fromAuthority(account.authority)
            .withScopes(scopes)
            .withCallback(object : SilentAuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    onSuccess(authenticationResult)
                }

                override fun onError(exception: MsalException) {
                    authError = exception.message ?: "Silent token acquisition failed"
                    onError(exception)
                }
            })
            .build()

        app.acquireTokenSilentAsync(parameters)
    }

    /**
     * Acquires Mail.Send access token silently for email delivery.
     */
    fun acquireMailTokenSilent(
        onSuccess: (IAuthenticationResult) -> Unit,
        onError: (MsalException) -> Unit
    ) {
        acquireTokenSilent(
            scopes = listOf("Mail.Send"),
            onSuccess = onSuccess,
            onError = onError
        )
    }

    /**
     * Prompts the user interactively in the foreground UI for Mail.Send consent if needed.
     */
    fun acquireMailTokenInteractive(
        activity: Activity,
        onSuccess: (IAuthenticationResult) -> Unit,
        onError: (MsalException) -> Unit
    ) {
        val app = msalApp
        val account = currentUser

        if (app == null || account == null) {
            val exception = MsalClientException(
                "NO_CURRENT_ACCOUNT",
                "No user is currently signed in or MSAL is not initialized"
            )
            authError = exception.message
            onError(exception)
            return
        }

        val parameters = AcquireTokenParameters.Builder()
            .startAuthorizationFromActivity(activity)
            .forAccount(account)
            .fromAuthority(account.authority)
            .withScopes(listOf("Mail.Send"))
            .withCallback(object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    authError = null
                    onSuccess(authenticationResult)
                }

                override fun onError(exception: MsalException) {
                    authError = exception.message ?: "Interactive Mail.Send consent failed"
                    onError(exception)
                }

                override fun onCancel() {
                    onError(MsalClientException("CANCELLED", "User cancelled Mail.Send consent"))
                }
            })
            .build()

        app.acquireToken(parameters)
    }
}
