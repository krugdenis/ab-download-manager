package com.abdownloadmanager.desktop.pages.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.abdownloadmanager.desktop.pages.home.sections.DownloadList
import com.abdownloadmanager.desktop.pages.home.sections.SearchBox
import com.abdownloadmanager.shared.pages.home.category.DefinedStatusCategories
import com.abdownloadmanager.shared.pages.home.category.DownloadStatusCategoryFilter
import com.abdownloadmanager.desktop.pages.home.sections.category.StatusFilterItem
import com.abdownloadmanager.desktop.pages.home.sections.queue.QueuesSection
import com.abdownloadmanager.desktop.window.custom.TitlePosition
import com.abdownloadmanager.desktop.window.custom.WindowEnd
import com.abdownloadmanager.desktop.window.custom.WindowStart
import com.abdownloadmanager.desktop.window.custom.WindowTitlePosition
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.pages.home.BaseHomeComponent
import com.abdownloadmanager.shared.pages.home.CategoryActions
import com.abdownloadmanager.shared.pages.home.CategoryDeletePromptState
import com.abdownloadmanager.shared.pages.home.ConfirmPromptState
import com.abdownloadmanager.shared.pages.home.DeletePromptState
import com.abdownloadmanager.shared.ui.widget.*
import com.abdownloadmanager.shared.ui.widget.menu.custom.MenuBar
import com.abdownloadmanager.shared.ui.widget.menu.custom.ShowOptionsInPopup
import com.abdownloadmanager.shared.ui.widget.menu.native.NativeMenuBar
import com.abdownloadmanager.shared.ui.configurable.RenderSpinner
import com.abdownloadmanager.shared.util.LocalSpeedUnit
import com.abdownloadmanager.shared.util.SpeedLimitDefaults
import com.abdownloadmanager.shared.util.category.Category
import com.abdownloadmanager.shared.util.category.rememberIconPainter
import com.abdownloadmanager.shared.util.convertPositiveBytesToSizeUnit
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.mvi.HandleEffects
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.WithTitleBarDirection
import com.abdownloadmanager.shared.util.ui.icon.MyIcons
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.util.ui.theme.myTextSizes
import com.abdownloadmanager.shared.util.ui.widget.MyIcon
import ir.amirab.util.datasize.SizeConverter
import ir.amirab.util.datasize.SizeFactors
import ir.amirab.util.datasize.SizeUnit
import ir.amirab.util.datasize.SizeWithUnit
import ir.amirab.util.datasize.asConverterConfig
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.PaddingValues
import ir.amirab.util.compose.IconSource
import ir.amirab.util.compose.action.MenuItem
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.compose.asStringSourceWithARgs
import ir.amirab.util.compose.localizationmanager.WithLanguageDirection
import ir.amirab.util.compose.resources.myStringResource
import ir.amirab.util.desktop.LocalFrameWindowScope
import ir.amirab.util.platform.Platform
import ir.amirab.util.platform.isMac
import kotlinx.coroutines.launch
import java.awt.datatransfer.DataFlavor
import java.io.File


