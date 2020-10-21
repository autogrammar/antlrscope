package net.ninjacat.headlights

import javafx.application.Application
import javafx.event.Event
import javafx.event.EventHandler
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Node
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.control.cell.PropertyValueFactory
import javafx.scene.input.KeyEvent
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import javafx.stage.Stage
import org.antlr.v4.runtime.InterpreterRuleContext
import org.antlr.v4.runtime.tree.ErrorNode
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.fxmisc.richtext.CodeArea
import org.fxmisc.richtext.LineNumberFactory
import java.nio.file.Files
import java.nio.file.Paths
import java.util.stream.Collectors


class AntlrViewApp : Application() {
    private val hack = Font.loadFont(javaClass.getResource("/Hack-Regular.ttf").toExternalForm(), 15.0)
    private val grammar: CodeArea = createRichEditor()
    private val text: CodeArea = createRichEditor()
    private val resultPane: VBox = VBox()
    private val outputPane: TabPane = TabPane()
    private val resultsTab: Tab = Tab("Results", Label())
    private val errors: TableView<ErrorMessage> = TableView()

    override fun start(primaryStage: Stage?) {
        primaryStage?.title = "ANTLR in the Headlights"
        primaryStage?.width = 1200.0
        primaryStage?.height = 800.0

        outputPane.tabs?.add(resultsTab)
        outputPane.tabs?.add(Tab("Errors", errors))
        outputPane.tabClosingPolicy = TabPane.TabClosingPolicy.UNAVAILABLE

        resultPane.children?.add(outputPane)
        VBox.setVgrow(outputPane, Priority.ALWAYS)

        configureErrorsView()

        val editorSplit = configureEditors()


        val mainContainer = SplitPane()
        mainContainer.orientation = Orientation.VERTICAL
        mainContainer.items.addAll(
                vboxOf(growing = editorSplit, editorSplit, createBottomBar()),
                resultPane
        )
        mainContainer.style = "-fx-font-smoothing-type: lcd; -fx-font-size: 15"

        val scene = Scene(mainContainer)
        scene.stylesheets.add(javaClass.getResource("/style.css").toExternalForm())
        scene.stylesheets.add(javaClass.getResource("/g4-highlight.css").toExternalForm())
        primaryStage?.scene = scene
        primaryStage?.show()
    }

    private fun configureEditors(): SplitPane {
        setupSyntaxHighlighting(grammar)

        val grammarPosition = createRichCaretPositionIndicator(grammar)
        val textPosition = createRichCaretPositionIndicator(text)

        if (parameters.named.containsKey("grammar")) {
            grammar.replaceText(loadFile(parameters.named["grammar"]))
        }
        if (parameters.named.containsKey("text")) {
            text.replaceText(loadFile(parameters.named["text"]))
        }

        val editorSplit = SplitPane()

        val lGrammar = Label("Grammar")
        val hbGrammar = HBox(lGrammar)
        hbGrammar.style = "-fx-background-color: #00B0D0;"
        val lText = Label("Text")
        val hbText = HBox(lText)
        hbText.style = "-fx-background-color: #00B0D0;"

        editorSplit.items.addAll(
                vboxOf(
                        growing = grammar,
                        hbGrammar,
                        grammar,
                        grammarPosition
                ), vboxOf(
                growing = text,
                hbText,
                text,
                textPosition
        )
        )
        return editorSplit
    }

    private fun setupSyntaxHighlighting(editor: CodeArea) {
        editor.visibleParagraphs.addModificationObserver(
                ParagraphStyler(editor) { text -> G4Highligher.computeHighlighting(text) }
        )
    }

    private fun createRichCaretPositionIndicator(editor: CodeArea): Label {
        val textPosition = Label()
        textPosition.padding = Insets(2.0, 2.0, 2.0, 2.0)
        val updateTextCaretPosition = updateRichCaretPosition(editor, textPosition)
        editor.addEventHandler(MouseEvent.MOUSE_CLICKED, updateTextCaretPosition)
        editor.addEventHandler(KeyEvent.KEY_RELEASED, updateTextCaretPosition)
        editor.textProperty().addListener { _, _, _ -> updateTextCaretPosition.handle(null) }
        return textPosition
    }

    private fun createCaretPositionIndicator(editor: TextArea): Label {
        val textPosition = Label()
        textPosition.padding = Insets(2.0, 2.0, 2.0, 2.0)
        val updateTextCaretPosition = updateCaretPosition(editor, textPosition)
        editor.addEventHandler(MouseEvent.MOUSE_CLICKED, updateTextCaretPosition)
        editor.addEventHandler(KeyEvent.KEY_RELEASED, updateTextCaretPosition)
        editor.textProperty().addListener { _, _, _ -> updateTextCaretPosition.handle(null) }
        return textPosition
    }

    private fun updateCaretPosition(editor: TextArea, label: Label): EventHandler<Event> {
        return EventHandler<Event> {
            val caret = editor.caretPosition
            val (line, pos) = caretToLinePos(caret, editor.text)
            label.text = "${line}:${pos}"
        }
    }

