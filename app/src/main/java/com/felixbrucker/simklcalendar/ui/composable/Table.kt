package com.felixbrucker.simklcalendar.ui.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import kotlin.math.max

@Composable
fun Table(
    rows: Int,
    columns: Int,
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    columnWeightIndex: Int? = null,
    cell: @Composable (row: Int, column: Int) -> Unit
) {
    val measurePolicy = remember(rows, columns, verticalAlignment, horizontalAlignment, columnWeightIndex) {
        TableMeasurePolicy(rows, columns, verticalAlignment, horizontalAlignment, columnWeightIndex)
    }
    Layout(
        content = {
            repeat(rows) { row ->
                repeat(columns) { column ->
                    cell(row, column)
                }
            }
        },
        measurePolicy = measurePolicy,
        modifier = modifier
    )
}

private class TableMeasurePolicy(
    private val rows: Int,
    private val columns: Int,
    private val verticalAlignment: Alignment.Vertical,
    private val horizontalAlignment: Alignment.Horizontal,
    private val columnWeightIndex: Int?
) : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val cellConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        // Array and IntArray avoid List and Integer boxing allocations during layout measure passes
        val measured = Array(measurables.size) { i -> measurables[i].measure(cellConstraints) }

        val columnWidths = IntArray(columns)
        for (column in 0 until columns) {
            var maxCellWidth = 0
            for (row in 0 until rows) {
                val placeable = measured[(row * columns) + column]
                if (placeable.width > maxCellWidth) {
                    maxCellWidth = placeable.width
                }
            }
            columnWidths[column] = maxCellWidth
        }

        if (columnWeightIndex != null && columnWeightIndex in 0 until columns) {
            val currentTotalWidth = columnWidths.sum()
            val extraWidth = max(0, constraints.minWidth - currentTotalWidth)
            columnWidths[columnWeightIndex] += extraWidth
        }

        val rowHeights = IntArray(rows)
        for (row in 0 until rows) {
            var maxCellHeight = 0
            for (column in 0 until columns) {
                val placeable = measured[(row * columns) + column]
                if (placeable.height > maxCellHeight) {
                    maxCellHeight = placeable.height
                }
            }
            rowHeights[row] = maxCellHeight
        }

        val tableWidth = columnWidths.sum()
        val tableHeight = rowHeights.sum()

        return layout(tableWidth, tableHeight) {
            var y = 0
            for (row in 0 until rows) {
                var x = 0
                val rowHeight = rowHeights[row]
                for (column in 0 until columns) {
                    val placeable = measured[(row * columns) + column]
                    val columnWidth = columnWidths[column]
                    val yOffset = verticalAlignment.align(placeable.height, rowHeight)
                    val xOffset = horizontalAlignment.align(placeable.width, columnWidth, layoutDirection)
                    placeable.placeRelative(x + xOffset, y + yOffset)

                    x += columnWidth
                }
                y += rowHeight
            }
        }
    }

}