@Composable
fun HomePage(component: HomeComponent) {
    val listState by component.downloadList.collectAsState()
    val queuePositions by component.downloadQueuePositions.collectAsState()
    var isDragging by remember { mutableStateOf(false) }

    var showDeletePromptState by remember {
        mutableStateOf(null as DeletePromptState?)
    }

    var showDeleteCategoryPromptState by remember {
        mutableStateOf(null as CategoryDeletePromptState?)
    }

    var showConfirmPrompt by remember {
        mutableStateOf(null as ConfirmPromptState?)
    }

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val tableState = component.tableState

    HandleEffects(component) { effect ->
        when (effect) {
            is BaseHomeComponent.Effects.Common -> {
                when (effect) {
                    is BaseHomeComponent.Effects.Common.DeleteItems -> {
                        if (effect.list.isNotEmpty()) {
                            showDeletePromptState = DeletePromptState(
                                downloadList = effect.list,
                                finishedCount = effect.finishedCount,
                                unfinishedCount = effect.unfinishedCount,
                            )
                        }
                    }

                    is BaseHomeComponent.Effects.Common.DeleteCategory -> {
                        showDeleteCategoryPromptState = CategoryDeletePromptState(effect.category)
                    }

                    is BaseHomeComponent.Effects.Common.AutoCategorize -> {
                        showConfirmPrompt = ConfirmPromptState(
                            title = Res.string.confirm_auto_categorize_downloads_title.asStringSource(),
                            description = Res.string.confirm_auto_categorize_downloads_description.asStringSource(),
                            onConfirm = component::onConfirmAutoCategorize
                        )
                    }

                    is BaseHomeComponent.Effects.Common.ResetCategoriesToDefault -> {
                        showConfirmPrompt = ConfirmPromptState(
                            title = Res.string.confirm_reset_to_default_categories_title.asStringSource(),
                            description = Res.string.confirm_reset_to_default_categories_description.asStringSource(),
                            onConfirm = component::onConfirmResetCategories
                        )
                    }

                    is BaseHomeComponent.Effects.Common.ScrollToDownloadItem -> {
                        val id = effect.downloadId
                        val positionOrNull = tableState
                            .getItemPosition(listState) { it.id == id }
                            .takeIf { it != -1 }
                        positionOrNull?.let { index ->
                            if (effect.skipIfVisible) {
                                val isVisible = lazyListState.layoutInfo.visibleItemsInfo
                                    .any { it.index == index }
                                if (isVisible) {
                                    return@let
                                }
                            }
                            coroutineScope.launch {
                                lazyListState.scrollToItem(index)
                            }
                        }
                    }
                }
            }

            is HomeComponent.Effects -> {
                when (effect) {
                    HomeComponent.Effects.BringToFront -> {
                        // handled else where
                    }
                }
            }
            else -> {}
        }
    }
    showDeletePromptState?.let {
        ShowDeletePrompts(
            deletePromptState = it,
            onCancel = {
                showDeletePromptState = null
            },
            onConfirm = {
                showDeletePromptState = null
                component.confirmDelete(it)
            })
    }
    showDeleteCategoryPromptState?.let {
        ShowDeleteCategoryPrompt(
            deletePromptState = it,
            onCancel = {
                showDeleteCategoryPromptState = null
            },
            onConfirm = {
                showDeleteCategoryPromptState = null
                component.onConfirmDeleteCategory(it)
            })
    }
    showConfirmPrompt?.let {
        ShowConfirmPrompt(
            promptState = it,
            onCancel = {
                showConfirmPrompt = null
            },
            onConfirm = {
                showConfirmPrompt?.onConfirm?.invoke()
                showConfirmPrompt = null
            }
        )
    }
    val mergeTopBar = shouldMergeTopBarWithTitleBar(component)
    if (mergeTopBar) {
        WindowTitlePosition(
            TitlePosition(
                centered = true,
                afterStart = true,
                padding = PaddingValues(end = 32.dp)
            )
        )
        WindowStart {
            HomeMenuBar(component, Modifier.fillMaxHeight())
        }
        WindowEnd {
            HomeSearch(
                component = component,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 2.dp)
            )
        }
    } else {
        WindowTitlePosition(
            TitlePosition(centered = false, afterStart = false)
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = {
                    if (it.awtTransferable.isDataFlavorSupported(DownloadItemListDataFlavor)) {
                        // this item is ours we don't want to use our download item for import list usage
                        return@dragAndDropTarget false
                    } else it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) ||
                            it.awtTransferable.isDataFlavorSupported(DataFlavor.stringFlavor)
                },
                target = remember {
                    object : DragAndDropTarget {
                        private fun onDraggedIn(event: DragAndDropEvent) {
                            if (event.awtTransferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                                component.onExternalTextDraggedIn {
                                    (event.awtTransferable.getTransferData(
                                        DataFlavor.stringFlavor
                                    ) as String)
                                }
                                return
                            }

                            if (event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                component.onExternalFilesDraggedIn {
                                    (event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>).filterIsInstance<File>()
                                }
                                return
                            }
                        }

                        override fun onStarted(event: DragAndDropEvent) {
                            isDragging = true
                            onDraggedIn(event)
                        }

                        override fun onEnded(event: DragAndDropEvent) {
                            isDragging = false
                            component.onDragExit()
                        }

                        override fun onDrop(event: DragAndDropEvent): Boolean {
                            isDragging = false
                            if (Platform.isMac()) {
                                onDraggedIn(event)
                            }
                            component.onDropped()
                            return true
                        }
                    }
                }
            )
    ) {
        Column(
            Modifier.alpha(
                animateFloatAsState(if (isDragging) 0.2f else 1f).value
            )
        ) {
            if (!mergeTopBar) {
                WithTitleBarDirection {
                    Spacer(Modifier.height(4.dp))
                    TopBar(component)
                    Spacer(Modifier.height(6.dp))
                }
            }
            Spacer(
                Modifier.fillMaxWidth()
                    .height(1.dp)
                    .background(myColors.surface)
            )
            Row {
                val categoriesWidth by component.categoriesWidth.collectAsState()
                Column(
                    Modifier
                        .padding(top = 8.dp).width(categoriesWidth)
                        .verticalScroll(rememberScrollState())
                ) {
                    Categories(
                        modifier = Modifier.fillMaxWidth(),
                        component = component,
                    )
                    Spacer(Modifier.size(8.dp))
                    QueuesSection(
                        modifier = Modifier.fillMaxWidth(),
                        component = component,
                    )
                    Spacer(Modifier.size(8.dp))
                }
                Spacer(Modifier.size(8.dp))
                //split pane
                Handle(
                    Modifier.width(5.dp)
                        .fillMaxHeight()
                ) { delta ->
                    component.setCategoriesWidth { it + delta }
                }
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.size(4.dp))
                        AddUrlButton {
                            component.requestEnterNewURL()
                        }
                        Actions(
                            component.headerActions,
                            component.showLabels.collectAsState().value
                        )
                    }
                    var lastSelected by remember { mutableStateOf(null as Long?) }
                    DownloadList(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .fillMaxWidth()
                            .weight(1f),
                        downloadList = listState,
                        downloadOptions = component.downloadOptions.collectAsState().value,
                        onRequestCloseOption = {
                            component.onRequestCloseDownloadItemOption()
                        },
                        onRequestOpenOption = { itemState ->
                            component.onRequestOpenDownloadItemOption(itemState)
                        },
                        selectionList = component.selectionList.collectAsState().value,
                        onItemSelectionChange = { id, checked ->
                            lastSelected = id
                            component.onItemSelectionChange(id, checked)
                        },
                        onRequestOpenDownload = {
                            component.openFileOrShowProperties(it)
                        },
                        onNewSelection = {
                            component.newSelection(ids = it)
                        },
                        lastSelectedId = lastSelected,
                        tableState = tableState,
                        fileIconProvider = component.fileIconProvider,
                        categoryManager = component.categoryManager,
                        queuePositions = queuePositions,
                        queueManager = component.queueManager,
                        lazyListState = lazyListState,
                    )
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                myColors.surface
                            )
                    )
                    Footer(component)
                }
            }
        }
        NotificationArea(
            Modifier
                .width(310.dp)
                .padding(24.dp)
                .align(Alignment.BottomEnd)
        )
        AnimatedVisibility(
            visible = isDragging,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            DragWidget(
                Modifier.fillMaxSize()
                    .wrapContentSize(Alignment.Center),
                component.currentActiveDrops.value?.size,
            )
        }
    }
}

