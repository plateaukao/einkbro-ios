package info.plateaukao.einkbro.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import info.plateaukao.einkbro.view.compose.onTopBar
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import info.plateaukao.einkbro.AppServices
import info.plateaukao.einkbro.browser.AdBlock
import info.plateaukao.einkbro.browser.Cookie
import info.plateaukao.einkbro.browser.DomainInterface
import info.plateaukao.einkbro.browser.Javascript
import info.plateaukao.einkbro.resources.Res
import info.plateaukao.einkbro.resources.empty_whitelist_hint
import info.plateaukao.einkbro.resources.list_empty
import info.plateaukao.einkbro.resources.menu_delete
import info.plateaukao.einkbro.resources.menu_edit
import info.plateaukao.einkbro.resources.setting_title_whitelist
import info.plateaukao.einkbro.resources.setting_title_whitelistCookie
import info.plateaukao.einkbro.resources.setting_title_whitelistJS
import info.plateaukao.einkbro.resources.whitelist_add
import info.plateaukao.einkbro.search.SplitSearchListType
import info.plateaukao.einkbro.util.LocalContext
import info.plateaukao.einkbro.view.EBToast
import info.plateaukao.einkbro.view.compose.EmptyListPlaceholder
import info.plateaukao.einkbro.view.compose.ListScaffold
import info.plateaukao.einkbro.view.dialog.DialogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Port of DataListActivity: whitelist-style data lists (ad-block / cookie /
 * javascript whitelists and split-search patterns).
 */
@Composable
fun DataListScreen(
    type: WhiteListType = WhiteListType.Adblock,
    onClose: () -> Unit = {},
) {
    val whitelistType = remember(type) {
        when (type) {
            WhiteListType.Adblock -> WhiteListTypeAdblock()
            WhiteListType.Javascript -> BaseWhiteListTypeJavascript()
            WhiteListType.Cookie -> BaseWhiteListTypeCookie()
            WhiteListType.SplitSearch -> SplitSearchListType()
        }
    }
    val dialogManager = remember { AppServices.dialogManager }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val whitelist = remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(whitelistType) { whitelist.value = whitelistType.getDomains() }
    ListScaffold(
        title = stringResource(whitelistType.titleId),
        onBack = onClose,
        actions = {
            IconButton(onClick = {
                scope.launch {
                    whitelistType.deleteAllDomains()
                    whitelist.value = emptyList()
                }
            }) {
                Icon(
                    tint = MaterialTheme.colors.onTopBar,
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(Res.string.menu_delete)
                )
            }
            IconButton(onClick = {
                whitelistType.addDomain(
                    scope,
                    dialogManager
                ) { whitelist.value += it }
            }) {
                Icon(
                    tint = MaterialTheme.colors.onTopBar,
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.whitelist_add)
                )
            }
        },
    ) { innerPadding ->
        WhiteListContent(
            modifier = Modifier.padding(innerPadding),
            list = whitelist,
            editAction = { domain ->
                scope.launch {
                    val value = dialogManager.getTextInput(
                        Res.string.menu_edit, whitelistType.titleId, domain
                    )?.trim() ?: run {
                        EBToast.show(context, "text input dialog is not available in the catalog")
                        return@launch
                    }
                    if (value.isBlank() || value == domain) return@launch
                    whitelistType.deleteDomain(domain)
                    if (whitelist.value.contains(value)) {
                        // renamed to an existing entry: just drop the old one
                        whitelist.value = whitelist.value.filter { it != domain }
                    } else {
                        whitelistType.domainHandler.addDomain(value)
                        whitelist.value =
                            whitelist.value.map { if (it == domain) value else it }
                    }
                }
            },
        ) { domain -> scope.launch { whitelistType.deleteDomain(domain) } }
    }
}

enum class WhiteListType {
    Adblock,
    Cookie,
    Javascript,
    SplitSearch
}


@Composable
fun WhiteListContent(
    modifier: Modifier = Modifier,
    list: MutableState<List<String>>,
    editAction: (String) -> Unit = {},
    deleteAction: (String) -> Unit = {},
) {
    if (list.value.isEmpty()) {
        EmptyListPlaceholder(
            stringResource(Res.string.list_empty) + "\n" + stringResource(Res.string.empty_whitelist_hint)
        )
    } else {
        LazyColumn(
            modifier = modifier,
            content = {
                items(list.value.size, key = { list.value[it] }) { index ->
                    val itemText = list.value[index]
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { editAction(itemText) },
                            text = itemText,
                        )
                        IconButton(onClick = {
                            deleteAction(itemText)
                            list.value = list.value.filter { it != itemText }
                        }) {
                            Icon(
                                tint = MaterialTheme.colors.onBackground,
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(Res.string.menu_delete)
                            )
                        }
                    }
                }
            }
        )
    }
}

abstract class BaseWhiteListType {
    abstract val titleId: StringResource
    abstract val domainHandler: DomainInterface
    suspend fun getDomains(): List<String> = domainHandler.getDomains()
    open fun addDomain(
        lifecycleScope: CoroutineScope,
        dialogManager: DialogManager,
        postAction: (String) -> Unit,
    ) {
        lifecycleScope.launch {
            val value = dialogManager.getTextInput(
                Res.string.whitelist_add, titleId, ""
            ) ?: run {
                EBToast.show(AppServices.context, "text input dialog is not available in the catalog")
                return@launch
            }
            if (value.isNotBlank()) {
                domainHandler.addDomain(value.trim())
                postAction(value.trim())
            }
        }
    }

    suspend fun deleteDomain(domain: String) = domainHandler.deleteDomain(domain)
    suspend fun deleteAllDomains() = domainHandler.deleteAllDomains()
}

class WhiteListTypeAdblock : BaseWhiteListType() {
    override val titleId: StringResource = Res.string.setting_title_whitelist
    private val adBlock: AdBlock by lazy { AdBlock() }
    override val domainHandler: DomainInterface by lazy { adBlock }
}

class BaseWhiteListTypeJavascript : BaseWhiteListType() {
    override val titleId: StringResource = Res.string.setting_title_whitelistJS
    private val javascript: Javascript by lazy { Javascript() }
    override val domainHandler: DomainInterface by lazy { javascript }
}

class BaseWhiteListTypeCookie : BaseWhiteListType() {
    override val titleId: StringResource = Res.string.setting_title_whitelistCookie
    private val cookie: Cookie by lazy { Cookie() }
    override val domainHandler: DomainInterface by lazy { cookie }
}
