package dev.sacode.flowrun.edit

import org.scalajs.dom
import dev.sacode.flowrun.FlowRunElements
import dev.sacode.flowrun.ProgramModel
import dev.sacode.flowrun.edit.FunctionEditor
import dev.sacode.flowrun.ProgramModel.Request
import dev.sacode.flowrun.Statement

class CtxMenu(
    flowRunElements: FlowRunElements,
    programModel: ProgramModel,
    functionEditor: FunctionEditor
) {

  private var nodeId = ""

  private var afterId = ""
  private var blockId = ""

  private val edgeContextMenu =
    dom.document.getElementById("flowrun-edge-context-menu").asInstanceOf[dom.html.Element]
  private val addOutputButton =
    edgeContextMenu.querySelector("#flowrun-add-output").asInstanceOf[dom.html.Element]

  private val nodeContextMenu =
    dom.document.getElementById("flowrun-node-context-menu").asInstanceOf[dom.html.Element]
  private val deleteButton =
    nodeContextMenu.querySelector("#flowrun-delete").asInstanceOf[dom.html.Element]

  flowRunElements.drawArea.addEventListener(
    "contextmenu",
    (event: dom.MouseEvent) => {

      // here we know which NODE/EDGE is right-clicked
      // we save relevant ids, and then use them when a button is clicked

      event.preventDefault()
      hideAll()
      event.target match {
        case g: dom.svg.Element =>
          g.parentNode match {
            case parent: dom.svg.G =>
              if parent.className.baseVal == "node" then
                nodeContextMenu.style.left = s"${event.clientX}px"
                nodeContextMenu.style.top = s"${event.clientY}px"
                nodeContextMenu.classList.add("active")
                nodeId = parent.id
              else if parent.className.baseVal == "edge" then
                edgeContextMenu.style.left = s"${event.clientX}px"
                edgeContextMenu.style.top = s"${event.clientY}px"
                edgeContextMenu.classList.add("active")

                val titleText = parent.getElementsByTagName("title")(0).textContent
                afterId = getAfterId(titleText)
                println("TITLE: " + titleText)
              else println("noooooooo idea")
            case _ =>
              println("Not a group")
          }
        case _ =>
          println("Not an svg element")
      }
    }
  )

  // close menu when clicked anywhere
  dom.window.addEventListener("click", event => hideAll())

  deleteButton.addEventListener(
    "click",
    (event: dom.MouseEvent) => {
      println("Delete " + nodeId)
      programModel.delete(Request.Delete(nodeId))
      functionEditor.loadCurrentFunction()
    }
  )

  addOutputButton.addEventListener(
    "click",
    (event: dom.MouseEvent) => {
      println(s"Add output after $afterId")
      programModel.addOutput(Request.AddOutput(Statement.newId, afterId, blockId))
      functionEditor.loadCurrentFunction()
    }
  )

  private def hideAll(): Unit =
    import dom.ext.*
    dom.document
      .getElementsByClassName("flowrun-context-menu")
      .foreach { e =>
        e.asInstanceOf[dom.html.Element].classList.remove("active")
      }
  end hideAll

  private def getAfterId(title: String): String =
    title.takeWhile(_ != ':')

}