@Composable
private fun shouldMergeTopBarWithTitleBar(component: HomeComponent): Boolean {
    val mergeTopBarWithTitleBarInSettings = component.mergeTopBarWithTitleBar.collectAsState().value
    if (!mergeTopBarWithTitleBarInSettings) return false
    val density = LocalDensity.current
    val widthDp = density.run {
        LocalWindowInfo.current.containerSize.width.toDp()
    }
    return widthDp > 700.dp
}


@Composable
private fun ShowDeletePrompts(
    deletePromptState: DeletePromptState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val shape = myShapes.defaultRounded
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .clip(shape)
                .border(2.dp, myColors.onBackground / 10, shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            myColors.surface,
                            myColors.background,
                        )
                    )
                )
                .padding(16.dp)
                .width(IntrinsicSize.Max)
                .widthIn(max = 260.dp)
        ) {
            Text(
                myStringResource(Res.string.confirm_delete_download_items_title),
                fontWeight = FontWeight.Bold,
                fontSize = myTextSizes.xl,
                color = myColors.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            val finishedCount = deletePromptState.finishedCount
            val unfinishedCount = deletePromptState.unfinishedCount
            Text(
                when {
                    deletePromptState.hasBothFinishedAndUnfinished() -> {
                        Res.string.confirm_delete_download_finished_and_unfinished_items_description.asStringSourceWithARgs(
                            Res.string.confirm_delete_download_finished_and_unfinished_items_description_createArgs(
                                finishedCount = finishedCount.toString(),
                                unfinishedCount = unfinishedCount.toString(),
                            )
                        )
                    }

                    deletePromptState.hasUnfinishedDownloads -> {
                        Res.string.confirm_delete_download_unfinished_items_description.asStringSourceWithARgs(
                            Res.string.confirm_delete_download_unfinished_items_description_createArgs(
                                count = unfinishedCount.toString(),
                            )
                        )
                    }

                    else -> {
                        Res.string.confirm_delete_download_items_description.asStringSourceWithARgs(
                            Res.string.confirm_delete_download_items_description_createArgs(
                                count = finishedCount.toString()
                            ),
                        )
                    }
                }.rememberString(),
                fontSize = myTextSizes.base,
                color = myColors.onBackground,
            )
            if (deletePromptState.hasFinishedDownloads) {
                Spacer(Modifier.height(12.dp))
                val alsoDeleteFileInteractionSource = remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .clickable(
                            interactionSource = alsoDeleteFileInteractionSource,
                            indication = null
                        ) {
                            deletePromptState.alsoDeleteFile = !deletePromptState.alsoDeleteFile
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CheckBox(
                        value = deletePromptState.alsoDeleteFile,
                        onValueChange = {
                            deletePromptState.alsoDeleteFile = it
                        },
                        modifier = Modifier
                            // the Row itself is clickable (focusable) so we don't need to focus this checkbox
                            // is there a better way?
                            .focusProperties { canFocus = false },
                        interactionSource = alsoDeleteFileInteractionSource,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        myStringResource(Res.string.also_delete_file_from_disk),
                        fontSize = myTextSizes.base,
                        color = myColors.onBackground,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val confirmFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    confirmFocusRequester.requestFocus()
                }
                Spacer(Modifier.weight(1f))
                ActionButton(
                    text = myStringResource(Res.string.delete),
                    onClick = onConfirm,
                    focusedBorderColor = SolidColor(myColors.error),
                    contentColor = myColors.error,
                    modifier = Modifier.focusRequester(confirmFocusRequester)
                )
                Spacer(Modifier.width(8.dp))
                ActionButton(text = myStringResource(Res.string.cancel), onClick = onCancel)
            }
        }
    }
}

