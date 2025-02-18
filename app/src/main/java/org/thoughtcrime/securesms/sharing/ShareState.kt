package org.thoughtcrime.securesms.sharing

data class ShareState(
  val loadState: ShareDataLoadState = ShareDataLoadState.Init
) {
  sealed class ShareDataLoadState {
    object Init : ShareDataLoadState()
    data class Loaded(val resolvedShareData: ResolvedShareData) : ShareDataLoadState()
    object Failed : ShareDataLoadState()
  }
}
