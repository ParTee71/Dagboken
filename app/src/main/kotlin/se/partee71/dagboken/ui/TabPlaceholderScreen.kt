package se.partee71.dagboken.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import se.partee71.dagboken.R
import se.partee71.dagboken.data.auth.AuthUser
import se.partee71.dagboken.navigation.DiaryKey
import se.partee71.dagboken.navigation.MedicinesKey
import se.partee71.dagboken.navigation.TodayKey
import se.partee71.dagboken.navigation.TopLevelKey
import se.partee71.dagboken.navigation.TrendsKey
import se.partee71.dagboken.ui.components.AccountAvatar
import se.partee71.dagboken.ui.components.AppTopBar
import se.partee71.dagboken.ui.components.EmptyState
import se.partee71.dagboken.ui.components.LocalBottomClearance

/** Flikens namn, ikon och vad den ska innehålla – samma ikon som i bottenraden. */
enum class TabInfo(val key: TopLevelKey, @StringRes val title: Int, @DrawableRes val icon: Int, @StringRes val upcoming: Int) {
    Today(TodayKey, R.string.tab_today, R.drawable.ic_sun, R.string.tab_today_upcoming),
    Diary(DiaryKey, R.string.tab_diary, R.drawable.ic_book, R.string.tab_diary_upcoming),
    Trends(TrendsKey, R.string.tab_trends, R.drawable.ic_trend, R.string.tab_trends_upcoming),
    Medicines(MedicinesKey, R.string.tab_medicines, R.drawable.ic_pill, R.string.tab_medicines_upcoming),
    ;

    companion object {
        fun of(key: TopLevelKey): TabInfo = entries.first { it.key == key }
    }
}

/**
 * En flik innan den byggts (etapp 5): rubriken med avataren uppe till höger (NAV-9) – [account]s
 * initialer och foto (AUTH-3) – och ett tomt tillstånd som säger vad som kommer. [onAccount] öppnar
 * inställningsarket.
 */
@Composable
fun TabPlaceholderScreen(tab: TopLevelKey, account: AuthUser? = null, onAccount: () -> Unit, modifier: Modifier = Modifier) {
    val info = TabInfo.of(tab)
    Column(modifier.fillMaxSize()) {
        AppTopBar(
            stringResource(info.title),
            actions = { AccountAvatar(account?.name ?: account?.email, onAccount, photoUrl = account?.photoUrl) },
        )
        Box(Modifier.fillMaxSize().padding(bottom = LocalBottomClearance.current), contentAlignment = Alignment.Center) {
            EmptyState(info.icon, stringResource(R.string.tab_upcoming_title), stringResource(info.upcoming))
        }
    }
}