@Composable
private fun ShowConfirmPrompt(
    promptState: ConfirmPromptState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val shape = myShapes.defaultRounded
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .clip(shape)
                .border(2.dp, myColors.onBackground / 10, shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            myColors.surface,
                            myColors.background,
                        )
                    )
                )
                .padding(16.dp)
                .width(IntrinsicSize.Max)
                .widthIn(max = 260.dp)
        ) {
            Text(
                text = promptState.title.rememberString(),
                fontWeight = FontWeight.Bold,
                fontSize = myTextSizes.xl,
                color = myColors.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = promptState.description.rememberString(),
                fontSize = myTextSizes.base,
                color = myColors.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val confirmFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    confirmFocusRequester.requestFocus()
                }
                Spacer(Modifier.weight(1f))
                ActionButton(
                    text = myStringResource(Res.string.ok),
                    onClick = onConfirm,
                    modifier = Modifier.focusRequester(confirmFocusRequester)
                )
                Spacer(Modifier.width(8.dp))
                ActionButton(text = myStringResource(Res.string.cancel), onClick = onCancel)
            }
        }
    }
}

@Composable
private fun ShowDeleteCategoryPrompt(
    deletePromptState: CategoryDeletePromptState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val shape = myShapes.defaultRounded
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier
                .clip(shape)
                .border(2.dp, myColors.onBackground / 10, shape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            myColors.surface,
                            myColors.background,
                        )
                    )
                )
                .padding(16.dp)
                .width(IntrinsicSize.Max)
                .widthIn(max = 260.dp)
        ) {
            Text(
                myStringResource(
                    Res.string.confirm_delete_category_item_title,
                    Res.string.confirm_delete_category_item_title_createArgs(
                        name = deletePromptState.category.name
                    ),
                ),
                fontWeight = FontWeight.Bold,
                fontSize = myTextSizes.xl,
                color = myColors.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                myStringResource(
                    Res.string.confirm_delete_category_item_description,
                    Res.string.confirm_delete_category_item_description_createArgs(
                        value = deletePromptState.category.name
                    )
                ),
                fontSize = myTextSizes.base,
                color = myColors.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                myStringResource(Res.string.your_download_will_not_be_deleted),
                fontSize = myTextSizes.base,
                color = myColors.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val confirmFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    confirmFocusRequester.requestFocus()
                }
                Spacer(Modifier.weight(1f))
                ActionButton(
                    text = myStringResource(Res.string.delete),
                    onClick = onConfirm,
                    focusedBorderColor = SolidColor(myColors.error),
                    modifier = Modifier.focusRequester(confirmFocusRequester),
                    contentColor = myColors.error,
                )
                Spacer(Modifier.width(8.dp))
                ActionButton(text = myStringResource(Res.string.cancel), onClick = onCancel)
            }
        }
    }
}

