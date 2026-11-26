package com.felixbrucker.simklcalendar.ui.composable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.felixbrucker.simklcalendar.ui.theme.PurpleLight
import com.felixbrucker.simklcalendar.ui.theme.SurfaceContainer
import com.felixbrucker.simklcalendar.ui.theme.TextGrey
import com.felixbrucker.simklcalendar.ui.theme.TextPrimary
import com.felixbrucker.simklcalendar.ui.theme.TextSubtle

@Composable
fun TopAppBarTitleSectionWithExpandableSearch(
    searchDisplayMode: SearchBarDisplayMode,
    searchQuery: String,
    searchQueryPlaceholder: String,
    focusRequester: FocusRequester,
    onSearchQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    isLoading: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    regularTopAppBarTitleSection: @Composable () -> Unit,
) {
    AnimatedContent(
        targetState = searchDisplayMode,
        transitionSpec = {
            if (targetState == SearchBarDisplayMode.EXPANDED) {
                (slideInHorizontally(animationSpec = tween(280)) { width -> width } + fadeIn(animationSpec = tween(250))) togetherWith
                    (slideOutHorizontally(animationSpec = tween(200)) { width -> -width / 4 } + fadeOut(animationSpec = tween(200)))
            } else if ((initialState == SearchBarDisplayMode.EXPANDED) && (targetState == SearchBarDisplayMode.HIDDEN)) {
                (slideInHorizontally(animationSpec = tween(200)) { width -> -width / 4 } + fadeIn(animationSpec = tween(200))) togetherWith
                    (slideOutHorizontally(animationSpec = tween(280)) { width -> width } + fadeOut(animationSpec = tween(250)))
            } else {
                fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200))
            }
        },
        label = "top_bar_search_transition",
    ) { mode ->
        when (mode) {
            SearchBarDisplayMode.EXPANDED -> {
                ExpandedSearchBar(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    searchQueryPlaceholder = searchQueryPlaceholder,
                    focusRequester = focusRequester,
                    onFocusChanged = onFocusChanged,
                    onSearch = onSearch,
                    onClearSearch = onClearSearch,
                    imeAction = imeAction,
                    isLoading = isLoading,
                )
            }

            SearchBarDisplayMode.HIDDEN -> {
                regularTopAppBarTitleSection()
            }
        }
    }
}

@Composable
private fun ExpandedSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchQueryPlaceholder: String,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    imeAction: ImeAction = ImeAction.Done,
    isLoading: Boolean = false,
) {
    val textFieldState = rememberTextFieldState(initialText = searchQuery)
    val currentSearchQuery by rememberUpdatedState(searchQuery)
    val currentOnSearchQueryChange by rememberUpdatedState(onSearchQueryChange)

    LaunchedEffect(searchQuery) {
        if (textFieldState.text.toString() != searchQuery) {
            textFieldState.setTextAndPlaceCursorAtEnd(searchQuery)
        }
    }

    LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { newText ->
                if (newText != currentSearchQuery) {
                    currentOnSearchQueryChange(newText)
                }
            }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceContainer)
            .border(1.dp, PurpleLight, RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchBarLeadingIcon(isLoading = isLoading)

        Spacer(modifier = Modifier.width(8.dp))

        SearchInputField(
            textFieldState = textFieldState,
            focusRequester = focusRequester,
            onFocusChanged = onFocusChanged,
            searchQueryPlaceholder = searchQueryPlaceholder,
            imeAction = imeAction,
            onSearch = onSearch,
            modifier = Modifier.weight(1f),
        )

        IconButton(
            onClick = onClearSearch,
            modifier = Modifier
                .size(32.dp)
                .testTag("clear_search_button"),
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Delete search query",
                tint = TextSubtle,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SearchBarLeadingIcon(isLoading: Boolean) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = PurpleLight,
        )
    } else {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search active",
            tint = PurpleLight,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SearchInputField(
    textFieldState: TextFieldState,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    searchQueryPlaceholder: String,
    imeAction: ImeAction,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        state = textFieldState,
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { state -> onFocusChanged(state.isFocused) }
            .testTag("search_input_field"),
        textStyle = TextStyle(
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
        ),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        onKeyboardAction = { onSearch() },
        lineLimits = TextFieldLineLimits.SingleLine,
        cursorBrush = SolidColor(PurpleLight),
        decorator = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (textFieldState.text.isEmpty()) {
                    Text(
                        text = searchQueryPlaceholder,
                        color = TextGrey,
                        fontSize = 14.sp,
                    )
                }
                innerTextField()
            }
        },
    )
}

enum class SearchBarDisplayMode {
    HIDDEN,
    EXPANDED,
}

