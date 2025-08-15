package org.torproject.android.ui.connect

sealed class ConnectUiState {
    object NoInternet : ConnectUiState()

    object Off : ConnectUiState()

    data class Starting(val bootstrapPercent: Int?) : ConnectUiState()

    object On : ConnectUiState()

    object Stopping : ConnectUiState()
}