@Composable
fun DragWidget(
    modifier: Modifier,
    linkCount: Int?,
) {
    val shape = RoundedCornerShape(12.dp)
    val background = myColors.onBackground / 10
    Column(
        modifier
            .clip(shape)
            .background(background)
            .padding(8.dp)
            .dashedBorder(
                shape = shape,
                width = 2.dp,
                color = myColors.onBackground,
                on = 1.dp,
                off = 4.dp
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MyIcon(
            MyIcons.download,
            null,
            Modifier.size(36.dp),
        )
        Text(
            text = myStringResource(Res.string.drop_link_or_file_here),
            fontSize = myTextSizes.xl
        )
        if (linkCount != null && Platform.isMac().not()) {
            when {
                linkCount > 0 -> {
                    Text(
                        myStringResource(
                            Res.string.n_links_will_be_imported,
                            Res.string.n_links_will_be_imported_createArgs(
                                count = linkCount.toString()
                            )
                        ),
                        fontSize = myTextSizes.base,
                        color = myColors.success,
                    )
                }

                linkCount == 0 -> {
                    Text(myStringResource(Res.string.nothing_will_be_imported))
                }
            }
        }
    }
}


@Composable
private fun Categories(
    modifier: Modifier,
    component: HomeComponent,
) {

    val currentTypeFilter = component.filterState.typeCategoryFilter
    val currentStatusFilter = component.filterState.statusFilter
    val categories by component.categoryManager.categoriesFlow.collectAsState()
    val clipShape = myShapes.defaultRounded
    val showCategoryOption by component.categoryActions.collectAsState()

    fun showCategoryOption(item: Category?) {
        component.showCategoryOptions(item)
    }

    fun closeCategoryOptions() {
        component.closeCategoryOptions()
    }
    Column(
        modifier
            .padding(start = 16.dp)
            .clip(clipShape)
            .border(1.dp, myColors.surface, clipShape)
            .padding(1.dp)
    ) {
        var expendedItem: DownloadStatusCategoryFilter? by remember {
            mutableStateOf(
                currentStatusFilter
            )
        }
        for (statusCategoryFilter in DefinedStatusCategories.values()) {
            StatusFilterItem(
                isExpanded = expendedItem == statusCategoryFilter,
                currentTypeCategoryFilter = currentTypeFilter,
                currentStatusCategoryFilter = currentStatusFilter,
                statusFilter = statusCategoryFilter,
                categories = categories,
                onFilterChange = {
                    component.onCategoryFilterChange(statusCategoryFilter, it)
                },
                onRequestExpand = { expand ->
                    expendedItem = statusCategoryFilter.takeIf { expand }
                },
                onItemsDroppedInCategory = { category, ids ->
                    component.moveItemsToCategory(category, ids)
                },
                onRequestOpenOptionMenu = {
                    showCategoryOption(it)
                }
            )
        }
    }
    showCategoryOption?.let {
        CategoryOption(
            categoryOptionMenuState = it,
            onDismiss = {
                closeCategoryOptions()
            }
        )
    }
}

@Composable
fun CategoryOption(
    categoryOptionMenuState: CategoryActions,
    onDismiss: () -> Unit,
) {
    ShowOptionsInPopup(
        MenuItem.SubMenu(
            icon = categoryOptionMenuState.categoryItem?.rememberIconPainter(),
            title = categoryOptionMenuState.categoryItem?.name?.asStringSource()
                ?: Res.string.categories.asStringSource(),
            categoryOptionMenuState.menu,
        ),
        onDismiss
    )
}

@Composable
private fun HomeMenuBar(
    component: HomeComponent,
    modifier: Modifier,
) {
    val nativeMenuBarWithTitleBarInSettings by component.useNativeMenuBar.collectAsState()
    val menu = component.menu
    if (nativeMenuBarWithTitleBarInSettings) {
        val scope = LocalFrameWindowScope.current
        NativeMenuBar(scope, menu)
    } else {
        MenuBar(
            modifier,
            menu
        )
    }
}

@Composable
private fun Footer(component: HomeComponent) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(1f))
        val activeCount by component.activeDownloadCountFlow.collectAsState()
        FooterItem(MyIcons.activeCount, activeCount.toString(), "")
        val size by component.globalSpeedFlow.collectAsState(0)
        val speed = convertPositiveBytesToSizeUnit(size, LocalSpeedUnit.current)
        if (speed != null) {
            val speedText = speed.formatedValue()
            val unitText = speed.unit.toString() + "/s"
            FooterItem(MyIcons.speed, speedText, unitText)
        }

        // Scheduler group
        val schedule by component.speedSchedule.collectAsState()
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SchedulerToggleButton(component)
            if (schedule.enabled) {
                SchedulerLimitInlineEditor(component)
            }
        }

        // Global limit group
        val speedLimit by component.speedLimit.collectAsState()
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeedLimitToggleButton(component)
            if (speedLimit > 0L) {
                SpeedLimitInlineEditor(component)
            }
        }
    }
}