    private fun updateRichCaretPosition(editor: CodeArea, label: Label): EventHandler<Event> {
        return EventHandler<Event> {
            val line = editor.currentParagraph + 1
            val pos = editor.caretColumn + 1
            label.text = "${line}:${pos}"
        }
    }

    private fun caretToLinePos(caret: Int, text: String): Pair<Int, Int> {
        val subtext = text.substring(0, caret)
        val line = subtext.chars().filter { it == '\n'.toInt() }.count().toInt() + 1
        val pos = caret - subtext.indexOfLast { it == '\n' }
        return Pair(line, pos)
    }

    private fun configureErrorsView() {
        val columnPosition = TableColumn<ErrorMessage, String>("Position")
        columnPosition.cellValueFactory = PropertyValueFactory("position")
        val columnMessage = TableColumn<ErrorMessage, String>("Message")
        errors.widthProperty().addListener { _, _, newv ->
            columnMessage.minWidth = newv.toDouble() - columnPosition.width - 5
        }
        columnMessage.cellValueFactory = PropertyValueFactory("message")
        errors.columns.addAll(columnPosition, columnMessage)
        errors.placeholder = Label("")
    }

    private fun vboxOf(growing: Node?, vararg children: Node): Node {
        val vbox = VBox()
        vbox.children.addAll(children)
        if (growing != null) {
            VBox.setVgrow(growing, Priority.ALWAYS)
        }
        return vbox
    }

    private fun loadFile(s: String?): String {
        if (s == null) {
            return ""
        }
        return Files.lines(Paths.get(s)).collect(Collectors.joining("\n"))
    }


    private fun createBottomBar(): Node {
        val bottom = HBox()
        bottom.style = ""
        val parseButton = Button("Parse")
        parseButton.onAction = EventHandler {
            onParseClicked()
        }
        bottom.padding = Insets(2.0, 2.0, 2.0, 2.0)
        bottom.children.add(parseButton)
        return bottom
    }

    private fun createRichEditor(): CodeArea {
        val result = CodeArea()
        result.paragraphGraphicFactory = LineNumberFactory.get(result)
        return result
    }

    private fun onParseClicked() {
        try {
            val antlrResult = AntlrGen.generateTree(grammar.text ?: "", text.text ?: "")

            populateErrorList(antlrResult.errors)

            if (antlrResult.isLexer()) {
                buildTokens(antlrResult.tokens!!)
            } else {
                buildTree(antlrResult.tree!!, antlrResult.grammar.ruleNames.asList())
            }
        } catch (ex: Exception) {
            populateErrorList(
                    listOf(
                            ErrorMessage(-1, -1, ex.message, ErrorSource.UNKNOWN)
                    )
            )
            ex.printStackTrace()
        }
    }

    private fun populateErrorList(errorList: List<ErrorMessage>) {
        errors.items.clear()
        if (errorList.isNotEmpty()) {
            errors.items.setAll(errorList)
            outputPane.selectionModel.select(1)
            if (errorList[0].errorSource != ErrorSource.UNKNOWN) {
                val editor = if (errorList[0].errorSource == ErrorSource.GRAMMAR) grammar else text
                editor.moveTo(errorList[0].line-1, errorList[0].pos-1)
                editor.requestFocus()
            }
        } else {
            outputPane.selectionModel.select(0)
        }
    }

    private fun setResult(content: Node) {
        resultsTab.content = content
    }

    private fun buildTokens(tokens: List<LexerToken>) {
        val list = ListView<String>()
        tokens.forEach {
            list.items.add("${it.type}:\n ${it.text}")
        }
        setResult(list)
    }

    private fun buildSubtree(parentUi: TreeItem<String>, parentParse: ParseTree, ruleNames: List<String>) {
        for (i in 0 until parentParse.childCount) {
            val child = parentParse.getChild(i)
            val uiChild = treeItemFromParseNode(child, ruleNames)
            uiChild.isExpanded = true
            parentUi.children.add(uiChild)
            buildSubtree(uiChild, child, ruleNames)
        }
    }

    private fun buildTree(generatedTree: ParseTree, ruleNames: List<String>) {
        val root = treeItemFromParseNode(generatedTree, ruleNames)
        root.isExpanded = true
        buildSubtree(root, generatedTree, ruleNames)
        val tree = TreeView<String>()
        tree.root = root
        setResult(tree)
    }

    private fun treeItemFromParseNode(
            child: ParseTree?,
            ruleNames: List<String>
    ): TreeItem<String> {
        return if (child is ErrorNode) {
            TreeItem("Error: ${child.text}")
        } else if (child is TerminalNode) {
            TreeItem("Token: ${child.text}")
        } else {
            TreeItem("<${ruleNames[(child as InterpreterRuleContext).ruleIndex]}>")
        }
    }

}

fun main(vararg args: String) {
    Application.launch(AntlrViewApp::class.java, *args)
}