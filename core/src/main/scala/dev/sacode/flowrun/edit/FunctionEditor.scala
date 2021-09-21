package dev.sacode.flowrun
package edit

import scalajs.js
import scalajs.js.JSConverters.*
import org.scalajs.dom
import reactify.*
import dev.sacode.flowrun.cytoscape.*
import dev.sacode.flowrun.parse.*
import dev.sacode.flowrun.edit.ctxmenu.*

class FunctionEditor(
    programModel: ProgramModel,
    flowrunChannel: Channel[FlowRun.Event],
    flowRunElements: FlowRunElements
) {

  private val cy = new cytoscape(
    js.Dynamic.literal(
      container = flowRunElements.drawArea,
      userZoomingEnabled = false,
      userPanningEnabled = false,
      boxSelectionEnabled = false,
      maxZoom = 1.3,
      //autoungrabify = true,
      style = utils.styleJson
    )
  )

  // load initial program
  loadCurrentFunction()

  // context menu
  if isTouchDevice then TouchContextMenu(programModel, cy).setup()
  else DesktopContextMenu(programModel, cy).setup()

  // edit menu
  EditPanel(programModel, flowRunElements, flowrunChannel, cy).setup()

  def disable(): Unit =
    cy.asDyn.autounselectify(true)

  def enable(): Unit =
    cy.asDyn.autounselectify(false)

  cy.asDyn.on(
    "unselect add remove",
    "node",
    (evt: js.Dynamic) => {
      // node not selected anymore, hide the inputs...
      flowRunElements.editStatement.innerText = ""
      cy.asDyn.nodes().unselect()
      // clear visual error on nodes
      clearErrors()
    }
  )

  flowrunChannel.attach {
    case FlowRun.Event.EvalError(nodeId, msg) =>
      cy.asDyn.nodes(s"node[id = '$nodeId']").data("has-error", true)
    case _ =>
  }

  def clearErrors(): Unit =
    cy.asDyn.nodes().data("has-error", false)
    flowrunChannel := FlowRun.Event.SyntaxSuccess

  def loadCurrentFunction(): Unit = {
    ///import scalajs.js.JSConverters.*
    //println("BEFORE: " + js.JSON.stringify(cy.asDyn.elements().jsons()))
    cy.remove("*")
    val currentFun = programModel.currentFunction
    val statements = currentFun.statements

    if currentFun.isMain then
      val firstNode = Node("begin", Node.Begin, id = "beginId")
      val lastNode = Node("end", Node.End, id = "endId")
      cy.add(firstNode.toLit)
      cy.add(lastNode.toLit)
      val firstEdge = cy.add(Edge(firstNode.id, lastNode.id).toLit)
      load(statements, firstNode, lastNode, firstEdge)
    else
      val rawParams = currentFun.parameters.map((n, t) => s"$n: $t").mkString(",")
      val firstNode = Node(
        currentFun.label,
        Node.Start,
        id = currentFun.id,
        rawName = currentFun.name,
        rawParams = rawParams,
        rawTpe = currentFun.tpe.toString
      )
      val retStmt = statements.last.asInstanceOf[Statement.Return]
      val lastNode = Node(
        retStmt.label,
        Node.Return,
        id = retStmt.id,
        rawExpr = retStmt.maybeValue.getOrElse("")
      )
      cy.add(firstNode.toLit)
      cy.add(lastNode.toLit)
      val firstEdge = cy.add(Edge(firstNode.id, lastNode.id).toLit)
      load(statements.init, firstNode, lastNode, firstEdge)

    doLayout(cy)
  }

  private def load(
      statements: List[Statement],
      lastNode: Node,
      nextNode: Node,
      lastEdge: js.Dynamic
  ): js.Dynamic = {
    import Statement.*

    var prevNode = lastNode
    var prevEdge = lastEdge

    statements.foreach {
      case stmt: Input =>
        val newNode = Node(stmt.label, Node.Input, id = stmt.id, rawName = stmt.name)
        cy.add(newNode.toLit)
        cy.add(Edge(prevNode.id, newNode.id).toLit)
        prevEdge.move(js.Dynamic.literal(target = newNode.id))
        prevNode = newNode
        prevEdge = cy.add(Edge(newNode.id, nextNode.id).toLit)
      case stmt: Output =>
        val newNode = Node(stmt.label, Node.Output, id = stmt.id, rawExpr = stmt.value)
        cy.add(newNode.toLit)
        prevEdge.move(js.Dynamic.literal(target = newNode.id))
        prevNode = newNode
        prevEdge = cy.add(Edge(newNode.id, nextNode.id).toLit)
      case stmt: Declare =>
        val newNode = Node(
          stmt.label,
          Node.Declare,
          id = stmt.id,
          rawName = stmt.name,
          rawTpe = stmt.tpe.toString,
          rawExpr = stmt.initValue.getOrElse("")
        )
        cy.add(newNode.toLit)
        prevEdge.move(js.Dynamic.literal(target = newNode.id))
        prevNode = newNode
        prevEdge = cy.add(Edge(newNode.id, nextNode.id).toLit)
      case stmt: Assign =>
        val newNode =
          Node(stmt.label, Node.Assign, id = stmt.id, rawName = stmt.name, rawExpr = stmt.value)
        cy.add(newNode.toLit)
        prevEdge.move(js.Dynamic.literal(target = newNode.id))
        prevNode = newNode
        prevEdge = cy.add(Edge(newNode.id, nextNode.id).toLit)
      case stmt: Call =>
        val newNode = Node(stmt.label, Node.Call, id = stmt.id, rawExpr = stmt.value)
        cy.add(newNode.toLit)
        prevEdge.move(js.Dynamic.literal(target = newNode.id))
        prevNode = newNode
        prevEdge = cy.add(Edge(newNode.id, nextNode.id).toLit)

      case stmt @ If(id, expr, trueBlock, falseBlock) =>
        
        val ifEndNode = Node("", Node.IfEnd)
        val ifNode =
          Node(stmt.condition, Node.If, id = stmt.id, endId = ifEndNode.id, rawExpr = expr)
        
        cy.add(ifNode.toLit)
        cy.add(ifEndNode.toLit)
        prevEdge.move(js.Dynamic.literal(target = ifNode.id))
        val trueEdge = cy.add(Edge(ifNode.id, ifNode.id, "true").toLit)
        val falseEdge = cy.add(Edge(ifNode.id, ifEndNode.id, "false").toLit)

        val lastFalseEdge = if falseBlock.statements.isEmpty then
          val falseNode = Node("", Node.Dummy, startId = ifNode.id, endId = ifEndNode.id)
          cy.add(falseNode.toLit)
          falseEdge.move(js.Dynamic.literal(target = falseNode.id))
          cy.add(Edge(falseNode.id, falseNode.id).toLit)
        else load(falseBlock.statements, ifNode, ifEndNode, falseEdge)

        val lastTrueEdge = if trueBlock.statements.isEmpty then
          val trueNode = Node("", Node.Dummy, startId = ifNode.id, endId = ifEndNode.id)
          cy.add(trueNode.toLit)
          trueEdge.move(js.Dynamic.literal(target = trueNode.id))
          cy.add(Edge(trueNode.id, trueNode.id).toLit)
        else load(trueBlock.statements, ifNode, ifEndNode, trueEdge)

        lastTrueEdge.move(js.Dynamic.literal(target = ifEndNode.id))
        lastFalseEdge.move(js.Dynamic.literal(target = ifEndNode.id))

        lastFalseEdge.data("dir", "vert")
        lastTrueEdge.data("dir", "vert")

        prevEdge =
          cy.add(Edge(ifEndNode.id, nextNode.id, dir = "vert", blockId = trueBlock.id).toLit)
        prevNode = ifEndNode
      
      case stmt @ While(id, expr, trueBlock, falseBlock) =>
        
        val whileEndNode = Node("", Node.WhileEnd)
        val whileNode =
          Node(stmt.condition, Node.While, id = stmt.id, endId = whileEndNode.id, rawExpr = expr)
        
        cy.add(whileNode.toLit)
        cy.add(whileEndNode.toLit)
        prevEdge.move(js.Dynamic.literal(target = whileNode.id))
        val trueEdge = cy.add(Edge(whileNode.id, whileNode.id, "true").toLit)
        val falseEdge = cy.add(Edge(whileNode.id, whileEndNode.id, "false").toLit)

        val lastFalseEdge = if falseBlock.statements.isEmpty then
          val falseNode = Node("", Node.Dummy, startId = whileNode.id, endId = whileEndNode.id)
          cy.add(falseNode.toLit)
          falseEdge.move(js.Dynamic.literal(target = falseNode.id))
          cy.add(Edge(falseNode.id, falseNode.id).toLit)
        else load(falseBlock.statements, whileNode, whileEndNode, falseEdge)

        val lastTrueEdge = if trueBlock.statements.isEmpty then
          val trueNode = Node("", Node.Dummy, startId = whileNode.id, endId = whileEndNode.id)
          cy.add(trueNode.toLit)
          trueEdge.move(js.Dynamic.literal(target = trueNode.id))
          cy.add(Edge(trueNode.id, trueNode.id).toLit)
        else load(trueBlock.statements, whileNode, whileEndNode, trueEdge)

        lastTrueEdge.move(js.Dynamic.literal(target = whileEndNode.id))
        lastFalseEdge.move(js.Dynamic.literal(target = whileEndNode.id))

        lastFalseEdge.data("dir", "vert")
        lastTrueEdge.data("dir", "vert")

        prevEdge =
          cy.add(Edge(whileEndNode.id, nextNode.id, dir = "vert", blockId = trueBlock.id).toLit)
        prevNode = whileEndNode
      

      case stmt @ (_: Dummy) =>
        val newNode = Node(stmt.label, "Dummy", id = stmt.id)
        cy.add(newNode.toLit)
        prevEdge.move(js.Dynamic.literal(target = newNode.id))

      case _: Block            => // noop
      case _: Statement.Return => // noop
    }
    prevEdge
  }

}