@Composable
private fun SchedulerLimitInlineEditor(component: HomeComponent) {
    val schedule by component.speedSchedule.collectAsState()
    val displayBytes = schedule.alternativeSpeedLimit

    val speedUnit = LocalSpeedUnit.current
    val allowedFactors = listOf(
        SizeFactors.FactorValue.Kilo,
        SizeFactors.FactorValue.Mega,
    )
    val units = remember(speedUnit) {
        allowedFactors.map {
            SizeUnit(
                factorValue = it,
                baseSize = speedUnit.baseSize,
                factors = speedUnit.factors
            )
        }
    }

    var currentUnit by remember(displayBytes, speedUnit) {
        mutableStateOf(
            SizeConverter.bytesToSize(
                displayBytes,
                speedUnit.copy(acceptedFactors = allowedFactors)
            ).unit
        )
    }
    var currentValue by remember(displayBytes, currentUnit) {
        val v = SizeConverter.bytesToSize(displayBytes, currentUnit.asConverterConfig())
            .formatedValue()
            .toDouble()
        mutableStateOf(v)
    }

    fun applySchedulerLimit(value: Double, unit: SizeUnit) {
        val bytes = SizeConverter.sizeToBytes(SizeWithUnit(value, unit))
        if (bytes <= 0L) {
            component.setSchedulerLimit(SpeedLimitDefaults.MIN_LIMIT_BYTES)
            return
        }
        component.setSchedulerLimit(bytes.coerceAtLeast(SpeedLimitDefaults.MIN_LIMIT_BYTES))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        NumberTextField(
            value = currentValue,
            onValueChange = {
                currentValue = it
                applySchedulerLimit(it, currentUnit)
            },
            enc = { currentValue + it * 1.0 },
            toValue = { it.toDoubleOrNull() },
            fromValue = { it.toString() },
            range = 0.0..1_000.0,
            modifier = Modifier
                .width(54.dp)
                .height(28.dp)
                .focusProperties { canFocus = false },
            enabled = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Decimal
            ),
            keyboardActions = KeyboardActions.Default,
            textPadding = PaddingValues(horizontal = 3.dp, vertical = 1.dp),
            shape = myShapes.defaultRounded,
        )
        Spacer(Modifier.width(4.dp))
        RenderSpinner(
            possibleValues = units,
            value = currentUnit,
            modifier = Modifier
                .width(54.dp)
                .height(28.dp)
                .focusProperties { canFocus = false },
            onSelect = {
                currentUnit = it
                applySchedulerLimit(currentValue, it)
            }
        ) { unit ->
            val prettified = remember(unit) { "$unit/s" }
            Text(prettified, fontSize = myTextSizes.xs)
        }
    }
}

