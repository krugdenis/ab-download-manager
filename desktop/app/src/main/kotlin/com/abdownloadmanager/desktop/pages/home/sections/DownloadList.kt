package com.abdownloadmanager.desktop.pages.home.sections

import com.abdownloadmanager.shared.util.DOUBLE_CLICK_DELAY
import com.abdownloadmanager.shared.util.ui.WithContentAlpha
import com.abdownloadmanager.shared.util.ui.getQueueColor
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.ui.widget.CheckBox
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.ui.widget.table.customtable.Table
import com.abdownloadmanager.shared.ui.widget.table.customtable.styled.MyStyledTableHeader
import com.abdownloadmanager.shared.ui.widget.menu.custom.LocalMenuDisabledItemBehavior
import com.abdownloadmanager.shared.ui.widget.menu.custom.MenuDisabledItemBehavior
import com.abdownloadmanager.shared.ui.widget.menu.custom.ShowOptionsInPopup
import ir.amirab.util.compose.action.MenuItem
import androidx.compose.foundation.*
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.DragAndDropTransferable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.abdownloadmanager.desktop.pages.home.DownloadItemTransferable
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.ui.widget.table.customtable.CellSize
import com.abdownloadmanager.shared.ui.widget.table.customtable.CustomCellRenderer
import com.abdownloadmanager.shared.ui.widget.table.customtable.SortableCell
import com.abdownloadmanager.shared.ui.widget.table.customtable.TableCell
import com.abdownloadmanager.shared.ui.widget.table.customtable.TableState
import com.abdownloadmanager.shared.util.FileIconProvider
import com.abdownloadmanager.shared.util.category.CategoryManager
import com.abdownloadmanager.shared.util.category.rememberCategoryOf
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import com.abdownloadmanager.shared.pages.home.QueuePositionInfo
import ir.amirab.downloader.monitor.*
import ir.amirab.downloader.queue.QueueManager
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.util.compose.resources.myStringResource
import ir.amirab.util.compose.StringSource
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.desktop.isCtrlPressed
import ir.amirab.util.desktop.isShiftPressed
import ir.amirab.util.ifThen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Debounce delay for queue item drag-and-drop reordering in milliseconds.
 *
 * Prevents excessive queue update API calls during continuous dragging by
 * batching rapid position changes into a single update.
 *
 * Value of 150ms is tuned to:
 * - Feel responsive to user (< 200ms perceived as instant)
 * - Avoid overwhelming QueueManager with updates
 * - Allow smooth visual feedback during drag
 */
private const val DRAG_DEBOUNCE_MS = 150L

class DownloadListContext(
    val onNewSelection: (List<Long>) -> Unit,
    val downloadList: List<IDownloadItemState>,
    val isAllSelected: Boolean,
) {
    fun newSelection(ids: List<Long>, isSelected: Boolean) {
        onNewSelection(ids.filter { isSelected })
    }

    fun changeAllSelection(isSelected: Boolean) {
        newSelection(downloadList.map { it.id }, isSelected)
    }
}

private val LocalDownloadListContext = compositionLocalOf<DownloadListContext> {
    error("DownloadListContext not provided")
}

