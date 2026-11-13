package com.aibuilt.simplecalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

// ---------- Colours ----------
private val BgDark = Color(0xFF0F1013)
private val DisplaySurface = Color(0xFF1B1D22)
private val DigitButton = Color(0xFF2B2E35)
private val DigitText = Color(0xFFF5F5F7)
private val OperatorButton = Color(0xFF3C4157)
private val OperatorText = Color(0xFFE7EAF5)
private val Amber = Color(0xFFFFB020)
private val AmberText = Color(0xFF231600)
private val DimGrey = Color(0xFF8A8F9C)
private val ErrorRed = Color(0xFFFF7A6B)

private val CalculatorColorScheme = darkColorScheme(
    primary = Amber,
    onPrimary = AmberText,
    background = BgDark,
    onBackground = DigitText,
    surface = DisplaySurface,
    onSurface = DigitText
)

// ---------- Key model ----------
private enum class KeyType { DIGIT, DECIMAL, OPERATOR, EQUALS, AC, BACKSPACE, PERCENT, SIGN }
private enum class KeyStyle { DIGIT, OPERATOR, EQUALS }

private data class CalcButton(
    val label: String,
    val type: KeyType,
    val value: String = "",
    val style: KeyStyle = KeyStyle.DIGIT
)

private val ButtonRows: List<List<CalcButton>> = listOf(
    listOf(
        CalcButton("AC", KeyType.AC, style = KeyStyle.OPERATOR),
        CalcButton("\u232B", KeyType.BACKSPACE, style = KeyStyle.OPERATOR),
        CalcButton("%", KeyType.PERCENT, style = KeyStyle.OPERATOR),
        CalcButton("\u00F7", KeyType.OPERATOR, "\u00F7", KeyStyle.OPERATOR)
    ),
    listOf(
        CalcButton("7", KeyType.DIGIT, "7"),
        CalcButton("8", KeyType.DIGIT, "8"),
        CalcButton("9", KeyType.DIGIT, "9"),
        CalcButton("\u00D7", KeyType.OPERATOR, "\u00D7", KeyStyle.OPERATOR)
    ),
    listOf(
        CalcButton("4", KeyType.DIGIT, "4"),
        CalcButton("5", KeyType.DIGIT, "5"),
        CalcButton("6", KeyType.DIGIT, "6"),
        CalcButton("\u2212", KeyType.OPERATOR, "-", KeyStyle.OPERATOR)
    ),
    listOf(
        CalcButton("1", KeyType.DIGIT, "1"),
        CalcButton("2", KeyType.DIGIT, "2"),
        CalcButton("3", KeyType.DIGIT, "3"),
        CalcButton("+", KeyType.OPERATOR, "+", KeyStyle.OPERATOR)
    ),
    listOf(
        CalcButton("\u00B1", KeyType.SIGN),
        CalcButton("0", KeyType.DIGIT, "0"),
        CalcButton(".", KeyType.DECIMAL, "."),
        CalcButton("=", KeyType.EQUALS, style = KeyStyle.EQUALS)
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CalculatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CalculatorScreen()
                }
            }
        }
    }
}

@Composable
private fun CalculatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CalculatorColorScheme, content = content)
}