@Composable
private fun SpeedLimitInlineEditor(component: HomeComponent) {
    val speedLimit by component.speedLimit.collectAsState()
    val lastCustomLimit by component.lastCustomSpeedLimit.collectAsState()
    val displayBytes = if (speedLimit > 0L) speedLimit else lastCustomLimit

    val speedUnit = LocalSpeedUnit.current
    val allowedFactors = listOf(
        SizeFactors.FactorValue.Kilo,
        SizeFactors.FactorValue.Mega,
    )
    val units = remember(speedUnit) {
        allowedFactors.map {
            SizeUnit(
                factorValue = it,
                baseSize = speedUnit.baseSize,
                factors = speedUnit.factors
            )
        }
    }

    var currentUnit by remember(displayBytes, speedUnit) {
        mutableStateOf(
            SizeConverter.bytesToSize(
                displayBytes,
                speedUnit.copy(acceptedFactors = allowedFactors)
            ).unit
        )
    }
    var currentValue by remember(displayBytes, currentUnit) {
        val v = SizeConverter.bytesToSize(displayBytes, currentUnit.asConverterConfig())
            .formatedValue()
            .toDouble()
        mutableStateOf(v)
    }

    fun applySpeedLimit(value: Double, unit: SizeUnit) {
        val bytes = SizeConverter.sizeToBytes(SizeWithUnit(value, unit))
        if (bytes <= 0L) {
            component.setSpeedLimit(0L)
            return
        }
        component.setSpeedLimit(bytes.coerceAtLeast(SpeedLimitDefaults.MIN_LIMIT_BYTES))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        NumberTextField(
            value = currentValue,
            onValueChange = {
                currentValue = it
                applySpeedLimit(it, currentUnit)
            },
            enc = { currentValue + it * 1.0 },
            toValue = { it.toDoubleOrNull() },
            fromValue = { it.toString() },
            range = 0.0..1_000.0,
            modifier = Modifier
                .width(54.dp)
                .height(28.dp)
                .focusProperties { canFocus = false },
            enabled = true,
            keyboardOptions = KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Decimal
            ),
            keyboardActions = KeyboardActions.Default,
            textPadding = PaddingValues(horizontal = 3.dp, vertical = 1.dp),
            shape = myShapes.defaultRounded,
        )
        Spacer(Modifier.width(4.dp))
        RenderSpinner(
            possibleValues = units,
            value = currentUnit,
            modifier = Modifier
                .width(54.dp)
                .height(28.dp)
                .focusProperties { canFocus = false },
            onSelect = {
                currentUnit = it
                applySpeedLimit(currentValue, it)
            }
        ) { unit ->
            val prettified = remember(unit) { "$unit/s" }
            Text(prettified, fontSize = myTextSizes.xs)
        }
    }
}

