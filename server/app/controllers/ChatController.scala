package controllers

import models.Chat.Room
import play.api.libs.json.JsValue
import play.api.mvc.{WebSocket, Action, Controller}
import play.api.routing.JavaScriptReverseRouter
import play.api.libs.concurrent.Execution.Implicits._

object ChatController extends Controller {

  def index = Action {
    Ok(views.html.chat())
  }

  def chat(username: String) = WebSocket.tryAccept[JsValue] { request =>
    Room.join(username).map { io =>
      Right(io)
    }.recover {
      case e => Left(Ok(e.toString))
    }
  }

  def jsRoutes() = Action { implicit request =>
    Ok(JavaScriptReverseRouter("chatRoutes")(
      routes.javascript.ChatController.chat,
      routes.javascript.Assets.at
    )).as(JAVASCRIPT)
  }

}