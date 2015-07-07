package application

import org.scalajs.dom
import org.scalajs.jquery.jQuery

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global, literal}
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.all._

@JSExport
object ChatJS {

  val maxMessages = 50

  var client: Option[ChatClient] = None

  @JSExport
  def main(): Unit = {
    val content = dom.document.getElementById("content")
    content.appendChild(signInPanel.render)
    content.appendChild(chatPanel.render)
    keyPressHandler
  }

  def signInPanel =
    div(id := "signInPanel")(
      form("role".attr := "form")(
        div(id := "usernameForm")(
          div(`class` := "input")(
            div(`class` := "input-prefix", raw("&#9786;")),
            input(id := "username", `class` := "form-control", `type` := "text", placeholder := "Username")
          ),
          button(`class` := "button", onclick := { () =>
            val input = jQuery("#username").value().toString.trim
            if (input == "") {
              jQuery("#usernameForm").addClass("has-error")
              dom.alert("Invalid username")
            } else {
              jQuery("#usernameForm").removeClass("has-error")
              client = ChatClient.connect(input).map{ c =>
                jQuery("#loginAs").text(s"Login as: ${c.username}")
                jQuery("#username").text("")
                jQuery("#signInPanel").addClass("hide")
                jQuery("#chatPanel").removeClass("hide")
                c
              }
            }
            false
          })("Sign in")
        )
      )
    )

  def chatPanel =
    div(id := "chatPanel", `class` := "hide")(
      div(`class` := "chatPanel-top")(
        span(id := "loginAs"),
        button(`class` := "button", onclick := { () =>
          signOut
        })("Sign out")
      ),
      div(`class` := "panel")(
        div(`class` := "panel-heading")(
          h3("Chat Room")
        ),
        div(`class` := "panel-body")(
          div(id := "messages")
        ),
        div(`class` := "panel-footer")(
          textarea(id := "message", `class` := "form-control message-new", placeholder := "Say something")
        )
      )
    )

  def createMessage(username: String, message: String) = {
    div(`class` := s"message message${if (username == client.map(_.username).getOrElse("")) "-me" else ""}")(
      div(`class` := "message-username")(
        div(username)
      ),
    div(`class` := "message-text")(raw(message))
    )
  }

  def keyPressHandler = {
    val element = jQuery("#message")
    element.keypress((e: dom.KeyboardEvent) => {
      if (!e.shiftKey && e.keyCode == 13) {
        e.preventDefault()
        client.map{_.send(element.value().toString)}
        element.value("")
      }
    })
  }

  def signOut = {
    client.map(_.close())
    jQuery("#signInPanel").removeClass("hide")
    jQuery("#chatPanel").addClass("hide")
    jQuery("#messages").html("")
  }

  object ChatClient {
    def connect(username: String): Option[ChatClient] = {
      try {
        if (global.window.WebSocket.toString != "undefined")
          Some(new ChatClient(username))
        else None
      } catch {
        case e: Throwable => {
          dom.alert("Unable to connect. " + e.toString)
          None
        }
      }
    }

    def receive(e: dom.MessageEvent) = {
      val messageElem = dom.document.getElementById("messages")
      val data = js.JSON.parse(e.data.toString)
      dom.console.log(data)

      if (data.error.toString != "undefined") {
        dom.alert(data.error.toString)
        signOut
      } else {
        val user = data.user.toString
        val message = data.message.toString

        messageElem.appendChild(createMessage(user, message).render)
        if (messageElem.childNodes.length >= maxMessages) {
          messageElem.removeChild(messageElem.firstChild)
        }
        messageElem.scrollTop = messageElem.scrollHeight
      }
    }
  }

  class ChatClient(val username: String) {

    val chatUrl: String = global.chatRoutes.controllers.ChatController.chat(username).webSocketURL().toString
    val socket = new dom.WebSocket(chatUrl)
    socket.onmessage = ChatClient.receive _

    def send(message: String): Unit = {
      val json: js.Object = literal(
        "text" -> message
      )
      socket.send(js.JSON.stringify(json))
    }

    def close() = socket.close()
  }

}