@Composable
private fun SchedulerToggleButton(component: HomeComponent) {
    val schedule by component.speedSchedule.collectAsState()
    val isSchedulerActive by component.isSchedulerActive.collectAsState()
    val isEnabled = schedule.enabled

    @Composable
    fun getTooltipText(): String {
        if (isSchedulerActive) {
            val limit = convertPositiveBytesToSizeUnit(schedule.alternativeSpeedLimit, LocalSpeedUnit.current)
            val formattedValue = limit?.formatedValue() ?: "?"
            val unitText = limit?.unit?.toString() ?: "-"
            return "Scheduler active: $formattedValue $unitText/s (click to disable)"
        }
        if (isEnabled) {
            return "Scheduler: Inactive (waiting for scheduled time)"
        }
        return "Scheduler: Disabled (click to enable)"
    }

    IconActionButton(
        onClick = { component.toggleScheduler() },
        icon = MyIcons.clock,
        contentDescription = getTooltipText().asStringSource(),
        indicateActive = isSchedulerActive,
        focusedBorderColor = myColors.warning,
        modifier = Modifier
            .size(28.dp)
            .focusProperties { canFocus = false }
    )
}

@Composable
private fun SpeedLimitToggleButton(component: HomeComponent) {
    val speedLimit by component.speedLimit.collectAsState()
    val isSchedulerActive by component.isSchedulerActive.collectAsState()
    val isEnabled = speedLimit > 0L

    val tooltipText = buildString {
        if (!isEnabled) {
            append("Global limit: Off")
            if (isSchedulerActive) {
                append(" (scheduler is active)")
            }
            return@buildString
        }

        val limit = convertPositiveBytesToSizeUnit(speedLimit, LocalSpeedUnit.current)
        val formattedValue = limit?.formatedValue() ?: "?"
        val unitText = limit?.unit?.toString() ?: "-"
        append("Global limit: $formattedValue $unitText/s")
        if (isSchedulerActive) {
            append(" (overridden by scheduler)")
        }
    }

    IconActionButton(
        onClick = { component.toggleSpeedLimit() },
        icon = MyIcons.fast,
        contentDescription = tooltipText.asStringSource(),
        indicateActive = isEnabled,
        modifier = Modifier
            .size(28.dp)
            .focusProperties { canFocus = false }
    )
}

@Composable
private fun FooterItem(icon: IconSource, value: String, unit: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(28.dp)
    ) {
        WithContentAlpha(0.25f) {
            MyIcon(icon, null, Modifier.size(16.dp))
        }
        Spacer(Modifier.width(8.dp))
        WithContentAlpha(0.75f) {
            Text(value, maxLines = 1, fontSize = myTextSizes.base)
        }
        Spacer(Modifier.width(8.dp))
        WithContentAlpha(0.25f) {
            Text(unit, maxLines = 1, fontSize = myTextSizes.base)
        }
    }
}

@Composable
private fun TopBar(component: HomeComponent) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HomeMenuBar(component, Modifier)
        Box(Modifier.weight(1f))
        HomeSearch(
            component = component,
            modifier = Modifier,
            textPadding = PaddingValues(8.dp),
        )
    }
}

@Composable
fun HomeSearch(
    component: HomeComponent,
    modifier: Modifier,
    textPadding: PaddingValues = PaddingValues(horizontal = 8.dp),
) {
    val searchBoxInteractionSource = remember { MutableInteractionSource() }

    val isFocused by searchBoxInteractionSource.collectIsFocusedAsState()
    WithLanguageDirection {
        SearchBox(
            text = component.filterState.textToSearch,
            onTextChange = {
                component.filterState.textToSearch = it
            },
            textPadding = textPadding,
            interactionSource = searchBoxInteractionSource,
            modifier = modifier
                .width(
                    animateDpAsState(
                        if (isFocused) 220.dp else 180.dp
                    ).value
                )
        )
    }
}