@Composable
private fun CalculatorScreen() {
    // Everything that must survive a screen rotation is rememberSaveable.
    var tokensRaw by rememberSaveable { mutableStateOf("") }
    var current by rememberSaveable { mutableStateOf("0") }
    var entryActive by rememberSaveable { mutableStateOf(false) }
    var isError by rememberSaveable { mutableStateOf(false) }

    val haptics = LocalHapticFeedback.current

    fun cleanReset() {
        tokensRaw = ""
        current = "0"
        entryActive = false
        isError = false
    }

    fun setError() {
        tokensRaw = ""
        current = "Error"
        entryActive = false
        isError = true
    }

    fun applyKey(button: CalcButton) {
        val tokens = if (tokensRaw.isEmpty()) emptyList() else tokensRaw.split(",")

        when (button.type) {
            KeyType.AC -> cleanReset()

            KeyType.DIGIT, KeyType.DECIMAL -> {
                if (isError) cleanReset()
                val d = button.value
                if (!entryActive) {
                    current = if (d == ".") "0." else d
                    entryActive = true
                } else {
                    when {
                        d == "." && !current.contains('.') -> current += "."
                        d == "." -> Unit
                        current == "0" -> current = d
                        current == "-0" -> current = "-$d"
                        current.length >= 16 -> Unit
                        else -> current += d
                    }
                }
            }

            KeyType.OPERATOR -> {
                if (isError) return
                val newTokens: List<String> = when {
                    entryActive -> tokens + listOf(current, button.value)
                    tokens.isEmpty() -> listOf(current, button.value)
                    tokens.last().toDoubleOrNull() == null -> tokens.dropLast(1) + button.value
                    else -> tokens + button.value
                }
                tokensRaw = newTokens.joinToString(",")
                entryActive = false
                val result = evaluateTokens(newTokens)
                if (result == null || result.isNaN() || result.isInfinite()) setError()
                else current = formatNumber(result)
            }

            KeyType.EQUALS -> {
                if (isError) return
                val newTokens: List<String> = when {
                    entryActive -> tokens + current
                    tokens.isEmpty() -> listOf(current)
                    tokens.last().toDoubleOrNull() == null -> tokens.dropLast(1)
                    else -> tokens
                }
                val result = evaluateTokens(newTokens)
                if (result == null || result.isNaN() || result.isInfinite()) {
                    setError()
                } else {
                    current = formatNumber(result)
                    tokensRaw = ""
                    entryActive = false
                }
            }

            KeyType.BACKSPACE -> {
                if (isError) {
                    cleanReset()
                    return
                }
                if (!entryActive) return
                val next = when {
                    current.length <= 1 -> ""
                    current.length == 2 && current.startsWith("-") -> ""
                    else -> current.dropLast(1)
                }
                if (next.isEmpty() || next == "-") {
                    current = "0"
                    entryActive = false
                } else {
                    current = next
                }
            }

            KeyType.PERCENT -> {
                if (isError) return
                val v = current.toDoubleOrNull() ?: 0.0
                current = formatNumber(v / 100.0)
                entryActive = true
            }

            KeyType.SIGN -> {
                if (isError) return
                if (entryActive || tokens.isEmpty()) {
                    val v = current.toDoubleOrNull() ?: 0.0
                    if (v != 0.0) {
                        current = if (current.startsWith("-")) current.drop(1) else "-$current"
                    }
                }
            }
        }
    }

    val expressionText = if (isError) "" else buildExpression(tokensRaw, current, entryActive)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = DisplaySurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = expressionText.ifEmpty { " " },
                    color = DimGrey,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                AutoSizeText(
                    text = current,
                    color = if (isError) ErrorRed else DigitText,
                    maxFontSize = 62.sp,
                    minFontSize = 18.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val spacing = 10.dp
            val cellByWidth = (maxWidth - spacing * 3) / 4
            val cellByHeight = (maxHeight - spacing * 4) / 5
            val cell: Dp = if (cellByWidth < cellByHeight) cellByWidth else cellByHeight

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ButtonRows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { button ->
                            CalcButtonView(
                                button = button,
                                size = cell,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    applyKey(button)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalcButtonView(
    button: CalcButton,
    size: Dp,
    onClick: () -> Unit
) {
    val background: Color
    val foreground: Color
    when (button.style) {
        KeyStyle.DIGIT -> {
            background = DigitButton
            foreground = DigitText
        }
        KeyStyle.OPERATOR -> {
            background = OperatorButton
            foreground = OperatorText
        }
        KeyStyle.EQUALS -> {
            background = Amber
            foreground = AmberText
        }
    }
    val fontSize = (size.value * 0.33f).coerceIn(16f, 30f).sp

    Surface(
        onClick = onClick,
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = background,
        contentColor = foreground
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = button.label,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * A single line of text that automatically shrinks so that it always fits the
 * available width instead of being clipped or scrolling off screen.
 */
@Composable
private fun AutoSizeText(
    text: String,
    color: Color,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val availableWidth = constraints.maxWidth
        val measurer = rememberTextMeasurer()
        val baseStyle = remember(maxFontSize, fontWeight) {
            TextStyle(fontSize = maxFontSize, fontWeight = fontWeight)
        }
        val measuredWidth = remember(text, availableWidth, baseStyle) {
            if (text.isEmpty() || availableWidth <= 0) 0
            else measurer.measure(
                text = text,
                style = baseStyle,
                maxLines = 1,
                softWrap = false
            ).size.width
        }
        val fontSize: TextUnit = if (measuredWidth > availableWidth && measuredWidth > 0) {
            val scaled = maxFontSize.value * availableWidth.toFloat() / measuredWidth.toFloat()
            scaled.coerceAtLeast(minFontSize.value).sp
        } else {
            maxFontSize
        }
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Builds the small grey line, e.g. "12 + 5". */
private fun buildExpression(tokensRaw: String, current: String, entryActive: Boolean): String {
    if (tokensRaw.isEmpty()) return ""
    val tokens = tokensRaw.split(",")
    val sb = StringBuilder()
    tokens.forEachIndexed { index, token ->
        if (index > 0) sb.append(' ')
        sb.append(token)
    }
    if (entryActive && tokens.isNotEmpty() && tokens.last().toDoubleOrNull() == null) {
        sb.append(' ').append(current)
    }
    return sb.toString()
}

/**
 * Evaluates a token list such as ["2", "+", "3", "\u00D7", "4"] honouring
 * operator precedence. Returns null when the maths is undefined (divide by zero).
 */
private fun evaluateTokens(rawTokens: List<String>): Double? {
    if (rawTokens.isEmpty()) return null
    val tokens = if (rawTokens.last().toDoubleOrNull() == null) rawTokens.dropLast(1) else rawTokens
    if (tokens.isEmpty()) return null

    val values = ArrayList<Double>()
    val ops = ArrayList<String>()

    fun precedence(op: String): Int = if (op == "\u00D7" || op == "\u00F7") 2 else 1

    fun applyTop(): Boolean {
        if (values.size < 2 || ops.isEmpty()) return false
        val b = values.removeAt(values.size - 1)
        val a = values.removeAt(values.size - 1)
        val op = ops.removeAt(ops.size - 1)
        when (op) {
            "+" -> values.add(a + b)
            "-" -> values.add(a - b)
            "\u00D7" -> values.add(a * b)
            "\u00F7" -> {
                if (b == 0.0) return false
                values.add(a / b)
            }
            else -> values.add(b)
        }
        return true
    }

    for (token in tokens) {
        val number = token.toDoubleOrNull()
        if (number != null) {
            values.add(number)
        } else {
            while (ops.isNotEmpty() && precedence(ops.last()) >= precedence(token)) {
                if (!applyTop()) return null
            }
            ops.add(token)
        }
    }
    while (ops.isNotEmpty()) {
        if (!applyTop()) return null
    }
    return values.lastOrNull()
}

/** Formats a number, trimming pointless trailing zeros so 5.0 shows as 5. */
private fun formatNumber(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "Error"
    if (value == 0.0) return "0"
    val abs = if (value < 0) -value else value
    if (abs >= 1e12 || abs < 1e-9) {
        val raw = String.format(Locale.US, "%.6E", value)
        val pieces = raw.split("E")
        var mantissa = pieces[0]
        if (mantissa.contains('.')) {
            mantissa = mantissa.trimEnd('0').trimEnd('.')
        }
        val exponent = pieces.getOrNull(1)?.toIntOrNull() ?: 0
        return mantissa + "e" + exponent
    }
    val bd = BigDecimal(value).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros()
    return bd.toPlainString()
}
