package dev.sacode.flowrun

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel
import org.scalajs.dom
import scalatags.JsDom.all.*
import org.getshaka.nativeconverter.NativeConverter
import org.getshaka.nativeconverter.fromJson
import reactify.*
import dev.sacode.flowrun.eval.Interpreter
import dev.sacode.flowrun.edit.FlowchartPresenter
import dev.sacode.flowrun.edit.FunctionSelector
import dev.sacode.flowrun.edit.StatementEditor
import dev.sacode.flowrun.edit.OutputArea
import dev.sacode.flowrun.edit.DebugArea
import dev.sacode.flowrun.edit.CtxMenu
import dev.sacode.flowrun.codegen.CodeGeneratorFactory
import dev.sacode.flowrun.codegen.Language

@JSExportTopLevel("FlowRun")
class FlowRun(
    mountElem: dom.Element,
    programJson: Option[String] = None,
    changeCallback: Option[js.Function1[FlowRun, Unit]] = None
) {

  private val FlowRunConfigKey = "flowrun-config"
  private val localConfig = initLocalConfig()
  def config(): FlowRunConfig = localConfig.get

  private val mountElemText = mountElem.innerText.trim

  private val maybeTemplate = dom.document.getElementById("flowrun-template").asInstanceOf[dom.html.Element]

  val flowRunElements = FlowRunElements.resolve(maybeTemplate)
  mountElem.innerText = ""
  // move all template children to mountElem
  // https://stackoverflow.com/a/20910214/4496364
  while flowRunElements.template.childNodes.length > 0 do
    mountElem.appendChild(flowRunElements.template.childNodes.head)

  private val maybeJson = programJson.orElse(
    Option.when(mountElemText.nonEmpty)(mountElemText)
  )
  private val program = maybeJson match
    case Some(json) => NativeConverter[Program].fromNative(js.JSON.parse(json))
    case None =>
      Program(
        AST.newId,
        "My Program",
        Function(
          "main", // don't touch!
          "main",
          statements = List(Statement.Begin(AST.newId), Statement.Return(AST.newId))
        ),
        List.empty
      )

  private val flowrunChannel = Channel[FlowRun.Event]
  private val programModel = ProgramModel(program, flowrunChannel)
  private var interpreter = Interpreter(programModel, flowrunChannel)

  private val flowchartPresenter = FlowchartPresenter(programModel, flowRunElements)
  private val functionSelector = FunctionSelector(programModel, flowrunChannel, flowRunElements)
  private val statementEditor = StatementEditor(programModel, flowrunChannel, flowRunElements)
  private val ctxMenu = CtxMenu(programModel)
  private var outputArea = OutputArea(interpreter, flowRunElements)
  private var debugArea = DebugArea(interpreter, flowRunElements)

  private var startedTime: String = ""

  flowRunElements.metaData.innerText = program.name

  functionSelector.loadFunctions()

  def json(): String =
    programModel.ast.toJson

  def funDOT(): String =
    flowchartPresenter.funDOT

  def codeText(): String =
    val generator = CodeGeneratorFactory(localConfig.get.lang, programModel.ast)
    val codeTry = generator.generate
    if codeTry.isFailure then println(codeTry.failed)
    codeTry.getOrElse("Error while generating code. Please fix errors in the program.")

  // run the program
  flowRunElements.runButton.onclick = _ => {
    outputArea.clearAll()
    flowchartPresenter.clearErrors()
    flowchartPresenter.clearSelected()
    flowrunChannel := FlowRun.Event.Deselected

    startedTime = getNowTime
    flowRunElements.runtimeOutput.appendChild(s"Started at: $startedTime".render)
    flowRunElements.runtimeOutput.classList.add("flowrun--success")
    flowRunElements.runtimeOutput.appendChild(br.render)
    flowRunElements.runtimeOutput.appendChild(br.render)

    interpreter = Interpreter(programModel, flowrunChannel) // fresh SymTable etc
    outputArea = OutputArea(interpreter, flowRunElements)
    debugArea = DebugArea(interpreter, flowRunElements)

    interpreter.run()
    flowchartPresenter.disable()
  }

  flowRunElements.addFunButton.onclick = _ => programModel.addNewFunction()

  flowRunElements.drawArea.addEventListener(
    "click",
    (event: dom.MouseEvent) => {
      event.preventDefault()
      getSvgNode(event.target) match {
        case ("NODE", n) =>
          val idParts = n.id.split("#")
          val nodeId = idParts(0)
          val tpe = idParts(1)
          programModel.currentStmtId = Some(nodeId)
          outputArea.clearSyntax()
          flowchartPresenter.loadCurrentFunction() // to highlight new node..
          statementEditor.edit(nodeId, tpe)
        case _ =>
          flowrunChannel := FlowRun.Event.Deselected
      }
    }
  )

  flowRunElements.drawArea.addEventListener(
    "contextmenu",
    (event: dom.MouseEvent) => {
      event.preventDefault()
      getSvgNode(event.target) match {
        case ("NODE", n) =>
          val idParts = n.id.split("#")
          val nodeId = idParts(0)
          val tpe = idParts(1)
          ctxMenu.handleRightClick(event, nodeId, tpe)
        case ("EDGE", n) =>
          ctxMenu.handleClick(event.clientX, event.clientY, n)
        case _ =>
      }
    }
  )

  import FlowRun.Event.*
  flowrunChannel.attach {
    case EvalSuccess =>
      flowRunElements.runtimeOutput.appendChild(div(br, s"Finished at: $getNowTime").render)
      flowRunElements.debugVariables.innerText = ""
      flowchartPresenter.enable()
    case SyntaxSuccess =>
      outputArea.clearSyntax()
      flowchartPresenter.loadCurrentFunction() // if function name updated
    case StmtDeleted | StmtAdded =>
      programModel.currentStmtId = None
      outputArea.clearStmt()
      outputArea.clearSyntax()
      flowchartPresenter.loadCurrentFunction()
    case SyntaxError(msg) =>
      outputArea.syntaxError(msg)
      flowchartPresenter.enable()
    case EvalError(nodeId, msg) =>
      outputArea.runtimeError(msg, Some(startedTime), Some(getNowTime))
      flowchartPresenter.highlightError(nodeId)
      flowchartPresenter.enable()
    case EvalOutput(output) =>
      val newOutput = pre(output).render
      flowRunElements.runtimeOutput.appendChild(newOutput)
    case EvalInput(nodeId, name) =>
      outputArea.evalInput(nodeId, name)
    case SymbolTableUpdated =>
      debugArea.showVariables()
    case FunctionUpdated =>
      flowchartPresenter.loadCurrentFunction()
      functionSelector.loadFunctions()
      outputArea.clearSyntax()
    case FunctionSelected =>
      programModel.currentStmtId = None
      outputArea.clearStmt()
      outputArea.clearSyntax()
      functionSelector.loadFunctions()
      flowchartPresenter.loadCurrentFunction()
    case Deselected =>
      programModel.currentStmtId = None
      outputArea.clearStmt()
      outputArea.clearSyntax()
      flowchartPresenter.clearSelected()
    case ConfigChanged => // noop
  }
  flowrunChannel.attach { _ =>
    // on any event hide menus
    ctxMenu.hideAllMenus()
    // gen code always
    generateCode()
  }

  // trigger first time to get the ball rolling
  flowrunChannel := SyntaxSuccess

  ///////////////////////////
  dom.window.addEventListener(
    "storage",
    (event: dom.StorageEvent) => {
      // When local storage changes set the config
      val savedConfig = dom.window.localStorage.getItem(FlowRunConfigKey)
      val savedTodos =
        if (savedConfig == null) FlowRunConfig(Language.java, "")
        else savedConfig.fromJson[FlowRunConfig]

      localConfig.set(savedTodos)
      flowrunChannel := ConfigChanged
    }
  )

  private def generateCode(): Unit = {
    // gen code always
    changeCallback match
      case None     => flowRunElements.codeArea.innerText = codeText()
      case Some(cb) => cb(this)
  }

  // TODO extract to config class
  private def initLocalConfig(): Var[FlowRunConfig] = {

    val config$ : Var[FlowRunConfig] = Var(null)
    config$.attach { newValue =>
      dom.window.localStorage.setItem(FlowRunConfigKey, newValue.toJson)
    }

    val savedConfig = dom.window.localStorage.getItem(FlowRunConfigKey)
    val savedTodos =
      if (savedConfig == null) FlowRunConfig(Language.java, "")
      else savedConfig.fromJson[FlowRunConfig]

    config$.set(savedTodos)
    config$
  }
}

object FlowRun:

  enum Event:
    case EvalSuccess
    case EvalError(nodeId: String, msg: String)
    case EvalOutput(msg: String)
    case EvalInput(nodeId: String, name: String)
    case SyntaxSuccess
    case StmtDeleted
    case StmtAdded
    case SyntaxError(msg: String)
    case SymbolTableUpdated
    case FunctionUpdated
    case FunctionSelected
    case Deselected
    case ConfigChanged
end FlowRun
