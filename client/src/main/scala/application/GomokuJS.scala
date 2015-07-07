package application

import org.scalajs.dom
import org.scalajs.jquery._

import scala.scalajs.js
import scala.scalajs.js.Dynamic._
import scala.scalajs.js.annotation.JSExport
import scalatags.JsDom.all._

@JSExport
object GomokuJS {

  var client: Option[GomokuClient] = None

  var roomId: String = ""
  var player: String = ""
  var first: Boolean = false
  var canMove: Boolean = false

  @JSExport
  def main(): Unit = {
    val href = dom.window.location.href
    roomId = href.substring(href.lastIndexOf("/") + 1)
    client = GomokuClient.connect(roomId)
  }

  def signInPanel = {
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
              client.map {
                _.login(input)
              }
            }
            false
          })("Sign in")
        )
      )
    )
  }

  def board = {
    div(id := "board", `class` := "hide")(
      div(`class` := "board-top")(
        span(id := "loginAs"),
        button(`class` := "button", onclick := { () =>
          signOut
        })("Sign out")
      ),
      div(id := "message"),
      div(`class` := "board")(
        for (i <- 1 to 19) yield div(`class` := "board-row")(
          for (j <- 1 to 19) yield
          div(id := i + "-" + j, `class` := "board-square")
        )
      )
    )
  }

  def getSymbol(p: String): String =
    if (p == player)
      if (first) "x" else "o"
    else
      if (first) "o" else "x"

  def move(player: String, coord: String) = {
    jQuery("#" + coord).html(getSymbol(player))
    canMove = !canMove
    if (GomokuJS.canMove) message("Your turn.")
    else message("Opponent's turn.")
  }

  def message(message: String) = {
    jQuery("#message").html(message)
  }

  def signOut = {
    client.map(_.close())
    jQuery("#signInPanel").removeClass("hide")
    jQuery("#board").addClass("hide")
    jQuery(".board-square").html("")
    dom.location.replace(global.gomokuRoutes.controllers.GomokuController.index().absoluteURL().toString)
  }

  def clickHandlers = {
    jQuery(".board-square").click({(element: dom.raw.HTMLElement) =>
      if (canMove)
        client.map(_.move(player, element.id))
    }: js.ThisFunction)
  }

  object GomokuClient {

    def connect(id: String): Option[GomokuClient] = {
      try {
        if (global.window.WebSocket.toString != "undefined")
          Some(new GomokuClient(id))
        else None
      } catch {
        case e: Throwable =>
          dom.alert("Unable to connect. " + e.toString)
          None
      }
    }

    def receive(e: dom.MessageEvent) = {
      val data = js.JSON.parse(e.data.toString)
      dom.console.log(data)

      if (data.room.toString == roomId) {
        if (data.error.toString != "undefined") {
          dom.alert(data.error.toString)
          signOut
        } else {
          val action = data.action.toString
          val player = data.player.toString
          val text = data.text.toString

          action match {
            case "join" =>
              if (player != "" && player == GomokuJS.player) {
                jQuery("#loginAs").text(s"Login as: ${GomokuJS.player}")
                jQuery("#username").text("")
                jQuery("#signInPanel").addClass("hide")
                jQuery("#board").removeClass("hide")
              }

            case "start" => {
              GomokuJS.first = player == GomokuJS.player
              GomokuJS.canMove = GomokuJS.first
              if (GomokuJS.canMove) message("Your turn.")
              else message("Opponent's turn.")
            }

            case "move" =>
              GomokuJS.move(player, text)

            case "win" => {
              GomokuJS.move(player, text)
              GomokuJS.canMove = false
              if (player == GomokuJS.player) message("You won")
              else message("Opponent won")
            }
          }
        }
      }
    }

  }

  class GomokuClient(val id: String) {
    val gameUrl: String = global.gomokuRoutes.controllers.GomokuController.game(id).webSocketURL().toString
    val socket = new dom.WebSocket(gameUrl)

    socket.onopen = {(e: dom.Event) =>
      val content = dom.document.getElementById("content")
      content.appendChild(signInPanel.render)
      content.appendChild(board.render)
      clickHandlers
    }

    socket.onmessage = GomokuClient.receive _

    def login(username: String): Unit = {
      val json: js.Object = literal(
        "action" -> "login",
        "player" -> username,
        "text" -> ""
      )
      GomokuJS.player = username
      GomokuJS.message("Waiting for other players.")
      socket.send(js.JSON.stringify(json))
    }

    def move(username: String, coord: String): Unit = {
      val json: js.Object = literal(
        "action" -> "move",
        "player" -> username,
        "text" -> coord
      )
      socket.send(js.JSON.stringify(json))
    }

    def close() = socket.close()
  }

}