@Composable
fun DownloadList(
    modifier: Modifier,
    downloadList: List<IDownloadItemState>,
    downloadOptions: MenuItem.SubMenu?,
    onRequestOpenOption: (IDownloadItemState) -> Unit,
    tableState: TableState<IDownloadItemState, DownloadListCells>,
    onRequestCloseOption: () -> Unit,
    selectionList: List<Long>,
    onItemSelectionChange: (Long, Boolean) -> Unit,
    onRequestOpenDownload: (Long) -> Unit,
    onNewSelection: (List<Long>) -> Unit,
    lastSelectedId: Long?,
    fileIconProvider: FileIconProvider,
    categoryManager: CategoryManager,
    queuePositions: Map<Long, QueuePositionInfo>,
    queueManager: QueueManager,
    lazyListState: LazyListState
) {
    ShowDownloadOptions(
        downloadOptions, onRequestCloseOption
    )
    val isALlSelected by derivedStateOf {
        val list = downloadList
        if (list.isEmpty()) {
            false
        } else {
            list.map { it.id }.all {
                it in selectionList
            }
        }
    }

    val listToBeDragged by rememberUpdatedState(
        downloadList.filter { it.id in selectionList }
    )

    val tableInteractionSource = remember { MutableInteractionSource() }
    val coroutineScope = rememberCoroutineScope()

    val queueIdByDownloadId = remember(downloadList, queuePositions) {
        downloadList.associate { item ->
            item.id to queuePositions[item.id]?.queueId
        }
    }

    fun newSelection(ids: List<Long>, isSelected: Boolean) {
        onNewSelection(ids.filter { isSelected })
    }

    fun changeAllSelection(isSelected: Boolean) {
        newSelection(downloadList.map { it.id }, isSelected)
    }

    val windowInfo = LocalWindowInfo.current
    var queueUpdateJob: kotlinx.coroutines.Job? by remember { mutableStateOf(null) }

    // Only allow reordering when sorted by queue position ascending
    val sortBy by tableState.sortBy.collectAsState()
    val canReorder = sortBy?.cell is DownloadListCells.QueuePosition && sortBy?.isDescending() == false

    val reorderableState = when {
        !canReorder -> null
        else -> rememberReorderableLazyListState(
            lazyListState,
            onMove = { from, to ->
                val fromItem = downloadList.getOrNull(from.index)
                val toItem = downloadList.getOrNull(to.index)
                if (fromItem == null || toItem == null) {
                    return@rememberReorderableLazyListState
                }
                val fromQueueId = queueIdByDownloadId[fromItem.id]
                val toQueueId = queueIdByDownloadId[toItem.id]
                if (fromQueueId == null || fromQueueId != toQueueId) {
                    return@rememberReorderableLazyListState
                }
                // Thread-safety: QueueManager uses internal mutex for queue modifications
                // Debouncing ensures we don't overwhelm the mutex with concurrent updates
                // Cancel previous pending update and schedule a new one with debounce
                queueUpdateJob?.cancel()
                queueUpdateJob = coroutineScope.launch {
                    delay(DRAG_DEBOUNCE_MS)

                    runCatching {
                        val queue = queueManager.queues.value.find { it.id == fromQueueId }
                            ?: return@launch

                        queue.swapQueueItem(
                            item = fromItem.id,
                            toPosition = { queueItems ->
                                queueItems.indexOf(toItem.id)
                            }
                        )
                    }.onFailure { error ->
                        // swapQueueItem is defensive, but catch other potential issues
                        println("Failed to swap queue items: ${error.message}")
                    }
                }
            }
        )
    }
    CompositionLocalProvider(
        LocalDownloadListContext provides DownloadListContext(
            onNewSelection,
            downloadList,
            isALlSelected,
        )
    ) {
        val itemHorizontalPadding = 16.dp
        Table(
            tableState = tableState,
            listState = lazyListState,
            key = { it.id },
            list = downloadList,
            sortDependencies = listOf(queuePositions),
            reorderableState = reorderableState,
            isItemReorderable = { queueIdByDownloadId[it.id] != null },
            modifier = modifier
                .onKeyEvent {
                    if (it.key == Key.A && isCtrlPressed(windowInfo)) {
                        changeAllSelection(true)
                        true
                    } else {
                        false
                    }
                }
                .onKeyEvent {
                    if (it.key == Key.Escape) {
                        changeAllSelection(false)
                        true
                    } else {
                        false
                    }
                }
                .clickable(
                    indication = null,
                    interactionSource = tableInteractionSource,
                    onClick = {
                        //deselect all on click empty area
                        changeAllSelection(false)
                    },
                ),
            drawOnEmpty = {
                WithContentAlpha(0.75f) {
                    Text(myStringResource(Res.string.list_is_empty), Modifier.align(Alignment.Center))
                }
            },
            wrapHeader = {
                MyStyledTableHeader(itemHorizontalPadding = itemHorizontalPadding, content = it)
            },
            wrapItem = { _, item, rowContent ->
                val isSelected = selectionList.contains(item.id)
                var shouldWaitForSecondClick by remember {
                    mutableStateOf(false)
                }
                LaunchedEffect(shouldWaitForSecondClick) {
                    delay(DOUBLE_CLICK_DELAY)
                    if (shouldWaitForSecondClick) {
                        shouldWaitForSecondClick = false
                    }
                }
                val itemInteractionSource = remember { MutableInteractionSource() }
                CompositionLocalProvider(
                    LocalDownloadItemProperties provides DownloadItemProperties(
                        isSelected,
                        item,
                    )
                ) {
                    val windowInfo = LocalWindowInfo.current
                    WithContentAlpha(1f) {
                        val shape = myShapes.defaultRounded
                        Box(
                            Modifier
                                .widthIn(min = getTableSize().visibleWidth)
                                .ifThen(isSelected) {
                                    dragAndDropSource(
                                        drawDragDecoration = {},
                                        transferData = {
                                            val selectedDownloads = listToBeDragged
                                            if (selectedDownloads.isEmpty() || !isSelected) {
                                                return@dragAndDropSource null
                                            }
                                            val shiftPressed = isShiftPressed(windowInfo)
                                            val supportedActions = listOf(
                                                if (shiftPressed) {
                                                    DragAndDropTransferAction.Move
                                                } else {
                                                    DragAndDropTransferAction.Copy
                                                }
                                            )
                                            DragAndDropTransferData(
                                                transferable = DragAndDropTransferable(
                                                    DownloadItemTransferable(selectedDownloads)
                                                ),
                                                supportedActions = supportedActions,
                                            )
                                        }
                                    )
                                }
                                .onClick(
                                    interactionSource = itemInteractionSource
                                ) {
                                    if (shouldWaitForSecondClick) {
                                        onRequestOpenDownload(item.id)
                                        shouldWaitForSecondClick = false
                                    } else {
                                        if (isCtrlPressed(windowInfo)) {
                                            onItemSelectionChange(item.id, !isSelected)
                                        } else {
                                            changeAllSelection(false)
                                            onItemSelectionChange(item.id, true)
                                            shouldWaitForSecondClick = true
                                        }
                                    }
                                }
                                .onClick(
                                    matcher = PointerMatcher.mouse(PointerButton.Secondary),
                                ) {
                                    onRequestOpenOption(item)
                                }
                                .onClick(
                                    enabled = lastSelectedId != null,
                                    keyboardModifiers = {
                                        this.isShiftPressed
                                    }
                                ) {

                                    val lastSelected = lastSelectedId ?: return@onClick
                                    val currentId = item.id

                                    val ids = tableState.getARangeOfItems(
                                        list = downloadList,
                                        id = { it.id },
                                        fromItem = lastSelected,
                                        toItem = currentId,
                                    )
                                    newSelection(ids, true)
                                }
                                .padding(vertical = 1.dp)
                                .clip(shape)
                                .indication(
                                    interactionSource = itemInteractionSource,
                                    indication = LocalIndication.current
                                )
                                .hoverable(itemInteractionSource)
                                .focusable(
                                    interactionSource = itemInteractionSource
                                )
                                .let {
                                    if (isSelected) {
                                        val selectionColor = myColors.onBackground
                                        it
                                            .border(
                                                1.dp,
                                                myColors.selectionGradient(0.10f, 0.05f, selectionColor),
                                                shape
                                            )
                                            .background(myColors.selectionGradient(0.15f, 0.03f, selectionColor))
                                    } else {
                                        it.border(1.dp, Color.Transparent)
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = itemHorizontalPadding)
                        ) {
                            rowContent()
                        }
                    }
                }
            }
        ) { cell, item ->
            when (cell) {
                DownloadListCells.Check -> {
                    CheckCell(
                        onCheckedChange = { downloadId, isChecked ->
                            val currentSelection = selectionList.find {
                                downloadId == it
                            }?.let { true } ?: false
                            onItemSelectionChange(downloadId, !currentSelection)
                        },
                        dItemState = item
                    )
                }

                is DownloadListCells.QueuePosition -> {
                    val positionInfo = queuePositions[item.id]
                    positionInfo?.let {
                        QueuePositionCell(
                            position = it.position,
                            queueColor = myColors.getQueueColor(it.queueId),
                            isActive = item.statusOrFinished() is DownloadJobStatus.IsActive,
                        )
                    } ?: Box(Modifier.fillMaxSize())
                }

                DownloadListCells.Name -> {
                    NameCell(
                        itemState = item,
                        category = categoryManager.rememberCategoryOf(item.id),
                        fileIconProvider = fileIconProvider,
                    )
                }

                DownloadListCells.DateAdded -> {
                    DateAddedCell(item)
                }

                DownloadListCells.Size -> {
                    SizeCell(item)
                }

                DownloadListCells.Speed -> {
                    SpeedCell(item)
                }

                DownloadListCells.Status -> {
                    StatusCell(item)
                }

                DownloadListCells.TimeLeft -> {
                    TimeLeftCell(item)
                }
            }
        }
    }
}

sealed interface DownloadListCells : TableCell<IDownloadItemState> {
    data object Check : DownloadListCells,
        CustomCellRenderer {
        override val id: String = "#"
        override val name: StringSource = "#".asStringSource()
        override val size: CellSize = CellSize.Fixed(26.dp)

        @Composable
        override fun drawHeader() {
            val c = LocalDownloadListContext.current
            CheckBox(
                c.isAllSelected,
                {
                    c.changeAllSelection(it)
                },
                modifier = Modifier.size(12.dp)
            )
        }
    }

    data class QueuePosition(
        private val positionProvider: (Long) -> Int?
    ) : DownloadListCells, SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy {
            positionProvider(it.id) ?: Int.MAX_VALUE
        }

        override val id: String = "QueuePosition"
        override val name: StringSource = "#".asStringSource()
        override val size: CellSize = CellSize.Fixed(60.dp)
    }

    data object Name : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.name }

        override val id: String = "Name"
        override val name: StringSource = Res.string.name.asStringSource()
        override val size: CellSize = CellSize.Resizeable(50.dp..1000.dp, 200.dp)
    }

    data object Status : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy(
            {
                it.statusOrFinished().order
            }, {
                when (it) {
                    is CompletedDownloadItemState -> 100
                    is ProcessingDownloadItemState -> it.percent ?: 0
                }
            }
        )

        override val id: String = "Status"
        override val name: StringSource = Res.string.status.asStringSource()
        override val size: CellSize = CellSize.Resizeable(100.dp..140.dp, 120.dp)
    }

    data object Size : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.contentLength }

        override val id: String = "Size"
        override val name: StringSource = Res.string.size.asStringSource()
        override val size: CellSize = CellSize.Resizeable(70.dp..110.dp, 70.dp)
    }

    data object Speed : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.speedOrNull() ?: 0L }

        override val id: String = "Speed"
        override val name: StringSource = Res.string.speed.asStringSource()
        override val size: CellSize = CellSize.Resizeable(70.dp..110.dp, 80.dp)
    }

    data object TimeLeft : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.remainingOrNull() ?: Long.MAX_VALUE }

        override val id: String = "Time Left"
        override val name: StringSource = Res.string.time_left.asStringSource()
        override val size: CellSize = CellSize.Resizeable(70.dp..150.dp, 100.dp)
    }

    data object DateAdded : DownloadListCells,
        SortableCell<IDownloadItemState> {
        override fun comparator(): Comparator<IDownloadItemState> = compareBy { it.dateAdded }

        override val id: String = "Date Added"
        override val name: StringSource = Res.string.date_added.asStringSource()
        override val size: CellSize = CellSize.Resizeable(90.dp..150.dp, 100.dp)
    }
}

@Composable
fun ShowDownloadOptions(
    options: MenuItem.SubMenu?,
    onDismiss: () -> Unit,
) {
    if (options != null) {
        CompositionLocalProvider(
            LocalMenuDisabledItemBehavior provides MenuDisabledItemBehavior.LowerOpacity
        ) {
            ShowOptionsInPopup(options, onDismiss)
        }
    }
}
