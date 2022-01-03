package dev.sacode.flowrun

import org.scalajs.dom
import scalatags.JsDom.all.*

/* UI sections and elements */
class FlowRunElements(
    template: dom.html.Element
) {
  // template is CLONED, so we can use "Run button", "output" and other "single purpose" elements freely

  template.id = ""
  template.style = "" // remove hidden attributes -> "display: block;"

  // areas
  val metaData: dom.Element = template.querySelector(".flowrun-meta")
  val functionsChooser: dom.Element = template.querySelector(".flowrun-fun-chooser")
  val drawArea: dom.Element = template.querySelector(".flowrun-draw")
  val scratchpad: dom.Element = template.querySelector(".flowrun-scratchpad")
  val debugVariables: dom.Element = template.querySelector(".flowrun-debug")

  // singletons
  val runButton = template.querySelector(".flowrun-btn-run").asInstanceOf[dom.html.Element]

  val addFunButton = template.querySelector(".flowrun-fun-add").asInstanceOf[dom.html.Element]
  val deleteFunButton = template.querySelector(".flowrun-fun-delete").asInstanceOf[dom.html.Element]

  val addParamButton = template.querySelector(".flowrun-fun-add-param").asInstanceOf[dom.html.Element]
  val deleteParamButton = template.querySelector(".flowrun-fun-delete-param").asInstanceOf[dom.html.Element]

  // general
  private val enterButton = template.querySelector(".flowrun-btn-enter").asInstanceOf[dom.html.Element]
  private val inputText = template.querySelector(".flowrun-input-text").asInstanceOf[dom.html.Input]
  private val inputSelect = template.querySelector(".flowrun-input-select").asInstanceOf[dom.html.Select]

  // clean up
  template.querySelector(".flowrun-template-transient").remove()
  metaData.innerText = ""
  functionsChooser.innerText = ""
  drawArea.innerText = ""
  scratchpad.innerText = ""
  debugVariables.innerText = ""

  def newInputText: dom.html.Input =
    inputText.cloneNode(true).asInstanceOf[dom.html.Input]

  def newInputSelect: dom.html.Select =
    inputSelect.cloneNode(true).asInstanceOf[dom.html.Select]

  def newEnterButton: dom.html.Element =
    enterButton.cloneNode(true).asInstanceOf[dom.html.Element]

  def newDeleteParamButton: dom.html.Element =
    deleteParamButton.cloneNode(true).asInstanceOf[dom.html.Element]

}

object FlowRunElements {

  def resolve(maybeTemplate: dom.html.Element): FlowRunElements =
    val t =
      if maybeTemplate == null then defaultTemplate
      else maybeTemplate.cloneNode(true).asInstanceOf[dom.html.Element]
    FlowRunElements(t)

  private def defaultTemplate: dom.html.Element = {

    val addFunButton = button("Add").render
    val deleteFunButton = button("Delete").render

    val addParamButton = button("+").render
    val deleteParamButton = button("-").render

    val runButton = button("Run").render
    val enterButton = button("Run").render
    val inputText = input(tpe := "text").render
    val inputSelect = select().render

    div(
      div(cls := "flowrun-meta")(),
      div(cls := "flowrun-function")(),
      div(cls := "flowrun-btn-run")(),
      div(cls := "flowrun-content")(
        div(cls := "flowrun-draw")(),
        div(cls := "flowrun-edit")(),
        div(cls := "flowrun-output")(),
        div(cls := "flowrun-debug")()
      )
    ).render
  }
}
