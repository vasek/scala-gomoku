package controllers

import models.Gomoku.{Gomoku, RoomActor}
import play.api.libs.json.JsValue
import play.api.mvc._
import play.api.routing.JavaScriptReverseRouter
import scala.concurrent.ExecutionContext.Implicits.global

object GomokuController extends Controller {

  def index = Action {
    Ok(views.html.gomoku())
  }

  def play() = Action {
    val ident = Gomoku.freeRoom()
    Redirect(routes.GomokuController.board(ident))
  }

  def board(id: String) = Action {
    Ok(views.html.board())
  }

  def game(id: String) = WebSocket.tryAccept[JsValue] { request =>
    RoomActor.join(id).map { io =>
      Right(io)
    }.recover {
      case e => Left(Ok(e.toString))
    }
  }

  def jsRoutes() = Action { implicit request =>
    Ok(JavaScriptReverseRouter("gomokuRoutes")(
      routes.javascript.GomokuController.game,
      routes.javascript.GomokuController.index,
      routes.javascript.Assets.at
    )).as(JAVASCRIPT)
  }

}
